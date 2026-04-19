package com.coreclaim.service;

import com.coreclaim.CoreClaimPlugin;
import com.coreclaim.model.Claim;
import com.coreclaim.model.ClaimFlag;
import com.coreclaim.model.ClaimFlagState;
import com.coreclaim.model.ClaimMemberSettings;
import com.coreclaim.model.ClaimPermission;
import com.coreclaim.storage.DatabaseManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;

public final class ClaimService {

    private static final String MISSING_SERVER_ID_CONDITION = "server_id IS NULL OR TRIM(server_id) = ''";

    private final CoreClaimPlugin plugin;
    private final DatabaseManager databaseManager;
    private final ProfileService profileService;
    private final Map<Integer, Claim> claims = new ConcurrentHashMap<>();
    private volatile Map<String, Map<Long, List<Claim>>> claimChunkIndex = Map.of();
    private final Object mutationLock = new Object();
    private volatile ClaimSyncPublisher claimSyncPublisher = ClaimSyncPublisher.NO_OP;

    public ClaimService(CoreClaimPlugin plugin, DatabaseManager databaseManager, ProfileService profileService) {
        this.plugin = plugin;
        this.databaseManager = databaseManager;
        this.profileService = profileService;
        backfillMissingServerIds();
        reloadClaims();
    }

    public String currentServerId() {
        return plugin.settings().serverId();
    }

    public void setClaimSyncPublisher(ClaimSyncPublisher claimSyncPublisher) {
        this.claimSyncPublisher = claimSyncPublisher == null ? ClaimSyncPublisher.NO_OP : claimSyncPublisher;
    }

    public String effectiveServerId(Claim claim) {
        if (claim == null) {
            return null;
        }
        return effectiveServerId(claim.serverId(), claim.world());
    }

    public boolean isLocalClaim(Claim claim) {
        String effectiveServerId = effectiveServerId(claim);
        return effectiveServerId != null && plugin.settings().isCurrentServer(effectiveServerId);
    }

    public String displayServerId(Claim claim) {
        String effectiveServerId = effectiveServerId(claim);
        return effectiveServerId == null || effectiveServerId.isBlank() ? "unknown" : effectiveServerId;
    }

    public boolean countsTowardQuota(Claim claim) {
        return claim != null && !claim.systemManaged();
    }

    public boolean matchesConfiguredDefaults(Claim claim) {
        return claim != null && matchesPermissionDefaults(claim) && matchesFlagDefaults(claim);
    }

    public boolean hasManualRuleOverrides(Claim claim) {
        return claim != null && !matchesConfiguredDefaults(claim);
    }

    public String ruleProfileName(Claim claim) {
        return claim != null && claim.systemManaged() ? "系统公共规则" : "新领地默认规则";
    }

    public TeleportTarget teleportTarget(Claim claim, float fallbackYaw, float fallbackPitch) {
        if (claim != null && claim.hasTeleportPoint()) {
            return new TeleportTarget(
                claim.world(),
                claim.teleportX(),
                claim.teleportY(),
                claim.teleportZ(),
                claim.teleportYaw() == null ? fallbackYaw : claim.teleportYaw(),
                claim.teleportPitch() == null ? fallbackPitch : claim.teleportPitch(),
                true
            );
        }
        return new TeleportTarget(
            claim.world(),
            claim.centerX() + 0.5D,
            claim.centerY() + 1D,
            claim.centerZ() + 0.5D,
            fallbackYaw,
            fallbackPitch,
            false
        );
    }

    public ClaimFlagState flagState(Claim claim, ClaimFlag flag) {
        if (claim == null || flag == null) {
            return ClaimFlagState.UNSET;
        }
        return claim.flagState(flag);
    }

    public boolean hasFlagPermission(Claim claim, UUID playerId, ClaimFlag flag) {
        if (claim == null || playerId == null || flag == null) {
            return false;
        }
        if (claim.owner().equals(playerId)) {
            return true;
        }
        if (claim.isDenied(playerId)) {
            return false;
        }

        ClaimFlagState state = claim.flagState(flag);
        if (claim.isTrusted(playerId)) {
            return true;
        }
        if (claim.denyAll()) {
            return false;
        }
        if (claim.systemManaged()) {
            return state.resolve(claim.permission(flag.fallbackPermission()));
        }
        if (profileService.isGloballyTrusted(claim.owner(), playerId)) {
            return state.resolve(claim.permission(flag.fallbackPermission()));
        }
        return false;
    }

    private String effectiveServerId(String explicitServerId, String worldName) {
        if (explicitServerId != null && !explicitServerId.isBlank()) {
            return explicitServerId.trim();
        }
        return databaseManager.isMySql() ? null : currentServerId();
    }

    public Optional<Claim> findClaim(Location location) {
        for (Claim claim : claimCandidates(location)) {
            if (claim.contains(location)) {
                return Optional.of(claim);
            }
        }
        return Optional.empty();
    }

    public Optional<Claim> findPlayerPresenceClaim(Location location) {
        List<Claim> candidates = claimCandidates(location);
        for (Claim claim : candidates) {
            if (claim.contains(location)) {
                return Optional.of(claim);
            }
        }

        Claim horizontalMatch = null;
        for (Claim claim : candidates) {
            if (claim.fullHeight() || !claim.containsHorizontally(location)) {
                continue;
            }
            if (horizontalMatch != null) {
                return Optional.empty();
            }
            horizontalMatch = claim;
        }
        return Optional.ofNullable(horizontalMatch);
    }

    public Optional<Claim> findClaimById(int id) {
        return Optional.ofNullable(claims.get(id));
    }

    public Optional<Claim> findClaimByIdOrLoad(int id) {
        Claim claim = claims.get(id);
        if (claim != null) {
            return Optional.of(claim);
        }
        return refreshClaimFromDatabase(id);
    }

    public Optional<Claim> findClaimByIdFresh(int id) {
        if (!databaseManager.isMySql()) {
            return findClaimByIdOrLoad(id);
        }
        reloadClaim(id);
        return findClaimById(id);
    }

    public Optional<Claim> refreshClaimFromDatabase(int id) {
        ClaimRefreshResult refreshed = reloadClaim(id);
        return refreshed.currentClaim() == null ? Optional.empty() : findClaimById(id);
    }

    public ClaimRefreshResult reloadClaim(int id) {
        synchronized (mutationLock) {
            Optional<Claim> loadedClaim = loadClaimFromDatabase(id);
            Claim previousClaim = claims.get(id);
            Claim previousSnapshot = snapshotClaim(previousClaim);
            if (loadedClaim.isPresent()) {
                claims.put(id, loadedClaim.get());
                rebuildClaimChunkIndex();
                return new ClaimRefreshResult(previousSnapshot, snapshotClaim(loadedClaim.get()));
            }
            if (previousClaim != null) {
                claims.remove(id);
                rebuildClaimChunkIndex();
            }
            return new ClaimRefreshResult(previousSnapshot, null);
        }
    }

    public Optional<Claim> updateClaimServerId(int id, String serverId) {
        String sanitizedServerId = serverId == null ? "" : serverId.trim();
        if (sanitizedServerId.isEmpty()) {
            return Optional.empty();
        }
        synchronized (mutationLock) {
            int updated = databaseManager.update(
                "UPDATE claims SET server_id = ? WHERE id = ?",
                statement -> {
                    statement.setString(1, sanitizedServerId);
                    statement.setInt(2, id);
                }
            );
            if (updated <= 0) {
                return Optional.empty();
            }
            ClaimRefreshResult refreshed = reloadClaim(id);
            publishClaimSync(ClaimSyncEventType.CLAIM_SERVER_CHANGED, id);
            return refreshed.currentClaim() == null ? Optional.empty() : findClaimById(id);
        }
    }

