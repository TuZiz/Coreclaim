package com.coreclaim.listener;

import com.coreclaim.CoreClaimPlugin;
import com.coreclaim.model.Claim;
import com.coreclaim.service.ClaimService;
import com.coreclaim.service.ClaimVisualService;
import com.coreclaim.service.ProfileService;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerTeleportEvent;

public final class ClaimEnterLeaveListener implements Listener {

    private final CoreClaimPlugin plugin;
    private final ClaimService claimService;
    private final ProfileService profileService;
    private final ClaimVisualService claimVisualService;
    private final Map<UUID, FlightState> managedFlight = new ConcurrentHashMap<>();

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
        updateFlight(event.getPlayer(), claim);
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
        if (handleTransition(event.getPlayer(), event.getFrom(), event.getTo())) {
            event.setTo(event.getFrom());
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        if (event.getTo() == null) {
            return;
        }
        if (handleTransition(event.getPlayer(), event.getFrom(), event.getTo())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onChangedWorld(PlayerChangedWorldEvent event) {
        Claim claim = claimService.findClaim(event.getPlayer().getLocation()).orElse(null);
        updateFlight(event.getPlayer(), claim);
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) {
        if (event.getRespawnLocation() == null) {
            return;
        }
        plugin.platformScheduler().runPlayerTask(event.getPlayer(), () -> {
            Claim claim = claimService.findClaim(event.getPlayer().getLocation()).orElse(null);
            updateFlight(event.getPlayer(), claim);
        });
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        managedFlight.remove(event.getPlayer().getUniqueId());
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

    private boolean handleTransition(Player player, org.bukkit.Location from, org.bukkit.Location to) {
        Claim fromClaim = claimService.findClaim(from).orElse(null);
        Claim toClaim = claimService.findClaim(to).orElse(null);
        int fromId = fromClaim == null ? -1 : fromClaim.id();
        int toId = toClaim == null ? -1 : toClaim.id();
        if (fromId == toId) {
            updateFlight(player, toClaim);
            return false;
        }

        if (toClaim != null
            && !toClaim.owner().equals(player.getUniqueId())
            && toClaim.isBlacklisted(player.getUniqueId())
            && !player.hasPermission("coreclaim.admin")) {
            player.sendMessage(plugin.message("blacklist-deny-enter", "{name}", toClaim.name()));
            return true;
        }

        updateFlight(player, toClaim);

        if (!plugin.getConfig().getBoolean("show-enter-leave-messages", true)) {
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
        return false;
    }

    private void updateFlight(Player player, Claim claim) {
        if (player == null) {
            return;
        }
        UUID playerId = player.getUniqueId();
        if (player.getGameMode() == GameMode.CREATIVE || player.getGameMode() == GameMode.SPECTATOR) {
            managedFlight.remove(playerId);
            return;
        }

        boolean shouldEnable = claim != null && claimService.canAccess(claim, playerId);
        FlightState previous = managedFlight.get(playerId);

        if (shouldEnable) {
            if (previous == null) {
                managedFlight.put(playerId, new FlightState(player.getAllowFlight(), player.isFlying()));
            }
            if (!player.getAllowFlight()) {
                player.setAllowFlight(true);
            }
            if (!player.isFlying()) {
                player.setFlying(true);
            }
            player.setFallDistance(0F);
            return;
        }

        if (previous == null) {
            return;
        }
        managedFlight.remove(playerId);
        if (player.isFlying()) {
            player.setFlying(false);
        }
        player.setAllowFlight(previous.allowFlight());
        if (previous.allowFlight() && previous.flying()) {
            player.setFlying(true);
        } else {
            player.setFallDistance(0F);
        }
    }

    private record FlightState(boolean allowFlight, boolean flying) {
    }
}
