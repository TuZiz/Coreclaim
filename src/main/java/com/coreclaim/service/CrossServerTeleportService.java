package com.coreclaim.service;

import com.coreclaim.CoreClaimPlugin;
import com.coreclaim.model.Claim;
import com.coreclaim.storage.DatabaseManager;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.time.Instant;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;

public final class CrossServerTeleportService {

    private static final String PLUGIN_CHANNEL = "BungeeCord";
    private static final String TABLE_NAME = "pending_cross_server_teleports";

    private final CoreClaimPlugin plugin;
    private final DatabaseManager databaseManager;
    private final ClaimService claimService;
    private final ClaimVisualService claimVisualService;

    public CrossServerTeleportService(
        CoreClaimPlugin plugin,
        DatabaseManager databaseManager,
        ClaimService claimService,
        ClaimVisualService claimVisualService
    ) {
        this.plugin = plugin;
        this.databaseManager = databaseManager;
        this.claimService = claimService;
        this.claimVisualService = claimVisualService;
        initializeStorage();
        validateConfiguration();
    }

    public boolean transferToRemoteClaim(Player player, Claim claim) {
        if (!plugin.settings().crossServerTeleportEnabled()) {
            return false;
        }
        if (isCurrentServerIdMisconfigured()) {
            logTransferWarning(
                "Cross-server teleport blocked because the current server-id is not configured.",
                player,
                claim,
                null
            );
            player.sendMessage(message(
                "cross-server-teleport-server-id-misconfigured",
                "&c&l! &7当前服的跨服路由未配置完成，请先设置唯一的 &eserver-id&7。"
            ));
            return true;
        }
        String targetServer = claimService.effectiveServerId(claim);
        logTransferInfo("Cross-server teleport request", player, claim, targetServer);
        if (targetServer == null) {
            logTransferWarning("Cross-server teleport blocked because claim server_id is missing.", player, claim, null);
            player.sendMessage(message(
                "cross-server-teleport-unmapped",
                "&c&l! &7领地 &e{name} &7缺少有效的 &bserver_id&7，无法判断目标区服。请管理员执行 &f/claim admin setserver <claimId> <serverId>&7。",
                "{server}", claimService.displayServerId(claim),
                "{world}", claim.world(),
                "{name}", claim.name()
            ));
            return true;
        }
        if (plugin.settings().isCurrentServer(targetServer)) {
            return false;
        }

        purgeExpired();
        PendingTeleport pendingTeleport = PendingTeleport.fromClaim(claimService.teleportTarget(claim, player.getLocation().getYaw(), player.getLocation().getPitch()), claim, targetServer);
        savePendingTeleport(player.getUniqueId().toString(), pendingTeleport);
        player.sendMessage(message(
            "cross-server-teleport-start",
            "&6&l跨区传送: &7正在把你传送到区服 &e{server} &7中的领地 &b{name}&7。",
            "{name}", claim.name(),
            "{server}", targetServer
        ));
        try {
            sendConnect(player, targetServer);
        } catch (IllegalStateException exception) {
            clearPendingTeleport(player.getUniqueId().toString());
            player.sendMessage(message(
                "cross-server-teleport-connect-failed",
                "&c&l! &7无法把你切换到目标区服 &e{server}&7，请检查代理转发是否正常。",
                "{server}", targetServer
            ));
            logTransferWarning(
                "Failed to send BungeeCord connect payload: " + exception.getMessage(),
                player,
                claim,
                targetServer
            );
            return true;
        }
        return true;
    }

    public void reloadSettings() {
        validateConfiguration();
    }

    public void consumePendingTeleport(Player player) {
        if (player == null || !player.isOnline()) {
            return;
        }
        purgeExpired();
        PendingTeleport pendingTeleport = findPendingTeleport(player.getUniqueId().toString());
        if (pendingTeleport == null) {
            return;
        }
        if (!plugin.settings().isCurrentServer(pendingTeleport.targetServer())) {
            return;
        }

        World world = Bukkit.getWorld(pendingTeleport.targetWorld());
        if (world == null) {
            clearPendingTeleport(player.getUniqueId().toString());
            plugin.getLogger().warning(
                "Cross-server teleport landing failed because the target world is unavailable."
                    + " player=" + player.getName()
                    + ", claimId=" + pendingTeleport.claimId()
                    + ", claimName=" + pendingTeleport.claimName()
                    + ", currentServerId=" + plugin.settings().serverId()
                    + ", targetServer=" + pendingTeleport.targetServer()
                    + ", targetWorld=" + pendingTeleport.targetWorld()
            );
            player.sendMessage(message(
                "cross-server-teleport-world-missing",
                "&c&l! &7目标世界 &e{world} &7在当前区服仍未加载，跨区落点失败。",
                "{world}", pendingTeleport.targetWorld()
            ));
            return;
        }

        clearPendingTeleport(player.getUniqueId().toString());
        Location destination = new Location(
            world,
            pendingTeleport.targetX(),
            pendingTeleport.targetY(),
            pendingTeleport.targetZ(),
            pendingTeleport.targetYaw(),
            pendingTeleport.targetPitch()
        );
        player.teleport(destination);

        Claim claim = claimService.findClaimByIdFresh(pendingTeleport.claimId())
            .orElseGet(() -> claimService.findClaim(destination).orElse(null));
        if (claim != null) {
            claimVisualService.showClaim(player, claim);
        }
        player.sendMessage(message(
            "claim-teleported",
            "&a&l传送: &7已抵达领地 &e{name}&7。",
            "{name}", pendingTeleport.claimName()
        ));
    }

