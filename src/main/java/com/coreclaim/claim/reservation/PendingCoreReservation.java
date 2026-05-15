package com.coreclaim.claim.reservation;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

public final class PendingCoreReservation {

    private final UUID reservationId;
    private final UUID ownerId;
    private final String world;
    private final int x;
    private final int y;
    private final int z;
    private final long createdAtMillis;
    private final ClaimCreationMode mode;
    private final AtomicReference<State> state = new AtomicReference<>(State.VALID);
    private volatile String invalidReason = "";
    private volatile Integer committedClaimId;

    public PendingCoreReservation(
        UUID reservationId,
        UUID ownerId,
        String world,
        int x,
        int y,
        int z,
        long createdAtMillis,
        ClaimCreationMode mode
    ) {
        this.reservationId = reservationId;
        this.ownerId = ownerId;
        this.world = world;
        this.x = x;
        this.y = y;
        this.z = z;
        this.createdAtMillis = createdAtMillis;
        this.mode = mode;
    }

    public UUID reservationId() {
        return reservationId;
    }

    public UUID ownerId() {
        return ownerId;
    }

    public String world() {
        return world;
    }

    public int x() {
        return x;
    }

    public int y() {
        return y;
    }

    public int z() {
        return z;
    }

    public long createdAtMillis() {
        return createdAtMillis;
    }

    public ClaimCreationMode mode() {
        return mode;
    }

    public boolean valid() {
        return state.get() == State.VALID;
    }

    public boolean committed() {
        return state.get() == State.COMMITTED;
    }

    public boolean released() {
        return state.get() == State.RELEASED;
    }

    public State state() {
        return state.get();
    }

    public String invalidReason() {
        return invalidReason;
    }

    public Integer committedClaimId() {
        return committedClaimId;
    }

    public LocationKey locationKey() {
        return new LocationKey(world, x, y, z);
    }

    boolean invalidate(String reason) {
        invalidReason = reason == null || reason.isBlank() ? "unknown" : reason;
        return state.compareAndSet(State.VALID, State.INVALID);
    }

    boolean commit(int claimId) {
        committedClaimId = claimId;
        return state.compareAndSet(State.VALID, State.COMMITTED);
    }

    void release() {
        state.set(State.RELEASED);
    }

    public enum State {
        VALID,
        INVALID,
        COMMITTED,
        RELEASED
    }
}
