package com.coreclaim.gui.holder;

import java.util.ArrayList;
import java.util.List;

public final class TrustOnlineAddHolder extends BaseHolder {
    public final int claimId;
    public final int page;
    public final int returnPage;
    public final List<TrustOnlineTargetSlotEntry> entries = new ArrayList<>();

    public TrustOnlineAddHolder(int claimId, int page, int returnPage) {
        this.claimId = claimId;
        this.page = page;
        this.returnPage = returnPage;
    }
}
