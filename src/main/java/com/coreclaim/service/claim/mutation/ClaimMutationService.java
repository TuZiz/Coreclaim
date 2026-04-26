package com.coreclaim.service.claim.mutation;

import com.coreclaim.model.Claim;
import com.coreclaim.model.ClaimFlag;
import com.coreclaim.model.ClaimFlagState;
import com.coreclaim.model.ClaimMemberSettings;
import com.coreclaim.model.ClaimPermission;
import com.coreclaim.service.claim.ClaimRuntime;
import com.coreclaim.service.claim.defaults.ClaimDefaultsService;
import com.coreclaim.service.claim.persistence.ClaimPersistenceRepository;
import com.coreclaim.service.claim.query.ClaimLookupService;
import java.util.Optional;
import java.util.UUID;
import org.bukkit.Location;

public final class ClaimMutationService {

    private final ClaimCreationMutations creationMutations;
    private final ClaimPropertyMutations propertyMutations;
    private final ClaimRelationMutations relationMutations;
    private final ClaimLifecycleMutations lifecycleMutations;

    public ClaimMutationService(
        ClaimRuntime runtime,
        ClaimLookupService lookupService,
        ClaimDefaultsService defaultsService,
        ClaimPersistenceRepository persistenceRepository
    ) {
        ClaimMutationContext context = new ClaimMutationContext(runtime, lookupService, defaultsService, persistenceRepository);
        this.creationMutations = new ClaimCreationMutations(context);
        this.propertyMutations = new ClaimPropertyMutations(context);
        this.relationMutations = new ClaimRelationMutations(context);
        this.lifecycleMutations = new ClaimLifecycleMutations(context);
    }

    public Optional<Claim> updateClaimServerId(int id, String serverId) {
        return propertyMutations.updateClaimServerId(id, serverId);
    }

    public void updateFlagState(Claim claim, ClaimFlag flag, ClaimFlagState state, UUID actorId) {
        propertyMutations.updateFlagState(claim, flag, state, actorId);
    }

    public Claim createClaim(UUID owner, String ownerName, String name, Location center, int initialDistance) {
        return creationMutations.createClaim(owner, ownerName, name, center, initialDistance);
    }

    public Claim createClaimFromBounds(
        UUID owner,
        String ownerName,
        String name,
        Location coreLocation,
        int minY,
        int maxY,
        int east,
        int south,
        int west,
        int north,
        boolean systemManaged
    ) {
        return creationMutations.createClaimFromBounds(owner, ownerName, name, coreLocation, minY, maxY, east, south, west, north, systemManaged);
    }

    public void updateBounds(Claim claim, int east, int south, int west, int north, UUID actorId) {
        propertyMutations.updateBounds(claim, east, south, west, north, actorId);
    }

    public void updateCoreVisibility(Claim claim, boolean coreVisible, UUID actorId) {
        propertyMutations.updateCoreVisibility(claim, coreVisible, actorId);
    }

    public void renameClaim(Claim claim, String name, UUID actorId) {
        propertyMutations.renameClaim(claim, name, actorId);
    }

    public void updateEnterMessage(Claim claim, String message, UUID actorId) {
        propertyMutations.updateEnterMessage(claim, message, actorId);
    }

    public void updateLeaveMessage(Claim claim, String message, UUID actorId) {
        propertyMutations.updateLeaveMessage(claim, message, actorId);
    }

    public void updateDenyAll(Claim claim, boolean denyAll, UUID actorId) {
        propertyMutations.updateDenyAll(claim, denyAll, actorId);
    }

    public void updateTeleportPoint(Claim claim, Location location, UUID actorId) {
        propertyMutations.updateTeleportPoint(claim, location, actorId);
    }

    public void updatePermission(Claim claim, ClaimPermission permission, boolean allowed, UUID actorId) {
        propertyMutations.updatePermission(claim, permission, allowed, actorId);
    }

    public boolean addTrustedMember(Claim claim, UUID memberId, UUID actorId) {
        return relationMutations.addTrustedMember(claim, memberId, actorId);
    }

    public boolean removeTrustedMember(Claim claim, UUID memberId, UUID actorId) {
        return relationMutations.removeTrustedMember(claim, memberId, actorId);
    }

    public boolean addDeniedMember(Claim claim, UUID memberId, UUID actorId) {
        return relationMutations.addDeniedMember(claim, memberId, actorId);
    }

    public boolean removeDeniedMember(Claim claim, UUID memberId, UUID actorId) {
        return relationMutations.removeDeniedMember(claim, memberId, actorId);
    }

    public ClaimMemberSettings memberSettings(Claim claim, UUID memberId) {
        return relationMutations.memberSettings(claim, memberId);
    }

    public boolean updateMemberPermission(Claim claim, UUID memberId, ClaimPermission permission, boolean allowed) {
        return relationMutations.updateMemberPermission(claim, memberId, permission, allowed);
    }

    public boolean transferClaim(Claim claim, UUID newOwner, String newOwnerName) {
        return relationMutations.transferClaim(claim, newOwner, newOwnerName);
    }

    public void cancelSaleListing(int claimId) {
        lifecycleMutations.cancelSaleListing(claimId);
    }

    public void removeClaim(Claim claim) {
        lifecycleMutations.removeClaim(claim);
    }

    public void save() {
        lifecycleMutations.save();
    }
}
