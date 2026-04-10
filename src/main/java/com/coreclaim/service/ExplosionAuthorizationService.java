package com.coreclaim.service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.Location;

public final class ExplosionAuthorizationService {

    private static final long AUTHORIZATION_WINDOW_MILLIS = 5000L;

    private final Map<String, Long> authorizedOrigins = new ConcurrentHashMap<>();

    public void authorize(Location location) {
        if (location == null || location.getWorld() == null) {
            return;
        }
        authorizedOrigins.put(key(location), System.currentTimeMillis() + AUTHORIZATION_WINDOW_MILLIS);
    }

    public boolean isAuthorized(Location location) {
        if (location == null || location.getWorld() == null) {
            return false;
        }
        String key = key(location);
        Long expiresAt = authorizedOrigins.get(key);
        if (expiresAt == null) {
            return false;
        }
        if (expiresAt < System.currentTimeMillis()) {
            authorizedOrigins.remove(key);
            return false;
        }
        return true;
    }

    private String key(Location location) {
        return location.getWorld().getName()
            + ":" + location.getBlockX()
            + ":" + location.getBlockY()
            + ":" + location.getBlockZ();
    }
}