    public List<Claim> claimsOf(UUID owner) {
        return claimsOf(owner, false);
    }

    public List<Claim> claimsOf(UUID owner, boolean includeSystem) {
        List<Claim> result = new ArrayList<>();
        for (Claim claim : claims.values()) {
            if (claim.owner().equals(owner) && (includeSystem || countsTowardQuota(claim))) {
                result.add(claim);
            }
        }
        result.sort(Comparator.comparingInt(Claim::id));
        return result;
    }

    public List<Claim> claimsOfFresh(UUID owner) {
        return claimsOfFresh(owner, false);
    }

    public List<Claim> claimsOfFresh(UUID owner, boolean includeSystem) {
        if (!databaseManager.isMySql()) {
            return claimsOf(owner, includeSystem);
        }
        List<Integer> freshIds = databaseManager.query(
            includeSystem
                ? "SELECT id FROM claims WHERE owner_uuid = ? ORDER BY id"
                : "SELECT id FROM claims WHERE owner_uuid = ? AND system_managed = 0 ORDER BY id",
            statement -> statement.setString(1, owner.toString()),
            resultSet -> {
                List<Integer> ids = new ArrayList<>();
                while (resultSet.next()) {
                    ids.add(resultSet.getInt("id"));
                }
                return ids;
            }
        );
        List<Claim> refreshedClaims = new ArrayList<>();
        for (int claimId : freshIds) {
            findClaimByIdFresh(claimId).ifPresent(refreshedClaims::add);
        }
        java.util.Set<Integer> idSet = java.util.Set.copyOf(freshIds);
        for (Claim cachedClaim : new ArrayList<>(claims.values())) {
            if (owner.equals(cachedClaim.owner())
                && (includeSystem || countsTowardQuota(cachedClaim))
                && !idSet.contains(cachedClaim.id())) {
                reloadClaim(cachedClaim.id());
            }
        }
        refreshedClaims.sort(Comparator.comparingInt(Claim::id));
        return refreshedClaims;
    }

    public int countClaims(UUID owner) {
        return countClaims(owner, false);
    }

    public int countClaims(UUID owner, boolean includeSystem) {
        return claimsOfFresh(owner, includeSystem).size();
    }

    private List<Claim> claimCandidates(Location location) {
        if (location == null || location.getWorld() == null) {
            return List.of();
        }
        Map<Long, List<Claim>> worldIndex = claimChunkIndex.get(location.getWorld().getName());
        if (worldIndex == null || worldIndex.isEmpty()) {
            return List.of();
        }
        List<Claim> candidates = worldIndex.get(chunkKey(location.getBlockX() >> 4, location.getBlockZ() >> 4));
        if (candidates == null || candidates.isEmpty()) {
            return List.of();
        }
        return candidates;
    }

    public List<Claim> allClaims() {
        return new ArrayList<>(claims.values());
    }

    public List<Claim> findClaimsByName(String rawName) {
        String normalizedName = normalizeClaimName(rawName);
        if (normalizedName == null) {
            return List.of();
        }
        return claims.values().stream()
            .filter(claim -> normalizedName.equals(normalizeClaimName(claim.name())))
            .sorted(Comparator.comparingInt(Claim::id))
            .toList();
    }

    public List<Claim> findClaimsByNameFresh(String rawName) {
        String normalizedName = normalizeClaimName(rawName);
        if (normalizedName == null || !databaseManager.isMySql()) {
            return findClaimsByName(rawName);
        }
        List<Integer> freshIds = databaseManager.query(
            "SELECT id FROM claims WHERE LOWER(name) = ? ORDER BY id",
            statement -> statement.setString(1, normalizedName),
            resultSet -> {
                List<Integer> ids = new ArrayList<>();
                while (resultSet.next()) {
                    ids.add(resultSet.getInt("id"));
                }
                return ids;
            }
        );
        List<Claim> refreshedClaims = new ArrayList<>();
        for (int claimId : freshIds) {
            findClaimByIdFresh(claimId).ifPresent(refreshedClaims::add);
        }
        java.util.Set<Integer> idSet = java.util.Set.copyOf(freshIds);
        for (Claim cachedClaim : new ArrayList<>(claims.values())) {
            if (normalizedName.equals(normalizeClaimName(cachedClaim.name())) && !idSet.contains(cachedClaim.id())) {
                reloadClaim(cachedClaim.id());
            }
        }
        refreshedClaims.sort(Comparator.comparingInt(Claim::id));
        return refreshedClaims;
    }

    public int reloadClaims() {
        synchronized (mutationLock) {
            backfillMissingServerIds();
            Map<Integer, Claim> loadedClaims = loadClaimsFromDatabase();
            claims.clear();
            claims.putAll(loadedClaims);
            rebuildClaimChunkIndex();
            return claims.size();
        }
    }

    public boolean isClaimNameTaken(String rawName) {
        return isClaimNameTaken(rawName, null);
    }

    public boolean isClaimNameTaken(String rawName, Integer excludedClaimId) {
        String normalizedName = normalizeClaimName(rawName);
        if (normalizedName == null) {
            return false;
        }
        if (databaseManager.isMySql()) {
            return databaseManager.query(
                excludedClaimId == null
                    ? "SELECT id FROM claims WHERE LOWER(name) = ? LIMIT 1"
                    : "SELECT id FROM claims WHERE LOWER(name) = ? AND id <> ? LIMIT 1",
                statement -> {
                    statement.setString(1, normalizedName);
                    if (excludedClaimId != null) {
                        statement.setInt(2, excludedClaimId);
                    }
                },
                ResultSet::next
            );
        }
        for (Claim claim : claims.values()) {
            if (excludedClaimId != null && excludedClaimId == claim.id()) {
                continue;
            }
            if (normalizedName.equals(normalizeClaimName(claim.name()))) {
                return true;
            }
        }
        return false;
    }

    public boolean overlaps(String world, int minX, int maxX, int minZ, int maxZ, Integer ignoredId) {
        return overlaps(world, minX, maxX, -64, 319, minZ, maxZ, ignoredId, true);
    }

    public boolean overlaps(String world, int minX, int maxX, int minY, int maxY, int minZ, int maxZ, Integer ignoredId, boolean fullHeight) {
        for (Claim claim : claims.values()) {
            if (!isLocalClaim(claim)) {
                continue;
            }
            if (claim.overlaps(world, minX, maxX, minY, maxY, minZ, maxZ, ignoredId, fullHeight)) {
                return true;
            }
        }
        return false;
    }

    public boolean hasCoreWithinSpacing(String world, int centerX, int centerZ, int spacing, Integer ignoredId) {
        for (Claim claim : claims.values()) {
            if (!isLocalClaim(claim)) {
                continue;
            }
            if (!claim.world().equals(world)) {
                continue;
            }
            if (ignoredId != null && ignoredId == claim.id()) {
                continue;
            }
            if (Math.abs(claim.centerX() - centerX) < spacing && Math.abs(claim.centerZ() - centerZ) < spacing) {
                return true;
            }
        }
        return false;
    }

