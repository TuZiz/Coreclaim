package com.coreclaim.service;

import java.util.Locale;

public enum ClaimSyncEventType {
    CLAIM_CREATED("claim_created"),
    CLAIM_DELETED("claim_deleted"),
    CLAIM_UPDATED("claim_updated"),
    CLAIM_SERVER_CHANGED("claim_server_changed"),
    CLAIM_OWNER_CHANGED("claim_owner_changed"),
    CLAIMS_RELOADED("claims_reloaded");

    private final String wireName;

    ClaimSyncEventType(String wireName) {
        this.wireName = wireName;
    }

    public String wireName() {
        return wireName;
    }

    public static ClaimSyncEventType fromWireName(String rawValue) {
        if (rawValue == null || rawValue.isBlank()) {
            return null;
        }
        String normalized = rawValue.trim().toLowerCase(Locale.ROOT);
        for (ClaimSyncEventType type : values()) {
            if (type.wireName.equals(normalized)) {
                return type;
            }
        }
        return null;
    }
}
