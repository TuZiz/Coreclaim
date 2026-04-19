package com.coreclaim.model;

import java.util.Arrays;
import java.util.Locale;
import org.bukkit.Material;

public enum ClaimFlag {
    CONTAINER("container", "容器交互", ClaimPermission.CONTAINER),
    USE_BUTTON("use-button", "按钮交互", ClaimPermission.REDSTONE),
    USE_LEVER("use-lever", "拉杆交互", ClaimPermission.REDSTONE),
    USE_PRESSURE_PLATE("use-pressure-plate", "压力板交互", ClaimPermission.REDSTONE),
    USE_DOOR("use-door", "门交互", ClaimPermission.INTERACT),
    USE_TRAPDOOR("use-trapdoor", "活板门交互", ClaimPermission.INTERACT),
    USE_FENCE_GATE("use-fence-gate", "栅栏门交互", ClaimPermission.INTERACT),
    USE_BED("use-bed", "床交互", ClaimPermission.INTERACT);

    private final String key;
    private final String displayName;
    private final ClaimPermission fallbackPermission;

    ClaimFlag(String key, String displayName, ClaimPermission fallbackPermission) {
        this.key = key;
        this.displayName = displayName;
        this.fallbackPermission = fallbackPermission;
    }

    public String key() {
        return key;
    }

    public String displayName() {
        return displayName;
    }

    public ClaimPermission fallbackPermission() {
        return fallbackPermission;
    }

    public static ClaimFlag fromKey(String rawValue) {
        if (rawValue == null || rawValue.isBlank()) {
            return null;
        }
        String normalized = rawValue.trim().toLowerCase(Locale.ROOT);
        return Arrays.stream(values())
            .filter(flag -> flag.key.equals(normalized)
                || flag.name().equalsIgnoreCase(normalized.replace('-', '_')))
            .findFirst()
            .orElse(null);
    }

    public static ClaimFlag fromInteraction(Material material) {
        if (material == null) {
            return null;
        }
        if (isContainerMaterial(material)) {
            return CONTAINER;
        }

        String materialName = material.name();
        if (materialName.endsWith("_BUTTON")) {
            return USE_BUTTON;
        }
        if (material == Material.LEVER) {
            return USE_LEVER;
        }
        if (materialName.endsWith("_PRESSURE_PLATE")) {
            return USE_PRESSURE_PLATE;
        }
        if (materialName.endsWith("_DOOR")) {
            return USE_DOOR;
        }
        if (materialName.endsWith("_TRAPDOOR")) {
            return USE_TRAPDOOR;
        }
        if (materialName.endsWith("_FENCE_GATE")) {
            return USE_FENCE_GATE;
        }
        if (materialName.endsWith("_BED")) {
            return USE_BED;
        }
        return null;
    }

    private static boolean isContainerMaterial(Material material) {
        String materialName = material.name();
        return materialName.endsWith("CHEST")
            || materialName.endsWith("BARREL")
            || materialName.endsWith("SHULKER_BOX")
            || material == Material.HOPPER
            || material == Material.DISPENSER
            || material == Material.DROPPER
            || material == Material.FURNACE
            || material == Material.BLAST_FURNACE
            || material == Material.SMOKER
            || material == Material.BREWING_STAND
            || material == Material.CHISELED_BOOKSHELF
            || material == Material.LECTERN;
    }
}
