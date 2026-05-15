package com.coreclaim.listener;

public final class PendingCoreProtectionPolicy {

    private PendingCoreProtectionPolicy() {
    }

    public static Decision blockMutation(boolean reserved, boolean bypassing) {
        if (!reserved) {
            return Decision.ALLOW;
        }
        return bypassing ? Decision.INVALIDATE_AND_ALLOW : Decision.CANCEL;
    }

    public static Decision environmentalMutation(boolean reserved) {
        return reserved ? Decision.CANCEL : Decision.ALLOW;
    }

    public enum Decision {
        ALLOW,
        CANCEL,
        INVALIDATE_AND_ALLOW
    }
}
