package com.coreclaim.claim.expansion;

public record ExpansionCostResult(
    double cost,
    double chargedBlocks,
    long rawAddedBlocks,
    long addedHorizontalArea,
    int chargedHeight,
    ExpansionPricingMode mode
) {
}
