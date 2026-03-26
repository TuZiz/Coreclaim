package com.coreclaim.listener;

import com.coreclaim.CoreClaimPlugin;
import com.coreclaim.service.ClaimInputService;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public final class ClaimInputListener implements Listener {

    private final CoreClaimPlugin plugin;
    private final ClaimInputService claimInputService;

    public ClaimInputListener(CoreClaimPlugin plugin, ClaimInputService claimInputService) {
        this.plugin = plugin;
        this.claimInputService = claimInputService;
    }

    @EventHandler(ignoreCancelled = true)
    public void onChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        if (!claimInputService.hasPending(player.getUniqueId())) {
            return;
        }
        event.setCancelled(true);
        String message = event.getMessage();
        plugin.platformScheduler().runPlayerTask(player, () -> claimInputService.handleInput(player, message));
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        claimInputService.cancel(event.getPlayer(), false);
    }
}
