package com.coreclaim.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.coreclaim.model.ClaimFlag;
import com.coreclaim.model.ClaimPermission;
import org.junit.jupiter.api.Test;

class PluginConfigLoaderTest {

    @Test
    void ordinaryClaimFallbackDefaultsDenyDangerousPermissions() {
        for (ClaimPermission permission : ClaimPermission.values()) {
            assertFalse(PluginConfigLoader.defaultPermissionValue(permission, false));
        }
    }

    @Test
    void ordinaryClaimFallbackDefaultsDenyDetailedUsePermissions() {
        for (ClaimFlag flag : ClaimFlag.values()) {
            String expected = flag == ClaimFlag.TIME_CYCLE ? "unset" : "deny";
            assertEquals(expected, PluginConfigLoader.defaultFlagValue(flag, false));
        }
    }

    @Test
    void systemClaimFallbackKeepsPublicUseDefaults() {
        assertTrue(PluginConfigLoader.defaultPermissionValue(ClaimPermission.INTERACT, true));
        assertTrue(PluginConfigLoader.defaultPermissionValue(ClaimPermission.TELEPORT, true));
        assertFalse(PluginConfigLoader.defaultPermissionValue(ClaimPermission.FLIGHT, true));
        assertEquals("allow", PluginConfigLoader.defaultFlagValue(ClaimFlag.USE_DOOR, true));
        assertEquals("deny", PluginConfigLoader.defaultFlagValue(ClaimFlag.CONTAINER, true));
    }
}
