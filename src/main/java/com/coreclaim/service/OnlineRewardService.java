package com.coreclaim.service;

import com.coreclaim.CoreClaimPlugin;
import com.coreclaim.item.ClaimCoreFactory;
import com.coreclaim.model.PlayerProfile;
import com.coreclaim.platform.PlatformScheduler;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.ComponentBuilder;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.entity.Player;

public final class OnlineRewardService {

    private final CoreClaimPlugin plugin;
    private final PlatformScheduler platformScheduler;
    private final ProfileService profileService;
    private final ClaimService claimService;
    private final ClaimCoreFactory claimCoreFactory;
    private PlatformScheduler.TaskHandle taskHandle;
    private int saveCounter;

    public OnlineRewardService(
        CoreClaimPlugin plugin,
        PlatformScheduler platformScheduler,
        ProfileService profileService,
        ClaimService claimService,
        ClaimCoreFactory claimCoreFactory
    ) {
        this.plugin = plugin;
        this.platformScheduler = platformScheduler;
        this.profileService = profileService;
        this.claimService = claimService;
        this.claimCoreFactory = claimCoreFactory;
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
    }

    private void tickOnlinePlayers() {
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            platformScheduler.runPlayerTask(player, () -> tickPlayer(player));
        }

        saveCounter++;
        if (saveCounter >= 5) {
            saveCounter = 0;
            profileService.save();
        }
    }

    private void tickPlayer(Player player) {
        PlayerProfile profile = profileService.getOrCreate(player.getUniqueId(), player.getName());
        profile.addOnlineMinutes(1);
        int current = profile.onlineMinutes();
        int requiredMinutes = plugin.settings().starterRewardMinutes();

        if (!profile.starterCoreGranted()
            && current < requiredMinutes
            && current > 0
            && current % 5 == 0) {
            int remaining = requiredMinutes - current;
            player.sendMessage(plugin.message(
                "starter-core-reminder",
                "{minutes}", String.valueOf(requiredMinutes),
                "{remaining}", String.valueOf(remaining)
            ));
        }

        if (!profile.starterCoreGranted() && current >= requiredMinutes) {
            profile.setStarterCoreGranted(true);
            claimCoreFactory.giveStarterCore(player, 1);
            player.sendMessage(plugin.message("claim-core-rewarded", "{minutes}", String.valueOf(requiredMinutes)));
            profileService.save();
            return;
        }

        if (shouldSendStarterReclaimReminder(player, profile, current, requiredMinutes)) {
            sendStarterReclaimReminder(player);
        }
    }

    private boolean shouldSendStarterReclaimReminder(Player player, PlayerProfile profile, int current, int requiredMinutes) {
        if (player == null || profile == null) {
            return false;
        }
        if (!profile.starterCoreGranted()) {
            return false;
        }
        if (claimService.countClaims(player.getUniqueId()) > 0) {
            return false;
        }
        if (claimCoreFactory.hasStarterCore(player)) {
            return false;
        }
        int interval = plugin.settings().starterReclaimReminderIntervalMinutes();
        return current > requiredMinutes && interval > 0 && (current - requiredMinutes) % interval == 0;
    }

    private void sendStarterReclaimReminder(Player player) {
        TextComponent message = new TextComponent(chatMessage(
            "starter-core-reclaim-reminder",
            "&6&l提醒: &7你还没有创建第一块领地，新人核心丢失的话可以点击后面的按钮补领。"
        ));
        TextComponent button = new TextComponent(plainMessage(
            "starter-core-reclaim-button",
            "&a[点击补领]"
        ));
        button.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/claim reclaimstarter"));
        button.setHoverEvent(new HoverEvent(
            HoverEvent.Action.SHOW_TEXT,
            new ComponentBuilder(plainMessage(
                "starter-core-reclaim-hover",
                "&e点击补领你的新人核心"
            )).create()
        ));
        player.spigot().sendMessage(message, button);
    }

    private String chatMessage(String path, String fallback, String... replacements) {
        String prefix = plugin.messagesConfig().getString("prefix", "&8[&6Claim&8] &f");
        String body = plugin.messagesConfig().contains(path) ? plugin.messagesConfig().getString(path, fallback) : fallback;
        String message = plugin.color(prefix + body);
        for (int index = 0; index + 1 < replacements.length; index += 2) {
            message = message.replace(replacements[index], replacements[index + 1]);
        }
        return message;
    }

    private String plainMessage(String path, String fallback, String... replacements) {
        String message = plugin.color(plugin.messagesConfig().contains(path)
            ? plugin.messagesConfig().getString(path, fallback)
            : fallback);
        for (int index = 0; index + 1 < replacements.length; index += 2) {
            message = message.replace(replacements[index], replacements[index + 1]);
        }
        return message;
    }
}
