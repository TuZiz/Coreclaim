package com.coreclaim.storage;

import static com.coreclaim.storage.DatabaseManager.SCHEMA_VERSION;
import static com.coreclaim.storage.DatabaseManager.SCHEMA_VERSION_KEY;

final class DatabaseSchemaInitializer {

    private static final String LEGACY_PUBLIC_PERMISSIONS_REPAIRED_KEY = "legacy_public_permissions_repaired";

    private final DatabaseManager database;

    DatabaseSchemaInitializer(DatabaseManager database) {
        this.database = database;
    }

    void initialize() {
        update(
            """
            CREATE TABLE IF NOT EXISTS profiles (
                uuid %s PRIMARY KEY,
                name %s NOT NULL,
                activity_points %s NOT NULL,
                online_minutes %s NOT NULL,
                online_seconds %s NOT NULL DEFAULT 0,
                starter_core_granted %s NOT NULL,
                starter_core_reclaimed %s NOT NULL DEFAULT 0,
                starter_core_used %s NOT NULL DEFAULT 0,
                auto_show_borders %s NOT NULL DEFAULT 0,
                last_seen_at %s NOT NULL DEFAULT 0,
                last_group_key %s NOT NULL DEFAULT '',
                cleanup_permission_exempt %s NOT NULL DEFAULT 0
            )%s
            """.formatted(
                uuidType(),
                shortTextType(),
                integerType(),
                integerType(),
                longType(),
                booleanType(),
                booleanType(),
                booleanType(),
                booleanType(),
                longType(),
                shortTextType(),
                booleanType(),
                tableOptions()
            ),
            statement -> {
            }
        );
        update(
            """
            CREATE TABLE IF NOT EXISTS claims (
                id %s,
                owner_uuid %s NOT NULL,
                owner_name %s NOT NULL,
                name %s NOT NULL DEFAULT '',
                name_key %s NOT NULL DEFAULT '',
                core_visible %s NOT NULL DEFAULT 1,
                world %s NOT NULL,
                server_id %s NOT NULL DEFAULT '',
                center_x %s NOT NULL,
                center_y %s NOT NULL,
                center_z %s NOT NULL,
                min_y %s NOT NULL DEFAULT -64,
                max_y %s NOT NULL DEFAULT 319,
                full_height %s NOT NULL DEFAULT 1,
                radius %s NOT NULL,
                east %s NOT NULL DEFAULT 0,
                south %s NOT NULL DEFAULT 0,
                west %s NOT NULL DEFAULT 0,
                north %s NOT NULL DEFAULT 0,
                enter_message %s,
                leave_message %s,
                allow_place %s NOT NULL DEFAULT 0,
                allow_break %s NOT NULL DEFAULT 0,
                allow_interact %s NOT NULL DEFAULT 0,
                allow_container %s NOT NULL DEFAULT 0,
                allow_mob_interact %s NOT NULL DEFAULT 0,
                allow_animal_spawn %s NOT NULL DEFAULT 1,
                allow_monster_spawn %s NOT NULL DEFAULT 1,
                allow_redstone %s NOT NULL DEFAULT 0,
                allow_explosion %s NOT NULL DEFAULT 0,
                allow_bucket %s NOT NULL DEFAULT 0,
                allow_teleport %s NOT NULL DEFAULT 0,
                allow_flight %s NOT NULL DEFAULT 0,
                system_managed %s NOT NULL DEFAULT 0,
                creation_type %s NOT NULL DEFAULT 'UNKNOWN_LEGACY',
                deny_all %s NOT NULL DEFAULT 0,
                tp_x %s,
                tp_y %s,
                tp_z %s,
                tp_yaw %s,
                tp_pitch %s,
                last_expanded_at %s NOT NULL DEFAULT 0,
                created_at %s NOT NULL
            )%s
            """.formatted(
                autoIncrementPrimaryKey(), uuidType(), shortTextType(), shortTextType(), shortTextType(), booleanType(), worldType(), shortTextType(),
                integerType(), integerType(), integerType(), integerType(), integerType(), booleanType(), integerType(),
                integerType(), integerType(), integerType(), integerType(), messageType(), messageType(), booleanType(),
                booleanType(), booleanType(), booleanType(), booleanType(), booleanType(), booleanType(), booleanType(), booleanType(),
                booleanType(), booleanType(), booleanType(), booleanType(), booleanType(), shortTextType(), booleanType(), doubleType(), doubleType(), doubleType(), doubleType(), doubleType(), longType(), longType(), tableOptions()
            ),
            statement -> {
            }
        );
        ensureColumn("claims", "name", shortTextType() + " NOT NULL DEFAULT ''");
        ensureColumn("claims", "name_key", shortTextType() + " NOT NULL DEFAULT ''");
        ensureColumn("claims", "core_visible", booleanType() + " NOT NULL DEFAULT 1");
        ensureColumn("claims", "server_id", shortTextType() + " NOT NULL DEFAULT ''");
        ensureColumn("claims", "min_y", integerType() + " NOT NULL DEFAULT -64");
        ensureColumn("claims", "max_y", integerType() + " NOT NULL DEFAULT 319");
        ensureColumn("claims", "full_height", booleanType() + " NOT NULL DEFAULT 1");
        ensureColumn("claims", "east", integerType() + " NOT NULL DEFAULT 0");
        ensureColumn("claims", "south", integerType() + " NOT NULL DEFAULT 0");
        ensureColumn("claims", "west", integerType() + " NOT NULL DEFAULT 0");
        ensureColumn("claims", "north", integerType() + " NOT NULL DEFAULT 0");
        ensureColumn("claims", "enter_message", messageType());
        ensureColumn("claims", "leave_message", messageType());
        ensureColumn("claims", "allow_place", booleanType() + " NOT NULL DEFAULT 0");
        ensureColumn("claims", "allow_break", booleanType() + " NOT NULL DEFAULT 0");
        ensureColumn("claims", "allow_interact", booleanType() + " NOT NULL DEFAULT 0");
        ensureColumn("claims", "allow_container", booleanType() + " NOT NULL DEFAULT 0");
        ensureColumn("claims", "allow_mob_interact", booleanType() + " NOT NULL DEFAULT 0");
        ensureColumn("claims", "allow_animal_spawn", booleanType() + " NOT NULL DEFAULT 1");
        ensureColumn("claims", "allow_monster_spawn", booleanType() + " NOT NULL DEFAULT 1");
        ensureColumn("claims", "allow_redstone", booleanType() + " NOT NULL DEFAULT 0");
        ensureColumn("claims", "allow_explosion", booleanType() + " NOT NULL DEFAULT 0");
        ensureColumn("claims", "allow_bucket", booleanType() + " NOT NULL DEFAULT 0");
        ensureColumn("claims", "allow_teleport", booleanType() + " NOT NULL DEFAULT 0");
        ensureColumn("claims", "allow_flight", booleanType() + " NOT NULL DEFAULT 0");
        ensureColumn("claims", "system_managed", booleanType() + " NOT NULL DEFAULT 0");
        ensureColumn("claims", "creation_type", shortTextType() + " NOT NULL DEFAULT 'UNKNOWN_LEGACY'");
        ensureColumn("claims", "deny_all", booleanType() + " NOT NULL DEFAULT 0");
        ensureColumn("claims", "tp_x", doubleType());
        ensureColumn("claims", "tp_y", doubleType());
        ensureColumn("claims", "tp_z", doubleType());
        ensureColumn("claims", "tp_yaw", doubleType());
        ensureColumn("claims", "tp_pitch", doubleType());
        ensureColumn("claims", "last_expanded_at", longType() + " NOT NULL DEFAULT 0");
        update(
            """
            UPDATE claims
            SET east = CASE WHEN east <= 0 THEN radius ELSE east END,
                south = CASE WHEN south <= 0 THEN radius ELSE south END,
                west = CASE WHEN west <= 0 THEN radius ELSE west END,
                north = CASE WHEN north <= 0 THEN radius ELSE north END,
                server_id = CASE WHEN server_id IS NULL THEN '' ELSE server_id END,
                system_managed = CASE WHEN system_managed IS NULL THEN 0 ELSE system_managed END,
                enter_message = CASE WHEN enter_message IS NULL THEN '' ELSE enter_message END,
                leave_message = CASE WHEN leave_message IS NULL THEN '' ELSE leave_message END
            """,
            statement -> {
            }
        );
        update(
            """
            UPDATE claims
            SET creation_type = CASE
                WHEN creation_type IS NULL OR TRIM(creation_type) = '' THEN 'UNKNOWN_LEGACY'
                ELSE creation_type
            END
            """,
            statement -> {
            }
        );
        update(
            "UPDATE claims SET name_key = LOWER(TRIM(name)) WHERE name_key IS NULL OR TRIM(name_key) = ''",
            statement -> {
            }
        );
        repairBlankClaimNameKeys();
        repairDuplicateClaimNameKeys();
        ensureIndex("claims", "idx_claims_name_key_unique", true, "name_key");
        ensureIndex("claims", "idx_claims_core_unique", true, "world", "center_x", "center_y", "center_z");
        ensureIndex("claims", "idx_claims_spatial", false, "world", "center_x", "center_z");
        update(
            """
            CREATE TABLE IF NOT EXISTS claim_spatial_lock_keys (
                lock_key %s PRIMARY KEY,
                touched_at %s NOT NULL DEFAULT 0
            )%s
            """.formatted(shortTextType(), longType(), tableOptions()),
            statement -> {
            }
        );
        update(
            """
            CREATE TABLE IF NOT EXISTS claim_members (
                claim_id %s NOT NULL,
                player_uuid %s NOT NULL,
                PRIMARY KEY (claim_id, player_uuid),
                FOREIGN KEY (claim_id) REFERENCES claims(id) ON DELETE CASCADE
            )%s
            """.formatted(integerType(), uuidType(), tableOptions()),
            statement -> {
            }
        );
        update(
            """
            CREATE TABLE IF NOT EXISTS claim_blacklist (
                claim_id %s NOT NULL,
                player_uuid %s NOT NULL,
                PRIMARY KEY (claim_id, player_uuid),
                FOREIGN KEY (claim_id) REFERENCES claims(id) ON DELETE CASCADE
            )%s
            """.formatted(integerType(), uuidType(), tableOptions()),
            statement -> {
            }
        );
        update(
            """
            CREATE TABLE IF NOT EXISTS claim_member_permissions (
                claim_id %s NOT NULL,
                player_uuid %s NOT NULL,
                allow_place %s NOT NULL DEFAULT 0,
                allow_break %s NOT NULL DEFAULT 0,
                allow_interact %s NOT NULL DEFAULT 0,
                allow_container %s NOT NULL DEFAULT 0,
                allow_mob_interact %s NOT NULL DEFAULT 0,
                allow_animal_spawn %s NOT NULL DEFAULT 1,
                allow_monster_spawn %s NOT NULL DEFAULT 1,
                allow_redstone %s NOT NULL DEFAULT 0,
                allow_explosion %s NOT NULL DEFAULT 0,
                allow_bucket %s NOT NULL DEFAULT 0,
                allow_teleport %s NOT NULL DEFAULT 0,
                allow_flight %s NOT NULL DEFAULT 1,
                PRIMARY KEY (claim_id, player_uuid),
                FOREIGN KEY (claim_id) REFERENCES claims(id) ON DELETE CASCADE
            )%s
            """.formatted(
                integerType(), uuidType(), booleanType(), booleanType(), booleanType(), booleanType(), booleanType(), booleanType(),
                booleanType(), booleanType(), booleanType(), booleanType(), booleanType(), booleanType(), tableOptions()
            ),
            statement -> {
            }
        );
        update(
            """
            CREATE TABLE IF NOT EXISTS claim_flags (
                claim_id %s NOT NULL,
                flag_key %s NOT NULL,
                state %s NOT NULL DEFAULT 0,
                PRIMARY KEY (claim_id, flag_key),
                FOREIGN KEY (claim_id) REFERENCES claims(id) ON DELETE CASCADE
            )%s
            """.formatted(integerType(), shortTextType(), integerType(), tableOptions()),
            statement -> {
            }
        );
        ensureColumn("profiles", "starter_core_reclaimed", booleanType() + " NOT NULL DEFAULT 0");
        ensureColumn("profiles", "starter_core_used", booleanType() + " NOT NULL DEFAULT 0");
        ensureColumn("profiles", "auto_show_borders", booleanType() + " NOT NULL DEFAULT 0");
        ensureColumn("profiles", "last_seen_at", longType() + " NOT NULL DEFAULT 0");
        ensureColumn("profiles", "last_group_key", shortTextType() + " NOT NULL DEFAULT ''");
        ensureColumn("profiles", "cleanup_permission_exempt", booleanType() + " NOT NULL DEFAULT 0");
        ensureColumn("profiles", "online_seconds", longType() + " NOT NULL DEFAULT 0");
        update(
            "UPDATE profiles SET online_seconds = online_minutes * 60 WHERE online_seconds <= 0 AND online_minutes > 0",
            statement -> {
            }
        );
        ensureColumn("claim_member_permissions", "allow_container", booleanType() + " NOT NULL DEFAULT 0");
        ensureColumn("claim_member_permissions", "allow_mob_interact", booleanType() + " NOT NULL DEFAULT 0");
        ensureColumn("claim_member_permissions", "allow_animal_spawn", booleanType() + " NOT NULL DEFAULT 1");
        ensureColumn("claim_member_permissions", "allow_monster_spawn", booleanType() + " NOT NULL DEFAULT 1");
        ensureColumn("claim_member_permissions", "allow_redstone", booleanType() + " NOT NULL DEFAULT 0");
        ensureColumn("claim_member_permissions", "allow_explosion", booleanType() + " NOT NULL DEFAULT 0");
        ensureColumn("claim_member_permissions", "allow_flight", booleanType() + " NOT NULL DEFAULT 1");
        update(
            """
            CREATE TABLE IF NOT EXISTS profile_global_members (
                owner_uuid %s NOT NULL,
                member_uuid %s NOT NULL,
                PRIMARY KEY (owner_uuid, member_uuid)
            )%s
            """.formatted(uuidType(), uuidType(), tableOptions()),
            statement -> {
            }
        );
        update(
            """
            CREATE TABLE IF NOT EXISTS claim_sale_listings (
                claim_id %s PRIMARY KEY,
                seller_uuid %s NOT NULL,
                seller_name %s NOT NULL,
                price %s NOT NULL,
                created_at %s NOT NULL,
                FOREIGN KEY (claim_id) REFERENCES claims(id) ON DELETE CASCADE
            )%s
            """.formatted(integerType(), uuidType(), shortTextType(), doubleType(), longType(), tableOptions()),
            statement -> {
            }
        );
        update(
            """
            CREATE TABLE IF NOT EXISTS claim_cleanup_state (
                claim_id %s PRIMARY KEY,
                has_build_evidence %s NOT NULL DEFAULT 0,
                has_interaction_evidence %s NOT NULL DEFAULT 0,
                grace_marked_at %s NOT NULL DEFAULT 0,
                delete_after_at %s NOT NULL DEFAULT 0,
                skip_cleanup %s NOT NULL DEFAULT 0,
                legacy_unknown %s NOT NULL DEFAULT 1,
                last_reason %s NOT NULL DEFAULT '',
                FOREIGN KEY (claim_id) REFERENCES claims(id) ON DELETE CASCADE
            )%s
            """.formatted(
                integerType(),
                booleanType(),
                booleanType(),
                longType(),
                longType(),
                booleanType(),
                booleanType(),
                shortTextType(),
                tableOptions()
            ),
            statement -> {
            }
        );
        update(
            """
            CREATE TABLE IF NOT EXISTS coreclaim_meta (
                meta_key %s PRIMARY KEY,
                meta_value %s NOT NULL
            )%s
            """.formatted(metaKeyType(), metaValueType(), tableOptions()),
            statement -> {
            }
        );
        repairLegacyPublicPermissionDefaults();
        ensureColumn("claim_cleanup_state", "has_build_evidence", booleanType() + " NOT NULL DEFAULT 0");
        ensureColumn("claim_cleanup_state", "has_interaction_evidence", booleanType() + " NOT NULL DEFAULT 0");
        ensureColumn("claim_cleanup_state", "grace_marked_at", longType() + " NOT NULL DEFAULT 0");
        ensureColumn("claim_cleanup_state", "delete_after_at", longType() + " NOT NULL DEFAULT 0");
        ensureColumn("claim_cleanup_state", "skip_cleanup", booleanType() + " NOT NULL DEFAULT 0");
        ensureColumn("claim_cleanup_state", "legacy_unknown", booleanType() + " NOT NULL DEFAULT 1");
        ensureColumn("claim_cleanup_state", "last_reason", shortTextType() + " NOT NULL DEFAULT ''");
        setMeta(SCHEMA_VERSION_KEY, String.valueOf(SCHEMA_VERSION));
    }

