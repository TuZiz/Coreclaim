package com.coreclaim.claim.expansion;

import com.coreclaim.config.ClaimExpansionPricingSettings;
import com.coreclaim.config.ClaimGroup;
import com.coreclaim.model.Claim;
import com.coreclaim.model.ClaimCreationType;

public final class ExpansionCostCalculator {

    private final ClaimExpansionPricingSettings settings;

    public ExpansionCostCalculator(ClaimExpansionPricingSettings settings) {
        this.settings = settings == null ? ClaimExpansionPricingSettings.defaults() : settings;
    }

    public ExpansionCostResult calculate(
        Claim claim,
        ClaimGroup group,
        int worldMinY,
        int worldMaxY,
        long oldArea,
        int oldHeight,
        long newArea,
        int newHeight,
        boolean verticalExpansion
    ) {
        long rawAddedBlocks = rawAddedBlocks(oldArea, oldHeight, newArea, newHeight);
        long addedHorizontalArea = Math.max(0L, newArea - oldArea);
        if (useCoreEffectiveHeight(claim, verticalExpansion)) {
            return calculateCoreEffectiveHeight(group, worldMinY, worldMaxY, rawAddedBlocks, addedHorizontalArea);
        }
        double cost = rawAddedBlocks * expandPrice(group);
        return new ExpansionCostResult(
            cost,
            rawAddedBlocks,
            rawAddedBlocks,
            addedHorizontalArea,
            Math.max(0, newHeight),
            ExpansionPricingMode.REAL_VOLUME
        );
    }

    public static long rawAddedBlocks(long oldArea, int oldHeight, long newArea, int newHeight) {
        long oldVolume = Math.max(0L, oldArea) * Math.max(0, oldHeight);
        long newVolume = Math.max(0L, newArea) * Math.max(0, newHeight);
        return Math.max(0L, newVolume - oldVolume);
    }

    private ExpansionCostResult calculateCoreEffectiveHeight(
        ClaimGroup group,
        int worldMinY,
        int worldMaxY,
        long rawAddedBlocks,
        long addedHorizontalArea
    ) {
        int worldHeight = Math.max(0, worldMaxY - worldMinY + 1);
        int chargedHeight = Math.min(worldHeight, settings.effectiveHeightCap());
        double chargedBlocks = addedHorizontalArea
            * (double) chargedHeight
            * settings.heightPriceFactor()
            * settings.fullHeightDiscount();
        double cost = applyLimits(chargedBlocks * expandPrice(group));
        return new ExpansionCostResult(
            cost,
            chargedBlocks,
            rawAddedBlocks,
            addedHorizontalArea,
            chargedHeight,
            ExpansionPricingMode.CORE_EFFECTIVE_HEIGHT
        );
    }

    private boolean useCoreEffectiveHeight(Claim claim, boolean verticalExpansion) {
        if (claim == null || verticalExpansion || !claim.fullHeight() || !settings.coreFullHeightEnabled()) {
            return false;
        }
        ClaimCreationType creationType = claim.creationType();
        return creationType == ClaimCreationType.CORE
            || (creationType == ClaimCreationType.UNKNOWN_LEGACY && settings.legacyFullHeightClaimsAsCore());
    }

    private double applyLimits(double cost) {
        if (cost <= 0D) {
            return 0D;
        }
        double limited = cost;
        if (settings.minimumCost() > 0D) {
            limited = Math.max(limited, settings.minimumCost());
        }
        if (settings.maximumCostPerExpansion() > 0D) {
            limited = Math.min(limited, settings.maximumCostPerExpansion());
        }
        return limited;
    }

    private double expandPrice(ClaimGroup group) {
        return group == null ? 0D : group.expandPricePerBlock();
    }
}
