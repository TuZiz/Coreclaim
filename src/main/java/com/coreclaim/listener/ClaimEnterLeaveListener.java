package com.coreclaim.listener;

import com.coreclaim.CoreClaimPlugin;
import com.coreclaim.model.Claim;
import com.coreclaim.model.ClaimPermission;
import com.coreclaim.platform.PlatformScheduler;
import com.coreclaim.service.ClaimService;
import com.coreclaim.service.ClaimVisualService;
import com.coreclaim.service.ProfileService;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerGameModeChangeEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.player.PlayerToggleFlightEvent;

public final class ClaimEnterLeaveListener implements Listener {

    private final CoreClaimPlugin plugin;
    private final ClaimService claimService;
    private final ProfileService profileService;
    private final ClaimVisualService claimVisualService;
    private final Map<UUID, PlayerFlightSession> flightSessions = new ConcurrentHashMap<>();

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
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Claim claim = claimService.findClaim(event.getPlayer().getLocation()).orElse(null);
        synchronizePlayer(event.getPlayer(), claim);
    }

    @EventHandler(ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        if (event.getTo() == null) {
            return;
        }
        if (event.getFrom().getBlockX() == event.getTo().getBlockX()
            && event.getFrom().getBlockY() == event.getTo().getBlockY()
            && event.getFrom().getBlockZ() == event.getTo().getBlockZ()
            && event.getFrom().getWorld() == event.getTo().getWorld()) {
            return;
        }
        if (handleLocationChange(event.getPlayer(), event.getFrom(), event.getTo())) {
            event.setTo(event.getFrom());
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        if (event.getTo() == null) {
            return;
        }
        if (handleLocationChange(event.getPlayer(), event.getFrom(), event.getTo())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onChangedWorld(PlayerChangedWorldEvent event) {
        Claim claim = claimService.findClaim(event.getPlayer().getLocation()).orElse(null);
        synchronizePlayer(event.getPlayer(), claim);
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) {
        if (event.getRespawnLocation() == null) {
            return;
        }
        plugin.platformScheduler().runPlayerTask(event.getPlayer(), () -> {
            Claim claim = claimService.findClaim(event.getPlayer().getLocation()).orElse(null);
            synchronizePlayer(event.getPlayer(), claim);
        });
    }

    @EventHandler
    public void onGameModeChange(PlayerGameModeChangeEvent event) {
        plugin.platformScheduler().runPlayerTask(event.getPlayer(), () -> {
            Claim claim = claimService.findClaim(event.getPlayer().getLocation()).orElse(null);
            synchronizePlayer(event.getPlayer(), claim);
        });
    }

    @EventHandler(ignoreCancelled = true)
    public void onToggleFlight(PlayerToggleFlightEvent event) {
        if (!event.isFlying()) {
            return;
        }
        Player player = event.getPlayer();
        PlayerFlightSession session = flightSessions.get(player.getUniqueId());
        if (session == null || !session.managingClaimFlight) {
            return;
        }

        Claim currentClaim = resolveCurrentClaim(session, player.getLocation());
        if (canUseClaimFlight(player, currentClaim)) {
            if (session.graceActive) {
                cancelGrace(session);
            }
            cleanupSession(player.getUniqueId(), session);
            return;
        }

        event.setCancelled(true);
        revokeManagedFlight(player, session);
        cleanupSession(player.getUniqueId(), session);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        PlayerFlightSession session = flightSessions.remove(event.getPlayer().getUniqueId());
        if (session != null) {
            cancelGrace(session);
        }
    }

    private void sendActionBar(Player player, String message) {
        player.spigot().sendMessage(ChatMessageType.ACTION_BAR, TextComponent.fromLegacyText(message));
    }

    private String enterMessage(Player player, Claim claim) {
        String custom = claim.enterMessage();
        if (custom != null && !custom.isBlank()) {
            return applyNotifyPlaceholders(custom, claim);
        }
        if (claim.owner().equals(player.getUniqueId())) {
            return plugin.message("enter-own-claim", "{name}", claim.name());
        }
        return plugin.message("enter-trusted-claim", "{owner}", claim.ownerName(), "{name}", claim.name());
    }

    private String leaveMessage(Claim claim) {
        String custom = claim.leaveMessage();
        if (custom != null && !custom.isBlank()) {
            return applyNotifyPlaceholders(custom, claim);
        }
        return plugin.message("leave-claim", "{name}", claim.name());
    }

    private String applyNotifyPlaceholders(String text, Claim claim) {
        return plugin.color(text
            .replace("%claim_name%", claim.name())
            .replace("{claim_name}", claim.name())
            .replace("{name}", claim.name())
            .replace("%owner%", claim.ownerName())
            .replace("{owner}", claim.ownerName()));
    }

    private boolean handleLocationChange(Player player, Location from, Location to) {
        UUID playerId = player.getUniqueId();
        PlayerFlightSession session = flightSessions.computeIfAbsent(playerId, ignored -> new PlayerFlightSession());
        Claim fromClaim = resolveCurrentClaim(session, from);
        Claim toClaim = resolveTargetClaim(session, fromClaim, to);
        int fromId = fromClaim == null ? -1 : fromClaim.id();
        int toId = toClaim == null ? -1 : toClaim.id();

        if (fromId != toId && isBlockedEntry(player, toClaim)) {
            player.sendMessage(plugin.message("blacklist-deny-enter", "{name}", toClaim.name()));
            cleanupSession(playerId, session);
            return true;
        }

        session.currentClaimId = toClaim == null ? null : toClaim.id();
        updateFlightState(player, session, toClaim);

        if (fromId == toId) {
            cleanupSession(playerId, session);
            return false;
        }

        if (!plugin.getConfig().getBoolean("show-enter-leave-messages", true)) {
            cleanupSession(playerId, session);
            return false;
        }
        if (fromClaim != null) {
            sendActionBar(player, leaveMessage(fromClaim));
        }
        if (toClaim != null) {
            if (profileService.getOrCreate(player.getUniqueId(), player.getName()).autoShowBorders()) {
                claimVisualService.showClaim(player, toClaim);
            }
            sendActionBar(player, enterMessage(player, toClaim));
        }
        cleanupSession(playerId, session);
        return false;
    }

    private void synchronizePlayer(Player player, Claim claim) {
        if (player == null || !player.isOnline()) {
            return;
        }
        UUID playerId = player.getUniqueId();
        PlayerFlightSession session = flightSessions.computeIfAbsent(playerId, ignored -> new PlayerFlightSession());
        session.currentClaimId = claim == null ? null : claim.id();
        updateFlightState(player, session, claim);
        cleanupSession(playerId, session);
    }

    private void updateFlightState(Player player, PlayerFlightSession session, Claim claim) {
        if (player.getGameMode() == GameMode.CREATIVE || player.getGameMode() == GameMode.SPECTATOR) {
            clearManagedFlight(session);
            return;
        }

        boolean shouldAllow = canUseClaimFlight(player, claim);
        if (shouldAllow) {
            cancelGrace(session);
            if (!session.managingClaimFlight) {
                if (!player.getAllowFlight()) {
                    session.beginManagedFlight(player.getAllowFlight(), player.isFlying());
                    player.setAllowFlight(true);
                    player.setFallDistance(0F);
                }
                return;
            }
            if (!player.getAllowFlight()) {
                player.setAllowFlight(true);
            }
            return;
        }

        if (!session.managingClaimFlight) {
            cancelGrace(session);
            return;
        }

        if (session.graceActive) {
            if (!player.isFlying()) {
                revokeManagedFlight(player, session);
            }
            return;
        }

        long graceTicks = plugin.settings().flightExitGraceTicks();
        if (player.isFlying() && graceTicks > 0L) {
            startGrace(player, session, graceTicks);
            return;
        }

        revokeManagedFlight(player, session);
    }

    private void startGrace(Player player, PlayerFlightSession session, long graceTicks) {
        if (session.graceActive) {
            return;
        }
        cancelGrace(session);
        session.graceActive = true;
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

        Claim claim = claimService.findClaim(player.getLocation()).orElse(null);
        session.currentClaimId = claim == null ? null : claim.id();
        if (canUseClaimFlight(player, claim)) {
            cleanupSession(playerId, session);
            return;
        }

        revokeManagedFlight(player, session);
        cleanupSession(playerId, session);
    }

    private void revokeManagedFlight(Player player, PlayerFlightSession session) {
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
            && claim.isBlacklisted(player.getUniqueId())
            && !player.hasPermission("coreclaim.admin");
    }

    private Claim resolveCurrentClaim(PlayerFlightSession session, Location location) {
        if (session.currentClaimId == null) {
            return null;
        }
        Claim currentClaim = claimService.findClaimById(session.currentClaimId).orElse(null);
        if (currentClaim != null && (location == null || currentClaim.contains(location))) {
            return currentClaim;
        }
        if (location == null) {
            session.currentClaimId = null;
            return null;
        }
        Claim resolved = claimService.findClaim(location).orElse(null);
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
        Claim resolved = claimService.findClaim(to).orElse(null);
        if (resolved != null) {
            session.currentClaimId = resolved.id();
        }
        return resolved;
    }

    private void cleanupSession(UUID playerId, PlayerFlightSession session) {
        if (session.currentClaimId == null && !session.managingClaimFlight && !session.graceActive) {
            flightSessions.remove(playerId, session);
        }
    }

    private static final class PlayerFlightSession {
        private Integer currentClaimId;
        private boolean managingClaimFlight;
        private boolean baselineAllowFlight;
        private boolean baselineFlying;
        private boolean graceActive;
        private PlatformScheduler.TaskHandle graceTask;

        private void beginManagedFlight(boolean baselineAllowFlight, boolean baselineFlying) {
            this.managingClaimFlight = true;
            this.baselineAllowFlight = baselineAllowFlight;
            this.baselineFlying = baselineFlying;
        }
    }
}
