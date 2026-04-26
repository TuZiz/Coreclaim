package com.coreclaim.gui.holder;

import com.coreclaim.model.ClaimDirection;

public final class ClaimExpandConfirmHolder extends BaseHolder {
    public final int claimId;
    public final ClaimDirection direction;
    public final int amount;

    public ClaimExpandConfirmHolder(int claimId, ClaimDirection direction, int amount) {
        this.claimId = claimId;
        this.direction = direction;
        this.amount = amount;
    }
}
