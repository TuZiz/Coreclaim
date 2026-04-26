package com.coreclaim.service;

import com.coreclaim.CoreClaimPlugin;
import com.coreclaim.item.ClaimCoreFactory;
import com.coreclaim.model.PlayerProfile;
import com.coreclaim.platform.PlatformScheduler;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.entity.Player;

public final class OnlineRewardService {

    private static final List<Integer> STARTER_COUNTDOWN_REMINDERS = List.of(20, 10, 5, 1);

    private final CoreClaimPlugin plugin;
    private final PlatformScheduler platformScheduler;
    private final ProfileService profileService;
    private final ClaimService claimService;
    private final ClaimCoreFactory claimCoreFactory;
    private final ClaimCleanupService claimCleanupService;
    private final Map<UUID, StarterRewardSession> sessions = new ConcurrentHashMap<>();
    private PlatformScheduler.TaskHandle taskHandle;
    private int saveCounter;

    public OnlineRewardService(
        CoreClaimPlugin plugin,
        PlatformScheduler platformScheduler,
        ProfileService profileService,
        ClaimService claimService,
        ClaimCoreFactory claimCoreFactory,
        ClaimCleanupService claimCleanupService
    ) {
        this.plugin = plugin;
        this.platformScheduler = platformScheduler;
        this.profileService = profileService;
        this.claimService = claimService;
        this.claimCoreFactory = claimCoreFactory;
        this.claimCleanupService = claimCleanupService;
    }

    public void start() {
        stop();
        this.taskHandle = platformScheduler.runRepeating(this::tickOnlinePlayers, 1200L, 1200L);
    }

    public void stop() {
        if (taskHandle != null) {
            taskHandle.cancel();
            taskHandle = null;
        }
        flushOnlinePlayers();
        sessions.clear();
    }

    public void handleJoin(Player player) {
        if (player == null) {
            return;
        }

        long now = System.currentTimeMillis();
        String groupKey = plugin.groups().resolve(player).key();
        boolean permissionExempt = plugin.settings().isInactiveClaimCleanupPermissionExempt(player);
        profileService.updatePresence(player.getUniqueId(), player.getName(), now, groupKey, permissionExempt);
        claimCleanupService.revokeGraceForOwner(player.getUniqueId());

        PlayerProfile profile = profileService.getOrCreate(player.getUniqueId(), player.getName());
        boolean hasOrdinaryClaim = claimService.countClaims(player.getUniqueId()) > 0;
        if (hasOrdinaryClaim) {
            sessions.remove(player.getUniqueId());
            return;
        }

        sessions.put(player.getUniqueId(), new StarterRewardSession());
        long currentSeconds = profile.onlineSeconds();
        int requiredMinutes = plugin.settings().starterRewardMinutes();
        long requiredSeconds = requiredMinutes * 60L;

        if (!profile.starterCoreGranted()) {
            if (currentSeconds >= requiredSeconds) {
                grantStarterCore(player, profile, requiredMinutes);
                return;
            }

            int remaining = remainingStarterRewardMinutes(currentSeconds, requiredMinutes);
            player.sendMessage(chatMessage(
                "starter-core-join-reminder",
                "&b&l领地: &7累计在线满 &e{minutes} &7分钟可获得第一块领地核心，当前还差 &e{remaining} &7分钟。",
                "{minutes}", String.valueOf(requiredMinutes),
                "{remaining}", String.valueOf(remaining)
            ));
            return;
        }

        if (claimCoreFactory.hasStarterCore(player)) {
            player.sendMessage(chatMessage(
                "starter-core-login-guidance",
                "&a&l首块领地: &7你已经拿到新人核心了，手持它对地面右键就能开始创建第一块领地。&f新人核心创建的是默认全格保护领地。"
            ));
            return;
        }

        player.sendMessage(chatMessage(
            "starter-core-missing-guidance",
            "&e&l提示: &7新人核心不会自动补发了；如果你已经丢失它，直接拿普通金锄头左键点 1、右键点 2，再输入 &e/claim create <名字> &7也能完成第一块领地。"
        ));
    }

    public void markOrdinaryClaimCreated(Player player) {
        if (player == null) {
            return;
        }
        sessions.remove(player.getUniqueId());
    }

    public void clearSession(UUID playerId) {
        if (playerId != null) {
            sessions.remove(playerId);
        }
    }

    public void handleQuit(Player player) {
        if (player == null) {
            return;
        }
        long now = System.currentTimeMillis();
        PlayerProfile profile = profileService.getOrCreate(player.getUniqueId(), player.getName());
        updateOnlineSeconds(profile, now);
        String groupKey = plugin.groups().resolve(player).key();
        boolean permissionExempt = plugin.settings().isInactiveClaimCleanupPermissionExempt(player);
        profileService.updatePresence(player.getUniqueId(), player.getName(), now, groupKey, permissionExempt);
        clearSession(player.getUniqueId());
    }

