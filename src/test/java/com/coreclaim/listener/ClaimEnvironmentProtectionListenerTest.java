package com.coreclaim.listener;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.coreclaim.model.ClaimFlagState;
import com.coreclaim.model.ClaimPermission;
import org.bukkit.Material;
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

    private static com.coreclaim.model.Claim claim(boolean allowExplosion) {
        return new com.coreclaim.model.Claim(
            1,
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
