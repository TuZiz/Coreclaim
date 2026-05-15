package com.coreclaim.platform;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

class PlatformSchedulerTest {

    @Test
    void normalizesFoliaTicksToPositiveValues() {
        assertEquals(1L, PlatformScheduler.positiveFoliaTicks(0L));
        assertEquals(1L, PlatformScheduler.positiveFoliaTicks(-20L));
        assertEquals(5L, PlatformScheduler.positiveFoliaTicks(5L));
    }

    @Test
    void exposesPlayerTeleportWrapperForFoliaSafeCallSites() {
        assertDoesNotThrow(() -> PlatformScheduler.class.getMethod("teleportPlayer", Player.class, Location.class));
    }
}
