package com.coreclaim.listener;

import com.coreclaim.CoreClaimPlugin;
import com.coreclaim.service.PendingClaimService;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public final class ClaimNamingListener implements Listener {

    private final CoreClaimPlugin plugin;
    private final PendingClaimService pendingClaimService;

    public ClaimNamingListener(CoreClaimPlugin plugin, PendingClaimService pendingClaimService) {
        this.plugin = plugin;
        this.pendingClaimService = pendingClaimService;
    }

    @EventHandler(ignoreCancelled = true)
    public void onChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        if (!pendingClaimService.hasPendingClaim(player.getUniqueId())) {
            return;
        }
        event.setCancelled(true);
        String message = event.getMessage().trim();
        plugin.platformScheduler().runPlayerTask(player, () -> {
            if ("cancel".equalsIgnoreCase(message)) {
                pendingClaimService.cancelPendingClaim(player, true);
            } else {
                pendingClaimService.completeClaim(player, message);
            }
        });
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        pendingClaimService.cancelPendingClaim(event.getPlayer(), false);
    }
}

