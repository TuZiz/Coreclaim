package com.coreclaim;

import org.bukkit.configuration.file.FileConfiguration;

final class RuleDefaultsRepair {

    private RuleDefaultsRepair() {
    }

    static boolean applyKnownReplacements(FileConfiguration rulesConfig, FileConfiguration defaults) {
        boolean changed = false;
        changed |= replaceBooleanIfExact(rulesConfig, defaults, "new-claim-defaults.permissions.flight", true);
        changed |= replaceStringIfExact(rulesConfig, defaults, "new-claim-defaults.flags.use-door", "allow");
        changed |= replaceStringIfExact(rulesConfig, defaults, "new-claim-defaults.flags.use-trapdoor", "allow");
        changed |= replaceStringIfExact(rulesConfig, defaults, "new-claim-defaults.flags.use-fence-gate", "allow");
        changed |= replaceStringIfExact(rulesConfig, defaults, "new-claim-defaults.flags.use-bed", "allow");
        return changed;
    }

    private static boolean replaceBooleanIfExact(
        FileConfiguration rulesConfig,
        FileConfiguration defaults,
        String path,
        boolean oldValue
    ) {
        if (!rulesConfig.isBoolean(path) || rulesConfig.getBoolean(path) != oldValue) {
            return false;
        }
        rulesConfig.set(path, defaults.getBoolean(path, oldValue));
        return true;
    }

    private static boolean replaceStringIfExact(
        FileConfiguration rulesConfig,
        FileConfiguration defaults,
        String path,
        String oldValue
    ) {
        if (!oldValue.equalsIgnoreCase(rulesConfig.getString(path, ""))) {
            return false;
        }
        rulesConfig.set(path, defaults.getString(path, oldValue));
        return true;
    }
}
