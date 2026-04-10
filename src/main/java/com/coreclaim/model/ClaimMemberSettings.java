package com.coreclaim.model;

public final class ClaimMemberSettings {

    private boolean allowPlace;
    private boolean allowBreak;
    private boolean allowInteract;
    private boolean allowContainer;
    private boolean allowRedstone;
    private boolean allowExplosion;
    private boolean allowBucket;
    private boolean allowTeleport;

    public ClaimMemberSettings(
        boolean allowPlace,
        boolean allowBreak,
        boolean allowInteract,
        boolean allowContainer,
        boolean allowRedstone,
        boolean allowExplosion,
        boolean allowBucket,
        boolean allowTeleport
    ) {
        this.allowPlace = allowPlace;
        this.allowBreak = allowBreak;
        this.allowInteract = allowInteract;
        this.allowContainer = allowContainer;
        this.allowRedstone = allowRedstone;
        this.allowExplosion = allowExplosion;
        this.allowBucket = allowBucket;
        this.allowTeleport = allowTeleport;
    }

    public boolean permission(ClaimPermission permission) {
        if (permission == ClaimPermission.PLACE) {
            return allowPlace;
        }
        if (permission == ClaimPermission.BREAK) {
            return allowBreak;
        }
        if (permission == ClaimPermission.INTERACT) {
            return allowInteract;
        }
        if (permission == ClaimPermission.CONTAINER) {
            return allowContainer;
        }
        if (permission == ClaimPermission.REDSTONE) {
            return allowRedstone;
        }
        if (permission == ClaimPermission.EXPLOSION) {
            return allowExplosion;
        }
        if (permission == ClaimPermission.BUCKET) {
            return allowBucket;
        }
        return allowTeleport;
    }

    public void setPermission(ClaimPermission permission, boolean allowed) {
        if (permission == ClaimPermission.PLACE) {
            allowPlace = allowed;
            return;
        }
        if (permission == ClaimPermission.BREAK) {
            allowBreak = allowed;
            return;
        }
        if (permission == ClaimPermission.INTERACT) {
            allowInteract = allowed;
            return;
        }
        if (permission == ClaimPermission.CONTAINER) {
            allowContainer = allowed;
            return;
        }
        if (permission == ClaimPermission.REDSTONE) {
            allowRedstone = allowed;
            return;
        }
        if (permission == ClaimPermission.EXPLOSION) {
            allowExplosion = allowed;
            return;
        }
        if (permission == ClaimPermission.BUCKET) {
            allowBucket = allowed;
            return;
        }
        allowTeleport = allowed;
    }
}
