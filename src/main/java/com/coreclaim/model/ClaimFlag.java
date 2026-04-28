package com.coreclaim.model;

import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import org.bukkit.Material;

public enum ClaimFlag {
    TIME_CYCLE("time-cycle", "Claim Time", ClaimPermission.INTERACT),
    LIQUID_FLOW("liquid-flow", "液体流入权限", ClaimPermission.INTERACT);

    private static final Set<String> LEGACY_INTERACT_KEYS = Set.of(
        "container",
        "use-door",
        "use-trapdoor",
        "use-fence-gate",
        "use-bed"
    );
    private static final Set<String> LEGACY_REDSTONE_KEYS = Set.of(
        "use-button",
        "use-lever",
        "use-pressure-plate"
    );

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
        return null;
    }

    public static boolean isContainerMaterial(Material material) {
        if (material == null) {
            return false;
        }
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

    public static Set<String> legacyInteractKeys() {
        return LEGACY_INTERACT_KEYS;
    }

    public static Set<String> legacyRedstoneKeys() {
        return LEGACY_REDSTONE_KEYS;
    }

    public static boolean isLegacyKey(String rawValue) {
        String normalized = normalizeLegacyKey(rawValue);
        return LEGACY_INTERACT_KEYS.contains(normalized) || LEGACY_REDSTONE_KEYS.contains(normalized);
    }

    public static boolean isLegacyInteractKey(String rawValue) {
        return LEGACY_INTERACT_KEYS.contains(normalizeLegacyKey(rawValue));
    }

    public static boolean isLegacyRedstoneKey(String rawValue) {
        return LEGACY_REDSTONE_KEYS.contains(normalizeLegacyKey(rawValue));
    }

    private static String normalizeLegacyKey(String rawValue) {
        if (rawValue == null) {
            return "";
        }
        return rawValue.trim().toLowerCase(Locale.ROOT).replace('_', '-');
    }
}
