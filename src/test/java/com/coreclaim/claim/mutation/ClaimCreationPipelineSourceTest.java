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
        assertTrue(regionMethod.contains("pendingCoreReservationService.reserve"));
        assertTrue(regionMethod.contains("pendingCoreReservationService.releaseAndClear(reservation)"));
        assertTrue(source.contains("databaseAsyncExecutor.supply(() -> claimService.createClaim(request))"));
        assertTrue(source.contains("pendingCoreReservationService.commit(reservation, result.claim())"));
    }

    @Test
    void pendingRegionMethodDoesNotRunDatabaseCreation() throws IOException {
        String source = read("src/main/java/com/coreclaim/service/PendingClaimService.java");
        String regionMethod = method(source, "private void completeClaimOnRegion", "private void completeClaimAsync");

        assertFalse(regionMethod.contains("claimService.createClaim("));
        assertFalse(regionMethod.contains("databaseManager"));
        assertFalse(regionMethod.contains(".transaction("));
        assertTrue(regionMethod.contains("pendingCoreReservationService.reserve"));
        assertTrue(regionMethod.contains("pendingCoreReservationService.releaseAndClear(reservation)"));
        assertTrue(source.contains("databaseAsyncExecutor.supply(() -> claimService.createClaim(request))"));
        assertTrue(source.contains("pendingCoreReservationService.commit(reservation, result.claim())"));
    }

    @Test
    void asyncDatabaseFailureReleasesReservationAndRefunds() throws IOException {
        String selection = read("src/main/java/com/coreclaim/selection/ClaimSelectionCreator.java");
        String pending = read("src/main/java/com/coreclaim/service/PendingClaimService.java");

        assertTrue(selection.contains("pendingCoreReservationService.releaseAndClear(reservation)"));
        assertTrue(selection.contains("refundCost(player, preview.cost())"));
        assertTrue(pending.contains("pendingCoreReservationService.releaseAndClear(reservation)"));
        assertTrue(pending.contains("refundCreationPayment(player, createCost)"));
        assertTrue(pending.contains("refundCore(pending)"));
    }

    @Test
    void committedClaimWithInvalidReservationIsCompensated() throws IOException {
        String selection = read("src/main/java/com/coreclaim/selection/ClaimSelectionCreator.java");
        String pending = read("src/main/java/com/coreclaim/service/PendingClaimService.java");

        assertTrue(selection.contains("compensateCommittedClaimCreationFailure(result.claim(), reservation"));
        assertTrue(selection.contains("claimService.removeCommittedClaimRecord(claim)"));
        assertTrue(selection.contains("pendingCoreReservationService.releaseAndClear(reservation)"));
        assertTrue(pending.contains("compensateCommittedClaimCreationFailure(result.claim(), reservation"));
        assertTrue(pending.contains("claimService.removeCommittedClaimRecord(claim)"));
        assertTrue(pending.contains("refundCore(pending)"));
    }

    @Test
    void starterCoreUsedIsMarkedOnlyAfterReservationCommit() throws IOException {
        String source = read("src/main/java/com/coreclaim/service/PendingClaimService.java");
        String asyncCreate = method(source, "private void completeClaimAsync", "private void finishPendingClaimAfterReservationCommit");
        String finalSuccess = method(source, "private void finishPendingClaimAfterReservationCommit", "private void compensateCommittedClaimCreationFailure");

        assertFalse(asyncCreate.contains("markStarterCoreUsedIfNeeded"));
        assertTrue(finalSuccess.contains("markStarterCoreUsedIfNeeded"));
    }

    @Test
    void pluginDisableCleansPendingReservations() throws IOException {
        String source = read("src/main/java/com/coreclaim/CoreClaimPlugin.java");

        assertTrue(source.contains("pendingCoreReservationService.shutdown()"));
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
