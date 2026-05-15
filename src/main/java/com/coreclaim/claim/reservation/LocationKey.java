package com.coreclaim.claim.reservation;

import org.bukkit.Location;

public record LocationKey(String world, int x, int y, int z) {

    public static LocationKey from(Location location) {
        if (location == null || location.getWorld() == null) {
            throw new IllegalArgumentException("world-missing");
        }
        return new LocationKey(location.getWorld().getName(), location.getBlockX(), location.getBlockY(), location.getBlockZ());
    }
}
