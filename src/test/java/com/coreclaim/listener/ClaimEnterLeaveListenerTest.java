package com.coreclaim.listener;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
}
