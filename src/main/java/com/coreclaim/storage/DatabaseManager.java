package com.coreclaim.storage;

import com.coreclaim.CoreClaimPlugin;
import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

public final class DatabaseManager {

    private final CoreClaimPlugin plugin;
    private final File databaseFile;
    private final Object lock = new Object();
    private Connection connection;

    public DatabaseManager(CoreClaimPlugin plugin) {
        this.plugin = plugin;
        this.databaseFile = new File(plugin.getDataFolder(), plugin.getConfig().getString("database.file", "coreclaim.db"));
        connect();
        initializeSchema();
    }

    public File databaseFile() {
        return databaseFile;
    }

    public void close() {
        synchronized (lock) {
            if (connection == null) {
                return;
            }
            try {
                connection.close();
            } catch (SQLException exception) {
                plugin.getLogger().warning("关闭数据库连接失败: " + exception.getMessage());
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
                throw new IllegalStateException("执行数据库查询失败: " + sql, exception);
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
                throw new IllegalStateException("执行数据库更新失败: " + sql, exception);
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
                    throw new SQLException("未返回生成的主键。");
                }
            } catch (SQLException exception) {
                throw new IllegalStateException("执行数据库插入失败: " + sql, exception);
            }
        }
    }

    private void connect() {
        synchronized (lock) {
            ensureDataFolder();
            try {
                Class.forName("org.sqlite.JDBC");
                this.connection = DriverManager.getConnection("jdbc:sqlite:" + databaseFile.getAbsolutePath());
                try (Statement statement = connection.createStatement()) {
                    statement.execute("PRAGMA foreign_keys = ON");
                }
            } catch (SQLException | ClassNotFoundException exception) {
                throw new IllegalStateException("初始化 SQLite 数据库失败", exception);
            }
        }
    }

    private void initializeSchema() {
        update(
            """
            CREATE TABLE IF NOT EXISTS profiles (
                uuid TEXT PRIMARY KEY,
                name TEXT NOT NULL,
                activity_points INTEGER NOT NULL,
                online_minutes INTEGER NOT NULL,
                starter_core_granted INTEGER NOT NULL
            )
            """,
            statement -> {
            }
        );
        update(
            """
            CREATE TABLE IF NOT EXISTS claims (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                owner_uuid TEXT NOT NULL,
                owner_name TEXT NOT NULL,
                name TEXT NOT NULL DEFAULT '',
                core_visible INTEGER NOT NULL DEFAULT 1,
                world TEXT NOT NULL,
                center_x INTEGER NOT NULL,
                center_y INTEGER NOT NULL,
                center_z INTEGER NOT NULL,
                radius INTEGER NOT NULL,
                east INTEGER NOT NULL DEFAULT 0,
                south INTEGER NOT NULL DEFAULT 0,
                west INTEGER NOT NULL DEFAULT 0,
                north INTEGER NOT NULL DEFAULT 0,
                enter_message TEXT NOT NULL DEFAULT '',
                leave_message TEXT NOT NULL DEFAULT '',
                allow_place INTEGER NOT NULL DEFAULT 1,
                allow_break INTEGER NOT NULL DEFAULT 1,
                allow_interact INTEGER NOT NULL DEFAULT 1,
                allow_bucket INTEGER NOT NULL DEFAULT 1,
                allow_teleport INTEGER NOT NULL DEFAULT 1,
                last_expanded_at INTEGER NOT NULL DEFAULT 0,
                created_at INTEGER NOT NULL
            )
            """,
            statement -> {
            }
        );
        ensureColumn("claims", "name", "TEXT NOT NULL DEFAULT ''");
        ensureColumn("claims", "core_visible", "INTEGER NOT NULL DEFAULT 1");
        ensureColumn("claims", "east", "INTEGER NOT NULL DEFAULT 0");
        ensureColumn("claims", "south", "INTEGER NOT NULL DEFAULT 0");
        ensureColumn("claims", "west", "INTEGER NOT NULL DEFAULT 0");
        ensureColumn("claims", "north", "INTEGER NOT NULL DEFAULT 0");
        ensureColumn("claims", "enter_message", "TEXT NOT NULL DEFAULT ''");
        ensureColumn("claims", "leave_message", "TEXT NOT NULL DEFAULT ''");
        ensureColumn("claims", "allow_place", "INTEGER NOT NULL DEFAULT 1");
        ensureColumn("claims", "allow_break", "INTEGER NOT NULL DEFAULT 1");
        ensureColumn("claims", "allow_interact", "INTEGER NOT NULL DEFAULT 1");
        ensureColumn("claims", "allow_bucket", "INTEGER NOT NULL DEFAULT 1");
        ensureColumn("claims", "allow_teleport", "INTEGER NOT NULL DEFAULT 1");
        ensureColumn("claims", "last_expanded_at", "INTEGER NOT NULL DEFAULT 0");
        update(
            """
            UPDATE claims
            SET east = CASE WHEN east <= 0 THEN radius ELSE east END,
                south = CASE WHEN south <= 0 THEN radius ELSE south END,
                west = CASE WHEN west <= 0 THEN radius ELSE west END,
                north = CASE WHEN north <= 0 THEN radius ELSE north END
            """,
            statement -> {
            }
        );
        update(
            """
            CREATE TABLE IF NOT EXISTS claim_members (
                claim_id INTEGER NOT NULL,
                player_uuid TEXT NOT NULL,
                PRIMARY KEY (claim_id, player_uuid),
                FOREIGN KEY (claim_id) REFERENCES claims(id) ON DELETE CASCADE
            )
            """,
            statement -> {
            }
        );
        update(
            """
            CREATE TABLE IF NOT EXISTS claim_member_permissions (
                claim_id INTEGER NOT NULL,
                player_uuid TEXT NOT NULL,
                allow_place INTEGER NOT NULL DEFAULT 0,
                allow_break INTEGER NOT NULL DEFAULT 0,
                allow_interact INTEGER NOT NULL DEFAULT 0,
                allow_bucket INTEGER NOT NULL DEFAULT 0,
                allow_teleport INTEGER NOT NULL DEFAULT 0,
                PRIMARY KEY (claim_id, player_uuid),
                FOREIGN KEY (claim_id) REFERENCES claims(id) ON DELETE CASCADE
            )
            """,
            statement -> {
            }
        );
        update(
            """
            CREATE TABLE IF NOT EXISTS profile_global_members (
                owner_uuid TEXT NOT NULL,
                member_uuid TEXT NOT NULL,
                PRIMARY KEY (owner_uuid, member_uuid)
            )
            """,
            statement -> {
            }
        );
    }

    private void ensureColumn(String table, String column, String definition) {
        boolean exists = query(
            "PRAGMA table_info(" + table + ")",
            statement -> {
            },
            resultSet -> {
                while (resultSet.next()) {
                    if (column.equalsIgnoreCase(resultSet.getString("name"))) {
                        return true;
                    }
                }
                return false;
            }
        );
        if (!exists) {
            update("ALTER TABLE " + table + " ADD COLUMN " + column + " " + definition, statement -> {
            });
        }
    }

    private void ensureConnection() {
        try {
            if (connection == null || connection.isClosed()) {
                connect();
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("检查数据库连接状态失败", exception);
        }
    }

    private void ensureDataFolder() {
        File dataFolder = plugin.getDataFolder();
        if (!dataFolder.exists() && !dataFolder.mkdirs()) {
            throw new IllegalStateException("无法创建插件数据目录: " + dataFolder.getAbsolutePath());
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
}
