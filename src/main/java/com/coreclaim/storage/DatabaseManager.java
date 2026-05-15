package com.coreclaim.storage;

import com.coreclaim.CoreClaimPlugin;
import com.coreclaim.storage.sql.UpsertSqlFactory;
import java.io.File;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Arrays;
import java.util.Locale;
import java.util.stream.Collectors;
import org.bukkit.configuration.file.FileConfiguration;

public final class DatabaseManager {

    static final int SCHEMA_VERSION = 10;
    static final String SCHEMA_VERSION_KEY = "schema_version";
    private final CoreClaimPlugin plugin;
    private final DatabaseType databaseType;
    private final File databaseFile;
    private final Object lock = new Object();
    private final UpsertSqlFactory upsertSqlFactory;
    private final DatabaseExecutor databaseExecutor;
    private final SqliteMigrationService sqliteMigrationService;
    private Connection connection;

    public DatabaseManager(CoreClaimPlugin plugin) {
        this.plugin = plugin;
        this.databaseType = resolveDatabaseType(plugin.getConfig().getString("database.type", "sqlite"));
        this.databaseFile = resolveDataFile(plugin.getConfig().getString(
            "database.sqlite.file",
            plugin.getConfig().getString("database.file", "coreclaim.db")
        ));
        this.upsertSqlFactory = new UpsertSqlFactory(databaseType == DatabaseType.MYSQL);
        this.databaseExecutor = new DatabaseExecutor(lock, this::ensureConnection, () -> connection);
        this.sqliteMigrationService = new SqliteMigrationService(plugin, this);
        connect();
        new DatabaseSchemaInitializer(this).initialize();
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
        return upsertSqlFactory.insertIgnoreSql(table, columns, values);
    }

    public String profileUpsertSql() {
        return upsertSqlFactory.profileUpsertSql();
    }

    public String claimUpsertSql() {
        return upsertSqlFactory.claimUpsertSql();
    }

    public String memberSettingsUpsertSql() {
        return upsertSqlFactory.memberSettingsUpsertSql();
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
        return databaseExecutor.query(sql, binder, extractor);
    }

    public int update(String sql, StatementBinder binder) {
        return databaseExecutor.update(sql, binder);
    }

    public long insertAndReturnKey(String sql, StatementBinder binder) {
        return databaseExecutor.insertAndReturnKey(sql, binder);
    }

    public <T> T transaction(TransactionCallback<T> callback) {
        return databaseExecutor.transaction(callback);
    }

