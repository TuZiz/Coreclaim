package com.coreclaim.storage;

import com.coreclaim.CoreClaimPlugin;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

final class SqliteMigrationService {

    private static final String MIGRATION_COMPLETED_KEY = "sqlite_migration_completed";
    private static final DateTimeFormatter BACKUP_TIMESTAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");
    private static final TableCopy[] MIGRATION_TABLES = {
        new TableCopy("profiles", new String[] {
            "uuid", "name", "activity_points", "online_minutes", "starter_core_granted", "starter_core_reclaimed", "auto_show_borders"
        }),
        new TableCopy("claims", new String[] {
            "id", "owner_uuid", "owner_name", "name", "core_visible", "world", "center_x", "center_y", "center_z",
            "min_y", "max_y", "full_height", "radius", "east", "south", "west", "north", "enter_message", "leave_message",
            "allow_place", "allow_break", "allow_interact", "allow_container", "allow_redstone", "allow_explosion",
            "allow_bucket", "allow_teleport", "allow_flight", "last_expanded_at", "created_at"
        }),
        new TableCopy("claim_members", new String[] {"claim_id", "player_uuid"}),
        new TableCopy("claim_blacklist", new String[] {"claim_id", "player_uuid"}),
        new TableCopy("claim_member_permissions", new String[] {
            "claim_id", "player_uuid", "allow_place", "allow_break", "allow_interact", "allow_container",
            "allow_redstone", "allow_explosion", "allow_bucket", "allow_teleport", "allow_flight"
        }),
        new TableCopy("claim_flags", new String[] {"claim_id", "flag_key", "state"}),
        new TableCopy("profile_global_members", new String[] {"owner_uuid", "member_uuid"}),
        new TableCopy("claim_sale_listings", new String[] {
            "claim_id", "seller_uuid", "seller_name", "price", "created_at"
        }, true)
    };

    private final CoreClaimPlugin plugin;
    private final DatabaseManager databaseManager;

    SqliteMigrationService(CoreClaimPlugin plugin, DatabaseManager databaseManager) {
        this.plugin = plugin;
        this.databaseManager = databaseManager;
    }

    void runIfConfigured(Connection targetConnection) {
        if (!databaseManager.isMySql() || !plugin.getConfig().getBoolean("database.migration.enabled", false)) {
            return;
        }
        if ("true".equalsIgnoreCase(databaseManager.getMeta(MIGRATION_COMPLETED_KEY))) {
            plugin.getLogger().info("SQLite migration already completed for this MySQL database.");
            return;
        }

        try {
            long existingRows = countCoreRows(targetConnection);
            if (existingRows > 0) {
                throw new IllegalStateException("MySQL target already contains CoreClaim data and has no migration completion marker.");
            }

            File sourceFile = databaseManager.resolveDataFile(plugin.getConfig().getString(
                "database.migration.source-sqlite-file",
                plugin.getConfig().getString(
                    "database.sqlite.file",
                    plugin.getConfig().getString("database.file", "coreclaim.db")
                )
            ));
            if (!sourceFile.isFile()) {
                throw new IllegalStateException("SQLite migration source not found: " + sourceFile.getAbsolutePath());
            }

            File backupFile = backupSqliteSource(sourceFile);
            migrateFromSqliteBackup(backupFile, targetConnection);
            plugin.getLogger().info("SQLite data migrated to MySQL from backup " + backupFile.getName() + ".");
        } catch (SQLException | IOException exception) {
            throw new IllegalStateException("Failed to migrate SQLite data to MySQL.", exception);
        }
    }

    private void migrateFromSqliteBackup(File backupFile, Connection targetConnection) throws SQLException {
        try (Connection sourceConnection = DriverManager.getConnection("jdbc:sqlite:" + backupFile.getAbsolutePath())) {
            try (Statement statement = sourceConnection.createStatement()) {
                statement.execute("PRAGMA foreign_keys = ON");
            }

            boolean previousAutoCommit = targetConnection.getAutoCommit();
            targetConnection.setAutoCommit(false);
            try {
                copyTables(sourceConnection, targetConnection);
                verifyCopiedRows(sourceConnection, targetConnection);
                databaseManager.setMeta(MIGRATION_COMPLETED_KEY, "true");
                targetConnection.commit();
            } catch (SQLException | RuntimeException exception) {
                targetConnection.rollback();
                throw exception;
            } finally {
                targetConnection.setAutoCommit(previousAutoCommit);
            }
        }
    }

