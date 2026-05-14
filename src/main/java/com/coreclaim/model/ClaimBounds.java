package com.coreclaim.model;

final class ClaimBounds {

    private final int centerX;
    private final int centerZ;
    private int east;
    private int south;
    private int west;
    private int north;

    ClaimBounds(int centerX, int centerZ, int east, int south, int west, int north) {
        this.centerX = centerX;
        this.centerZ = centerZ;
        this.east = east;
        this.south = south;
        this.west = west;
        this.north = north;
    }

    int east() {
        return east;
    }

    int south() {
        return south;
    }

    int west() {
        return west;
    }

    int north() {
        return north;
    }

    void set(int east, int south, int west, int north) {
        this.east = east;
        this.south = south;
        this.west = west;
        this.north = north;
    }

    int minX() {
        return centerX - west;
    }

    int maxX() {
        return centerX + east;
    }

    int minZ() {
        return centerZ - north;
    }

    int maxZ() {
        return centerZ + south;
    }

    int width() {
        return east + west + 1;
    }

    int depth() {
        return north + south + 1;
    }

    long area() {
        return (long) width() * depth();
    }

    int displayRadius() {
        return Math.max(Math.max(east, west), Math.max(north, south));
    }

    int distance(ClaimDirection direction) {
        if (direction == ClaimDirection.EAST) {
            return east;
        }
        if (direction == ClaimDirection.SOUTH) {
            return south;
        }
        if (direction == ClaimDirection.WEST) {
            return west;
        }
        if (direction == ClaimDirection.NORTH) {
            return north;
        }
        return north;
    }
}
