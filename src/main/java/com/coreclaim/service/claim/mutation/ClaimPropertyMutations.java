package com.coreclaim.service.claim.mutation;

import com.coreclaim.model.Claim;
import com.coreclaim.model.ClaimFlag;
import com.coreclaim.model.ClaimFlagState;
import com.coreclaim.model.ClaimPermission;
import com.coreclaim.service.ClaimService;
import com.coreclaim.service.ClaimSyncEventType;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.bukkit.Location;

final class ClaimPropertyMutations {

    private final ClaimMutationContext context;

    ClaimPropertyMutations(ClaimMutationContext context) {
        this.context = context;
    }

    Optional<Claim> updateClaimServerId(int id, String serverId) {
        String sanitizedServerId = serverId == null ? "" : serverId.trim();
        if (sanitizedServerId.isEmpty()) {
            return Optional.empty();
        }
        synchronized (context.runtime.mutationLock()) {
            int updated = context.runtime.databaseManager().update(
                "UPDATE claims SET server_id = ? WHERE id = ?",
                statement -> {
                    statement.setString(1, sanitizedServerId);
                    statement.setInt(2, id);
                }
            );
            if (updated <= 0) {
                return Optional.empty();
            }
            ClaimService.ClaimRefreshResult refreshed = context.lookupService.reloadClaim(id);
            context.publishClaimSync(ClaimSyncEventType.CLAIM_SERVER_CHANGED, id);
            return refreshed.currentClaim() == null ? Optional.empty() : context.lookupService.findClaimById(id);
        }
    }

    void updateFlagState(Claim claim, ClaimFlag flag, ClaimFlagState state, UUID actorId) {
        if (claim == null || flag == null) {
            return;
        }
        ClaimFlagState nextState = state == null ? ClaimFlagState.UNSET : state;
        claim.setFlagState(flag, nextState);
        synchronized (context.runtime.mutationLock()) {
            if (nextState == ClaimFlagState.UNSET) {
                context.persistenceRepository.deleteFlagState(claim.id(), flag);
            } else {
                context.persistenceRepository.saveFlagState(claim.id(), flag, nextState);
            }
        }
        context.publishClaimSync(ClaimSyncEventType.CLAIM_UPDATED, claim.id());
        context.recordInteractionActivity(claim, actorId);
    }

