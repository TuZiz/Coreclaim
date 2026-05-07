package com.coreclaim.listener;

import com.coreclaim.CoreClaimPlugin;
import com.coreclaim.model.Claim;
import com.coreclaim.model.ClaimFlag;
import com.coreclaim.model.ClaimFlagState;
import com.coreclaim.model.ClaimPermission;
import com.coreclaim.platform.PlatformScheduler;
import com.coreclaim.service.ClaimService;
import com.coreclaim.service.ClaimVisualService;
import com.coreclaim.profile.ProfileService;
import com.coreclaim.util.AdminAccess;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerGameModeChangeEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerKickEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.player.PlayerToggleFlightEvent;

public final class ClaimEnterLeaveListener implements Listener {

    private static final long CLAIM_DAY_TIME = 6000L;
    private static final long CLAIM_NIGHT_TIME = 18000L;
    private static final long BLOCKED_ENTRY_NOTICE_COOLDOWN_MILLIS = 2000L;

    private final CoreClaimPlugin plugin;
    private final ClaimService claimService;
    private final ProfileService profileService;
    private final ClaimVisualService claimVisualService;
    private final ClaimEntryMessageFormatter entryMessageFormatter;
    private final Map<UUID, PlayerFlightSession> flightSessions = new ConcurrentHashMap<>();
    private final Map<UUID, BlockedEntryNotice> blockedEntryNotices = new ConcurrentHashMap<>();
    private final PlatformScheduler.TaskHandle reconcileTask;

    public ClaimEnterLeaveListener(
        CoreClaimPlugin plugin,
        ClaimService claimService,
        ProfileService profileService,
        ClaimVisualService claimVisualService
    ) {
        this.plugin = plugin;
        this.claimService = claimService;
        this.profileService = profileService;
        this.claimVisualService = claimVisualService;
        this.entryMessageFormatter = new ClaimEntryMessageFormatter(plugin);
        long intervalTicks = plugin.settings().flightReconcileIntervalTicks();
        this.reconcileTask = intervalTicks > 0L
            ? plugin.platformScheduler().runRepeating(this::reconcileAllPlayers, intervalTicks, intervalTicks)
            : null;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        synchronizePlayer(event.getPlayer(), "join");
    }