    private void tickOnlinePlayers() {
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            platformScheduler.runPlayerTask(player, () -> tickPlayer(player));
        }

        saveCounter++;
        if (!profileService.usesSharedDatabase() && saveCounter >= 5) {
            saveCounter = 0;
            profileService.save();
        }
    }

    private void tickPlayer(Player player) {
        long now = System.currentTimeMillis();
        PlayerProfile profile = profileService.getOrCreate(player.getUniqueId(), player.getName());
        long previousSeconds = profile.onlineSeconds();
        long addedSeconds = updateOnlineSeconds(profile, now);
        long currentSeconds = profile.onlineSeconds();
        if (profileService.usesSharedDatabase() && addedSeconds > 0L) {
            profileService.saveProfile(profile);
        }

        StarterRewardSession session = sessions.get(player.getUniqueId());
        if (session == null) {
            return;
        }

        int requiredMinutes = plugin.settings().starterRewardMinutes();
        long requiredSeconds = requiredMinutes * 60L;
        if (profile.starterCoreGranted()) {
            return;
        }

        Integer reminder = crossedStarterReminder(previousSeconds, currentSeconds, requiredMinutes);
        if (reminder != null) {
            player.sendMessage(plugin.message(
                "starter-core-reminder",
                "{minutes}", String.valueOf(requiredMinutes),
                "{remaining}", String.valueOf(reminder)
            ));
        }

        if (currentSeconds >= requiredSeconds) {
            grantStarterCore(player, profile, requiredMinutes);
        }
    }

    private Integer crossedStarterReminder(long previousSeconds, long currentSeconds, int requiredMinutes) {
        int previousMinutes = (int) Math.max(0L, previousSeconds / 60L);
        int currentMinutes = (int) Math.max(0L, currentSeconds / 60L);
        if (currentMinutes <= 0 || currentMinutes >= requiredMinutes || currentMinutes <= previousMinutes) {
            return null;
        }
        for (int remaining : STARTER_COUNTDOWN_REMINDERS) {
            int triggerMinutes = requiredMinutes - remaining;
            if (previousMinutes < triggerMinutes && currentMinutes >= triggerMinutes) {
                return remaining;
            }
        }
        return null;
    }

    private int remainingStarterRewardMinutes(long currentSeconds, int requiredMinutes) {
        long requiredSeconds = requiredMinutes * 60L;
        long remainingSeconds = Math.max(0L, requiredSeconds - currentSeconds);
        return remainingSeconds <= 0L ? 0 : (int) Math.max(1L, (remainingSeconds + 59L) / 60L);
    }

    private long updateOnlineSeconds(PlayerProfile profile, long now) {
        long lastTrackedAt = profile.lastSeenAt();
        if (lastTrackedAt <= 0L || now <= lastTrackedAt) {
            profile.setLastSeenAt(now);
            return 0L;
        }
        long elapsedSeconds = (now - lastTrackedAt) / 1000L;
        if (elapsedSeconds <= 0L) {
            return 0L;
        }
        profile.addOnlineSeconds(elapsedSeconds);
        profile.setLastSeenAt(lastTrackedAt + elapsedSeconds * 1000L);
        return elapsedSeconds;
    }

    private void flushOnlinePlayers() {
        long now = System.currentTimeMillis();
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            PlayerProfile profile = profileService.getOrCreate(player.getUniqueId(), player.getName());
            long addedSeconds = updateOnlineSeconds(profile, now);
            if (addedSeconds > 0L) {
                profile.setLastSeenAt(now);
                profileService.saveProfile(profile);
            }
        }
    }

    private void grantStarterCore(Player player, PlayerProfile profile, int requiredMinutes) {
        profile.setStarterCoreGranted(true);
        claimCoreFactory.giveStarterCore(player, 1);
        player.sendMessage(plugin.message("claim-core-rewarded", "{minutes}", String.valueOf(requiredMinutes)));
        player.sendMessage(chatMessage(
            "starter-core-guidance",
            "&e&l下一步: &7手持新人核心对地面右键，输入领地名字后就能创建第一块领地。&f新人核心创建的是默认全格保护领地。"
        ));
        profileService.saveProfile(profile);
    }

    private String chatMessage(String path, String fallback, String... replacements) {
        String prefix = plugin.messagesConfig().getString("prefix", "&#64748B[&#A7F3D0Claim&#64748B] &#CBD5E1");
        String body = plugin.messagesConfig().contains(path) ? plugin.messagesConfig().getString(path, fallback) : fallback;
        String message = plugin.color(prefix + body);
        for (int index = 0; index + 1 < replacements.length; index += 2) {
            message = message.replace(replacements[index], replacements[index + 1]);
        }
        return message;
    }

    private record StarterRewardSession() {
    }
}
