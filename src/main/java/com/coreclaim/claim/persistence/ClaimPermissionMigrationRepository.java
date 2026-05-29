package com.coreclaim.claim.persistence;

import com.coreclaim.claim.ClaimRuntime;
import com.coreclaim.model.ClaimFlag;
import com.coreclaim.model.ClaimFlagState;
import com.coreclaim.model.PermissionMergeSupport;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class ClaimPermissionMigrationRepository {

    private final ClaimRuntime runtime;

    ClaimPermissionMigrationRepository(ClaimRuntime runtime) {
        this.runtime = runtime;
    }

    void migrateMergedPermissionData() {
        runtime.databaseManager().transaction(() -> {
            normalizeClaimPermissionRows();
            normalizeMemberPermissionRows();
            deleteOrphanMemberPermissionRows();
            deleteLegacyFlagRows();
            return null;
        });
    }

    private void normalizeClaimPermissionRows() {
        Map<Integer, PermissionRow> rows = runtime.databaseManager().query(
            "SELECT id, allow_interact, allow_redstone FROM claims",
            statement -> {
            },
            resultSet -> {
                Map<Integer, PermissionRow> values = new HashMap<>();
                while (resultSet.next()) {
                    values.put(resultSet.getInt("id"), new PermissionRow(
                        resultSet.getInt("allow_interact") != 0,
                        resultSet.getInt("allow_redstone") != 0
                    ));
                }
                return values;
            }
        );
        Map<Integer, EnumMap<LegacyPermissionGroup, List<ClaimFlagState>>> legacyStates = legacyFlagStates();
        for (Map.Entry<Integer, PermissionRow> entry : rows.entrySet()) {
            int claimId = entry.getKey();
            PermissionRow row = entry.getValue();
            EnumMap<LegacyPermissionGroup, List<ClaimFlagState>> states = legacyStates.get(claimId);
            boolean interact = PermissionMergeSupport.mergeLegacyFlagStates(
                row.allowInteract(),
                states == null ? List.of() : states.getOrDefault(LegacyPermissionGroup.INTERACT, List.of())
            );
            boolean redstone = PermissionMergeSupport.mergeLegacyFlagStates(
                row.allowRedstone(),
                states == null ? List.of() : states.getOrDefault(LegacyPermissionGroup.REDSTONE, List.of())
            );
            if (interact == row.allowInteract() && redstone == row.allowRedstone()) {
                continue;
            }
            runtime.databaseManager().update(
                "UPDATE claims SET allow_interact = ?, allow_redstone = ? WHERE id = ?",
                statement -> {
                    statement.setInt(1, interact ? 1 : 0);
                    statement.setInt(2, redstone ? 1 : 0);
                    statement.setInt(3, claimId);
                }
            );
        }
    }

    private void normalizeMemberPermissionRows() {
        // allow_container is now reused as the independent utility-interact permission.
        // Do not mirror it into allow_interact during startup or the split cannot persist.
    }

    private void deleteOrphanMemberPermissionRows() {
        int removedRows = runtime.databaseManager().update(
            """
            DELETE FROM claim_member_permissions
            WHERE NOT EXISTS (
                SELECT 1
                FROM claim_members
                WHERE claim_members.claim_id = claim_member_permissions.claim_id
                  AND claim_members.player_uuid = claim_member_permissions.player_uuid
            )
            """,
            statement -> {
            }
        );
        if (removedRows > 0 && runtime.plugin() != null) {
            runtime.plugin().getLogger().info("Removed " + removedRows + " orphan claim member permission rows.");
        }
    }

    private Map<Integer, EnumMap<LegacyPermissionGroup, List<ClaimFlagState>>> legacyFlagStates() {
        String placeholders = "?, ".repeat(ClaimFlag.legacyInteractKeys().size() + ClaimFlag.legacyRedstoneKeys().size() - 1) + "?";
        return runtime.databaseManager().query(
            "SELECT claim_id, flag_key, state FROM claim_flags WHERE flag_key IN (" + placeholders + ")",
            statement -> {
                int index = 1;
                for (String key : ClaimFlag.legacyInteractKeys()) {
                    statement.setString(index++, key);
                }
                for (String key : ClaimFlag.legacyRedstoneKeys()) {
                    statement.setString(index++, key);
                }
            },
            resultSet -> {
                Map<Integer, EnumMap<LegacyPermissionGroup, List<ClaimFlagState>>> states = new HashMap<>();
                while (resultSet.next()) {
                    String flagKey = resultSet.getString("flag_key");
                    LegacyPermissionGroup group = ClaimFlag.isLegacyInteractKey(flagKey)
                        ? LegacyPermissionGroup.INTERACT
                        : LegacyPermissionGroup.REDSTONE;
                    states
                        .computeIfAbsent(resultSet.getInt("claim_id"), ignored -> new EnumMap<>(LegacyPermissionGroup.class))
                        .computeIfAbsent(group, ignored -> new ArrayList<>())
                        .add(ClaimFlagState.fromDatabase(resultSet.getInt("state")));
                }
                return states;
            }
        );
    }

    private void deleteLegacyFlagRows() {
        Set<String> interactKeys = ClaimFlag.legacyInteractKeys();
        Set<String> redstoneKeys = ClaimFlag.legacyRedstoneKeys();
        String placeholders = "?, ".repeat(interactKeys.size() + redstoneKeys.size() - 1) + "?";
        runtime.databaseManager().update(
            "DELETE FROM claim_flags WHERE flag_key IN (" + placeholders + ")",
            statement -> {
                int index = 1;
                for (String key : interactKeys) {
                    statement.setString(index++, key);
                }
                for (String key : redstoneKeys) {
                    statement.setString(index++, key);
                }
            }
        );
    }

    private enum LegacyPermissionGroup {
        INTERACT,
        REDSTONE
    }

    private record PermissionRow(boolean allowInteract, boolean allowRedstone) {
    }
}
