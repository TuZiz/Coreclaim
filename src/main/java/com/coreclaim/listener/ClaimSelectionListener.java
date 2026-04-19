package com.coreclaim.listener;

import com.coreclaim.service.ClaimSelectionService;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

public final class ClaimSelectionListener implements Listener {

    private final ClaimSelectionService claimSelectionService;

    public ClaimSelectionListener(ClaimSelectionService claimSelectionService) {
        this.claimSelectionService = claimSelectionService;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND || event.getClickedBlock() == null) {
            return;
        }

        Player player = event.getPlayer();
        ItemStack mainHand = player.getInventory().getItemInMainHand();
        if (!claimSelectionService.canUseSelectionTool(mainHand)) {
            return;
        }

        if (event.getAction() == Action.LEFT_CLICK_BLOCK) {
            event.setCancelled(true);
            claimSelectionService.setFirstPoint(player, event.getClickedBlock().getLocation());
            return;
        }
        if (event.getAction() == Action.RIGHT_CLICK_BLOCK) {
            event.setCancelled(true);
            claimSelectionService.setSecondPoint(player, event.getClickedBlock().getLocation());
        }
    }
}
