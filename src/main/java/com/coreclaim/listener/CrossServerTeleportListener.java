package com.coreclaim.listener;

import com.coreclaim.CoreClaimPlugin;
import com.coreclaim.service.CrossServerTeleportService;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

public final class CrossServerTeleportListener implements Listener {

    private final CoreClaimPlugin plugin;
    private final CrossServerTeleportService crossServerTeleportService;

    public CrossServerTeleportListener(CoreClaimPlugin plugin, CrossServerTeleportService crossServerTeleportService) {
        this.plugin = plugin;
        this.crossServerTeleportService = crossServerTeleportService;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        plugin.platformScheduler().runPlayerLater(
            event.getPlayer(),
            () -> crossServerTeleportService.consumePendingTeleport(event.getPlayer()),
            20L
        );
    }
}
