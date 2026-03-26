package com.coreclaim.listener;

import com.coreclaim.CoreClaimPlugin;
import com.coreclaim.gui.MenuService;
import com.coreclaim.model.Claim;
import com.coreclaim.service.ClaimActionService;
import com.coreclaim.service.ClaimService;
import com.coreclaim.service.PendingClaimService;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.player.PlayerInteractEvent;

public final class ClaimCoreInteractionListener implements Listener {

    private final CoreClaimPlugin plugin;
    private final ClaimService claimService;
    private final PendingClaimService pendingClaimService;
    private final ClaimActionService claimActionService;
    private final MenuService menuService;

    public ClaimCoreInteractionListener(
        CoreClaimPlugin plugin,
        ClaimService claimService,
        PendingClaimService pendingClaimService,
        ClaimActionService claimActionService,
        MenuService menuService
    ) {
        this.plugin = plugin;
        this.claimService = claimService;
        this.pendingClaimService = pendingClaimService;
        this.claimActionService = claimActionService;
        this.menuService = menuService;
    }

    @EventHandler(ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getClickedBlock() == null) {
            return;
        }
        Block block = event.getClickedBlock();
        if (block.getType() != plugin.settings().coreMaterial()) {
            return;
        }

        Player player = event.getPlayer();
        Claim claim = claimService.findClaim(block.getLocation())
            .filter(found -> found.centerX() == block.getX() && found.centerY() == block.getY() && found.centerZ() == block.getZ())
            .orElse(null);
        if (claim != null) {
            event.setCancelled(true);
            if (!claim.owner().equals(player.getUniqueId()) && !player.hasPermission("coreclaim.admin")) {
                player.sendMessage(plugin.message("trust-no-permission"));
                return;
            }
            menuService.openCoreMenu(player, claim);
            player.sendMessage(plugin.message("claim-core-info-opened"));
            return;
        }
        if (pendingClaimService.hasPendingClaim(player.getUniqueId())) {
            event.setCancelled(true);
            player.sendMessage(plugin.message("claim-name-prompt"));
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        if (block.getType() != plugin.settings().coreMaterial()) {
            return;
        }

        Claim claim = claimService.findClaim(block.getLocation())
            .filter(found -> found.centerX() == block.getX() && found.centerY() == block.getY() && found.centerZ() == block.getZ())
            .orElse(null);
        if (claim == null) {
            return;
        }

        Player player = event.getPlayer();
        event.setDropItems(false);
        event.setExpToDrop(0);
        claimActionService.hideClaimCore(player, claim);
    }
}
