package com.coreclaim.service;

import com.coreclaim.CoreClaimPlugin;
import com.coreclaim.model.Claim;
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
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;

public final class ClaimService {

    private final CoreClaimPlugin plugin;
    private final DatabaseManager databaseManager;
    private final ProfileService profileService;
    private final Map<Integer, Claim> claims = new ConcurrentHashMap<>();
    private volatile Map<String, Map<Long, List<Claim>>> claimChunkIndex = Map.of();
    private final Object mutationLock = new Object();

    public ClaimService(CoreClaimPlugin plugin, DatabaseManager databaseManager, ProfileService profileService) {
        this.plugin = plugin;
        this.databaseManager = databaseManager;
        this.profileService = profileService;
        load();
    }

    public Optional<Claim> findClaim(Location location) {
        if (location == null || location.getWorld() == null) {
            return Optional.empty();
        }
        Map<Long, List<Claim>> worldIndex = claimChunkIndex.get(location.getWorld().getName());
        if (worldIndex == null || worldIndex.isEmpty()) {
            return Optional.empty();
        }
        List<Claim> candidates = worldIndex.get(chunkKey(location.getBlockX() >> 4, location.getBlockZ() >> 4));
        if (candidates == null || candidates.isEmpty()) {
            return Optional.empty();
        }
        return candidates.stream()
            .filter(claim -> claim.contains(location))
            .min(Comparator.comparingLong(Claim::area));
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

    public Optional<Claim> refreshClaimFromDatabase(int id) {
        synchronized (mutationLock) {
            Optional<Claim> loadedClaim = loadClaimFromDatabase(id);
            Claim previousClaim = claims.get(id);
            if (loadedClaim.isPresent()) {
                claims.put(id, loadedClaim.get());
                rebuildClaimChunkIndex();
                return loadedClaim;
            }
            if (previousClaim != null) {
                claims.remove(id);
                rebuildClaimChunkIndex();
            }
            return Optional.empty();
        }
    }

    public List<Claim> claimsOf(UUID owner) {
        List<Claim> result = new ArrayList<>();
        for (Claim claim : claims.values()) {
            if (claim.owner().equals(owner)) {
                result.add(claim);
            }
        }
        result.sort(Comparator.comparingInt(Claim::id));
        return result;
    }

    public int countClaims(UUID owner) {
        return claimsOf(owner).size();
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

    public boolean isClaimNameTaken(String rawName) {
        return isClaimNameTaken(rawName, null);
    }

    public boolean isClaimNameTaken(String rawName, Integer excludedClaimId) {
        String normalizedName = normalizeClaimName(rawName);
        if (normalizedName == null) {
            return false;
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
            if (claim.overlaps(world, minX, maxX, minY, maxY, minZ, maxZ, ignoredId, fullHeight)) {
                return true;
            }
        }
        return false;
    }

    public boolean hasCoreWithinSpacing(String world, int centerX, int centerZ, int spacing, Integer ignoredId) {
        for (Claim claim : claims.values()) {
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
        if (claim.isBlacklisted(playerId)) {
            return false;
        }
        return claim.canAccess(playerId) || profileService.isGloballyTrusted(claim.owner(), playerId);
    }

    public boolean hasPermission(Claim claim, UUID playerId, ClaimPermission permission) {
        if (claim.owner().equals(playerId)) {
            return true;
        }
        if (claim.isBlacklisted(playerId)) {
            return false;
        }
        if (claim.isTrusted(playerId)) {
            return claim.memberPermission(playerId, permission, claim.permission(permission));
        }
        if (profileService.isGloballyTrusted(claim.owner(), playerId)) {
            return claim.permission(permission);
        }
        return false;
    }

    public Claim createClaim(UUID owner, String ownerName, String name, Location center, int initialDistance) {
        synchronized (mutationLock) {
            String sanitizedName = validateAvailableClaimName(name, null);
            int minY = center.getWorld() == null ? -64 : center.getWorld().getMinHeight();
            int maxY = center.getWorld() == null ? 319 : center.getWorld().getMaxHeight() - 1;
            long createdAt = Instant.now().getEpochSecond();
            int generatedId = (int) databaseManager.insertAndReturnKey(
                """
                INSERT INTO claims (
                    owner_uuid, owner_name, name, core_visible, world, center_x, center_y, center_z,
                    min_y, max_y, full_height, radius, east, south, west, north, enter_message, leave_message,
                    allow_place, allow_break, allow_interact, allow_container, allow_redstone, allow_explosion, allow_bucket, allow_teleport, allow_flight, last_expanded_at, created_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                statement -> {
                    statement.setString(1, owner.toString());
                    statement.setString(2, ownerName);
                    statement.setString(3, sanitizedName);
                    statement.setInt(4, 1);
                    statement.setString(5, center.getWorld().getName());
                    statement.setInt(6, center.getBlockX());
                    statement.setInt(7, center.getBlockY());
                    statement.setInt(8, center.getBlockZ());
                    statement.setInt(9, minY);
                    statement.setInt(10, maxY);
                    statement.setInt(11, 1);
                    statement.setInt(12, initialDistance);
                    statement.setInt(13, initialDistance);
                    statement.setInt(14, initialDistance);
                    statement.setInt(15, initialDistance);
                    statement.setInt(16, initialDistance);
                    statement.setString(17, "");
                    statement.setString(18, "");
                    statement.setInt(19, 0);
                    statement.setInt(20, 0);
                    statement.setInt(21, 0);
                    statement.setInt(22, 0);
                    statement.setInt(23, 0);
                    statement.setInt(24, 0);
                    statement.setInt(25, 0);
                    statement.setInt(26, 0);
                    statement.setInt(27, 1);
                    statement.setLong(28, 0L);
                    statement.setLong(29, createdAt);
                }
            );

            Claim claim = new Claim(
                generatedId,
                owner,
                ownerName,
                sanitizedName,
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
                0L
            );
            claims.put(claim.id(), claim);
            rebuildClaimChunkIndex();
            return claim;
        }
    }

    public Claim createClaimFromBounds(UUID owner, String ownerName, String name, Location coreLocation, int minY, int maxY, int east, int south, int west, int north) {
        synchronized (mutationLock) {
            String sanitizedName = validateAvailableClaimName(name, null);
            long createdAt = Instant.now().getEpochSecond();
            int generatedId = (int) databaseManager.insertAndReturnKey(
                """
                INSERT INTO claims (
                    owner_uuid, owner_name, name, core_visible, world, center_x, center_y, center_z,
                    min_y, max_y, full_height, radius, east, south, west, north, enter_message, leave_message,
                    allow_place, allow_break, allow_interact, allow_container, allow_redstone, allow_explosion, allow_bucket, allow_teleport, allow_flight, last_expanded_at, created_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                statement -> {
                    statement.setString(1, owner.toString());
                    statement.setString(2, ownerName);
                    statement.setString(3, sanitizedName);
                    statement.setInt(4, 1);
                    statement.setString(5, coreLocation.getWorld().getName());
                    statement.setInt(6, coreLocation.getBlockX());
                    statement.setInt(7, coreLocation.getBlockY());
                    statement.setInt(8, coreLocation.getBlockZ());
                    statement.setInt(9, minY);
                    statement.setInt(10, maxY);
                    statement.setInt(11, 0);
                    statement.setInt(12, Math.max(Math.max(east, west), Math.max(south, north)));
                    statement.setInt(13, east);
                    statement.setInt(14, south);
                    statement.setInt(15, west);
                    statement.setInt(16, north);
                    statement.setString(17, "");
                    statement.setString(18, "");
                    statement.setInt(19, 0);
                    statement.setInt(20, 0);
                    statement.setInt(21, 0);
                    statement.setInt(22, 0);
                    statement.setInt(23, 0);
                    statement.setInt(24, 0);
                    statement.setInt(25, 0);
                    statement.setInt(26, 0);
                    statement.setInt(27, 1);
                    statement.setLong(28, 0L);
                    statement.setLong(29, createdAt);
                }
            );

            Claim claim = new Claim(
                generatedId,
                owner,
                ownerName,
                sanitizedName,
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
                0L
            );
            claims.put(claim.id(), claim);
            rebuildClaimChunkIndex();
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
        }
    }

    public boolean addTrustedMember(Claim claim, UUID memberId) {
        synchronized (mutationLock) {
            if (!claim.addTrustedMember(memberId)) {
                return false;
            }
            ClaimMemberSettings settings = createMemberSettings(claim);
            claim.setMemberSettings(memberId, settings);
            databaseManager.update(
                databaseManager.insertIgnoreSql("claim_members", "claim_id, player_uuid", "?, ?"),
                statement -> {
                    statement.setInt(1, claim.id());
                    statement.setString(2, memberId.toString());
                }
            );
            saveMemberSettings(claim.id(), memberId, settings);
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
            return true;
        }
    }

    public boolean addBlacklistedMember(Claim claim, UUID memberId) {
        synchronized (mutationLock) {
            if (!claim.addBlacklistedMember(memberId)) {
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
            return true;
        }
    }

    public boolean removeBlacklistedMember(Claim claim, UUID memberId) {
        synchronized (mutationLock) {
            if (!claim.removeBlacklistedMember(memberId)) {
                return false;
            }
            databaseManager.update(
                "DELETE FROM claim_blacklist WHERE claim_id = ? AND player_uuid = ?",
                statement -> {
                    statement.setInt(1, claim.id());
                    statement.setString(2, memberId.toString());
                }
            );
            return true;
        }
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
            return true;
        }
    }

    public boolean transferClaim(Claim claim, UUID newOwner, String newOwnerName) {
        if (claim == null || newOwner == null) {
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
            targetClaim.clearBlacklistedMembers();
            targetClaim.clearMemberSettings();
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

            World world = plugin.getServer().getWorld(claim.world());
            if (world != null) {
                Location coreLocation = new Location(world, claim.centerX(), claim.centerY(), claim.centerZ());
                if (coreLocation.getBlock().getType() == plugin.settings().coreMaterial()) {
                    coreLocation.getBlock().setType(Material.AIR, false);
                }
            }
        }
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
                        statement.setInt(7, claim.centerX());
                        statement.setInt(8, claim.centerY());
                        statement.setInt(9, claim.centerZ());
                        statement.setInt(10, claim.minY());
                        statement.setInt(11, claim.maxY());
                        statement.setInt(12, claim.fullHeight() ? 1 : 0);
                        statement.setInt(13, claim.displayRadius());
                        statement.setInt(14, claim.east());
                        statement.setInt(15, claim.south());
                        statement.setInt(16, claim.west());
                        statement.setInt(17, claim.north());
                        statement.setString(18, claim.enterMessage());
                        statement.setString(19, claim.leaveMessage());
                        statement.setInt(20, claim.permission(ClaimPermission.PLACE) ? 1 : 0);
                        statement.setInt(21, claim.permission(ClaimPermission.BREAK) ? 1 : 0);
                        statement.setInt(22, claim.permission(ClaimPermission.INTERACT) ? 1 : 0);
                        statement.setInt(23, claim.permission(ClaimPermission.CONTAINER) ? 1 : 0);
                        statement.setInt(24, claim.permission(ClaimPermission.REDSTONE) ? 1 : 0);
                        statement.setInt(25, claim.permission(ClaimPermission.EXPLOSION) ? 1 : 0);
                        statement.setInt(26, claim.permission(ClaimPermission.BUCKET) ? 1 : 0);
                        statement.setInt(27, claim.permission(ClaimPermission.TELEPORT) ? 1 : 0);
                        statement.setInt(28, claim.permission(ClaimPermission.FLIGHT) ? 1 : 0);
                        statement.setLong(29, claim.lastExpandedAt());
                        statement.setLong(30, claim.createdAt());
                    }
                );
                for (Map.Entry<UUID, ClaimMemberSettings> entry : claim.memberSettings().entrySet()) {
                    saveMemberSettings(claim.id(), entry.getKey(), entry.getValue());
                }
            }
        }
    }

