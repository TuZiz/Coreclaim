package com.coreclaim.protection.listener;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.coreclaim.model.ClaimPermission;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;

class ProtectionRuleSupportTest {

    private final ProtectionRuleSupport support = new ProtectionRuleSupport(null, null, null, null, null);

    @Test
    void classifiesRightClickToolChangesByWriteRisk() {
        assertEquals(
            ClaimPermission.BREAK,
            support.requiredPermissionForBlockToolChange(Material.OAK_LOG, new ItemStack(Material.DIAMOND_AXE))
        );
        assertEquals(
            ClaimPermission.BREAK,
            support.requiredPermissionForBlockToolChange(Material.EXPOSED_COPPER, new ItemStack(Material.DIAMOND_AXE))
        );
        assertEquals(
            ClaimPermission.BREAK,
            support.requiredPermissionForBlockToolChange(Material.GRASS_BLOCK, new ItemStack(Material.DIAMOND_SHOVEL))
        );
        assertEquals(
            ClaimPermission.INTERACT,
            support.requiredPermissionForBlockToolChange(Material.CAMPFIRE, new ItemStack(Material.DIAMOND_SHOVEL))
        );
        assertEquals(
            ClaimPermission.INTERACT,
            support.requiredPermissionForBlockToolChange(Material.DIRT, new ItemStack(Material.DIAMOND_HOE))
        );
    }

    @Test
    void recognizesOnlyAxeStrippableWoodForPublicStrippingException() {
        assertTrue(support.isAxeStrippingWood(Material.OAK_LOG, new ItemStack(Material.DIAMOND_AXE)));
        assertTrue(support.isAxeStrippingWood(Material.BAMBOO_BLOCK, new ItemStack(Material.GOLDEN_AXE)));
        assertFalse(support.isAxeStrippingWood(Material.STRIPPED_OAK_LOG, new ItemStack(Material.DIAMOND_AXE)));
        assertFalse(support.isAxeStrippingWood(Material.OAK_LOG, new ItemStack(Material.STICK)));
        assertFalse(support.isAxeStrippingWood(Material.EXPOSED_COPPER, new ItemStack(Material.DIAMOND_AXE)));
    }

    @Test
    void copperChestToolChangesAreBlockDriven() {
        Material oxidizedCopperChest = Material.matchMaterial("OXIDIZED_COPPER_CHEST");
        if (oxidizedCopperChest == null) {
            return;
        }
        assertTrue(support.isBlockDrivenToolChange(oxidizedCopperChest, new ItemStack(Material.STONE_AXE)));
        assertFalse(support.isBlockDrivenToolChange(oxidizedCopperChest, new ItemStack(Material.STICK)));
        assertEquals(
            Material.matchMaterial("WEATHERED_COPPER_CHEST"),
            ProtectionMaterialRules.scrapedCopperChestMaterial(oxidizedCopperChest)
        );
    }

    @Test
    void copperWaxScrapeAndTerrainToolChangesAreBlockDriven() {
        assertTrue(support.isBlockDrivenToolChange(Material.EXPOSED_COPPER, new ItemStack(Material.STONE_AXE)));
        assertTrue(support.isBlockDrivenToolChange(Material.COPPER_BLOCK, new ItemStack(Material.HONEYCOMB)));
        assertTrue(support.isBlockDrivenToolChange(Material.CAMPFIRE, new ItemStack(Material.STONE_SHOVEL)));
        assertTrue(support.isBlockDrivenToolChange(Material.GRASS_BLOCK, new ItemStack(Material.STONE_SHOVEL)));
        assertTrue(support.isBlockDrivenToolChange(Material.DIRT, new ItemStack(Material.STONE_HOE)));
        assertFalse(support.isBlockDrivenToolChange(Material.DIRT, new ItemStack(Material.STICK)));

        assertEquals(Material.COPPER_BLOCK, ProtectionMaterialRules.scrapedOrWaxedCopperMaterial(Material.EXPOSED_COPPER, new ItemStack(Material.STONE_AXE)));
        assertEquals(Material.matchMaterial("WAXED_COPPER_BLOCK"), ProtectionMaterialRules.scrapedOrWaxedCopperMaterial(Material.COPPER_BLOCK, new ItemStack(Material.HONEYCOMB)));
        assertEquals(Material.DIRT_PATH, ProtectionMaterialRules.flattenedPathMaterial(Material.GRASS_BLOCK, new ItemStack(Material.STONE_SHOVEL)));
        assertEquals(Material.FARMLAND, ProtectionMaterialRules.tilledSoilMaterial(Material.DIRT, new ItemStack(Material.STONE_HOE)));
        assertEquals(Material.DIRT, ProtectionMaterialRules.tilledSoilMaterial(Material.COARSE_DIRT, new ItemStack(Material.STONE_HOE)));
    }

