package com.coreclaim.selection;

import com.coreclaim.service.ClaimVisualService;
import com.coreclaim.service.ClaimService;
import com.coreclaim.CoreClaimPlugin;
import com.coreclaim.config.ClaimGroup;
import com.coreclaim.model.Claim;
import com.coreclaim.model.ClaimDirection;
import java.text.DecimalFormat;
import java.util.Map;
import java.util.UUID;
import java.util.function.Predicate;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;

final class ClaimSelectionPreviewBuilder {

    private static final DecimalFormat MONEY = new DecimalFormat("0.##");

    private final CoreClaimPlugin plugin;
    private final ClaimService claimService;
    private final ClaimVisualService claimVisualService;
    private final Map<UUID, ClaimSelectionSession> sessions;

    ClaimSelectionPreviewBuilder(
        CoreClaimPlugin plugin,
        ClaimService claimService,
        ClaimVisualService claimVisualService,
        Map<UUID, ClaimSelectionSession> sessions
    ) {
        this.plugin = plugin;
        this.claimService = claimService;
        this.claimVisualService = claimVisualService;
        this.sessions = sessions;
    }

    ClaimSelectionService.SelectionPreview preview(Player player, boolean bypassQuotaAndCost) {
        if (player == null) {
            return null;
        }
        ClaimSelectionSession session = sessions.get(player.getUniqueId());
        if (session == null || session.pos1 == null) {
            return null;
        }
        if (session.pos2 == null) {
            return ClaimSelectionService.SelectionPreview.pendingPoint(session.pos1);
        }

        World world = player.getServer().getWorld(session.world);
        if (world == null) {
            return ClaimSelectionService.SelectionPreview.failed(plugin.message("world-missing"));
        }

        int minX = Math.min(session.pos1.getBlockX(), session.pos2.getBlockX());
        int maxX = Math.max(session.pos1.getBlockX(), session.pos2.getBlockX());
        int minY = Math.min(session.pos1.getBlockY(), session.pos2.getBlockY());
        int maxY = Math.max(session.pos1.getBlockY(), session.pos2.getBlockY());
        int minZ = Math.min(session.pos1.getBlockZ(), session.pos2.getBlockZ());
        int maxZ = Math.max(session.pos1.getBlockZ(), session.pos2.getBlockZ());
        int width = maxX - minX + 1;
        int height = maxY - minY + 1;
        int depth = maxZ - minZ + 1;
        long area = (long) width * depth;
        long volume = area * height;

        int centerX = (minX + maxX) / 2;
        int centerZ = (minZ + maxZ) / 2;
        int west = centerX - minX;
        int east = maxX - centerX;
        int north = centerZ - minZ;
        int south = maxZ - centerZ;

        int coreY = resolveCoreY(world, centerX, centerZ);
        Location coreLocation = new Location(world, centerX, coreY, centerZ);
        ClaimGroup group = plugin.groups().resolve(player);
        int claimCount = claimService.countClaims(player.getUniqueId());
        int maxClaims = group.maxClaims();
        double cost = bypassQuotaAndCost ? 0D : volume * group.selectionCreatePricePerBlock();

        ClaimSelectionService.SelectionPreview basePreview = buildDeniedBase(width, height, depth, area, volume, cost, minX, maxX, minY, maxY, minZ, maxZ, coreLocation, east, south, west, north);
        if (!bypassQuotaAndCost && claimCount >= maxClaims) {
            return basePreview.withFailure(plugin.message("claim-no-slot"));
        }
        if (!plugin.settings().isClaimWorld(world.getName())) {
            return basePreview.withFailure(plugin.message("claim-world-only", "{world}", plugin.settings().claimWorldsDisplay()));
        }
        if (group.exceedsDistanceLimit(east, south, west, north)) {
            return basePreview.withFailure(plugin.message("selection-too-large", "{max}", String.valueOf(group.maxDistance() * 2 + 1)));
        }

        ClaimSelectionMoveSuggestion overlapSuggestion = findMoveSuggestion(world.getName(), minX, maxX, minY, maxY, minZ, maxZ, 0, claim -> true);
        if (overlapSuggestion != null) {
            return basePreview.withFailure(appendMoveSuggestion(plugin.message("claim-overlap"), overlapSuggestion));
        }

        ClaimSelectionMoveSuggestion selectionGapSuggestion = findMoveSuggestion(world.getName(), minX, maxX, minY, maxY, minZ, maxZ, plugin.settings().selectionMinimumGap(), claim -> !claim.fullHeight());
        if (selectionGapSuggestion != null) {
            return basePreview.withFailure(appendMoveSuggestion(plugin.message("selection-claim-too-close", "{gap}", String.valueOf(plugin.settings().selectionMinimumGap())), selectionGapSuggestion));
        }

        ClaimSelectionMoveSuggestion fullHeightGapSuggestion = findMoveSuggestion(world.getName(), minX, maxX, minY, maxY, minZ, maxZ, plugin.settings().selectionMinimumGap(), Claim::fullHeight);
        if (fullHeightGapSuggestion != null) {
            return basePreview.withFailure(appendMoveSuggestion(plugin.message("selection-near-core-claim", "{gap}", String.valueOf(plugin.settings().selectionMinimumGap())), fullHeightGapSuggestion));
        }

        if (coreY >= world.getMaxHeight() || !coreLocation.getBlock().getType().isAir()) {
            return basePreview.withFailure(plugin.message("selection-core-blocked"));
        }

        return ClaimSelectionService.SelectionPreview.allowed(width, height, depth, area, volume, cost, minX, maxX, minY, maxY, minZ, maxZ, coreLocation, east, south, west, north);
    }

