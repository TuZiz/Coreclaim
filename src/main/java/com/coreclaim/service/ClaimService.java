package com.coreclaim.service;

import com.coreclaim.sync.ClaimSyncPublisher;
import com.coreclaim.cleanup.ClaimCleanupService;
import com.coreclaim.profile.ProfileService;
import com.coreclaim.CoreClaimPlugin;
import com.coreclaim.model.Claim;
import com.coreclaim.model.ClaimFlag;
import com.coreclaim.model.ClaimFlagState;
import com.coreclaim.model.ClaimMemberSettings;
import com.coreclaim.model.ClaimPermission;
import com.coreclaim.claim.ClaimRuntime;
import com.coreclaim.claim.auth.ClaimAuthorizationService.AuthorizationDecision;
import com.coreclaim.claim.auth.ClaimAuthorizationService;
import com.coreclaim.claim.defaults.ClaimDefaultsService;
import com.coreclaim.claim.mutation.ClaimMutationService;
import com.coreclaim.claim.persistence.ClaimPersistenceRepository;
import com.coreclaim.claim.query.ClaimLookupService;
import com.coreclaim.storage.DatabaseManager;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;
import org.bukkit.Location;

public final class ClaimService {

    private final Map<Integer, Claim> claims = new ConcurrentHashMap<>();
    private final Object mutationLock = new Object();
    private final ClaimRuntime runtime;
    private final ClaimPersistenceRepository persistenceRepository;
    private final ClaimLookupService lookupService;
    private final ClaimAuthorizationService authorizationService;
    private final ClaimDefaultsService defaultsService;
    private final ClaimMutationService mutationService;

    public ClaimService(CoreClaimPlugin plugin, DatabaseManager databaseManager, ProfileService profileService) {
        this.runtime = new ClaimRuntime(plugin, databaseManager, profileService, claims, mutationLock);
        this.persistenceRepository = new ClaimPersistenceRepository(runtime);
        this.lookupService = new ClaimLookupService(runtime, persistenceRepository);
        this.authorizationService = new ClaimAuthorizationService(runtime);
        this.defaultsService = new ClaimDefaultsService(runtime, persistenceRepository);
        this.mutationService = new ClaimMutationService(runtime, lookupService, defaultsService, persistenceRepository);
        persistenceRepository.backfillMissingServerIds(currentServerId());
        persistenceRepository.migrateMergedPermissionData();
        lookupService.reloadClaims();
    }

    public String currentServerId() {
        return lookupService.currentServerId();
    }

    public void setClaimSyncPublisher(ClaimSyncPublisher claimSyncPublisher) {
        runtime.setClaimSyncPublisher(claimSyncPublisher);
    }

    public void setClaimCleanupService(ClaimCleanupService claimCleanupService) {
        runtime.setClaimCleanupService(claimCleanupService);
    }

    public String effectiveServerId(Claim claim) {
        return lookupService.effectiveServerId(claim);
    }

    public boolean isLocalClaim(Claim claim) {
        return lookupService.isLocalClaim(claim);
    }

    public String displayServerId(Claim claim) {
        return lookupService.displayServerId(claim);
    }

    public boolean countsTowardQuota(Claim claim) {
        return lookupService.countsTowardQuota(claim);
    }

    public boolean matchesConfiguredDefaults(Claim claim) {
        return defaultsService.matchesConfiguredDefaults(claim);
    }

    public boolean hasManualRuleOverrides(Claim claim) {
        return defaultsService.hasManualRuleOverrides(claim);
    }

    public String ruleProfileName(Claim claim) {
        return lookupService.ruleProfileName(claim);
    }

