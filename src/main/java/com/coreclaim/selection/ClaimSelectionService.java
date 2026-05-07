package com.coreclaim.selection;

import com.coreclaim.service.OnlineRewardService;
import com.coreclaim.service.HologramService;
import com.coreclaim.service.ClaimVisualService;
import com.coreclaim.service.ClaimService;
import com.coreclaim.profile.ProfileService;
import com.coreclaim.CoreClaimPlugin;
import com.coreclaim.economy.EconomyHook;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public final class ClaimSelectionService {

    private final CoreClaimPlugin plugin;
    private final Map<UUID, ClaimSelectionSession> sessions = new ConcurrentHashMap<>();
    private final ClaimSelectionToolSupport toolSupport;
    private final ClaimSelectionPreviewBuilder previewBuilder;
    private final ClaimSelectionCreator creator;

    public ClaimSelectionService(
        CoreClaimPlugin plugin,
        ClaimService claimService,
        ProfileService profileService,
        ClaimVisualService claimVisualService,
        HologramService hologramService,
        EconomyHook economyHook,
        OnlineRewardService onlineRewardService
    ) {
        this.plugin = plugin;
        this.toolSupport = new ClaimSelectionToolSupport(plugin);
        this.previewBuilder = new ClaimSelectionPreviewBuilder(plugin, claimService, claimVisualService, sessions);
        this.creator = new ClaimSelectionCreator(plugin, claimService, claimVisualService, hologramService, economyHook, onlineRewardService, previewBuilder, sessions);
    }

    public boolean isSelectionTool(ItemStack item) {
        return toolSupport.isSelectionTool(item);
    }

    public boolean canUseSelectionTool(ItemStack item) {
        return toolSupport.canUseSelectionTool(item);
    }

    public ItemStack normalizeSelectionTool(ItemStack item) {
        return toolSupport.normalizeSelectionTool(item);
    }

    public void clear(UUID playerId) {
        sessions.remove(playerId);
    }

    public void clear(Player player) {
        if (player != null) {
            clear(player.getUniqueId());
        }
    }

    public boolean setFirstPoint(Player player, Location location) {
        if (!validateClick(player, location)) {
            return false;
        }
        ClaimSelectionSession session = sessions.computeIfAbsent(player.getUniqueId(), ignored -> new ClaimSelectionSession());
        session.pos1 = blockLocation(location);
        session.world = location.getWorld().getName();
        player.sendMessage(plugin.message(
            "selection-pos1-set",
            "{x}", String.valueOf(session.pos1.getBlockX()),
            "{y}", String.valueOf(session.pos1.getBlockY()),
            "{z}", String.valueOf(session.pos1.getBlockZ())
        ));
        updatePreview(player);
        return true;
    }

    public boolean setSecondPoint(Player player, Location location) {
        if (!validateClick(player, location)) {
            return false;
        }
        ClaimSelectionSession session = sessions.computeIfAbsent(player.getUniqueId(), ignored -> new ClaimSelectionSession());
        if (session.pos1 == null) {
            player.sendMessage(plugin.message("selection-pos1-missing"));
            return false;
        }
        if (!session.world.equalsIgnoreCase(location.getWorld().getName())) {
            player.sendMessage(plugin.message(
                "selection-world-mismatch",
                "{world}", session.world
            ));
            return false;
        }
        session.pos2 = blockLocation(location);
        player.sendMessage(plugin.message(
            "selection-pos2-set",
            "{x}", String.valueOf(session.pos2.getBlockX()),
            "{y}", String.valueOf(session.pos2.getBlockY()),
            "{z}", String.valueOf(session.pos2.getBlockZ())
        ));
        updatePreview(player);
        return true;
    }

    public boolean createClaim(Player player, String rawName) {
        return creator.createClaim(player, rawName);
    }

    public boolean createSystemClaim(Player player, String rawName) {
        return creator.createSystemClaim(player, rawName);
    }

    public SelectionPreview preview(Player player) {
        return previewBuilder.preview(player, false);
    }

    public void updatePreview(Player player) {
        previewBuilder.updatePreview(player);
    }

    private boolean validateClick(Player player, Location location) {
        if (player == null || location == null || location.getWorld() == null) {
            return false;
        }
        if (!plugin.settings().isClaimWorld(location.getWorld().getName())) {
            player.sendMessage(plugin.message("claim-world-only", "{world}", plugin.settings().claimWorldsDisplay()));
            return false;
        }
        return true;
    }

    private Location blockLocation(Location location) {
        return new Location(location.getWorld(), location.getBlockX(), location.getBlockY(), location.getBlockZ());
    }

    public record SelectionPreview(
        boolean ready,
        boolean allowed,
        int width,
        int height,
        int depth,
        long area,
        long volume,
        double cost,
        int minX,
        int maxX,
        int minY,
        int maxY,
        int minZ,
        int maxZ,
        Location coreLocation,
        int east,
        int south,
        int west,
        int north,
        String failureMessage
    ) {
        static SelectionPreview pendingPoint(Location pos1) {
            return new SelectionPreview(false, false, 0, 0, 0, 0L, 0L, 0D, 0, 0, 0, 0, 0, 0, null, 0, 0, 0, 0, "");
        }

        static SelectionPreview failed(String message) {
            return new SelectionPreview(false, false, 0, 0, 0, 0L, 0L, 0D, 0, 0, 0, 0, 0, 0, null, 0, 0, 0, 0, message);
        }

        static SelectionPreview denied(
            int width,
            int height,
            int depth,
            long area,
            long volume,
            double cost,
            int minX,
            int maxX,
            int minY,
            int maxY,
            int minZ,
            int maxZ,
            Location coreLocation,
            int east,
            int south,
            int west,
            int north,
            String failureMessage
        ) {
            return new SelectionPreview(true, false, width, height, depth, area, volume, cost, minX, maxX, minY, maxY, minZ, maxZ, coreLocation, east, south, west, north, failureMessage);
        }

        static SelectionPreview allowed(
            int width,
            int height,
            int depth,
            long area,
            long volume,
            double cost,
            int minX,
            int maxX,
            int minY,
            int maxY,
            int minZ,
            int maxZ,
            Location coreLocation,
            int east,
            int south,
            int west,
            int north
        ) {
            return new SelectionPreview(true, true, width, height, depth, area, volume, cost, minX, maxX, minY, maxY, minZ, maxZ, coreLocation, east, south, west, north, "");
        }

        SelectionPreview withFailure(String failureMessage) {
            return denied(width, height, depth, area, volume, cost, minX, maxX, minY, maxY, minZ, maxZ, coreLocation, east, south, west, north, failureMessage);
        }
    }
}
