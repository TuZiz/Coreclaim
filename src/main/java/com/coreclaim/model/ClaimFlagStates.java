package com.coreclaim.model;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

final class ClaimFlagStates {

    private final EnumMap<ClaimFlag, ClaimFlagState> flagStates = new EnumMap<>(ClaimFlag.class);

    ClaimFlagState flagState(ClaimFlag flag) {
        return flagStates.getOrDefault(flag, ClaimFlagState.UNSET);
    }

    void setFlagState(ClaimFlag flag, ClaimFlagState state) {
        if (flag == null) {
            return;
        }
        if (state == null || state == ClaimFlagState.UNSET) {
            flagStates.remove(flag);
            return;
        }
        flagStates.put(flag, state);
    }

    void clearFlagState(ClaimFlag flag) {
        if (flag == null) {
            return;
        }
        flagStates.remove(flag);
    }

    Map<ClaimFlag, ClaimFlagState> flagStates() {
        return Collections.unmodifiableMap(new EnumMap<>(flagStates));
    }
}