    private void validateConfiguration() {
        if (!plugin.settings().crossServerTeleportEnabled()) {
            return;
        }
        if (isCurrentServerIdMisconfigured()) {
            plugin.getLogger().warning(
                "Cross-server teleport is enabled, but this server is still using the default server-id '"
                    + plugin.settings().serverId()
                    + "'. Set a unique server-id on every backend before using shared claim teleports."
            );
        }
        if (hasLegacyWorldServerMapConfigured()) {
            plugin.getLogger().warning(
                "cross-server-teleport legacy world-server-map is configured, but runtime routing now only uses claim.server_id."
                    + " Use /claim admin setserver <claimId> <serverId> to repair old claims."
            );
        }
    }

    private void initializeStorage() {
        databaseManager.update(
            """
            CREATE TABLE IF NOT EXISTS %s (
                player_uuid %s PRIMARY KEY,
                target_server %s NOT NULL,
                target_world %s NOT NULL,
                target_x %s NOT NULL,
                target_y %s NOT NULL,
                target_z %s NOT NULL,
                target_yaw %s NOT NULL,
                target_pitch %s NOT NULL,
                claim_id %s NOT NULL,
                claim_name %s NOT NULL,
                created_at %s NOT NULL
            )%s
            """.formatted(
                TABLE_NAME,
                uuidType(),
                shortTextType(),
                worldType(),
                doubleType(),
                doubleType(),
                doubleType(),
                doubleType(),
                doubleType(),
                integerType(),
                shortTextType(),
                longType(),
                tableOptions()
            ),
            statement -> {
            }
        );
        databaseManager.ensureColumn(TABLE_NAME, "target_server", shortTextType() + " NOT NULL DEFAULT ''");
        databaseManager.ensureColumn(TABLE_NAME, "target_yaw", doubleType() + " NOT NULL DEFAULT 0");
        databaseManager.ensureColumn(TABLE_NAME, "target_pitch", doubleType() + " NOT NULL DEFAULT 0");
    }

    private void savePendingTeleport(String playerId, PendingTeleport pendingTeleport) {
        databaseManager.update(pendingTeleportUpsertSql(), statement -> {
            statement.setString(1, playerId);
            statement.setString(2, pendingTeleport.targetServer());
            statement.setString(3, pendingTeleport.targetWorld());
            statement.setDouble(4, pendingTeleport.targetX());
            statement.setDouble(5, pendingTeleport.targetY());
            statement.setDouble(6, pendingTeleport.targetZ());
            statement.setDouble(7, pendingTeleport.targetYaw());
            statement.setDouble(8, pendingTeleport.targetPitch());
            statement.setInt(9, pendingTeleport.claimId());
            statement.setString(10, pendingTeleport.claimName());
            statement.setLong(11, pendingTeleport.createdAt());
        });
    }

    private PendingTeleport findPendingTeleport(String playerId) {
        return databaseManager.query(
            "SELECT target_server, target_world, target_x, target_y, target_z, target_yaw, target_pitch, claim_id, claim_name, created_at FROM " + TABLE_NAME + " WHERE player_uuid = ?",
            statement -> statement.setString(1, playerId),
            resultSet -> resultSet.next()
                ? new PendingTeleport(
                    resultSet.getString("target_server"),
                    resultSet.getString("target_world"),
                    resultSet.getDouble("target_x"),
                    resultSet.getDouble("target_y"),
                    resultSet.getDouble("target_z"),
                    (float) resultSet.getDouble("target_yaw"),
                    (float) resultSet.getDouble("target_pitch"),
                    resultSet.getInt("claim_id"),
                    resultSet.getString("claim_name"),
                    resultSet.getLong("created_at")
                )
                : null
        );
    }

    private void clearPendingTeleport(String playerId) {
        databaseManager.update(
            "DELETE FROM " + TABLE_NAME + " WHERE player_uuid = ?",
            statement -> statement.setString(1, playerId)
        );
    }

    private void purgeExpired() {
        long cutoff = Instant.now().getEpochSecond() - plugin.settings().crossServerTeleportPendingTimeoutSeconds();
        databaseManager.update(
            "DELETE FROM " + TABLE_NAME + " WHERE created_at < ?",
            statement -> statement.setLong(1, cutoff)
        );
    }

