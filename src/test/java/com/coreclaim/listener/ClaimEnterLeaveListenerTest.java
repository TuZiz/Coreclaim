package com.coreclaim.listener;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.bukkit.Location;
import org.junit.jupiter.api.Test;

class ClaimEnterLeaveListenerTest {

    @Test
    void throttlesRepeatedBlockedEntryNoticesForSameClaim() {
        ClaimEnterLeaveListener.BlockedEntryNotice previous = new ClaimEnterLeaveListener.BlockedEntryNotice(12, 1000L);

        assertFalse(ClaimEnterLeaveListener.shouldShowBlockedEntryNotice(previous, 12, 2500L));
        assertTrue(ClaimEnterLeaveListener.shouldShowBlockedEntryNotice(previous, 12, 3000L));
    }

    @Test
    void allowsBlockedEntryNoticeForFirstOrDifferentClaim() {
        ClaimEnterLeaveListener.BlockedEntryNotice previous = new ClaimEnterLeaveListener.BlockedEntryNotice(12, 1000L);

        assertTrue(ClaimEnterLeaveListener.shouldShowBlockedEntryNotice(null, 12, 1000L));
        assertTrue(ClaimEnterLeaveListener.shouldShowBlockedEntryNotice(previous, 13, 1200L));
    }

    @Test
    void sameBlockTreatsYawOnlyMovementAsNoPositionChange() {
        assertTrue(ClaimEnterLeaveListener.sameBlock(
            new Location(null, 1.1D, 64D, 1.1D, 0F, 0F),
            new Location(null, 1.9D, 64D, 1.9D, 180F, 45F)
        ));
    }

    @Test
    void notifyResolutionCacheSkipsOnlySameHeightSameClaimMovement() {
        Location from = new Location(null, 1D, 64D, 1D);
        Location sameHeight = new Location(null, 2D, 64D, 1D);
        Location differentHeight = new Location(null, 2D, 65D, 1D);

        assertTrue(ClaimEnterLeaveListener.canSkipNotifyResolution(from, sameHeight, 7, 7, 7));
        assertFalse(ClaimEnterLeaveListener.canSkipNotifyResolution(from, sameHeight, 7, 8, 7));
        assertFalse(ClaimEnterLeaveListener.canSkipNotifyResolution(from, sameHeight, 7, 7, -1));
        assertFalse(ClaimEnterLeaveListener.canSkipNotifyResolution(from, differentHeight, 7, 7, 7));
    }
}