    public TeleportTarget teleportTarget(Claim claim, float fallbackYaw, float fallbackPitch) {
        if (claim != null && claim.hasTeleportPoint()) {
            return new TeleportTarget(
                claim.world(),
                claim.teleportX(),
                claim.teleportY(),
                claim.teleportZ(),
                claim.teleportYaw() == null ? fallbackYaw : claim.teleportYaw(),
                claim.teleportPitch() == null ? fallbackPitch : claim.teleportPitch(),
                true
            );
        }
        return new TeleportTarget(
            claim.world(),
            claim.centerX() + 0.5D,
            claim.centerY() + 1D,
            claim.centerZ() + 0.5D,
            fallbackYaw,
            fallbackPitch,
            false
        );
    }

    public ClaimFlagState flagState(Claim claim, ClaimFlag flag) {
        return authorizationService.flagState(claim, flag);
    }

    public boolean hasFlagPermission(Claim claim, UUID playerId, ClaimFlag flag) {
        return authorizationService.hasFlagPermission(claim, playerId, flag);
    }

    public AuthorizationDecision permissionDecision(Claim claim, UUID playerId, ClaimPermission permission, boolean bypassing) {
        return authorizationService.permissionDecision(claim, playerId, permission, bypassing);
    }

    public Optional<Claim> findClaim(Location location) {
        return lookupService.findClaim(location);
    }

    public Optional<Claim> findPlayerPresenceClaim(Location location) {
        return lookupService.findPlayerPresenceClaim(location);
    }

    public Optional<Claim> findClaimById(int id) {
        return lookupService.findClaimById(id);
    }

    public Optional<Claim> findClaimByIdOrLoad(int id) {
        return lookupService.findClaimByIdOrLoad(id);
    }

    public Optional<Claim> findClaimByIdFresh(int id) {
        return lookupService.findClaimByIdFresh(id);
    }

    public Optional<Claim> refreshClaimFromDatabase(int id) {
        return lookupService.refreshClaimFromDatabase(id);
    }

    public ClaimRefreshResult reloadClaim(int id) {
        return lookupService.reloadClaim(id);
    }

    public Optional<Claim> updateClaimServerId(int id, String serverId) {
        return mutationService.updateClaimServerId(id, serverId);
    }

    public List<Claim> claimsOf(UUID owner) {
        return claimsOf(owner, false);
    }

    public List<Claim> claimsOf(UUID owner, boolean includeSystem) {
        return lookupService.claimsOf(owner, includeSystem);
    }

    public List<Claim> claimsOfFresh(UUID owner) {
        return claimsOfFresh(owner, false);
    }

    public List<Claim> claimsOfFresh(UUID owner, boolean includeSystem) {
        return lookupService.claimsOfFresh(owner, includeSystem);
    }

    public int countClaims(UUID owner) {
        return countClaims(owner, false);
    }

    public int countClaims(UUID owner, boolean includeSystem) {
        return claimsOfFresh(owner, includeSystem).size();
    }

    public List<ClaimListEntry> visibleClaimsOf(UUID playerId) {
        return visibleClaimsOf(playerId, false);
    }

    public List<ClaimListEntry> visibleClaimsOf(UUID playerId, boolean includeSystem) {
        return lookupService.visibleClaimsOf(playerId, includeSystem);
    }

    public List<ClaimListEntry> visibleClaimsOfFresh(UUID playerId) {
        return visibleClaimsOfFresh(playerId, false);
    }

    public List<ClaimListEntry> visibleClaimsOfFresh(UUID playerId, boolean includeSystem) {
        return lookupService.visibleClaimsOfFresh(playerId, includeSystem);
    }

    public Optional<ClaimListEntry> visibleClaimEntryFresh(UUID playerId, int claimId) {
        return visibleClaimEntryFresh(playerId, claimId, false);
    }

    public Optional<ClaimListEntry> visibleClaimEntryFresh(UUID playerId, int claimId, boolean includeSystem) {
        return lookupService.visibleClaimEntryFresh(playerId, claimId, includeSystem);
    }

    public List<Claim> allClaims() {
        return lookupService.allClaims();
    }

    public List<Claim> findClaimsByName(String rawName) {
        return lookupService.findClaimsByName(rawName);
    }

