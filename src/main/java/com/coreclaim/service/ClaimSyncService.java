package com.coreclaim.service;

import com.coreclaim.CoreClaimPlugin;
import com.coreclaim.config.PluginConfig;
import com.coreclaim.storage.DatabaseManager;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPubSub;

public final class ClaimSyncService implements ClaimSyncPublisher {

    private static final String PROTOCOL_VERSION = "v1";

    private final CoreClaimPlugin plugin;
    private final DatabaseManager databaseManager;
    private final ClaimService claimService;
    private final HologramService hologramService;
    private final String originInstanceId = UUID.randomUUID().toString();
    private final AtomicBoolean running = new AtomicBoolean(false);
    private volatile ExecutorService publishExecutor;
    private volatile Thread subscriberThread;
    private volatile JedisPubSub subscriber;

    public ClaimSyncService(
        CoreClaimPlugin plugin,
        DatabaseManager databaseManager,
        ClaimService claimService,
        HologramService hologramService
    ) {
        this.plugin = plugin;
        this.databaseManager = databaseManager;
        this.claimService = claimService;
        this.hologramService = hologramService;
    }

    public void start() {
        if (!databaseManager.isMySql()) {
            return;
        }
        PluginConfig.ClaimSyncSettings settings = plugin.settings().claimSync();
        if (!settings.enabled()) {
            plugin.getLogger().warning(
                "database.type=mysql is enabled, but claim-sync.enabled=false. "
                    + "Other servers will not see claim changes until /claim reload is used."
            );
            return;
        }
        if (!settings.usesRedis()) {
            plugin.getLogger().warning("claim-sync.transport must be redis. Claim cache sync is disabled.");
            return;
        }
        if (!running.compareAndSet(false, true)) {
            return;
        }
        publishExecutor = Executors.newSingleThreadExecutor(daemonThreadFactory("CoreClaim-Redis-Publisher"));
        startSubscriberThread();
    }

    public void reloadSettings() {
        stop();
        start();
    }

    public void stop() {
        running.set(false);
        JedisPubSub currentSubscriber = subscriber;
        if (currentSubscriber != null) {
            try {
                currentSubscriber.unsubscribe();
            } catch (RuntimeException ignored) {
            }
        }
        Thread currentThread = subscriberThread;
        if (currentThread != null) {
            currentThread.interrupt();
        }
        ExecutorService currentExecutor = publishExecutor;
        if (currentExecutor != null) {
            currentExecutor.shutdownNow();
            try {
                currentExecutor.awaitTermination(2L, TimeUnit.SECONDS);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            }
        }
        subscriber = null;
        subscriberThread = null;
        publishExecutor = null;
    }

    @Override
    public void publish(ClaimSyncEventType eventType, int claimId) {
        if (!databaseManager.isMySql() || eventType == null || !running.get()) {
            return;
        }
        ExecutorService executor = publishExecutor;
        if (executor == null || executor.isShutdown()) {
            return;
        }
        executor.execute(() -> publishPayload(encode(eventType, claimId)));
    }

    @Override
    public void publishClaimsReloaded() {
        publish(ClaimSyncEventType.CLAIMS_RELOADED, 0);
    }

    private void startSubscriberThread() {
        Thread thread = daemonThreadFactory("CoreClaim-Redis-Subscriber").newThread(this::subscriberLoop);
        subscriberThread = thread;
        thread.start();
    }

    private void subscriberLoop() {
        while (running.get()) {
            PluginConfig.ClaimSyncSettings settings = plugin.settings().claimSync();
            try (Jedis jedis = openJedis(settings)) {
                plugin.getLogger().info("Claim sync connected to Redis channel '" + settings.redisChannel() + "'.");
                scheduleFullReload("redis-sync-connected");
                JedisPubSub currentSubscriber = new JedisPubSub() {
                    @Override
                    public void onMessage(String channel, String message) {
                        handleIncomingMessage(message);
                    }
                };
                subscriber = currentSubscriber;
                jedis.subscribe(currentSubscriber, settings.redisChannel());
            } catch (RuntimeException exception) {
                if (running.get()) {
                    plugin.getLogger().warning(
                        "Claim sync Redis subscriber unavailable: " + describe(exception)
                            + ". Retrying in " + settings.reconnectSeconds() + "s; /claim reload remains available."
                    );
                    sleepReconnectDelay(settings.reconnectSeconds());
                }
            } finally {
                subscriber = null;
            }
        }
    }

