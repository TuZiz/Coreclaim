package com.coreclaim.service.claim.mutation;

import com.coreclaim.model.Claim;
import com.coreclaim.service.ClaimCleanupService;
import com.coreclaim.service.ClaimSyncEventType;
import com.coreclaim.service.claim.ClaimRuntime;
import com.coreclaim.service.claim.defaults.ClaimDefaultsService;
import com.coreclaim.service.claim.persistence.ClaimPersistenceRepository;
import com.coreclaim.service.claim.query.ClaimLookupService;

final class ClaimMutationContext {

    final ClaimRuntime runtime;
    final ClaimLookupService lookupService;
    final ClaimDefaultsService defaultsService;
    final ClaimPersistenceRepository persistenceRepository;

    ClaimMutationContext(
        ClaimRuntime runtime,
        ClaimLookupService lookupService,
        ClaimDefaultsService defaultsService,
        ClaimPersistenceRepository persistenceRepository
    ) {
        this.runtime = runtime;
        this.lookupService = lookupService;
        this.defaultsService = defaultsService;
        this.persistenceRepository = persistenceRepository;
    }

    void cancelSaleListing(int claimId) {
        runtime.databaseManager().update(
            "DELETE FROM claim_sale_listings WHERE claim_id = ?",
            statement -> statement.setInt(1, claimId)
        );
    }

    void trackNewClaim(Claim claim) {
        ClaimCleanupService cleanupService = runtime.claimCleanupService();
        if (cleanupService != null) {
            cleanupService.trackNewClaim(claim);
        }
    }

    void untrackClaim(int claimId) {
        ClaimCleanupService cleanupService = runtime.claimCleanupService();
        if (cleanupService != null) {
            cleanupService.removeTracking(claimId);
        }
    }

    void recordBuildActivity(Claim claim, java.util.UUID actorId) {
        ClaimCleanupService cleanupService = runtime.claimCleanupService();
        if (cleanupService != null) {
            cleanupService.recordBuildActivity(claim, actorId);
        }
    }

    void recordInteractionActivity(Claim claim, java.util.UUID actorId) {
        ClaimCleanupService cleanupService = runtime.claimCleanupService();
        if (cleanupService != null) {
            cleanupService.recordInteractionActivity(claim, actorId);
        }
    }

    void publishClaimSync(ClaimSyncEventType eventType, int claimId) {
        if (!runtime.databaseManager().isMySql()) {
            return;
        }
        try {
            runtime.claimSyncPublisher().publish(eventType, claimId);
        } catch (RuntimeException exception) {
            runtime.plugin().getLogger().warning("Failed to publish claim sync event " + eventType.wireName()
                + " for claim " + claimId + ": " + exception.getMessage());
        }
    }
}
