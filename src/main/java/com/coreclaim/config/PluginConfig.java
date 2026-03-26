package com.coreclaim.config;

import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class PluginConfig {

    private final String claimWorld;
    private final int starterRewardMinutes;
    private final int directionExpandAmount;
    private final int minimumCoreSpacing;
    private final int minimumGap;
    private final int claimNameMaxLength;
    private final int chatInputTimeoutSeconds;
    private final Material coreMaterial;
    private final String centerCoreHologramText;
    private final double centerCoreHologramHeight;
    private final int expandCooldownSeconds;
    private final boolean warnOnSecondClaim;
    private final int coreUseMinActivity;
    private final Set<Material> allowInteract;
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
        this.starterRewardMinutes = Math.max(1, config.getInt("starter-reward-minutes", 30));
        this.directionExpandAmount = Math.max(1, config.getInt("direction-expand-amount", 10));
        this.minimumGap = Math.max(5, config.getInt("minimum-gap", config.getInt("minimum-core-spacing", 100)));
        this.minimumCoreSpacing = minimumGap;
        this.claimNameMaxLength = Math.max(3, config.getInt("claim-name-max-length", 16));
        this.chatInputTimeoutSeconds = Math.max(5, config.getInt("chat-input-timeout-seconds", 30));
        this.coreMaterial = resolveMaterial(config.getString("center-core.material",
            config.getString("claim-core.material",
                config.getString("core-tool.material", "AMETHYST_CLUSTER"))));
        this.centerCoreHologramText = config.getString("center-core.hologram-text", "&6%claim_name%");
        this.centerCoreHologramHeight = config.getDouble("center-core.hologram-height", 1.8D);
        this.expandCooldownSeconds = Math.max(0, config.getInt("expand-cooldown-seconds", 0));
        this.warnOnSecondClaim = config.getBoolean("warn-on-second-claim", false);
        this.coreUseMinActivity = Math.max(0, config.getInt("core-use-min-activity", 0));
        this.allowInteract = resolveMaterials(config.getStringList("allow-interact"));
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
        Material material = Material.matchMaterial(name == null ? "" : name);
        return material == null ? Material.AMETHYST_CLUSTER : material;
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

    public int starterRewardMinutes() {
        return starterRewardMinutes;
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

    public int claimNameMaxLength() {
        return claimNameMaxLength;
    }

    public int chatInputTimeoutSeconds() {
        return chatInputTimeoutSeconds;
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
