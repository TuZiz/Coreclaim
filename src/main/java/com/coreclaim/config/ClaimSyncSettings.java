package com.coreclaim.config;

public record ClaimSyncSettings(
    boolean enabled,
    String transport,
    String redisHost,
    int redisPort,
    String redisPassword,
    int redisDatabase,
    String redisChannel,
    int reconnectSeconds
) {
    public boolean usesRedis() {
        return "redis".equalsIgnoreCase(transport);
    }

    public boolean hasRedisPassword() {
        return redisPassword != null && !redisPassword.isBlank();
    }
}
