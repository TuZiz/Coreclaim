package com.coreclaim.listener;

import com.coreclaim.CoreClaimPlugin;
import com.coreclaim.model.Claim;
import com.coreclaim.service.ClaimService;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public final class ClaimEnterLeaveListener implements Listener {

    private final CoreClaimPlugin plugin;
    private final ClaimService claimService;

    public ClaimEnterLeaveListener(CoreClaimPlugin plugin, ClaimService claimService) {
        this.plugin = plugin;
        this.claimService = claimService;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        claimService.findClaim(event.getPlayer().getLocation());
    }

    @EventHandler(ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        if (event.getTo() == null) {
            return;
        }
        if (!plugin.getConfig().getBoolean("show-enter-leave-messages", true)) {
            return;
        }
        if (event.getFrom().getBlockX() == event.getTo().getBlockX()
            && event.getFrom().getBlockY() == event.getTo().getBlockY()
            && event.getFrom().getBlockZ() == event.getTo().getBlockZ()
            && event.getFrom().getWorld() == event.getTo().getWorld()) {
            return;
        }

        Claim fromClaim = claimService.findClaim(event.getFrom()).orElse(null);
        Claim toClaim = claimService.findClaim(event.getTo()).orElse(null);
        int fromId = fromClaim == null ? -1 : fromClaim.id();
        int toId = toClaim == null ? -1 : toClaim.id();
        if (fromId == toId) {
            return;
        }

        Player player = event.getPlayer();
        if (fromClaim != null) {
            sendActionBar(player, plugin.message("leave-claim", "{name}", fromClaim.name()));
        }
        if (toClaim != null) {
            if (toClaim.owner().equals(player.getUniqueId())) {
                sendActionBar(player, plugin.message("enter-own-claim", "{name}", toClaim.name()));
            } else {
                sendActionBar(player, plugin.message(
                    "enter-trusted-claim",
                    "{owner}", toClaim.ownerName(),
                    "{name}", toClaim.name()
                ));
            }
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        // no-op, reserved for future caching
    }

    private void sendActionBar(Player player, String message) {
        player.spigot().sendMessage(ChatMessageType.ACTION_BAR, TextComponent.fromLegacyText(message));
    }
}
