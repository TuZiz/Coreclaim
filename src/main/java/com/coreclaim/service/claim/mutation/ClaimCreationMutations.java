package com.coreclaim.service.claim.mutation;

import com.coreclaim.model.Claim;
import com.coreclaim.service.ClaimSyncEventType;
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
            String sanitizedName = context.lookupService.validateAvailableClaimName(name, null);
            String currentServerId = context.lookupService.currentServerId();
            int minY = center.getWorld() == null ? -64 : center.getWorld().getMinHeight();
            int maxY = center.getWorld() == null ? 319 : center.getWorld().getMaxHeight() - 1;
            long createdAt = Instant.now().getEpochSecond();
            int generatedId = insertClaim(owner, ownerName, sanitizedName, center, minY, maxY, initialDistance, initialDistance, initialDistance, initialDistance, true, false, currentServerId, createdAt);
            Claim claim = new Claim(
                generatedId,
                owner,
                ownerName,
                sanitizedName,
                currentServerId,
                center.getWorld().getName(),
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
                false,
                false,
                false,
                false,
                false,
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
            String sanitizedName = context.lookupService.validateAvailableClaimName(name, null);
            String currentServerId = context.lookupService.currentServerId();
            long createdAt = Instant.now().getEpochSecond();
            int generatedId = insertClaim(owner, ownerName, sanitizedName, coreLocation, minY, maxY, east, south, west, north, false, systemManaged, currentServerId, createdAt);
            Claim claim = new Claim(
                generatedId,
                owner,
                ownerName,
                sanitizedName,
                currentServerId,
                coreLocation.getWorld().getName(),
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
                owner_uuid, owner_name, name, core_visible, world, server_id, center_x, center_y, center_z,
                min_y, max_y, full_height, radius, east, south, west, north, enter_message, leave_message,
                allow_place, allow_break, allow_interact, allow_container, allow_mob_interact, allow_redstone, allow_explosion, allow_bucket, allow_teleport, allow_flight, system_managed, last_expanded_at, created_at
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """,
            statement -> {
                statement.setString(1, owner.toString());
                statement.setString(2, ownerName);
                statement.setString(3, sanitizedName);
                statement.setInt(4, 1);
                statement.setString(5, coreLocation.getWorld().getName());
                statement.setString(6, currentServerId);
                statement.setInt(7, coreLocation.getBlockX());
                statement.setInt(8, coreLocation.getBlockY());
                statement.setInt(9, coreLocation.getBlockZ());
                statement.setInt(10, minY);
                statement.setInt(11, maxY);
                statement.setInt(12, fullHeight ? 1 : 0);
                statement.setInt(13, radius);
                statement.setInt(14, east);
                statement.setInt(15, south);
                statement.setInt(16, west);
                statement.setInt(17, north);
                statement.setString(18, "");
                statement.setString(19, "");
                statement.setInt(20, 0);
                statement.setInt(21, 0);
                statement.setInt(22, 0);
                statement.setInt(23, 0);
                statement.setInt(24, 0);
                statement.setInt(25, 0);
                statement.setInt(26, 0);
                statement.setInt(27, 0);
                statement.setInt(28, 0);
                statement.setInt(29, 1);
                statement.setInt(30, systemManaged ? 1 : 0);
                statement.setLong(31, 0L);
                statement.setLong(32, createdAt);
            }
        );
    }

    private void registerNewClaim(Claim claim) {
        context.defaultsService.applyClaimDefaults(claim);
        context.runtime.claims().put(claim.id(), claim);
        context.lookupService.rebuildClaimChunkIndex();
        context.publishClaimSync(ClaimSyncEventType.CLAIM_CREATED, claim.id());
        context.trackNewClaim(claim);
    }
}
