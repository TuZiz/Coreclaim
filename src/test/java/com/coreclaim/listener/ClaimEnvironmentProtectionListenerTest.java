package com.coreclaim.listener;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.coreclaim.model.ClaimFlagState;
import com.coreclaim.model.ClaimPermission;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.junit.jupiter.api.Test;

class ClaimEnvironmentProtectionListenerTest {

    @Test
    void allowsVillagerHarvestAndReplantCropChanges() {
        assertTrue(ClaimEnvironmentProtectionListener.isVillagerFarmChange(Material.WHEAT, Material.AIR));
        assertTrue(ClaimEnvironmentProtectionListener.isVillagerFarmChange(Material.CARROTS, Material.AIR));
        assertTrue(ClaimEnvironmentProtectionListener.isVillagerFarmChange(Material.AIR, Material.POTATOES));
        assertTrue(ClaimEnvironmentProtectionListener.isVillagerFarmChange(Material.CAVE_AIR, Material.BEETROOTS));
    }

    @Test
    void doesNotTreatOtherEntityBlockChangesAsFarming() {
        assertFalse(ClaimEnvironmentProtectionListener.isVillagerFarmChange(Material.GRASS_BLOCK, Material.DIRT));
        assertFalse(ClaimEnvironmentProtectionListener.isVillagerFarmChange(Material.AIR, Material.STONE));
        assertFalse(ClaimEnvironmentProtectionListener.isVillagerFarmChange(Material.OAK_LOG, Material.AIR));
    }

    @Test
    void allowsFoxSweetBerryHarvestChangesOnly() {
        assertTrue(ClaimEnvironmentProtectionListener.isFoxSweetBerryChange(Material.SWEET_BERRY_BUSH, Material.SWEET_BERRY_BUSH));
        assertTrue(ClaimEnvironmentProtectionListener.isFoxSweetBerryChange(Material.SWEET_BERRY_BUSH, Material.AIR));
        assertTrue(ClaimEnvironmentProtectionListener.isFoxSweetBerryChange(Material.CAVE_VINES, Material.CAVE_VINES));
        assertTrue(ClaimEnvironmentProtectionListener.isFoxSweetBerryChange(Material.CAVE_VINES, Material.CAVE_VINES_PLANT));
        assertTrue(ClaimEnvironmentProtectionListener.isFoxSweetBerryChange(Material.CAVE_VINES_PLANT, Material.CAVE_VINES_PLANT));
        assertTrue(ClaimEnvironmentProtectionListener.isFoxSweetBerryChange(Material.CAVE_VINES_PLANT, Material.AIR));
        assertFalse(ClaimEnvironmentProtectionListener.isFoxSweetBerryChange(Material.WHEAT, Material.AIR));
        assertFalse(ClaimEnvironmentProtectionListener.isFoxSweetBerryChange(Material.SWEET_BERRY_BUSH, Material.DIRT));
        assertFalse(ClaimEnvironmentProtectionListener.isFoxSweetBerryChange(Material.CAVE_VINES, Material.DIRT));
    }

    @Test
    void allowsBeeHiveNaturalStateChangesOnly() {
        assertTrue(ClaimEnvironmentProtectionListener.isBeeHiveStateChange(Material.BEEHIVE, Material.BEEHIVE));
        assertTrue(ClaimEnvironmentProtectionListener.isBeeHiveStateChange(Material.BEE_NEST, Material.BEE_NEST));
        assertFalse(ClaimEnvironmentProtectionListener.isBeeHiveStateChange(Material.BEEHIVE, Material.AIR));
        assertFalse(ClaimEnvironmentProtectionListener.isBeeHiveStateChange(Material.OAK_LOG, Material.OAK_LOG));
    }

