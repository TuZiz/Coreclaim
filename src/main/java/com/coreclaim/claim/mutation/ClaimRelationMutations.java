package com.coreclaim.claim.mutation;

import com.coreclaim.model.Claim;
import com.coreclaim.model.ClaimMemberSettings;
import com.coreclaim.model.ClaimPermission;
import com.coreclaim.sync.ClaimSyncEventType;
import java.util.UUID;

final class ClaimRelationMutations {

    private final ClaimMutationContext context;

    ClaimRelationMutations(ClaimMutationContext context) {
        this.context = context;
    }

    boolean addTrustedMember(Claim claim, UUID memberId, UUID actorId) {
        synchronized (context.runtime.mutationLock()) {
            if (!claim.addTrustedMember(memberId)) {
                return false;
            }
            claim.removeDeniedMember(memberId);
            ClaimMemberSettings settings = context.defaultsService.createTrustedMemberSettings();
            claim.setMemberSettings(memberId, settings);
            context.runtime.databaseManager().update(
                context.runtime.databaseManager().insertIgnoreSql("claim_members", "claim_id, player_uuid", "?, ?"),
                statement -> {
                    statement.setInt(1, claim.id());
                    statement.setString(2, memberId.toString());
                }
            );
            deleteRelation("claim_blacklist", claim.id(), memberId);
            deleteRelation("claim_member_permissions", claim.id(), memberId);
            context.persistenceRepository.saveMemberSettings(claim.id(), memberId, settings);
            context.publishClaimSync(ClaimSyncEventType.CLAIM_UPDATED, claim.id());
            context.recordInteractionActivity(claim, actorId);
            return true;
        }
    }

    boolean removeTrustedMember(Claim claim, UUID memberId, UUID actorId) {
        synchronized (context.runtime.mutationLock()) {
            boolean removedMember = claim.removeTrustedMember(memberId);
            claim.removeMemberSettings(memberId);
            int removedMemberRows = deleteRelation("claim_members", claim.id(), memberId);
            int removedPermissionRows = deleteRelation("claim_member_permissions", claim.id(), memberId);
            boolean removedGlobalTrust = context.runtime.profileService() != null
                && context.runtime.profileService().removeGlobalTrustedMember(claim.owner(), memberId);
            if (!removedMember && removedMemberRows <= 0 && removedPermissionRows <= 0 && !removedGlobalTrust) {
                return false;
            }
            context.publishClaimSync(ClaimSyncEventType.CLAIM_UPDATED, claim.id());
            context.recordInteractionActivity(claim, actorId);
            return true;
        }
    }

    boolean addDeniedMember(Claim claim, UUID memberId, UUID actorId) {
        synchronized (context.runtime.mutationLock()) {
            if (!claim.addDeniedMember(memberId)) {
                return false;
            }
            claim.removeTrustedMember(memberId);
            claim.removeMemberSettings(memberId);
            deleteRelation("claim_members", claim.id(), memberId);
            deleteRelation("claim_member_permissions", claim.id(), memberId);
            context.runtime.databaseManager().update(
                context.runtime.databaseManager().insertIgnoreSql("claim_blacklist", "claim_id, player_uuid", "?, ?"),
                statement -> {
                    statement.setInt(1, claim.id());
                    statement.setString(2, memberId.toString());
                }
            );
            context.publishClaimSync(ClaimSyncEventType.CLAIM_UPDATED, claim.id());
            context.recordInteractionActivity(claim, actorId);
            return true;
        }
    }

    boolean removeDeniedMember(Claim claim, UUID memberId, UUID actorId) {
        synchronized (context.runtime.mutationLock()) {
            if (!claim.removeDeniedMember(memberId)) {
                return false;
            }
            deleteRelation("claim_blacklist", claim.id(), memberId);
            context.publishClaimSync(ClaimSyncEventType.CLAIM_UPDATED, claim.id());
            context.recordInteractionActivity(claim, actorId);
            return true;
        }
    }

    ClaimMemberSettings memberSettings(Claim claim, UUID memberId) {
        ClaimMemberSettings settings = claim.memberSettings(memberId);
        return settings == null ? context.defaultsService.createMemberSettings(claim) : settings;
    }

    boolean updateMemberPermission(Claim claim, UUID memberId, ClaimPermission permission, boolean allowed) {
        synchronized (context.runtime.mutationLock()) {
            if (!claim.isTrusted(memberId)) {
                return false;
            }
            ClaimMemberSettings settings = claim.memberSettings(memberId);
            if (settings == null) {
                settings = context.defaultsService.createMemberSettings(claim);
                claim.setMemberSettings(memberId, settings);
            }
            settings.setPermission(permission, allowed);
            context.persistenceRepository.saveMemberSettings(claim.id(), memberId, settings);
            context.publishClaimSync(ClaimSyncEventType.CLAIM_UPDATED, claim.id());
            return true;
        }
    }

    boolean transferClaim(Claim claim, UUID newOwner, String newOwnerName) {
        if (claim == null || newOwner == null || claim.systemManaged()) {
            return false;
        }
        synchronized (context.runtime.mutationLock()) {
            Claim targetClaim = context.runtime.claims().get(claim.id());
            if (targetClaim == null) {
                return false;
            }
            UUID previousOwner = targetClaim.owner();
            if (previousOwner.equals(newOwner)) {
                return false;
            }
            String targetOwnerName = newOwnerName == null || newOwnerName.isBlank() ? newOwner.toString() : newOwnerName;
            boolean transferred = context.runtime.databaseManager().transaction(() -> {
                int updated = context.runtime.databaseManager().update(
                    "UPDATE claims SET owner_uuid = ?, owner_name = ? WHERE id = ? AND owner_uuid = ?",
                    statement -> {
                        statement.setString(1, newOwner.toString());
                        statement.setString(2, targetOwnerName);
                        statement.setInt(3, targetClaim.id());
                        statement.setString(4, previousOwner.toString());
                    }
                );
                if (updated <= 0) {
                    return false;
                }
                context.persistenceRepository.clearClaimRelations(targetClaim.id());
                context.cancelSaleListing(targetClaim.id());
                return true;
            });
            if (!transferred) {
                if (context.runtime.databaseManager().isMySql()) {
                    context.lookupService.refreshClaimFromDatabase(targetClaim.id());
                }
                return false;
            }
            targetClaim.setOwner(newOwner, targetOwnerName);
            targetClaim.clearTrustedMembers();
            targetClaim.clearDeniedMembers();
            targetClaim.clearMemberSettings();
            context.publishClaimSync(ClaimSyncEventType.CLAIM_OWNER_CHANGED, targetClaim.id());
            context.recordInteractionActivity(targetClaim, newOwner);
            return true;
        }
    }

    private int deleteRelation(String table, int claimId, UUID memberId) {
        return context.runtime.databaseManager().update(
            "DELETE FROM " + table + " WHERE claim_id = ? AND player_uuid = ?",
            statement -> {
                statement.setInt(1, claimId);
                statement.setString(2, memberId.toString());
            }
        );
    }
}
