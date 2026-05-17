package com.coreclaim.service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.Location;

public final class ExplosionAuthorizationService {

    private static final long AUTHORIZATION_WINDOW_MILLIS = 5000L;

    private final Map<String, Long> authorizedOrigins = new ConcurrentHashMap<>();
    private final long authorizationWindowMillis;

    public ExplosionAuthorizationService() {
        this(AUTHORIZATION_WINDOW_MILLIS);
    }

    ExplosionAuthorizationService(long authorizationWindowMillis) {
        this.authorizationWindowMillis = Math.max(0L, authorizationWindowMillis);
    }

    public void authorize(Location location) {
        if (location == null || location.getWorld() == null) {
            return;
        }
        authorize(location.getWorld().getName(), location.getBlockX(), location.getBlockY(), location.getBlockZ());
    }

    void authorize(String world, int x, int y, int z) {
        if (world == null || world.isBlank()) {
            return;
        }
        authorizedOrigins.put(key(world, x, y, z), System.currentTimeMillis() + authorizationWindowMillis);
    }

    public boolean isAuthorized(Location location) {
        if (location == null || location.getWorld() == null) {
            return false;
        }
        long now = System.currentTimeMillis();
        removeExpired(now);
        String key = key(location);
        Long expiresAt = authorizedOrigins.get(key);
        if (expiresAt == null) {
            return false;
        }
        return expiresAt > now;
    }

    public boolean isAuthorizedNearby(Location location, int radius) {
        if (location == null || location.getWorld() == null) {
            return false;
        }
        long now = System.currentTimeMillis();
        removeExpired(now);
        int resolvedRadius = Math.max(0, radius);
        return isAuthorizedNearby(
            location.getWorld().getName(),
            location.getBlockX(),
            location.getBlockY(),
            location.getBlockZ(),
            resolvedRadius
        );
    }

    boolean isAuthorizedNearby(String world, int blockX, int blockY, int blockZ, int radius) {
        if (world == null || world.isBlank()) {
            return false;
        }
        long now = System.currentTimeMillis();
        removeExpired(now);
        int resolvedRadius = Math.max(0, radius);
        int minX = blockX - resolvedRadius;
        int maxX = blockX + resolvedRadius;
        int minY = blockY - resolvedRadius;
        int maxY = blockY + resolvedRadius;
        int minZ = blockZ - resolvedRadius;
        int maxZ = blockZ + resolvedRadius;
        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    Long expiresAt = authorizedOrigins.get(key(world, x, y, z));
                    if (expiresAt != null && expiresAt > now) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private String key(Location location) {
        return key(location.getWorld().getName(), location.getBlockX(), location.getBlockY(), location.getBlockZ());
    }

    private String key(String world, int x, int y, int z) {
        return world + ":" + x + ":" + y + ":" + z;
    }

    private void removeExpired(long now) {
        authorizedOrigins.entrySet().removeIf(entry -> entry.getValue() <= now);
    }
}