    @Test
    void mapsStrippableWoodToStrippedMaterial() {
        assertEquals(Material.STRIPPED_OAK_LOG, support.strippedWoodMaterial(Material.OAK_LOG));
        assertEquals(Material.STRIPPED_DARK_OAK_WOOD, support.strippedWoodMaterial(Material.DARK_OAK_WOOD));
        assertEquals(Material.STRIPPED_CRIMSON_STEM, support.strippedWoodMaterial(Material.CRIMSON_STEM));
        assertEquals(Material.STRIPPED_WARPED_HYPHAE, support.strippedWoodMaterial(Material.WARPED_HYPHAE));
        assertEquals(Material.STRIPPED_BAMBOO_BLOCK, support.strippedWoodMaterial(Material.BAMBOO_BLOCK));
        assertNull(support.strippedWoodMaterial(Material.STRIPPED_OAK_LOG));
        assertNull(support.strippedWoodMaterial(Material.EXPOSED_COPPER));
    }

    @Test
    void directStateBlocksRemainInteractAndUnrelatedItemsAreIgnored() {
        assertEquals(
            ClaimPermission.INTERACT,
            support.requiredPermissionForBlockToolChange(Material.CAMPFIRE, null)
        );
        assertEquals(
            ClaimPermission.INTERACT,
            support.requiredPermissionForBlockToolChange(Material.COMPOSTER, new ItemStack(Material.WHEAT_SEEDS))
        );
        assertNull(support.requiredPermissionForBlockToolChange(Material.DIRT, new ItemStack(Material.STICK)));
    }

    @Test
    void composterInputCanBeAllowedWithoutOpeningFullComposters() {
        assertTrue(
            support.isComposterCompostInput(Material.COMPOSTER, false, new ItemStack(Material.WHEAT_SEEDS))
        );
        assertTrue(
            support.isComposterCompostInput(Material.COMPOSTER, false, new ItemStack(Material.OAK_SAPLING))
        );
        assertFalse(
            support.isComposterCompostInput(Material.COMPOSTER, true, new ItemStack(Material.WHEAT_SEEDS))
        );
        assertFalse(
            support.isComposterCompostInput(Material.COMPOSTER, false, new ItemStack(Material.DIRT))
        );
        assertFalse(
            support.isComposterCompostInput(Material.BARREL, false, new ItemStack(Material.WHEAT_SEEDS))
        );
    }

    @Test
    void cakeConsumptionCanBeAllowedWithoutAllowingCandlePlacement() {
        assertTrue(support.isCakeConsumption(Material.CAKE, null));
        assertTrue(support.isCakeConsumption(Material.CAKE, new ItemStack(Material.AIR)));
        assertTrue(support.isCakeConsumption(Material.CAKE, new ItemStack(Material.STICK)));
        assertTrue(support.isCakeConsumption(Material.CANDLE_CAKE, null));
        assertTrue(support.isCakeConsumption(Material.WHITE_CANDLE_CAKE, new ItemStack(Material.STICK)));
        assertFalse(support.isCakeConsumption(Material.CAKE, new ItemStack(Material.CANDLE)));
        assertFalse(support.isCakeConsumption(Material.CAKE, new ItemStack(Material.WHITE_CANDLE)));
        assertFalse(support.isCakeConsumption(Material.OAK_LOG, new ItemStack(Material.STICK)));
    }

    @Test
    void mergedInteractionPermissionsClassifyCommonBlocks() {
        assertEquals(ClaimPermission.INTERACT, support.requiredPermissionForBlockInteract(Material.CHEST, null));
        assertEquals(ClaimPermission.INTERACT, support.requiredPermissionForBlockInteract(Material.OAK_DOOR, null));
        assertEquals(ClaimPermission.INTERACT, support.requiredPermissionForBlockInteract(Material.OAK_TRAPDOOR, null));
        assertEquals(ClaimPermission.INTERACT, support.requiredPermissionForBlockInteract(Material.OAK_FENCE_GATE, null));
        assertEquals(ClaimPermission.INTERACT, support.requiredPermissionForBlockInteract(Material.RED_BED, null));
        assertEquals(ClaimPermission.REDSTONE, support.requiredPermissionForBlockInteract(Material.STONE_BUTTON, null));
        assertEquals(ClaimPermission.REDSTONE, support.requiredPermissionForBlockInteract(Material.LEVER, null));
        assertEquals(ClaimPermission.REDSTONE, support.requiredPermissionForBlockInteract(Material.OAK_PRESSURE_PLATE, null));
    }

    @Test
    void mobEntityClassificationExcludesPlayersAndArmorStands() {
        assertTrue(ProtectionRuleSupport.isMobEntityType(true, false, false));
        assertFalse(ProtectionRuleSupport.isMobEntityType(true, true, false));
        assertFalse(ProtectionRuleSupport.isMobEntityType(true, false, true));
        assertFalse(ProtectionRuleSupport.isMobEntityType(false, false, false));
    }
}
