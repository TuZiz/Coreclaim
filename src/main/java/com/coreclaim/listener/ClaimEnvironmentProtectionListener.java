package com.coreclaim.listener;

import com.coreclaim.model.Claim;
import com.coreclaim.model.ClaimPermission;
import com.coreclaim.service.ClaimService;
import java.util.Iterator;
import java.util.Optional;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.block.data.Directional;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.entity.Tameable;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockDispenseEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.event.block.BlockIgniteEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.block.BlockSpreadEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityTargetLivingEntityEvent;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.inventory.InventoryPickupItemEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

public final class ClaimEnvironmentProtectionListener implements Listener {

    private final ClaimService claimService;

    public ClaimEnvironmentProtectionListener(ClaimService claimService) {
        this.claimService = claimService;
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        Iterator<Block> iterator = event.blockList().iterator();
        while (iterator.hasNext()) {
            if (claimService.findClaim(iterator.next().getLocation()).isPresent()) {
                iterator.remove();
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onFlow(BlockFromToEvent event) {
        if (crossesClaimBoundary(event.getBlock().getLocation(), event.getToBlock().getLocation())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onPistonExtend(BlockPistonExtendEvent event) {
        for (Block block : event.getBlocks()) {
            if (crossesClaimBoundary(block.getLocation(), block.getRelative(event.getDirection()).getLocation())) {
                event.setCancelled(true);
                return;
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onPistonRetract(BlockPistonRetractEvent event) {
        for (Block block : event.getBlocks()) {
            if (crossesClaimBoundary(block.getLocation(), block.getRelative(event.getDirection()).getLocation())) {
                event.setCancelled(true);
                return;
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onEntityChangeBlock(EntityChangeBlockEvent event) {
        if (claimService.findClaim(event.getBlock().getLocation()).isPresent()) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockIgnite(BlockIgniteEvent event) {
        Optional<Claim> claim = claimService.findClaim(event.getBlock().getLocation());
        if (claim.isEmpty()) {
            return;
        }
        Player player = event.getPlayer();
        if (player == null || !claimService.hasPermission(claim.get(), player.getUniqueId(), com.coreclaim.model.ClaimPermission.INTERACT)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockBurn(BlockBurnEvent event) {
        if (claimService.findClaim(event.getBlock().getLocation()).isPresent()) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockSpread(BlockSpreadEvent event) {
        if (crossesClaimBoundary(event.getSource().getLocation(), event.getBlock().getLocation())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onInventoryMove(InventoryMoveItemEvent event) {
        Location source = inventoryLocation(event.getSource());
        Location destination = inventoryLocation(event.getDestination());
        if (source == null || destination == null) {
            return;
        }
        if (crossesClaimBoundary(source, destination)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onInventoryPickup(InventoryPickupItemEvent event) {
        Location inventoryLocation = inventoryLocation(event.getInventory());
        Location itemLocation = event.getItem().getLocation();
        if (inventoryLocation == null) {
            return;
        }
        if (crossesClaimBoundary(itemLocation, inventoryLocation)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onDispense(BlockDispenseEvent event) {
        BlockFace facing = event.getBlock().getBlockData() instanceof Directional directional
            ? directional.getFacing()
            : BlockFace.SELF;
        Location target = event.getBlock().getRelative(facing).getLocation();
        if (crossesClaimBoundary(event.getBlock().getLocation(), target)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onInventoryOpen(InventoryOpenEvent event) {
        if (!(event.getPlayer() instanceof Player player) || player.hasPermission("coreclaim.admin")) {
            return;
        }
        Location location = inventoryLocation(event.getInventory());
        if (location == null) {
            return;
        }
        Optional<Claim> claim = claimService.findClaim(location);
        if (claim.isPresent() && !claimService.hasPermission(claim.get(), player.getUniqueId(), ClaimPermission.INTERACT)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onEntityTarget(EntityTargetLivingEntityEvent event) {
        if (!(event.getEntity() instanceof Mob mob)) {
            return;
        }
        if (mob instanceof Tameable tameable && tameable.getOwner() != null) {
            return;
        }
        if (event.getTarget() == null) {
            return;
        }
        Optional<Claim> targetClaim = claimService.findClaim(event.getTarget().getLocation());
        if (targetClaim.isEmpty()) {
            return;
        }
        Optional<Claim> sourceClaim = claimService.findClaim(event.getEntity().getLocation());
        if (claimId(sourceClaim) != claimId(targetClaim)) {
            event.setCancelled(true);
        }
    }

    private boolean crossesClaimBoundary(Location from, Location to) {
        Optional<Claim> fromClaim = claimService.findClaim(from);
        Optional<Claim> toClaim = claimService.findClaim(to);
        if (fromClaim.isEmpty() && toClaim.isEmpty()) {
            return false;
        }
        return claimId(fromClaim) != claimId(toClaim);
    }

    private int claimId(Optional<Claim> claim) {
        return claim.map(Claim::id).orElse(-1);
    }

    private Location inventoryLocation(Inventory inventory) {
        if (inventory == null) {
            return null;
        }
        try {
            Location location = inventory.getLocation();
            if (location != null) {
                return location;
            }
        } catch (Throwable ignored) {
        }
        InventoryHolder holder = inventory.getHolder();
        if (holder instanceof BlockState blockState) {
            return blockState.getLocation();
        }
        if (holder instanceof Entity entity) {
            return entity.getLocation();
        }
        return null;
    }
}
