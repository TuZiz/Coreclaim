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

public final class DatabaseManager {

    private static final int SCHEMA_VERSION = 1;
    private static final String SCHEMA_VERSION_KEY = "schema_version";
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
        new TableCopy("profile_global_members", new String[] {"owner_uuid", "member_uuid"}),
        new TableCopy("claim_sale_listings", new String[] {
            "claim_id", "seller_uuid", "seller_name", "price", "created_at"
        }, true)
    };

    private final CoreClaimPlugin plugin;
    private final DatabaseType databaseType;
    private final File databaseFile;
    private final Object lock = new Object();
    private Connection connection;

    public DatabaseManager(CoreClaimPlugin plugin) {
        this.plugin = plugin;
        this.databaseType = resolveDatabaseType(plugin.getConfig().getString("database.type", "sqlite"));
        this.databaseFile = resolveDataFile(plugin.getConfig().getString(
            "database.sqlite.file",
            plugin.getConfig().getString("database.file", "coreclaim.db")
        ));
        connect();
        initializeSchema();
        runSqliteMigrationIfConfigured();
    }

    public File databaseFile() {
        return databaseFile;
    }

    public boolean isMySql() {
        return databaseType == DatabaseType.MYSQL;
    }

    public String displayName() {
        if (databaseType == DatabaseType.MYSQL) {
            return "mysql://" + plugin.getConfig().getString("database.mysql.host", "localhost")
                + ":" + plugin.getConfig().getInt("database.mysql.port", 3306)
                + "/" + plugin.getConfig().getString("database.mysql.database", "coreclaim");
        }
        return databaseFile.getName();
    }

    public String insertIgnoreSql(String table, String columns, String values) {
        if (databaseType == DatabaseType.MYSQL) {
            return "INSERT IGNORE INTO " + table + " (" + columns + ") VALUES (" + values + ")";
        }
        return "INSERT OR IGNORE INTO " + table + " (" + columns + ") VALUES (" + values + ")";
    }

    public String profileUpsertSql() {
        if (databaseType == DatabaseType.MYSQL) {
            return """
                INSERT INTO profiles (
                    uuid,
                    name,
                    activity_points,
                    online_minutes,
                    starter_core_granted,
                    starter_core_reclaimed,
                    auto_show_borders
                )
                VALUES (?, ?, ?, ?, ?, ?, ?)
                ON DUPLICATE KEY UPDATE
                    name = VALUES(name),
                    activity_points = VALUES(activity_points),
                    online_minutes = VALUES(online_minutes),
                    starter_core_granted = VALUES(starter_core_granted),
                    starter_core_reclaimed = VALUES(starter_core_reclaimed),
                    auto_show_borders = VALUES(auto_show_borders)
                """;
        }
        return """
            INSERT INTO profiles (
                uuid,
                name,
                activity_points,
                online_minutes,
                starter_core_granted,
                starter_core_reclaimed,
                auto_show_borders
            )
            VALUES (?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT(uuid) DO UPDATE SET
                name = excluded.name,
                activity_points = excluded.activity_points,
                online_minutes = excluded.online_minutes,
                starter_core_granted = excluded.starter_core_granted,
                starter_core_reclaimed = excluded.starter_core_reclaimed,
                auto_show_borders = excluded.auto_show_borders
            """;
    }

    public String claimUpsertSql() {
        if (databaseType == DatabaseType.MYSQL) {
            return """
                INSERT INTO claims (
                    id, owner_uuid, owner_name, name, core_visible, world, center_x, center_y, center_z,
                    min_y, max_y, full_height, radius, east, south, west, north, enter_message, leave_message,
                    allow_place, allow_break, allow_interact, allow_container, allow_redstone, allow_explosion,
                    allow_bucket, allow_teleport, allow_flight, last_expanded_at, created_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON DUPLICATE KEY UPDATE
                    owner_uuid = VALUES(owner_uuid),
                    owner_name = VALUES(owner_name),
                    name = VALUES(name),
                    core_visible = VALUES(core_visible),
                    world = VALUES(world),
                    center_x = VALUES(center_x),
                    center_y = VALUES(center_y),
                    center_z = VALUES(center_z),
                    min_y = VALUES(min_y),
                    max_y = VALUES(max_y),
                    full_height = VALUES(full_height),
                    radius = VALUES(radius),
                    east = VALUES(east),
                    south = VALUES(south),
                    west = VALUES(west),
                    north = VALUES(north),
                    enter_message = VALUES(enter_message),
                    leave_message = VALUES(leave_message),
                    allow_place = VALUES(allow_place),
                    allow_break = VALUES(allow_break),
                    allow_interact = VALUES(allow_interact),
                    allow_container = VALUES(allow_container),
                    allow_redstone = VALUES(allow_redstone),
                    allow_explosion = VALUES(allow_explosion),
                    allow_bucket = VALUES(allow_bucket),
                    allow_teleport = VALUES(allow_teleport),
                    allow_flight = VALUES(allow_flight),
                    last_expanded_at = VALUES(last_expanded_at),
                    created_at = VALUES(created_at)
                """;
        }
        return """
            INSERT INTO claims (
                id, owner_uuid, owner_name, name, core_visible, world, center_x, center_y, center_z,
                min_y, max_y, full_height, radius, east, south, west, north, enter_message, leave_message,
                allow_place, allow_break, allow_interact, allow_container, allow_redstone, allow_explosion, allow_bucket, allow_teleport, allow_flight, last_expanded_at, created_at
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT(id) DO UPDATE SET
                owner_uuid = excluded.owner_uuid,
                owner_name = excluded.owner_name,
                name = excluded.name,
                core_visible = excluded.core_visible,
                world = excluded.world,
                center_x = excluded.center_x,
                center_y = excluded.center_y,
                center_z = excluded.center_z,
                min_y = excluded.min_y,
                max_y = excluded.max_y,
                full_height = excluded.full_height,
                radius = excluded.radius,
                east = excluded.east,
                south = excluded.south,
                west = excluded.west,
                north = excluded.north,
                enter_message = excluded.enter_message,
                leave_message = excluded.leave_message,
                allow_place = excluded.allow_place,
                allow_break = excluded.allow_break,
                allow_interact = excluded.allow_interact,
                allow_container = excluded.allow_container,
                allow_redstone = excluded.allow_redstone,
                allow_explosion = excluded.allow_explosion,
                allow_bucket = excluded.allow_bucket,
                allow_teleport = excluded.allow_teleport,
                allow_flight = excluded.allow_flight,
                last_expanded_at = excluded.last_expanded_at,
                created_at = excluded.created_at
            """;
    }

    public String memberSettingsUpsertSql() {
        if (databaseType == DatabaseType.MYSQL) {
            return """
                INSERT INTO claim_member_permissions (
                    claim_id, player_uuid, allow_place, allow_break, allow_interact, allow_container,
                    allow_redstone, allow_explosion, allow_bucket, allow_teleport, allow_flight
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON DUPLICATE KEY UPDATE
                    allow_place = VALUES(allow_place),
                    allow_break = VALUES(allow_break),
                    allow_interact = VALUES(allow_interact),
                    allow_container = VALUES(allow_container),
                    allow_redstone = VALUES(allow_redstone),
                    allow_explosion = VALUES(allow_explosion),
                    allow_bucket = VALUES(allow_bucket),
                    allow_teleport = VALUES(allow_teleport),
                    allow_flight = VALUES(allow_flight)
                """;
        }
        return """
            INSERT INTO claim_member_permissions (
                claim_id, player_uuid, allow_place, allow_break, allow_interact, allow_container, allow_redstone, allow_explosion, allow_bucket, allow_teleport, allow_flight
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT(claim_id, player_uuid) DO UPDATE SET
                allow_place = excluded.allow_place,
                allow_break = excluded.allow_break,
                allow_interact = excluded.allow_interact,
                allow_container = excluded.allow_container,
                allow_redstone = excluded.allow_redstone,
                allow_explosion = excluded.allow_explosion,
                allow_bucket = excluded.allow_bucket,
                allow_teleport = excluded.allow_teleport,
                allow_flight = excluded.allow_flight
            """;
    }

    public void close() {
        synchronized (lock) {
            if (connection == null) {
                return;
            }
            try {
                connection.close();
            } catch (SQLException exception) {
                plugin.getLogger().warning("Failed to close database connection: " + exception.getMessage());
            } finally {
                connection = null;
            }
        }
    }

    public <T> T query(String sql, StatementBinder binder, ResultExtractor<T> extractor) {
        synchronized (lock) {
            ensureConnection();
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                binder.bind(statement);
                try (ResultSet resultSet = statement.executeQuery()) {
                    return extractor.extract(resultSet);
                }
            } catch (SQLException exception) {
                throw new IllegalStateException("Failed to execute database query: " + sql, exception);
            }
        }
    }

    public int update(String sql, StatementBinder binder) {
        synchronized (lock) {
            ensureConnection();
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                binder.bind(statement);
                return statement.executeUpdate();
            } catch (SQLException exception) {
                throw new IllegalStateException("Failed to execute database update: " + sql, exception);
            }
        }
    }

    public long insertAndReturnKey(String sql, StatementBinder binder) {
        synchronized (lock) {
            ensureConnection();
            try (PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
                binder.bind(statement);
                statement.executeUpdate();
                try (ResultSet keys = statement.getGeneratedKeys()) {
                    if (keys.next()) {
                        return keys.getLong(1);
                    }
                    throw new SQLException("No generated key was returned.");
                }
            } catch (SQLException exception) {
                throw new IllegalStateException("Failed to execute database insert: " + sql, exception);
            }
        }
    }

    public <T> T transaction(TransactionCallback<T> callback) {
        synchronized (lock) {
            ensureConnection();
            try {
                boolean previousAutoCommit = connection.getAutoCommit();
                if (!previousAutoCommit) {
                    return callback.execute();
                }
                connection.setAutoCommit(false);
                try {
                    T result = callback.execute();
                    connection.commit();
                    return result;
                } catch (SQLException | RuntimeException exception) {
                    connection.rollback();
                    throw exception;
                } finally {
                    connection.setAutoCommit(previousAutoCommit);
                }
            } catch (SQLException exception) {
                throw new IllegalStateException("Failed to execute database transaction.", exception);
            }
        }
    }

    private void connect() {
        synchronized (lock) {
            ensureDataFolder();
            try {
                if (databaseType == DatabaseType.MYSQL) {
                    Class.forName("com.mysql.cj.jdbc.Driver");
                    this.connection = DriverManager.getConnection(
                        mysqlJdbcUrl(),
                        plugin.getConfig().getString("database.mysql.username", "root"),
                        plugin.getConfig().getString("database.mysql.password", "")
                    );
                    try (Statement statement = connection.createStatement()) {
                        statement.execute("SET NAMES utf8mb4");
                    }
                    return;
                }

                Class.forName("org.sqlite.JDBC");
                this.connection = DriverManager.getConnection("jdbc:sqlite:" + databaseFile.getAbsolutePath());
                try (Statement statement = connection.createStatement()) {
                    statement.execute("PRAGMA foreign_keys = ON");
                }
            } catch (SQLException | ClassNotFoundException exception) {
                throw new IllegalStateException("Failed to initialize " + databaseType.displayName + " database.", exception);
            }
        }
    }

    private void initializeSchema() {
        update(
            """
            CREATE TABLE IF NOT EXISTS profiles (
                uuid %s PRIMARY KEY,
                name %s NOT NULL,
                activity_points %s NOT NULL,
                online_minutes %s NOT NULL,
                starter_core_granted %s NOT NULL,
                starter_core_reclaimed %s NOT NULL DEFAULT 0,
                auto_show_borders %s NOT NULL DEFAULT 0
            )%s
            """.formatted(uuidType(), shortTextType(), integerType(), integerType(), booleanType(), booleanType(), booleanType(), tableOptions()),
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
                core_visible %s NOT NULL DEFAULT 1,
                world %s NOT NULL,
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
                allow_place %s NOT NULL DEFAULT 1,
                allow_break %s NOT NULL DEFAULT 1,
                allow_interact %s NOT NULL DEFAULT 1,
                allow_container %s NOT NULL DEFAULT 1,
                allow_redstone %s NOT NULL DEFAULT 1,
                allow_explosion %s NOT NULL DEFAULT 0,
                allow_bucket %s NOT NULL DEFAULT 1,
                allow_teleport %s NOT NULL DEFAULT 1,
                allow_flight %s NOT NULL DEFAULT 1,
                last_expanded_at %s NOT NULL DEFAULT 0,
                created_at %s NOT NULL
            )%s
            """.formatted(
                autoIncrementPrimaryKey(), uuidType(), shortTextType(), shortTextType(), booleanType(), worldType(),
                integerType(), integerType(), integerType(), integerType(), integerType(), booleanType(), integerType(),
                integerType(), integerType(), integerType(), integerType(), messageType(), messageType(), booleanType(),
                booleanType(), booleanType(), booleanType(), booleanType(), booleanType(), booleanType(), booleanType(),
                booleanType(), longType(), longType(), tableOptions()
            ),
            statement -> {
            }
        );
        ensureColumn("claims", "name", shortTextType() + " NOT NULL DEFAULT ''");
        ensureColumn("claims", "core_visible", booleanType() + " NOT NULL DEFAULT 1");
        ensureColumn("claims", "min_y", integerType() + " NOT NULL DEFAULT -64");
        ensureColumn("claims", "max_y", integerType() + " NOT NULL DEFAULT 319");
        ensureColumn("claims", "full_height", booleanType() + " NOT NULL DEFAULT 1");
        ensureColumn("claims", "east", integerType() + " NOT NULL DEFAULT 0");
        ensureColumn("claims", "south", integerType() + " NOT NULL DEFAULT 0");
        ensureColumn("claims", "west", integerType() + " NOT NULL DEFAULT 0");
        ensureColumn("claims", "north", integerType() + " NOT NULL DEFAULT 0");
        ensureColumn("claims", "enter_message", messageType());
        ensureColumn("claims", "leave_message", messageType());
        ensureColumn("claims", "allow_place", booleanType() + " NOT NULL DEFAULT 1");
        ensureColumn("claims", "allow_break", booleanType() + " NOT NULL DEFAULT 1");
        ensureColumn("claims", "allow_interact", booleanType() + " NOT NULL DEFAULT 1");
        ensureColumn("claims", "allow_container", booleanType() + " NOT NULL DEFAULT 1");
        ensureColumn("claims", "allow_redstone", booleanType() + " NOT NULL DEFAULT 1");
        ensureColumn("claims", "allow_explosion", booleanType() + " NOT NULL DEFAULT 0");
        ensureColumn("claims", "allow_bucket", booleanType() + " NOT NULL DEFAULT 1");
        ensureColumn("claims", "allow_teleport", booleanType() + " NOT NULL DEFAULT 1");
        ensureColumn("claims", "allow_flight", booleanType() + " NOT NULL DEFAULT 1");
        ensureColumn("claims", "last_expanded_at", longType() + " NOT NULL DEFAULT 0");
        update(
            """
            UPDATE claims
            SET east = CASE WHEN east <= 0 THEN radius ELSE east END,
                south = CASE WHEN south <= 0 THEN radius ELSE south END,
                west = CASE WHEN west <= 0 THEN radius ELSE west END,
                north = CASE WHEN north <= 0 THEN radius ELSE north END,
                enter_message = CASE WHEN enter_message IS NULL THEN '' ELSE enter_message END,
                leave_message = CASE WHEN leave_message IS NULL THEN '' ELSE leave_message END
            """,
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
                allow_redstone %s NOT NULL DEFAULT 0,
                allow_explosion %s NOT NULL DEFAULT 0,
                allow_bucket %s NOT NULL DEFAULT 0,
                allow_teleport %s NOT NULL DEFAULT 0,
                allow_flight %s NOT NULL DEFAULT 1,
                PRIMARY KEY (claim_id, player_uuid),
                FOREIGN KEY (claim_id) REFERENCES claims(id) ON DELETE CASCADE
            )%s
            """.formatted(
                integerType(), uuidType(), booleanType(), booleanType(), booleanType(), booleanType(), booleanType(),
                booleanType(), booleanType(), booleanType(), booleanType(), tableOptions()
            ),
            statement -> {
            }
        );
        ensureColumn("profiles", "starter_core_reclaimed", booleanType() + " NOT NULL DEFAULT 0");
        ensureColumn("profiles", "auto_show_borders", booleanType() + " NOT NULL DEFAULT 0");
        ensureColumn("claim_member_permissions", "allow_container", booleanType() + " NOT NULL DEFAULT 0");
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
            CREATE TABLE IF NOT EXISTS coreclaim_meta (
                meta_key %s PRIMARY KEY,
                meta_value %s NOT NULL
            )%s
            """.formatted(metaKeyType(), metaValueType(), tableOptions()),
            statement -> {
            }
        );
        setMeta(SCHEMA_VERSION_KEY, String.valueOf(SCHEMA_VERSION));
    }

    private void runSqliteMigrationIfConfigured() {
        if (databaseType != DatabaseType.MYSQL || !plugin.getConfig().getBoolean("database.migration.enabled", false)) {
            return;
        }
        synchronized (lock) {
            ensureConnection();
            if ("true".equalsIgnoreCase(getMeta(MIGRATION_COMPLETED_KEY))) {
                plugin.getLogger().info("SQLite migration already completed for this MySQL database.");
                return;
            }

            try {
                long existingRows = countCoreRows(connection);
                if (existingRows > 0) {
                    throw new IllegalStateException("MySQL target already contains CoreClaim data and has no migration completion marker.");
                }

                File sourceFile = resolveDataFile(plugin.getConfig().getString(
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
                migrateFromSqliteBackup(backupFile);
                plugin.getLogger().info("SQLite data migrated to MySQL from backup " + backupFile.getName() + ".");
            } catch (SQLException | IOException exception) {
                throw new IllegalStateException("Failed to migrate SQLite data to MySQL.", exception);
            }
        }
    }

    private void migrateFromSqliteBackup(File backupFile) throws SQLException {
        try (Connection sourceConnection = DriverManager.getConnection("jdbc:sqlite:" + backupFile.getAbsolutePath())) {
            try (Statement statement = sourceConnection.createStatement()) {
                statement.execute("PRAGMA foreign_keys = ON");
            }

            boolean previousAutoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try {
                for (TableCopy table : MIGRATION_TABLES) {
                    if (table.optional() && !tableExists(sourceConnection, table.name())) {
                        continue;
                    }
                    copyTable(sourceConnection, connection, table);
                }
                for (TableCopy table : MIGRATION_TABLES) {
                    if (table.optional() && !tableExists(sourceConnection, table.name())) {
                        continue;
                    }
                    long sourceRows = countRows(sourceConnection, table.name());
                    long targetRows = countRows(connection, table.name());
                    if (sourceRows != targetRows) {
                        throw new SQLException("Migrated row count mismatch for " + table.name() + ": source=" + sourceRows + ", target=" + targetRows);
                    }
                }
                setMeta(MIGRATION_COMPLETED_KEY, "true");
                connection.commit();
            } catch (SQLException | RuntimeException exception) {
                connection.rollback();
                throw exception;
            } finally {
                connection.setAutoCommit(previousAutoCommit);
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

    private String getMeta(String key) {
        return query(
            "SELECT meta_value FROM coreclaim_meta WHERE meta_key = ?",
            statement -> statement.setString(1, key),
            resultSet -> resultSet.next() ? resultSet.getString("meta_value") : null
        );
    }

    private void setMeta(String key, String value) {
        update(metaUpsertSql(), statement -> {
            statement.setString(1, key);
            statement.setString(2, value);
        });
    }

    private String metaUpsertSql() {
        if (databaseType == DatabaseType.MYSQL) {
            return """
                INSERT INTO coreclaim_meta (meta_key, meta_value)
                VALUES (?, ?)
                ON DUPLICATE KEY UPDATE meta_value = VALUES(meta_value)
                """;
        }
        return """
            INSERT INTO coreclaim_meta (meta_key, meta_value)
            VALUES (?, ?)
            ON CONFLICT(meta_key) DO UPDATE SET meta_value = excluded.meta_value
            """;
    }

    private void ensureColumn(String table, String column, String definition) {
        synchronized (lock) {
            ensureConnection();
            try {
                if (columnExists(table, column)) {
                    return;
                }
                update("ALTER TABLE " + table + " ADD COLUMN " + column + " " + definition, statement -> {
                });
            } catch (SQLException exception) {
                throw new IllegalStateException("Failed to inspect database column " + table + "." + column, exception);
            }
        }
    }

    private boolean columnExists(String table, String column) throws SQLException {
        DatabaseMetaData metaData = connection.getMetaData();
        String catalog = databaseType == DatabaseType.MYSQL ? connection.getCatalog() : null;
        try (ResultSet resultSet = metaData.getColumns(catalog, null, table, null)) {
            while (resultSet.next()) {
                if (column.equalsIgnoreCase(resultSet.getString("COLUMN_NAME"))) {
                    return true;
                }
            }
        }
        return false;
    }

    private void ensureConnection() {
        try {
            if (connection == null || connection.isClosed()) {
                connect();
                return;
            }
            if (databaseType == DatabaseType.MYSQL && !connection.isValid(2)) {
                connect();
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to check database connection state.", exception);
        }
    }

    private void ensureDataFolder() {
        File dataFolder = plugin.getDataFolder();
        if (!dataFolder.exists() && !dataFolder.mkdirs()) {
            throw new IllegalStateException("Unable to create plugin data directory: " + dataFolder.getAbsolutePath());
        }
    }

    private File resolveDataFile(String path) {
        File file = new File(path == null || path.isBlank() ? "coreclaim.db" : path);
        return file.isAbsolute() ? file : new File(plugin.getDataFolder(), file.getPath());
    }

    private String mysqlJdbcUrl() {
        String host = plugin.getConfig().getString("database.mysql.host", "localhost");
        int port = plugin.getConfig().getInt("database.mysql.port", 3306);
        String database = plugin.getConfig().getString("database.mysql.database", "coreclaim");
        boolean useSsl = plugin.getConfig().getBoolean("database.mysql.use-ssl", false);
        return "jdbc:mysql://" + host + ":" + port + "/" + database
            + "?useSSL=" + useSsl
            + "&allowPublicKeyRetrieval=true"
            + "&useUnicode=true"
            + "&characterEncoding=utf8"
            + "&serverTimezone=UTC";
    }

    private DatabaseType resolveDatabaseType(String rawType) {
        if (rawType != null) {
            String normalized = rawType.trim().toLowerCase(Locale.ROOT);
            if (normalized.equals("mysql") || normalized.equals("mariadb")) {
                return DatabaseType.MYSQL;
            }
        }
        return DatabaseType.SQLITE;
    }

    private String autoIncrementPrimaryKey() {
        return databaseType == DatabaseType.MYSQL
            ? "INT NOT NULL AUTO_INCREMENT PRIMARY KEY"
            : "INTEGER PRIMARY KEY AUTOINCREMENT";
    }

    private String uuidType() {
        return databaseType == DatabaseType.MYSQL ? "VARCHAR(36)" : "TEXT";
    }

    private String shortTextType() {
        return databaseType == DatabaseType.MYSQL ? "VARCHAR(128)" : "TEXT";
    }

    private String worldType() {
        return databaseType == DatabaseType.MYSQL ? "VARCHAR(128)" : "TEXT";
    }

    private String metaKeyType() {
        return databaseType == DatabaseType.MYSQL ? "VARCHAR(64)" : "TEXT";
    }

    private String metaValueType() {
        return databaseType == DatabaseType.MYSQL ? "VARCHAR(255)" : "TEXT";
    }

    private String messageType() {
        return "TEXT";
    }

    private String integerType() {
        return databaseType == DatabaseType.MYSQL ? "INT" : "INTEGER";
    }

    private String longType() {
        return databaseType == DatabaseType.MYSQL ? "BIGINT" : "INTEGER";
    }

    private String doubleType() {
        return databaseType == DatabaseType.MYSQL ? "DOUBLE" : "REAL";
    }

    private String booleanType() {
        return databaseType == DatabaseType.MYSQL ? "TINYINT(1)" : "INTEGER";
    }

    private String tableOptions() {
        return databaseType == DatabaseType.MYSQL ? " ENGINE=InnoDB DEFAULT CHARSET=utf8mb4" : "";
    }

    private String placeholders(int count) {
        return "?, ".repeat(Math.max(0, count - 1)) + "?";
    }

    private enum DatabaseType {
        SQLITE("SQLite"),
        MYSQL("MySQL/MariaDB");

        private final String displayName;

        DatabaseType(String displayName) {
            this.displayName = displayName;
        }
    }

    private record TableCopy(String name, String[] columns, boolean optional) {
        private TableCopy(String name, String[] columns) {
            this(name, columns, false);
        }
    }

    @FunctionalInterface
    public interface StatementBinder {
        void bind(PreparedStatement statement) throws SQLException;
    }

    @FunctionalInterface
    public interface ResultExtractor<T> {
        T extract(ResultSet resultSet) throws SQLException;
    }

    @FunctionalInterface
    public interface TransactionCallback<T> {
        T execute() throws SQLException;
    }
}
