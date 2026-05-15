package com.coreclaim.claim.mutation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ClaimCreationOptionsTest {

    @Test
    void coreClaimFinalValidationKeepsQuotaAndCorePlacementEnabled() {
        ClaimCreationOptions options = ClaimCreationOptions.coreClaim(3, 12, 4, 8);

        assertTrue(options.enforceQuota());
        assertTrue(options.enforceSizeLimit());
        assertTrue(options.requireCoreAir());
        assertTrue(options.placeCoreBlock());
        assertEquals(8, options.lockGap());
    }

    @Test
    void systemSelectionStillLocksGapButBypassesQuotaAndSize() {
        ClaimCreationOptions options = ClaimCreationOptions.selectionClaim(3, 12, 4, 6, 2, true);

        assertFalse(options.enforceQuota());
        assertFalse(options.enforceSizeLimit());
        assertEquals(6, options.lockGap());
    }
}
