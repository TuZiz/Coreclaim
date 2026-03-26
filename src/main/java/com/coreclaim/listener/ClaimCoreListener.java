package com.coreclaim.listener;

import com.coreclaim.CoreClaimPlugin;
import com.coreclaim.item.ClaimCoreFactory;
import com.coreclaim.service.PendingClaimService;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;
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
        if (!claimCoreFactory.isClaimCore(item)) {
            return;
        }

        Player player = event.getPlayer();
        if (pendingClaimService.hasPendingClaim(player.getUniqueId())) {
            event.setCancelled(true);
            player.sendMessage(plugin.message("claim-name-prompt"));
            return;
        }

        if (!pendingClaimService.beginClaimCreation(player, event.getBlockPlaced().getLocation())) {
            event.setCancelled(true);
        }
    }
}
