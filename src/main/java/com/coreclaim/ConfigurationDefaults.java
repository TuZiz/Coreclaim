package com.coreclaim;

import java.util.ArrayList;
import java.util.List;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

final class ConfigurationDefaults {

    private ConfigurationDefaults() {
    }

    static List<String> missingPaths(ConfigurationSection defaults, FileConfiguration config) {
        List<String> missingPaths = new ArrayList<>();
        collectMissingConfigPaths(defaults, config, "", missingPaths);
        return missingPaths;
    }

    private static void collectMissingConfigPaths(
        ConfigurationSection defaults,
        FileConfiguration config,
        String prefix,
        List<String> missingPaths
    ) {
        for (String key : defaults.getKeys(false)) {
            String path = prefix.isEmpty() ? key : prefix + "." + key;
            Object value = defaults.get(key);
            if (value instanceof ConfigurationSection section) {
                collectMissingConfigPaths(section, config, path, missingPaths);
                continue;
            }
            if (!config.contains(path)) {
                missingPaths.add(path);
            }
        }
    }
}
