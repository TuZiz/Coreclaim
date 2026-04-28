package com.coreclaim;

import com.coreclaim.model.ClaimFlag;
import com.coreclaim.model.ClaimFlagState;
import com.coreclaim.model.PermissionMergeSupport;
import java.util.Set;
import org.bukkit.configuration.file.FileConfiguration;

final class RuleDefaultsRepair {

    private RuleDefaultsRepair() {
    }

    static boolean applyKnownReplacements(FileConfiguration rulesConfig, FileConfiguration defaults) {
        boolean changed = false;
        changed |= replaceBooleanIfExact(rulesConfig, defaults, "new-claim-defaults.permissions.flight", true);
        changed |= mergeInteractAndContainer(rulesConfig, "new-claim-defaults.permissions");
        changed |= mergeInteractAndContainer(rulesConfig, "system-claim-defaults.permissions");
        changed |= mergeLegacyFlagGroup(
            rulesConfig,
            "new-claim-defaults",
            "interact",
            ClaimFlag.legacyInteractKeys()
        );
        changed |= mergeLegacyFlagGroup(
            rulesConfig,
            "system-claim-defaults",
            "interact",
            ClaimFlag.legacyInteractKeys()
        );
        changed |= mergeLegacyFlagGroup(
            rulesConfig,
            "new-claim-defaults",
            "redstone",
            ClaimFlag.legacyRedstoneKeys()
        );
        changed |= mergeLegacyFlagGroup(
            rulesConfig,
            "system-claim-defaults",
            "redstone",
            ClaimFlag.legacyRedstoneKeys()
        );
        return changed;
    }

    private static boolean mergeInteractAndContainer(FileConfiguration rulesConfig, String permissionsPath) {
        String interactPath = permissionsPath + ".interact";
        String containerPath = permissionsPath + ".container";
        if (!rulesConfig.isSet(containerPath)) {
            return false;
        }
        boolean merged = PermissionMergeSupport.mergeInteractAndContainer(
            rulesConfig.getBoolean(interactPath, false),
            rulesConfig.getBoolean(containerPath, false)
        );
        if (!rulesConfig.isBoolean(interactPath) || rulesConfig.getBoolean(interactPath) != merged) {
            rulesConfig.set(interactPath, merged);
        }
        rulesConfig.set(containerPath, null);
        return true;
    }

    private static boolean mergeLegacyFlagGroup(
        FileConfiguration rulesConfig,
        String defaultsPath,
        String permissionKey,
        Set<String> legacyKeys
    ) {
        String permissionPath = defaultsPath + ".permissions." + permissionKey;
        boolean currentAllowed = rulesConfig.getBoolean(permissionPath, false);
        boolean seenLegacy = false;
        boolean hasDeny = false;
        for (String legacyKey : legacyKeys) {
            String path = defaultsPath + ".flags." + legacyKey;
            if (!rulesConfig.isSet(path)) {
                continue;
            }
            seenLegacy = true;
            ClaimFlagState state = ClaimFlagState.fromInput(rulesConfig.getString(path));
            if (state == ClaimFlagState.DENY) {
                hasDeny = true;
            }
        }
        boolean changed = false;
        if (seenLegacy) {
            boolean merged = hasDeny ? false : currentAllowed;
            if (!rulesConfig.isBoolean(permissionPath) || rulesConfig.getBoolean(permissionPath) != merged) {
                rulesConfig.set(permissionPath, merged);
                changed = true;
            }
            for (String legacyKey : legacyKeys) {
                String path = defaultsPath + ".flags." + legacyKey;
                if (rulesConfig.isSet(path)) {
                    rulesConfig.set(path, null);
                    changed = true;
                }
            }
        }
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

}
