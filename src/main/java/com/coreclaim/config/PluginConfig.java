package com.coreclaim.config;

import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class PluginConfig {

    private final String claimWorld;
    private final List<String> claimWorlds;
    private final Set<String> claimWorldNamesLower;
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

    public PluginConfig(FileConfiguration config) {
        this.claimWorld = config.getString("world", "world");
        Set<String> configuredClaimWorlds = new LinkedHashSet<>();
        for (String worldName : config.getStringList("claim-worlds")) {
            if (worldName != null && !worldName.isBlank()) {
                configuredClaimWorlds.add(worldName);
            }
        }
        if (configuredClaimWorlds.isEmpty()) {
            configuredClaimWorlds.add(this.claimWorld);
            if ("world".equalsIgnoreCase(this.claimWorld)) {
                configuredClaimWorlds.add("world_nether");
                configuredClaimWorlds.add("world_the_end");
            }
        }
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

    public String claimWorld() {
        return claimWorld;
    }

    public List<String> claimWorlds() {
        return claimWorlds;
    }

    public String claimWorldsDisplay() {
        return String.join(", ", claimWorlds);
    }

    public boolean isClaimWorld(String worldName) {
        return worldName != null && claimWorldNamesLower.contains(worldName.toLowerCase(java.util.Locale.ROOT));
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
}
