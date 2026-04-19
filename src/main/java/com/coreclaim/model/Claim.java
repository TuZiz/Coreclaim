package com.coreclaim.model;

import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.bukkit.Location;

public final class Claim {

    private final int id;
    private volatile UUID owner;
    private volatile String ownerName;
    private final String serverId;
    private final String world;
    private final int centerX;
    private final int centerY;
    private final int centerZ;
    private final int minY;
    private final int maxY;
    private final boolean fullHeight;
    private final long createdAt;
    private final Set<UUID> trustedMembers = new LinkedHashSet<>();
    private final Set<UUID> blacklistedMembers = new LinkedHashSet<>();
    private final Map<UUID, ClaimMemberSettings> memberSettings = new LinkedHashMap<>();
    private final EnumMap<ClaimFlag, ClaimFlagState> flagStates = new EnumMap<>(ClaimFlag.class);
    private String name;
    private boolean coreVisible;
    private int east;
    private int south;
    private int west;
    private int north;
    private String enterMessage;
    private String leaveMessage;
    private boolean allowPlace;
    private boolean allowBreak;
    private boolean allowInteract;
    private boolean allowContainer;
    private boolean allowRedstone;
    private boolean allowExplosion;
    private boolean allowBucket;
    private boolean allowTeleport;
    private boolean allowFlight;
    private final boolean systemManaged;
    private boolean denyAll;
    private Double teleportX;
    private Double teleportY;
    private Double teleportZ;
    private Float teleportYaw;
    private Float teleportPitch;
    private long lastExpandedAt;

