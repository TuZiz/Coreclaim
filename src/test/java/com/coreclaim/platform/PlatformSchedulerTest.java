package com.coreclaim.platform;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class PlatformSchedulerTest {

    @Test
    void normalizesFoliaTicksToPositiveValues() {
        assertEquals(1L, PlatformScheduler.positiveFoliaTicks(0L));
        assertEquals(1L, PlatformScheduler.positiveFoliaTicks(-20L));
        assertEquals(5L, PlatformScheduler.positiveFoliaTicks(5L));
    }
}
