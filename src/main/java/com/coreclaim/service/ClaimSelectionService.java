package com.coreclaim.service;

import com.coreclaim.CoreClaimPlugin;
import com.coreclaim.config.ClaimGroup;
import com.coreclaim.economy.EconomyHook;
import com.coreclaim.model.Claim;
import com.coreclaim.model.ClaimDirection;
import java.text.DecimalFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

public final class ClaimSelectionService {

    private static final DecimalFormat MONEY = new DecimalFormat("0.##");

    private final CoreClaimPlugin plugin;
    private final ClaimService claimService;
    private final ProfileService profileService;
    private final ClaimVisualService claimVisualService;
    private final HologramService hologramService;
    private final EconomyHook economyHook;
    private final OnlineRewardService onlineRewardService;
    private final NamespacedKey selectionToolMarkerKey;
    private final Map<UUID, SelectionSession> sessions = new ConcurrentHashMap<>();

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
        this.claimService = claimService;
        this.profileService = profileService;
        this.claimVisualService = claimVisualService;
        this.hologramService = hologramService;
        this.economyHook = economyHook;
        this.onlineRewardService = onlineRewardService;
        this.selectionToolMarkerKey = new NamespacedKey(plugin, "claim_selection_tool_marker");
    }

    public boolean isSelectionTool(ItemStack item) {
        if (item == null || item.getType().isAir()) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return false;
        }
        if (hasSelectionToolMarker(meta)) {
            return true;
        }
        return isLegacySelectionTool(item, meta);
    }

    public boolean canUseSelectionTool(ItemStack item) {
        return item != null
            && !item.getType().isAir()
            && item.getType() == plugin.settings().selectionToolMaterial();
    }

    public ItemStack normalizeSelectionTool(ItemStack item) {
        if (item == null || item.getType().isAir()) {
            return item;
        }
        if (!canUseSelectionTool(item)) {
            return item;
        }
        ItemStack clone = item.clone();
        ItemMeta meta = clone.getItemMeta();
        if (meta == null) {
            return clone;
        }
        meta.setDisplayName(plugin.color(plugin.settings().selectionToolName()));
        List<String> lore = plugin.settings().selectionToolLore().stream().map(plugin::color).toList();
        if (!lore.isEmpty()) {
            meta.setLore(lore);
        }
        if (plugin.settings().selectionToolCustomModelData() > 0) {
            meta.setCustomModelData(plugin.settings().selectionToolCustomModelData());
        } else {
            meta.setCustomModelData(null);
        }
        if (plugin.settings().selectionToolGlow()) {
            meta.addEnchant(Enchantment.LUCK, 1, true);
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        }
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        meta.getPersistentDataContainer().set(selectionToolMarkerKey, PersistentDataType.STRING, "true");
        clone.setItemMeta(meta);
        return clone;
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
        SelectionSession session = sessions.computeIfAbsent(player.getUniqueId(), ignored -> new SelectionSession());
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
        SelectionSession session = sessions.computeIfAbsent(player.getUniqueId(), ignored -> new SelectionSession());
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
        SelectionPreview preview = preview(player);
        if (preview == null || !preview.ready()) {
            player.sendMessage(plugin.message("selection-missing-points"));
            return false;
        }
        if (!preview.allowed()) {
            player.sendMessage(preview.failureMessage());
            return false;
        }

        String name = rawName == null ? "" : rawName.trim();
        if (name.isEmpty()) {
            player.sendMessage(plugin.message("claim-name-empty"));
            return false;
        }
        if (name.length() > plugin.settings().claimNameMaxLength()) {
            player.sendMessage(plugin.message("claim-name-too-long", "{max}", String.valueOf(plugin.settings().claimNameMaxLength())));
            return false;
        }
        if (claimService.isClaimNameTaken(name)) {
            player.sendMessage(plugin.message("claim-name-exists", "{name}", name));
            return false;
        }

        if (preview.cost() > 0D) {
            if (!economyHook.available()) {
                player.sendMessage(plugin.message("economy-missing"));
                return false;
            }
            if (!economyHook.has(player, preview.cost())) {
                player.sendMessage(plugin.message("economy-not-enough", "{cost}", MONEY.format(preview.cost())));
                return false;
            }
            if (!economyHook.withdraw(player, preview.cost())) {
                player.sendMessage(plugin.message("economy-missing"));
                return false;
            }
        }

        boolean firstOrdinaryClaim = claimService.countClaims(player.getUniqueId()) == 0;
        Claim claim;
        try {
            claim = claimService.createClaimFromBounds(
                player.getUniqueId(),
                player.getName(),
                name,
                preview.coreLocation(),
                preview.minY(),
                preview.maxY(),
                preview.east(),
                preview.south(),
                preview.west(),
                preview.north()
            );
        } catch (IllegalArgumentException exception) {
            if (preview.cost() > 0D && economyHook.available()) {
                economyHook.deposit(player, preview.cost());
            }
            player.sendMessage(plugin.message("claim-name-exists", "{name}", name));
            return false;
        }
        preview.coreLocation().getBlock().setType(plugin.settings().coreMaterial(), false);
        hologramService.spawnClaimHologram(claim);
        claimVisualService.showClaim(player, claim);
        clear(player.getUniqueId());
        onlineRewardService.markOrdinaryClaimCreated(player);

        player.sendMessage(plugin.message(
            "selection-create-success",
            "{name}", claim.name(),
            "{width}", String.valueOf(claim.width()),
            "{height}", String.valueOf(claim.height()),
            "{depth}", String.valueOf(claim.depth()),
            "{volume}", String.valueOf(preview.volume()),
            "{cost}", MONEY.format(preview.cost())
        ));
        if (firstOrdinaryClaim) {
            player.sendMessage(plugin.message("second-claim-selection-tip"));
        }
        return true;
    }

    public boolean createSystemClaim(Player player, String rawName) {
        SelectionPreview preview = preview(player, true);
        if (preview == null || !preview.ready()) {
            player.sendMessage(plugin.message("selection-missing-points"));
            return false;
        }
        if (!preview.allowed()) {
            player.sendMessage(preview.failureMessage());
            return false;
        }

        String name = rawName == null ? "" : rawName.trim();
        if (name.isEmpty()) {
            player.sendMessage(plugin.message("claim-name-empty"));
            return false;
        }
        if (name.length() > plugin.settings().claimNameMaxLength()) {
            player.sendMessage(plugin.message("claim-name-too-long", "{max}", String.valueOf(plugin.settings().claimNameMaxLength())));
            return false;
        }
        if (claimService.isClaimNameTaken(name)) {
            player.sendMessage(plugin.message("claim-name-exists", "{name}", name));
            return false;
        }

        Claim claim;
        try {
            claim = claimService.createClaimFromBounds(
                player.getUniqueId(),
                player.getName(),
                name,
                preview.coreLocation(),
                preview.minY(),
                preview.maxY(),
                preview.east(),
                preview.south(),
                preview.west(),
                preview.north(),
                true
            );
        } catch (IllegalArgumentException exception) {
            player.sendMessage(plugin.message("claim-name-exists", "{name}", name));
            return false;
        }
        preview.coreLocation().getBlock().setType(plugin.settings().coreMaterial(), false);
        hologramService.spawnClaimHologram(claim);
        claimVisualService.showClaim(player, claim);
        clear(player.getUniqueId());
        player.sendMessage(plugin.message(
            "selection-create-system-success",
            "{name}", claim.name(),
            "{width}", String.valueOf(claim.width()),
            "{height}", String.valueOf(claim.height()),
            "{depth}", String.valueOf(claim.depth())
        ));
        return true;
    }

    public SelectionPreview preview(Player player) {
        return preview(player, false);
    }

    private SelectionPreview preview(Player player, boolean bypassQuotaAndCost) {
        if (player == null) {
            return null;
        }
        SelectionSession session = sessions.get(player.getUniqueId());
        if (session == null || session.pos1 == null) {
            return null;
        }
        if (session.pos2 == null) {
            return SelectionPreview.pendingPoint(session.pos1);
        }

        World world = player.getServer().getWorld(session.world);
        if (world == null) {
            return SelectionPreview.failed(plugin.message("world-missing"));
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

        if (!bypassQuotaAndCost && claimCount >= maxClaims) {
            return SelectionPreview.denied(
                width, height, depth, area, volume, cost, minX, maxX, minY, maxY, minZ, maxZ, coreLocation, east, south, west, north,
                plugin.message("claim-no-slot")
            );
        }
        if (!plugin.settings().isClaimWorld(world.getName())) {
            return SelectionPreview.denied(
                width, height, depth, area, volume, cost, minX, maxX, minY, maxY, minZ, maxZ, coreLocation, east, south, west, north,
                plugin.message("claim-world-only", "{world}", plugin.settings().claimWorldsDisplay())
            );
        }
        if (Math.max(Math.max(east, west), Math.max(north, south)) > group.maxDistance()) {
            return SelectionPreview.denied(
                width, height, depth, area, volume, cost, minX, maxX, minY, maxY, minZ, maxZ, coreLocation, east, south, west, north,
                plugin.message("selection-too-large", "{max}", String.valueOf(group.maxDistance() * 2 + 1))
            );
        }

        MoveSuggestion overlapSuggestion = findMoveSuggestion(
            world.getName(),
            minX,
            maxX,
            minY,
            maxY,
            minZ,
            maxZ,
            0,
            claim -> true
        );
        if (overlapSuggestion != null) {
            return SelectionPreview.denied(
                width, height, depth, area, volume, cost, minX, maxX, minY, maxY, minZ, maxZ, coreLocation, east, south, west, north,
                appendMoveSuggestion(plugin.message("claim-overlap"), overlapSuggestion)
            );
        }

        MoveSuggestion selectionGapSuggestion = findMoveSuggestion(
            world.getName(),
            minX,
            maxX,
            minY,
            maxY,
            minZ,
            maxZ,
            plugin.settings().selectionMinimumGap(),
            claim -> !claim.fullHeight()
        );
        if (selectionGapSuggestion != null) {
            return SelectionPreview.denied(
                width, height, depth, area, volume, cost, minX, maxX, minY, maxY, minZ, maxZ, coreLocation, east, south, west, north,
                appendMoveSuggestion(plugin.message(
                    "selection-claim-too-close",
                    "{gap}",
                    String.valueOf(plugin.settings().selectionMinimumGap())
                ), selectionGapSuggestion)
            );
        }

        MoveSuggestion fullHeightGapSuggestion = findMoveSuggestion(
            world.getName(),
            minX,
            maxX,
            minY,
            maxY,
            minZ,
            maxZ,
            plugin.settings().selectionMinimumGap(),
            Claim::fullHeight
        );
        if (fullHeightGapSuggestion != null) {
            return SelectionPreview.denied(
                width, height, depth, area, volume, cost, minX, maxX, minY, maxY, minZ, maxZ, coreLocation, east, south, west, north,
                appendMoveSuggestion(plugin.message(
                    "selection-near-core-claim",
                    "{gap}",
                    String.valueOf(plugin.settings().selectionMinimumGap())
                ), fullHeightGapSuggestion)
            );
        }

        if (coreY >= world.getMaxHeight() || !coreLocation.getBlock().getType().isAir()) {
            return SelectionPreview.denied(
                width, height, depth, area, volume, cost, minX, maxX, minY, maxY, minZ, maxZ, coreLocation, east, south, west, north,
                plugin.message("selection-core-blocked")
            );
        }

        return SelectionPreview.allowed(width, height, depth, area, volume, cost, minX, maxX, minY, maxY, minZ, maxZ, coreLocation, east, south, west, north);
    }

    public void updatePreview(Player player) {
        SelectionPreview preview = preview(player);
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

    private int resolveCoreY(World world, int centerX, int centerZ) {
        return world.getHighestBlockYAt(centerX, centerZ) + 1;
    }

    private boolean hasSelectionToolMarker(ItemMeta meta) {
        String marker = meta.getPersistentDataContainer().get(selectionToolMarkerKey, PersistentDataType.STRING);
        return "true".equals(marker);
    }

    private boolean isLegacySelectionTool(ItemStack item, ItemMeta meta) {
        if (item.getType() != plugin.settings().selectionToolMaterial()) {
            return false;
        }

        String configuredName = plugin.color(plugin.settings().selectionToolName());
        List<String> configuredLore = plugin.settings().selectionToolLore().stream().map(plugin::color).toList();

        boolean nameMatches = meta.hasDisplayName() && Objects.equals(meta.getDisplayName(), configuredName);
        boolean loreMatches = meta.hasLore() && Objects.equals(meta.getLore(), configuredLore);
        boolean customModelMatches = plugin.settings().selectionToolCustomModelData() > 0
            && meta.hasCustomModelData()
            && meta.getCustomModelData() == plugin.settings().selectionToolCustomModelData();

        return nameMatches || loreMatches || customModelMatches;
    }

    private void sendActionBar(Player player, String message) {
        player.spigot().sendMessage(ChatMessageType.ACTION_BAR, TextComponent.fromLegacyText(message));
    }

    private String previewActionBar(SelectionPreview preview) {
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

    private String appendMoveSuggestion(String message, MoveSuggestion suggestion) {
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
            case EAST -> "东";
            case SOUTH -> "南";
            case WEST -> "西";
            case NORTH -> "北";
        };
    }

    private String stripPrefix(String message) {
        String prefix = plugin.color(plugin.messagesConfig().getString("prefix", ""));
        if (message == null) {
            return "";
        }
        return message.startsWith(prefix) ? message.substring(prefix.length()) : message;
    }

    private MoveSuggestion findMoveSuggestion(
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
        MoveSuggestion best = null;
        for (Claim claim : claimService.allClaims()) {
            if (filter != null && !filter.test(claim)) {
                continue;
            }
            if (!claim.overlaps(world, expandedMinX, expandedMaxX, minY, maxY, expandedMinZ, expandedMaxZ, null, false)) {
                continue;
            }
            MoveSuggestion suggestion = buildMoveSuggestion(claim, minX, maxX, minZ, maxZ, effectiveGap);
            if (suggestion == null) {
                continue;
            }
            if (best == null || suggestion.blocks() < best.blocks()) {
                best = suggestion;
            }
        }
        return best;
    }

    private MoveSuggestion buildMoveSuggestion(Claim claim, int minX, int maxX, int minZ, int maxZ, int gap) {
        MoveSuggestion best = null;
        best = pickBetter(best, ClaimDirection.WEST, maxX - (claim.minX() - gap - 1));
        best = pickBetter(best, ClaimDirection.EAST, claim.maxX() + gap + 1 - minX);
        best = pickBetter(best, ClaimDirection.NORTH, maxZ - (claim.minZ() - gap - 1));
        best = pickBetter(best, ClaimDirection.SOUTH, claim.maxZ() + gap + 1 - minZ);
        return best;
    }

    private MoveSuggestion pickBetter(MoveSuggestion current, ClaimDirection direction, int blocks) {
        if (blocks <= 0) {
            return current;
        }
        if (current == null || blocks < current.blocks()) {
            return new MoveSuggestion(direction, blocks);
        }
        return current;
    }

    private static final class SelectionSession {
        private String world;
        private Location pos1;
        private Location pos2;
    }

    private record MoveSuggestion(ClaimDirection direction, int blocks) {
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
        private static SelectionPreview pendingPoint(Location pos1) {
            return new SelectionPreview(false, false, 0, 0, 0, 0L, 0L, 0D, 0, 0, 0, 0, 0, 0, null, 0, 0, 0, 0, "");
        }

        private static SelectionPreview failed(String message) {
            return new SelectionPreview(false, false, 0, 0, 0, 0L, 0L, 0D, 0, 0, 0, 0, 0, 0, null, 0, 0, 0, 0, message);
        }

        private static SelectionPreview denied(
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

        private static SelectionPreview allowed(
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
    }
}
