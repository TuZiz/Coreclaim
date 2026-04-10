package com.coreclaim.listener;

import com.coreclaim.CoreClaimPlugin;
import com.coreclaim.service.ClaimSelectionService;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;

public final class SelectionToolListener implements Listener {

    private final CoreClaimPlugin plugin;
    private final ClaimSelectionService claimSelectionService;

    public SelectionToolListener(CoreClaimPlugin plugin, ClaimSelectionService claimSelectionService) {
        this.plugin = plugin;
        this.claimSelectionService = claimSelectionService;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        plugin.platformScheduler().runPlayerTask(event.getPlayer(), () -> claimSelectionService.normalizePlayerInventoryAndCursor(event.getPlayer()));
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        claimSelectionService.clear(event.getPlayer());
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
        if (!claimSelectionService.isSelectionToolCandidate(result)) {
            return;
        }
        event.getInventory().setResult(claimSelectionService.normalizeSelectionTool(result));
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onCraft(CraftItemEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        if (!claimSelectionService.isSelectionToolCandidate(event.getCurrentItem())) {
            return;
        }
        plugin.platformScheduler().runPlayerTask(player, () -> claimSelectionService.normalizePlayerInventoryAndCursor(player));
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onPickup(EntityPickupItemEvent event) {
        if (!(event.getEntity() instanceof Player)) {
            return;
        }
        Item itemEntity = event.getItem();
        ItemStack stack = itemEntity.getItemStack();
        if (!claimSelectionService.isSelectionToolCandidate(stack)) {
            return;
        }
        itemEntity.setItemStack(claimSelectionService.normalizeSelectionTool(stack));
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        if (claimSelectionService.isSelectionToolCandidate(event.getCurrentItem())
            || claimSelectionService.isSelectionToolCandidate(event.getCursor())) {
            plugin.platformScheduler().runPlayerTask(player, () -> claimSelectionService.normalizePlayerInventoryAndCursor(player));
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        if (!claimSelectionService.isSelectionToolCandidate(event.getOldCursor())) {
            return;
        }
        plugin.platformScheduler().runPlayerTask(player, () -> claimSelectionService.normalizePlayerInventoryAndCursor(player));
    }
}