    public List<Claim> findClaimsByNameFresh(String rawName) {
        return lookupService.findClaimsByNameFresh(rawName);
    }

    public int reloadClaims() {
        return lookupService.reloadClaims();
    }

    public boolean isClaimNameTaken(String rawName) {
        return isClaimNameTaken(rawName, null);
    }

    public boolean isClaimNameTaken(String rawName, Integer excludedClaimId) {
        return lookupService.isClaimNameTaken(rawName, excludedClaimId);
    }

    public boolean overlaps(String world, int minX, int maxX, int minZ, int maxZ, Integer ignoredId) {
        return overlaps(world, minX, maxX, -64, 319, minZ, maxZ, ignoredId, true);
    }

    public boolean overlaps(String world, int minX, int maxX, int minY, int maxY, int minZ, int maxZ, Integer ignoredId, boolean fullHeight) {
        return lookupService.overlaps(world, minX, maxX, minY, maxY, minZ, maxZ, ignoredId, fullHeight);
    }

    public boolean hasCoreWithinSpacing(String world, int centerX, int centerZ, int spacing, Integer ignoredId) {
        return lookupService.hasCoreWithinSpacing(world, centerX, centerZ, spacing, ignoredId);
    }

    public boolean hasClaimWithinGap(
        String world,
        int minX,
        int maxX,
        int minY,
        int maxY,
        int minZ,
        int maxZ,
        int gap,
        Integer ignoredId,
        boolean fullHeight,
        Predicate<Claim> filter
    ) {
        return lookupService.hasClaimWithinGap(world, minX, maxX, minY, maxY, minZ, maxZ, gap, ignoredId, fullHeight, filter);
    }

    public boolean canAccess(Claim claim, UUID playerId) {
        return authorizationService.canAccess(claim, playerId);
    }

    public boolean hasPermission(Claim claim, UUID playerId, ClaimPermission permission) {
        return authorizationService.hasPermission(claim, playerId, permission);
    }

    public void updateFlagState(Claim claim, ClaimFlag flag, ClaimFlagState state) {
        updateFlagState(claim, flag, state, null);
    }

    public void updateFlagState(Claim claim, ClaimFlag flag, ClaimFlagState state, UUID actorId) {
        mutationService.updateFlagState(claim, flag, state, actorId);
    }

    public Claim createClaim(UUID owner, String ownerName, String name, Location center, int initialDistance) {
        return mutationService.createClaim(owner, ownerName, name, center, initialDistance);
    }

    public Claim createClaimFromBounds(UUID owner, String ownerName, String name, Location coreLocation, int minY, int maxY, int east, int south, int west, int north) {
        return createClaimFromBounds(owner, ownerName, name, coreLocation, minY, maxY, east, south, west, north, false);
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
        return mutationService.createClaimFromBounds(owner, ownerName, name, coreLocation, minY, maxY, east, south, west, north, systemManaged);
    }

    public void updateBounds(Claim claim, int east, int south, int west, int north) {
        updateBounds(claim, east, south, west, north, null);
    }

    public void updateBounds(Claim claim, int east, int south, int west, int north, UUID actorId) {
        mutationService.updateBounds(claim, east, south, west, north, actorId);
    }

    public void updateCoreVisibility(Claim claim, boolean coreVisible) {
        updateCoreVisibility(claim, coreVisible, null);
    }

    public void updateCoreVisibility(Claim claim, boolean coreVisible, UUID actorId) {
        mutationService.updateCoreVisibility(claim, coreVisible, actorId);
    }

    public void renameClaim(Claim claim, String name) {
        renameClaim(claim, name, null);
    }

    public void renameClaim(Claim claim, String name, UUID actorId) {
        mutationService.renameClaim(claim, name, actorId);
    }

    public void updateEnterMessage(Claim claim, String message) {
        updateEnterMessage(claim, message, null);
    }

