package com.coreclaim.model;

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
    private final ClaimMembers members = new ClaimMembers();
    private final ClaimFlagStates flags = new ClaimFlagStates();
    private final ClaimPermissionState permissionState;
    private final ClaimTeleportPoint teleportPoint;
    private final ClaimBounds bounds;
    private String name;
    private boolean coreVisible;
    private String enterMessage;
    private String leaveMessage;
    private final boolean systemManaged;
    private boolean denyAll;
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
        this.bounds = new ClaimBounds(centerX, centerZ, east, south, west, north);
        this.createdAt = createdAt;
        this.coreVisible = coreVisible;
        this.enterMessage = enterMessage == null ? "" : enterMessage;
        this.leaveMessage = leaveMessage == null ? "" : leaveMessage;
        this.permissionState = new ClaimPermissionState(
            allowPlace,
            allowBreak,
            allowInteract,
            allowContainer,
            allowRedstone,
            allowExplosion,
            allowBucket,
            allowTeleport,
            allowFlight
        );
        this.systemManaged = systemManaged;
        this.denyAll = denyAll;
        this.teleportPoint = new ClaimTeleportPoint(teleportX, teleportY, teleportZ, teleportYaw, teleportPitch);
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
        return bounds.east();
    }

    public synchronized int south() {
        return bounds.south();
    }

    public synchronized int west() {
        return bounds.west();
    }

    public synchronized int north() {
        return bounds.north();
    }

    public synchronized void setBounds(int east, int south, int west, int north) {
        bounds.set(east, south, west, north);
    }

    public long createdAt() {
        return createdAt;
    }

    public synchronized boolean isOwner(UUID playerId) {
        return owner.equals(playerId);
    }

    public synchronized boolean isTrusted(UUID playerId) {
        return members.isTrusted(playerId);
    }

    public synchronized boolean canAccess(UUID playerId) {
        return members.canAccess(owner, playerId);
    }

    public synchronized boolean addTrustedMember(UUID playerId) {
        return members.addTrustedMember(owner, playerId);
    }

    public synchronized boolean removeTrustedMember(UUID playerId) {
        return members.removeTrustedMember(playerId);
    }

    public synchronized Set<UUID> trustedMembers() {
        return members.trustedMembers();
    }

    public synchronized void clearTrustedMembers() {
        members.clearTrustedMembers();
    }

    public synchronized int trustedCount() {
        return members.trustedCount();
    }

    public synchronized boolean addDeniedMember(UUID playerId) {
        return members.addDeniedMember(owner, playerId);
    }

    public synchronized boolean removeDeniedMember(UUID playerId) {
        return members.removeDeniedMember(playerId);
    }

    public synchronized boolean isDenied(UUID playerId) {
        return members.isDenied(playerId);
    }

    public synchronized Set<UUID> deniedMembers() {
        return members.deniedMembers();
    }

    public synchronized void clearDeniedMembers() {
        members.clearDeniedMembers();
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
        return teleportPoint.exists();
    }

    public synchronized Double teleportX() {
        return teleportPoint.x();
    }

    public synchronized Double teleportY() {
        return teleportPoint.y();
    }

    public synchronized Double teleportZ() {
        return teleportPoint.z();
    }

    public synchronized Float teleportYaw() {
        return teleportPoint.yaw();
    }

    public synchronized Float teleportPitch() {
        return teleportPoint.pitch();
    }

    public synchronized void setTeleportPoint(double x, double y, double z, float yaw, float pitch) {
        teleportPoint.set(x, y, z, yaw, pitch);
    }

    public synchronized void clearTeleportPoint() {
        teleportPoint.clear();
    }

    public synchronized ClaimFlagState flagState(ClaimFlag flag) {
        return flags.flagState(flag);
    }

    public synchronized void setFlagState(ClaimFlag flag, ClaimFlagState state) {
        flags.setFlagState(flag, state);
    }

    public synchronized void clearFlagState(ClaimFlag flag) {
        flags.clearFlagState(flag);
    }

    public synchronized Map<ClaimFlag, ClaimFlagState> flagStates() {
        return flags.flagStates();
    }

    public synchronized ClaimMemberSettings memberSettings(UUID playerId) {
        return members.memberSettings(playerId);
    }

    public synchronized void setMemberSettings(UUID playerId, ClaimMemberSettings settings) {
        members.setMemberSettings(playerId, settings);
    }

    public synchronized void removeMemberSettings(UUID playerId) {
        members.removeMemberSettings(playerId);
    }

    public synchronized Map<UUID, ClaimMemberSettings> memberSettings() {
        return members.memberSettings();
    }

    public synchronized void clearMemberSettings() {
        members.clearMemberSettings();
    }

    public synchronized boolean memberPermission(UUID playerId, ClaimPermission permission, boolean fallback) {
        return members.memberPermission(playerId, permission, fallback);
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
        return permissionState.allowed(permission);
    }

    public synchronized void setPermission(ClaimPermission permission, boolean allowed) {
        permissionState.setAllowed(permission, allowed);
    }

    public synchronized long lastExpandedAt() {
        return lastExpandedAt;
    }

    public synchronized void setLastExpandedAt(long lastExpandedAt) {
        this.lastExpandedAt = Math.max(0L, lastExpandedAt);
    }

    public synchronized int minX() {
        return bounds.minX();
    }

    public synchronized int maxX() {
        return bounds.maxX();
    }

    public synchronized int minZ() {
        return bounds.minZ();
    }

    public synchronized int maxZ() {
        return bounds.maxZ();
    }

    public synchronized int width() {
        return bounds.width();
    }

    public synchronized int depth() {
        return bounds.depth();
    }

    public synchronized long area() {
        return bounds.area();
    }

    public synchronized int displayRadius() {
        return bounds.displayRadius();
    }

    public synchronized int distance(ClaimDirection direction) {
        return bounds.distance(direction);
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
