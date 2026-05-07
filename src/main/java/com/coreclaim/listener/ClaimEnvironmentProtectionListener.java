package com.coreclaim.listener;

import com.coreclaim.model.Claim;
import com.coreclaim.model.ClaimFlag;
import com.coreclaim.model.ClaimFlagState;
import com.coreclaim.model.ClaimPermission;
import com.coreclaim.cleanup.ClaimCleanupService;
import com.coreclaim.service.ClaimService;
import com.coreclaim.service.ExplosionAuthorizationService;
import com.coreclaim.util.AdminAccess;
import java.util.Iterator;
import java.util.Optional;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.block.data.Directional;
import org.bukkit.entity.Ambient;
import org.bukkit.entity.Animals;
import org.bukkit.entity.Bee;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Enemy;
import org.bukkit.entity.FallingBlock;
import org.bukkit.entity.Fox;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Minecart;
import org.bukkit.entity.Player;
import org.bukkit.entity.Tameable;
import org.bukkit.entity.Turtle;
import org.bukkit.entity.Villager;
import org.bukkit.entity.WaterMob;
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
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityTargetLivingEntityEvent;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.inventory.InventoryPickupItemEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

public final class ClaimEnvironmentProtectionListener implements Listener {

    private final ClaimService claimService;
    private final ExplosionAuthorizationService explosionAuthorizationService;
    private final ClaimCleanupService claimCleanupService;

