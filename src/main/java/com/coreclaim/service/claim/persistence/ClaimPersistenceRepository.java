package com.coreclaim.service.claim.persistence;

import com.coreclaim.model.Claim;
import com.coreclaim.model.ClaimFlag;
import com.coreclaim.model.ClaimFlagState;
import com.coreclaim.model.ClaimMemberSettings;
import com.coreclaim.model.ClaimPermission;
import com.coreclaim.service.ClaimService;
import com.coreclaim.service.claim.ClaimRuntime;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public final class ClaimPersistenceRepository {

    private static final String MISSING_SERVER_ID_CONDITION = "server_id IS NULL OR TRIM(server_id) = ''";

    private final ClaimRuntime runtime;

    public ClaimPersistenceRepository(ClaimRuntime runtime) {
        this.runtime = runtime;
    }

    public void backfillMissingServerIds(String currentServerId) {
        if (runtime.databaseManager().isMySql()) {
            return;
        }
        runtime.databaseManager().update(
            "UPDATE claims SET server_id = ? WHERE " + MISSING_SERVER_ID_CONDITION,
            statement -> statement.setString(1, currentServerId)
        );
    }

    public Optional<Claim> loadClaimFromDatabase(int id) {
        Optional<Claim> loadedClaim = runtime.databaseManager().query(
            """
            SELECT id, owner_uuid, owner_name, name, core_visible, world, server_id, center_x, center_y, center_z,
                   min_y, max_y, full_height, radius, east, south, west, north, enter_message, leave_message,
                   allow_place, allow_break, allow_interact, allow_container, allow_redstone, allow_explosion, allow_bucket, allow_teleport, allow_flight,
                   system_managed, deny_all, tp_x, tp_y, tp_z, tp_yaw, tp_pitch, last_expanded_at, created_at
            FROM claims
            WHERE id = ?
            """,
            statement -> statement.setInt(1, id),
            resultSet -> resultSet.next() ? Optional.of(ClaimRowMapper.claimFromResultSet(resultSet)) : Optional.empty()
        );
        loadedClaim.ifPresent(this::loadClaimRelationsFromDatabase);
        return loadedClaim;
    }

    public Map<Integer, Claim> loadClaimsFromDatabase() {
        Map<Integer, Claim> loadedClaims = new HashMap<>();
        runtime.databaseManager().query(
            """
            SELECT id, owner_uuid, owner_name, name, core_visible, world, server_id, center_x, center_y, center_z,
                   min_y, max_y, full_height, radius, east, south, west, north, enter_message, leave_message,
                   allow_place, allow_break, allow_interact, allow_container, allow_redstone, allow_explosion, allow_bucket, allow_teleport, allow_flight,
                   system_managed, deny_all, tp_x, tp_y, tp_z, tp_yaw, tp_pitch, last_expanded_at, created_at
            FROM claims
            """,
            statement -> {
            },
            resultSet -> {
                while (resultSet.next()) {
                    Claim claim = ClaimRowMapper.claimFromResultSet(resultSet);
                    loadedClaims.put(claim.id(), claim);
                }
                return null;
            }
        );

        runtime.databaseManager().query(
            "SELECT claim_id, player_uuid FROM claim_members",
            statement -> {
            },
            resultSet -> {
                while (resultSet.next()) {
                    Claim claim = loadedClaims.get(resultSet.getInt("claim_id"));
                    if (claim != null) {
                        claim.addTrustedMember(UUID.fromString(resultSet.getString("player_uuid")));
                    }
                }
                return null;
            }
        );
        runtime.databaseManager().query(
            "SELECT claim_id, player_uuid FROM claim_blacklist",
            statement -> {
            },
            resultSet -> {
                while (resultSet.next()) {
                    Claim claim = loadedClaims.get(resultSet.getInt("claim_id"));
                    if (claim != null) {
                        claim.addDeniedMember(UUID.fromString(resultSet.getString("player_uuid")));
                    }
                }
                return null;
            }
        );
        runtime.databaseManager().query(
            "SELECT claim_id, flag_key, state FROM claim_flags",
            statement -> {
            },
            resultSet -> {
                while (resultSet.next()) {
                    Claim claim = loadedClaims.get(resultSet.getInt("claim_id"));
                    if (claim == null) {
                        continue;
                    }
                    ClaimFlag flag = ClaimFlag.fromKey(resultSet.getString("flag_key"));
                    if (flag == null) {
                        continue;
                    }
                    claim.setFlagState(flag, ClaimFlagState.fromDatabase(resultSet.getInt("state")));
                }
                return null;
            }
        );
        runtime.databaseManager().query(
            """
            SELECT claim_id, player_uuid, allow_place, allow_break, allow_interact, allow_container, allow_redstone, allow_explosion, allow_bucket, allow_teleport, allow_flight
            FROM claim_member_permissions
            """,
            statement -> {
            },
            resultSet -> {
                while (resultSet.next()) {
                    Claim claim = loadedClaims.get(resultSet.getInt("claim_id"));
                    if (claim == null) {
                        continue;
                    }
                    UUID playerId = UUID.fromString(resultSet.getString("player_uuid"));
                    claim.setMemberSettings(playerId, ClaimRowMapper.memberSettingsFromResultSet(resultSet));
                }
                return null;
            }
        );
        return loadedClaims;
    }

    public void loadClaimRelationsFromDatabase(Claim claim) {
        runtime.databaseManager().query(
            "SELECT player_uuid FROM claim_members WHERE claim_id = ?",
            statement -> statement.setInt(1, claim.id()),
            resultSet -> {
                while (resultSet.next()) {
                    claim.addTrustedMember(UUID.fromString(resultSet.getString("player_uuid")));
                }
                return null;
            }
        );
        runtime.databaseManager().query(
            "SELECT player_uuid FROM claim_blacklist WHERE claim_id = ?",
            statement -> statement.setInt(1, claim.id()),
            resultSet -> {
                while (resultSet.next()) {
                    claim.addDeniedMember(UUID.fromString(resultSet.getString("player_uuid")));
                }
                return null;
            }
        );
        runtime.databaseManager().query(
            "SELECT flag_key, state FROM claim_flags WHERE claim_id = ?",
            statement -> statement.setInt(1, claim.id()),
            resultSet -> {
                while (resultSet.next()) {
                    ClaimFlag flag = ClaimFlag.fromKey(resultSet.getString("flag_key"));
                    if (flag != null) {
                        claim.setFlagState(flag, ClaimFlagState.fromDatabase(resultSet.getInt("state")));
                    }
                }
                return null;
            }
        );
        runtime.databaseManager().query(
            """
            SELECT player_uuid, allow_place, allow_break, allow_interact, allow_container, allow_redstone, allow_explosion, allow_bucket, allow_teleport, allow_flight
            FROM claim_member_permissions
            WHERE claim_id = ?
            """,
            statement -> statement.setInt(1, claim.id()),
            resultSet -> {
                while (resultSet.next()) {
                    UUID playerId = UUID.fromString(resultSet.getString("player_uuid"));
                    claim.setMemberSettings(playerId, ClaimRowMapper.memberSettingsFromResultSet(resultSet));
                }
                return null;
            }
        );
    }

    public void saveAllClaims(Iterable<Claim> claims, java.util.function.Function<Claim, String> displayServerIdProvider) {
        for (Claim claim : claims) {
            runtime.databaseManager().update(
                runtime.databaseManager().claimUpsertSql(),
                statement -> {
                    statement.setInt(1, claim.id());
                    statement.setString(2, claim.owner().toString());
                    statement.setString(3, claim.ownerName());
                    statement.setString(4, claim.name());
                    statement.setInt(5, claim.coreVisible() ? 1 : 0);
                    statement.setString(6, claim.world());
                    statement.setString(7, displayServerIdProvider.apply(claim));
                    statement.setInt(8, claim.centerX());
                    statement.setInt(9, claim.centerY());
                    statement.setInt(10, claim.centerZ());
                    statement.setInt(11, claim.minY());
                    statement.setInt(12, claim.maxY());
                    statement.setInt(13, claim.fullHeight() ? 1 : 0);
                    statement.setInt(14, claim.displayRadius());
                    statement.setInt(15, claim.east());
                    statement.setInt(16, claim.south());
                    statement.setInt(17, claim.west());
                    statement.setInt(18, claim.north());
                    statement.setString(19, claim.enterMessage());
                    statement.setString(20, claim.leaveMessage());
                    statement.setInt(21, claim.permission(ClaimPermission.PLACE) ? 1 : 0);
                    statement.setInt(22, claim.permission(ClaimPermission.BREAK) ? 1 : 0);
                    statement.setInt(23, claim.permission(ClaimPermission.INTERACT) ? 1 : 0);
                    statement.setInt(24, claim.permission(ClaimPermission.CONTAINER) ? 1 : 0);
                    statement.setInt(25, claim.permission(ClaimPermission.REDSTONE) ? 1 : 0);
                    statement.setInt(26, claim.permission(ClaimPermission.EXPLOSION) ? 1 : 0);
                    statement.setInt(27, claim.permission(ClaimPermission.BUCKET) ? 1 : 0);
                    statement.setInt(28, claim.permission(ClaimPermission.TELEPORT) ? 1 : 0);
                    statement.setInt(29, claim.permission(ClaimPermission.FLIGHT) ? 1 : 0);
                    statement.setInt(30, claim.systemManaged() ? 1 : 0);
                    statement.setInt(31, claim.denyAll() ? 1 : 0);
                    if (claim.hasTeleportPoint()) {
                        statement.setDouble(32, claim.teleportX());
                        statement.setDouble(33, claim.teleportY());
                        statement.setDouble(34, claim.teleportZ());
                        statement.setDouble(35, claim.teleportYaw());
                        statement.setDouble(36, claim.teleportPitch());
                    } else {
                        statement.setNull(32, java.sql.Types.DOUBLE);
                        statement.setNull(33, java.sql.Types.DOUBLE);
                        statement.setNull(34, java.sql.Types.DOUBLE);
                        statement.setNull(35, java.sql.Types.DOUBLE);
                        statement.setNull(36, java.sql.Types.DOUBLE);
                    }
                    statement.setLong(37, claim.lastExpandedAt());
                    statement.setLong(38, claim.createdAt());
                }
            );
            for (Map.Entry<UUID, ClaimMemberSettings> entry : claim.memberSettings().entrySet()) {
                saveMemberSettings(claim.id(), entry.getKey(), entry.getValue());
            }
        }
    }

    public void saveFlagState(int claimId, ClaimFlag flag, ClaimFlagState state) {
        runtime.databaseManager().update(
            runtime.databaseManager().insertIgnoreSql("claim_flags", "claim_id, flag_key, state", "?, ?, ?"),
            statement -> {
                statement.setInt(1, claimId);
                statement.setString(2, flag.key());
                statement.setInt(3, state.databaseValue());
            }
        );
        runtime.databaseManager().update(
            "UPDATE claim_flags SET state = ? WHERE claim_id = ? AND flag_key = ?",
            statement -> {
                statement.setInt(1, state.databaseValue());
                statement.setInt(2, claimId);
                statement.setString(3, flag.key());
            }
        );
    }

    public void deleteFlagState(int claimId, ClaimFlag flag) {
        runtime.databaseManager().update(
            "DELETE FROM claim_flags WHERE claim_id = ? AND flag_key = ?",
            statement -> {
                statement.setInt(1, claimId);
                statement.setString(2, flag.key());
            }
        );
    }

    public void updatePermissionDefaults(Claim claim) {
        runtime.databaseManager().update(
            """
            UPDATE claims SET
                allow_place = ?,
                allow_break = ?,
                allow_interact = ?,
                allow_container = ?,
                allow_redstone = ?,
                allow_explosion = ?,
                allow_bucket = ?,
                allow_teleport = ?,
                allow_flight = ?,
                deny_all = ?
            WHERE id = ?
            """,
            statement -> {
                statement.setInt(1, claim.permission(ClaimPermission.PLACE) ? 1 : 0);
                statement.setInt(2, claim.permission(ClaimPermission.BREAK) ? 1 : 0);
                statement.setInt(3, claim.permission(ClaimPermission.INTERACT) ? 1 : 0);
                statement.setInt(4, claim.permission(ClaimPermission.CONTAINER) ? 1 : 0);
                statement.setInt(5, claim.permission(ClaimPermission.REDSTONE) ? 1 : 0);
                statement.setInt(6, claim.permission(ClaimPermission.EXPLOSION) ? 1 : 0);
                statement.setInt(7, claim.permission(ClaimPermission.BUCKET) ? 1 : 0);
                statement.setInt(8, claim.permission(ClaimPermission.TELEPORT) ? 1 : 0);
                statement.setInt(9, claim.permission(ClaimPermission.FLIGHT) ? 1 : 0);
                statement.setInt(10, 0);
                statement.setInt(11, claim.id());
            }
        );
    }

    public void saveMemberSettings(int claimId, UUID memberId, ClaimMemberSettings settings) {
        runtime.databaseManager().update(
            runtime.databaseManager().memberSettingsUpsertSql(),
            statement -> {
                statement.setInt(1, claimId);
                statement.setString(2, memberId.toString());
                statement.setInt(3, settings.permission(ClaimPermission.PLACE) ? 1 : 0);
                statement.setInt(4, settings.permission(ClaimPermission.BREAK) ? 1 : 0);
                statement.setInt(5, settings.permission(ClaimPermission.INTERACT) ? 1 : 0);
                statement.setInt(6, settings.permission(ClaimPermission.CONTAINER) ? 1 : 0);
                statement.setInt(7, settings.permission(ClaimPermission.REDSTONE) ? 1 : 0);
                statement.setInt(8, settings.permission(ClaimPermission.EXPLOSION) ? 1 : 0);
                statement.setInt(9, settings.permission(ClaimPermission.BUCKET) ? 1 : 0);
                statement.setInt(10, settings.permission(ClaimPermission.TELEPORT) ? 1 : 0);
                statement.setInt(11, settings.permission(ClaimPermission.FLIGHT) ? 1 : 0);
            }
        );
    }

    public void clearClaimRelations(int claimId) {
        runtime.databaseManager().update(
            "DELETE FROM claim_members WHERE claim_id = ?",
            statement -> statement.setInt(1, claimId)
        );
        runtime.databaseManager().update(
            "DELETE FROM claim_blacklist WHERE claim_id = ?",
            statement -> statement.setInt(1, claimId)
        );
        runtime.databaseManager().update(
            "DELETE FROM claim_member_permissions WHERE claim_id = ?",
            statement -> statement.setInt(1, claimId)
        );
    }

    public Claim snapshotClaim(Claim claim) {
        return ClaimRowMapper.snapshotClaim(claim);
    }
}
