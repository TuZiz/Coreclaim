package com.coreclaim.model;

import java.util.Collection;

public final class PermissionMergeSupport {

    private PermissionMergeSupport() {
    }

    public static boolean mergeInteractAndContainer(boolean allowInteract, boolean allowContainer) {
        return allowInteract && allowContainer;
    }

    public static boolean mergeLegacyFlagStates(boolean currentAllowed, Collection<ClaimFlagState> legacyStates) {
        if (!currentAllowed || legacyStates == null || legacyStates.isEmpty()) {
            return currentAllowed;
        }
        for (ClaimFlagState state : legacyStates) {
            if (state == ClaimFlagState.DENY) {
                return false;
            }
        }
        return true;
    }
}
