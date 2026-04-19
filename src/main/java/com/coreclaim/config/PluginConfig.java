package com.coreclaim.config;

import com.coreclaim.model.ClaimFlag;
import com.coreclaim.model.ClaimFlagState;
import com.coreclaim.model.ClaimPermission;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class PluginConfig {

    private final String claimWorld;
    private final List<String> claimWorlds;
    private final Set<String> claimWorldNamesLower;
    private final boolean claimWorldRestrictionEnabled;
    private final String serverId;
    private final String normalizedServerId;
    private final int starterRewardMinutes;
    private final int starterReclaimReminderIntervalMinutes;
    private final int directionExpandAmount;
    private final int minimumCoreSpacing;
    private final int selectionMinimumGap;
    private final int minimumGap;
    private final int claimNameMaxLength;
    private final int chatInputTimeoutSeconds;
    private final Material selectionToolMaterial;
    private final String selectionToolName;
    private final List<String> selectionToolLore;
    private final boolean selectionToolGlow;
    private final int selectionToolCustomModelData;
    private final Material coreMaterial;
    private final String centerCoreHologramText;
    private final double centerCoreHologramHeight;
    private final int expandCooldownSeconds;
    private final boolean warnOnSecondClaim;
    private final int coreUseMinActivity;
    private final long flightExitGraceTicks;
    private final long flightReconcileIntervalTicks;
    private final boolean flightDebug;
    private final Set<Material> allowInteract;
    private final boolean allowEnderPearlEntry;
    private final boolean allowChorusFruitEntry;
    private final boolean allowPortalEntry;
    private final boolean allowFishingHookInteract;
    private final boolean allowVehicleCrossBorder;
    private final boolean blockSpecialExplosiveUse;
    private final boolean strictRedstoneInteract;
    private final Set<Material> alwaysProtectedInteract;
    private final int claimVisualDurationSeconds;
    private final int claimVisualIntervalTicks;
    private final float claimVisualSize;
    private final int claimVisualPillarHeight;
    private final double claimVisualPillarStep;
    private final double claimVisualLineStep;
    private final int claimVisualRed;
    private final int claimVisualGreen;
    private final int claimVisualBlue;
    private final boolean crossServerTeleportEnabled;
    private final int crossServerTeleportPendingTimeoutSeconds;
    private final Map<String, String> legacyCrossServerWorldServerMap;
    private final Map<ClaimPermission, Boolean> newClaimPermissionDefaults;
    private final Map<ClaimPermission, Boolean> systemClaimPermissionDefaults;
    private final Map<ClaimFlag, ClaimFlagState> newClaimFlagDefaults;
    private final Map<ClaimFlag, ClaimFlagState> systemClaimFlagDefaults;
    private final ClaimSyncSettings claimSyncSettings;

    public PluginConfig(FileConfiguration config, FileConfiguration rulesConfig) {
        this.claimWorld = config.getString("world", "world");
        this.serverId = sanitizeServerId(config.getString("server-id", "local"), "local");
        this.normalizedServerId = serverId.toLowerCase(Locale.ROOT);
        Set<String> configuredClaimWorlds = new LinkedHashSet<>();
        for (String worldName : config.getStringList("claim-worlds")) {
            if (worldName != null && !worldName.isBlank()) {
                configuredClaimWorlds.add(worldName);
            }
        }
        this.claimWorldRestrictionEnabled = !configuredClaimWorlds.isEmpty();
        this.claimWorlds = List.copyOf(configuredClaimWorlds);
        this.claimWorldNamesLower = new HashSet<>();
        for (String worldName : configuredClaimWorlds) {
            if (worldName != null && !worldName.isBlank()) {
                this.claimWorldNamesLower.add(worldName.toLowerCase(java.util.Locale.ROOT));
            }
        }
        this.starterRewardMinutes = Math.max(1, config.getInt("starter-reward-minutes", 30));
        this.starterReclaimReminderIntervalMinutes = Math.max(1, config.getInt("starter-reclaim-reminder-interval-minutes", 5));
        this.directionExpandAmount = Math.max(1, config.getInt("direction-expand-amount", 10));
        this.minimumGap = Math.max(5, config.getInt("minimum-gap", config.getInt("minimum-core-spacing", 100)));
        this.minimumCoreSpacing = minimumGap;
        this.selectionMinimumGap = Math.max(0, config.getInt("selection-minimum-gap", 10));
        this.claimNameMaxLength = Math.max(3, config.getInt("claim-name-max-length", 16));
        this.chatInputTimeoutSeconds = Math.max(5, config.getInt("chat-input-timeout-seconds", 30));
        this.selectionToolMaterial = resolveMaterial(config.getString("selection-tool.material", "GOLDEN_HOE"), Material.GOLDEN_HOE);
        this.selectionToolName = config.getString("selection-tool.name", "&6&l圈地工具");
        this.selectionToolLore = new ArrayList<>(config.getStringList("selection-tool.lore"));
        this.selectionToolGlow = config.getBoolean("selection-tool.glow", false);
        this.selectionToolCustomModelData = Math.max(0, config.getInt("selection-tool.custom-model-data", 0));
        this.coreMaterial = resolveMaterial(config.getString("center-core.material",
            config.getString("claim-core.material",
                config.getString("core-tool.material", "AMETHYST_CLUSTER"))));
        this.centerCoreHologramText = config.getString("center-core.hologram-text", "&6%claim_name%");
        this.centerCoreHologramHeight = config.getDouble("center-core.hologram-height", 1.8D);
        this.expandCooldownSeconds = Math.max(0, config.getInt("expand-cooldown-seconds", 0));
        this.warnOnSecondClaim = config.getBoolean("warn-on-second-claim", false);
        this.coreUseMinActivity = Math.max(0, config.getInt("core-use-min-activity", 0));
        this.flightExitGraceTicks = Math.max(0L, config.getLong("flight.exit-grace-ticks", 20L));
        this.flightReconcileIntervalTicks = Math.max(1L, config.getLong("flight.reconcile-interval-ticks", 10L));
        this.flightDebug = config.getBoolean("flight.debug", false);
        this.allowInteract = resolveMaterials(config.getStringList("allow-interact"));
        this.allowEnderPearlEntry = config.getBoolean("protection.allow-ender-pearl-entry", false);
        this.allowChorusFruitEntry = config.getBoolean("protection.allow-chorus-fruit-entry", false);
        this.allowPortalEntry = config.getBoolean("protection.allow-portal-entry", false);
        this.allowFishingHookInteract = config.getBoolean("protection.allow-fishing-hook-interact", false);
        this.allowVehicleCrossBorder = config.getBoolean("protection.allow-vehicle-cross-border", false);
        this.blockSpecialExplosiveUse = config.getBoolean("protection.block-special-explosive-use", true);
        this.strictRedstoneInteract = config.getBoolean("protection.strict-redstone-interact", true);
        this.alwaysProtectedInteract = resolveMaterials(config.getStringList("protection.always-protected-interact"));
        this.claimVisualDurationSeconds = Math.max(1, config.getInt("claim-visual.duration-seconds", 10));
        this.claimVisualIntervalTicks = Math.max(1, config.getInt("claim-visual.interval-ticks", 10));
        this.claimVisualSize = (float) Math.max(0.1D, config.getDouble("claim-visual.size", 1.2D));
        this.claimVisualPillarHeight = Math.max(1, config.getInt("claim-visual.pillar-height", 12));
        this.claimVisualPillarStep = Math.max(0.2D, config.getDouble("claim-visual.pillar-step", 0.6D));
        this.claimVisualLineStep = Math.max(0.5D, config.getDouble("claim-visual.line-step", 3.0D));
        this.claimVisualRed = clampColor(config.getInt("claim-visual.color.red", 80));
        this.claimVisualGreen = clampColor(config.getInt("claim-visual.color.green", 255));
        this.claimVisualBlue = clampColor(config.getInt("claim-visual.color.blue", 140));
        this.crossServerTeleportEnabled = config.getBoolean("cross-server-teleport.enabled", false);
        this.crossServerTeleportPendingTimeoutSeconds = Math.max(10, config.getInt("cross-server-teleport.pending-timeout-seconds", 30));
        this.legacyCrossServerWorldServerMap = loadLegacyWorldServerMap(config);
        this.newClaimPermissionDefaults = loadPermissionDefaults(
            rulesConfig,
            config,
            "new-claim-defaults.permissions",
            "permissions.new-claim-defaults",
            false
        );
        this.systemClaimPermissionDefaults = loadPermissionDefaults(
            rulesConfig,
            config,
            "system-claim-defaults.permissions",
            "permissions.system-claim-defaults",
            true
        );
        this.newClaimFlagDefaults = loadNewClaimFlagDefaults(rulesConfig, config);
        this.systemClaimFlagDefaults = loadFlagDefaults(
            rulesConfig,
            config,
            "system-claim-defaults.flags",
            "system-claim-defaults",
            "flags.system-claim-defaults",
            true
        );
        this.claimSyncSettings = loadClaimSyncSettings(config);
    }

    private Material resolveMaterial(String name) {
        return resolveMaterial(name, Material.AMETHYST_CLUSTER);
    }

    private Material resolveMaterial(String name, Material fallback) {
        Material material = Material.matchMaterial(name == null ? "" : name);
        return material == null ? fallback : material;
    }

    private int clampColor(int value) {
        return Math.max(0, Math.min(255, value));
    }

    private String sanitizeServerId(String rawValue, String fallback) {
        String trimmed = rawValue == null ? "" : rawValue.trim();
        return trimmed.isEmpty() ? fallback : trimmed;
    }

    private Set<Material> resolveMaterials(List<String> names) {
        Set<Material> materials = new HashSet<>();
        for (String name : names) {
            Material material = Material.matchMaterial(name == null ? "" : name);
            if (material != null) {
                materials.add(material);
            }
        }
        return materials;
    }

    private Map<String, String> loadLegacyWorldServerMap(FileConfiguration config) {
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

    private ClaimSyncSettings loadClaimSyncSettings(FileConfiguration config) {
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

    private Map<ClaimPermission, Boolean> loadPermissionDefaults(
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

    private Map<ClaimFlag, ClaimFlagState> loadNewClaimFlagDefaults(FileConfiguration rulesConfig, FileConfiguration legacyConfig) {
        return loadFlagDefaults(rulesConfig, legacyConfig, "new-claim-defaults.flags", "new-claim-defaults", "flags.new-claim-defaults", false);
    }

    private Map<ClaimFlag, ClaimFlagState> loadFlagDefaults(
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

    private String readString(
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

    private boolean readBoolean(
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

    private boolean defaultPermissionValue(ClaimPermission permission, boolean systemDefaults) {
        if (!systemDefaults) {
            return permission == ClaimPermission.FLIGHT;
        }
        return switch (permission) {
            case INTERACT, TELEPORT -> true;
            default -> false;
        };
    }

    private String permissionKey(ClaimPermission permission) {
        return permission.name().toLowerCase(Locale.ROOT);
    }

    private String defaultFlagValue(ClaimFlag flag, boolean systemDefaults) {
        return switch (flag) {
            case CONTAINER, USE_BUTTON, USE_LEVER, USE_PRESSURE_PLATE -> "deny";
            case USE_DOOR, USE_TRAPDOOR, USE_FENCE_GATE, USE_BED -> "allow";
        };
    }

    public String claimWorld() {
        return claimWorld;
    }

    public List<String> claimWorlds() {
        return claimWorlds;
    }

    public String claimWorldsDisplay() {
        return claimWorldRestrictionEnabled ? String.join(", ", claimWorlds) : "全部世界";
    }

    public boolean isClaimWorld(String worldName) {
        if (worldName == null) {
            return false;
        }
        if (!claimWorldRestrictionEnabled) {
            return true;
        }
        return claimWorldNamesLower.contains(worldName.toLowerCase(java.util.Locale.ROOT));
    }

    public String serverId() {
        return serverId;
    }

    public boolean isCurrentServer(String targetServerId) {
        if (targetServerId == null || targetServerId.isBlank()) {
            return false;
        }
        return normalizedServerId.equals(targetServerId.trim().toLowerCase(Locale.ROOT));
    }

    public int starterRewardMinutes() {
        return starterRewardMinutes;
    }

    public int starterReclaimReminderIntervalMinutes() {
        return starterReclaimReminderIntervalMinutes;
    }

    public int directionExpandAmount() {
        return directionExpandAmount;
    }

    public int minimumCoreSpacing() {
        return minimumCoreSpacing;
    }

    public int minimumGap() {
        return minimumGap;
    }

    public int selectionMinimumGap() {
        return selectionMinimumGap;
    }

    public int claimNameMaxLength() {
        return claimNameMaxLength;
    }

    public int chatInputTimeoutSeconds() {
        return chatInputTimeoutSeconds;
    }

    public Material selectionToolMaterial() {
        return selectionToolMaterial;
    }

    public String selectionToolName() {
        return selectionToolName;
    }

    public List<String> selectionToolLore() {
        return List.copyOf(selectionToolLore);
    }

    public boolean selectionToolGlow() {
        return selectionToolGlow;
    }

    public int selectionToolCustomModelData() {
        return selectionToolCustomModelData;
    }

    public Material coreMaterial() {
        return coreMaterial;
    }

    public String centerCoreHologramText() {
        return centerCoreHologramText;
    }

    public double centerCoreHologramHeight() {
        return centerCoreHologramHeight;
    }

    public int expandCooldownSeconds() {
        return expandCooldownSeconds;
    }

    public boolean warnOnSecondClaim() {
        return warnOnSecondClaim;
    }

    public int coreUseMinActivity() {
        return coreUseMinActivity;
    }

    public long flightExitGraceTicks() {
        return flightExitGraceTicks;
    }

    public long flightReconcileIntervalTicks() {
        return flightReconcileIntervalTicks;
    }

    public boolean flightDebug() {
        return flightDebug;
    }

    public boolean isAllowedInteract(Material material) {
        return allowInteract.contains(material);
    }

    public boolean allowEnderPearlEntry() {
        return allowEnderPearlEntry;
    }

    public boolean allowChorusFruitEntry() {
        return allowChorusFruitEntry;
    }

    public boolean allowPortalEntry() {
        return allowPortalEntry;
    }

    public boolean allowFishingHookInteract() {
        return allowFishingHookInteract;
    }

    public boolean allowVehicleCrossBorder() {
        return allowVehicleCrossBorder;
    }

    public boolean blockSpecialExplosiveUse() {
        return blockSpecialExplosiveUse;
    }

    public boolean strictRedstoneInteract() {
        return strictRedstoneInteract;
    }

    public boolean isAlwaysProtectedInteract(Material material) {
        if (alwaysProtectedInteract.contains(material)) {
            return true;
        }
        String name = material.name();
        return name.endsWith("_BUTTON")
            || name.endsWith("_DOOR")
            || name.endsWith("_TRAPDOOR")
            || name.endsWith("_FENCE_GATE")
            || name.endsWith("_BED")
            || material == Material.LEVER
            || material == Material.BELL
            || material == Material.LECTERN
            || material == Material.NOTE_BLOCK
            || material == Material.RESPAWN_ANCHOR;
    }

    public int claimVisualDurationSeconds() {
        return claimVisualDurationSeconds;
    }

    public int claimVisualIntervalTicks() {
        return claimVisualIntervalTicks;
    }

    public float claimVisualSize() {
        return claimVisualSize;
    }

    public int claimVisualPillarHeight() {
        return claimVisualPillarHeight;
    }

    public double claimVisualPillarStep() {
        return claimVisualPillarStep;
    }

    public double claimVisualLineStep() {
        return claimVisualLineStep;
    }

    public int claimVisualRed() {
        return claimVisualRed;
    }

    public int claimVisualGreen() {
        return claimVisualGreen;
    }

    public int claimVisualBlue() {
        return claimVisualBlue;
    }

    public boolean crossServerTeleportEnabled() {
        return crossServerTeleportEnabled;
    }

    public int crossServerTeleportPendingTimeoutSeconds() {
        return crossServerTeleportPendingTimeoutSeconds;
    }

    public ClaimFlagState newClaimFlagDefault(ClaimFlag flag) {
        return newClaimFlagDefaults.getOrDefault(flag, ClaimFlagState.UNSET);
    }

    public Map<ClaimFlag, ClaimFlagState> newClaimFlagDefaults() {
        return newClaimFlagDefaults;
    }

    public Map<ClaimFlag, ClaimFlagState> systemClaimFlagDefaults() {
        return systemClaimFlagDefaults;
    }

    public Map<ClaimPermission, Boolean> newClaimPermissionDefaults() {
        return newClaimPermissionDefaults;
    }

    public Map<ClaimPermission, Boolean> systemClaimPermissionDefaults() {
        return systemClaimPermissionDefaults;
    }

    public boolean claimPermissionDefault(ClaimPermission permission, boolean systemManaged) {
        Map<ClaimPermission, Boolean> defaults = systemManaged ? systemClaimPermissionDefaults : newClaimPermissionDefaults;
        return defaults.getOrDefault(permission, defaultPermissionValue(permission, systemManaged));
    }

    public ClaimFlagState claimFlagDefault(ClaimFlag flag, boolean systemManaged) {
        Map<ClaimFlag, ClaimFlagState> defaults = systemManaged ? systemClaimFlagDefaults : newClaimFlagDefaults;
        return defaults.getOrDefault(flag, ClaimFlagState.UNSET);
    }

    public ClaimSyncSettings claimSync() {
        return claimSyncSettings;
    }

    public String resolveLegacyCrossServerTargetServer(String worldName) {
        if (worldName == null || worldName.isBlank()) {
            return null;
        }
        return legacyCrossServerWorldServerMap.get(worldName.toLowerCase(Locale.ROOT));
    }

    public record ClaimSyncSettings(
        boolean enabled,
        String transport,
        String redisHost,
        int redisPort,
        String redisPassword,
        int redisDatabase,
        String redisChannel,
        int reconnectSeconds
    ) {
        public boolean usesRedis() {
            return "redis".equalsIgnoreCase(transport);
        }

        public boolean hasRedisPassword() {
            return redisPassword != null && !redisPassword.isBlank();
        }
    }
}
