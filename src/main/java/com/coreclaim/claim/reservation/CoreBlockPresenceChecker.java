package com.coreclaim.claim.reservation;

public interface CoreBlockPresenceChecker {

    Presence check(PendingCoreReservation reservation);

    enum Presence {
        PRESENT,
        WORLD_UNLOADED,
        MISSING_OR_REPLACED
    }
}
