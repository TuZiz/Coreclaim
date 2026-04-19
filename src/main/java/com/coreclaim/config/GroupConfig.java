package com.coreclaim.config;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.permissions.Permissible;

public final class GroupConfig {

    private final List<ClaimGroup> groups;
    private final ClaimGroup fallbackGroup;

    public GroupConfig(FileConfiguration configuration) {
        List<ClaimGroup> loadedGroups = new ArrayList<>();
        for (String key : configuration.getKeys(false)) {
            ConfigurationSection section = configuration.getConfigurationSection(key);
            if (section == null) {
                continue;
            }
            int maxClaims = resolveMaxClaims(section);
            loadedGroups.add(new ClaimGroup(
                key,
                section.getString("display-name", key),
                section.getInt("priority", 0),
                section.getString("permission", ""),
                Math.max(1, section.getInt("initial-distance", 8)),
                Math.max(1, section.getInt("max-distance", 50)),
                Math.max(0D, section.getDouble("core-create-price-per-block", 0D)),
                Math.max(0D, section.getDouble("selection-create-price-per-block", section.getDouble("expand-price-per-block", 50D))),
                Math.max(0D, section.getDouble("expand-price-per-block", 50D)),
                maxClaims
            ));
        }

        if (loadedGroups.isEmpty()) {
            loadedGroups.add(new ClaimGroup("default", "Default", 0, "", 8, 50, 0D, 50D, 50D, 3));
        }

        loadedGroups.sort(Comparator.comparingInt(ClaimGroup::priority).reversed());
        this.groups = List.copyOf(loadedGroups);
        this.fallbackGroup = groups.stream()
            .filter(group -> group.permission() == null || group.permission().isBlank())
            .findFirst()
            .orElse(groups.get(groups.size() - 1));
    }

    public ClaimGroup resolve(Permissible permissible) {
        if (permissible == null) {
            return fallbackGroup;
        }
        return groups.stream()
            .filter(group -> group.matches(permissible))
            .findFirst()
            .orElse(fallbackGroup);
    }

    public List<ClaimGroup> groups() {
        return groups;
    }

    private int resolveMaxClaims(ConfigurationSection section) {
        int configuredMax = section.getInt("max-claims", -1);
        if (configuredMax > 0) {
            return configuredMax;
        }

        int migratedMax = 0;
        ConfigurationSection slotsSection = section.getConfigurationSection("claim-slots");
        if (slotsSection != null) {
            for (String slotKey : slotsSection.getKeys(false)) {
                migratedMax = Math.max(migratedMax, Math.max(1, slotsSection.getInt(slotKey, 1)));
            }
        }
        return migratedMax > 0 ? migratedMax : 3;
    }
}