    @Test
    void mapsCreatureSpawnTypesToClaimPermissions() {
        assertEquals(ClaimPermission.MONSTER_SPAWN, ClaimEnvironmentProtectionListener.spawnPermissionForCreature(true, false, false, false));
        assertEquals(ClaimPermission.ANIMAL_SPAWN, ClaimEnvironmentProtectionListener.spawnPermissionForCreature(false, true, false, false));
        assertEquals(ClaimPermission.ANIMAL_SPAWN, ClaimEnvironmentProtectionListener.spawnPermissionForCreature(false, false, true, false));
        assertEquals(ClaimPermission.ANIMAL_SPAWN, ClaimEnvironmentProtectionListener.spawnPermissionForCreature(false, false, false, true));
        assertEquals(null, ClaimEnvironmentProtectionListener.spawnPermissionForCreature(false, false, false, false));
    }

    @Test
    void leavesCommandAndPluginCreatureSpawnsToTheirCaller() {
        assertFalse(ClaimEnvironmentProtectionListener.isManagedCreatureSpawnReason(CreatureSpawnEvent.SpawnReason.CUSTOM));
        assertFalse(ClaimEnvironmentProtectionListener.isManagedCreatureSpawnReason(CreatureSpawnEvent.SpawnReason.COMMAND));
        assertFalse(ClaimEnvironmentProtectionListener.isManagedCreatureSpawnReason(CreatureSpawnEvent.SpawnReason.DEFAULT));
        assertTrue(ClaimEnvironmentProtectionListener.isManagedCreatureSpawnReason(CreatureSpawnEvent.SpawnReason.NATURAL));
        assertTrue(ClaimEnvironmentProtectionListener.isManagedCreatureSpawnReason(CreatureSpawnEvent.SpawnReason.SPAWNER));
    }

    @Test
    void recognizesLiquidFlowMaterials() {
        assertTrue(ClaimEnvironmentProtectionListener.isLiquidFlowMaterial(Material.WATER));
        assertTrue(ClaimEnvironmentProtectionListener.isLiquidFlowMaterial(Material.LAVA));
        assertTrue(ClaimEnvironmentProtectionListener.isLiquidFlowMaterial(Material.BUBBLE_COLUMN));
        assertFalse(ClaimEnvironmentProtectionListener.isLiquidFlowMaterial(Material.STONE));
    }

    @Test
    void resolvesLiquidFlowFlagAgainstBucketFallback() {
        assertTrue(ClaimEnvironmentProtectionListener.isLiquidFlowAllowed(ClaimFlagState.ALLOW));
        assertFalse(ClaimEnvironmentProtectionListener.isLiquidFlowAllowed(ClaimFlagState.DENY));
        assertFalse(ClaimEnvironmentProtectionListener.isLiquidFlowAllowed(ClaimFlagState.UNSET));
        assertFalse(ClaimEnvironmentProtectionListener.isLiquidFlowAllowed(null));
    }

    @Test
    void sameBlockSkipsIdenticalInventoryMoveEndpoints() {
        assertTrue(ClaimEnvironmentProtectionListener.sameBlock(
            new Location(null, 5D, 64D, 5D),
            new Location(null, 5.9D, 64D, 5.9D)
        ));
        assertFalse(ClaimEnvironmentProtectionListener.sameBlock(
            new Location(null, 5D, 64D, 5D),
            new Location(null, 6D, 64D, 5D)
        ));
    }

    @Test
    void sameChunkRecognizesChunkLocalMovement() {
        assertTrue(ClaimEnvironmentProtectionListener.sameChunk(
            new Location(null, 0D, 64D, 0D),
            new Location(null, 15D, 70D, 15D)
        ));
        assertFalse(ClaimEnvironmentProtectionListener.sameChunk(
            new Location(null, 0D, 64D, 0D),
            new Location(null, 16D, 64D, 0D)
        ));
    }

