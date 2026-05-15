package com.coreclaim.claim.mutation;

import com.coreclaim.claim.query.ClaimNameNormalizer;
import com.coreclaim.config.PluginConfig;

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

    ValidationResult validateAndLock(ClaimCreationRequest request) {
        if (request == null || request.world() == null || request.world().isBlank()) {
            throw new IllegalArgumentException("world-missing");
        }
        PluginConfig settings = context.runtime.plugin().settings();
        if (!settings.isClaimWorld(request.world())) {
            throw new IllegalArgumentException("claim-world-only");
        }
        ClaimCreationOptions options = request.options();
        int ownerClaimCount = countOwnerClaims(request.owner());
        if (options.enforceQuota() && ownerClaimCount >= options.maxClaims()) {
            throw new IllegalArgumentException("claim-no-slot");
        }
        if (options.enforceSizeLimit() && options.maxDistance() > 0
            && Math.max(Math.max(request.east(), request.west()), Math.max(request.north(), request.south())) > options.maxDistance()) {
            throw new IllegalArgumentException("selection-too-large");
        }
        if (request.minY() > request.maxY() || request.minY() < request.worldMinY() || request.maxY() > request.worldMaxY()) {
            throw new IllegalArgumentException("selection-too-large");
        }

        int minX = request.minX();
        int maxX = request.maxX();
        int minZ = request.minZ();
        int maxZ = request.maxZ();
        int lockGap = options.lockGap();
        context.runtime.spatialLockService().lockArea(request.world(), minX - lockGap, maxX + lockGap, minZ - lockGap, maxZ + lockGap);
        if (context.runtime.spatialLockService().hasOverlappingClaim(request.world(), minX, maxX, request.minY(), request.maxY(), minZ, maxZ, null, request.fullHeight())) {
            throw new IllegalArgumentException("claim-overlap");
        }
        if (options.minimumGap() > 0 && context.runtime.spatialLockService().hasClaimWithinGap(
            request.world(), minX, maxX, request.minY(), request.maxY(), minZ, maxZ, options.minimumGap(), null, request.fullHeight()
        )) {
            throw new IllegalArgumentException("claim-overlap");
        }
        if (options.selectionMinimumGap() > 0 && context.runtime.spatialLockService().hasClaimWithinGap(
            request.world(), minX, maxX, request.minY(), request.maxY(), minZ, maxZ, options.selectionMinimumGap(), null, request.fullHeight()
        )) {
            throw new IllegalArgumentException("selection-claim-too-close");
        }
        if (options.minimumCoreSpacing() > 0 && context.runtime.spatialLockService().hasCoreWithinSpacing(
            request.world(), request.centerX(), request.centerZ(), options.minimumCoreSpacing(), null
        )) {
            throw new IllegalArgumentException("claim-core-too-close");
        }
        if (context.runtime.spatialLockService().hasCoreAt(request.world(), request.centerX(), request.centerY(), request.centerZ(), null)) {
            throw new IllegalArgumentException("claim-overlap");
        }
        return new ValidationResult(ownerClaimCount);
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

    record ValidationResult(int ownerClaimCount) {
    }
}
