package com.coreclaim.claim.mutation;

import java.util.UUID;
import org.bukkit.Location;
import org.bukkit.World;

public record ClaimCreationRequest(
    UUID owner,
    String ownerName,
    String name,
    String world,
    int centerX,
    int centerY,
    int centerZ,
    int worldMinY,
    int worldMaxY,
    int minY,
    int maxY,
    int east,
    int south,
    int west,
    int north,
    boolean fullHeight,
    boolean systemManaged,
    ClaimCreationOptions options
) {

    public static ClaimCreationRequest core(
        UUID owner,
        String ownerName,
        String name,
        Location center,
        int initialDistance,
        ClaimCreationOptions options
    ) {
        World world = requireWorld(center);
        return new ClaimCreationRequest(
            owner,
            ownerName,
            name,
            world.getName(),
            center.getBlockX(),
            center.getBlockY(),
            center.getBlockZ(),
            world.getMinHeight(),
            world.getMaxHeight() - 1,
            world.getMinHeight(),
            world.getMaxHeight() - 1,
            initialDistance,
            initialDistance,
            initialDistance,
            initialDistance,
            true,
            false,
            options
        );
    }

    public static ClaimCreationRequest bounds(
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
        boolean systemManaged,
        ClaimCreationOptions options
    ) {
        World world = requireWorld(coreLocation);
        return new ClaimCreationRequest(
            owner,
            ownerName,
            name,
            world.getName(),
            coreLocation.getBlockX(),
            coreLocation.getBlockY(),
            coreLocation.getBlockZ(),
            world.getMinHeight(),
            world.getMaxHeight() - 1,
            minY,
            maxY,
            east,
            south,
            west,
            north,
            false,
            systemManaged,
            options
        );
    }

    public int minX() {
        return centerX - west;
    }

    public int maxX() {
        return centerX + east;
    }

    public int minZ() {
        return centerZ - north;
    }

    public int maxZ() {
        return centerZ + south;
    }

    private static World requireWorld(Location location) {
        if (location == null || location.getWorld() == null) {
            throw new IllegalArgumentException("world-missing");
        }
        return location.getWorld();
    }
}