    private void handleIncomingMessage(String message) {
        ClaimSyncMessage syncMessage = decode(message);
        if (syncMessage == null || originInstanceId.equals(syncMessage.originInstanceId())) {
            return;
        }
        if (syncMessage.eventType() == ClaimSyncEventType.CLAIMS_RELOADED) {
            scheduleFullReload("redis-event");
            return;
        }
        if (syncMessage.claimId() <= 0) {
            return;
        }
        plugin.platformScheduler().runLater(() -> {
            ClaimService.ClaimRefreshResult refreshed = claimService.reloadClaim(syncMessage.claimId());
            hologramService.reconcileLocalClaimArtifacts(refreshed.previousClaim(), refreshed.currentClaim(), claimService);
        }, 1L);
    }

    private void scheduleFullReload(String reason) {
        plugin.platformScheduler().runLater(() -> {
            int count = claimService.reloadClaims();
            hologramService.refreshAll(claimService);
            plugin.getLogger().info("Claim sync reloaded " + count + " claims from database. reason=" + reason);
        }, 1L);
    }

    private void publishPayload(String payload) {
        PluginConfig.ClaimSyncSettings settings = plugin.settings().claimSync();
        try (Jedis jedis = openJedis(settings)) {
            jedis.publish(settings.redisChannel(), payload);
        } catch (RuntimeException exception) {
            plugin.getLogger().warning("Failed to publish claim sync event: " + describe(exception));
        }
    }

    private Jedis openJedis(PluginConfig.ClaimSyncSettings settings) {
        Jedis jedis = new Jedis(settings.redisHost(), settings.redisPort());
        if (settings.hasRedisPassword()) {
            jedis.auth(settings.redisPassword());
        }
        if (settings.redisDatabase() > 0) {
            jedis.select(settings.redisDatabase());
        }
        return jedis;
    }

    private String encode(ClaimSyncEventType eventType, int claimId) {
        return PROTOCOL_VERSION
            + "|" + eventType.wireName()
            + "|" + claimId
            + "|" + originInstanceId
            + "|" + Instant.now().toEpochMilli();
    }

    private ClaimSyncMessage decode(String payload) {
        if (payload == null || payload.isBlank()) {
            return null;
        }
        String[] parts = payload.split("\\|", -1);
        if (parts.length != 5 || !PROTOCOL_VERSION.equals(parts[0])) {
            return null;
        }
        ClaimSyncEventType eventType = ClaimSyncEventType.fromWireName(parts[1]);
        if (eventType == null) {
            return null;
        }
        try {
            return new ClaimSyncMessage(eventType, Integer.parseInt(parts[2]), parts[3]);
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private void sleepReconnectDelay(int seconds) {
        try {
            Thread.sleep(Math.max(1, seconds) * 1000L);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
    }

    private ThreadFactory daemonThreadFactory(String name) {
        return runnable -> {
            Thread thread = new Thread(runnable, name);
            thread.setDaemon(true);
            return thread;
        };
    }

    private String describe(Throwable throwable) {
        Throwable cause = throwable;
        while (cause.getCause() != null) {
            cause = cause.getCause();
        }
        String message = cause.getMessage();
        return message == null || message.isBlank() ? cause.getClass().getSimpleName() : cause.getClass().getSimpleName() + ": " + message;
    }

    private record ClaimSyncMessage(ClaimSyncEventType eventType, int claimId, String originInstanceId) {
    }
}
