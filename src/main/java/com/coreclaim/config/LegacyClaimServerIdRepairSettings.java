package com.coreclaim.config;

import java.util.Collections;
import java.util.Locale;
import java.util.Map;

public record LegacyClaimServerIdRepairSettings(
    boolean enabled,
    String defaultServerId,
    Map<String, String> worldMap
) {

    public LegacyClaimServerIdRepairSettings {
        defaultServerId = defaultServerId == null ? "" : defaultServerId.trim();
        worldMap = worldMap == null ? Map.of() : Collections.unmodifiableMap(worldMap);
    }

    public String targetServerId(String worldName) {
        if (worldName != null && !worldName.isBlank()) {
            String mapped = worldMap.get(worldName.trim().toLowerCase(Locale.ROOT));
            if (mapped != null && !mapped.isBlank()) {
                return mapped.trim();
            }
        }
        return defaultServerId;
    }

    public boolean hasAnyRepairTarget() {
        return !defaultServerId.isBlank() || !worldMap.isEmpty();
    }
}
