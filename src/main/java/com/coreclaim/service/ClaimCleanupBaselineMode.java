package com.coreclaim.service;

import java.util.Locale;

public enum ClaimCleanupBaselineMode {
    EMPTY,
    USED,
    SKIP;

    public static ClaimCleanupBaselineMode fromInput(String rawInput) {
        if (rawInput == null || rawInput.isBlank()) {
            return null;
        }
        try {
            return valueOf(rawInput.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }
}