    public boolean hasClaimWithinGap(
        String world,
        int minX,
        int maxX,
        int minY,
        int maxY,
        int minZ,
        int maxZ,
        int gap,
        Integer ignoredId,
        boolean fullHeight,
        Predicate<Claim> filter
    ) {
        int expandedMinX = minX - Math.max(0, gap);
        int expandedMaxX = maxX + Math.max(0, gap);
        int expandedMinZ = minZ - Math.max(0, gap);
        int expandedMaxZ = maxZ + Math.max(0, gap);
        for (Claim claim : claims.values()) {
            if (!isLocalClaim(claim)) {
                continue;
            }
            if (filter != null && !filter.test(claim)) {
                continue;
            }
            if (claim.overlaps(world, expandedMinX, expandedMaxX, minY, maxY, expandedMinZ, expandedMaxZ, ignoredId, fullHeight)) {
                return true;
            }
        }
        return false;
    }

    public boolean canAccess(Claim claim, UUID playerId) {
        if (claim.owner().equals(playerId)) {
            return true;
        }
        if (claim.isDenied(playerId)) {
            return false;
        }
        if (claim.canAccess(playerId)) {
            return true;
        }
        if (claim.denyAll()) {
            return false;
        }
        return profileService.isGloballyTrusted(claim.owner(), playerId);
    }

    public boolean hasPermission(Claim claim, UUID playerId, ClaimPermission permission) {
        if (claim.owner().equals(playerId)) {
            return true;
        }
        if (claim.isDenied(playerId)) {
            return false;
        }
        if (claim.isTrusted(playerId)) {
            return true;
        }
        if (claim.denyAll()) {
            return false;
        }
        if (claim.systemManaged()) {
            return claim.permission(permission);
        }
        if (profileService.isGloballyTrusted(claim.owner(), playerId)) {
            return claim.permission(permission);
        }
        return false;
    }

    public void updateFlagState(Claim claim, ClaimFlag flag, ClaimFlagState state) {
        if (claim == null || flag == null) {
            return;
        }
        ClaimFlagState nextState = state == null ? ClaimFlagState.UNSET : state;
        claim.setFlagState(flag, nextState);
        synchronized (mutationLock) {
            if (nextState == ClaimFlagState.UNSET) {
                databaseManager.update(
                    "DELETE FROM claim_flags WHERE claim_id = ? AND flag_key = ?",
                    statement -> {
                        statement.setInt(1, claim.id());
                        statement.setString(2, flag.key());
                    }
                );
            } else {
                databaseManager.update(
                    databaseManager.insertIgnoreSql("claim_flags", "claim_id, flag_key, state", "?, ?, ?"),
                    statement -> {
                        statement.setInt(1, claim.id());
                        statement.setString(2, flag.key());
                        statement.setInt(3, nextState.databaseValue());
                    }
                );
                databaseManager.update(
                    "UPDATE claim_flags SET state = ? WHERE claim_id = ? AND flag_key = ?",
                    statement -> {
                        statement.setInt(1, nextState.databaseValue());
                        statement.setInt(2, claim.id());
                        statement.setString(3, flag.key());
                    }
                );
            }
        }
        publishClaimSync(ClaimSyncEventType.CLAIM_UPDATED, claim.id());
    }

    public Claim createClaim(UUID owner, String ownerName, String name, Location center, int initialDistance) {
        synchronized (mutationLock) {
            String sanitizedName = validateAvailableClaimName(name, null);
            String currentServerId = currentServerId();
            int minY = center.getWorld() == null ? -64 : center.getWorld().getMinHeight();
            int maxY = center.getWorld() == null ? 319 : center.getWorld().getMaxHeight() - 1;
            long createdAt = Instant.now().getEpochSecond();
            int generatedId = (int) databaseManager.insertAndReturnKey(
                """
                INSERT INTO claims (
                    owner_uuid, owner_name, name, core_visible, world, server_id, center_x, center_y, center_z,
                    min_y, max_y, full_height, radius, east, south, west, north, enter_message, leave_message,
                    allow_place, allow_break, allow_interact, allow_container, allow_redstone, allow_explosion, allow_bucket, allow_teleport, allow_flight, system_managed, last_expanded_at, created_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                statement -> {
                    statement.setString(1, owner.toString());
                    statement.setString(2, ownerName);
                    statement.setString(3, sanitizedName);
                    statement.setInt(4, 1);
                    statement.setString(5, center.getWorld().getName());
                    statement.setString(6, currentServerId);
                    statement.setInt(7, center.getBlockX());
                    statement.setInt(8, center.getBlockY());
                    statement.setInt(9, center.getBlockZ());
                    statement.setInt(10, minY);
                    statement.setInt(11, maxY);
                    statement.setInt(12, 1);
                    statement.setInt(13, initialDistance);
                    statement.setInt(14, initialDistance);
                    statement.setInt(15, initialDistance);
                    statement.setInt(16, initialDistance);
                    statement.setInt(17, initialDistance);
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
                    statement.setInt(28, 1);
                    statement.setInt(29, 0);
                    statement.setLong(30, 0L);
                    statement.setLong(31, createdAt);
                }
            );

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
            applyClaimDefaults(claim);
            claims.put(claim.id(), claim);
            rebuildClaimChunkIndex();
            publishClaimSync(ClaimSyncEventType.CLAIM_CREATED, claim.id());
            return claim;
        }
    }

    public Claim createClaimFromBounds(UUID owner, String ownerName, String name, Location coreLocation, int minY, int maxY, int east, int south, int west, int north) {
        return createClaimFromBounds(owner, ownerName, name, coreLocation, minY, maxY, east, south, west, north, false);
    }

    public Claim createClaimFromBounds(
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
        synchronized (mutationLock) {
            String sanitizedName = validateAvailableClaimName(name, null);
            String currentServerId = currentServerId();
            long createdAt = Instant.now().getEpochSecond();
            int generatedId = (int) databaseManager.insertAndReturnKey(
                """
                INSERT INTO claims (
                    owner_uuid, owner_name, name, core_visible, world, server_id, center_x, center_y, center_z,
                    min_y, max_y, full_height, radius, east, south, west, north, enter_message, leave_message,
                    allow_place, allow_break, allow_interact, allow_container, allow_redstone, allow_explosion, allow_bucket, allow_teleport, allow_flight, system_managed, last_expanded_at, created_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
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
                    statement.setInt(12, 0);
                    statement.setInt(13, Math.max(Math.max(east, west), Math.max(south, north)));
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
                    statement.setInt(28, 1);
                    statement.setInt(29, systemManaged ? 1 : 0);
                    statement.setLong(30, 0L);
                    statement.setLong(31, createdAt);
                }
            );

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
            applyClaimDefaults(claim);
            claims.put(claim.id(), claim);
            rebuildClaimChunkIndex();
            publishClaimSync(ClaimSyncEventType.CLAIM_CREATED, claim.id());
            return claim;
        }
    }

    public void updateBounds(Claim claim, int east, int south, int west, int north) {
        synchronized (mutationLock) {
            claim.setBounds(east, south, west, north);
            claim.setLastExpandedAt(Instant.now().getEpochSecond());
            databaseManager.update(
                "UPDATE claims SET radius = ?, east = ?, south = ?, west = ?, north = ?, last_expanded_at = ? WHERE id = ?",
                statement -> {
                    statement.setInt(1, claim.displayRadius());
                    statement.setInt(2, east);
                    statement.setInt(3, south);
                    statement.setInt(4, west);
                    statement.setInt(5, north);
                    statement.setLong(6, claim.lastExpandedAt());
                    statement.setInt(7, claim.id());
                }
            );
            rebuildClaimChunkIndex();
            publishClaimSync(ClaimSyncEventType.CLAIM_UPDATED, claim.id());
        }
    }

