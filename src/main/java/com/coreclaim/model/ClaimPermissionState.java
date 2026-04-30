package com.coreclaim.model;

final class ClaimPermissionState {

    private boolean allowPlace;
    private boolean allowBreak;
    private boolean allowInteract;
    private boolean allowMobInteract;
    private boolean allowRedstone;
    private boolean allowExplosion;
    private boolean allowBucket;
    private boolean allowTeleport;
    private boolean allowFlight;

    ClaimPermissionState(
        boolean allowPlace,
        boolean allowBreak,
        boolean allowInteract,
        boolean allowContainer,
        boolean allowMobInteract,
        boolean allowRedstone,
        boolean allowExplosion,
        boolean allowBucket,
        boolean allowTeleport,
        boolean allowFlight
    ) {
        this.allowPlace = allowPlace;
        this.allowBreak = allowBreak;
        this.allowInteract = allowInteract && allowContainer;
        this.allowMobInteract = allowMobInteract;
        this.allowRedstone = allowRedstone;
        this.allowExplosion = allowExplosion;
        this.allowBucket = allowBucket;
        this.allowTeleport = allowTeleport;
        this.allowFlight = allowFlight;
    }

    boolean allowed(ClaimPermission permission) {
        if (permission == null) {
            return allowFlight;
        }
        return switch (permission) {
            case PLACE -> allowPlace;
            case BREAK -> allowBreak;
            case INTERACT -> allowInteract;
            case MOB_INTERACT -> allowMobInteract;
            case REDSTONE -> allowRedstone;
            case EXPLOSION -> allowExplosion;
            case BUCKET -> allowBucket;
            case TELEPORT -> allowTeleport;
            case FLIGHT -> allowFlight;
        };
    }

    void setAllowed(ClaimPermission permission, boolean allowed) {
        if (permission == null) {
            allowFlight = allowed;
            return;
        }
        switch (permission) {
            case PLACE -> allowPlace = allowed;
            case BREAK -> allowBreak = allowed;
            case INTERACT -> allowInteract = allowed;
            case MOB_INTERACT -> allowMobInteract = allowed;
            case REDSTONE -> allowRedstone = allowed;
            case EXPLOSION -> allowExplosion = allowed;
            case BUCKET -> allowBucket = allowed;
            case TELEPORT -> allowTeleport = allowed;
            case FLIGHT -> allowFlight = allowed;
        }
    }
}
