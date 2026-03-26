package com.coreclaim.model;

public final class ClaimMemberSettings {

    private boolean allowPlace;
    private boolean allowBreak;
    private boolean allowInteract;
    private boolean allowBucket;
    private boolean allowTeleport;

    public ClaimMemberSettings(
        boolean allowPlace,
        boolean allowBreak,
        boolean allowInteract,
        boolean allowBucket,
        boolean allowTeleport
    ) {
        this.allowPlace = allowPlace;
        this.allowBreak = allowBreak;
        this.allowInteract = allowInteract;
        this.allowBucket = allowBucket;
        this.allowTeleport = allowTeleport;
    }

    public boolean permission(ClaimPermission permission) {
        return switch (permission) {
            case PLACE -> allowPlace;
            case BREAK -> allowBreak;
            case INTERACT -> allowInteract;
            case BUCKET -> allowBucket;
            case TELEPORT -> allowTeleport;
        };
    }

    public void setPermission(ClaimPermission permission, boolean allowed) {
        switch (permission) {
            case PLACE -> allowPlace = allowed;
            case BREAK -> allowBreak = allowed;
            case INTERACT -> allowInteract = allowed;
            case BUCKET -> allowBucket = allowed;
            case TELEPORT -> allowTeleport = allowed;
        }
    }
}
