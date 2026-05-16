package com.coreclaim.claim.expansion;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.coreclaim.config.ClaimExpansionPricingSettings;
import com.coreclaim.config.ClaimGroup;
import com.coreclaim.model.Claim;
import com.coreclaim.model.ClaimCreationType;
import org.junit.jupiter.api.Test;

class ExpansionCostCalculatorTest {

    private static final ClaimGroup GROUP = new ClaimGroup("default", "Default", 0, "", 5, 50, 0D, 0D, 2D, 3);

    @Test
    void coreFullHeightHorizontalExpansionUsesEffectiveHeightAndDiscount() {
        ExpansionCostResult result = calculator(settings()).calculate(
            claim(ClaimCreationType.CORE, true),
            GROUP,
            -64,
            319,
            100,
            384,
            120,
            384,
            false
        );

        assertEquals(ExpansionPricingMode.CORE_EFFECTIVE_HEIGHT, result.mode());
        assertEquals(20L, result.addedHorizontalArea());
        assertEquals(96, result.chargedHeight());
        assertEquals(20D * 96D * 0.35D * 0.55D, result.chargedBlocks(), 0.000001D);
        assertEquals(result.chargedBlocks() * 2D, result.cost(), 0.000001D);
    }

    @Test
    void coreEffectiveHeightUsesWorldHeightWhenLowerThanCap() {
        ExpansionCostResult result = calculator(settings()).calculate(
            claim(ClaimCreationType.CORE, true),
            GROUP,
            0,
            63,
            100,
            64,
            110,
            64,
            false
        );

        assertEquals(64, result.chargedHeight());
        assertEquals(10D * 64D * 0.35D * 0.55D * 2D, result.cost(), 0.000001D);
    }

    @Test
    void minimumCostAppliesOnlyWhenCostIsPositive() {
        ClaimExpansionPricingSettings minimum = new ClaimExpansionPricingSettings(true, true, 96, 0.35D, 0.55D, 100D, -1D, true);

        ExpansionCostResult positive = calculator(minimum).calculate(claim(ClaimCreationType.CORE, true), GROUP, -64, 319, 100, 384, 101, 384, false);
        ExpansionCostResult zero = calculator(minimum).calculate(claim(ClaimCreationType.CORE, true), GROUP, -64, 319, 100, 384, 100, 384, false);

        assertEquals(100D, positive.cost(), 0.000001D);
        assertEquals(0D, zero.cost(), 0.000001D);
    }

    @Test
    void maximumCostCapsCoreExpansion() {
        ClaimExpansionPricingSettings maximum = new ClaimExpansionPricingSettings(true, true, 96, 0.35D, 0.55D, 0D, 50D, true);

        ExpansionCostResult result = calculator(maximum).calculate(claim(ClaimCreationType.CORE, true), GROUP, -64, 319, 100, 384, 200, 384, false);

        assertEquals(50D, result.cost(), 0.000001D);
    }

    @Test
    void invalidCapFallsBackAndNegativeFactorsClampToZero() {
        ClaimExpansionPricingSettings invalid = new ClaimExpansionPricingSettings(true, true, 0, -0.5D, -1D, 0D, 0D, true);

        ExpansionCostResult result = calculator(invalid).calculate(claim(ClaimCreationType.CORE, true), GROUP, -64, 319, 100, 384, 110, 384, false);

        assertEquals(ClaimExpansionPricingSettings.DEFAULT_EFFECTIVE_HEIGHT_CAP, result.chargedHeight());
        assertEquals(0D, result.chargedBlocks(), 0.000001D);
        assertEquals(0D, result.cost(), 0.000001D);
    }

    @Test
    void selectionHorizontalExpansionUsesRealVolume() {
        ExpansionCostResult result = calculator(settings()).calculate(
            claim(ClaimCreationType.SELECTION, false),
            GROUP,
            -64,
            319,
            100,
            10,
            120,
            10,
            false
        );

        assertEquals(ExpansionPricingMode.REAL_VOLUME, result.mode());
        assertEquals(200L, result.rawAddedBlocks());
        assertEquals(200D, result.chargedBlocks(), 0.000001D);
        assertEquals(400D, result.cost(), 0.000001D);
    }

