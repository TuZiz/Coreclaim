package com.coreclaim.listener;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.coreclaim.model.ClaimFlagState;
import org.bukkit.Material;
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
}