    @Test
    void claimBoundaryComparisonAllowsSameClaimAndRejectsDifferentClaim() {
        com.coreclaim.model.Claim source = claim(1, true);
        com.coreclaim.model.Claim destination = claim(2, true);

        assertFalse(ClaimEnvironmentProtectionListener.crossesClaimBoundary(Optional.of(source), Optional.of(source)));
        assertTrue(ClaimEnvironmentProtectionListener.crossesClaimBoundary(Optional.of(source), Optional.of(destination)));
        assertTrue(ClaimEnvironmentProtectionListener.crossesClaimBoundary(Optional.empty(), Optional.of(destination)));
        assertFalse(ClaimEnvironmentProtectionListener.crossesClaimBoundary(Optional.empty(), Optional.empty()));
    }

    @Test
    void allowsNonPlayerTntIgnitionOnlyWhenExplosionDefaultIsAllowed() {
        assertTrue(ClaimEnvironmentProtectionListener.canIgniteProtectedBlock(claim(true), false, ClaimPermission.EXPLOSION, false));
        assertFalse(ClaimEnvironmentProtectionListener.canIgniteProtectedBlock(claim(false), false, ClaimPermission.EXPLOSION, false));
        assertFalse(ClaimEnvironmentProtectionListener.canIgniteProtectedBlock(claim(true), false, ClaimPermission.INTERACT, false));
    }

    @Test
    void playerIgnitionUsesResolvedPermissionDecision() {
        assertTrue(ClaimEnvironmentProtectionListener.canIgniteProtectedBlock(claim(false), true, ClaimPermission.EXPLOSION, true));
        assertFalse(ClaimEnvironmentProtectionListener.canIgniteProtectedBlock(claim(true), true, ClaimPermission.EXPLOSION, false));
    }

    @Test
    void blockExplosionsOnlyAffectSameAuthorizedOrPublicExplosionClaim() {
        com.coreclaim.model.Claim source = claim(1, true);
        com.coreclaim.model.Claim other = claim(2, true);

        assertTrue(ClaimEnvironmentProtectionListener.canBlockExplosionAffectClaim(source, source, true, false));
        assertTrue(ClaimEnvironmentProtectionListener.canBlockExplosionAffectClaim(source, source, false, true));
        assertFalse(ClaimEnvironmentProtectionListener.canBlockExplosionAffectClaim(source, null, true, true));
        assertFalse(ClaimEnvironmentProtectionListener.canBlockExplosionAffectClaim(source, other, true, true));
        assertFalse(ClaimEnvironmentProtectionListener.canBlockExplosionAffectClaim(null, source, true, true));
        assertFalse(ClaimEnvironmentProtectionListener.canBlockExplosionAffectClaim(source, source, false, false));
    }

    @Test
    void unauthorizedBlockExplosionInsideClaimCancelsAndClearsEvent() {
        BlockExplodeEvent event = blockExplodeEvent(blockAt(0), blockAt(1), blockAt(20), blockAt(100));

        ClaimEnvironmentProtectionListener.applyBlockExplosionProtection(event, location(0), claimFinder(), false);

        assertTrue(event.isCancelled());
        assertTrue(event.blockList().isEmpty());
        assertEquals(0F, event.getYield());
    }

    @Test
    void authorizedBlockExplosionInsideClaimOnlyAffectsSameClaim() {
        Block sameClaim = blockAt(1);
        Block otherClaim = blockAt(20);
        Block wilderness = blockAt(100);
        BlockExplodeEvent event = blockExplodeEvent(blockAt(0), sameClaim, otherClaim, wilderness);

        ClaimEnvironmentProtectionListener.applyBlockExplosionProtection(event, location(0), claimFinder(), true);

        assertFalse(event.isCancelled());
        assertEquals(List.of(sameClaim), event.blockList());
    }

    @Test
    void publicExplosionClaimStillCannotAffectOtherClaimsOrWilderness() {
        Block sameClaim = blockAt(1);
        Block otherClaim = blockAt(20);
        Block wilderness = blockAt(100);
        BlockExplodeEvent event = blockExplodeEvent(blockAt(0), sameClaim, otherClaim, wilderness);

        ClaimEnvironmentProtectionListener.applyBlockExplosionProtection(event, location(0), claimFinder(true), false);

        assertFalse(event.isCancelled());
        assertEquals(List.of(sameClaim), event.blockList());
    }

