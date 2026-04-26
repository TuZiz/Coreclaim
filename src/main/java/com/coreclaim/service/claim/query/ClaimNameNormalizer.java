package com.coreclaim.service.claim.query;

import java.util.Locale;

final class ClaimNameNormalizer {

    private ClaimNameNormalizer() {
    }

    static String normalize(String rawName) {
        String sanitizedName = sanitize(rawName);
        if (sanitizedName == null) {
            return null;
        }
        return sanitizedName.toLowerCase(Locale.ROOT);
    }

    static String sanitize(String rawName) {
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
