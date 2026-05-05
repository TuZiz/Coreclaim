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

    public boolean hasDistanceLimit() {
        return maxDistance > 0;
    }

    public int remainingDistance(int currentDistance) {
        if (!hasDistanceLimit()) {
            return Integer.MAX_VALUE;
        }
        return Math.max(0, maxDistance - currentDistance);
    }

    public int clampExpandAmount(int currentDistance, int requestedAmount) {
        int positiveAmount = Math.max(0, requestedAmount);
        return hasDistanceLimit()
            ? Math.min(positiveAmount, remainingDistance(currentDistance))
            : positiveAmount;
    }

    public boolean exceedsDistanceLimit(int east, int south, int west, int north) {
        return hasDistanceLimit() && Math.max(Math.max(east, west), Math.max(north, south)) > maxDistance;
    }
}
