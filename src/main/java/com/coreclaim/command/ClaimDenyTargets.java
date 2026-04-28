package com.coreclaim.command;

import java.util.List;
import java.util.Locale;

final class ClaimDenyTargets {

    private static final List<String> ALL_TARGETS = List.of("*", "全部", "all");

    private ClaimDenyTargets() {
    }

    static boolean isAllTarget(String input) {
        if (input == null) {
            return false;
        }
        String normalized = input.trim().toLowerCase(Locale.ROOT);
        return ALL_TARGETS.stream().anyMatch(target -> target.equalsIgnoreCase(normalized));
    }

    static List<String> allTargetOptions() {
        return ALL_TARGETS;
    }
}