    public void updateCoreVisibility(Claim claim, boolean coreVisible) {
        synchronized (mutationLock) {
            claim.setCoreVisible(coreVisible);
            databaseManager.update(
                "UPDATE claims SET core_visible = ? WHERE id = ?",
                statement -> {
                    statement.setInt(1, coreVisible ? 1 : 0);
                    statement.setInt(2, claim.id());
                }
            );
            publishClaimSync(ClaimSyncEventType.CLAIM_UPDATED, claim.id());
        }
    }

    public void renameClaim(Claim claim, String name) {
        synchronized (mutationLock) {
            String sanitizedName = validateAvailableClaimName(name, claim.id());
            claim.setName(sanitizedName);
            databaseManager.update(
                "UPDATE claims SET name = ? WHERE id = ?",
                statement -> {
                    statement.setString(1, sanitizedName);
                    statement.setInt(2, claim.id());
                }
            );
            publishClaimSync(ClaimSyncEventType.CLAIM_UPDATED, claim.id());
        }
    }

    public void updateEnterMessage(Claim claim, String message) {
        synchronized (mutationLock) {
            claim.setEnterMessage(message);
            databaseManager.update(
                "UPDATE claims SET enter_message = ? WHERE id = ?",
                statement -> {
                    statement.setString(1, claim.enterMessage());
                    statement.setInt(2, claim.id());
                }
            );
            publishClaimSync(ClaimSyncEventType.CLAIM_UPDATED, claim.id());
        }
    }

    public void updateLeaveMessage(Claim claim, String message) {
        synchronized (mutationLock) {
            claim.setLeaveMessage(message);
            databaseManager.update(
                "UPDATE claims SET leave_message = ? WHERE id = ?",
                statement -> {
                    statement.setString(1, claim.leaveMessage());
                    statement.setInt(2, claim.id());
                }
            );
            publishClaimSync(ClaimSyncEventType.CLAIM_UPDATED, claim.id());
        }
    }

    public void updateDenyAll(Claim claim, boolean denyAll) {
        synchronized (mutationLock) {
            claim.setDenyAll(denyAll);
            databaseManager.update(
                "UPDATE claims SET deny_all = ? WHERE id = ?",
                statement -> {
                    statement.setInt(1, denyAll ? 1 : 0);
                    statement.setInt(2, claim.id());
                }
            );
            publishClaimSync(ClaimSyncEventType.CLAIM_UPDATED, claim.id());
        }
    }

    public void updateTeleportPoint(Claim claim, Location location) {
        synchronized (mutationLock) {
            if (location == null) {
                claim.clearTeleportPoint();
            } else {
                claim.setTeleportPoint(location.getX(), location.getY(), location.getZ(), location.getYaw(), location.getPitch());
            }
            databaseManager.update(
                "UPDATE claims SET tp_x = ?, tp_y = ?, tp_z = ?, tp_yaw = ?, tp_pitch = ? WHERE id = ?",
                statement -> {
                    if (claim.hasTeleportPoint()) {
                        statement.setDouble(1, claim.teleportX());
                        statement.setDouble(2, claim.teleportY());
                        statement.setDouble(3, claim.teleportZ());
                        statement.setDouble(4, claim.teleportYaw());
                        statement.setDouble(5, claim.teleportPitch());
                    } else {
                        statement.setNull(1, java.sql.Types.DOUBLE);
                        statement.setNull(2, java.sql.Types.DOUBLE);
                        statement.setNull(3, java.sql.Types.DOUBLE);
                        statement.setNull(4, java.sql.Types.DOUBLE);
                        statement.setNull(5, java.sql.Types.DOUBLE);
                    }
                    statement.setInt(6, claim.id());
                }
            );
            publishClaimSync(ClaimSyncEventType.CLAIM_UPDATED, claim.id());
        }
    }

    public void updatePermission(Claim claim, ClaimPermission permission, boolean allowed) {
        synchronized (mutationLock) {
            claim.setPermission(permission, allowed);
            String column = switch (permission) {
                case PLACE -> "allow_place";
                case BREAK -> "allow_break";
                case INTERACT -> "allow_interact";
                case CONTAINER -> "allow_container";
                case REDSTONE -> "allow_redstone";
                case EXPLOSION -> "allow_explosion";
                case BUCKET -> "allow_bucket";
                case TELEPORT -> "allow_teleport";
                case FLIGHT -> "allow_flight";
            };
            databaseManager.update(
                "UPDATE claims SET " + column + " = ? WHERE id = ?",
                statement -> {
                    statement.setInt(1, allowed ? 1 : 0);
                    statement.setInt(2, claim.id());
                }
            );
            publishClaimSync(ClaimSyncEventType.CLAIM_UPDATED, claim.id());
        }
    }

    public boolean addTrustedMember(Claim claim, UUID memberId) {
        synchronized (mutationLock) {
            if (!claim.addTrustedMember(memberId)) {
                return false;
            }
            claim.removeDeniedMember(memberId);
            claim.removeMemberSettings(memberId);
            databaseManager.update(
                databaseManager.insertIgnoreSql("claim_members", "claim_id, player_uuid", "?, ?"),
                statement -> {
                    statement.setInt(1, claim.id());
                    statement.setString(2, memberId.toString());
                }
            );
            databaseManager.update(
                "DELETE FROM claim_blacklist WHERE claim_id = ? AND player_uuid = ?",
                statement -> {
                    statement.setInt(1, claim.id());
                    statement.setString(2, memberId.toString());
                }
            );
            databaseManager.update(
                "DELETE FROM claim_member_permissions WHERE claim_id = ? AND player_uuid = ?",
                statement -> {
                    statement.setInt(1, claim.id());
                    statement.setString(2, memberId.toString());
                }
            );
            publishClaimSync(ClaimSyncEventType.CLAIM_UPDATED, claim.id());
            return true;
        }
    }

    public boolean removeTrustedMember(Claim claim, UUID memberId) {
        synchronized (mutationLock) {
            if (!claim.removeTrustedMember(memberId)) {
                return false;
            }
            claim.removeMemberSettings(memberId);
            databaseManager.update(
                "DELETE FROM claim_members WHERE claim_id = ? AND player_uuid = ?",
                statement -> {
                    statement.setInt(1, claim.id());
                    statement.setString(2, memberId.toString());
                }
            );
            databaseManager.update(
                "DELETE FROM claim_member_permissions WHERE claim_id = ? AND player_uuid = ?",
                statement -> {
                    statement.setInt(1, claim.id());
                    statement.setString(2, memberId.toString());
                }
            );
            publishClaimSync(ClaimSyncEventType.CLAIM_UPDATED, claim.id());
            return true;
        }
    }