    public void updateEnterMessage(Claim claim, String message, UUID actorId) {
        mutationService.updateEnterMessage(claim, message, actorId);
    }

    public void updateLeaveMessage(Claim claim, String message) {
        updateLeaveMessage(claim, message, null);
    }

    public void updateLeaveMessage(Claim claim, String message, UUID actorId) {
        mutationService.updateLeaveMessage(claim, message, actorId);
    }

    public void updateDenyAll(Claim claim, boolean denyAll) {
        updateDenyAll(claim, denyAll, null);
    }

    public void updateDenyAll(Claim claim, boolean denyAll, UUID actorId) {
        mutationService.updateDenyAll(claim, denyAll, actorId);
    }

    public void updateTeleportPoint(Claim claim, Location location) {
        updateTeleportPoint(claim, location, null);
    }

    public void updateTeleportPoint(Claim claim, Location location, UUID actorId) {
        mutationService.updateTeleportPoint(claim, location, actorId);
    }

    public void updatePermission(Claim claim, ClaimPermission permission, boolean allowed) {
        updatePermission(claim, permission, allowed, null);
    }

    public void updatePermission(Claim claim, ClaimPermission permission, boolean allowed, UUID actorId) {
        mutationService.updatePermission(claim, permission, allowed, actorId);
    }

    public boolean addTrustedMember(Claim claim, UUID memberId) {
        return addTrustedMember(claim, memberId, null);
    }

    public boolean addTrustedMember(Claim claim, UUID memberId, UUID actorId) {
        return mutationService.addTrustedMember(claim, memberId, actorId);
    }

    public boolean removeTrustedMember(Claim claim, UUID memberId) {
        return removeTrustedMember(claim, memberId, null);
    }

    public boolean removeTrustedMember(Claim claim, UUID memberId, UUID actorId) {
        return mutationService.removeTrustedMember(claim, memberId, actorId);
    }

    public boolean addDeniedMember(Claim claim, UUID memberId) {
        return addDeniedMember(claim, memberId, null);
    }

    public boolean addDeniedMember(Claim claim, UUID memberId, UUID actorId) {
        return mutationService.addDeniedMember(claim, memberId, actorId);
    }

    public boolean removeDeniedMember(Claim claim, UUID memberId) {
        return removeDeniedMember(claim, memberId, null);
    }

    public boolean removeDeniedMember(Claim claim, UUID memberId, UUID actorId) {
        return mutationService.removeDeniedMember(claim, memberId, actorId);
    }

    public boolean addBlacklistedMember(Claim claim, UUID memberId) {
        return addDeniedMember(claim, memberId);
    }

    public boolean removeBlacklistedMember(Claim claim, UUID memberId) {
        return removeDeniedMember(claim, memberId);
    }

    public ClaimMemberSettings memberSettings(Claim claim, UUID memberId) {
        return mutationService.memberSettings(claim, memberId);
    }

    public boolean updateMemberPermission(Claim claim, UUID memberId, ClaimPermission permission, boolean allowed) {
        return mutationService.updateMemberPermission(claim, memberId, permission, allowed);
    }

    public boolean transferClaim(Claim claim, UUID newOwner, String newOwnerName) {
        return mutationService.transferClaim(claim, newOwner, newOwnerName);
    }

    public void cancelSaleListing(int claimId) {
        mutationService.cancelSaleListing(claimId);
    }

    public void removeClaim(Claim claim) {
        mutationService.removeClaim(claim);
    }

    public void save() {
        mutationService.save();
    }

    public record ClaimRefreshResult(Claim previousClaim, Claim currentClaim) {
    }

    public enum ClaimListRelation {
        OWNER,
        TRUSTED_MEMBER
    }

    public record ClaimListEntry(Claim claim, ClaimListRelation relation) {
    }

    public record TeleportTarget(
        String world,
        double x,
        double y,
        double z,
        float yaw,
        float pitch,
        boolean custom
    ) {
    }
}
