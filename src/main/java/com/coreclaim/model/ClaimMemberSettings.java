package com.coreclaim.model;

public final class ClaimMemberSettings {

    private boolean allowPlace;
    private boolean allowBreak;
    private boolean allowInteract;
    private boolean allowMobInteract;
    private boolean allowAnimalSpawn;
    private boolean allowMonsterSpawn;
    private boolean allowRedstone;
    private boolean allowExplosion;
    private boolean allowBucket;
    private boolean allowTeleport;
    private boolean allowFlight;

    public ClaimMemberSettings(
        boolean allowPlace,
        boolean allowBreak,
        boolean allowInteract,
        boolean allowContainer,
        boolean allowMobInteract,
        boolean allowAnimalSpawn,
        boolean allowMonsterSpawn,
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
        this.allowAnimalSpawn = allowAnimalSpawn;
        this.allowMonsterSpawn = allowMonsterSpawn;
        this.allowRedstone = allowRedstone;
        this.allowExplosion = allowExplosion;
        this.allowBucket = allowBucket;
        this.allowTeleport = allowTeleport;
        this.allowFlight = allowFlight;
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
        if (permission == ClaimPermission.MOB_INTERACT) {
            return allowMobInteract;
        }
        if (permission == ClaimPermission.ANIMAL_SPAWN) {
            return allowAnimalSpawn;
        }
        if (permission == ClaimPermission.MONSTER_SPAWN) {
            return allowMonsterSpawn;
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
        if (permission == ClaimPermission.TELEPORT) {
            return allowTeleport;
        }
        return allowFlight;
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
        if (permission == ClaimPermission.MOB_INTERACT) {
            allowMobInteract = allowed;
            return;
        }
        if (permission == ClaimPermission.ANIMAL_SPAWN) {
            allowAnimalSpawn = allowed;
            return;
        }
        if (permission == ClaimPermission.MONSTER_SPAWN) {
            allowMonsterSpawn = allowed;
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
        if (permission == ClaimPermission.TELEPORT) {
            allowTeleport = allowed;
            return;
        }
        allowFlight = allowed;
    }
}
