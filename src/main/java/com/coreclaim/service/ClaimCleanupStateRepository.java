package com.coreclaim.service;

import static com.coreclaim.service.ClaimCleanupStateSupport.normalizeReasonKey;

import com.coreclaim.model.ClaimCleanupState;
import com.coreclaim.storage.DatabaseManager;
import java.util.HashMap;
import java.util.Map;

final class ClaimCleanupStateRepository {

    private final DatabaseManager databaseManager;

    ClaimCleanupStateRepository(DatabaseManager databaseManager) {
        this.databaseManager = databaseManager;
    }

    Map<Integer, ClaimCleanupState> loadStates() {
        return databaseManager.query(
            """
            SELECT claim_id, has_build_evidence, has_interaction_evidence, grace_marked_at,
                   delete_after_at, skip_cleanup, legacy_unknown, last_reason
            FROM claim_cleanup_state
            """,
            statement -> {
            },
            resultSet -> {
                Map<Integer, ClaimCleanupState> result = new HashMap<>();
                while (resultSet.next()) {
                    ClaimCleanupState state = new ClaimCleanupState(resultSet.getInt("claim_id"));
                    state.setHasBuildEvidence(resultSet.getInt("has_build_evidence") == 1);
                    state.setHasInteractionEvidence(resultSet.getInt("has_interaction_evidence") == 1);
                    state.setGraceMarkedAt(resultSet.getLong("grace_marked_at"));
                    state.setDeleteAfterAt(resultSet.getLong("delete_after_at"));
                    state.setSkipCleanup(resultSet.getInt("skip_cleanup") == 1);
                    state.setLegacyUnknown(resultSet.getInt("legacy_unknown") == 1);
                    state.setLastReason(normalizeReasonKey(resultSet.getString("last_reason")));
                    result.put(state.getClaimId(), state);
                }
                return result;
            }
        );
    }

    void persistState(ClaimCleanupState state) {
        if (state == null) {
            return;
        }
        databaseManager.update(
            databaseManager.insertIgnoreSql(
                "claim_cleanup_state",
                "claim_id, has_build_evidence, has_interaction_evidence, grace_marked_at, delete_after_at, skip_cleanup, legacy_unknown, last_reason",
                "?, ?, ?, ?, ?, ?, ?, ?"
            ),
            statement -> {
                statement.setInt(1, state.getClaimId());
                statement.setInt(2, state.hasBuildEvidence() ? 1 : 0);
                statement.setInt(3, state.hasInteractionEvidence() ? 1 : 0);
                statement.setLong(4, state.getGraceMarkedAt());
                statement.setLong(5, state.getDeleteAfterAt());
                statement.setInt(6, state.isSkipCleanup() ? 1 : 0);
                statement.setInt(7, state.isLegacyUnknown() ? 1 : 0);
                statement.setString(8, normalizeReasonKey(state.getLastReason()));
            }
        );
        databaseManager.update(
            """
            UPDATE claim_cleanup_state
            SET has_build_evidence = ?, has_interaction_evidence = ?, grace_marked_at = ?, delete_after_at = ?,
                skip_cleanup = ?, legacy_unknown = ?, last_reason = ?
            WHERE claim_id = ?
            """,
            statement -> {
                statement.setInt(1, state.hasBuildEvidence() ? 1 : 0);
                statement.setInt(2, state.hasInteractionEvidence() ? 1 : 0);
                statement.setLong(3, state.getGraceMarkedAt());
                statement.setLong(4, state.getDeleteAfterAt());
                statement.setInt(5, state.isSkipCleanup() ? 1 : 0);
                statement.setInt(6, state.isLegacyUnknown() ? 1 : 0);
                statement.setString(7, normalizeReasonKey(state.getLastReason()));
                statement.setInt(8, state.getClaimId());
            }
        );
    }
}
