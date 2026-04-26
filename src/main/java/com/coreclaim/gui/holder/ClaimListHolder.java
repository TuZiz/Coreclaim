package com.coreclaim.gui.holder;

import java.util.ArrayList;
import java.util.List;

public final class ClaimListHolder extends BaseHolder {
    public final int page;
    public final List<ClaimListSlotEntry> entries = new ArrayList<>();

    public ClaimListHolder(int page) {
        this.page = page;
    }
}
