package com.coreclaim.model;

public enum ClaimCleanupReason {
    NONE(""),
    NO_BUILD("NO_BUILD"),
    NEVER_INTERACTED("NEVER_INTERACTED"),
    NO_BUILD_AND_NEVER_INTERACTED("NO_BUILD_AND_NEVER_INTERACTED");

    private final String key;

    ClaimCleanupReason(String key) {
        this.key = key;
    }

    public String key() {
        return key;
    }

    public static ClaimCleanupReason fromEvidence(boolean hasBuildEvidence, boolean hasInteractionEvidence) {
        if (!hasBuildEvidence && !hasInteractionEvidence) {
            return NO_BUILD_AND_NEVER_INTERACTED;
        }
        if (!hasBuildEvidence) {
            return NO_BUILD;
        }
        if (!hasInteractionEvidence) {
            return NEVER_INTERACTED;
        }
        return NONE;
    }

    public static ClaimCleanupReason fromKey(String rawKey) {
        if (rawKey == null || rawKey.isBlank()) {
            return NONE;
        }
        for (ClaimCleanupReason reason : values()) {
            if (reason.key.equalsIgnoreCase(rawKey)) {
                return reason;
            }
        }
        return NONE;
    }
}
