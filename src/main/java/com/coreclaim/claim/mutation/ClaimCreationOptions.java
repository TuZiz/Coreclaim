package com.coreclaim.claim.mutation;

public record ClaimCreationOptions(
    int maxClaims,
    int maxDistance,
    int minimumGap,
    int selectionMinimumGap,
    int minimumCoreSpacing,
    boolean enforceQuota,
    boolean enforceSizeLimit,
    boolean requireCoreAir,
    boolean placeCoreBlock,
    String coreBlockedFailureKey
) {

    public static ClaimCreationOptions coreClaim(int maxClaims, int maxDistance, int minimumGap, int minimumCoreSpacing) {
        return new ClaimCreationOptions(
            maxClaims,
            maxDistance,
            minimumGap,
            0,
            minimumCoreSpacing,
            true,
            true,
            true,
            true,
            "claim-core-blocked"
        );
    }

    public static ClaimCreationOptions selectionClaim(
        int maxClaims,
        int maxDistance,
        int minimumGap,
        int selectionMinimumGap,
        int minimumCoreSpacing,
        boolean systemManaged
    ) {
        return new ClaimCreationOptions(
            maxClaims,
            maxDistance,
            minimumGap,
            selectionMinimumGap,
            minimumCoreSpacing,
            !systemManaged,
            !systemManaged,
            true,
            true,
            "selection-core-blocked"
        );
    }

    int lockGap() {
        return Math.max(Math.max(0, minimumGap), Math.max(Math.max(0, selectionMinimumGap), Math.max(0, minimumCoreSpacing)));
    }
}
