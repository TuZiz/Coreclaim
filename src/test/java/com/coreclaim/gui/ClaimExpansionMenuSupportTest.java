package com.coreclaim.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.coreclaim.model.Claim;
import com.coreclaim.model.ClaimDirection;
import com.coreclaim.service.ClaimActionService;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ClaimExpansionMenuSupportTest {

    @Test
    void verticalBoundariesUseActualHeightBoundsInsteadOfCoreOffset() {
        Claim claim = claim(20, 30, 70);

        ClaimActionService.ExpansionPreview upPreview = preview(20, 31, -39);
        ClaimActionService.ExpansionPreview downPreview = preview(19, 30, 51);

        assertEquals(30, ClaimExpansionMenuSupport.currentBoundary(claim, ClaimDirection.UP));
        assertEquals(31, ClaimExpansionMenuSupport.targetBoundary(upPreview, ClaimDirection.UP));
        assertEquals(20, ClaimExpansionMenuSupport.currentBoundary(claim, ClaimDirection.DOWN));
        assertEquals(19, ClaimExpansionMenuSupport.targetBoundary(downPreview, ClaimDirection.DOWN));
    }

    @Test
    void horizontalBoundariesKeepUsingDirectionalDistance() {
        Claim claim = claim(60, 80, 70);
        ClaimActionService.ExpansionPreview preview = preview(60, 80, 8);

        assertEquals(5, ClaimExpansionMenuSupport.currentBoundary(claim, ClaimDirection.NORTH));
        assertEquals(8, ClaimExpansionMenuSupport.targetBoundary(preview, ClaimDirection.NORTH));
    }

    private static ClaimActionService.ExpansionPreview preview(int minY, int maxY, int targetDistance) {
        return new ClaimActionService.ExpansionPreview(
            true,
            0D,
            targetDistance,
            1,
            11,
            maxY - minY + 1,
            11,
            5,
            5,
            5,
            5,
            minY,
            maxY,
            false,
            false,
            false
        );
    }

    private static Claim claim(int minY, int maxY, int centerY) {
        return new Claim(
            1,
            UUID.randomUUID(),
            "owner",
            "claim",
            "server",
            "world",
            0,
            centerY,
            0,
            minY,
            maxY,
            false,
            5,
            5,
            5,
            5,
            0L,
            true,
            "",
            "",
            false,
            false,
            false,
            false,
            false,
            false,
            false,
            false,
            false,
            false,
            false,
            false,
            false,
            false,
            null,
            null,
            null,
            null,
            null,
            0L
        );
    }
}
