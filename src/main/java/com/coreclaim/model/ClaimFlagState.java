package com.coreclaim.model;

import java.util.Locale;

public enum ClaimFlagState {
    UNSET(0),
    ALLOW(1),
    DENY(2);

    private final int databaseValue;

    ClaimFlagState(int databaseValue) {
        this.databaseValue = databaseValue;
    }

    public int databaseValue() {
        return databaseValue;
    }

    public boolean resolve(boolean fallback) {
        return switch (this) {
            case ALLOW -> true;
            case DENY -> false;
            case UNSET -> fallback;
        };
    }

    public ClaimFlagState next() {
        return switch (this) {
            case UNSET -> ALLOW;
            case ALLOW -> DENY;
            case DENY -> UNSET;
        };
    }

    public static ClaimFlagState fromDatabase(int rawValue) {
        return switch (rawValue) {
            case 1 -> ALLOW;
            case 2 -> DENY;
            default -> UNSET;
        };
    }

    public static ClaimFlagState fromInput(String rawValue) {
        if (rawValue == null || rawValue.isBlank()) {
            return null;
        }
        String normalized = rawValue.trim().toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "allow", "on", "true", "yes" -> ALLOW;
            case "deny", "off", "false", "no" -> DENY;
            case "unset", "reset", "clear", "default" -> UNSET;
            default -> null;
        };
    }
}