    private void copyTables(Connection sourceConnection, Connection targetConnection) throws SQLException {
        for (TableCopy table : MIGRATION_TABLES) {
            if (table.optional() && !tableExists(sourceConnection, table.name())) {
                continue;
            }
            copyTable(sourceConnection, targetConnection, table);
        }
    }

    private void verifyCopiedRows(Connection sourceConnection, Connection targetConnection) throws SQLException {
        for (TableCopy table : MIGRATION_TABLES) {
            if (table.optional() && !tableExists(sourceConnection, table.name())) {
                continue;
            }
            long sourceRows = countRows(sourceConnection, table.name());
            long targetRows = countRows(targetConnection, table.name());
            if (sourceRows != targetRows) {
                throw new SQLException("Migrated row count mismatch for " + table.name() + ": source=" + sourceRows + ", target=" + targetRows);
            }
        }
    }

    private void copyTable(Connection sourceConnection, Connection targetConnection, TableCopy table) throws SQLException {
        String columns = String.join(", ", table.columns());
        String insertSql = "INSERT INTO " + table.name() + " (" + columns + ") VALUES (" + placeholders(table.columns().length) + ")";
        try (
            Statement selectStatement = sourceConnection.createStatement();
            ResultSet resultSet = selectStatement.executeQuery("SELECT " + columns + " FROM " + table.name());
            PreparedStatement insertStatement = targetConnection.prepareStatement(insertSql)
        ) {
            int batchSize = 0;
            while (resultSet.next()) {
                for (int index = 0; index < table.columns().length; index++) {
                    insertStatement.setObject(index + 1, resultSet.getObject(table.columns()[index]));
                }
                insertStatement.addBatch();
                batchSize++;
                if (batchSize >= 500) {
                    insertStatement.executeBatch();
                    batchSize = 0;
                }
            }
            if (batchSize > 0) {
                insertStatement.executeBatch();
            }
        }
    }

    private File backupSqliteSource(File sourceFile) throws IOException {
        File backupDirectory = new File(plugin.getDataFolder(), "migration-backups");
        if (!backupDirectory.exists() && !backupDirectory.mkdirs()) {
            throw new IOException("Unable to create migration backup directory: " + backupDirectory.getAbsolutePath());
        }
        File backupFile = new File(backupDirectory, "coreclaim-" + BACKUP_TIMESTAMP.format(LocalDateTime.now()) + ".db");
        Files.copy(sourceFile.toPath(), backupFile.toPath());
        return backupFile;
    }

    private long countCoreRows(Connection targetConnection) throws SQLException {
        long total = 0L;
        for (TableCopy table : MIGRATION_TABLES) {
            total += countRows(targetConnection, table.name());
        }
        return total;
    }

    private long countRows(Connection targetConnection, String table) throws SQLException {
        try (Statement statement = targetConnection.createStatement();
             ResultSet resultSet = statement.executeQuery("SELECT COUNT(*) FROM " + table)) {
            return resultSet.next() ? resultSet.getLong(1) : 0L;
        }
    }

    private boolean tableExists(Connection targetConnection, String table) throws SQLException {
        DatabaseMetaData metaData = targetConnection.getMetaData();
        try (ResultSet resultSet = metaData.getTables(null, null, table, null)) {
            if (resultSet.next()) {
                return true;
            }
        }
        try (ResultSet resultSet = metaData.getTables(null, null, table.toUpperCase(Locale.ROOT), null)) {
            if (resultSet.next()) {
                return true;
            }
        }
        try (Statement statement = targetConnection.createStatement()) {
            statement.executeQuery("SELECT 1 FROM " + table + " LIMIT 1").close();
            return true;
        } catch (SQLException ignored) {
            return false;
        }
    }

    private String placeholders(int count) {
        return "?, ".repeat(Math.max(0, count - 1)) + "?";
    }

    private record TableCopy(String name, String[] columns, boolean optional) {
        private TableCopy(String name, String[] columns) {
            this(name, columns, false);
        }
    }
}
