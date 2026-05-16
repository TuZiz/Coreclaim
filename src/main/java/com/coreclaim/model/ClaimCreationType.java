package com.coreclaim.model;

import java.util.Locale;

public enum ClaimCreationType {
    CORE,
    SELECTION,
    SYSTEM_SELECTION,
    UNKNOWN_LEGACY;

    public static ClaimCreationType fromDatabase(String value) {
        if (value == null || value.isBlank()) {
            return UNKNOWN_LEGACY;
        }
        try {
            return ClaimCreationType.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            return UNKNOWN_LEGACY;
        }
    }

    public String databaseValue() {
        return name();
    }
}
