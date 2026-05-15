package com.coreclaim.listener;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.coreclaim.listener.PendingCoreProtectionPolicy.Decision;
import org.junit.jupiter.api.Test;

class PendingCoreProtectionPolicyTest {

    @Test
    void normalBlockBreakOnPendingCoreIsCancelled() {
        assertEquals(Decision.CANCEL, PendingCoreProtectionPolicy.blockMutation(true, false));
    }

    @Test
    void bypassBlockBreakInvalidatesAndAllowsEvent() {
        assertEquals(Decision.INVALIDATE_AND_ALLOW, PendingCoreProtectionPolicy.blockMutation(true, true));
    }

    @Test
    void explosionAndPistonProtectReservedBlocks() {
        assertEquals(Decision.CANCEL, PendingCoreProtectionPolicy.environmentalMutation(true));
        assertEquals(Decision.ALLOW, PendingCoreProtectionPolicy.environmentalMutation(false));
    }
}
