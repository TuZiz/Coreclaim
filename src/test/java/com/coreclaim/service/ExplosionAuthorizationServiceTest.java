package com.coreclaim.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ExplosionAuthorizationServiceTest {

    @Test
    void authorizedLocationMatchesNearbyWithinRadius() {
        ExplosionAuthorizationService service = new ExplosionAuthorizationService(5000L);

        service.authorize("world", 10, 64, 10);

        assertTrue(service.isAuthorizedNearby("world", 10, 64, 10, 1));
        assertTrue(service.isAuthorizedNearby("world", 11, 64, 10, 1));
        assertTrue(service.isAuthorizedNearby("world", 10, 65, 11, 1));
        assertFalse(service.isAuthorizedNearby("world", 12, 64, 10, 1));
        assertFalse(service.isAuthorizedNearby("world", 11, 64, 10, 0));
        assertFalse(service.isAuthorizedNearby("other", 10, 64, 10, 1));
    }

    @Test
    void expiredAuthorizationIsRemovedAndNoLongerMatches() throws InterruptedException {
        ExplosionAuthorizationService service = new ExplosionAuthorizationService(1L);

        service.authorize("world", 0, 64, 0);
        Thread.sleep(5L);

        assertFalse(service.isAuthorizedNearby("world", 0, 64, 0, 1));
    }
}
