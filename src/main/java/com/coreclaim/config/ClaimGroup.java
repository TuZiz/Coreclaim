package com.coreclaim.config;

import org.bukkit.permissions.Permissible;

public record ClaimGroup(
    String key,
    String displayName,
    int priority,
    String permission,
    int initialDistance,
    int maxDistance,
    double coreCreatePricePerBlock,
    double selectionCreatePricePerBlock,
    double expandPricePerBlock,
    int maxClaims
) {

    public ClaimGroup {
        maxClaims = Math.max(1, maxClaims);
    }

    public boolean matches(Permissible permissible) {
        return permission == null || permission.isBlank() || permissible.hasPermission(permission);
    }

    public int claimSlotsForActivity(int activityPoints) {
        return maxClaims;
    }
}
