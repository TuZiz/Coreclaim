package com.coreclaim.protection.listener;

import static com.coreclaim.protection.listener.BlockProtectionListener.PreCancelledInteractionResolution.ALLOW_AXE_STRIPPING;
import static com.coreclaim.protection.listener.BlockProtectionListener.PreCancelledInteractionResolution.ALLOW_CAKE_CONSUMPTION;
import static com.coreclaim.protection.listener.BlockProtectionListener.PreCancelledInteractionResolution.DENY;
import static com.coreclaim.protection.listener.BlockProtectionListener.PreCancelledInteractionResolution.IGNORE;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class BlockProtectionListenerTest {

    @Test
    void preCancelledCakeAllowsPlayersWithInteractPermission() {
        assertEquals(
            ALLOW_CAKE_CONSUMPTION,
            BlockProtectionListener.resolvePreCancelledInteraction(true, true, false, false, true, false)
        );
    }

    @Test
    void preCancelledCakeDeniesPlayersWithoutInteractPermission() {
        assertEquals(
            DENY,
            BlockProtectionListener.resolvePreCancelledInteraction(true, true, false, false, false, true)
        );
    }

    @Test
    void preCancelledAxeStrippingAllowsPlayersWhoCanAccessClaim() {
        assertEquals(
            ALLOW_AXE_STRIPPING,
            BlockProtectionListener.resolvePreCancelledInteraction(true, false, true, false, false, true)
        );
    }

    @Test
    void preCancelledAxeStrippingDeniesPlayersWhoCannotAccessClaim() {
        assertEquals(
            DENY,
            BlockProtectionListener.resolvePreCancelledInteraction(true, false, true, false, false, false)
        );
    }

    @Test
    void preCancelledUnknownInteractionsStayUntouched() {
        assertEquals(
            IGNORE,
            BlockProtectionListener.resolvePreCancelledInteraction(true, false, false, false, false, false)
        );
    }
}
