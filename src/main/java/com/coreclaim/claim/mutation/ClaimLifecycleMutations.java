package com.coreclaim.claim.mutation;

import com.coreclaim.model.Claim;
import com.coreclaim.sync.ClaimSyncEventType;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;

final class ClaimLifecycleMutations {

    private final ClaimMutationContext context;

    ClaimLifecycleMutations(ClaimMutationContext context) {
        this.context = context;
    }

    void cancelSaleListing(int claimId) {
        context.cancelSaleListing(claimId);
    }

    void removeClaim(Claim claim) {
        synchronized (context.runtime.mutationLock()) {
            removeCommittedClaimRecord(claim);

            World world = context.lookupService.isLocalClaim(claim) ? context.runtime.plugin().getServer().getWorld(claim.world()) : null;
            if (world != null) {
                Location coreLocation = new Location(world, claim.centerX(), claim.centerY(), claim.centerZ());
                context.runtime.plugin().platformScheduler().runLocationTask(coreLocation, () -> {
                    if (coreLocation.getBlock().getType() == context.runtime.plugin().settings().coreMaterial()) {
                        coreLocation.getBlock().setType(Material.AIR, false);
                    }
                });
            }
        }
    }

    void removeCommittedClaimRecord(Claim claim) {
        synchronized (context.runtime.mutationLock()) {
            context.runtime.claims().remove(claim.id());
            context.lookupService.rebuildClaimChunkIndex();
            context.cancelSaleListing(claim.id());
            context.runtime.databaseManager().update(
                "DELETE FROM claims WHERE id = ?",
                statement -> statement.setInt(1, claim.id())
            );
            context.publishClaimSync(ClaimSyncEventType.CLAIM_DELETED, claim.id());
            context.untrackClaim(claim.id());
        }
    }

    void save() {
        if (context.runtime.databaseManager().isMySql()) {
            return;
        }
        synchronized (context.runtime.mutationLock()) {
            context.persistenceRepository.saveAllClaims(context.runtime.claims().values(), context.lookupService::displayServerId);
        }
    }
}
