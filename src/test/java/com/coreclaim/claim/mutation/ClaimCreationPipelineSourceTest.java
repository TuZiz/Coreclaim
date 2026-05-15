package com.coreclaim.claim.mutation;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class ClaimCreationPipelineSourceTest {

    @Test
    void selectionRegionMethodDoesNotRunDatabaseCreation() throws IOException {
        String source = read("src/main/java/com/coreclaim/selection/ClaimSelectionCreator.java");
        String regionMethod = method(source, "private void createClaimOnRegion", "private void createSystemClaimOnRegion");

        assertFalse(regionMethod.contains("claimService.createClaim("));
        assertFalse(regionMethod.contains("databaseManager"));
        assertFalse(regionMethod.contains(".transaction("));
        assertTrue(source.contains("databaseAsyncExecutor.supply(() -> claimService.createClaim(request))"));
    }

    @Test
    void pendingRegionMethodDoesNotRunDatabaseCreation() throws IOException {
        String source = read("src/main/java/com/coreclaim/service/PendingClaimService.java");
        String regionMethod = method(source, "private void completeClaimOnRegion", "private void completeClaimAsync");

        assertFalse(regionMethod.contains("claimService.createClaim("));
        assertFalse(regionMethod.contains("databaseManager"));
        assertFalse(regionMethod.contains(".transaction("));
        assertTrue(source.contains("databaseAsyncExecutor.supply(() -> {"));
        assertTrue(source.contains("ClaimCreationResult result = claimService.createClaim(request);"));
    }

    @Test
    void asyncDatabaseFailureCleansCoreAndRefunds() throws IOException {
        String selection = read("src/main/java/com/coreclaim/selection/ClaimSelectionCreator.java");
        String pending = read("src/main/java/com/coreclaim/service/PendingClaimService.java");

        assertTrue(selection.contains("claimCoreRegionService.clearTemporaryCore(preview.coreLocation())"));
        assertTrue(selection.contains("refundCost(player, preview.cost())"));
        assertTrue(pending.contains("claimCoreRegionService.clearTemporaryCore(coreLocation)"));
        assertTrue(pending.contains("refundCreationPayment(player, createCost)"));
        assertTrue(pending.contains("refundCore(pending)"));
    }

    @Test
    void committedClaimRegistrationDoesNotFailCreationForSyncOrTrackingErrors() throws IOException {
        String source = read("src/main/java/com/coreclaim/claim/mutation/ClaimCreationMutations.java");
        String registerMethod = method(source, "private void registerCommittedClaim", "\n}");

        assertTrue(registerMethod.contains("context.publishClaimSync(ClaimSyncEventType.CLAIM_CREATED, claim.id());"));
        assertTrue(registerMethod.contains("Claim committed but sync publish failed"));
        assertTrue(registerMethod.contains("context.trackNewClaim(claim);"));
        assertTrue(registerMethod.contains("Claim committed but activity tracking failed"));
        assertFalse(registerMethod.contains("throw exception"));
    }

    private static String read(String path) throws IOException {
        return Files.readString(Path.of(path));
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
