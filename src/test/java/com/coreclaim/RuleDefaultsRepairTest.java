package com.coreclaim;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

class RuleDefaultsRepairTest {

    @Test
    void repairsOldDangerousNewClaimDefaults() {
        YamlConfiguration defaults = new YamlConfiguration();
        defaults.set("new-claim-defaults.permissions.flight", false);
        defaults.set("new-claim-defaults.permissions.interact", false);
        defaults.set("new-claim-defaults.permissions.redstone", false);

        YamlConfiguration existing = new YamlConfiguration();
        existing.set("new-claim-defaults.permissions.flight", true);
        existing.set("new-claim-defaults.permissions.interact", true);
        existing.set("new-claim-defaults.permissions.container", false);
        existing.set("new-claim-defaults.permissions.redstone", true);
        existing.set("new-claim-defaults.flags.container", "allow");
        existing.set("new-claim-defaults.flags.use-button", "allow");
        existing.set("new-claim-defaults.flags.use-lever", "deny");
        existing.set("new-claim-defaults.flags.use-pressure-plate", "allow");
        existing.set("new-claim-defaults.flags.use-door", "allow");
        existing.set("new-claim-defaults.flags.use-trapdoor", "allow");
        existing.set("new-claim-defaults.flags.use-fence-gate", "allow");
        existing.set("new-claim-defaults.flags.use-bed", "allow");
        existing.set("new-claim-defaults.flags.liquid-flow", "allow");
        existing.set("new-claim-defaults.flags.time-cycle", "night");

        assertTrue(RuleDefaultsRepair.applyKnownReplacements(existing, defaults));
        assertFalse(existing.getBoolean("new-claim-defaults.permissions.flight"));
        assertFalse(existing.getBoolean("new-claim-defaults.permissions.interact"));
        assertFalse(existing.getBoolean("new-claim-defaults.permissions.redstone"));
        assertFalse(existing.isSet("new-claim-defaults.permissions.container"));
        assertFalse(existing.isSet("new-claim-defaults.flags.container"));
        assertFalse(existing.isSet("new-claim-defaults.flags.use-button"));
        assertFalse(existing.isSet("new-claim-defaults.flags.use-lever"));
        assertFalse(existing.isSet("new-claim-defaults.flags.use-pressure-plate"));
        assertFalse(existing.isSet("new-claim-defaults.flags.use-door"));
        assertFalse(existing.isSet("new-claim-defaults.flags.use-trapdoor"));
        assertFalse(existing.isSet("new-claim-defaults.flags.use-fence-gate"));
        assertFalse(existing.isSet("new-claim-defaults.flags.use-bed"));
        assertEquals("allow", existing.getString("new-claim-defaults.permissions.liquid-flow"));
        assertEquals("night", existing.getString("new-claim-defaults.permissions.time-cycle"));
        assertFalse(existing.isSet("new-claim-defaults.flags.liquid-flow"));
        assertFalse(existing.isSet("new-claim-defaults.flags.time-cycle"));
    }
}
