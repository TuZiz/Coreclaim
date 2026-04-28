package com.coreclaim.command;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ClaimDenyTargetsTest {

    @Test
    void recognizesAllAccessGateAliases() {
        assertTrue(ClaimDenyTargets.isAllTarget("*"));
        assertTrue(ClaimDenyTargets.isAllTarget("全部"));
        assertTrue(ClaimDenyTargets.isAllTarget("all"));
        assertTrue(ClaimDenyTargets.isAllTarget(" ALL "));
    }

    @Test
    void keepsPlayerNamesAsPlayerTargets() {
        assertFalse(ClaimDenyTargets.isAllTarget("Player"));
        assertFalse(ClaimDenyTargets.isAllTarget("全"));
        assertFalse(ClaimDenyTargets.isAllTarget(""));
        assertFalse(ClaimDenyTargets.isAllTarget(null));
    }
}
