package com.coreclaim.claim.query;

import java.util.Locale;

public final class ClaimNameNormalizer {

    private ClaimNameNormalizer() {
    }

    public static String normalize(String rawName) {
        String sanitizedName = sanitize(rawName);
        if (sanitizedName == null) {
            return null;
        }
        return sanitizedName.toLowerCase(Locale.ROOT);
    }

    public static String sanitize(String rawName) {
        if (rawName == null) {
            return null;
        }
        String trimmed = rawName.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        return trimmed.replaceAll("\\s+", " ");
    }
}
