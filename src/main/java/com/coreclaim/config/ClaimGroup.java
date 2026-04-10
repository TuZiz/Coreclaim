package com.coreclaim.config;

import java.util.Map;
import java.util.TreeMap;
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
    TreeMap<Integer, Integer> claimSlots
) {

    public ClaimGroup {
        claimSlots = new TreeMap<>(claimSlots);
        if (claimSlots.isEmpty()) {
            claimSlots.put(0, 1);
        }
    }

    public boolean matches(Permissible permissible) {
        return permission == null || permission.isBlank() || permissible.hasPermission(permission);
    }

    public int claimSlotsForActivity(int activityPoints) {
        Map.Entry<Integer, Integer> entry = claimSlots.floorEntry(Math.max(0, activityPoints));
        return entry == null ? claimSlots.firstEntry().getValue() : entry.getValue();
    }
}
