package com.coreclaim.model;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class ClaimFlagMergeTest {

    @Test
    void legacyFlagKeysAreNotPublicFlagInputs() {
        assertNull(ClaimFlag.fromKey("container"));
        assertNull(ClaimFlag.fromKey("use-door"));
        assertNull(ClaimFlag.fromKey("use-button"));
        assertTrue(ClaimFlag.isLegacyInteractKey("use-trapdoor"));
        assertTrue(ClaimFlag.isLegacyRedstoneKey("use-pressure-plate"));
    }

    @Test
    void legacyMergeKeepsDenyPriority() {
        assertFalse(PermissionMergeSupport.mergeInteractAndContainer(true, false));
        assertFalse(PermissionMergeSupport.mergeLegacyFlagStates(true, List.of(ClaimFlagState.ALLOW, ClaimFlagState.DENY)));
        assertTrue(PermissionMergeSupport.mergeLegacyFlagStates(true, List.of(ClaimFlagState.ALLOW)));
        assertFalse(PermissionMergeSupport.mergeLegacyFlagStates(false, List.of(ClaimFlagState.ALLOW)));
    }
}
