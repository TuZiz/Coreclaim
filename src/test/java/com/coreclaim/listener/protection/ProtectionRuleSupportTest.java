package com.coreclaim.listener.protection;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.coreclaim.model.ClaimPermission;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;

class ProtectionRuleSupportTest {

    private final ProtectionRuleSupport support = new ProtectionRuleSupport(null, null, null, null, null);

    @Test
    void classifiesRightClickToolChangesByWriteRisk() {
        assertEquals(
            ClaimPermission.INTERACT,
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
        assertNull(support.requiredPermissionForBlockToolChange(Material.DIRT, new ItemStack(Material.STICK)));
    }
}