    public Claim(
        int id,
        UUID owner,
        String ownerName,
        String name,
        String serverId,
        String world,
        int centerX,
        int centerY,
        int centerZ,
        int minY,
        int maxY,
        boolean fullHeight,
        int east,
        int south,
        int west,
        int north,
        long createdAt,
        boolean coreVisible,
        String enterMessage,
        String leaveMessage,
        boolean allowPlace,
        boolean allowBreak,
        boolean allowInteract,
        boolean allowContainer,
        boolean allowRedstone,
        boolean allowExplosion,
        boolean allowBucket,
        boolean allowTeleport,
        boolean allowFlight,
        boolean systemManaged,
        boolean denyAll,
        Double teleportX,
        Double teleportY,
        Double teleportZ,
        Float teleportYaw,
        Float teleportPitch,
        long lastExpandedAt
    ) {
        this.id = id;
        this.owner = owner;
        this.ownerName = ownerName;
        this.name = name;
        this.serverId = serverId == null ? "" : serverId;
        this.world = world;
        this.centerX = centerX;
        this.centerY = centerY;
        this.centerZ = centerZ;
        this.minY = Math.min(minY, maxY);
        this.maxY = Math.max(minY, maxY);
        this.fullHeight = fullHeight;
        this.east = east;
        this.south = south;
        this.west = west;
        this.north = north;
        this.createdAt = createdAt;
        this.coreVisible = coreVisible;
        this.enterMessage = enterMessage == null ? "" : enterMessage;
        this.leaveMessage = leaveMessage == null ? "" : leaveMessage;
        this.allowPlace = allowPlace;
        this.allowBreak = allowBreak;
        this.allowInteract = allowInteract;
        this.allowContainer = allowContainer;
        this.allowRedstone = allowRedstone;
        this.allowExplosion = allowExplosion;
        this.allowBucket = allowBucket;
        this.allowTeleport = allowTeleport;
        this.allowFlight = allowFlight;
        this.systemManaged = systemManaged;
        this.denyAll = denyAll;
        this.teleportX = teleportX;
        this.teleportY = teleportY;
        this.teleportZ = teleportZ;
        this.teleportYaw = teleportYaw;
        this.teleportPitch = teleportPitch;
        this.lastExpandedAt = Math.max(0L, lastExpandedAt);
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

    public synchronized void setOwner(UUID owner, String ownerName) {
        if (owner == null) {
            throw new IllegalArgumentException("owner");
        }
        this.owner = owner;
        this.ownerName = ownerName == null || ownerName.isBlank() ? owner.toString() : ownerName;
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

    public String serverId() {
        return serverId;
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

    public int minY() {
        return minY;
    }

    public int maxY() {
        return maxY;
    }

    public boolean fullHeight() {
        return fullHeight;
    }

    public int height() {
        return Math.max(1, maxY - minY + 1);
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
        return owner.equals(playerId) || (trustedMembers.contains(playerId) && !blacklistedMembers.contains(playerId));
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

    public synchronized void clearTrustedMembers() {
        trustedMembers.clear();
    }

    public synchronized int trustedCount() {
        return trustedMembers.size();
    }

    public synchronized boolean addDeniedMember(UUID playerId) {
        if (owner.equals(playerId)) {
            return false;
        }
        return blacklistedMembers.add(playerId);
    }

    public synchronized boolean removeDeniedMember(UUID playerId) {
        return blacklistedMembers.remove(playerId);
    }

    public synchronized boolean isDenied(UUID playerId) {
        return blacklistedMembers.contains(playerId);
    }

    public synchronized Set<UUID> deniedMembers() {
        return Collections.unmodifiableSet(new LinkedHashSet<>(blacklistedMembers));
    }

    public synchronized void clearDeniedMembers() {
        blacklistedMembers.clear();
    }

    public synchronized boolean denyAll() {
        return denyAll;
    }

    public boolean systemManaged() {
        return systemManaged;
    }

    public synchronized void setDenyAll(boolean denyAll) {
        this.denyAll = denyAll;
    }

    public synchronized boolean hasTeleportPoint() {
        return teleportX != null && teleportY != null && teleportZ != null;
    }

    public synchronized Double teleportX() {
        return teleportX;
    }

    public synchronized Double teleportY() {
        return teleportY;
    }

    public synchronized Double teleportZ() {
        return teleportZ;
    }

    public synchronized Float teleportYaw() {
        return teleportYaw;
    }

    public synchronized Float teleportPitch() {
        return teleportPitch;
    }

    public synchronized void setTeleportPoint(double x, double y, double z, float yaw, float pitch) {
        this.teleportX = x;
        this.teleportY = y;
        this.teleportZ = z;
        this.teleportYaw = yaw;
        this.teleportPitch = pitch;
    }

    public synchronized void clearTeleportPoint() {
        teleportX = null;
        teleportY = null;
        teleportZ = null;
        teleportYaw = null;
        teleportPitch = null;
    }

    public synchronized ClaimFlagState flagState(ClaimFlag flag) {
        return flagStates.getOrDefault(flag, ClaimFlagState.UNSET);
    }

    public synchronized void setFlagState(ClaimFlag flag, ClaimFlagState state) {
        if (flag == null) {
            return;
        }
        if (state == null || state == ClaimFlagState.UNSET) {
            flagStates.remove(flag);
            return;
        }
        flagStates.put(flag, state);
    }

    public synchronized void clearFlagState(ClaimFlag flag) {
        if (flag == null) {
            return;
        }
        flagStates.remove(flag);
    }

    public synchronized Map<ClaimFlag, ClaimFlagState> flagStates() {
        return Collections.unmodifiableMap(new EnumMap<>(flagStates));
    }

    public synchronized ClaimMemberSettings memberSettings(UUID playerId) {
        return memberSettings.get(playerId);
    }

    public synchronized void setMemberSettings(UUID playerId, ClaimMemberSettings settings) {
        if (playerId == null || settings == null) {
            return;
        }
        memberSettings.put(playerId, settings);
    }

    public synchronized void removeMemberSettings(UUID playerId) {
        memberSettings.remove(playerId);
    }

    public synchronized Map<UUID, ClaimMemberSettings> memberSettings() {
        return Collections.unmodifiableMap(new LinkedHashMap<>(memberSettings));
    }

    public synchronized void clearMemberSettings() {
        memberSettings.clear();
    }

    public synchronized boolean memberPermission(UUID playerId, ClaimPermission permission, boolean fallback) {
        ClaimMemberSettings settings = memberSettings.get(playerId);
        return settings == null ? fallback : settings.permission(permission);
    }

    public synchronized String enterMessage() {
        return enterMessage;
    }

    public synchronized void setEnterMessage(String enterMessage) {
        this.enterMessage = enterMessage == null ? "" : enterMessage;
    }

    public synchronized String leaveMessage() {
        return leaveMessage;
    }

    public synchronized void setLeaveMessage(String leaveMessage) {
        this.leaveMessage = leaveMessage == null ? "" : leaveMessage;
    }

    public synchronized boolean permission(ClaimPermission permission) {
        if (permission == ClaimPermission.PLACE) {
            return allowPlace;
        }
        if (permission == ClaimPermission.BREAK) {
            return allowBreak;
        }
        if (permission == ClaimPermission.INTERACT) {
            return allowInteract;
        }
        if (permission == ClaimPermission.CONTAINER) {
            return allowContainer;
        }
        if (permission == ClaimPermission.REDSTONE) {
            return allowRedstone;
        }
        if (permission == ClaimPermission.EXPLOSION) {
            return allowExplosion;
        }
        if (permission == ClaimPermission.BUCKET) {
            return allowBucket;
        }
        if (permission == ClaimPermission.TELEPORT) {
            return allowTeleport;
        }
        return allowFlight;
    }

    public synchronized void setPermission(ClaimPermission permission, boolean allowed) {
        if (permission == ClaimPermission.PLACE) {
            allowPlace = allowed;
            return;
        }
        if (permission == ClaimPermission.BREAK) {
            allowBreak = allowed;
            return;
        }
        if (permission == ClaimPermission.INTERACT) {
            allowInteract = allowed;
            return;
        }
        if (permission == ClaimPermission.CONTAINER) {
            allowContainer = allowed;
            return;
        }
        if (permission == ClaimPermission.REDSTONE) {
            allowRedstone = allowed;
            return;
        }
        if (permission == ClaimPermission.EXPLOSION) {
            allowExplosion = allowed;
            return;
        }
        if (permission == ClaimPermission.BUCKET) {
            allowBucket = allowed;
            return;
        }
        if (permission == ClaimPermission.TELEPORT) {
            allowTeleport = allowed;
            return;
        }
        allowFlight = allowed;
    }

    public synchronized long lastExpandedAt() {
        return lastExpandedAt;
    }

    public synchronized void setLastExpandedAt(long lastExpandedAt) {
        this.lastExpandedAt = Math.max(0L, lastExpandedAt);
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
        if (direction == ClaimDirection.EAST) {
            return east;
        }
        if (direction == ClaimDirection.SOUTH) {
            return south;
        }
        if (direction == ClaimDirection.WEST) {
            return west;
        }
        return north;
    }

    public boolean contains(Location location) {
        if (location == null || location.getWorld() == null) {
            return false;
        }
        if (!containsHorizontally(location)) {
            return false;
        }
        int blockX = location.getBlockX();
        int blockZ = location.getBlockZ();
        if (blockX == centerX && location.getBlockY() == centerY && blockZ == centerZ) {
            return true;
        }
        return fullHeight || (location.getBlockY() >= minY && location.getBlockY() <= maxY);
    }

    public boolean containsHorizontally(Location location) {
        if (location == null || location.getWorld() == null) {
            return false;
        }
        if (!world.equals(location.getWorld().getName())) {
            return false;
        }
        int blockX = location.getBlockX();
        int blockZ = location.getBlockZ();
        return blockX >= minX() && blockX <= maxX() && blockZ >= minZ() && blockZ <= maxZ();
    }

    public boolean overlaps(
        String targetWorld,
        int targetMinX,
        int targetMaxX,
        int targetMinY,
        int targetMaxY,
        int targetMinZ,
        int targetMaxZ,
        Integer ignoredId,
        boolean targetFullHeight
    ) {
        if (!world.equals(targetWorld)) {
            return false;
        }
        if (ignoredId != null && ignoredId == id) {
            return false;
        }
        boolean horizontalOverlap = targetMinX <= maxX()
            && targetMaxX >= minX()
            && targetMinZ <= maxZ()
            && targetMaxZ >= minZ();
        if (!horizontalOverlap) {
            return false;
        }
        return fullHeight || targetFullHeight || (targetMinY <= maxY && targetMaxY >= minY);
    }
}