    @Test
    void selectionVerticalExpansionUsesRealVolume() {
        ExpansionCostResult result = calculator(settings()).calculate(
            claim(ClaimCreationType.SELECTION, false),
            GROUP,
            -64,
            319,
            100,
            10,
            100,
            15,
            true
        );

        assertEquals(ExpansionPricingMode.REAL_VOLUME, result.mode());
        assertEquals(500L, result.rawAddedBlocks());
        assertEquals(1000D, result.cost(), 0.000001D);
    }

    @Test
    void selectionFullHeightDoesNotUseCoreDiscount() {
        ExpansionCostResult result = calculator(settings()).calculate(
            claim(ClaimCreationType.SELECTION, true),
            GROUP,
            -64,
            319,
            100,
            384,
            110,
            384,
            false
        );

        assertEquals(ExpansionPricingMode.REAL_VOLUME, result.mode());
        assertEquals(3840L, result.rawAddedBlocks());
        assertEquals(7680D, result.cost(), 0.000001D);
    }

    @Test
    void nonPositiveRealVolumeDifferenceCostsZero() {
        ExpansionCostResult result = calculator(settings()).calculate(
            claim(ClaimCreationType.SELECTION, false),
            GROUP,
            -64,
            319,
            100,
            10,
            90,
            10,
            false
        );

        assertEquals(0L, result.rawAddedBlocks());
        assertEquals(0D, result.cost(), 0.000001D);
    }

    @Test
    void legacyFullHeightCanBeTreatedAsCore() {
        ExpansionCostResult result = calculator(settings()).calculate(
            claim(ClaimCreationType.UNKNOWN_LEGACY, true),
            GROUP,
            -64,
            319,
            100,
            384,
            110,
            384,
            false
        );

        assertEquals(ExpansionPricingMode.CORE_EFFECTIVE_HEIGHT, result.mode());
    }

    @Test
    void legacyFullHeightCanRemainRealVolumeWhenConfigured() {
        ClaimExpansionPricingSettings realLegacy = new ClaimExpansionPricingSettings(false, true, 96, 0.35D, 0.55D, 0D, -1D, true);

        ExpansionCostResult result = calculator(realLegacy).calculate(
            claim(ClaimCreationType.UNKNOWN_LEGACY, true),
            GROUP,
            -64,
            319,
            100,
            384,
            110,
            384,
            false
        );

        assertEquals(ExpansionPricingMode.REAL_VOLUME, result.mode());
    }

    @Test
    void coreVerticalExpansionUsesRealVolume() {
        ExpansionCostResult result = calculator(settings()).calculate(
            claim(ClaimCreationType.CORE, false),
            GROUP,
            -64,
            319,
            100,
            100,
            100,
            110,
            true
        );

        assertEquals(ExpansionPricingMode.REAL_VOLUME, result.mode());
        assertEquals(2000D, result.cost(), 0.000001D);
    }

    private static ExpansionCostCalculator calculator(ClaimExpansionPricingSettings settings) {
        return new ExpansionCostCalculator(settings);
    }

    private static ClaimExpansionPricingSettings settings() {
        return ClaimExpansionPricingSettings.defaults();
    }

    private static Claim claim(ClaimCreationType creationType, boolean fullHeight) {
        return new Claim(
            1,
            java.util.UUID.randomUUID(),
            "Player",
            "Home",
            "local",
            "world",
            0,
            64,
            0,
            fullHeight ? -64 : 60,
            fullHeight ? 319 : 69,
            fullHeight,
            4,
            4,
            4,
            4,
            1L,
            true,
            "",
            "",
            false,
            false,
            false,
            false,
            false,
            true,
            true,
            false,
            false,
            false,
            false,
            false,
            false,
            creationType,
            false,
            null,
            null,
            null,
            null,
            null,
            0L
        );
    }
}
