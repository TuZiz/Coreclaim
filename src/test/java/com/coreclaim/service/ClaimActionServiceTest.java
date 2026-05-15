package com.coreclaim.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class ClaimActionServiceTest {

    @Test
    void expansionCostUsesAddedProtectedVolume() {
        assertEquals(100L, ClaimActionService.expansionCostBlocks(100L, 10, 110L, 10));
        assertEquals(500L, ClaimActionService.expansionCostBlocks(100L, 10, 100L, 15));
        assertEquals(650L, ClaimActionService.expansionCostBlocks(100L, 10, 110L, 15));
    }

    @Test
    void expansionCostNeverGoesNegative() {
        assertEquals(0L, ClaimActionService.expansionCostBlocks(100L, 10, 90L, 10));
        assertEquals(0L, ClaimActionService.expansionCostBlocks(100L, 10, 100L, 9));
    }
}