    public boolean addDeniedMember(Claim claim, UUID memberId) {
        synchronized (mutationLock) {
            if (!claim.addDeniedMember(memberId)) {
                return false;
            }
            claim.removeTrustedMember(memberId);
            claim.removeMemberSettings(memberId);
            databaseManager.update(
                "DELETE FROM claim_members WHERE claim_id = ? AND player_uuid = ?",
                statement -> {
                    statement.setInt(1, claim.id());
                    statement.setString(2, memberId.toString());
                }
            );
            databaseManager.update(
                "DELETE FROM claim_member_permissions WHERE claim_id = ? AND player_uuid = ?",
                statement -> {
                    statement.setInt(1, claim.id());
                    statement.setString(2, memberId.toString());
                }
            );
            databaseManager.update(
                databaseManager.insertIgnoreSql("claim_blacklist", "claim_id, player_uuid", "?, ?"),
                statement -> {
                    statement.setInt(1, claim.id());
                    statement.setString(2, memberId.toString());
                }
            );
            publishClaimSync(ClaimSyncEventType.CLAIM_UPDATED, claim.id());
            return true;
        }
    }

    public boolean removeDeniedMember(Claim claim, UUID memberId) {
        synchronized (mutationLock) {
            if (!claim.removeDeniedMember(memberId)) {
                return false;
            }
            databaseManager.update(
                "DELETE FROM claim_blacklist WHERE claim_id = ? AND player_uuid = ?",
                statement -> {
                    statement.setInt(1, claim.id());
                    statement.setString(2, memberId.toString());
                }
            );
            publishClaimSync(ClaimSyncEventType.CLAIM_UPDATED, claim.id());
            return true;
        }
    }

    public boolean addBlacklistedMember(Claim claim, UUID memberId) {
        return addDeniedMember(claim, memberId);
    }

    public boolean removeBlacklistedMember(Claim claim, UUID memberId) {
        return removeDeniedMember(claim, memberId);
    }

    public ClaimMemberSettings memberSettings(Claim claim, UUID memberId) {
        ClaimMemberSettings settings = claim.memberSettings(memberId);
        return settings == null ? createMemberSettings(claim) : settings;
    }

    public boolean updateMemberPermission(Claim claim, UUID memberId, ClaimPermission permission, boolean allowed) {
        synchronized (mutationLock) {
            if (!claim.isTrusted(memberId)) {
                return false;
            }
            ClaimMemberSettings settings = claim.memberSettings(memberId);
            if (settings == null) {
                settings = createMemberSettings(claim);
                claim.setMemberSettings(memberId, settings);
            }
            settings.setPermission(permission, allowed);
            saveMemberSettings(claim.id(), memberId, settings);
            publishClaimSync(ClaimSyncEventType.CLAIM_UPDATED, claim.id());
            return true;
        }
    }

    public boolean transferClaim(Claim claim, UUID newOwner, String newOwnerName) {
        if (claim == null || newOwner == null) {
            return false;
        }
        if (claim.systemManaged()) {
            return false;
        }
        synchronized (mutationLock) {
            Claim targetClaim = claims.get(claim.id());
            if (targetClaim == null) {
                return false;
            }
            UUID previousOwner = targetClaim.owner();
            if (previousOwner.equals(newOwner)) {
                return false;
            }
            String targetOwnerName = newOwnerName == null || newOwnerName.isBlank() ? newOwner.toString() : newOwnerName;
            boolean transferred = databaseManager.transaction(() -> {
                int updated = databaseManager.update(
                    "UPDATE claims SET owner_uuid = ?, owner_name = ? WHERE id = ? AND owner_uuid = ?",
                    statement -> {
                        statement.setString(1, newOwner.toString());
                        statement.setString(2, targetOwnerName);
                        statement.setInt(3, targetClaim.id());
                        statement.setString(4, previousOwner.toString());
                    }
                );
                if (updated <= 0) {
                    return false;
                }
                clearClaimRelations(targetClaim.id());
                cancelSaleListing(targetClaim.id());
                return true;
            });
            if (!transferred) {
                if (databaseManager.isMySql()) {
                    refreshClaimFromDatabase(targetClaim.id());
                }
                return false;
            }
            targetClaim.setOwner(newOwner, targetOwnerName);
            targetClaim.clearTrustedMembers();
            targetClaim.clearDeniedMembers();
            targetClaim.clearMemberSettings();
            publishClaimSync(ClaimSyncEventType.CLAIM_OWNER_CHANGED, targetClaim.id());
            return true;
        }
    }

    public void cancelSaleListing(int claimId) {
        databaseManager.update(
            "DELETE FROM claim_sale_listings WHERE claim_id = ?",
            statement -> statement.setInt(1, claimId)
        );
    }

    public void removeClaim(Claim claim) {
        synchronized (mutationLock) {
            claims.remove(claim.id());
            rebuildClaimChunkIndex();
            cancelSaleListing(claim.id());
            databaseManager.update(
                "DELETE FROM claims WHERE id = ?",
                statement -> statement.setInt(1, claim.id())
            );

            World world = isLocalClaim(claim) ? plugin.getServer().getWorld(claim.world()) : null;
            if (world != null) {
                Location coreLocation = new Location(world, claim.centerX(), claim.centerY(), claim.centerZ());
                if (coreLocation.getBlock().getType() == plugin.settings().coreMaterial()) {
                    coreLocation.getBlock().setType(Material.AIR, false);
                }
            }
            publishClaimSync(ClaimSyncEventType.CLAIM_DELETED, claim.id());
        }
    }

    private void applyClaimDefaults(Claim claim) {
        applyClaimPermissionDefaults(claim);
        applyClaimFlagDefaults(claim);
    }

    private void applyClaimPermissionDefaults(Claim claim) {
        for (ClaimPermission permission : ClaimPermission.values()) {
            claim.setPermission(permission, plugin.settings().claimPermissionDefault(permission, claim.systemManaged()));
        }
        claim.setDenyAll(false);
        databaseManager.update(
            """
            UPDATE claims SET
                allow_place = ?,
                allow_break = ?,
                allow_interact = ?,
                allow_container = ?,
                allow_redstone = ?,
                allow_explosion = ?,
                allow_bucket = ?,
                allow_teleport = ?,
                allow_flight = ?,
                deny_all = ?
            WHERE id = ?
            """,
            statement -> {
                statement.setInt(1, claim.permission(ClaimPermission.PLACE) ? 1 : 0);
                statement.setInt(2, claim.permission(ClaimPermission.BREAK) ? 1 : 0);
                statement.setInt(3, claim.permission(ClaimPermission.INTERACT) ? 1 : 0);
                statement.setInt(4, claim.permission(ClaimPermission.CONTAINER) ? 1 : 0);
                statement.setInt(5, claim.permission(ClaimPermission.REDSTONE) ? 1 : 0);
                statement.setInt(6, claim.permission(ClaimPermission.EXPLOSION) ? 1 : 0);
                statement.setInt(7, claim.permission(ClaimPermission.BUCKET) ? 1 : 0);
                statement.setInt(8, claim.permission(ClaimPermission.TELEPORT) ? 1 : 0);
                statement.setInt(9, claim.permission(ClaimPermission.FLIGHT) ? 1 : 0);
                statement.setInt(10, 0);
                statement.setInt(11, claim.id());
            }
        );
    }

    private void applyClaimFlagDefaults(Claim claim) {
        for (Map.Entry<ClaimFlag, ClaimFlagState> entry : defaultFlagStates(claim).entrySet()) {
            ClaimFlagState state = entry.getValue();
            if (state == null || state == ClaimFlagState.UNSET) {
                continue;
            }
            claim.setFlagState(entry.getKey(), state);
            databaseManager.update(
                databaseManager.insertIgnoreSql("claim_flags", "claim_id, flag_key, state", "?, ?, ?"),
                statement -> {
                    statement.setInt(1, claim.id());
                    statement.setString(2, entry.getKey().key());
                    statement.setInt(3, state.databaseValue());
                }
            );
            databaseManager.update(
                "UPDATE claim_flags SET state = ? WHERE claim_id = ? AND flag_key = ?",
                statement -> {
                    statement.setInt(1, state.databaseValue());
                    statement.setInt(2, claim.id());
                    statement.setString(3, entry.getKey().key());
                }
            );
        }
    }

