package com.coreclaim.listener;

import com.coreclaim.config.PluginConfig;
import com.coreclaim.model.Claim;
import com.coreclaim.model.ClaimFlag;
import com.coreclaim.model.ClaimFlagState;
import com.coreclaim.model.ClaimPermission;
import com.coreclaim.cleanup.ClaimCleanupService;
import com.coreclaim.service.ClaimService;
import com.coreclaim.service.ExplosionAuthorizationService;
import com.coreclaim.util.AdminAccess;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.function.Function;
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

    private static final long BLOCK_INVENTORY_LOCATION_CACHE_NANOS = 100_000_000L;
    private static final long ENTITY_INVENTORY_LOCATION_CACHE_NANOS = 50_000_000L;

    private final ClaimService claimService;
    private final ExplosionAuthorizationService explosionAuthorizationService;
    private final ClaimCleanupService claimCleanupService;
    private final PluginConfig settings;
    private final Map<Inventory, CachedInventoryLocation> inventoryLocationCache = Collections.synchronizedMap(new WeakHashMap<>());

    public ClaimEnvironmentProtectionListener(
        ClaimService claimService,
        ExplosionAuthorizationService explosionAuthorizationService,
        ClaimCleanupService claimCleanupService,
        PluginConfig settings
    ) {
        this.claimService = claimService;
        this.explosionAuthorizationService = explosionAuthorizationService;
        this.claimCleanupService = claimCleanupService;
        this.settings = settings;
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        Location origin = explodedBlockOrigin(event);
        boolean authorized = explosionAuthorizationService != null && explosionAuthorizationService.isAuthorizedNearby(origin, 1);
        applyBlockExplosionProtection(event, origin, claimService::findClaim, authorized);
    }

    static void applyBlockExplosionProtection(
        BlockExplodeEvent event,
        Location origin,
        Function<Location, Optional<Claim>> claimFinder,
        boolean authorized
    ) {
        Optional<Claim> sourceClaim = claimFinder.apply(origin);
        boolean sourcePublicExplosion = sourceClaim.isPresent()
            && sourceClaim.get().permission(ClaimPermission.EXPLOSION);
        if (sourceClaim.isPresent() && !authorized && !sourcePublicExplosion) {
            event.blockList().clear();
            event.setYield(0F);
            event.setCancelled(true);
            return;
        }
        Iterator<Block> iterator = event.blockList().iterator();
        while (iterator.hasNext()) {
            Block block = iterator.next();
            Optional<Claim> targetClaim = claimFinder.apply(block.getLocation());
            if (sourceClaim.isPresent()) {
                if (targetClaim.isEmpty() || targetClaim.get().id() != sourceClaim.get().id()) {
                    iterator.remove();
                }
                continue;
            }

            if (targetClaim.isPresent()) {
                iterator.remove();
            }
        }
    }

    static Location explodedBlockOrigin(BlockExplodeEvent event) {
        try {
            Object state = event.getClass().getMethod("getExplodedBlockState").invoke(event);
            if (state instanceof BlockState blockState) {
                return blockState.getLocation();
            }
        } catch (ReflectiveOperationException | LinkageError ignored) {
        }
        return event.getBlock().getLocation();
    }

    @EventHandler(ignoreCancelled = true)
    public void onFlow(BlockFromToEvent event) {
        if (settings != null && !settings.liquidFlowCrossClaimCheck()) {
            return;
        }
        Location from = event.getBlock().getLocation();
        Location to = event.getToBlock().getLocation();
        if (sameBlock(from, to)) {
            return;
        }
        if (sameChunk(from, to) && !claimService.hasClaimCandidateAt(from)) {
            return;
        }
        if (isLiquidFlowMaterial(event.getBlock().getType())) {
            if (shouldCancelLiquidFlow(from, to)) {
                event.setCancelled(true);
            }
            return;
        }
        if (crossesClaimBoundary(from, to)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onPistonExtend(BlockPistonExtendEvent event) {
        if (shouldCancelPistonMove(event.getBlocks(), event.getDirection())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onPistonRetract(BlockPistonRetractEvent event) {
        if (shouldCancelPistonMove(event.getBlocks(), event.getDirection())) {
            event.setCancelled(true);
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
        if (!canIgniteProtectedBlock(
            claim.get(),
            player != null,
            permission,
            player != null && claimService.hasPermission(claim.get(), player.getUniqueId(), permission)
        )) {
            event.setCancelled(true);
            return;
        }
        if (permission == ClaimPermission.EXPLOSION) {
            explosionAuthorizationService.authorize(event.getBlock().getLocation());
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
        if (settings != null && !settings.hopperCrossClaimCheck()) {
            return;
        }
        Inventory sourceInventory = event.getSource();
        Inventory destinationInventory = event.getDestination();
        if (sourceInventory == null || destinationInventory == null) {
            return;
        }
        Location source = cachedInventoryLocation(sourceInventory);
        Location destination = cachedInventoryLocation(destinationInventory);
        if (source == null || destination == null) {
            return;
        }
        if (sameBlock(source, destination)) {
            return;
        }
        if (sameChunk(source, destination) && !claimService.hasClaimCandidateAt(source)) {
            return;
        }
        Optional<Claim> sourceClaim = findClaimIfCandidate(source);
        Optional<Claim> destinationClaim = findClaimIfCandidate(destination);
        if (!crossesClaimBoundary(source, destination, sourceClaim, destinationClaim)) {
            return;
        }
        if (claimId(sourceClaim) != claimId(destinationClaim)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onInventoryPickup(InventoryPickupItemEvent event) {
        if (settings != null && !settings.inventoryPickupCrossClaimCheck()) {
            return;
        }
        Location inventoryLocation = cachedInventoryLocation(event.getInventory());
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
        if (sameBlock(from, to)) {
            return false;
        }
        if (sameChunk(from, to) && !claimService.hasClaimCandidateAt(from)) {
            return false;
        }
        Optional<Claim> fromClaim = findClaimIfCandidate(from);
        Optional<Claim> toClaim = findClaimIfCandidate(to);
        return crossesClaimBoundary(from, to, fromClaim, toClaim);
    }

    private boolean crossesClaimBoundary(Location from, Location to, Optional<Claim> fromClaim, Optional<Claim> toClaim) {
        if (sameBlock(from, to)) {
            return false;
        }
        return crossesClaimBoundary(fromClaim, toClaim);
    }

    static boolean crossesClaimBoundary(Optional<Claim> fromClaim, Optional<Claim> toClaim) {
        if (fromClaim.isEmpty() && toClaim.isEmpty()) {
            return false;
        }
        return claimId(fromClaim) != claimId(toClaim);
    }

    private boolean shouldCancelLiquidFlow(Location from, Location to) {
        if (sameBlock(from, to)) {
            return false;
        }
        if (sameChunk(from, to) && !claimService.hasClaimCandidateAt(from)) {
            return false;
        }
        Optional<Claim> fromClaim = findClaimIfCandidate(from);
        Optional<Claim> toClaim = findClaimIfCandidate(to);
        if (toClaim.isEmpty() || claimId(fromClaim) == claimId(toClaim)) {
            return false;
        }
        return !isLiquidFlowAllowed(toClaim.get());
    }

    private boolean shouldCancelPistonMove(java.util.List<Block> blocks, BlockFace direction) {
        if (settings != null && !settings.pistonCrossClaimCheck()) {
            return false;
        }
        if (blocks == null || blocks.isEmpty() || direction == null) {
            return false;
        }
        Set<BlockMoveKey> checkedPairs = new HashSet<>();
        for (Block block : blocks) {
            Location from = block.getLocation();
            Location to = block.getRelative(direction).getLocation();
            if (sameBlock(from, to)) {
                continue;
            }
            if (sameChunk(from, to) && !claimService.hasClaimCandidateAt(from)) {
                continue;
            }
            if (!checkedPairs.add(BlockMoveKey.of(from, to))) {
                continue;
            }
            if (crossesClaimBoundary(from, to)) {
                return true;
            }
        }
        return false;
    }

    private Optional<Claim> findClaimIfCandidate(Location location) {
        if (!claimService.hasClaimCandidateAt(location)) {
            return Optional.empty();
        }
        return claimService.findClaim(location);
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

    static boolean sameBlock(Location from, Location to) {
        if (from == null || to == null || from.getWorld() != to.getWorld()) {
            return false;
        }
        return from.getBlockX() == to.getBlockX()
            && from.getBlockY() == to.getBlockY()
            && from.getBlockZ() == to.getBlockZ();
    }

    static boolean sameChunk(Location from, Location to) {
        if (from == null || to == null || from.getWorld() != to.getWorld()) {
            return false;
        }
        return (from.getBlockX() >> 4) == (to.getBlockX() >> 4)
            && (from.getBlockZ() >> 4) == (to.getBlockZ() >> 4);
    }

    static boolean canIgniteProtectedBlock(Claim claim, boolean playerPresent, ClaimPermission permission, boolean playerHasPermission) {
        if (claim == null) {
            return true;
        }
        if (playerPresent) {
            return playerHasPermission;
        }
        return permission == ClaimPermission.EXPLOSION && claim.permission(ClaimPermission.EXPLOSION);
    }

    static boolean canBlockExplosionAffectClaim(
        Claim sourceClaim,
        Claim targetClaim,
        boolean authorizedOrigin,
        boolean sourcePublicExplosion
    ) {
        if (targetClaim == null) {
            return false;
        }
        if (sourceClaim == null || sourceClaim.id() != targetClaim.id()) {
            return false;
        }
        return authorizedOrigin || sourcePublicExplosion;
    }

    private static int claimId(Optional<Claim> claim) {
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

    private Location cachedInventoryLocation(Inventory inventory) {
        if (inventory == null) {
            return null;
        }
        long now = System.nanoTime();
        CachedInventoryLocation cached = inventoryLocationCache.get(inventory);
        if (cached != null && now - cached.createdAtNanos() <= cached.ttlNanos()) {
            return cloneLocation(cached.location());
        }
        ResolvedInventoryLocation resolved = resolveInventoryLocation(inventory);
        if (resolved != null) {
            inventoryLocationCache.put(inventory, new CachedInventoryLocation(cloneLocation(resolved.location()), now, resolved.ttlNanos()));
            return resolved.location();
        }
        return null;
    }

    private Location inventoryLocation(Inventory inventory) {
        ResolvedInventoryLocation resolved = resolveInventoryLocation(inventory);
        return resolved == null ? null : resolved.location();
    }

    private ResolvedInventoryLocation resolveInventoryLocation(Inventory inventory) {
        if (inventory == null) {
            return null;
        }
        try {
            Location location = inventory.getLocation();
            if (location != null) {
                return new ResolvedInventoryLocation(location, BLOCK_INVENTORY_LOCATION_CACHE_NANOS);
            }
        } catch (Throwable ignored) {
        }
        InventoryHolder holder = inventory.getHolder();
        if (holder instanceof BlockState blockState) {
            return new ResolvedInventoryLocation(blockState.getLocation(), BLOCK_INVENTORY_LOCATION_CACHE_NANOS);
        }
        if (holder instanceof Entity entity) {
            if (entity instanceof Minecart) {
                Location railLocation = entity.getLocation().getBlock().getLocation();
                if (findClaimIfCandidate(railLocation).isPresent()) {
                    return new ResolvedInventoryLocation(railLocation, ENTITY_INVENTORY_LOCATION_CACHE_NANOS);
                }
                Location belowRail = railLocation.clone().subtract(0D, 1D, 0D);
                if (findClaimIfCandidate(belowRail).isPresent()) {
                    return new ResolvedInventoryLocation(belowRail, ENTITY_INVENTORY_LOCATION_CACHE_NANOS);
                }
                return new ResolvedInventoryLocation(railLocation, ENTITY_INVENTORY_LOCATION_CACHE_NANOS);
            }
            return new ResolvedInventoryLocation(entity.getLocation(), ENTITY_INVENTORY_LOCATION_CACHE_NANOS);
        }
        return null;
    }

    private static Location cloneLocation(Location location) {
        return location == null ? null : location.clone();
    }

    private record CachedInventoryLocation(Location location, long createdAtNanos, long ttlNanos) {
    }

    private record ResolvedInventoryLocation(Location location, long ttlNanos) {
    }

    private record BlockMoveKey(
        String worldName,
        int fromX,
        int fromY,
        int fromZ,
        int toX,
        int toY,
        int toZ
    ) {

        static BlockMoveKey of(Location from, Location to) {
            String worldName = from.getWorld() == null ? "" : from.getWorld().getName();
            return new BlockMoveKey(
                worldName,
                from.getBlockX(),
                from.getBlockY(),
                from.getBlockZ(),
                to.getBlockX(),
                to.getBlockY(),
                to.getBlockZ()
            );
        }
    }
}
