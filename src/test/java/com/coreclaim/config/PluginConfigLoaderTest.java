package com.coreclaim.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.coreclaim.model.ClaimFlag;
import com.coreclaim.model.ClaimFlagState;
import com.coreclaim.model.ClaimPermission;
import java.util.Map;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

class PluginConfigLoaderTest {

    @Test
    void ordinaryClaimFallbackDefaultsDenyDangerousPermissions() {
        for (ClaimPermission permission : ClaimPermission.values()) {
            if (permission == ClaimPermission.ANIMAL_SPAWN || permission == ClaimPermission.MONSTER_SPAWN) {
                assertTrue(PluginConfigLoader.defaultPermissionValue(permission, false));
            } else {
                assertFalse(PluginConfigLoader.defaultPermissionValue(permission, false));
            }
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
        assertFalse(PluginConfigLoader.defaultPermissionValue(ClaimPermission.MOB_INTERACT, true));
        assertTrue(PluginConfigLoader.defaultPermissionValue(ClaimPermission.ANIMAL_SPAWN, true));
        assertTrue(PluginConfigLoader.defaultPermissionValue(ClaimPermission.MONSTER_SPAWN, true));
        assertFalse(PluginConfigLoader.defaultPermissionValue(ClaimPermission.INTERACT, true));
        assertTrue(PluginConfigLoader.defaultPermissionValue(ClaimPermission.TELEPORT, true));
        assertFalse(PluginConfigLoader.defaultPermissionValue(ClaimPermission.FLIGHT, true));
        assertEquals("deny", PluginConfigLoader.defaultFlagValue(ClaimFlag.LIQUID_FLOW, true));
    }

    @Test
    void permissionKeysUseConfigFriendlyNames() {
        assertEquals("mob-interact", ClaimPermission.MOB_INTERACT.key());
        assertEquals("animal-spawn", ClaimPermission.ANIMAL_SPAWN.key());
        assertEquals("monster-spawn", ClaimPermission.MONSTER_SPAWN.key());
    }

    @Test
    void extendedPermissionDefaultsLoadFromPermissionsSection() {
        YamlConfiguration rules = new YamlConfiguration();
        rules.set("new-claim-defaults.permissions.liquid-flow", "allow");
        rules.set("new-claim-defaults.permissions.time-cycle", "night");

        Map<ClaimFlag, ClaimFlagState> defaults = PluginConfigLoader.loadNewClaimFlagDefaults(rules, new YamlConfiguration());

        assertEquals(ClaimFlagState.ALLOW, defaults.get(ClaimFlag.LIQUID_FLOW));
        assertEquals(ClaimFlagState.DENY, defaults.get(ClaimFlag.TIME_CYCLE));
    }

    @Test
    void legacyFlagDefaultsRemainReadable() {
        YamlConfiguration rules = new YamlConfiguration();
        rules.set("new-claim-defaults.flags.liquid-flow", "deny");
        rules.set("new-claim-defaults.flags.time-cycle", "day");

        Map<ClaimFlag, ClaimFlagState> defaults = PluginConfigLoader.loadNewClaimFlagDefaults(rules, new YamlConfiguration());

        assertEquals(ClaimFlagState.DENY, defaults.get(ClaimFlag.LIQUID_FLOW));
        assertEquals(ClaimFlagState.ALLOW, defaults.get(ClaimFlag.TIME_CYCLE));
    }

    @Test
    void claimSpacingDefaultsAllowAdjacentClaims() {
        PluginConfig config = new PluginConfig(new YamlConfiguration(), new YamlConfiguration());

        assertEquals(0, config.minimumGap());
        assertEquals(0, config.minimumCoreSpacing());
        assertEquals(0, config.selectionMinimumGap());
    }

    @Test
    void explicitPositiveClaimSpacingStillLoads() {
        YamlConfiguration rawConfig = new YamlConfiguration();
        rawConfig.set("minimum-gap", 8);
        rawConfig.set("selection-minimum-gap", 3);

        PluginConfig config = new PluginConfig(rawConfig, new YamlConfiguration());

        assertEquals(8, config.minimumGap());
        assertEquals(8, config.minimumCoreSpacing());
        assertEquals(3, config.selectionMinimumGap());
    }

    @Test
    void redisMessageSecretLoadsFromClaimSyncSettings() {
        YamlConfiguration rawConfig = new YamlConfiguration();
        rawConfig.set("claim-sync.enabled", true);
        rawConfig.set("claim-sync.redis.message-secret", "secret-value");

        PluginConfig config = new PluginConfig(rawConfig, new YamlConfiguration());

        assertTrue(config.claimSync().hasRedisMessageSecret());
        assertEquals("secret-value", config.claimSync().redisMessageSecret());
    }
}
