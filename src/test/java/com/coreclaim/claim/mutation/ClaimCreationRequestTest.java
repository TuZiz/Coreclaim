package com.coreclaim.claim.mutation;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.UUID;
import com.coreclaim.model.ClaimCreationType;
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

    @Test
    void factoriesMarkCreationSource() {
        assertEquals(
            ClaimCreationType.UNKNOWN_LEGACY,
            new ClaimCreationRequest(
                UUID.randomUUID(),
                "Player",
                "Home",
                "world",
                0,
                64,
                0,
                -64,
                319,
                -64,
                319,
                1,
                1,
                1,
                1,
                true,
                false,
                null
            ).creationType()
        );
    }
}
