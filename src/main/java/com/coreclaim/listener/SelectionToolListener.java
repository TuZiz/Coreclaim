package com.coreclaim.listener;

import com.coreclaim.service.ClaimSelectionService;
import com.coreclaim.service.OnlineRewardService;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;

public final class SelectionToolListener implements Listener {

    private final ClaimSelectionService claimSelectionService;
    private final OnlineRewardService onlineRewardService;

    public SelectionToolListener(ClaimSelectionService claimSelectionService, OnlineRewardService onlineRewardService) {
        this.claimSelectionService = claimSelectionService;
        this.onlineRewardService = onlineRewardService;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        onlineRewardService.handleJoin(event.getPlayer());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        claimSelectionService.clear(event.getPlayer());
        onlineRewardService.handleQuit(event.getPlayer());
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        claimSelectionService.clear(event.getEntity());
    }

    @EventHandler
    public void onChangedWorld(PlayerChangedWorldEvent event) {
        claimSelectionService.clear(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPrepareCraft(PrepareItemCraftEvent event) {
        if (event.getInventory() == null) {
            return;
        }
        ItemStack result = event.getInventory().getResult();
        if (!claimSelectionService.canUseSelectionTool(result)) {
            return;
        }
        event.getInventory().setResult(claimSelectionService.normalizeSelectionTool(result));
    }
}