    private boolean matchesPermissionDefaults(Claim claim) {
        for (ClaimPermission permission : ClaimPermission.values()) {
            if (claim.permission(permission) != plugin.settings().claimPermissionDefault(permission, claim.systemManaged())) {
                return false;
            }
        }
        return !claim.denyAll();
    }

    private boolean matchesFlagDefaults(Claim claim) {
        Map<ClaimFlag, ClaimFlagState> defaults = defaultFlagStates(claim);
        for (ClaimFlag flag : ClaimFlag.values()) {
            if (claim.flagState(flag) != defaults.getOrDefault(flag, ClaimFlagState.UNSET)) {
                return false;
            }
        }
        return true;
    }

    private Map<ClaimFlag, ClaimFlagState> defaultFlagStates(Claim claim) {
        return claim != null && claim.systemManaged()
            ? plugin.settings().systemClaimFlagDefaults()
            : plugin.settings().newClaimFlagDefaults();
    }

    private void clearClaimRelations(int claimId) {
        databaseManager.update(
            "DELETE FROM claim_members WHERE claim_id = ?",
            statement -> statement.setInt(1, claimId)
        );
        databaseManager.update(
            "DELETE FROM claim_blacklist WHERE claim_id = ?",
            statement -> statement.setInt(1, claimId)
        );
        databaseManager.update(
            "DELETE FROM claim_member_permissions WHERE claim_id = ?",
            statement -> statement.setInt(1, claimId)
        );
    }

    public void save() {
        if (databaseManager.isMySql()) {
            return;
        }
        synchronized (mutationLock) {
            for (Claim claim : claims.values()) {
                databaseManager.update(
                    databaseManager.claimUpsertSql(),
                    statement -> {
                        statement.setInt(1, claim.id());
                        statement.setString(2, claim.owner().toString());
                        statement.setString(3, claim.ownerName());
                        statement.setString(4, claim.name());
                        statement.setInt(5, claim.coreVisible() ? 1 : 0);
                        statement.setString(6, claim.world());
                        statement.setString(7, displayServerId(claim));
                        statement.setInt(8, claim.centerX());
                        statement.setInt(9, claim.centerY());
                        statement.setInt(10, claim.centerZ());
                        statement.setInt(11, claim.minY());
                        statement.setInt(12, claim.maxY());
                        statement.setInt(13, claim.fullHeight() ? 1 : 0);
                        statement.setInt(14, claim.displayRadius());
                        statement.setInt(15, claim.east());
                        statement.setInt(16, claim.south());
                        statement.setInt(17, claim.west());
                        statement.setInt(18, claim.north());
                        statement.setString(19, claim.enterMessage());
                        statement.setString(20, claim.leaveMessage());
                        statement.setInt(21, claim.permission(ClaimPermission.PLACE) ? 1 : 0);
                        statement.setInt(22, claim.permission(ClaimPermission.BREAK) ? 1 : 0);
                        statement.setInt(23, claim.permission(ClaimPermission.INTERACT) ? 1 : 0);
                        statement.setInt(24, claim.permission(ClaimPermission.CONTAINER) ? 1 : 0);
                        statement.setInt(25, claim.permission(ClaimPermission.REDSTONE) ? 1 : 0);
                        statement.setInt(26, claim.permission(ClaimPermission.EXPLOSION) ? 1 : 0);
                        statement.setInt(27, claim.permission(ClaimPermission.BUCKET) ? 1 : 0);
                        statement.setInt(28, claim.permission(ClaimPermission.TELEPORT) ? 1 : 0);
                        statement.setInt(29, claim.permission(ClaimPermission.FLIGHT) ? 1 : 0);
                        statement.setInt(30, claim.systemManaged() ? 1 : 0);
                        statement.setInt(31, claim.denyAll() ? 1 : 0);
                        if (claim.hasTeleportPoint()) {
                            statement.setDouble(32, claim.teleportX());
                            statement.setDouble(33, claim.teleportY());
                            statement.setDouble(34, claim.teleportZ());
                            statement.setDouble(35, claim.teleportYaw());
                            statement.setDouble(36, claim.teleportPitch());
                        } else {
                            statement.setNull(32, java.sql.Types.DOUBLE);
                            statement.setNull(33, java.sql.Types.DOUBLE);
                            statement.setNull(34, java.sql.Types.DOUBLE);
                            statement.setNull(35, java.sql.Types.DOUBLE);
                            statement.setNull(36, java.sql.Types.DOUBLE);
                        }
                        statement.setLong(37, claim.lastExpandedAt());
                        statement.setLong(38, claim.createdAt());
                    }
                );
                for (Map.Entry<UUID, ClaimMemberSettings> entry : claim.memberSettings().entrySet()) {
                    saveMemberSettings(claim.id(), entry.getKey(), entry.getValue());
                }
            }
        }
    }

    private Map<Integer, Claim> loadClaimsFromDatabase() {
        Map<Integer, Claim> loadedClaims = new HashMap<>();
        databaseManager.query(
            """
            SELECT id, owner_uuid, owner_name, name, core_visible, world, server_id, center_x, center_y, center_z,
                   min_y, max_y, full_height, radius, east, south, west, north, enter_message, leave_message,
                   allow_place, allow_break, allow_interact, allow_container, allow_redstone, allow_explosion, allow_bucket, allow_teleport, allow_flight,
                   system_managed, deny_all, tp_x, tp_y, tp_z, tp_yaw, tp_pitch, last_expanded_at, created_at
            FROM claims
            """,
            statement -> {
            },
            resultSet -> {
                while (resultSet.next()) {
                    Claim claim = claimFromResultSet(resultSet);
                    loadedClaims.put(claim.id(), claim);
                }
                return null;
            }
        );

        databaseManager.query(
            "SELECT claim_id, player_uuid FROM claim_members",
            statement -> {
            },
            resultSet -> {
                while (resultSet.next()) {
                    Claim claim = loadedClaims.get(resultSet.getInt("claim_id"));
                    if (claim != null) {
                        claim.addTrustedMember(UUID.fromString(resultSet.getString("player_uuid")));
                    }
                }
                return null;
            }
        );
        databaseManager.query(
            "SELECT claim_id, player_uuid FROM claim_blacklist",
            statement -> {
            },
            resultSet -> {
                while (resultSet.next()) {
                    Claim claim = loadedClaims.get(resultSet.getInt("claim_id"));
                    if (claim != null) {
                        claim.addDeniedMember(UUID.fromString(resultSet.getString("player_uuid")));
                    }
                }
                return null;
            }
        );
        databaseManager.query(
            "SELECT claim_id, flag_key, state FROM claim_flags",
            statement -> {
            },
            resultSet -> {
                while (resultSet.next()) {
                    Claim claim = loadedClaims.get(resultSet.getInt("claim_id"));
                    if (claim == null) {
                        continue;
                    }
                    ClaimFlag flag = ClaimFlag.fromKey(resultSet.getString("flag_key"));
                    if (flag == null) {
                        continue;
                    }
                    claim.setFlagState(flag, ClaimFlagState.fromDatabase(resultSet.getInt("state")));
                }
                return null;
            }
        );
        databaseManager.query(
            """
            SELECT claim_id, player_uuid, allow_place, allow_break, allow_interact, allow_container, allow_redstone, allow_explosion, allow_bucket, allow_teleport, allow_flight
            FROM claim_member_permissions
            """,
            statement -> {
            },
            resultSet -> {
                while (resultSet.next()) {
                    Claim claim = loadedClaims.get(resultSet.getInt("claim_id"));
                    if (claim == null) {
                        continue;
                    }
                    UUID playerId = UUID.fromString(resultSet.getString("player_uuid"));
                    claim.setMemberSettings(playerId, new ClaimMemberSettings(
                        resultSet.getInt("allow_place") != 0,
                        resultSet.getInt("allow_break") != 0,
                        resultSet.getInt("allow_interact") != 0,
                        resultSet.getInt("allow_container") != 0,
                        resultSet.getInt("allow_redstone") != 0,
                        resultSet.getInt("allow_explosion") != 0,
                        resultSet.getInt("allow_bucket") != 0,
                        resultSet.getInt("allow_teleport") != 0,
                        resultSet.getInt("allow_flight") != 0
                    ));
                }
                return null;
            }
        );
        return loadedClaims;
    }

