package com.coreclaim.config;

public record ClaimExpansionPricingSettings(
    boolean legacyFullHeightClaimsAsCore,
    boolean coreFullHeightEnabled,
    int effectiveHeightCap,
    double heightPriceFactor,
    double fullHeightDiscount,
    double minimumCost,
    double maximumCostPerExpansion,
    boolean selectionUseRealVolume
) {
    public static final int DEFAULT_EFFECTIVE_HEIGHT_CAP = 96;

    public ClaimExpansionPricingSettings {
        effectiveHeightCap = effectiveHeightCap <= 0 ? DEFAULT_EFFECTIVE_HEIGHT_CAP : effectiveHeightCap;
        heightPriceFactor = Math.max(0D, heightPriceFactor);
        fullHeightDiscount = Math.max(0D, fullHeightDiscount);
        minimumCost = Math.max(0D, minimumCost);
        maximumCostPerExpansion = maximumCostPerExpansion <= 0D ? -1D : maximumCostPerExpansion;
    }

    public static ClaimExpansionPricingSettings defaults() {
        return new ClaimExpansionPricingSettings(
            true,
            true,
            DEFAULT_EFFECTIVE_HEIGHT_CAP,
            0.35D,
            0.55D,
            0D,
            -1D,
            true
        );
    }
}
