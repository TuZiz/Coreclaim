package com.coreclaim.model;

import java.util.Locale;

public enum ClaimPermission {
    PLACE,
    BREAK,
    INTERACT,
    MOB_INTERACT,
    ANIMAL_SPAWN,
    MONSTER_SPAWN,
    REDSTONE,
    EXPLOSION,
    BUCKET,
    TELEPORT,
    FLIGHT;

    public String key() {
        return name().toLowerCase(Locale.ROOT).replace('_', '-');
    }
}
