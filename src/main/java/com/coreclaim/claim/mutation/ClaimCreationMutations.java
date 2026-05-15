package com.coreclaim.claim.mutation;

import com.coreclaim.model.Claim;
import com.coreclaim.sync.ClaimSyncEventType;
import java.time.Instant;
import java.util.UUID;
import org.bukkit.Location;

final class ClaimCreationMutations {

    private final ClaimMutationContext context;

    ClaimCreationMutations(ClaimMutationContext context) {
        this.context = context;
    }

    Claim createClaim(UUID owner, String ownerName, String name, Location center, int initialDistance) {
        synchronized (context.runtime.mutationLock()) {
            return context.runtime.databaseManager().transaction(() -> {
                String sanitizedName = context.lookupService.validateAvailableClaimName(name, null);
                String currentServerId = context.lookupService.currentServerId();
                int minY = center.getWorld() == null ? -64 : center.getWorld().getMinHeight();
                int maxY = center.getWorld() == null ? 319 : center.getWorld().getMaxHeight() - 1;
                String world = center.getWorld().getName();
                int minX = center.getBlockX() - initialDistance;
                int maxX = center.getBlockX() + initialDistance;
                int minZ = center.getBlockZ() - initialDistance;
                int maxZ = center.getBlockZ() + initialDistance;
                assertDatabaseAreaAvailable(world, minX, maxX, minY, maxY, minZ, maxZ, null, true, center);
                long createdAt = Instant.now().getEpochSecond();
                int generatedId = insertClaim(owner, ownerName, sanitizedName, center, minY, maxY, initialDistance, initialDistance, initialDistance, initialDistance, true, false, currentServerId, createdAt);
                Claim claim = new Claim(
                    generatedId,
                    owner,
                    ownerName,
                    sanitizedName,
                    currentServerId,
                    world,
                    center.getBlockX(),
                    center.getBlockY(),
                    center.getBlockZ(),
                    minY,
                    maxY,
                    true,
                    initialDistance,
                    initialDistance,
                    initialDistance,
                    initialDistance,
                    createdAt,
                    true,
                    "",
                    "",
                    false,
                    false,
                    true,
                    true,
                    false,
                    false,
                    false,
                    true,
                    true,
                    false,
                    false,
                    true,
                    false,
                    false,
                    null,
                    null,
                    null,
                    null,
                    null,
                    0L
                );
                registerNewClaim(claim);
                return claim;
            });
        }
    }

    Claim createClaimFromBounds(
        UUID owner,
        String ownerName,
        String name,
        Location coreLocation,
        int minY,
        int maxY,
        int east,
        int south,
        int west,
        int north,
        boolean systemManaged
    ) {
        synchronized (context.runtime.mutationLock()) {
            return context.runtime.databaseManager().transaction(() -> {
                String sanitizedName = context.lookupService.validateAvailableClaimName(name, null);
                String currentServerId = context.lookupService.currentServerId();
                String world = coreLocation.getWorld().getName();
                int minX = coreLocation.getBlockX() - west;
                int maxX = coreLocation.getBlockX() + east;
                int minZ = coreLocation.getBlockZ() - north;
                int maxZ = coreLocation.getBlockZ() + south;
                assertDatabaseAreaAvailable(world, minX, maxX, minY, maxY, minZ, maxZ, null, false, coreLocation);
                long createdAt = Instant.now().getEpochSecond();
                int generatedId = insertClaim(owner, ownerName, sanitizedName, coreLocation, minY, maxY, east, south, west, north, false, systemManaged, currentServerId, createdAt);
                Claim claim = new Claim(
                    generatedId,
                    owner,
                    ownerName,
                    sanitizedName,
                    currentServerId,
                    world,
                    coreLocation.getBlockX(),
                    coreLocation.getBlockY(),
                    coreLocation.getBlockZ(),
                    minY,
                    maxY,
                    false,
                    east,
                    south,
                    west,
                    north,
                    createdAt,
                    true,
                    "",
                    "",
                    false,
                    false,
                    false,
                    false,
                    false,
                    false,
                    false,
                    false,
                    false,
                    false,
                    false,
                    true,
                    systemManaged,
                    false,
                    null,
                    null,
                    null,
                    null,
                    null,
                    0L
                );
                registerNewClaim(claim);
                return claim;
            });
        }
    }

