package com.coreclaim.claim.reservation;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class PendingCoreReservationRegistryTest {

    private static final LocationKey KEY = new LocationKey("world", 1, 64, 1);

    @Test
    void releaseRemovesReservationAndMarksReleased() {
        PendingCoreReservationRegistry registry = new PendingCoreReservationRegistry();
        PendingCoreReservation reservation = reservation();

        registry.put(reservation);
        registry.release(reservation);

        assertFalse(registry.isReserved(KEY));
        assertTrue(reservation.released());
    }

    @Test
    void invalidReservationCannotCommit() {
        PendingCoreReservationRegistry registry = new PendingCoreReservationRegistry();
        PendingCoreReservation reservation = reservation();

        registry.put(reservation);
        registry.markInvalid(reservation, "bypass-break");

        assertFalse(registry.validateStillReserved(reservation));
        assertThrows(IllegalStateException.class, () -> registry.commit(reservation, 10));
    }

    @Test
    void commitRemovesReservationButDoesNotMarkReleased() {
        PendingCoreReservationRegistry registry = new PendingCoreReservationRegistry();
        PendingCoreReservation reservation = reservation();

        registry.put(reservation);
        registry.commit(reservation, 10);

        assertFalse(registry.isReserved(KEY));
        assertTrue(reservation.committed());
    }

    @Test
    void duplicateValidReservationIsRejected() {
        PendingCoreReservationRegistry registry = new PendingCoreReservationRegistry();

        registry.put(reservation());

        assertThrows(IllegalArgumentException.class, () -> registry.put(reservation()));
    }

    private PendingCoreReservation reservation() {
        return new PendingCoreReservation(
            UUID.randomUUID(),
            UUID.randomUUID(),
            KEY.world(),
            KEY.x(),
            KEY.y(),
            KEY.z(),
            System.currentTimeMillis(),
            ClaimCreationMode.CORE_CLAIM
        );
    }
}
