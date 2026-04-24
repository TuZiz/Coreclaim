package com.coreclaim.service.claim.mutation;

import com.coreclaim.model.Claim;
import com.coreclaim.model.ClaimFlag;
import com.coreclaim.model.ClaimFlagState;
import com.coreclaim.model.ClaimMemberSettings;
import com.coreclaim.model.ClaimPermission;
import com.coreclaim.service.ClaimCleanupService;
import com.coreclaim.service.ClaimService;
import com.coreclaim.service.ClaimSyncEventType;
import com.coreclaim.service.claim.ClaimRuntime;
import com.coreclaim.service.claim.defaults.ClaimDefaultsService;
import com.coreclaim.service.claim.persistence.ClaimPersistenceRepository;
import com.coreclaim.service.claim.query.ClaimLookupService;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;

public final class ClaimMutationService {

    private final ClaimRuntime runtime;
    private final ClaimLookupService lookupService;
    private final ClaimDefaultsService defaultsService;
    private final ClaimPersistenceRepository persistenceRepository;

    public ClaimMutationService(
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

    public Optional<Claim> updateClaimServerId(int id, String serverId) {
        String sanitizedServerId = serverId == null ? "" : serverId.trim();
        if (sanitizedServerId.isEmpty()) {
            return Optional.empty();
        }
        synchronized (runtime.mutationLock()) {
            int updated = runtime.databaseManager().update(
                "UPDATE claims SET server_id = ? WHERE id = ?",
                statement -> {
                    statement.setString(1, sanitizedServerId);
                    statement.setInt(2, id);
                }
            );
            if (updated <= 0) {
                return Optional.empty();
            }
            ClaimService.ClaimRefreshResult refreshed = lookupService.reloadClaim(id);
            publishClaimSync(ClaimSyncEventType.CLAIM_SERVER_CHANGED, id);
            return refreshed.currentClaim() == null ? Optional.empty() : lookupService.findClaimById(id);
        }
    }

    public void updateFlagState(Claim claim, ClaimFlag flag, ClaimFlagState state, UUID actorId) {
        if (claim == null || flag == null) {
            return;
        }
        ClaimFlagState nextState = state == null ? ClaimFlagState.UNSET : state;
        claim.setFlagState(flag, nextState);
        synchronized (runtime.mutationLock()) {
            if (nextState == ClaimFlagState.UNSET) {
                persistenceRepository.deleteFlagState(claim.id(), flag);
            } else {
                persistenceRepository.saveFlagState(claim.id(), flag, nextState);
            }
        }
        publishClaimSync(ClaimSyncEventType.CLAIM_UPDATED, claim.id());
        recordInteractionActivity(claim, actorId);
    }

    public Claim createClaim(UUID owner, String ownerName, String name, Location center, int initialDistance) {
        synchronized (runtime.mutationLock()) {
            String sanitizedName = lookupService.validateAvailableClaimName(name, null);
            String currentServerId = lookupService.currentServerId();
            int minY = center.getWorld() == null ? -64 : center.getWorld().getMinHeight();
            int maxY = center.getWorld() == null ? 319 : center.getWorld().getMaxHeight() - 1;
            long createdAt = Instant.now().getEpochSecond();
            int generatedId = (int) runtime.databaseManager().insertAndReturnKey(
                """
                INSERT INTO claims (
                    owner_uuid, owner_name, name, core_visible, world, server_id, center_x, center_y, center_z,
                    min_y, max_y, full_height, radius, east, south, west, north, enter_message, leave_message,
                    allow_place, allow_break, allow_interact, allow_container, allow_redstone, allow_explosion, allow_bucket, allow_teleport, allow_flight, system_managed, last_expanded_at, created_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                statement -> {
                    statement.setString(1, owner.toString());
                    statement.setString(2, ownerName);
                    statement.setString(3, sanitizedName);
                    statement.setInt(4, 1);
                    statement.setString(5, center.getWorld().getName());
                    statement.setString(6, currentServerId);
                    statement.setInt(7, center.getBlockX());
                    statement.setInt(8, center.getBlockY());
                    statement.setInt(9, center.getBlockZ());
                    statement.setInt(10, minY);
                    statement.setInt(11, maxY);
                    statement.setInt(12, 1);
                    statement.setInt(13, initialDistance);
                    statement.setInt(14, initialDistance);
                    statement.setInt(15, initialDistance);
                    statement.setInt(16, initialDistance);
                    statement.setInt(17, initialDistance);
                    statement.setString(18, "");
                    statement.setString(19, "");
                    statement.setInt(20, 0);
                    statement.setInt(21, 0);
                    statement.setInt(22, 0);
                    statement.setInt(23, 0);
                    statement.setInt(24, 0);
                    statement.setInt(25, 0);
                    statement.setInt(26, 0);
                    statement.setInt(27, 0);
                    statement.setInt(28, 1);
                    statement.setInt(29, 0);
                    statement.setLong(30, 0L);
                    statement.setLong(31, createdAt);
                }
            );

            Claim claim = new Claim(
                generatedId,
                owner,
                ownerName,
                sanitizedName,
                currentServerId,
                center.getWorld().getName(),
                center.getBlockX(),
                center.getBlockY(),
                center.getBlockZ(),
                minY,
                maxY,
                true,
                initialDistance,
                initialDistance,
                initialDistance,
                initialDistance,
                createdAt,
                true,
                "",
                "",
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                true,
                false,
                false,
                null,
                null,
                null,
                null,
                null,
                0L
            );
            defaultsService.applyClaimDefaults(claim);
            runtime.claims().put(claim.id(), claim);
            lookupService.rebuildClaimChunkIndex();
            publishClaimSync(ClaimSyncEventType.CLAIM_CREATED, claim.id());
            trackNewClaim(claim);
            return claim;
        }
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
        synchronized (runtime.mutationLock()) {
            String sanitizedName = lookupService.validateAvailableClaimName(name, null);
            String currentServerId = lookupService.currentServerId();
            long createdAt = Instant.now().getEpochSecond();
            int generatedId = (int) runtime.databaseManager().insertAndReturnKey(
                """
                INSERT INTO claims (
                    owner_uuid, owner_name, name, core_visible, world, server_id, center_x, center_y, center_z,
                    min_y, max_y, full_height, radius, east, south, west, north, enter_message, leave_message,
                    allow_place, allow_break, allow_interact, allow_container, allow_redstone, allow_explosion, allow_bucket, allow_teleport, allow_flight, system_managed, last_expanded_at, created_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                statement -> {
                    statement.setString(1, owner.toString());
                    statement.setString(2, ownerName);
                    statement.setString(3, sanitizedName);
                    statement.setInt(4, 1);
                    statement.setString(5, coreLocation.getWorld().getName());
                    statement.setString(6, currentServerId);
                    statement.setInt(7, coreLocation.getBlockX());
                    statement.setInt(8, coreLocation.getBlockY());
                    statement.setInt(9, coreLocation.getBlockZ());
                    statement.setInt(10, minY);
                    statement.setInt(11, maxY);
                    statement.setInt(12, 0);
                    statement.setInt(13, Math.max(Math.max(east, west), Math.max(south, north)));
                    statement.setInt(14, east);
                    statement.setInt(15, south);
                    statement.setInt(16, west);
                    statement.setInt(17, north);
                    statement.setString(18, "");
                    statement.setString(19, "");
                    statement.setInt(20, 0);
                    statement.setInt(21, 0);
                    statement.setInt(22, 0);
                    statement.setInt(23, 0);
                    statement.setInt(24, 0);
                    statement.setInt(25, 0);
                    statement.setInt(26, 0);
                    statement.setInt(27, 0);
                    statement.setInt(28, 1);
                    statement.setInt(29, systemManaged ? 1 : 0);
                    statement.setLong(30, 0L);
                    statement.setLong(31, createdAt);
                }
            );

            Claim claim = new Claim(
                generatedId,
                owner,
                ownerName,
                sanitizedName,
                currentServerId,
                coreLocation.getWorld().getName(),
                coreLocation.getBlockX(),
                coreLocation.getBlockY(),
                coreLocation.getBlockZ(),
                minY,
                maxY,
                false,
                east,
                south,
                west,
                north,
                createdAt,
                true,
                "",
                "",
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                true,
                systemManaged,
                false,
                null,
                null,
                null,
                null,
                null,
                0L
            );
            defaultsService.applyClaimDefaults(claim);
            runtime.claims().put(claim.id(), claim);
            lookupService.rebuildClaimChunkIndex();
            publishClaimSync(ClaimSyncEventType.CLAIM_CREATED, claim.id());
            trackNewClaim(claim);
            return claim;
        }
    }

    public void updateBounds(Claim claim, int east, int south, int west, int north, UUID actorId) {
        synchronized (runtime.mutationLock()) {
            claim.setBounds(east, south, west, north);
            claim.setLastExpandedAt(Instant.now().getEpochSecond());
            runtime.databaseManager().update(
                "UPDATE claims SET radius = ?, east = ?, south = ?, west = ?, north = ?, last_expanded_at = ? WHERE id = ?",
                statement -> {
                    statement.setInt(1, claim.displayRadius());
                    statement.setInt(2, east);
                    statement.setInt(3, south);
                    statement.setInt(4, west);
                    statement.setInt(5, north);
                    statement.setLong(6, claim.lastExpandedAt());
                    statement.setInt(7, claim.id());
                }
            );
            lookupService.rebuildClaimChunkIndex();
            publishClaimSync(ClaimSyncEventType.CLAIM_UPDATED, claim.id());
        }
        recordBuildActivity(claim, actorId);
    }

    public void updateCoreVisibility(Claim claim, boolean coreVisible, UUID actorId) {
        synchronized (runtime.mutationLock()) {
            claim.setCoreVisible(coreVisible);
            runtime.databaseManager().update(
                "UPDATE claims SET core_visible = ? WHERE id = ?",
                statement -> {
                    statement.setInt(1, coreVisible ? 1 : 0);
                    statement.setInt(2, claim.id());
                }
            );
            publishClaimSync(ClaimSyncEventType.CLAIM_UPDATED, claim.id());
        }
        recordInteractionActivity(claim, actorId);
    }

    public void renameClaim(Claim claim, String name, UUID actorId) {
        synchronized (runtime.mutationLock()) {
            String sanitizedName = lookupService.validateAvailableClaimName(name, claim.id());
            claim.setName(sanitizedName);
            runtime.databaseManager().update(
                "UPDATE claims SET name = ? WHERE id = ?",
                statement -> {
                    statement.setString(1, sanitizedName);
                    statement.setInt(2, claim.id());
                }
            );
            publishClaimSync(ClaimSyncEventType.CLAIM_UPDATED, claim.id());
        }
        recordInteractionActivity(claim, actorId);
    }

    public void updateEnterMessage(Claim claim, String message, UUID actorId) {
        synchronized (runtime.mutationLock()) {
            claim.setEnterMessage(message);
            runtime.databaseManager().update(
                "UPDATE claims SET enter_message = ? WHERE id = ?",
                statement -> {
                    statement.setString(1, claim.enterMessage());
                    statement.setInt(2, claim.id());
                }
            );
            publishClaimSync(ClaimSyncEventType.CLAIM_UPDATED, claim.id());
        }
        recordInteractionActivity(claim, actorId);
    }

    public void updateLeaveMessage(Claim claim, String message, UUID actorId) {
        synchronized (runtime.mutationLock()) {
            claim.setLeaveMessage(message);
            runtime.databaseManager().update(
                "UPDATE claims SET leave_message = ? WHERE id = ?",
                statement -> {
                    statement.setString(1, claim.leaveMessage());
                    statement.setInt(2, claim.id());
                }
            );
            publishClaimSync(ClaimSyncEventType.CLAIM_UPDATED, claim.id());
        }
        recordInteractionActivity(claim, actorId);
    }

    public void updateDenyAll(Claim claim, boolean denyAll, UUID actorId) {
        synchronized (runtime.mutationLock()) {
            claim.setDenyAll(denyAll);
            runtime.databaseManager().update(
                "UPDATE claims SET deny_all = ? WHERE id = ?",
                statement -> {
                    statement.setInt(1, denyAll ? 1 : 0);
                    statement.setInt(2, claim.id());
                }
            );
            publishClaimSync(ClaimSyncEventType.CLAIM_UPDATED, claim.id());
        }
        recordInteractionActivity(claim, actorId);
    }

    public void updateTeleportPoint(Claim claim, Location location, UUID actorId) {
        synchronized (runtime.mutationLock()) {
            if (location == null) {
                claim.clearTeleportPoint();
            } else {
                claim.setTeleportPoint(location.getX(), location.getY(), location.getZ(), location.getYaw(), location.getPitch());
            }
            runtime.databaseManager().update(
                "UPDATE claims SET tp_x = ?, tp_y = ?, tp_z = ?, tp_yaw = ?, tp_pitch = ? WHERE id = ?",
                statement -> {
                    if (claim.hasTeleportPoint()) {
                        statement.setDouble(1, claim.teleportX());
                        statement.setDouble(2, claim.teleportY());
                        statement.setDouble(3, claim.teleportZ());
                        statement.setDouble(4, claim.teleportYaw());
                        statement.setDouble(5, claim.teleportPitch());
                    } else {
                        statement.setNull(1, java.sql.Types.DOUBLE);
                        statement.setNull(2, java.sql.Types.DOUBLE);
                        statement.setNull(3, java.sql.Types.DOUBLE);
                        statement.setNull(4, java.sql.Types.DOUBLE);
                        statement.setNull(5, java.sql.Types.DOUBLE);
                    }
                    statement.setInt(6, claim.id());
                }
            );
            publishClaimSync(ClaimSyncEventType.CLAIM_UPDATED, claim.id());
        }
        recordInteractionActivity(claim, actorId);
    }

    public void updatePermission(Claim claim, ClaimPermission permission, boolean allowed, UUID actorId) {
        synchronized (runtime.mutationLock()) {
            claim.setPermission(permission, allowed);
            String column = switch (permission) {
                case PLACE -> "allow_place";
                case BREAK -> "allow_break";
                case INTERACT -> "allow_interact";
                case CONTAINER -> "allow_container";
                case REDSTONE -> "allow_redstone";
                case EXPLOSION -> "allow_explosion";
                case BUCKET -> "allow_bucket";
                case TELEPORT -> "allow_teleport";
                case FLIGHT -> "allow_flight";
            };
            runtime.databaseManager().update(
                "UPDATE claims SET " + column + " = ? WHERE id = ?",
                statement -> {
                    statement.setInt(1, allowed ? 1 : 0);
                    statement.setInt(2, claim.id());
                }
            );
            publishClaimSync(ClaimSyncEventType.CLAIM_UPDATED, claim.id());
        }
        recordInteractionActivity(claim, actorId);
    }

    public boolean addTrustedMember(Claim claim, UUID memberId, UUID actorId) {
        synchronized (runtime.mutationLock()) {
            if (!claim.addTrustedMember(memberId)) {
                return false;
            }
            claim.removeDeniedMember(memberId);
            claim.removeMemberSettings(memberId);
            runtime.databaseManager().update(
                runtime.databaseManager().insertIgnoreSql("claim_members", "claim_id, player_uuid", "?, ?"),
                statement -> {
                    statement.setInt(1, claim.id());
                    statement.setString(2, memberId.toString());
                }
            );
            runtime.databaseManager().update(
                "DELETE FROM claim_blacklist WHERE claim_id = ? AND player_uuid = ?",
                statement -> {
                    statement.setInt(1, claim.id());
                    statement.setString(2, memberId.toString());
                }
            );
            runtime.databaseManager().update(
                "DELETE FROM claim_member_permissions WHERE claim_id = ? AND player_uuid = ?",
                statement -> {
                    statement.setInt(1, claim.id());
                    statement.setString(2, memberId.toString());
                }
            );
            publishClaimSync(ClaimSyncEventType.CLAIM_UPDATED, claim.id());
            recordInteractionActivity(claim, actorId);
            return true;
        }
    }

    public boolean removeTrustedMember(Claim claim, UUID memberId, UUID actorId) {
        synchronized (runtime.mutationLock()) {
            if (!claim.removeTrustedMember(memberId)) {
                return false;
            }
            claim.removeMemberSettings(memberId);
            runtime.databaseManager().update(
                "DELETE FROM claim_members WHERE claim_id = ? AND player_uuid = ?",
                statement -> {
                    statement.setInt(1, claim.id());
                    statement.setString(2, memberId.toString());
                }
            );
            runtime.databaseManager().update(
                "DELETE FROM claim_member_permissions WHERE claim_id = ? AND player_uuid = ?",
                statement -> {
                    statement.setInt(1, claim.id());
                    statement.setString(2, memberId.toString());
                }
            );
            publishClaimSync(ClaimSyncEventType.CLAIM_UPDATED, claim.id());
            recordInteractionActivity(claim, actorId);
            return true;
        }
    }

    public boolean addDeniedMember(Claim claim, UUID memberId, UUID actorId) {
        synchronized (runtime.mutationLock()) {
            if (!claim.addDeniedMember(memberId)) {
                return false;
            }
            claim.removeTrustedMember(memberId);
            claim.removeMemberSettings(memberId);
            runtime.databaseManager().update(
                "DELETE FROM claim_members WHERE claim_id = ? AND player_uuid = ?",
                statement -> {
                    statement.setInt(1, claim.id());
                    statement.setString(2, memberId.toString());
                }
            );
            runtime.databaseManager().update(
                "DELETE FROM claim_member_permissions WHERE claim_id = ? AND player_uuid = ?",
                statement -> {
                    statement.setInt(1, claim.id());
                    statement.setString(2, memberId.toString());
                }
            );
            runtime.databaseManager().update(
                runtime.databaseManager().insertIgnoreSql("claim_blacklist", "claim_id, player_uuid", "?, ?"),
                statement -> {
                    statement.setInt(1, claim.id());
                    statement.setString(2, memberId.toString());
                }
            );
            publishClaimSync(ClaimSyncEventType.CLAIM_UPDATED, claim.id());
            recordInteractionActivity(claim, actorId);
            return true;
        }
    }

    public boolean removeDeniedMember(Claim claim, UUID memberId, UUID actorId) {
        synchronized (runtime.mutationLock()) {
            if (!claim.removeDeniedMember(memberId)) {
                return false;
            }
            runtime.databaseManager().update(
                "DELETE FROM claim_blacklist WHERE claim_id = ? AND player_uuid = ?",
                statement -> {
                    statement.setInt(1, claim.id());
                    statement.setString(2, memberId.toString());
                }
            );
            publishClaimSync(ClaimSyncEventType.CLAIM_UPDATED, claim.id());
            recordInteractionActivity(claim, actorId);
            return true;
        }
    }

    public ClaimMemberSettings memberSettings(Claim claim, UUID memberId) {
        ClaimMemberSettings settings = claim.memberSettings(memberId);
        return settings == null ? defaultsService.createMemberSettings(claim) : settings;
    }

    public boolean updateMemberPermission(Claim claim, UUID memberId, ClaimPermission permission, boolean allowed) {
        synchronized (runtime.mutationLock()) {
            if (!claim.isTrusted(memberId)) {
                return false;
            }
            ClaimMemberSettings settings = claim.memberSettings(memberId);
            if (settings == null) {
                settings = defaultsService.createMemberSettings(claim);
                claim.setMemberSettings(memberId, settings);
            }
            settings.setPermission(permission, allowed);
            persistenceRepository.saveMemberSettings(claim.id(), memberId, settings);
            publishClaimSync(ClaimSyncEventType.CLAIM_UPDATED, claim.id());
            return true;
        }
    }

    public boolean transferClaim(Claim claim, UUID newOwner, String newOwnerName) {
        if (claim == null || newOwner == null || claim.systemManaged()) {
            return false;
        }
        synchronized (runtime.mutationLock()) {
            Claim targetClaim = runtime.claims().get(claim.id());
            if (targetClaim == null) {
                return false;
            }
            UUID previousOwner = targetClaim.owner();
            if (previousOwner.equals(newOwner)) {
                return false;
            }
            String targetOwnerName = newOwnerName == null || newOwnerName.isBlank() ? newOwner.toString() : newOwnerName;
            boolean transferred = runtime.databaseManager().transaction(() -> {
                int updated = runtime.databaseManager().update(
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
                persistenceRepository.clearClaimRelations(targetClaim.id());
                cancelSaleListing(targetClaim.id());
                return true;
            });
            if (!transferred) {
                if (runtime.databaseManager().isMySql()) {
                    lookupService.refreshClaimFromDatabase(targetClaim.id());
                }
                return false;
            }
            targetClaim.setOwner(newOwner, targetOwnerName);
            targetClaim.clearTrustedMembers();
            targetClaim.clearDeniedMembers();
            targetClaim.clearMemberSettings();
            publishClaimSync(ClaimSyncEventType.CLAIM_OWNER_CHANGED, targetClaim.id());
            recordInteractionActivity(targetClaim, newOwner);
            return true;
        }
    }

    public void cancelSaleListing(int claimId) {
        runtime.databaseManager().update(
            "DELETE FROM claim_sale_listings WHERE claim_id = ?",
            statement -> statement.setInt(1, claimId)
        );
    }

    public void removeClaim(Claim claim) {
        synchronized (runtime.mutationLock()) {
            runtime.claims().remove(claim.id());
            lookupService.rebuildClaimChunkIndex();
            cancelSaleListing(claim.id());
            runtime.databaseManager().update(
                "DELETE FROM claims WHERE id = ?",
                statement -> statement.setInt(1, claim.id())
            );

            World world = lookupService.isLocalClaim(claim) ? runtime.plugin().getServer().getWorld(claim.world()) : null;
            if (world != null) {
                Location coreLocation = new Location(world, claim.centerX(), claim.centerY(), claim.centerZ());
                if (coreLocation.getBlock().getType() == runtime.plugin().settings().coreMaterial()) {
                    coreLocation.getBlock().setType(Material.AIR, false);
                }
            }
            publishClaimSync(ClaimSyncEventType.CLAIM_DELETED, claim.id());
            untrackClaim(claim.id());
        }
    }

    public void save() {
        if (runtime.databaseManager().isMySql()) {
            return;
        }
        synchronized (runtime.mutationLock()) {
            persistenceRepository.saveAllClaims(runtime.claims().values(), lookupService::displayServerId);
        }
    }

    private void trackNewClaim(Claim claim) {
        ClaimCleanupService cleanupService = runtime.claimCleanupService();
        if (cleanupService != null) {
            cleanupService.trackNewClaim(claim);
        }
    }

    private void untrackClaim(int claimId) {
        ClaimCleanupService cleanupService = runtime.claimCleanupService();
        if (cleanupService != null) {
            cleanupService.removeTracking(claimId);
        }
    }

    private void recordBuildActivity(Claim claim, UUID actorId) {
        ClaimCleanupService cleanupService = runtime.claimCleanupService();
        if (cleanupService != null) {
            cleanupService.recordBuildActivity(claim, actorId);
        }
    }

    private void recordInteractionActivity(Claim claim, UUID actorId) {
        ClaimCleanupService cleanupService = runtime.claimCleanupService();
        if (cleanupService != null) {
            cleanupService.recordInteractionActivity(claim, actorId);
        }
    }

    private void publishClaimSync(ClaimSyncEventType eventType, int claimId) {
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
