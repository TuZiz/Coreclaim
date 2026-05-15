package com.coreclaim.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.junit.jupiter.api.Test;

class ClaimSpatialLockServiceTest {

    @Test
    void lockKeysUseFloorChunksForNegativeCoordinates() {
        List<String> keys = ClaimSpatialLockService.lockKeys("world", -17, 16, -1, 16);

        assertEquals(List.of(
            "world:-2:-1",
            "world:-2:0",
            "world:-2:1",
            "world:-1:-1",
            "world:-1:0",
            "world:-1:1",
            "world:0:-1",
            "world:0:0",
            "world:0:1",
            "world:1:-1",
            "world:1:0",
            "world:1:1"
        ), keys);
    }

    @Test
    void lockKeysCoverGapExpandedArea() {
        List<String> keys = ClaimSpatialLockService.lockKeys("world", 0 - 16, 15 + 16, 0 - 16, 15 + 16);

        assertEquals(List.of(
            "world:-1:-1",
            "world:-1:0",
            "world:-1:1",
            "world:0:-1",
            "world:0:0",
            "world:0:1",
            "world:1:-1",
            "world:1:0",
            "world:1:1"
        ), keys);
    }
}
