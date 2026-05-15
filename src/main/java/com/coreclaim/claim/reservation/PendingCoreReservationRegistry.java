package com.coreclaim.claim.reservation;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public final class PendingCoreReservationRegistry {

    private final ConcurrentMap<LocationKey, PendingCoreReservation> reservations = new ConcurrentHashMap<>();

    public void put(PendingCoreReservation reservation) {
        PendingCoreReservation previous = reservations.putIfAbsent(reservation.locationKey(), reservation);
        if (previous == null) {
            return;
        }
        if (!previous.valid()) {
            reservations.remove(previous.locationKey(), previous);
            previous.release();
            put(reservation);
            return;
        }
        throw new IllegalArgumentException("claim-core-blocked");
    }

    public Optional<PendingCoreReservation> at(LocationKey key) {
        PendingCoreReservation reservation = key == null ? null : reservations.get(key);
        return Optional.ofNullable(reservation);
    }

    public boolean isReserved(LocationKey key) {
        return at(key).map(PendingCoreReservation::valid).orElse(false);
    }

    public boolean isReservedBy(LocationKey key, java.util.UUID reservationId) {
        return at(key)
            .filter(PendingCoreReservation::valid)
            .map(reservation -> reservation.reservationId().equals(reservationId))
            .orElse(false);
    }

    public boolean validateStillReserved(PendingCoreReservation reservation) {
        if (reservation == null || !reservation.valid()) {
            return false;
        }
        return isReservedBy(reservation.locationKey(), reservation.reservationId());
    }

    public void markInvalid(PendingCoreReservation reservation, String reason) {
        if (reservation == null) {
            return;
        }
        reservation.invalidate(reason);
    }

    public void commit(PendingCoreReservation reservation, int claimId) {
        if (!validateStillReserved(reservation) || !reservation.commit(claimId)) {
            throw new IllegalStateException("pending-core-invalid");
        }
        reservations.remove(reservation.locationKey(), reservation);
    }

    public void release(PendingCoreReservation reservation) {
        if (reservation == null) {
            return;
        }
        reservations.remove(reservation.locationKey(), reservation);
        reservation.release();
    }

    public List<PendingCoreReservation> snapshot() {
        return new ArrayList<>(reservations.values());
    }
}
