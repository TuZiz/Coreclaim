package com.coreclaim.config;

import com.coreclaim.model.ClaimFlag;
import com.coreclaim.model.ClaimFlagState;
import com.coreclaim.model.ClaimPermission;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;

final class PluginConfigLoader {

    private PluginConfigLoader() {
    }

    static Material resolveMaterial(String name) {
        return resolveMaterial(name, Material.AMETHYST_CLUSTER);
    }

    static Material resolveMaterial(String name, Material fallback) {
        Material material = Material.matchMaterial(name == null ? "" : name);
        return material == null ? fallback : material;
    }

    static int clampColor(int value) {
        return Math.max(0, Math.min(255, value));
    }

    static String sanitizeServerId(String rawValue, String fallback) {
        String trimmed = rawValue == null ? "" : rawValue.trim();
        return trimmed.isEmpty() ? fallback : trimmed;
    }

    static Set<Material> resolveMaterials(List<String> names) {
        Set<Material> materials = new HashSet<>();
        for (String name : names) {
            Material material = Material.matchMaterial(name == null ? "" : name);
            if (material != null) {
                materials.add(material);
            }
        }
        return materials;
    }

    static Set<String> normalizeLowercaseValues(List<String> values) {
        Set<String> normalized = new LinkedHashSet<>();
        for (String value : values) {
            if (value == null || value.isBlank()) {
                continue;
            }
            normalized.add(value.trim().toLowerCase(Locale.ROOT));
        }
        return Collections.unmodifiableSet(normalized);
    }

    static Map<String, String> loadLegacyWorldServerMap(FileConfiguration config) {
        Map<String, String> mappings = new HashMap<>();
        String path = config.getConfigurationSection("cross-server-teleport.legacy-world-server-map") != null
            ? "cross-server-teleport.legacy-world-server-map"
            : "cross-server-teleport.world-server-map";
        if (config.getConfigurationSection(path) == null) {
            return Collections.emptyMap();
        }
        for (String worldName : config.getConfigurationSection(path).getKeys(false)) {
            String targetServer = sanitizeServerId(config.getString(path + "." + worldName, ""), "");
            if (worldName == null || worldName.isBlank() || targetServer.isBlank()) {
                continue;
            }
            mappings.put(worldName.toLowerCase(Locale.ROOT), targetServer);
        }
        return Collections.unmodifiableMap(mappings);
    }

    static ClaimSyncSettings loadClaimSyncSettings(FileConfiguration config) {
        String transport = sanitizeServerId(config.getString("claim-sync.transport", "redis"), "redis").toLowerCase(Locale.ROOT);
        return new ClaimSyncSettings(
            config.getBoolean("claim-sync.enabled", false),
            transport,
            sanitizeServerId(config.getString("claim-sync.redis.host", "127.0.0.1"), "127.0.0.1"),
            Math.max(1, config.getInt("claim-sync.redis.port", 6379)),
            config.getString("claim-sync.redis.password", ""),
            Math.max(0, config.getInt("claim-sync.redis.database", 0)),
            sanitizeServerId(config.getString("claim-sync.redis.channel", "coreclaim:claim-sync"), "coreclaim:claim-sync"),
            Math.max(1, config.getInt("claim-sync.redis.reconnect-seconds", 5))
        );
    }

    static Map<ClaimPermission, Boolean> loadPermissionDefaults(
        FileConfiguration primaryConfig,
        FileConfiguration legacyConfig,
        String primaryPath,
        String legacyPath,
        boolean systemDefaults
    ) {
        EnumMap<ClaimPermission, Boolean> defaults = new EnumMap<>(ClaimPermission.class);
        for (ClaimPermission permission : ClaimPermission.values()) {
            defaults.put(permission, readBoolean(
                primaryConfig,
                primaryPath + "." + permissionKey(permission),
                legacyConfig,
                legacyPath + "." + permissionKey(permission),
                defaultPermissionValue(permission, systemDefaults)
            ));
        }
        return Collections.unmodifiableMap(defaults);
    }

    static Map<ClaimFlag, ClaimFlagState> loadNewClaimFlagDefaults(FileConfiguration rulesConfig, FileConfiguration legacyConfig) {
        return loadFlagDefaults(rulesConfig, legacyConfig, "new-claim-defaults.flags", "new-claim-defaults", "flags.new-claim-defaults", false);
    }

    static Map<ClaimFlag, ClaimFlagState> loadFlagDefaults(
        FileConfiguration primaryConfig,
        FileConfiguration legacyConfig,
        String primaryPath,
        String legacyFilePath,
        String legacyPath,
        boolean systemDefaults
    ) {
        EnumMap<ClaimFlag, ClaimFlagState> defaults = new EnumMap<>(ClaimFlag.class);
        for (ClaimFlag flag : ClaimFlag.values()) {
            ClaimFlagState state = ClaimFlagState.fromInput(
                readString(
                    primaryConfig,
                    primaryPath + "." + flag.key(),
                    legacyConfig,
                    legacyFilePath + "." + flag.key(),
                    legacyPath + "." + flag.key(),
                    defaultFlagValue(flag, systemDefaults)
                )
            );
            defaults.put(flag, state == null ? ClaimFlagState.UNSET : state);
        }
        return Collections.unmodifiableMap(defaults);
    }

    static boolean defaultPermissionValue(ClaimPermission permission, boolean systemDefaults) {
        if (!systemDefaults) {
            return permission == ClaimPermission.FLIGHT;
        }
        return switch (permission) {
            case INTERACT, TELEPORT -> true;
            default -> false;
        };
    }

    private static String readString(
        FileConfiguration primaryConfig,
        String primaryPath,
        FileConfiguration fallbackConfig,
        String fallbackPrimaryPath,
        String fallbackSecondaryPath,
        String defaultValue
    ) {
        if (primaryConfig != null && primaryConfig.isSet(primaryPath)) {
            return primaryConfig.getString(primaryPath, defaultValue);
        }
        if (fallbackConfig != null && fallbackConfig.isSet(fallbackPrimaryPath)) {
            return fallbackConfig.getString(fallbackPrimaryPath, defaultValue);
        }
        if (fallbackConfig != null && fallbackConfig.isSet(fallbackSecondaryPath)) {
            return fallbackConfig.getString(fallbackSecondaryPath, defaultValue);
        }
        return defaultValue;
    }

    private static boolean readBoolean(
        FileConfiguration primaryConfig,
        String primaryPath,
        FileConfiguration fallbackConfig,
        String fallbackPath,
        boolean defaultValue
    ) {
        if (primaryConfig != null && primaryConfig.isSet(primaryPath)) {
            return primaryConfig.getBoolean(primaryPath, defaultValue);
        }
        if (fallbackConfig != null && fallbackConfig.isSet(fallbackPath)) {
            return fallbackConfig.getBoolean(fallbackPath, defaultValue);
        }
        return defaultValue;
    }

    private static String permissionKey(ClaimPermission permission) {
        return permission.name().toLowerCase(Locale.ROOT);
    }

    private static String defaultFlagValue(ClaimFlag flag, boolean systemDefaults) {
        return switch (flag) {
            case TIME_CYCLE -> "unset";
            case CONTAINER, USE_BUTTON, USE_LEVER, USE_PRESSURE_PLATE -> "deny";
            case USE_DOOR, USE_TRAPDOOR, USE_FENCE_GATE, USE_BED -> "allow";
        };
    }
}
