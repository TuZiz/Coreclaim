package com.coreclaim.listener;

import com.coreclaim.CoreClaimPlugin;
import com.coreclaim.service.RemovalConfirmationService;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;

public final class RemovalConfirmListener implements Listener {

    private final CoreClaimPlugin plugin;
    private final RemovalConfirmationService removalConfirmationService;

    public RemovalConfirmListener(CoreClaimPlugin plugin, RemovalConfirmationService removalConfirmationService) {
        this.plugin = plugin;
        this.removalConfirmationService = removalConfirmationService;
    }

    @EventHandler(ignoreCancelled = true)
    public void onChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        if (!removalConfirmationService.hasPending(player.getUniqueId())) {
            return;
        }

        String message = event.getMessage().trim();
        if (!isConfirmDelete(message) && !isCancel(message)) {
            return;
        }

        event.setCancelled(true);
        plugin.platformScheduler().runPlayerTask(player, () -> {
            if (isConfirmDelete(message)) {
                removalConfirmationService.confirm(player);
            } else if (removalConfirmationService.cancel(player)) {
                player.sendMessage(plugin.message("claim-remove-cancelled"));
            }
        });
    }

    private boolean isConfirmDelete(String message) {
        return "确认删除".equals(message) || "confirm".equalsIgnoreCase(message);
    }

    private boolean isCancel(String message) {
        return "取消".equals(message) || "cancel".equalsIgnoreCase(message);
    }
}
