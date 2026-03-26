package com.coreclaim.model;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;
import org.bukkit.Location;

public final class Claim {

    private final int id;
    private final UUID owner;
    private final String ownerName;
    private final String world;
    private final int centerX;
    private final int centerY;
    private final int centerZ;
    private final long createdAt;
    private final Set<UUID> trustedMembers = new LinkedHashSet<>();
    private String name;
    private boolean coreVisible;
    private int east;
    private int south;
    private int west;
    private int north;

    public Claim(
        int id,
        UUID owner,
        String ownerName,
        String name,
        String world,
        int centerX,
        int centerY,
        int centerZ,
        int east,
        int south,
        int west,
        int north,
        long createdAt,
        boolean coreVisible
    ) {
        this.id = id;
        this.owner = owner;
        this.ownerName = ownerName;
        this.name = name;
        this.world = world;
        this.centerX = centerX;
        this.centerY = centerY;
        this.centerZ = centerZ;
        this.east = east;
        this.south = south;
        this.west = west;
        this.north = north;
        this.createdAt = createdAt;
        this.coreVisible = coreVisible;
    }

    public int id() {
        return id;
    }

    public UUID owner() {
        return owner;
    }

    public String ownerName() {
        return ownerName;
    }

    public synchronized String name() {
        return name;
    }

    public synchronized void setName(String name) {
        this.name = name;
    }

    public synchronized boolean coreVisible() {
        return coreVisible;
    }

    public synchronized void setCoreVisible(boolean coreVisible) {
        this.coreVisible = coreVisible;
    }

    public String world() {
        return world;
    }

    public int centerX() {
        return centerX;
    }

    public int centerY() {
        return centerY;
    }

    public int centerZ() {
        return centerZ;
    }

    public synchronized int east() {
        return east;
    }

    public synchronized int south() {
        return south;
    }

    public synchronized int west() {
        return west;
    }

    public synchronized int north() {
        return north;
    }

    public synchronized void setBounds(int east, int south, int west, int north) {
        this.east = east;
        this.south = south;
        this.west = west;
        this.north = north;
    }

    public long createdAt() {
        return createdAt;
    }

    public synchronized boolean isOwner(UUID playerId) {
        return owner.equals(playerId);
    }

    public synchronized boolean isTrusted(UUID playerId) {
        return trustedMembers.contains(playerId);
    }

    public synchronized boolean canAccess(UUID playerId) {
        return owner.equals(playerId) || trustedMembers.contains(playerId);
    }

    public synchronized boolean addTrustedMember(UUID playerId) {
        if (owner.equals(playerId)) {
            return false;
        }
        return trustedMembers.add(playerId);
    }

    public synchronized boolean removeTrustedMember(UUID playerId) {
        return trustedMembers.remove(playerId);
    }

    public synchronized Set<UUID> trustedMembers() {
        return Collections.unmodifiableSet(new LinkedHashSet<>(trustedMembers));
    }

    public synchronized int trustedCount() {
        return trustedMembers.size();
    }

    public synchronized int minX() {
        return centerX - west;
    }

    public synchronized int maxX() {
        return centerX + east;
    }

    public synchronized int minZ() {
        return centerZ - north;
    }

    public synchronized int maxZ() {
        return centerZ + south;
    }

    public synchronized int width() {
        return east + west + 1;
    }

    public synchronized int depth() {
        return north + south + 1;
    }

    public synchronized long area() {
        return (long) width() * depth();
    }

    public synchronized int displayRadius() {
        return Math.max(Math.max(east, west), Math.max(north, south));
    }

    public synchronized int distance(ClaimDirection direction) {
        return switch (direction) {
            case EAST -> east;
            case SOUTH -> south;
            case WEST -> west;
            case NORTH -> north;
        };
    }

    public boolean contains(Location location) {
        if (location == null || location.getWorld() == null) {
            return false;
        }
        if (!world.equals(location.getWorld().getName())) {
            return false;
        }
        int blockX = location.getBlockX();
        int blockZ = location.getBlockZ();
        return blockX >= minX() && blockX <= maxX()
            && blockZ >= minZ() && blockZ <= maxZ();
    }

    public boolean overlaps(String targetWorld, int targetMinX, int targetMaxX, int targetMinZ, int targetMaxZ, Integer ignoredId) {
        if (!world.equals(targetWorld)) {
            return false;
        }
        if (ignoredId != null && ignoredId == id) {
            return false;
        }
        return targetMinX <= maxX()
            && targetMaxX >= minX()
            && targetMinZ <= maxZ()
            && targetMaxZ >= minZ();
    }
}
