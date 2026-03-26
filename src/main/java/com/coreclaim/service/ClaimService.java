package com.coreclaim.service;

import com.coreclaim.CoreClaimPlugin;
import com.coreclaim.model.Claim;
import com.coreclaim.model.ClaimMemberSettings;
import com.coreclaim.model.ClaimPermission;
import com.coreclaim.storage.DatabaseManager;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;

public final class ClaimService {

    private final CoreClaimPlugin plugin;
    private final DatabaseManager databaseManager;
    private final ProfileService profileService;
    private final Map<Integer, Claim> claims = new ConcurrentHashMap<>();
    private final Object mutationLock = new Object();

    public ClaimService(CoreClaimPlugin plugin, DatabaseManager databaseManager, ProfileService profileService) {
        this.plugin = plugin;
        this.databaseManager = databaseManager;
        this.profileService = profileService;
        load();
    }

    public Optional<Claim> findClaim(Location location) {
        return claims.values().stream()
            .filter(claim -> claim.contains(location))
            .min(Comparator.comparingLong(Claim::area));
    }

    public Optional<Claim> findClaimById(int id) {
        return Optional.ofNullable(claims.get(id));
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

    public boolean overlaps(String world, int minX, int maxX, int minZ, int maxZ, Integer ignoredId) {
        for (Claim claim : claims.values()) {
            if (claim.overlaps(world, minX, maxX, minZ, maxZ, ignoredId)) {
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

    public boolean canAccess(Claim claim, UUID playerId) {
        return claim.canAccess(playerId) || profileService.isGloballyTrusted(claim.owner(), playerId);
    }

    public boolean hasPermission(Claim claim, UUID playerId, ClaimPermission permission) {
        if (claim.owner().equals(playerId)) {
            return true;
        }
        if (claim.isTrusted(playerId)) {
            return claim.memberPermission(playerId, permission, claim.permission(permission));
        }
        return profileService.isGloballyTrusted(claim.owner(), playerId) && claim.permission(permission);
    }

    public Claim createClaim(UUID owner, String ownerName, String name, Location center, int initialDistance) {
        synchronized (mutationLock) {
            long createdAt = Instant.now().getEpochSecond();
            int generatedId = (int) databaseManager.insertAndReturnKey(
                """
                INSERT INTO claims (
                    owner_uuid, owner_name, name, core_visible, world, center_x, center_y, center_z,
                    radius, east, south, west, north, enter_message, leave_message,
                    allow_place, allow_break, allow_interact, allow_bucket, allow_teleport, last_expanded_at, created_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                statement -> {
                    statement.setString(1, owner.toString());
                    statement.setString(2, ownerName);
                    statement.setString(3, name);
                    statement.setInt(4, 1);
                    statement.setString(5, center.getWorld().getName());
                    statement.setInt(6, center.getBlockX());
                    statement.setInt(7, center.getBlockY());
                    statement.setInt(8, center.getBlockZ());
                    statement.setInt(9, initialDistance);
                    statement.setInt(10, initialDistance);
                    statement.setInt(11, initialDistance);
                    statement.setInt(12, initialDistance);
                    statement.setInt(13, initialDistance);
                    statement.setString(14, "");
                    statement.setString(15, "");
                    statement.setInt(16, 0);
                    statement.setInt(17, 0);
                    statement.setInt(18, 0);
                    statement.setInt(19, 0);
                    statement.setInt(20, 0);
                    statement.setLong(21, 0L);
                    statement.setLong(22, createdAt);
                }
            );

            Claim claim = new Claim(
                generatedId,
                owner,
                ownerName,
                name,
                center.getWorld().getName(),
                center.getBlockX(),
                center.getBlockY(),
                center.getBlockZ(),
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
                0L
            );
            claims.put(claim.id(), claim);
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
            claim.setName(name);
            databaseManager.update(
                "UPDATE claims SET name = ? WHERE id = ?",
                statement -> {
                    statement.setString(1, name);
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
                case BUCKET -> "allow_bucket";
                case TELEPORT -> "allow_teleport";
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
                "INSERT OR IGNORE INTO claim_members (claim_id, player_uuid) VALUES (?, ?)",
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

    public void removeClaim(Claim claim) {
        synchronized (mutationLock) {
            claims.remove(claim.id());
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

    public void save() {
        synchronized (mutationLock) {
            for (Claim claim : claims.values()) {
                databaseManager.update(
                    """
                    INSERT INTO claims (
                        id, owner_uuid, owner_name, name, core_visible, world, center_x, center_y, center_z,
                        radius, east, south, west, north, enter_message, leave_message,
                        allow_place, allow_break, allow_interact, allow_bucket, allow_teleport, last_expanded_at, created_at
                    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    ON CONFLICT(id) DO UPDATE SET
                        owner_uuid = excluded.owner_uuid,
                        owner_name = excluded.owner_name,
                        name = excluded.name,
                        core_visible = excluded.core_visible,
                        world = excluded.world,
                        center_x = excluded.center_x,
                        center_y = excluded.center_y,
                        center_z = excluded.center_z,
                        radius = excluded.radius,
                        east = excluded.east,
                        south = excluded.south,
                        west = excluded.west,
                        north = excluded.north,
                        enter_message = excluded.enter_message,
                        leave_message = excluded.leave_message,
                        allow_place = excluded.allow_place,
                        allow_break = excluded.allow_break,
                        allow_interact = excluded.allow_interact,
                        allow_bucket = excluded.allow_bucket,
                        allow_teleport = excluded.allow_teleport,
                        last_expanded_at = excluded.last_expanded_at,
                        created_at = excluded.created_at
                    """,
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
                        statement.setInt(10, claim.displayRadius());
                        statement.setInt(11, claim.east());
                        statement.setInt(12, claim.south());
                        statement.setInt(13, claim.west());
                        statement.setInt(14, claim.north());
                        statement.setString(15, claim.enterMessage());
                        statement.setString(16, claim.leaveMessage());
                        statement.setInt(17, claim.permission(ClaimPermission.PLACE) ? 1 : 0);
                        statement.setInt(18, claim.permission(ClaimPermission.BREAK) ? 1 : 0);
                        statement.setInt(19, claim.permission(ClaimPermission.INTERACT) ? 1 : 0);
                        statement.setInt(20, claim.permission(ClaimPermission.BUCKET) ? 1 : 0);
                        statement.setInt(21, claim.permission(ClaimPermission.TELEPORT) ? 1 : 0);
                        statement.setLong(22, claim.lastExpandedAt());
                        statement.setLong(23, claim.createdAt());
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
                   radius, east, south, west, north, enter_message, leave_message,
                   allow_place, allow_break, allow_interact, allow_bucket, allow_teleport, last_expanded_at, created_at
            FROM claims
            """,
            statement -> {
            },
            resultSet -> {
                while (resultSet.next()) {
                    int fallbackDistance = resultSet.getInt("radius");
                    int east = resultSet.getInt("east");
                    int south = resultSet.getInt("south");
                    int west = resultSet.getInt("west");
                    int north = resultSet.getInt("north");
                    Claim claim = new Claim(
                        resultSet.getInt("id"),
                        UUID.fromString(resultSet.getString("owner_uuid")),
                        resultSet.getString("owner_name"),
                        resultSet.getString("name"),
                        resultSet.getString("world"),
                        resultSet.getInt("center_x"),
                        resultSet.getInt("center_y"),
                        resultSet.getInt("center_z"),
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
                        resultSet.getInt("allow_bucket") != 0,
                        resultSet.getInt("allow_teleport") != 0,
                        resultSet.getLong("last_expanded_at")
                    );
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
            """
            SELECT claim_id, player_uuid, allow_place, allow_break, allow_interact, allow_bucket, allow_teleport
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
                        resultSet.getInt("allow_bucket") != 0,
                        resultSet.getInt("allow_teleport") != 0
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
            claim.permission(ClaimPermission.BUCKET),
            claim.permission(ClaimPermission.TELEPORT)
        );
    }

    private void saveMemberSettings(int claimId, UUID memberId, ClaimMemberSettings settings) {
        databaseManager.update(
            """
            INSERT INTO claim_member_permissions (
                claim_id, player_uuid, allow_place, allow_break, allow_interact, allow_bucket, allow_teleport
            ) VALUES (?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT(claim_id, player_uuid) DO UPDATE SET
                allow_place = excluded.allow_place,
                allow_break = excluded.allow_break,
                allow_interact = excluded.allow_interact,
                allow_bucket = excluded.allow_bucket,
                allow_teleport = excluded.allow_teleport
            """,
            statement -> {
                statement.setInt(1, claimId);
                statement.setString(2, memberId.toString());
                statement.setInt(3, settings.permission(ClaimPermission.PLACE) ? 1 : 0);
                statement.setInt(4, settings.permission(ClaimPermission.BREAK) ? 1 : 0);
                statement.setInt(5, settings.permission(ClaimPermission.INTERACT) ? 1 : 0);
                statement.setInt(6, settings.permission(ClaimPermission.BUCKET) ? 1 : 0);
                statement.setInt(7, settings.permission(ClaimPermission.TELEPORT) ? 1 : 0);
            }
        );
    }
}