    private Optional<Claim> loadClaimFromDatabase(int id) {
        Optional<Claim> loadedClaim = databaseManager.query(
            """
            SELECT id, owner_uuid, owner_name, name, core_visible, world, server_id, center_x, center_y, center_z,
                   min_y, max_y, full_height, radius, east, south, west, north, enter_message, leave_message,
                   allow_place, allow_break, allow_interact, allow_container, allow_redstone, allow_explosion, allow_bucket, allow_teleport, allow_flight,
                   system_managed, deny_all, tp_x, tp_y, tp_z, tp_yaw, tp_pitch, last_expanded_at, created_at
            FROM claims
            WHERE id = ?
            """,
            statement -> statement.setInt(1, id),
            resultSet -> resultSet.next() ? Optional.of(claimFromResultSet(resultSet)) : Optional.empty()
        );
        loadedClaim.ifPresent(this::loadClaimRelationsFromDatabase);
        return loadedClaim;
    }

    private Claim claimFromResultSet(ResultSet resultSet) throws SQLException {
        int fallbackDistance = resultSet.getInt("radius");
        int east = resultSet.getInt("east");
        int south = resultSet.getInt("south");
        int west = resultSet.getInt("west");
        int north = resultSet.getInt("north");
        return new Claim(
            resultSet.getInt("id"),
            UUID.fromString(resultSet.getString("owner_uuid")),
            resultSet.getString("owner_name"),
            resultSet.getString("name"),
            resultSet.getString("server_id"),
            resultSet.getString("world"),
            resultSet.getInt("center_x"),
            resultSet.getInt("center_y"),
            resultSet.getInt("center_z"),
            resultSet.getInt("min_y"),
            resultSet.getInt("max_y"),
            resultSet.getInt("full_height") != 0,
            east <= 0 ? fallbackDistance : east,
            south <= 0 ? fallbackDistance : south,
            west <= 0 ? fallbackDistance : west,
            north <= 0 ? fallbackDistance : north,
            resultSet.getLong("created_at"),
            resultSet.getInt("core_visible") == 1,
            resultSet.getString("enter_message"),
            resultSet.getString("leave_message"),
            resultSet.getInt("allow_place") != 0,
            resultSet.getInt("allow_break") != 0,
            resultSet.getInt("allow_interact") != 0,
            resultSet.getInt("allow_container") != 0,
            resultSet.getInt("allow_redstone") != 0,
            resultSet.getInt("allow_explosion") != 0,
            resultSet.getInt("allow_bucket") != 0,
            resultSet.getInt("allow_teleport") != 0,
            resultSet.getInt("allow_flight") != 0,
            resultSet.getInt("system_managed") != 0,
            resultSet.getInt("deny_all") != 0,
            nullableDouble(resultSet, "tp_x"),
            nullableDouble(resultSet, "tp_y"),
            nullableDouble(resultSet, "tp_z"),
            nullableFloat(resultSet, "tp_yaw"),
            nullableFloat(resultSet, "tp_pitch"),
            resultSet.getLong("last_expanded_at")
        );
    }

    private void loadClaimRelationsFromDatabase(Claim claim) {
        databaseManager.query(
            "SELECT player_uuid FROM claim_members WHERE claim_id = ?",
            statement -> statement.setInt(1, claim.id()),
            resultSet -> {
                while (resultSet.next()) {
                    claim.addTrustedMember(UUID.fromString(resultSet.getString("player_uuid")));
                }
                return null;
            }
        );
        databaseManager.query(
            "SELECT player_uuid FROM claim_blacklist WHERE claim_id = ?",
            statement -> statement.setInt(1, claim.id()),
            resultSet -> {
                while (resultSet.next()) {
                    claim.addDeniedMember(UUID.fromString(resultSet.getString("player_uuid")));
                }
                return null;
            }
        );
        databaseManager.query(
            "SELECT flag_key, state FROM claim_flags WHERE claim_id = ?",
            statement -> statement.setInt(1, claim.id()),
            resultSet -> {
                while (resultSet.next()) {
                    ClaimFlag flag = ClaimFlag.fromKey(resultSet.getString("flag_key"));
                    if (flag != null) {
                        claim.setFlagState(flag, ClaimFlagState.fromDatabase(resultSet.getInt("state")));
                    }
                }
                return null;
            }
        );
        databaseManager.query(
            """
            SELECT player_uuid, allow_place, allow_break, allow_interact, allow_container, allow_redstone, allow_explosion, allow_bucket, allow_teleport, allow_flight
            FROM claim_member_permissions
            WHERE claim_id = ?
            """,
            statement -> statement.setInt(1, claim.id()),
            resultSet -> {
                while (resultSet.next()) {
                    UUID playerId = UUID.fromString(resultSet.getString("player_uuid"));
                    claim.setMemberSettings(playerId, new ClaimMemberSettings(
                        resultSet.getInt("allow_place") != 0,
                        resultSet.getInt("allow_break") != 0,
                        resultSet.getInt("allow_interact") != 0,
                        resultSet.getInt("allow_container") != 0,
                        resultSet.getInt("allow_redstone") != 0,
                        resultSet.getInt("allow_explosion") != 0,
                        resultSet.getInt("allow_bucket") != 0,
                        resultSet.getInt("allow_teleport") != 0,
                        resultSet.getInt("allow_flight") != 0
                    ));
                }
                return null;
            }
        );
    }

    private ClaimMemberSettings createMemberSettings(Claim claim) {
        return new ClaimMemberSettings(
            claim.permission(ClaimPermission.PLACE),
            claim.permission(ClaimPermission.BREAK),
            claim.permission(ClaimPermission.INTERACT),
            claim.permission(ClaimPermission.CONTAINER),
            claim.permission(ClaimPermission.REDSTONE),
            claim.permission(ClaimPermission.EXPLOSION),
            claim.permission(ClaimPermission.BUCKET),
            claim.permission(ClaimPermission.TELEPORT),
            claim.permission(ClaimPermission.FLIGHT)
        );
    }