    void updatePreview(Player player) {
        ClaimSelectionService.SelectionPreview preview = preview(player, false);
        if (preview == null) {
            return;
        }
        if (!preview.ready()) {
            sendActionBar(player, plugin.plainMessage("selection-actionbar-pos1"));
            return;
        }
        sendActionBar(player, previewActionBar(preview));
        World previewWorld = preview.coreLocation() == null ? player.getWorld() : preview.coreLocation().getWorld();
        claimVisualService.showSelection(
            player,
            previewWorld,
            preview.minX(),
            preview.maxX(),
            preview.minY(),
            preview.maxY(),
            preview.minZ(),
            preview.maxZ()
        );
    }

    private ClaimSelectionService.SelectionPreview buildDeniedBase(
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
        return ClaimSelectionService.SelectionPreview.denied(width, height, depth, area, volume, cost, minX, maxX, minY, maxY, minZ, maxZ, coreLocation, east, south, west, north, "");
    }

    private int resolveCoreY(World world, int centerX, int centerZ) {
        return world.getHighestBlockYAt(centerX, centerZ) + 1;
    }

    private void sendActionBar(Player player, String message) {
        player.spigot().sendMessage(ChatMessageType.ACTION_BAR, TextComponent.fromLegacyText(message));
    }

    private String previewActionBar(ClaimSelectionService.SelectionPreview preview) {
        String status = preview.allowed() ? "" : plugin.color(" &#475569| &#FF6B6B" + stripPrefix(preview.failureMessage()));
        return plugin.plainMessage(
            "selection-actionbar-preview",
            "{width}", String.valueOf(preview.width()),
            "{height}", String.valueOf(preview.height()),
            "{depth}", String.valueOf(preview.depth()),
            "{volume}", String.valueOf(preview.volume()),
            "{cost}", MONEY.format(preview.cost()),
            "{status}", status
        );
    }

    private String appendMoveSuggestion(String message, ClaimSelectionMoveSuggestion suggestion) {
        if (message == null || message.isBlank() || suggestion == null) {
            return message;
        }
        return message + plugin.plainMessage(
            "selection-move-suggestion",
            "{direction}", directionDisplay(suggestion.direction()),
            "{blocks}", String.valueOf(suggestion.blocks())
        );
    }

    private String directionDisplay(ClaimDirection direction) {
        return switch (direction) {
            case EAST -> "\u4e1c";
            case SOUTH -> "\u5357";
            case WEST -> "\u897f";
            case NORTH -> "\u5317";
        };
    }

    private String stripPrefix(String message) {
        String prefix = plugin.color(plugin.messagesConfig().getString("prefix", ""));
        if (message == null) {
            return "";
        }
        return message.startsWith(prefix) ? message.substring(prefix.length()) : message;
    }

    private ClaimSelectionMoveSuggestion findMoveSuggestion(
        String world,
        int minX,
        int maxX,
        int minY,
        int maxY,
        int minZ,
        int maxZ,
        int gap,
        Predicate<Claim> filter
    ) {
        int effectiveGap = Math.max(0, gap);
        int expandedMinX = minX - effectiveGap;
        int expandedMaxX = maxX + effectiveGap;
        int expandedMinZ = minZ - effectiveGap;
        int expandedMaxZ = maxZ + effectiveGap;
        ClaimSelectionMoveSuggestion best = null;
        for (Claim claim : claimService.allClaims()) {
            if (filter != null && !filter.test(claim)) {
                continue;
            }
            if (!claim.overlaps(world, expandedMinX, expandedMaxX, minY, maxY, expandedMinZ, expandedMaxZ, null, false)) {
                continue;
            }
            ClaimSelectionMoveSuggestion suggestion = buildMoveSuggestion(claim, minX, maxX, minZ, maxZ, effectiveGap);
            if (suggestion == null) {
                continue;
            }
            if (best == null || suggestion.blocks() < best.blocks()) {
                best = suggestion;
            }
        }
        return best;
    }

    private ClaimSelectionMoveSuggestion buildMoveSuggestion(Claim claim, int minX, int maxX, int minZ, int maxZ, int gap) {
        ClaimSelectionMoveSuggestion best = null;
        best = pickBetter(best, ClaimDirection.WEST, maxX - (claim.minX() - gap - 1));
        best = pickBetter(best, ClaimDirection.EAST, claim.maxX() + gap + 1 - minX);
        best = pickBetter(best, ClaimDirection.NORTH, maxZ - (claim.minZ() - gap - 1));
        best = pickBetter(best, ClaimDirection.SOUTH, claim.maxZ() + gap + 1 - minZ);
        return best;
    }

    private ClaimSelectionMoveSuggestion pickBetter(ClaimSelectionMoveSuggestion current, ClaimDirection direction, int blocks) {
        if (blocks <= 0) {
            return current;
        }
        if (current == null || blocks < current.blocks()) {
            return new ClaimSelectionMoveSuggestion(direction, blocks);
        }
        return current;
    }
}