    @EventHandler(ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        if (event.getTo() == null) {
            return;
        }
        if (sameBlock(event.getFrom(), event.getTo())) {
            return;
        }
        if (handleLocationChange(event.getPlayer(), event.getFrom(), event.getTo(), "move")) {
            event.setTo(event.getFrom());
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        if (event.getTo() == null) {
            return;
        }
        if (handleLocationChange(event.getPlayer(), event.getFrom(), event.getTo(), "teleport")) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onChangedWorld(PlayerChangedWorldEvent event) {
        synchronizePlayer(event.getPlayer(), "world-change");
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) {
        if (event.getRespawnLocation() == null) {
            return;
        }
        plugin.platformScheduler().runPlayerTask(event.getPlayer(), () -> synchronizePlayer(event.getPlayer(), "respawn"));
    }

    @EventHandler
    public void onGameModeChange(PlayerGameModeChangeEvent event) {
        plugin.platformScheduler().runPlayerTask(event.getPlayer(), () -> synchronizePlayer(event.getPlayer(), "gamemode"));
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        PlayerFlightSession session = flightSessions.get(player.getUniqueId());
        if (session != null) {
            revokeManagedFlight(player, session, "death");
            cleanupSession(player.getUniqueId(), session);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onToggleFlight(PlayerToggleFlightEvent event) {
        Player player = event.getPlayer();
        PlayerFlightSession session = flightSessions.get(player.getUniqueId());
        if (session == null || !session.managingClaimFlight) {
            return;
        }

        Claim claim = claimService.findPlayerPresenceClaim(player.getLocation()).orElse(null);
        session.currentClaimId = claim == null ? null : claim.id();
        if (canUseClaimFlight(player, claim)) {
            cancelGrace(session);
            debugFlight(player, "toggle-allowed", claim, session);
            return;
        }

        event.setCancelled(true);
        revokeManagedFlight(player, session, "toggle-denied");
        cleanupSession(player.getUniqueId(), session);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        handleDisconnect(event.getPlayer(), "quit-revoke");
    }

    @EventHandler
    public void onKick(PlayerKickEvent event) {
        handleDisconnect(event.getPlayer(), "kick-revoke");
    }

    public void shutdown() {
        if (reconcileTask != null) {
            reconcileTask.cancel();
        }
        for (Player player : Bukkit.getOnlinePlayers()) {
            handleDisconnect(player, "disable-revoke");
        }
        flightSessions.clear();
    }

    private void reconcileAllPlayers() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            plugin.platformScheduler().runPlayerTask(player, () -> synchronizePlayer(player, "periodic"));
        }
    }

    private void sendActionBar(Player player, String message) {
        player.spigot().sendMessage(ChatMessageType.ACTION_BAR, TextComponent.fromLegacyText(message));
    }

    private boolean handleLocationChange(Player player, Location from, Location to, String reason) {
        UUID playerId = player.getUniqueId();
        PlayerFlightSession session = flightSessions.computeIfAbsent(playerId, ignored -> new PlayerFlightSession());
        Claim fromClaim = resolveCurrentClaim(session, from);
        Claim toClaim = resolveTargetClaim(session, fromClaim, to);
        int fromId = fromClaim == null ? -1 : fromClaim.id();
        int toId = toClaim == null ? -1 : toClaim.id();
        Claim fromNotifyClaim = resolveNotifyClaim(from);
        Claim toNotifyClaim = resolveNotifyClaim(to);
        int fromNotifyId = fromNotifyClaim == null ? -1 : fromNotifyClaim.id();
        int toNotifyId = toNotifyClaim == null ? -1 : toNotifyClaim.id();

        if (fromId != toId && isBlockedEntry(player, toClaim)) {
            notifyBlockedEntry(player, playerId, toClaim);
            debugFlight(player, "blocked-entry", toClaim, session);
            cleanupSession(playerId, session);
            return true;
        }

        session.currentClaimId = toClaim == null ? null : toClaim.id();
        updateFlightState(player, session, toClaim, reason + (fromId == toId ? "-same-claim" : "-claim-change"));
        updateClaimTime(player, session, toClaim);

        if (fromNotifyId == toNotifyId) {
            cleanupSession(playerId, session);
            return false;
        }

        if (!plugin.getConfig().getBoolean("show-enter-leave-messages", true)) {
            cleanupSession(playerId, session);
            return false;
        }
        if (fromNotifyClaim != null) {
            sendActionBar(player, entryMessageFormatter.leaveMessage(fromNotifyClaim));
        }
        if (toNotifyClaim != null) {
            if (profileService.getOrCreate(player.getUniqueId(), player.getName()).autoShowBorders()) {
                claimVisualService.showClaim(player, toNotifyClaim);
            }
            sendActionBar(player, entryMessageFormatter.enterMessage(player, toNotifyClaim));
        }
        cleanupSession(playerId, session);
        return false;
    }

    private void synchronizePlayer(Player player, String reason) {
        if (player == null || !player.isOnline()) {
            return;
        }
        Claim claim = claimService.findPlayerPresenceClaim(player.getLocation()).orElse(null);
        UUID playerId = player.getUniqueId();
        PlayerFlightSession session = flightSessions.computeIfAbsent(playerId, ignored -> new PlayerFlightSession());
        session.currentClaimId = claim == null ? null : claim.id();
        updateFlightState(player, session, claim, reason);
        updateClaimTime(player, session, claim);
        cleanupSession(playerId, session);
    }

    private void handleDisconnect(Player player, String reason) {
        if (player == null) {
            return;
        }
        UUID playerId = player.getUniqueId();
        blockedEntryNotices.remove(playerId);
        PlayerFlightSession session = flightSessions.get(playerId);
        if (session == null) {
            return;
        }
        if (session.managingClaimFlight) {
            revokeManagedFlight(player, session, reason);
        } else {
            cancelGrace(session);
        }
        resetClaimTime(player, session);
        cleanupSession(playerId, session);
        flightSessions.remove(playerId, session);
    }

    private void notifyBlockedEntry(Player player, UUID playerId, Claim claim) {
        long nowMillis = System.currentTimeMillis();
        BlockedEntryNotice previous = blockedEntryNotices.get(playerId);
        if (!shouldShowBlockedEntryNotice(previous, claim.id(), nowMillis)) {
            return;
        }
        blockedEntryNotices.put(playerId, new BlockedEntryNotice(claim.id(), nowMillis));
        player.sendMessage(plugin.message("claim-entry-denied", "{name}", claim.name()));
        claimVisualService.showClaim(player, claim);
    }

    static boolean shouldShowBlockedEntryNotice(BlockedEntryNotice previous, int claimId, long nowMillis) {
        return previous == null
            || previous.claimId() != claimId
            || nowMillis - previous.notifiedAtMillis() >= BLOCKED_ENTRY_NOTICE_COOLDOWN_MILLIS;
    }

    private void updateFlightState(Player player, PlayerFlightSession session, Claim claim, String reason) {
        if (player.getGameMode() == GameMode.CREATIVE || player.getGameMode() == GameMode.SPECTATOR) {
            clearManagedFlight(session);
            debugFlight(player, reason + "-native-flight-mode", claim, session);
            return;
        }

        boolean shouldAllow = canUseClaimFlight(player, claim);
        if (shouldAllow) {
            cancelGrace(session);
            if (!session.managingClaimFlight) {
                if (player.getAllowFlight()) {
                    debugFlight(player, reason + "-already-allowed", claim, session);
                    return;
                }
                session.beginManagedFlight(player.getAllowFlight(), player.isFlying());
                player.setAllowFlight(true);
                player.setFallDistance(0F);
                debugFlight(player, reason + "-grant", claim, session);
                return;
            }
            if (!player.getAllowFlight()) {
                player.setAllowFlight(true);
            }
            debugFlight(player, reason + "-keep", claim, session);
            return;
        }

        if (!session.managingClaimFlight) {
            cancelGrace(session);
            debugFlight(player, reason + "-no-managed-flight", claim, session);
            return;
        }

        if (session.graceActive) {
            if (!player.isFlying()) {
                revokeManagedFlight(player, session, reason + "-grace-landed");
            } else {
                debugFlight(player, reason + "-grace-active", claim, session);
            }
            return;
        }

        long graceTicks = plugin.settings().flightExitGraceTicks();
        if (player.isFlying() && graceTicks > 0L) {
            startGrace(player, session, graceTicks, claim, reason);
            return;
        }

        revokeManagedFlight(player, session, reason + "-revoke");
    }

    private void updateClaimTime(Player player, PlayerFlightSession session, Claim claim) {
        ClaimFlagState timeState = claim == null ? ClaimFlagState.UNSET : claim.flagState(ClaimFlag.TIME_CYCLE);
        if (timeState == ClaimFlagState.ALLOW) {
            applyClaimTime(player, session, CLAIM_DAY_TIME);
            return;
        }
        if (timeState == ClaimFlagState.DENY) {
            applyClaimTime(player, session, CLAIM_NIGHT_TIME);
            return;
        }
        resetClaimTime(player, session);
    }

    private void applyClaimTime(Player player, PlayerFlightSession session, long playerTime) {
        player.setPlayerTime(playerTime, false);
        session.managingClaimTime = true;
    }

    private void resetClaimTime(Player player, PlayerFlightSession session) {
        if (session.managingClaimTime) {
            player.resetPlayerTime();
            session.managingClaimTime = false;
        }
    }

    private void startGrace(Player player, PlayerFlightSession session, long graceTicks, Claim claim, String reason) {
        cancelGrace(session);
        session.graceActive = true;
        debugFlight(player, reason + "-start-grace", claim, session);
        session.graceTask = plugin.platformScheduler().runPlayerLater(player, () -> expireGrace(player), graceTicks);
    }

    private void expireGrace(Player player) {
        if (player == null || !player.isOnline()) {
            return;
        }
        UUID playerId = player.getUniqueId();
        PlayerFlightSession session = flightSessions.get(playerId);
        if (session == null || !session.managingClaimFlight || !session.graceActive) {
            return;
        }

        session.graceActive = false;
        session.graceTask = null;

        Claim claim = claimService.findPlayerPresenceClaim(player.getLocation()).orElse(null);
        session.currentClaimId = claim == null ? null : claim.id();
        if (canUseClaimFlight(player, claim)) {
            debugFlight(player, "grace-expired-but-still-allowed", claim, session);
            cleanupSession(playerId, session);
            return;
        }

        revokeManagedFlight(player, session, "grace-expired");
        cleanupSession(playerId, session);
    }

    private void revokeManagedFlight(Player player, PlayerFlightSession session, String reason) {
        if (!session.managingClaimFlight) {
            cancelGrace(session);
            return;
        }
        cancelGrace(session);
        if (player.isFlying() && !(session.baselineAllowFlight && session.baselineFlying)) {
            player.setFlying(false);
        }
        player.setAllowFlight(session.baselineAllowFlight);
        if (session.baselineAllowFlight && session.baselineFlying && !player.isFlying()) {
            player.setFlying(true);
        } else {
            player.setFallDistance(0F);
        }
        debugFlight(player, reason, resolveCurrentClaim(session, player.getLocation()), session);
        clearManagedFlight(session);
    }

    private void cancelGrace(PlayerFlightSession session) {
        PlatformScheduler.TaskHandle graceTask = session.graceTask;
        if (graceTask != null) {
            graceTask.cancel();
        }
        session.graceTask = null;
        session.graceActive = false;
    }

    private void clearManagedFlight(PlayerFlightSession session) {
        cancelGrace(session);
        session.managingClaimFlight = false;
        session.baselineAllowFlight = false;
        session.baselineFlying = false;
    }

    private boolean canUseClaimFlight(Player player, Claim claim) {
        return claim != null && claimService.hasPermission(claim, player.getUniqueId(), ClaimPermission.FLIGHT);
    }

    private boolean isBlockedEntry(Player player, Claim claim) {
        return claim != null
            && !claim.owner().equals(player.getUniqueId())
            && !AdminAccess.hasForceBypass(player)
            && (claim.isDenied(player.getUniqueId()) || (claim.denyAll() && !claim.isTrusted(player.getUniqueId())));
    }

    private Claim resolveCurrentClaim(PlayerFlightSession session, Location location) {
        if (session.currentClaimId == null) {
            return location == null ? null : claimService.findPlayerPresenceClaim(location).orElse(null);
        }
        Claim currentClaim = claimService.findClaimById(session.currentClaimId).orElse(null);
        if (currentClaim != null && (location == null || currentClaim.contains(location))) {
            return currentClaim;
        }
        if (location == null) {
            session.currentClaimId = null;
            return null;
        }
        Claim resolved = claimService.findPlayerPresenceClaim(location).orElse(null);
        session.currentClaimId = resolved == null ? null : resolved.id();
        return resolved;
    }

    private Claim resolveTargetClaim(PlayerFlightSession session, Claim currentClaim, Location to) {
        if (to == null) {
            return currentClaim;
        }
        if (currentClaim != null && currentClaim.contains(to)) {
            return currentClaim;
        }
        Claim resolved = claimService.findPlayerPresenceClaim(to).orElse(null);
        if (resolved != null) {
            session.currentClaimId = resolved.id();
        }
        return resolved;
    }

    private Claim resolveNotifyClaim(Location location) {
        return location == null ? null : claimService.findClaim(location).orElse(null);
    }

    record BlockedEntryNotice(int claimId, long notifiedAtMillis) {
    }

    private void cleanupSession(UUID playerId, PlayerFlightSession session) {
        if (session.currentClaimId == null && !session.managingClaimFlight && !session.managingClaimTime && !session.graceActive) {
            flightSessions.remove(playerId, session);
        }
    }

    private boolean sameBlock(Location from, Location to) {
        return from.getWorld() == to.getWorld()
            && from.getBlockX() == to.getBlockX()
            && from.getBlockY() == to.getBlockY()
            && from.getBlockZ() == to.getBlockZ();
    }

    private void debugFlight(Player player, String reason, Claim claim, PlayerFlightSession session) {
        if (!plugin.settings().flightDebug()) {
            return;
        }
        plugin.getLogger().info("[ClaimFlight] player=" + player.getName()
            + " reason=" + reason
            + " claim=" + (claim == null ? "none" : "#" + claim.id() + ":" + claim.name())
            + " allowFlight=" + player.getAllowFlight()
            + " flying=" + player.isFlying()
            + " managed=" + session.managingClaimFlight
            + " grace=" + session.graceActive
            + " baselineAllowFlight=" + session.baselineAllowFlight
            + " baselineFlying=" + session.baselineFlying);
    }

}
