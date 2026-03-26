package com.coreclaim.config;

import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;

public final class PluginConfig {

    private final int starterRewardMinutes;
    private final int directionExpandAmount;
    private final int minimumCoreSpacing;
    private final int claimNameMaxLength;
    private final Material coreMaterial;

    public PluginConfig(FileConfiguration config) {
        this.starterRewardMinutes = Math.max(1, config.getInt("starter-reward-minutes", 30));
        this.directionExpandAmount = Math.max(1, config.getInt("direction-expand-amount", 10));
        this.minimumCoreSpacing = Math.max(1, config.getInt("minimum-core-spacing", 100));
        this.claimNameMaxLength = Math.max(3, config.getInt("claim-name-max-length", 16));
        this.coreMaterial = resolveMaterial(config.getString("claim-core.material", "AMETHYST_CLUSTER"));
    }

    private Material resolveMaterial(String name) {
        Material material = Material.matchMaterial(name == null ? "" : name);
        return material == null ? Material.AMETHYST_CLUSTER : material;
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
}
