package com.coreclaim.storage;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

public final class DatabaseExecutor {

    @FunctionalInterface
    public interface ConnectionProvider {
        Connection get();
    }

    private final Object lock;
    private final Runnable ensureConnection;
    private final ConnectionProvider connectionProvider;

    public DatabaseExecutor(Object lock, Runnable ensureConnection, ConnectionProvider connectionProvider) {
        this.lock = lock;
        this.ensureConnection = ensureConnection;
        this.connectionProvider = connectionProvider;
    }

    public <T> T query(String sql, DatabaseManager.StatementBinder binder, DatabaseManager.ResultExtractor<T> extractor) {
        synchronized (lock) {
            ensureConnection.run();
            try (PreparedStatement statement = connectionProvider.get().prepareStatement(sql)) {
                binder.bind(statement);
                try (ResultSet resultSet = statement.executeQuery()) {
                    return extractor.extract(resultSet);
                }
            } catch (SQLException exception) {
                throw new IllegalStateException("Failed to execute database query: " + sql, exception);
            }
        }
    }

    public int update(String sql, DatabaseManager.StatementBinder binder) {
        synchronized (lock) {
            ensureConnection.run();
            try (PreparedStatement statement = connectionProvider.get().prepareStatement(sql)) {
                binder.bind(statement);
                return statement.executeUpdate();
            } catch (SQLException exception) {
                throw new IllegalStateException("Failed to execute database update: " + sql, exception);
            }
        }
    }

    public long insertAndReturnKey(String sql, DatabaseManager.StatementBinder binder) {
        synchronized (lock) {
            ensureConnection.run();
            try (PreparedStatement statement = connectionProvider.get().prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
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

    public <T> T transaction(DatabaseManager.TransactionCallback<T> callback) {
        synchronized (lock) {
            ensureConnection.run();
            Connection connection = connectionProvider.get();
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
}
