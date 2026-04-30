package com.coreclaim.model;

import java.util.Locale;

public enum ClaimPermission {
    PLACE,
    BREAK,
    INTERACT,
    MOB_INTERACT,
    REDSTONE,
    EXPLOSION,
    BUCKET,
    TELEPORT,
    FLIGHT;

    public String key() {
        return name().toLowerCase(Locale.ROOT).replace('_', '-');
    }
}
