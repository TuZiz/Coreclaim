package com.coreclaim.service;

import com.coreclaim.CoreClaimPlugin;
import com.coreclaim.model.Claim;
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

    public Claim createClaim(UUID owner, String ownerName, String name, Location center, int initialDistance) {
        synchronized (mutationLock) {
            long createdAt = Instant.now().getEpochSecond();
            int generatedId = (int) databaseManager.insertAndReturnKey(
                """
                INSERT INTO claims (
                    owner_uuid, owner_name, name, core_visible, world, center_x, center_y, center_z,
                    radius, east, south, west, north, created_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
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
                    statement.setLong(14, createdAt);
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
                true
            );
            claims.put(claim.id(), claim);
            return claim;
        }
    }

    public void updateBounds(Claim claim, int east, int south, int west, int north) {
        synchronized (mutationLock) {
            claim.setBounds(east, south, west, north);
            databaseManager.update(
                "UPDATE claims SET radius = ?, east = ?, south = ?, west = ?, north = ? WHERE id = ?",
                statement -> {
                    statement.setInt(1, claim.displayRadius());
                    statement.setInt(2, east);
                    statement.setInt(3, south);
                    statement.setInt(4, west);
                    statement.setInt(5, north);
                    statement.setInt(6, claim.id());
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

    public boolean addTrustedMember(Claim claim, UUID memberId) {
        synchronized (mutationLock) {
            if (!claim.addTrustedMember(memberId)) {
                return false;
            }
            databaseManager.update(
                "INSERT OR IGNORE INTO claim_members (claim_id, player_uuid) VALUES (?, ?)",
                statement -> {
                    statement.setInt(1, claim.id());
                    statement.setString(2, memberId.toString());
                }
            );
            return true;
        }
    }

    public boolean removeTrustedMember(Claim claim, UUID memberId) {
        synchronized (mutationLock) {
            if (!claim.removeTrustedMember(memberId)) {
                return false;
            }
            databaseManager.update(
                "DELETE FROM claim_members WHERE claim_id = ? AND player_uuid = ?",
                statement -> {
                    statement.setInt(1, claim.id());
                    statement.setString(2, memberId.toString());
                }
            );
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
                        radius, east, south, west, north, created_at
                    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
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
                        statement.setLong(15, claim.createdAt());
                    }
                );
            }
        }
    }

    private void load() {
        databaseManager.query(
            """
            SELECT id, owner_uuid, owner_name, name, core_visible, world, center_x, center_y, center_z,
                   radius, east, south, west, north, created_at
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
                        resultSet.getInt("core_visible") == 1
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
    }
}