    void updateBounds(Claim claim, int east, int south, int west, int north, UUID actorId) {
        synchronized (context.runtime.mutationLock()) {
            claim.setBounds(east, south, west, north);
            claim.setLastExpandedAt(Instant.now().getEpochSecond());
            context.runtime.databaseManager().update(
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
            context.lookupService.rebuildClaimChunkIndex();
            context.publishClaimSync(ClaimSyncEventType.CLAIM_UPDATED, claim.id());
        }
        context.recordBuildActivity(claim, actorId);
    }

    void updateCoreVisibility(Claim claim, boolean coreVisible, UUID actorId) {
        updateBooleanClaimColumn(claim, "core_visible", coreVisible, changed -> claim.setCoreVisible(coreVisible));
        context.recordInteractionActivity(claim, actorId);
    }

    void renameClaim(Claim claim, String name, UUID actorId) {
        synchronized (context.runtime.mutationLock()) {
            String sanitizedName = context.lookupService.validateAvailableClaimName(name, claim.id());
            claim.setName(sanitizedName);
            context.runtime.databaseManager().update(
                "UPDATE claims SET name = ? WHERE id = ?",
                statement -> {
                    statement.setString(1, sanitizedName);
                    statement.setInt(2, claim.id());
                }
            );
            context.publishClaimSync(ClaimSyncEventType.CLAIM_UPDATED, claim.id());
        }
        context.recordInteractionActivity(claim, actorId);
    }

    void updateEnterMessage(Claim claim, String message, UUID actorId) {
        updateMessage(claim, "enter_message", message, claim::setEnterMessage, claim::enterMessage);
        context.recordInteractionActivity(claim, actorId);
    }

    void updateLeaveMessage(Claim claim, String message, UUID actorId) {
        updateMessage(claim, "leave_message", message, claim::setLeaveMessage, claim::leaveMessage);
        context.recordInteractionActivity(claim, actorId);
    }

    void updateDenyAll(Claim claim, boolean denyAll, UUID actorId) {
        updateBooleanClaimColumn(claim, "deny_all", denyAll, changed -> claim.setDenyAll(denyAll));
        context.recordInteractionActivity(claim, actorId);
    }

    void updateTeleportPoint(Claim claim, Location location, UUID actorId) {
        synchronized (context.runtime.mutationLock()) {
            if (location == null) {
                claim.clearTeleportPoint();
            } else {
                claim.setTeleportPoint(location.getX(), location.getY(), location.getZ(), location.getYaw(), location.getPitch());
            }
            context.runtime.databaseManager().update(
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
            context.publishClaimSync(ClaimSyncEventType.CLAIM_UPDATED, claim.id());
        }
        context.recordInteractionActivity(claim, actorId);
    }

    void updatePermission(Claim claim, ClaimPermission permission, boolean allowed, UUID actorId) {
        synchronized (context.runtime.mutationLock()) {
            claim.setPermission(permission, allowed);
            if (permission == ClaimPermission.INTERACT) {
                context.runtime.databaseManager().update(
                    "UPDATE claims SET allow_interact = ?, allow_container = ? WHERE id = ?",
                    statement -> {
                        statement.setInt(1, allowed ? 1 : 0);
                        statement.setInt(2, allowed ? 1 : 0);
                        statement.setInt(3, claim.id());
                    }
                );
                context.publishClaimSync(ClaimSyncEventType.CLAIM_UPDATED, claim.id());
                context.recordInteractionActivity(claim, actorId);
                return;
            }
            context.runtime.databaseManager().update(
                "UPDATE claims SET " + permissionColumn(permission) + " = ? WHERE id = ?",
                statement -> {
                    statement.setInt(1, allowed ? 1 : 0);
                    statement.setInt(2, claim.id());
                }
            );
            context.publishClaimSync(ClaimSyncEventType.CLAIM_UPDATED, claim.id());
        }
        context.recordInteractionActivity(claim, actorId);
    }

    private void updateBooleanClaimColumn(Claim claim, String column, boolean value, java.util.function.Consumer<Boolean> mutator) {
        synchronized (context.runtime.mutationLock()) {
            mutator.accept(value);
            context.runtime.databaseManager().update(
                "UPDATE claims SET " + column + " = ? WHERE id = ?",
                statement -> {
                    statement.setInt(1, value ? 1 : 0);
                    statement.setInt(2, claim.id());
                }
            );
            context.publishClaimSync(ClaimSyncEventType.CLAIM_UPDATED, claim.id());
        }
    }

    private void updateMessage(Claim claim, String column, String message, java.util.function.Consumer<String> mutator, java.util.function.Supplier<String> valueSupplier) {
        synchronized (context.runtime.mutationLock()) {
            mutator.accept(message);
            context.runtime.databaseManager().update(
                "UPDATE claims SET " + column + " = ? WHERE id = ?",
                statement -> {
                    statement.setString(1, valueSupplier.get());
                    statement.setInt(2, claim.id());
                }
            );
            context.publishClaimSync(ClaimSyncEventType.CLAIM_UPDATED, claim.id());
        }
    }

    private String permissionColumn(ClaimPermission permission) {
        return switch (permission) {
            case PLACE -> "allow_place";
            case BREAK -> "allow_break";
            case INTERACT -> "allow_interact";
            case MOB_INTERACT -> "allow_mob_interact";
            case REDSTONE -> "allow_redstone";
            case EXPLOSION -> "allow_explosion";
            case BUCKET -> "allow_bucket";
            case TELEPORT -> "allow_teleport";
            case FLIGHT -> "allow_flight";
        };
    }
}
