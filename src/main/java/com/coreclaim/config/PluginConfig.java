package com.coreclaim.config;

import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;

public final class PluginConfig {

    private final int starterRewardMinutes;
    private final int directionExpandAmount;
    private final int minimumCoreSpacing;
    private final int claimNameMaxLength;
    private final Material coreMaterial;
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
        this.starterRewardMinutes = Math.max(1, config.getInt("starter-reward-minutes", 30));
        this.directionExpandAmount = Math.max(1, config.getInt("direction-expand-amount", 10));
        this.minimumCoreSpacing = Math.max(1, config.getInt("minimum-core-spacing", 100));
        this.claimNameMaxLength = Math.max(3, config.getInt("claim-name-max-length", 16));
        this.coreMaterial = resolveMaterial(config.getString("claim-core.material", "AMETHYST_CLUSTER"));
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

    public int starterRewardMinutes() {
        return starterRewardMinutes;
    }

    public int directionExpandAmount() {
        return directionExpandAmount;
    }

    public int minimumCoreSpacing() {
        return minimumCoreSpacing;
    }

    public int claimNameMaxLength() {
        return claimNameMaxLength;
    }

    public Material coreMaterial() {
        return coreMaterial;
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
