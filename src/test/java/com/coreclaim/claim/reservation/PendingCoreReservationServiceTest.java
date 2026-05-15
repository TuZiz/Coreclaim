package com.coreclaim.claim.reservation;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.coreclaim.claim.reservation.CoreBlockPresenceChecker.Presence;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PendingCoreReservationServiceTest {

    @Test
    void validReservationWithCorePresentCanCommit() {
        PendingCoreReservation reservation = reservation();
        PendingCoreReservationService service = service(Presence.PRESENT);
        service.register(reservation);

        assertTrue(service.validateStillReservedAndCorePresent(reservation));
    }

    @Test
    void validReservationWithAirMarksInvalid() {
        PendingCoreReservation reservation = reservation();
        PendingCoreReservationService service = service(Presence.MISSING_OR_REPLACED);
        service.register(reservation);

        assertFalse(service.validateStillReservedAndCorePresent(reservation));
        assertFalse(reservation.valid());
        assertTrue(reservation.invalidReason().contains("core-missing-or-replaced"));
    }

    @Test
    void validReservationWithDifferentBlockMarksInvalid() {
        PendingCoreReservation reservation = reservation();
        PendingCoreReservationService service = service(Presence.MISSING_OR_REPLACED);
        service.register(reservation);

        assertFalse(service.validateStillReservedAndCorePresent(reservation));
        assertFalse(reservation.valid());
    }

    @Test
    void unloadedWorldMarksInvalid() {
        PendingCoreReservation reservation = reservation();
        PendingCoreReservationService service = service(Presence.WORLD_UNLOADED);
        service.register(reservation);

        assertFalse(service.validateStillReservedAndCorePresent(reservation));
        assertTrue(reservation.invalidReason().contains("world-unloaded"));
    }

    @Test
    void invalidReservationDoesNotCallCoreChecker() {
        PendingCoreReservation reservation = reservation();
        PendingCoreReservationService service = new PendingCoreReservationService(null, null, ignored -> {
            throw new AssertionError("checker should not run");
        });
        service.register(reservation);
        service.markInvalid(reservation, "bypass-break");

        assertFalse(service.validateStillReservedAndCorePresent(reservation));
    }

    @Test
    void releasedReservationDoesNotCallCoreChecker() {
        PendingCoreReservation reservation = reservation();
        PendingCoreReservationService service = new PendingCoreReservationService(null, null, ignored -> {
            throw new AssertionError("checker should not run");
        });
        service.register(reservation);
        service.release(reservation);

        assertFalse(service.validateStillReservedAndCorePresent(reservation));
    }

    private PendingCoreReservationService service(Presence presence) {
        return new PendingCoreReservationService(null, null, ignored -> presence);
    }

    private PendingCoreReservation reservation() {
        return new PendingCoreReservation(
            UUID.randomUUID(),
            UUID.randomUUID(),
            "world",
            1,
            64,
            1,
            System.currentTimeMillis(),
            ClaimCreationMode.CORE_CLAIM
        );
    }
}