    private void repairLegacyPublicPermissionDefaults() {
        if ("true".equalsIgnoreCase(database.getMeta(LEGACY_PUBLIC_PERMISSIONS_REPAIRED_KEY))) {
            return;
        }
        update(
            """
            UPDATE claims
            SET allow_place = 0,
                allow_break = 0,
                allow_interact = 0,
                allow_container = 0,
                allow_mob_interact = 0,
                allow_redstone = 0,
                allow_explosion = 0,
                allow_bucket = 0,
                allow_teleport = 0,
                allow_flight = 0
            WHERE system_managed = 0
            """,
            statement -> {
            }
        );
        setMeta(LEGACY_PUBLIC_PERMISSIONS_REPAIRED_KEY, "true");
    }

    private void repairDuplicateClaimNameKeys() {
        java.util.List<String> duplicateKeys = database.query(
            """
            SELECT name_key
            FROM claims
            WHERE TRIM(name_key) <> ''
            GROUP BY name_key
            HAVING COUNT(*) > 1
            """,
            statement -> {
            },
            resultSet -> {
                java.util.List<String> keys = new java.util.ArrayList<>();
                while (resultSet.next()) {
                    keys.add(resultSet.getString("name_key"));
                }
                return keys;
            }
        );
        for (String duplicateKey : duplicateKeys) {
            java.util.List<Integer> ids = database.query(
                "SELECT id FROM claims WHERE name_key = ? ORDER BY id",
                statement -> statement.setString(1, duplicateKey),
                resultSet -> {
                    java.util.List<Integer> rows = new java.util.ArrayList<>();
                    while (resultSet.next()) {
                        rows.add(resultSet.getInt("id"));
                    }
                    return rows;
                }
            );
            for (int index = 1; index < ids.size(); index++) {
                int id = ids.get(index);
                database.update(
                    database.isMySql()
                        ? "UPDATE claims SET name = CONCAT(name, '-', ?), name_key = CONCAT(name_key, '-', ?) WHERE id = ?"
                        : "UPDATE claims SET name = name || '-' || ?, name_key = name_key || '-' || ? WHERE id = ?",
                    statement -> {
                        statement.setInt(1, id);
                        statement.setInt(2, id);
                        statement.setInt(3, id);
                    }
                );
            }
        }
    }