    private void load() {
        databaseManager.query(
            """
            SELECT id, owner_uuid, owner_name, name, core_visible, world, center_x, center_y, center_z,
                   min_y, max_y, full_height, radius, east, south, west, north, enter_message, leave_message,
                   allow_place, allow_break, allow_interact, allow_container, allow_redstone, allow_explosion, allow_bucket, allow_teleport, allow_flight, last_expanded_at, created_at
            FROM claims
            """,
            statement -> {
            },
            resultSet -> {
                while (resultSet.next()) {
                    Claim claim = claimFromResultSet(resultSet);
                    claims.put(claim.id(), claim);
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
                    Claim claim = claims.get(resultSet.getInt("claim_id"));
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
                    Claim claim = claims.get(resultSet.getInt("claim_id"));
                    if (claim != null) {
                        claim.addBlacklistedMember(UUID.fromString(resultSet.getString("player_uuid")));
                    }
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
                    Claim claim = claims.get(resultSet.getInt("claim_id"));
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
        rebuildClaimChunkIndex();
    }

    private Optional<Claim> loadClaimFromDatabase(int id) {
        Optional<Claim> loadedClaim = databaseManager.query(
            """
            SELECT id, owner_uuid, owner_name, name, core_visible, world, center_x, center_y, center_z,
                   min_y, max_y, full_height, radius, east, south, west, north, enter_message, leave_message,
                   allow_place, allow_break, allow_interact, allow_container, allow_redstone, allow_explosion, allow_bucket, allow_teleport, allow_flight, last_expanded_at, created_at
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
                    claim.addBlacklistedMember(UUID.fromString(resultSet.getString("player_uuid")));
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
}
