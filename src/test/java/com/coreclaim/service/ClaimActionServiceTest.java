package com.coreclaim.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class ClaimActionServiceTest {

    @Test
    void expansionCostUsesAddedProtectedVolume() {
        assertEquals(100L, ClaimActionService.expansionCostBlocks(100L, 10, 110L, 10));
        assertEquals(500L, ClaimActionService.expansionCostBlocks(100L, 10, 100L, 15));
        assertEquals(650L, ClaimActionService.expansionCostBlocks(100L, 10, 110L, 15));
    }

    @Test
    void expansionCostNeverGoesNegative() {
        assertEquals(0L, ClaimActionService.expansionCostBlocks(100L, 10, 90L, 10));
        assertEquals(0L, ClaimActionService.expansionCostBlocks(100L, 10, 100L, 9));
    }

    @Test
    void buildExpansionPreviewUsesExpansionCostCalculator() throws IOException {
        String source = Files.readString(Path.of("src/main/java/com/coreclaim/service/ClaimActionService.java"));
        String method = method(source, "private ExpansionPreview buildExpansionPreview", "static long expansionCostBlocks");

        assertTrue(source.contains("new ExpansionCostCalculator(plugin.settings().claimExpansionPricing())"));
        assertTrue(method.contains("expansionCostCalculator.calculate("));
        assertTrue(method.contains("costResult.cost()"));
    }

    @Test
    void expansionPaymentStillUsesPreviewCostAndRefundPathKeepsPreviewCost() throws IOException {
        String source = Files.readString(Path.of("src/main/java/com/coreclaim/service/ClaimActionService.java"));
        String method = method(source, "public boolean expandClaim", "public boolean unclaimCurrent");

        assertTrue(method.contains("economyHook.has(player, preview.cost())"));
        assertTrue(method.contains("economyHook.withdraw(player, preview.cost())"));
        assertTrue(method.contains("economyHook.deposit(player, preview.cost())"));
    }

    private static String method(String source, String start, String end) {
        int startIndex = source.indexOf(start);
        int endIndex = source.indexOf(end, startIndex + start.length());
        if (startIndex < 0 || endIndex < 0) {
            throw new AssertionError("Unable to find source segment: " + start + " -> " + end);
        }
        return source.substring(startIndex, endIndex);
    }
}