    private void repairBlankClaimNameKeys() {
        java.util.List<Integer> ids = database.query(
            "SELECT id FROM claims WHERE name_key IS NULL OR TRIM(name_key) = '' ORDER BY id",
            statement -> {
            },
            resultSet -> {
                java.util.List<Integer> rows = new java.util.ArrayList<>();
                while (resultSet.next()) {
                    rows.add(resultSet.getInt("id"));
                }
                return rows;
            }
        );
        for (int id : ids) {
            String fallbackName = "claim-" + id;
            database.update(
                "UPDATE claims SET name = ?, name_key = ? WHERE id = ?",
                statement -> {
                    statement.setString(1, fallbackName);
                    statement.setString(2, fallbackName);
                    statement.setInt(3, id);
                }
            );
        }
    }

    private int update(String sql, DatabaseManager.StatementBinder binder) {
        return database.update(sql, binder);
    }

    private void ensureColumn(String table, String column, String definition) {
        database.ensureColumn(table, column, definition);
    }

    private void ensureIndex(String table, String indexName, boolean unique, String... columns) {
        database.ensureIndex(table, indexName, unique, columns);
    }

    private void setMeta(String key, String value) {
        database.setMeta(key, value);
    }

    private String autoIncrementPrimaryKey() {
        return database.autoIncrementPrimaryKey();
    }

    private String uuidType() {
        return database.uuidType();
    }

    private String shortTextType() {
        return database.shortTextType();
    }

    private String worldType() {
        return database.worldType();
    }

    private String metaKeyType() {
        return database.metaKeyType();
    }

    private String metaValueType() {
        return database.metaValueType();
    }

    private String messageType() {
        return database.messageType();
    }

    private String integerType() {
        return database.integerType();
    }

    private String longType() {
        return database.longType();
    }

    private String doubleType() {
        return database.doubleType();
    }

    private String booleanType() {
        return database.booleanType();
    }

    private String tableOptions() {
        return database.tableOptions();
    }
}
