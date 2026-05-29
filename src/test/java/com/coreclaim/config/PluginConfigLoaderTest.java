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
        assertEquals("utility-interact", ClaimPermission.UTILITY_INTERACT.key());
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
    void highFrequencyProtectionChecksDefaultToEnabled() {
        PluginConfig config = new PluginConfig(new YamlConfiguration(), new YamlConfiguration());

        assertTrue(config.hopperCrossClaimCheck());
        assertTrue(config.liquidFlowCrossClaimCheck());
        assertTrue(config.pistonCrossClaimCheck());
        assertTrue(config.inventoryPickupCrossClaimCheck());
    }

    @Test
    void highFrequencyProtectionChecksCanBeDisabledIndividually() {
        YamlConfiguration rawConfig = new YamlConfiguration();
        rawConfig.set("protection.hopper-cross-claim-check", false);
        rawConfig.set("protection.liquid-flow-cross-claim-check", false);
        rawConfig.set("protection.piston-cross-claim-check", false);
        rawConfig.set("protection.inventory-pickup-cross-claim-check", false);

        PluginConfig config = new PluginConfig(rawConfig, new YamlConfiguration());

        assertFalse(config.hopperCrossClaimCheck());
        assertFalse(config.liquidFlowCrossClaimCheck());
        assertFalse(config.pistonCrossClaimCheck());
        assertFalse(config.inventoryPickupCrossClaimCheck());
    }

    @Test
    void legacyClaimServerIdRepairDefaultsToDisabled() {
        PluginConfig config = new PluginConfig(new YamlConfiguration(), new YamlConfiguration());

        assertFalse(config.legacyClaimServerIdRepair().enabled());
        assertEquals("", config.legacyClaimServerIdRepair().defaultServerId());
        assertFalse(config.legacyClaimServerIdRepair().hasAnyRepairTarget());
    }

    @Test
    void legacyClaimServerIdRepairWorldMapOverridesDefaultServer() {
        YamlConfiguration rawConfig = new YamlConfiguration();
        rawConfig.set("legacy-claim-server-id-repair.enabled", true);
        rawConfig.set("legacy-claim-server-id-repair.default-server-id", "fallback");
        rawConfig.set("legacy-claim-server-id-repair.world-map.world_nether", "nether-server");

        PluginConfig config = new PluginConfig(rawConfig, new YamlConfiguration());

        assertTrue(config.legacyClaimServerIdRepair().enabled());
        assertEquals("nether-server", config.legacyClaimServerIdRepair().targetServerId("WORLD_NETHER"));
        assertEquals("fallback", config.legacyClaimServerIdRepair().targetServerId("world"));
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

    @Test
    void claimExpansionPricingDefaultsLoadWhenConfigMissing() {
        PluginConfig config = new PluginConfig(new YamlConfiguration(), new YamlConfiguration());

        assertTrue(config.claimExpansionPricing().legacyFullHeightClaimsAsCore());
        assertTrue(config.claimExpansionPricing().coreFullHeightEnabled());
        assertEquals(96, config.claimExpansionPricing().effectiveHeightCap());
        assertEquals(0.35D, config.claimExpansionPricing().heightPriceFactor(), 0.000001D);
        assertEquals(0.55D, config.claimExpansionPricing().fullHeightDiscount(), 0.000001D);
        assertEquals(-1D, config.claimExpansionPricing().maximumCostPerExpansion(), 0.000001D);
    }

    @Test
    void claimExpansionPricingInvalidValuesAreClamped() {
        YamlConfiguration rawConfig = new YamlConfiguration();
        rawConfig.set("claim-expansion-pricing.core-full-height.effective-height-cap", 0);
        rawConfig.set("claim-expansion-pricing.core-full-height.height-price-factor", -1D);
        rawConfig.set("claim-expansion-pricing.core-full-height.full-height-discount", -2D);
        rawConfig.set("claim-expansion-pricing.core-full-height.minimum-cost", -5D);
        rawConfig.set("claim-expansion-pricing.core-full-height.maximum-cost-per-expansion", 0D);

        PluginConfig config = new PluginConfig(rawConfig, new YamlConfiguration());

        assertEquals(96, config.claimExpansionPricing().effectiveHeightCap());
        assertEquals(0D, config.claimExpansionPricing().heightPriceFactor(), 0.000001D);
        assertEquals(0D, config.claimExpansionPricing().fullHeightDiscount(), 0.000001D);
        assertEquals(0D, config.claimExpansionPricing().minimumCost(), 0.000001D);
        assertEquals(-1D, config.claimExpansionPricing().maximumCostPerExpansion(), 0.000001D);
    }
}