    private int insertClaim(
        UUID owner,
        String ownerName,
        String sanitizedName,
        Location coreLocation,
        int minY,
        int maxY,
        int east,
        int south,
        int west,
        int north,
        boolean fullHeight,
        boolean systemManaged,
        String currentServerId,
        long createdAt
    ) {
        int radius = fullHeight ? east : Math.max(Math.max(east, west), Math.max(south, north));
        return (int) context.runtime.databaseManager().insertAndReturnKey(
            """
            INSERT INTO claims (
                owner_uuid, owner_name, name, name_key, core_visible, world, server_id, center_x, center_y, center_z,
                min_y, max_y, full_height, radius, east, south, west, north, enter_message, leave_message,
                allow_place, allow_break, allow_interact, allow_container, allow_mob_interact, allow_animal_spawn, allow_monster_spawn, allow_redstone, allow_explosion, allow_bucket, allow_teleport, allow_flight, system_managed, last_expanded_at, created_at
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """,
            statement -> {
                statement.setString(1, owner.toString());
                statement.setString(2, ownerName);
                statement.setString(3, sanitizedName);
                statement.setString(4, com.coreclaim.claim.query.ClaimNameNormalizer.normalize(sanitizedName));
                statement.setInt(5, 1);
                statement.setString(6, coreLocation.getWorld().getName());
                statement.setString(7, currentServerId);
                statement.setInt(8, coreLocation.getBlockX());
                statement.setInt(9, coreLocation.getBlockY());
                statement.setInt(10, coreLocation.getBlockZ());
                statement.setInt(11, minY);
                statement.setInt(12, maxY);
                statement.setInt(13, fullHeight ? 1 : 0);
                statement.setInt(14, radius);
                statement.setInt(15, east);
                statement.setInt(16, south);
                statement.setInt(17, west);
                statement.setInt(18, north);
                statement.setString(19, "");
                statement.setString(20, "");
                statement.setInt(21, 0);
                statement.setInt(22, 0);
                statement.setInt(23, 0);
                statement.setInt(24, 0);
                statement.setInt(25, 0);
                statement.setInt(26, 1);
                statement.setInt(27, 1);
                statement.setInt(28, 0);
                statement.setInt(29, 0);
                statement.setInt(30, 0);
                statement.setInt(31, 0);
                statement.setInt(32, 1);
                statement.setInt(33, systemManaged ? 1 : 0);
                statement.setLong(34, 0L);
                statement.setLong(35, createdAt);
            }
        );
    }

    private void assertDatabaseAreaAvailable(
        String world,
        int minX,
        int maxX,
        int minY,
        int maxY,
        int minZ,
        int maxZ,
        Integer ignoredId,
        boolean fullHeight,
        Location coreLocation
    ) {
        context.runtime.spatialLockService().lockArea(world, minX, maxX, minZ, maxZ);
        if (context.runtime.spatialLockService().hasOverlappingClaim(world, minX, maxX, minY, maxY, minZ, maxZ, ignoredId, fullHeight)) {
            throw new IllegalArgumentException("claim-overlap");
        }
        if (coreLocation != null && context.runtime.spatialLockService().hasCoreAt(world, coreLocation.getBlockX(), coreLocation.getBlockY(), coreLocation.getBlockZ(), ignoredId)) {
            throw new IllegalArgumentException("claim-overlap");
        }
    }

    private void registerNewClaim(Claim claim) {
        context.defaultsService.applyClaimDefaults(claim);
        context.runtime.claims().put(claim.id(), claim);
        context.lookupService.rebuildClaimChunkIndex();
        context.publishClaimSync(ClaimSyncEventType.CLAIM_CREATED, claim.id());
        context.trackNewClaim(claim);
    }
}
