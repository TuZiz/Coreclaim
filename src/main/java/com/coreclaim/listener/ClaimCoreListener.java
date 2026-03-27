package com.coreclaim.listener;

import com.coreclaim.CoreClaimPlugin;
import com.coreclaim.item.ClaimCoreFactory;
import com.coreclaim.service.PendingClaimService;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

public final class ClaimCoreListener implements Listener {

    private final CoreClaimPlugin plugin;
    private final ClaimCoreFactory claimCoreFactory;
    private final PendingClaimService pendingClaimService;

    public ClaimCoreListener(
        CoreClaimPlugin plugin,
        ClaimCoreFactory claimCoreFactory,
        PendingClaimService pendingClaimService
    ) {
        this.plugin = plugin;
        this.claimCoreFactory = claimCoreFactory;
        this.pendingClaimService = pendingClaimService;
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        ItemStack item = event.getItemInHand();
        if (!claimCoreFactory.isAnyClaimCore(item)) {
            return;
        }
        event.setCancelled(true);

        Player player = event.getPlayer();
        if (pendingClaimService.hasPendingClaim(player.getUniqueId())) {
            player.sendMessage(plugin.message("claim-name-prompt"));
            return;
        }

        boolean starterCore = claimCoreFactory.isStarterCore(item);
        if (!pendingClaimService.beginClaimCreation(player, event.getBlockPlaced().getLocation(), starterCore)) {
            return;
        }
        consumeCore(player, event.getHand());
    }

    private void consumeCore(Player player, EquipmentSlot hand) {
        ItemStack stack = hand == EquipmentSlot.OFF_HAND
            ? player.getInventory().getItemInOffHand()
            : player.getInventory().getItemInMainHand();
        if (!claimCoreFactory.isAnyClaimCore(stack)) {
            return;
        }
        if (stack.getAmount() <= 1) {
            if (hand == EquipmentSlot.OFF_HAND) {
                player.getInventory().setItemInOffHand(new ItemStack(Material.AIR));
            } else {
                player.getInventory().setItemInMainHand(new ItemStack(Material.AIR));
            }
            return;
        }
        stack.setAmount(stack.getAmount() - 1);
        if (hand == EquipmentSlot.OFF_HAND) {
            player.getInventory().setItemInOffHand(stack);
        } else {
            player.getInventory().setItemInMainHand(stack);
        }
    }
}
