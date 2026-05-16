package com.coreclaim.claim.mutation;

import com.coreclaim.model.Claim;
import com.coreclaim.model.ClaimCreationType;
import com.coreclaim.sync.ClaimSyncEventType;
import java.time.Instant;
import java.util.logging.Level;
import java.util.UUID;
import org.bukkit.Location;

final class ClaimCreationMutations {

    private final ClaimMutationContext context;
    private final FinalClaimCreationValidator finalValidator;

    ClaimCreationMutations(ClaimMutationContext context) {
        this.context = context;
        this.finalValidator = new FinalClaimCreationValidator(context);
    }

    Claim createClaim(UUID owner, String ownerName, String name, Location center, int initialDistance, ClaimCreationOptions options) {
        ClaimCreationOptions effectiveOptions = options == null
            ? ClaimCreationOptions.coreClaim(Integer.MAX_VALUE, Integer.MAX_VALUE, 0, 0)
            : options;
        return createClaim(ClaimCreationRequest.core(owner, ownerName, name, center, initialDistance, effectiveOptions)).claim();
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
        boolean systemManaged,
        ClaimCreationOptions options
    ) {
        ClaimCreationOptions effectiveOptions = options == null
            ? ClaimCreationOptions.selectionClaim(Integer.MAX_VALUE, Integer.MAX_VALUE, 0, 0, 0, systemManaged)
            : options;
        return createClaim(ClaimCreationRequest.bounds(owner, ownerName, name, coreLocation, minY, maxY, east, south, west, north, systemManaged, effectiveOptions)).claim();
    }

    ClaimCreationResult createClaim(ClaimCreationRequest request) {
        ClaimCreationOptions effectiveOptions = request.options() == null
            ? ClaimCreationOptions.selectionClaim(Integer.MAX_VALUE, Integer.MAX_VALUE, 0, 0, 0, request.systemManaged())
            : request.options();
        ClaimCreationRequest effectiveRequest = new ClaimCreationRequest(
            request.owner(),
            request.ownerName(),
            request.name(),
            request.world(),
            request.centerX(),
            request.centerY(),
            request.centerZ(),
            request.worldMinY(),
            request.worldMaxY(),
            request.minY(),
            request.maxY(),
            request.east(),
            request.south(),
            request.west(),
            request.north(),
            request.fullHeight(),
            request.systemManaged(),
            request.creationType(),
            effectiveOptions
        );
        synchronized (context.runtime.mutationLock()) {
            ClaimCreationResult result = context.runtime.databaseManager().transaction(() -> {
                String sanitizedName = finalValidator.sanitizeAvailableName(effectiveRequest.name());
                String currentServerId = context.lookupService.currentServerId();
                FinalClaimCreationValidator.ValidationResult validation = finalValidator.validateAndLock(effectiveRequest);
                long createdAt = Instant.now().getEpochSecond();
                int generatedId = insertClaim(sanitizedName, effectiveRequest, currentServerId, createdAt);
                Claim createdClaim = buildClaim(generatedId, sanitizedName, effectiveRequest, currentServerId, createdAt);
                context.defaultsService.applyClaimDefaults(createdClaim);
                return new ClaimCreationResult(createdClaim, validation.ownerClaimCount());
            });
            registerCommittedClaim(result.claim());
            return result;
        }
    }

    private int insertClaim(
        String sanitizedName,
        ClaimCreationRequest request,
        String currentServerId,
        long createdAt
    ) {
        int radius = request.fullHeight() ? request.east() : Math.max(Math.max(request.east(), request.west()), Math.max(request.south(), request.north()));
        return (int) context.runtime.databaseManager().insertAndReturnKey(
            """
            INSERT INTO claims (
                owner_uuid, owner_name, name, name_key, core_visible, world, server_id, center_x, center_y, center_z,
                min_y, max_y, full_height, radius, east, south, west, north, enter_message, leave_message,
                allow_place, allow_break, allow_interact, allow_container, allow_mob_interact, allow_animal_spawn, allow_monster_spawn, allow_redstone, allow_explosion, allow_bucket, allow_teleport, allow_flight, system_managed, creation_type, last_expanded_at, created_at
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """,
            statement -> {
                statement.setString(1, request.owner().toString());
                statement.setString(2, request.ownerName());
                statement.setString(3, sanitizedName);
                statement.setString(4, com.coreclaim.claim.query.ClaimNameNormalizer.normalize(sanitizedName));
                statement.setInt(5, 1);
                statement.setString(6, request.world());
                statement.setString(7, currentServerId);
                statement.setInt(8, request.centerX());
                statement.setInt(9, request.centerY());
                statement.setInt(10, request.centerZ());
                statement.setInt(11, request.minY());
                statement.setInt(12, request.maxY());
                statement.setInt(13, request.fullHeight() ? 1 : 0);
                statement.setInt(14, radius);
                statement.setInt(15, request.east());
                statement.setInt(16, request.south());
                statement.setInt(17, request.west());
                statement.setInt(18, request.north());
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
                statement.setInt(33, request.systemManaged() ? 1 : 0);
                statement.setString(34, creationType(request).databaseValue());
                statement.setLong(35, 0L);
                statement.setLong(36, createdAt);
            }
        );
    }

    private Claim buildClaim(int generatedId, String sanitizedName, ClaimCreationRequest request, String currentServerId, long createdAt) {
        return new Claim(
            generatedId,
            request.owner(),
            request.ownerName(),
            sanitizedName,
            currentServerId,
            request.world(),
            request.centerX(),
            request.centerY(),
            request.centerZ(),
            request.minY(),
            request.maxY(),
            request.fullHeight(),
            request.east(),
            request.south(),
            request.west(),
            request.north(),
            createdAt,
            true,
            "",
            "",
            false,
            false,
            request.fullHeight(),
            request.fullHeight(),
            false,
            false,
            false,
            request.fullHeight(),
            request.fullHeight(),
            false,
            false,
            true,
            request.systemManaged(),
            creationType(request),
            false,
            null,
            null,
            null,
            null,
            null,
            0L
        );
    }

    private ClaimCreationType creationType(ClaimCreationRequest request) {
        return request.creationType() == null ? ClaimCreationType.UNKNOWN_LEGACY : request.creationType();
    }

    private void registerCommittedClaim(Claim claim) {
        try {
            context.runtime.claims().put(claim.id(), claim);
            context.lookupService.rebuildClaimChunkIndex();
        } catch (RuntimeException exception) {
            context.runtime.plugin().getLogger().log(
                Level.SEVERE,
                "Claim " + claim.id() + " committed to database but failed to register in memory. Reloading claims.",
                exception
            );
            try {
                context.lookupService.reloadClaims();
            } catch (RuntimeException reloadException) {
                context.runtime.plugin().getLogger().log(
                    Level.SEVERE,
                    "Failed to reload claims after committed claim registration failure.",
                    reloadException
                );
            }
        }

        try {
            context.publishClaimSync(ClaimSyncEventType.CLAIM_CREATED, claim.id());
        } catch (RuntimeException exception) {
            context.runtime.plugin().getLogger().log(Level.WARNING, "Claim committed but sync publish failed: " + claim.id(), exception);
        }

        try {
            context.trackNewClaim(claim);
        } catch (RuntimeException exception) {
            context.runtime.plugin().getLogger().log(Level.WARNING, "Claim committed but activity tracking failed: " + claim.id(), exception);
        }
    }
}
