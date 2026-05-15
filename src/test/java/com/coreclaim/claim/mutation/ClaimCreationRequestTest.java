package com.coreclaim.claim.mutation;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class ClaimCreationRequestTest {

    @Test
    void boundsExposeSpatialAreaWithoutBukkitAccess() {
        ClaimCreationRequest request = new ClaimCreationRequest(
            UUID.randomUUID(),
            "Player",
            "Home",
            "world",
            10,
            64,
            -20,
            -64,
            319,
            40,
            80,
            3,
            4,
            5,
            6,
            false,
            false,
            ClaimCreationOptions.selectionClaim(3, 12, 7, 9, 11, false)
        );

        assertEquals(5, request.minX());
        assertEquals(13, request.maxX());
        assertEquals(-26, request.minZ());
        assertEquals(-16, request.maxZ());
    }
}