    public void ensureIndex(String table, String indexName, boolean unique, String... columns) {
        synchronized (lock) {
            ensureConnection();
            try {
                if (indexExists(table, indexName)) {
                    return;
                }
                String columnList = Arrays.stream(columns)
                    .map(column -> "`" + column + "`")
                    .collect(Collectors.joining(", "));
                String uniquePrefix = unique ? "UNIQUE " : "";
                update(
                    "CREATE " + uniquePrefix + "INDEX " + indexName + " ON " + table + " (" + columnList + ")",
                    statement -> {
                    }
                );
            } catch (SQLException exception) {
                throw new IllegalStateException("Failed to inspect database index " + table + "." + indexName, exception);
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

    private void runSqliteMigrationIfConfigured() {
        if (databaseType != DatabaseType.MYSQL || !plugin.getConfig().getBoolean("database.migration.enabled", false)) {
            return;
        }
        synchronized (lock) {
            ensureConnection();
            sqliteMigrationService.runIfConfigured(connection);
        }
    }

    String getMeta(String key) {
        return query(
            "SELECT meta_value FROM coreclaim_meta WHERE meta_key = ?",
            statement -> statement.setString(1, key),
            resultSet -> resultSet.next() ? resultSet.getString("meta_value") : null
        );
    }

    void setMeta(String key, String value) {
        update(metaUpsertSql(), statement -> {
            statement.setString(1, key);
            statement.setString(2, value);
        });
    }

    private String metaUpsertSql() {
        return upsertSqlFactory.metaUpsertSql();
    }

    public void ensureColumn(String table, String column, String definition) {
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

    private boolean indexExists(String table, String indexName) throws SQLException {
        DatabaseMetaData metaData = connection.getMetaData();
        String catalog = databaseType == DatabaseType.MYSQL ? connection.getCatalog() : null;
        try (ResultSet resultSet = metaData.getIndexInfo(catalog, null, table, false, false)) {
            while (resultSet.next()) {
                if (indexName.equalsIgnoreCase(resultSet.getString("INDEX_NAME"))) {
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

    File resolveDataFile(String path) {
        File file = new File(path == null || path.isBlank() ? "coreclaim.db" : path);
        return file.isAbsolute() ? file : new File(plugin.getDataFolder(), file.getPath());
    }

    private String mysqlJdbcUrl() {
        String host = plugin.getConfig().getString("database.mysql.host", "localhost");
        int port = plugin.getConfig().getInt("database.mysql.port", 3306);
        String database = plugin.getConfig().getString("database.mysql.database", "coreclaim");
        String sslMode = mysqlSslMode(plugin.getConfig());
        boolean allowPublicKeyRetrieval = plugin.getConfig().getBoolean("database.mysql.allow-public-key-retrieval", false);
        return "jdbc:mysql://" + host + ":" + port + "/" + database
            + "?sslMode=" + sslMode
            + "&allowPublicKeyRetrieval=" + allowPublicKeyRetrieval
            + "&useUnicode=true"
            + "&characterEncoding=utf8"
            + "&serverTimezone=UTC";
    }

    static String mysqlSslMode(FileConfiguration config) {
        String sslMode = config.getString("database.mysql.ssl-mode", "");
        if (sslMode == null || sslMode.isBlank()) {
            sslMode = config.getBoolean("database.mysql.use-ssl", false) ? "REQUIRED" : "DISABLED";
        }
        return sslMode.trim().toUpperCase(Locale.ROOT).replace('-', '_');
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

    String autoIncrementPrimaryKey() {
        return databaseType == DatabaseType.MYSQL
            ? "INT NOT NULL AUTO_INCREMENT PRIMARY KEY"
            : "INTEGER PRIMARY KEY AUTOINCREMENT";
    }

    String uuidType() {
        return databaseType == DatabaseType.MYSQL ? "VARCHAR(36)" : "TEXT";
    }

    String shortTextType() {
        return databaseType == DatabaseType.MYSQL ? "VARCHAR(128)" : "TEXT";
    }

    String worldType() {
        return databaseType == DatabaseType.MYSQL ? "VARCHAR(128)" : "TEXT";
    }

    String metaKeyType() {
        return databaseType == DatabaseType.MYSQL ? "VARCHAR(64)" : "TEXT";
    }

    String metaValueType() {
        return databaseType == DatabaseType.MYSQL ? "VARCHAR(255)" : "TEXT";
    }

    String messageType() {
        return "TEXT";
    }

    String integerType() {
        return databaseType == DatabaseType.MYSQL ? "INT" : "INTEGER";
    }

    String longType() {
        return databaseType == DatabaseType.MYSQL ? "BIGINT" : "INTEGER";
    }

    String doubleType() {
        return databaseType == DatabaseType.MYSQL ? "DOUBLE" : "REAL";
    }

    String booleanType() {
        return databaseType == DatabaseType.MYSQL ? "TINYINT(1)" : "INTEGER";
    }

    String tableOptions() {
        return databaseType == DatabaseType.MYSQL ? " ENGINE=InnoDB DEFAULT CHARSET=utf8mb4" : "";
    }

    private enum DatabaseType {
        SQLITE("SQLite"),
        MYSQL("MySQL/MariaDB");

        private final String displayName;

        DatabaseType(String displayName) {
            this.displayName = displayName;
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