    @Test
    void wildernessBlockExplosionCannotAffectClaimBlocks() {
        Block sameClaim = blockAt(1);
        Block otherClaim = blockAt(20);
        Block wilderness = blockAt(100);
        BlockExplodeEvent event = blockExplodeEvent(blockAt(100), sameClaim, otherClaim, wilderness);

        ClaimEnvironmentProtectionListener.applyBlockExplosionProtection(event, location(100), claimFinder(), false);

        assertFalse(event.isCancelled());
        assertEquals(List.of(wilderness), event.blockList());
    }

    @Test
    void legacyBlockExplodeOriginFallsBackToEventBlockLocation() {
        Block source = blockAt(0);
        BlockExplodeEvent event = blockExplodeEvent(source, blockAt(1));

        assertEquals(source.getLocation(), ClaimEnvironmentProtectionListener.explodedBlockOrigin(event));
    }

    private static com.coreclaim.model.Claim claim(boolean allowExplosion) {
        return claim(1, allowExplosion);
    }

    private static Function<Location, Optional<com.coreclaim.model.Claim>> claimFinder() {
        return claimFinder(false);
    }

    private static Function<Location, Optional<com.coreclaim.model.Claim>> claimFinder(boolean sourceExplosionAllowed) {
        com.coreclaim.model.Claim source = claim(1, sourceExplosionAllowed);
        com.coreclaim.model.Claim other = claim(2, true);
        return location -> {
            int blockX = location.getBlockX();
            if (blockX == 0 || blockX == 1) {
                return Optional.of(source);
            }
            if (blockX == 20) {
                return Optional.of(other);
            }
            return Optional.empty();
        };
    }

    private static BlockExplodeEvent blockExplodeEvent(Block source, Block... blocks) {
        return new BlockExplodeEvent(source, new ArrayList<>(List.of(blocks)), 1.0F);
    }

    private static Block blockAt(int x) {
        Location location = location(x);
        return (Block)Proxy.newProxyInstance(
            Block.class.getClassLoader(),
            new Class<?>[] {Block.class},
            (proxy, method, args) -> switch (method.getName()) {
                case "getLocation" -> location;
                case "getType" -> Material.STONE;
                case "toString" -> "Block@" + location.getBlockX();
                case "hashCode" -> location.getBlockX();
                case "equals" -> proxy == args[0];
                default -> defaultValue(method.getReturnType());
            }
        );
    }

    private static Location location(int x) {
        return new Location(null, x, 64D, 0D);
    }

    private static Object defaultValue(Class<?> returnType) {
        if (!returnType.isPrimitive()) {
            return null;
        }
        if (returnType == boolean.class) {
            return false;
        }
        if (returnType == char.class) {
            return '\0';
        }
        if (returnType == byte.class) {
            return (byte)0;
        }
        if (returnType == short.class) {
            return (short)0;
        }
        if (returnType == int.class) {
            return 0;
        }
        if (returnType == long.class) {
            return 0L;
        }
        if (returnType == float.class) {
            return 0F;
        }
        if (returnType == double.class) {
            return 0D;
        }
        return null;
    }

    private static com.coreclaim.model.Claim claim(int id, boolean allowExplosion) {
        return new com.coreclaim.model.Claim(
            id,
            java.util.UUID.randomUUID(),
            "owner",
            "claim",
            "server",
            "world",
            0,
            64,
            0,
            0,
            255,
            true,
            5,
            5,
            5,
            5,
            0L,
            true,
            "",
            "",
            false,
            false,
            false,
            false,
            false,
            false,
            false,
            false,
            allowExplosion,
            false,
            false,
            false,
            false,
            false,
            null,
            null,
            null,
            null,
            null,
            0L
        );
    }

}
