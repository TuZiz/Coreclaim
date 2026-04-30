package com.coreclaim.listener.protection;

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
