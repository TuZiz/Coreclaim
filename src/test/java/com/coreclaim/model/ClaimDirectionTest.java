package com.coreclaim.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class ClaimDirectionTest {

    @Test
    void parsesVerticalDirectionAliases() {
        assertEquals(ClaimDirection.UP, ClaimDirection.fromInput("up"));
        assertEquals(ClaimDirection.UP, ClaimDirection.fromInput("u"));
        assertEquals(ClaimDirection.UP, ClaimDirection.fromInput("上"));
        assertEquals(ClaimDirection.DOWN, ClaimDirection.fromInput("down"));
        assertEquals(ClaimDirection.DOWN, ClaimDirection.fromInput("d"));
        assertEquals(ClaimDirection.DOWN, ClaimDirection.fromInput("下"));
        assertTrue(ClaimDirection.UP.vertical());
        assertTrue(ClaimDirection.DOWN.vertical());
    }

    @Test
    void verticalDistanceUsesCoreHeightOffset() {
        Claim claim = new Claim(
            1,
            UUID.randomUUID(),
            "owner",
            "claim",
            "local",
            "world",
            0,
            64,
            0,
            60,
            70,
            false,
            2,
            3,
            4,
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

        assertEquals(6, claim.distance(ClaimDirection.UP));
        assertEquals(4, claim.distance(ClaimDirection.DOWN));

        claim.setHeightBounds(55, 80, false);

        assertEquals(16, claim.distance(ClaimDirection.UP));
        assertEquals(9, claim.distance(ClaimDirection.DOWN));
    }
}