    private void saveMemberSettings(int claimId, UUID memberId, ClaimMemberSettings settings) {
        databaseManager.update(
            databaseManager.memberSettingsUpsertSql(),
            statement -> {
                statement.setInt(1, claimId);
                statement.setString(2, memberId.toString());
                statement.setInt(3, settings.permission(ClaimPermission.PLACE) ? 1 : 0);
                statement.setInt(4, settings.permission(ClaimPermission.BREAK) ? 1 : 0);
                statement.setInt(5, settings.permission(ClaimPermission.INTERACT) ? 1 : 0);
                statement.setInt(6, settings.permission(ClaimPermission.CONTAINER) ? 1 : 0);
                statement.setInt(7, settings.permission(ClaimPermission.REDSTONE) ? 1 : 0);
                statement.setInt(8, settings.permission(ClaimPermission.EXPLOSION) ? 1 : 0);
                statement.setInt(9, settings.permission(ClaimPermission.BUCKET) ? 1 : 0);
                statement.setInt(10, settings.permission(ClaimPermission.TELEPORT) ? 1 : 0);
                statement.setInt(11, settings.permission(ClaimPermission.FLIGHT) ? 1 : 0);
            }
        );
    }

    private Claim snapshotClaim(Claim claim) {
        if (claim == null) {
            return null;
        }
        Claim snapshot = new Claim(
            claim.id(),
            claim.owner(),
            claim.ownerName(),
            claim.name(),
            claim.serverId(),
            claim.world(),
            claim.centerX(),
            claim.centerY(),
            claim.centerZ(),
            claim.minY(),
            claim.maxY(),
            claim.fullHeight(),
            claim.east(),
            claim.south(),
            claim.west(),
            claim.north(),
            claim.createdAt(),
            claim.coreVisible(),
            claim.enterMessage(),
            claim.leaveMessage(),
            claim.permission(ClaimPermission.PLACE),
            claim.permission(ClaimPermission.BREAK),
            claim.permission(ClaimPermission.INTERACT),
            claim.permission(ClaimPermission.CONTAINER),
            claim.permission(ClaimPermission.REDSTONE),
            claim.permission(ClaimPermission.EXPLOSION),
            claim.permission(ClaimPermission.BUCKET),
            claim.permission(ClaimPermission.TELEPORT),
            claim.permission(ClaimPermission.FLIGHT),
            claim.systemManaged(),
            claim.denyAll(),
            claim.teleportX(),
            claim.teleportY(),
            claim.teleportZ(),
            claim.teleportYaw(),
            claim.teleportPitch(),
            claim.lastExpandedAt()
        );
        for (UUID memberId : claim.trustedMembers()) {
            snapshot.addTrustedMember(memberId);
        }
        for (UUID memberId : claim.deniedMembers()) {
            snapshot.addDeniedMember(memberId);
        }
        for (Map.Entry<ClaimFlag, ClaimFlagState> entry : claim.flagStates().entrySet()) {
            snapshot.setFlagState(entry.getKey(), entry.getValue());
        }
        for (Map.Entry<UUID, ClaimMemberSettings> entry : claim.memberSettings().entrySet()) {
            ClaimMemberSettings settings = entry.getValue();
            snapshot.setMemberSettings(entry.getKey(), new ClaimMemberSettings(
                settings.permission(ClaimPermission.PLACE),
                settings.permission(ClaimPermission.BREAK),
                settings.permission(ClaimPermission.INTERACT),
                settings.permission(ClaimPermission.CONTAINER),
                settings.permission(ClaimPermission.REDSTONE),
                settings.permission(ClaimPermission.EXPLOSION),
                settings.permission(ClaimPermission.BUCKET),
                settings.permission(ClaimPermission.TELEPORT),
                settings.permission(ClaimPermission.FLIGHT)
            ));
        }
        return snapshot;
    }

    private Double nullableDouble(ResultSet resultSet, String column) throws SQLException {
        double value = resultSet.getDouble(column);
        return resultSet.wasNull() ? null : value;
    }

    private Float nullableFloat(ResultSet resultSet, String column) throws SQLException {
        double value = resultSet.getDouble(column);
        return resultSet.wasNull() ? null : (float) value;
    }

    private void publishClaimSync(ClaimSyncEventType eventType, int claimId) {
        if (!databaseManager.isMySql()) {
            return;
        }
        try {
            claimSyncPublisher.publish(eventType, claimId);
        } catch (RuntimeException exception) {
            plugin.getLogger().warning("Failed to publish claim sync event " + eventType.wireName()
                + " for claim " + claimId + ": " + exception.getMessage());
        }
    }

    private String validateAvailableClaimName(String rawName, Integer excludedClaimId) {
        String sanitizedName = sanitizeClaimName(rawName);
        String normalizedName = normalizeClaimName(sanitizedName);
        if (normalizedName == null) {
            throw new IllegalArgumentException("claim-name-empty");
        }
        if (isClaimNameTaken(normalizedName, excludedClaimId)) {
            throw new IllegalArgumentException("claim-name-exists");
        }
        return sanitizedName;
    }

    private String normalizeClaimName(String rawName) {
        String sanitizedName = sanitizeClaimName(rawName);
        if (sanitizedName == null) {
            return null;
        }
        return sanitizedName.toLowerCase(Locale.ROOT);
    }

    private String sanitizeClaimName(String rawName) {
        if (rawName == null) {
            return null;
        }
        String trimmed = rawName.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        return trimmed.replaceAll("\\s+", " ");
    }

    private void rebuildClaimChunkIndex() {
        Map<String, Map<Long, List<Claim>>> rebuilt = new HashMap<>();
        for (Claim claim : claims.values()) {
            if (!isLocalClaim(claim)) {
                continue;
            }
            Map<Long, List<Claim>> worldIndex = rebuilt.computeIfAbsent(claim.world(), ignored -> new HashMap<>());
            int minChunkX = claim.minX() >> 4;
            int maxChunkX = claim.maxX() >> 4;
            int minChunkZ = claim.minZ() >> 4;
            int maxChunkZ = claim.maxZ() >> 4;
            for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
                for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
                    worldIndex.computeIfAbsent(chunkKey(chunkX, chunkZ), ignored -> new ArrayList<>()).add(claim);
                }
            }
        }

        Map<String, Map<Long, List<Claim>>> finalized = new HashMap<>();
        for (Map.Entry<String, Map<Long, List<Claim>>> worldEntry : rebuilt.entrySet()) {
            Map<Long, List<Claim>> buckets = new HashMap<>();
            for (Map.Entry<Long, List<Claim>> bucketEntry : worldEntry.getValue().entrySet()) {
                List<Claim> candidates = bucketEntry.getValue();
                candidates.sort(Comparator.comparingLong(Claim::area));
                buckets.put(bucketEntry.getKey(), List.copyOf(candidates));
            }
            finalized.put(worldEntry.getKey(), Map.copyOf(buckets));
        }
        claimChunkIndex = Map.copyOf(finalized);
    }

    private long chunkKey(int chunkX, int chunkZ) {
        return (((long) chunkX) << 32) ^ (chunkZ & 0xffffffffL);
    }

    private void backfillMissingServerIds() {
        if (databaseManager.isMySql()) {
            return;
        }
        databaseManager.update(
            "UPDATE claims SET server_id = ? WHERE " + MISSING_SERVER_ID_CONDITION,
            statement -> statement.setString(1, currentServerId())
        );
    }

    public record ClaimRefreshResult(Claim previousClaim, Claim currentClaim) {
    }

    public record TeleportTarget(
        String world,
        double x,
        double y,
        double z,
        float yaw,
        float pitch,
        boolean custom
    ) {
    }
}
