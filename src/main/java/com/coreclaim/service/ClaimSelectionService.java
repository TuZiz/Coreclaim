package com.coreclaim.service;

import com.coreclaim.CoreClaimPlugin;
import com.coreclaim.config.ClaimGroup;
import com.coreclaim.economy.EconomyHook;
import com.coreclaim.model.Claim;
import com.coreclaim.model.ClaimDirection;
import com.coreclaim.model.PlayerProfile;
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
    private final NamespacedKey selectionToolMarkerKey;
    private final Map<UUID, SelectionSession> sessions = new ConcurrentHashMap<>();

    public ClaimSelectionService(
        CoreClaimPlugin plugin,
        ClaimService claimService,
        ProfileService profileService,
        ClaimVisualService claimVisualService,
        HologramService hologramService,
        EconomyHook economyHook
    ) {
        this.plugin = plugin;
        this.claimService = claimService;
        this.profileService = profileService;
        this.claimVisualService = claimVisualService;
        this.hologramService = hologramService;
        this.economyHook = economyHook;
        this.selectionToolMarkerKey = new NamespacedKey(plugin, "claim_selection_tool_marker");
    }

    public boolean activate(Player player) {
        if (player == null) {
            return false;
        }
        sessions.putIfAbsent(player.getUniqueId(), new SelectionSession());
        ensureSelectionTool(player);
        player.sendMessage(chatMessage("selection-wand-received", "&a&l选区模式: &7已进入圈地工具选区模式，左键设置点 1，右键设置点 2。"));
        sendActionBar(player, plainMessage("selection-actionbar-start", "&e圈地工具已启用 &8| &7左键点 1 右键点 2"));
        return true;
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

    public boolean isSelectionToolCandidate(ItemStack item) {
        return item != null
            && !item.getType().isAir()
            && (isSelectionTool(item) || item.getType() == plugin.settings().selectionToolMaterial());
    }

    public ItemStack normalizeSelectionTool(ItemStack item) {
        if (item == null || item.getType().isAir()) {
            return item;
        }
        if (!isSelectionToolCandidate(item)) {
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

    public void normalizePlayerInventory(Player player) {
        if (player == null) {
            return;
        }
        ItemStack[] contents = player.getInventory().getContents();
        for (int index = 0; index < contents.length; index++) {
            ItemStack current = contents[index];
            if (isSelectionToolCandidate(current)) {
                player.getInventory().setItem(index, normalizeSelectionTool(current));
            }
        }
    }

    public void normalizePlayerInventoryAndCursor(Player player) {
        if (player == null) {
            return;
        }
        normalizePlayerInventory(player);
        ItemStack cursor = player.getOpenInventory().getCursor();
        if (isSelectionToolCandidate(cursor)) {
            player.getOpenInventory().setCursor(normalizeSelectionTool(cursor));
        }
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
        player.sendMessage(chatMessage(
            "selection-pos1-set",
            "&a&l点 1: &7已设置为 &f({x}, {y}, {z})",
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
            player.sendMessage(chatMessage("selection-pos1-missing", "&c&l! &7请先设置第一个点。"));
            return false;
        }
        if (!session.world.equalsIgnoreCase(location.getWorld().getName())) {
            player.sendMessage(chatMessage(
                "selection-world-mismatch",
                "&c&l! &7第二个点必须和第一个点在同一世界 &b{world}&7。",
                "{world}", session.world
            ));
            return false;
        }
        session.pos2 = blockLocation(location);
        player.sendMessage(chatMessage(
            "selection-pos2-set",
            "&b&l点 2: &7已设置为 &f({x}, {y}, {z})",
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
            player.sendMessage(chatMessage("selection-missing-points", "&c&l! &7请先完成两个对角点的选择。"));
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

        String configured = plugin.messagesConfig().getString("selection-create-success", "");
        if (configured != null && configured.contains("{height}")) {
            player.sendMessage(chatMessage(
                "selection-create-success",
                "&a&l创建成功: &7领地 &e{name} &7已创建，大小 &b{width}x{height}x{depth} &7，花费 &6{cost}&7。",
                "{name}", claim.name(),
                "{width}", String.valueOf(claim.width()),
                "{height}", String.valueOf(claim.height()),
                "{depth}", String.valueOf(claim.depth()),
                "{cost}", MONEY.format(preview.cost())
            ));
        } else {
            player.sendMessage(plugin.color("&8[&6Claim&8] &f&a&l创建成功: &7领地 &e"
                + claim.name()
                + " &7已创建，大小 &b"
                + claim.width() + "x" + claim.height() + "x" + claim.depth()
                + " &7，体积 &f" + preview.volume()
                + " &7，花费 &6" + MONEY.format(preview.cost()) + "&7。"));
        }
        return true;
    }

    public SelectionPreview preview(Player player) {
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
        PlayerProfile profile = profileService.getOrCreate(player.getUniqueId(), player.getName());
        int claimCount = claimService.countClaims(player.getUniqueId());
        int maxClaims = group.claimSlotsForActivity(profile.activityPoints());
        double cost = volume * group.selectionCreatePricePerBlock();

        if (claimCount >= maxClaims) {
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
                chatMessage("selection-too-large", "&c&l! &7选区过大，当前组允许的最大边长为 &e{max}&7。", "{max}", String.valueOf(group.maxDistance() * 2 + 1))
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
                appendMoveSuggestion(chatMessage(
                    "selection-claim-too-close",
                    "&c&l! &7锄头圈地之间至少需要保留 &e{gap} &7格间隔。",
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
                appendMoveSuggestion(chatMessage(
                    "selection-near-core-claim",
                    "&c&l! &7锄头圈地不能贴着核心领地创建，至少保留 &e{gap} &7格。",
                    "{gap}",
                    String.valueOf(plugin.settings().selectionMinimumGap())
                ), fullHeightGapSuggestion)
            );
        }

        if (coreY >= world.getMaxHeight() || !coreLocation.getBlock().getType().isAir()) {
            return SelectionPreview.denied(
                width, height, depth, area, volume, cost, minX, maxX, minY, maxY, minZ, maxZ, coreLocation, east, south, west, north,
                chatMessage("selection-core-blocked", "&c&l! &7选区中心位置被占用，请整体平移后再试。")
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
            sendActionBar(player, plainMessage("selection-actionbar-pos1", "&e已设置点 1 &8| &7请继续右键选择点 2"));
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

    private void ensureSelectionTool(Player player) {
        normalizePlayerInventoryAndCursor(player);

        ItemStack mainHand = player.getInventory().getItemInMainHand();
        if (isSelectionToolCandidate(mainHand)) {
            player.getInventory().setItemInMainHand(normalizeSelectionTool(mainHand));
            return;
        }

        ItemStack[] contents = player.getInventory().getContents();
        for (int index = 0; index < contents.length; index++) {
            ItemStack current = contents[index];
            if (!isSelectionToolCandidate(current)) {
                continue;
            }
            ItemStack normalized = normalizeSelectionTool(current);
            ItemStack previousMainHand = mainHand == null ? null : mainHand.clone();
            player.getInventory().setItemInMainHand(normalized);
            player.getInventory().setItem(index, previousMainHand);
            return;
        }

        ItemStack createdTool = createSelectionTool();
        ItemStack previousMainHand = mainHand == null ? null : mainHand.clone();
        player.getInventory().setItemInMainHand(createdTool);
        if (previousMainHand != null && !previousMainHand.getType().isAir()) {
            Map<Integer, ItemStack> leftovers = player.getInventory().addItem(previousMainHand);
            leftovers.values().forEach(leftover -> player.getWorld().dropItemNaturally(player.getLocation(), leftover));
        }
    }

    private ItemStack createSelectionTool() {
        return normalizeSelectionTool(new ItemStack(plugin.settings().selectionToolMaterial()));
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
        String status = preview.allowed() ? "" : plugin.color(" &8| &c" + stripPrefix(preview.failureMessage()));
        String configured = plugin.messagesConfig().getString("selection-actionbar-preview", "");
        if (configured != null && configured.contains("{height}")) {
            return plainMessage(
                "selection-actionbar-preview",
                "&d选区 &f{width}x{height}x{depth} &8| &7体积 &f{volume} &8| &6价格 &e{cost}{status}",
                "{width}", String.valueOf(preview.width()),
                "{height}", String.valueOf(preview.height()),
                "{depth}", String.valueOf(preview.depth()),
                "{volume}", String.valueOf(preview.volume()),
                "{cost}", MONEY.format(preview.cost()),
                "{status}", status
            );
        }
        return plugin.color("&d选区 &f"
            + preview.width() + "x" + preview.height() + "x" + preview.depth()
            + " &8| &7体积 &f" + preview.volume()
            + " &8| &6价格 &e" + MONEY.format(preview.cost())
            + status);
    }

    private String appendMoveSuggestion(String message, MoveSuggestion suggestion) {
        if (message == null || message.isBlank() || suggestion == null) {
            return message;
        }
        return message + plainMessage(
            "selection-move-suggestion",
            " &7建议向 &e{direction} &7移动 &e{blocks} &7格。",
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

    private String chatMessage(String path, String fallback, String... replacements) {
        String prefix = plugin.messagesConfig().getString("prefix", "&8[&6Claim&8] &f");
        String body = plugin.messagesConfig().contains(path) ? plugin.messagesConfig().getString(path, fallback) : fallback;
        String message = plugin.color(prefix + body);
        for (int index = 0; index + 1 < replacements.length; index += 2) {
            message = message.replace(replacements[index], replacements[index + 1]);
        }
        return message;
    }

    private String plainMessage(String path, String fallback, String... replacements) {
        String message = plugin.color(plugin.messagesConfig().contains(path)
            ? plugin.messagesConfig().getString(path, fallback)
            : fallback);
        for (int index = 0; index + 1 < replacements.length; index += 2) {
            message = message.replace(replacements[index], replacements[index + 1]);
        }
        return message;
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