    public ClaimEnvironmentProtectionListener(
        ClaimService claimService,
        ExplosionAuthorizationService explosionAuthorizationService,
        ClaimCleanupService claimCleanupService
    ) {
        this.claimService = claimService;
        this.explosionAuthorizationService = explosionAuthorizationService;
        this.claimCleanupService = claimCleanupService;
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        Optional<Claim> sourceClaim = claimService.findClaim(event.getBlock().getLocation());
        boolean authorized = explosionAuthorizationService.isAuthorized(event.getBlock().getLocation());
        Iterator<Block> iterator = event.blockList().iterator();
        while (iterator.hasNext()) {
            Optional<Claim> targetClaim = claimService.findClaim(iterator.next().getLocation());
            if (targetClaim.isEmpty()) {
                continue;
            }
            if (!authorized || sourceClaim.isEmpty() || claimId(sourceClaim) != claimId(targetClaim)) {
                iterator.remove();
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onFlow(BlockFromToEvent event) {
        if (isLiquidFlowMaterial(event.getBlock().getType())) {
            if (shouldCancelLiquidFlow(event.getBlock().getLocation(), event.getToBlock().getLocation())) {
                event.setCancelled(true);
            }
            return;
        }
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
        if (event.getEntity() instanceof FallingBlock fallingBlock) {
            Location source = fallingBlockSource(fallingBlock);
            if (crossesClaimBoundary(source, event.getBlock().getLocation())) {
                event.setCancelled(true);
            }
            return;
        }
        if (isNaturalTurtleEggPlacement(event)) {
            return;
        }
        if (event.getEntity() instanceof Villager
            && isVillagerFarmChange(event.getBlock().getType(), event.getTo())) {
            return;
        }
        if (event.getEntity() instanceof Fox
            && isFoxSweetBerryChange(event.getBlock().getType(), event.getTo())) {
            return;
        }
        if (event.getEntity() instanceof Bee
            && isBeeHiveStateChange(event.getBlock().getType(), event.getTo())) {
            return;
        }
        if (claimService.findClaim(event.getBlock().getLocation()).isPresent()) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onCreatureSpawn(CreatureSpawnEvent event) {
        if (!isManagedCreatureSpawnReason(event.getSpawnReason())) {
            return;
        }
        ClaimPermission permission = spawnPermissionForCreature(event.getEntity());
        if (permission == null) {
            return;
        }
        Optional<Claim> claim = claimService.findClaim(event.getLocation());
        if (claim.isPresent() && !claim.get().permission(permission)) {
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
        ClaimPermission permission = event.getBlock().getType() == org.bukkit.Material.TNT
            ? ClaimPermission.EXPLOSION
            : ClaimPermission.INTERACT;
        if (player == null || !claimService.hasPermission(claim.get(), player.getUniqueId(), permission)) {
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
        if (!(event.getPlayer() instanceof Player player) || AdminAccess.hasForceBypass(player)) {
            return;
        }
        Location location = inventoryLocation(event.getInventory());
        if (location == null) {
            return;
        }
        Optional<Claim> claim = claimService.findClaim(location);
        if (claim.isPresent()) {
            if (!claimService.hasPermission(claim.get(), player.getUniqueId(), ClaimPermission.INTERACT)) {
                event.setCancelled(true);
                return;
            }
            claimCleanupService.recordInteractionActivity(claim.get(), player.getUniqueId());
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

    private boolean shouldCancelLiquidFlow(Location from, Location to) {
        Optional<Claim> fromClaim = claimService.findClaim(from);
        Optional<Claim> toClaim = claimService.findClaim(to);
        if (toClaim.isEmpty() || claimId(fromClaim) == claimId(toClaim)) {
            return false;
        }
        return !isLiquidFlowAllowed(toClaim.get());
    }

    private boolean isLiquidFlowAllowed(Claim claim) {
        return isLiquidFlowAllowed(claim.flagState(ClaimFlag.LIQUID_FLOW));
    }

    private boolean isNaturalTurtleEggPlacement(EntityChangeBlockEvent event) {
        return event.getEntity() instanceof Turtle && event.getTo() == Material.TURTLE_EGG;
    }

    static boolean isVillagerFarmChange(Material from, Material to) {
        if (isVillagerCrop(from) && to == Material.AIR) {
            return true;
        }
        return isAirBlock(from) && isVillagerCrop(to);
    }

    static boolean isFoxSweetBerryChange(Material from, Material to) {
        if (from == Material.SWEET_BERRY_BUSH) {
            return to == Material.SWEET_BERRY_BUSH || isAirBlock(to);
        }
        if (from == Material.CAVE_VINES || from == Material.CAVE_VINES_PLANT) {
            return to == Material.CAVE_VINES
                || to == Material.CAVE_VINES_PLANT
                || isAirBlock(to);
        }
        return false;
    }

    static boolean isBeeHiveStateChange(Material from, Material to) {
        return (from == Material.BEEHIVE || from == Material.BEE_NEST) && from == to;
    }

    static boolean isManagedCreatureSpawnReason(CreatureSpawnEvent.SpawnReason reason) {
        return reason != CreatureSpawnEvent.SpawnReason.CUSTOM
            && reason != CreatureSpawnEvent.SpawnReason.COMMAND
            && reason != CreatureSpawnEvent.SpawnReason.DEFAULT;
    }

    static ClaimPermission spawnPermissionForCreature(boolean enemy, boolean animal, boolean waterMob, boolean ambient) {
        if (enemy) {
            return ClaimPermission.MONSTER_SPAWN;
        }
        if (animal || waterMob || ambient) {
            return ClaimPermission.ANIMAL_SPAWN;
        }
        return null;
    }

    private static ClaimPermission spawnPermissionForCreature(LivingEntity entity) {
        return spawnPermissionForCreature(
            entity instanceof Enemy,
            entity instanceof Animals,
            entity instanceof WaterMob,
            entity instanceof Ambient
        );
    }

    private static boolean isVillagerCrop(Material material) {
        return material == Material.WHEAT
            || material == Material.CARROTS
            || material == Material.POTATOES
            || material == Material.BEETROOTS;
    }

    private static boolean isAirBlock(Material material) {
        return material == Material.AIR
            || material == Material.CAVE_AIR
            || material == Material.VOID_AIR;
    }

    static boolean isLiquidFlowMaterial(Material material) {
        return material == Material.WATER
            || material == Material.LAVA
            || material == Material.BUBBLE_COLUMN;
    }

    static boolean isLiquidFlowAllowed(ClaimFlagState state) {
        ClaimFlagState resolvedState = state == null ? ClaimFlagState.UNSET : state;
        return resolvedState == ClaimFlagState.ALLOW;
    }

    private int claimId(Optional<Claim> claim) {
        return claim.map(Claim::id).orElse(-1);
    }

    private Location fallingBlockSource(FallingBlock fallingBlock) {
        try {
            Object result = fallingBlock.getClass().getMethod("getSourceLoc").invoke(fallingBlock);
            if (result instanceof Location location) {
                return location;
            }
        } catch (ReflectiveOperationException ignored) {
        }
        return fallingBlock.getLocation();
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
            if (entity instanceof Minecart) {
                Location railLocation = entity.getLocation().getBlock().getLocation();
                if (claimService.findClaim(railLocation).isPresent()) {
                    return railLocation;
                }
                Location belowRail = railLocation.clone().subtract(0D, 1D, 0D);
                if (claimService.findClaim(belowRail).isPresent()) {
                    return belowRail;
                }
                return railLocation;
            }
            return entity.getLocation();
        }
        return null;
    }
}
