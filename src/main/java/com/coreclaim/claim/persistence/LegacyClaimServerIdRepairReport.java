package com.coreclaim.claim.persistence;

import java.util.List;
import java.util.Set;

public record LegacyClaimServerIdRepairReport(
    boolean mysql,
    boolean repairEnabled,
    int missingCount,
    int repairedCount,
    int unrepairedCount,
    Set<String> worlds,
    List<LegacyClaimServerIdRepairRow> unrepairedRows
) {

    public static LegacyClaimServerIdRepairReport none(boolean mysql, boolean repairEnabled) {
        return new LegacyClaimServerIdRepairReport(mysql, repairEnabled, 0, 0, 0, Set.of(), List.of());
    }

    public boolean hasMissingRows() {
        return missingCount > 0;
    }

    public record LegacyClaimServerIdRepairRow(int claimId, String name, String world) {
    }
}
