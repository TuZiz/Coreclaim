package com.coreclaim.cleanup;

import com.coreclaim.model.ClaimCleanupReason;
import com.coreclaim.model.ClaimCleanupState;

final class ClaimCleanupStateSupport {

    private ClaimCleanupStateSupport() {
    }

    static ClaimCleanupReason reasonFromState(ClaimCleanupState state) {
        if (state == null) {
            return ClaimCleanupReason.NONE;
        }
        return ClaimCleanupReason.fromEvidence(state.hasBuildEvidence(), state.hasInteractionEvidence());
    }

    static boolean hasGrace(ClaimCleanupState state) {
        return state != null && (state.getGraceMarkedAt() > 0L || state.getDeleteAfterAt() > 0L);
    }

    static void clearGrace(ClaimCleanupState state) {
        if (state == null) {
            return;
        }
        state.setGraceMarkedAt(0L);
        state.setDeleteAfterAt(0L);
    }

    static String normalizeReasonKey(String rawReason) {
        if (rawReason == null || rawReason.isBlank()) {
            return ClaimCleanupReason.NONE.key();
        }
        return ClaimCleanupReason.fromKey(rawReason).key();
    }
}