    private void sendConnect(Player player, String targetServer) {
        try {
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            try (DataOutputStream output = new DataOutputStream(buffer)) {
                output.writeUTF("Connect");
                output.writeUTF(targetServer);
            }
            player.sendPluginMessage(plugin, PLUGIN_CHANNEL, buffer.toByteArray());
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to write BungeeCord connect payload.", exception);
        }
    }

    private String pendingTeleportUpsertSql() {
        if (databaseManager.isMySql()) {
            return """
                INSERT INTO pending_cross_server_teleports (
                    player_uuid, target_server, target_world, target_x, target_y, target_z, target_yaw, target_pitch, claim_id, claim_name, created_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON DUPLICATE KEY UPDATE
                    target_server = VALUES(target_server),
                    target_world = VALUES(target_world),
                    target_x = VALUES(target_x),
                    target_y = VALUES(target_y),
                    target_z = VALUES(target_z),
                    target_yaw = VALUES(target_yaw),
                    target_pitch = VALUES(target_pitch),
                    claim_id = VALUES(claim_id),
                    claim_name = VALUES(claim_name),
                    created_at = VALUES(created_at)
                """;
        }
        return """
            INSERT INTO pending_cross_server_teleports (
                player_uuid, target_server, target_world, target_x, target_y, target_z, target_yaw, target_pitch, claim_id, claim_name, created_at
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT(player_uuid) DO UPDATE SET
                target_server = excluded.target_server,
                target_world = excluded.target_world,
                target_x = excluded.target_x,
                target_y = excluded.target_y,
                target_z = excluded.target_z,
                target_yaw = excluded.target_yaw,
                target_pitch = excluded.target_pitch,
                claim_id = excluded.claim_id,
                claim_name = excluded.claim_name,
                created_at = excluded.created_at
            """;
    }

    private String uuidType() {
        return databaseManager.isMySql() ? "VARCHAR(36)" : "TEXT";
    }

    private String worldType() {
        return databaseManager.isMySql() ? "VARCHAR(128)" : "TEXT";
    }

    private String shortTextType() {
        return databaseManager.isMySql() ? "VARCHAR(128)" : "TEXT";
    }

    private String integerType() {
        return databaseManager.isMySql() ? "INT" : "INTEGER";
    }

    private String longType() {
        return databaseManager.isMySql() ? "BIGINT" : "INTEGER";
    }

    private String doubleType() {
        return databaseManager.isMySql() ? "DOUBLE" : "REAL";
    }

    private String tableOptions() {
        return databaseManager.isMySql() ? " ENGINE=InnoDB DEFAULT CHARSET=utf8mb4" : "";
    }

    private boolean isCurrentServerIdMisconfigured() {
        String currentServerId = plugin.settings().serverId();
        return currentServerId == null || currentServerId.isBlank() || "local".equalsIgnoreCase(currentServerId.trim());
    }

    private boolean hasLegacyWorldServerMapConfigured() {
        return hasConfiguredSection("cross-server-teleport.legacy-world-server-map")
            || hasConfiguredSection("cross-server-teleport.world-server-map");
    }

    private boolean hasConfiguredSection(String path) {
        if (plugin.getConfig().getConfigurationSection(path) == null) {
            return false;
        }
        return !plugin.getConfig().getConfigurationSection(path).getKeys(false).isEmpty();
    }

    private void logTransferInfo(String message, Player player, Claim claim, String targetServer) {
        plugin.getLogger().info(buildTransferLog(message, player, claim, targetServer));
    }

    private void logTransferWarning(String message, Player player, Claim claim, String targetServer) {
        plugin.getLogger().warning(buildTransferLog(message, player, claim, targetServer));
    }

    private String buildTransferLog(String message, Player player, Claim claim, String targetServer) {
        return message
            + " player=" + player.getName()
            + ", claimId=" + claim.id()
            + ", claimName=" + claim.name()
            + ", claimServerId=" + claim.serverId()
            + ", currentServerId=" + plugin.settings().serverId()
            + ", targetServer=" + (targetServer == null || targetServer.isBlank() ? "<none>" : targetServer);
    }

    private String message(String path, String fallback, String... replacements) {
        String prefix = plugin.messagesConfig().getString("prefix", "&6[CoreClaim] &f");
        String formatted = plugin.messagesConfig().getString(path, fallback);
        for (int index = 0; index + 1 < replacements.length; index += 2) {
            formatted = formatted.replace(replacements[index], replacements[index + 1]);
        }
        return plugin.color(prefix + formatted);
    }

    private record PendingTeleport(
        String targetServer,
        String targetWorld,
        double targetX,
        double targetY,
        double targetZ,
        float targetYaw,
        float targetPitch,
        int claimId,
        String claimName,
        long createdAt
    ) {
        private static PendingTeleport fromClaim(ClaimService.TeleportTarget target, Claim claim, String targetServer) {
            return new PendingTeleport(
                targetServer,
                target.world(),
                target.x(),
                target.y(),
                target.z(),
                target.yaw(),
                target.pitch(),
                claim.id(),
                claim.name(),
                Instant.now().getEpochSecond()
            );
        }
    }
}
