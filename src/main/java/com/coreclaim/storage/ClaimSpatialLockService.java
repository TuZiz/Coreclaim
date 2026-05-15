package com.coreclaim.storage;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public final class ClaimSpatialLockService {

    private static final int CHUNK_SIZE = 16;

    private final DatabaseManager database;

    public ClaimSpatialLockService(DatabaseManager database) {
        this.database = database;
    }

    public void lockArea(String world, int minX, int maxX, int minZ, int maxZ) {
        if (!database.isMySql()) {
            return;
        }
        for (String lockKey : lockKeys(world, minX, maxX, minZ, maxZ)) {
            database.update(
                database.insertIgnoreSql("claim_spatial_lock_keys", "lock_key, touched_at", "?, ?"),
                statement -> {
                    statement.setString(1, lockKey);
                    statement.setLong(2, Instant.now().getEpochSecond());
                }
            );
            database.query(
                "SELECT lock_key FROM claim_spatial_lock_keys WHERE lock_key = ? FOR UPDATE",
                statement -> statement.setString(1, lockKey),
                resultSet -> null
            );
        }
    }

    public boolean hasOverlappingClaim(
        String world,
        int minX,
        int maxX,
        int minY,
        int maxY,
        int minZ,
        int maxZ,
        Integer ignoredId,
        boolean fullHeight
    ) {
        String ignoredFilter = ignoredId == null ? "" : " AND id <> ?";
        return database.query(
            """
            SELECT id
            FROM claims
            WHERE world = ?
              %s
              AND ? <= center_x + east
              AND ? >= center_x - west
              AND ? <= center_z + south
              AND ? >= center_z - north
              AND (full_height = 1 OR ? = 1 OR (? <= max_y AND ? >= min_y))
            LIMIT 1
            """.formatted(ignoredFilter),
            statement -> {
                int index = 1;
                statement.setString(index++, world);
                if (ignoredId != null) {
                    statement.setInt(index++, ignoredId);
                }
                statement.setInt(index++, minX);
                statement.setInt(index++, maxX);
                statement.setInt(index++, minZ);
                statement.setInt(index++, maxZ);
                statement.setInt(index++, fullHeight ? 1 : 0);
                statement.setInt(index++, minY);
                statement.setInt(index, maxY);
            },
            resultSet -> resultSet.next()
        );
    }

    public boolean hasCoreAt(String world, int centerX, int centerY, int centerZ, Integer ignoredId) {
        String ignoredFilter = ignoredId == null ? "" : " AND id <> ?";
        return database.query(
            """
            SELECT id
            FROM claims
            WHERE world = ?
              AND center_x = ?
              AND center_y = ?
              AND center_z = ?
              %s
            LIMIT 1
            """.formatted(ignoredFilter),
            statement -> {
                int index = 1;
                statement.setString(index++, world);
                statement.setInt(index++, centerX);
                statement.setInt(index++, centerY);
                statement.setInt(index++, centerZ);
                if (ignoredId != null) {
                    statement.setInt(index, ignoredId);
                }
            },
            resultSet -> resultSet.next()
        );
    }

    public static List<String> lockKeys(String world, int minX, int maxX, int minZ, int maxZ) {
        String safeWorld = world == null ? "" : world;
        int fromChunkX = Math.floorDiv(Math.min(minX, maxX), CHUNK_SIZE);
        int toChunkX = Math.floorDiv(Math.max(minX, maxX), CHUNK_SIZE);
        int fromChunkZ = Math.floorDiv(Math.min(minZ, maxZ), CHUNK_SIZE);
        int toChunkZ = Math.floorDiv(Math.max(minZ, maxZ), CHUNK_SIZE);
        List<String> keys = new ArrayList<>();
        for (int chunkX = fromChunkX; chunkX <= toChunkX; chunkX++) {
            for (int chunkZ = fromChunkZ; chunkZ <= toChunkZ; chunkZ++) {
                keys.add(safeWorld + ":" + chunkX + ":" + chunkZ);
            }
        }
        return keys;
    }
}
