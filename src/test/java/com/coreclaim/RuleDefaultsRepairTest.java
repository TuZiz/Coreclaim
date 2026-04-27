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
        defaults.set("new-claim-defaults.flags.use-door", "deny");
        defaults.set("new-claim-defaults.flags.use-trapdoor", "deny");
        defaults.set("new-claim-defaults.flags.use-fence-gate", "deny");
        defaults.set("new-claim-defaults.flags.use-bed", "deny");

        YamlConfiguration existing = new YamlConfiguration();
        existing.set("new-claim-defaults.permissions.flight", true);
        existing.set("new-claim-defaults.flags.use-door", "allow");
        existing.set("new-claim-defaults.flags.use-trapdoor", "allow");
        existing.set("new-claim-defaults.flags.use-fence-gate", "allow");
        existing.set("new-claim-defaults.flags.use-bed", "allow");

        assertTrue(RuleDefaultsRepair.applyKnownReplacements(existing, defaults));
        assertFalse(existing.getBoolean("new-claim-defaults.permissions.flight"));
        assertEquals("deny", existing.getString("new-claim-defaults.flags.use-door"));
        assertEquals("deny", existing.getString("new-claim-defaults.flags.use-trapdoor"));
        assertEquals("deny", existing.getString("new-claim-defaults.flags.use-fence-gate"));
        assertEquals("deny", existing.getString("new-claim-defaults.flags.use-bed"));
    }
}
