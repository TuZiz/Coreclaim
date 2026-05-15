package com.coreclaim.claim.mutation;

import com.coreclaim.claim.query.ClaimNameNormalizer;
import com.coreclaim.config.PluginConfig;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;

final class FinalClaimCreationValidator {

    private final ClaimMutationContext context;

    FinalClaimCreationValidator(ClaimMutationContext context) {
        this.context = context;
    }

    String sanitizeAvailableName(String name) {
        String sanitizedName = ClaimNameNormalizer.sanitize(name);
        if (sanitizedName == null) {
            throw new IllegalArgumentException("claim-name-empty");
        }
        String normalizedName = ClaimNameNormalizer.normalize(sanitizedName);
        if (context.runtime.databaseManager().query(
            "SELECT id FROM claims WHERE name_key = ? LIMIT 1",
            statement -> statement.setString(1, normalizedName),
            resultSet -> resultSet.next()
        )) {
            throw new IllegalArgumentException("claim-name-exists");
        }
        return sanitizedName;
    }

    void validateAndLock(
        java.util.UUID owner,
        Location coreLocation,
        int minY,
        int maxY,
        int east,
        int south,
        int west,
        int north,
        boolean fullHeight,
        ClaimCreationOptions options
    ) {
        if (coreLocation == null || coreLocation.getWorld() == null) {
            throw new IllegalArgumentException("world-missing");
        }
        World world = coreLocation.getWorld();
        PluginConfig settings = context.runtime.plugin().settings();
        if (!settings.isClaimWorld(world.getName())) {
            throw new IllegalArgumentException("claim-world-only");
        }
        if (options.enforceQuota() && countOwnerClaims(owner) >= options.maxClaims()) {
            throw new IllegalArgumentException("claim-no-slot");
        }
        if (options.enforceSizeLimit() && options.maxDistance() > 0
            && Math.max(Math.max(east, west), Math.max(north, south)) > options.maxDistance()) {
            throw new IllegalArgumentException("selection-too-large");
        }
        if (minY > maxY || minY < world.getMinHeight() || maxY >= world.getMaxHeight()) {
            throw new IllegalArgumentException("selection-too-large");
        }

        int minX = coreLocation.getBlockX() - west;
        int maxX = coreLocation.getBlockX() + east;
        int minZ = coreLocation.getBlockZ() - north;
        int maxZ = coreLocation.getBlockZ() + south;
        int lockGap = options.lockGap();
        context.runtime.spatialLockService().lockArea(world.getName(), minX - lockGap, maxX + lockGap, minZ - lockGap, maxZ + lockGap);
        if (context.runtime.spatialLockService().hasOverlappingClaim(world.getName(), minX, maxX, minY, maxY, minZ, maxZ, null, fullHeight)) {
            throw new IllegalArgumentException("claim-overlap");
        }
        if (options.minimumGap() > 0 && context.runtime.spatialLockService().hasClaimWithinGap(
            world.getName(), minX, maxX, minY, maxY, minZ, maxZ, options.minimumGap(), null, fullHeight
        )) {
            throw new IllegalArgumentException("claim-overlap");
        }
        if (options.selectionMinimumGap() > 0 && context.runtime.spatialLockService().hasClaimWithinGap(
            world.getName(), minX, maxX, minY, maxY, minZ, maxZ, options.selectionMinimumGap(), null, fullHeight
        )) {
            throw new IllegalArgumentException("selection-claim-too-close");
        }
        if (options.minimumCoreSpacing() > 0 && context.runtime.spatialLockService().hasCoreWithinSpacing(
            world.getName(), coreLocation.getBlockX(), coreLocation.getBlockZ(), options.minimumCoreSpacing(), null
        )) {
            throw new IllegalArgumentException("claim-core-too-close");
        }
        if (context.runtime.spatialLockService().hasCoreAt(world.getName(), coreLocation.getBlockX(), coreLocation.getBlockY(), coreLocation.getBlockZ(), null)) {
            throw new IllegalArgumentException("claim-overlap");
        }
        if (options.requireCoreAir() && !coreLocation.getBlock().getType().isAir()) {
            throw new IllegalArgumentException(options.coreBlockedFailureKey());
        }
    }

    boolean placeCoreBlock(Location coreLocation, ClaimCreationOptions options) {
        if (!options.placeCoreBlock()) {
            return false;
        }
        if (coreLocation == null || coreLocation.getWorld() == null || !coreLocation.getBlock().getType().isAir()) {
            throw new IllegalArgumentException(options.coreBlockedFailureKey());
        }
        coreLocation.getBlock().setType(context.runtime.plugin().settings().coreMaterial(), false);
        return coreLocation.getBlock().getType() == context.runtime.plugin().settings().coreMaterial();
    }

    void clearPlacedCoreBlock(Location coreLocation) {
        if (coreLocation == null || coreLocation.getWorld() == null) {
            return;
        }
        if (coreLocation.getBlock().getType() == context.runtime.plugin().settings().coreMaterial()) {
            coreLocation.getBlock().setType(Material.AIR, false);
        }
    }

    private int countOwnerClaims(java.util.UUID owner) {
        if (owner == null) {
            return 0;
        }
        return context.runtime.databaseManager().query(
            "SELECT COUNT(*) AS claim_count FROM claims WHERE owner_uuid = ? AND system_managed = 0",
            statement -> statement.setString(1, owner.toString()),
            resultSet -> resultSet.next() ? resultSet.getInt("claim_count") : 0
        );
    }
}
