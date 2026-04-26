package com.coreclaim.model;

final class ClaimTeleportPoint {

    private Double x;
    private Double y;
    private Double z;
    private Float yaw;
    private Float pitch;

    ClaimTeleportPoint(Double x, Double y, Double z, Float yaw, Float pitch) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.yaw = yaw;
        this.pitch = pitch;
    }

    boolean exists() {
        return x != null && y != null && z != null;
    }

    Double x() {
        return x;
    }

    Double y() {
        return y;
    }

    Double z() {
        return z;
    }

    Float yaw() {
        return yaw;
    }

    Float pitch() {
        return pitch;
    }

    void set(double x, double y, double z, float yaw, float pitch) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.yaw = yaw;
        this.pitch = pitch;
    }

    void clear() {
        x = null;
        y = null;
        z = null;
        yaw = null;
        pitch = null;
    }
}
