package com.coreclaim.gui;

import com.coreclaim.CoreClaimPlugin;
import com.coreclaim.config.ClaimGroup;
import com.coreclaim.model.Claim;
import com.coreclaim.model.ClaimDirection;
import com.coreclaim.model.PlayerProfile;
import com.coreclaim.service.ClaimActionService;
import com.coreclaim.service.ClaimService;
import com.coreclaim.service.ProfileService;
import com.coreclaim.service.RemovalConfirmationService;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

public final class MenuService {

    private final CoreClaimPlugin plugin;
    private final ClaimService claimService;
    private final ProfileService profileService;
    private final ClaimActionService claimActionService;
    private final RemovalConfirmationService removalConfirmationService;

    public MenuService(
        CoreClaimPlugin plugin,
        ClaimService claimService,
        ProfileService profileService,
        ClaimActionService claimActionService,
        RemovalConfirmationService removalConfirmationService
    ) {
        this.plugin = plugin;
        this.claimService = claimService;
        this.profileService = profileService;
        this.claimActionService = claimActionService;
        this.removalConfirmationService = removalConfirmationService;
    }

    public void openMainMenu(Player player) {
        MainMenuHolder holder = new MainMenuHolder();
        Inventory inventory = Bukkit.createInventory(holder, menuSize("main"), menuTitle("main"));
        holder.inventory = inventory;
        fill(inventory, "main", "filler");

        PlayerProfile profile = profileService.getOrCreate(player.getUniqueId(), player.getName());
        ClaimGroup group = plugin.groups().resolve(player);
        int claimCount = claimService.countClaims(player.getUniqueId());
        int maxClaims = group.claimSlotsForActivity(profile.activityPoints());
        Claim currentClaim = claimActionService.findOwnedClaim(player);

        inventory.setItem(slot("main", "info"), configuredItem("main", "info",
            "{activity}", String.valueOf(profile.activityPoints()),
            "{claim_count}", String.valueOf(claimCount),
            "{claim_limit}", String.valueOf(maxClaims),
            "{online_minutes}", String.valueOf(profile.onlineMinutes())
        ));
        inventory.setItem(slot("main", "group-info"), configuredItem("main", "group-info",
            "{group}", group.displayName(),
            "{expand_price}", ClaimActionService.formatMoney(group.expandPricePerBlock()),
            "{max_distance}", String.valueOf(group.maxDistance()),
            "{expand_amount}", String.valueOf(plugin.settings().directionExpandAmount())
        ));
        inventory.setItem(
            slot("main", "current-claim"),
            currentClaim == null
                ? configuredItem("main", "current-claim-empty")
                : configuredItem("main", "current-claim",
                    "{name}", currentClaim.name(),
                    "{width}", String.valueOf(currentClaim.width()),
                    "{depth}", String.valueOf(currentClaim.depth()),
                    "{area}", String.valueOf(currentClaim.area()))
        );
        inventory.setItem(slot("main", "claim-list"), configuredItem("main", "claim-list"));
        inventory.setItem(slot("main", "close"), configuredItem("main", "close"));
        player.openInventory(inventory);
    }

    public void openClaimListMenu(Player player, int page) {
        ClaimListHolder holder = new ClaimListHolder(page);
        Inventory inventory = Bukkit.createInventory(holder, menuSize("claim-list"), menuTitle("claim-list"));
        holder.inventory = inventory;
        fill(inventory, "claim-list", "filler");

        List<Claim> claims = claimService.claimsOf(player.getUniqueId());
        List<Integer> entrySlots = slots("claim-list", "entry");
        int start = Math.max(0, page) * entrySlots.size();
        int end = Math.min(claims.size(), start + entrySlots.size());
        for (int index = start; index < end; index++) {
            Claim claim = claims.get(index);
            inventory.setItem(entrySlots.get(index - start), configuredItem("claim-list", "entry",
                "{name}", claim.name(),
                "{world}", claim.world(),
                "{x}", String.valueOf(claim.centerX()),
                "{z}", String.valueOf(claim.centerZ()),
                "{width}", String.valueOf(claim.width()),
                "{depth}", String.valueOf(claim.depth()),
                "{area}", String.valueOf(claim.area()),
                "{trusted}", String.valueOf(claim.trustedCount())
            ));
        }

        inventory.setItem(slot("claim-list", "prev-page"), configuredItem("claim-list", "prev-page"));
        inventory.setItem(slot("claim-list", "back"), configuredItem("claim-list", "back"));
        inventory.setItem(slot("claim-list", "next-page"), configuredItem("claim-list", "next-page"));
        player.openInventory(inventory);
    }

    public void openClaimManageMenu(Player player, Claim claim) {
        ClaimManageHolder holder = new ClaimManageHolder(claim.id());
        Inventory inventory = Bukkit.createInventory(holder, menuSize("claim-manage"),
            menuTitle("claim-manage", "{name}", claim.name()));
        holder.inventory = inventory;
        fill(inventory, "claim-manage", "filler");

        int amount = plugin.settings().directionExpandAmount();
        ClaimActionService.ExpansionPreview north = claimActionService.previewExpansion(player, claim, ClaimDirection.NORTH);
        ClaimActionService.ExpansionPreview south = claimActionService.previewExpansion(player, claim, ClaimDirection.SOUTH);
        ClaimActionService.ExpansionPreview west = claimActionService.previewExpansion(player, claim, ClaimDirection.WEST);
        ClaimActionService.ExpansionPreview east = claimActionService.previewExpansion(player, claim, ClaimDirection.EAST);

        inventory.setItem(slot("claim-manage", "info"), configuredItem("claim-manage", "info",
            "{name}", claim.name(),
            "{world}", claim.world(),
            "{x}", String.valueOf(claim.centerX()),
            "{z}", String.valueOf(claim.centerZ()),
            "{width}", String.valueOf(claim.width()),
            "{depth}", String.valueOf(claim.depth()),
            "{area}", String.valueOf(claim.area()),
            "{east}", String.valueOf(claim.east()),
            "{south}", String.valueOf(claim.south()),
            "{west}", String.valueOf(claim.west()),
            "{north}", String.valueOf(claim.north()),
            "{trusted}", String.valueOf(claim.trustedCount())
        ));
        inventory.setItem(slot("claim-manage", "expand-north"), configuredItem("claim-manage", "expand-north",
            "{amount}", String.valueOf(amount),
            "{price}", north.costText(),
            "{current}", String.valueOf(claim.north()),
            "{target}", String.valueOf(north.targetDistance())
        ));
        inventory.setItem(slot("claim-manage", "expand-south"), configuredItem("claim-manage", "expand-south",
            "{amount}", String.valueOf(amount),
            "{price}", south.costText(),
            "{current}", String.valueOf(claim.south()),
            "{target}", String.valueOf(south.targetDistance())
        ));
        inventory.setItem(slot("claim-manage", "expand-west"), configuredItem("claim-manage", "expand-west",
            "{amount}", String.valueOf(amount),
            "{price}", west.costText(),
            "{current}", String.valueOf(claim.west()),
            "{target}", String.valueOf(west.targetDistance())
        ));
        inventory.setItem(slot("claim-manage", "expand-east"), configuredItem("claim-manage", "expand-east",
            "{amount}", String.valueOf(amount),
            "{price}", east.costText(),
            "{current}", String.valueOf(claim.east()),
            "{target}", String.valueOf(east.targetDistance())
        ));
        inventory.setItem(slot("claim-manage", "trust"), configuredItem("claim-manage", "trust"));
        inventory.setItem(slot("claim-manage", "teleport"), configuredItem("claim-manage", "teleport"));
        inventory.setItem(slot("claim-manage", "delete"), configuredItem("claim-manage", "delete"));
        inventory.setItem(slot("claim-manage", "back"), configuredItem("claim-manage", "back"));
        player.openInventory(inventory);
    }

    public void openCoreMenu(Player player, Claim claim) {
        CoreMenuHolder holder = new CoreMenuHolder(claim.id());
        Inventory inventory = Bukkit.createInventory(holder, menuSize("core"),
            menuTitle("core", "{name}", claim.name()));
        holder.inventory = inventory;
        fill(inventory, "core", "filler");

        inventory.setItem(slot("core", "info"), configuredItem("core", "info",
            "{name}", claim.name(),
            "{owner}", claim.ownerName(),
            "{x}", String.valueOf(claim.centerX()),
            "{z}", String.valueOf(claim.centerZ()),
            "{width}", String.valueOf(claim.width()),
            "{depth}", String.valueOf(claim.depth()),
            "{trusted}", String.valueOf(claim.trustedCount())
        ));
        inventory.setItem(slot("core", "teleport"), configuredItem("core", "teleport"));
        inventory.setItem(slot("core", "settings"), configuredItem("core", "settings"));
        inventory.setItem(slot("core", "hide"), configuredItem("core", "hide"));
        inventory.setItem(slot("core", "close"), configuredItem("core", "close"));
        player.openInventory(inventory);
    }

    public void openTrustMenu(Player player, Claim claim, int page) {
        TrustMenuHolder holder = new TrustMenuHolder(claim.id(), page);
        Inventory inventory = Bukkit.createInventory(holder, menuSize("trust"),
            menuTitle("trust", "{name}", claim.name()));
        holder.inventory = inventory;
        fill(inventory, "trust", "filler");

        List<UUID> trustedPlayers = new ArrayList<>(claim.trustedMembers());
        List<Integer> trustedSlots = slots("trust", "trusted-entry");
        int start = Math.max(0, page) * trustedSlots.size();
        int end = Math.min(trustedPlayers.size(), start + trustedSlots.size());

        for (int index = start; index < end; index++) {
            UUID trustedId = trustedPlayers.get(index);
            inventory.setItem(trustedSlots.get(index - start), playerHead(trustedId,
                itemName("trust", "trusted-entry", "{player}", playerName(trustedId)),
                itemLore("trust", "trusted-entry", "{player}", playerName(trustedId), "{name}", claim.name())
            ));
        }
        if (trustedPlayers.isEmpty()) {
            inventory.setItem(slot("trust", "empty"), configuredItem("trust", "empty"));
        }

        List<Integer> candidateSlots = slots("trust", "candidate-entry");
        List<Player> candidates = Bukkit.getOnlinePlayers().stream()
            .filter(online -> !online.getUniqueId().equals(claim.owner()))
            .filter(online -> !claim.isTrusted(online.getUniqueId()))
            .map(online -> (Player) online)
            .toList();
        for (int index = 0; index < Math.min(candidateSlots.size(), candidates.size()); index++) {
            Player candidate = candidates.get(index);
            inventory.setItem(candidateSlots.get(index), playerHead(candidate.getUniqueId(),
                itemName("trust", "candidate-entry", "{player}", candidate.getName()),
                itemLore("trust", "candidate-entry", "{player}", candidate.getName(), "{name}", claim.name())
            ));
        }

        inventory.setItem(slot("trust", "prev-page"), configuredItem("trust", "prev-page"));
        inventory.setItem(slot("trust", "back"), configuredItem("trust", "back"));
        inventory.setItem(slot("trust", "next-page"), configuredItem("trust", "next-page"));
        player.openInventory(inventory);
    }

    public void handleClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        if (!(event.getInventory().getHolder() instanceof BaseHolder holder)) {
            return;
        }

        event.setCancelled(true);
        int slot = event.getRawSlot();
        if (slot < 0 || slot >= event.getInventory().getSize()) {
            return;
        }

        if (holder instanceof MainMenuHolder) {
            handleMainMenu(player, slot);
        } else if (holder instanceof ClaimListHolder claimListHolder) {
            handleClaimListMenu(player, claimListHolder, slot, event.isRightClick());
        } else if (holder instanceof ClaimManageHolder claimManageHolder) {
            handleClaimManageMenu(player, claimManageHolder, slot);
        } else if (holder instanceof TrustMenuHolder trustMenuHolder) {
            handleTrustMenu(player, trustMenuHolder, slot);
        } else if (holder instanceof CoreMenuHolder coreMenuHolder) {
            handleCoreMenu(player, coreMenuHolder, slot);
        }
    }

    private void handleMainMenu(Player player, int slot) {
        if (slot == slot("main", "current-claim")) {
            playConfiguredSound(player, "main", "current-claim");
            Claim claim = claimActionService.findOwnedClaim(player);
            if (claim == null) {
                player.sendMessage(plugin.message("menu-no-current-claim"));
                return;
            }
            openClaimManageMenu(player, claim);
        } else if (slot == slot("main", "claim-list")) {
            playConfiguredSound(player, "main", "claim-list");
            openClaimListMenu(player, 0);
        } else if (slot == slot("main", "close")) {
            playConfiguredSound(player, "main", "close");
            player.closeInventory();
        }
    }

    private void handleClaimListMenu(Player player, ClaimListHolder holder, int slot, boolean rightClick) {
        List<Claim> claims = claimService.claimsOf(player.getUniqueId());
        List<Integer> entrySlots = slots("claim-list", "entry");
        int slotIndex = entrySlots.indexOf(slot);
        int index = holder.page * entrySlots.size() + slotIndex;
        if (slotIndex >= 0 && index < claims.size()) {
            Claim claim = claims.get(index);
            if (rightClick) {
                playConfiguredSound(player, "claim-list", "entry");
                claimActionService.teleportToClaim(player, claim);
            } else {
                playConfiguredSound(player, "claim-list", "entry");
                openClaimManageMenu(player, claim);
            }
            return;
        }
        if (slot == slot("claim-list", "prev-page") && holder.page > 0) {
            playConfiguredSound(player, "claim-list", "prev-page");
            openClaimListMenu(player, holder.page - 1);
        } else if (slot == slot("claim-list", "back")) {
            playConfiguredSound(player, "claim-list", "back");
            openMainMenu(player);
        } else if (slot == slot("claim-list", "next-page") && (holder.page + 1) * entrySlots.size() < claims.size()) {
            playConfiguredSound(player, "claim-list", "next-page");
            openClaimListMenu(player, holder.page + 1);
        }
    }

    private void handleClaimManageMenu(Player player, ClaimManageHolder holder, int slot) {
        Claim claim = claimService.findClaimById(holder.claimId).orElse(null);
        if (claim == null) {
            player.closeInventory();
            player.sendMessage(plugin.message("claim-not-found"));
            return;
        }

        if (slot == slot("claim-manage", "expand-north")) {
            playConfiguredSound(player, "claim-manage", "expand-north");
            if (claimActionService.expandClaim(player, claim, ClaimDirection.NORTH, plugin.settings().directionExpandAmount())) {
                openClaimManageMenu(player, claim);
            }
        } else if (slot == slot("claim-manage", "expand-south")) {
            playConfiguredSound(player, "claim-manage", "expand-south");
            if (claimActionService.expandClaim(player, claim, ClaimDirection.SOUTH, plugin.settings().directionExpandAmount())) {
                openClaimManageMenu(player, claim);
            }
        } else if (slot == slot("claim-manage", "expand-west")) {
            playConfiguredSound(player, "claim-manage", "expand-west");
            if (claimActionService.expandClaim(player, claim, ClaimDirection.WEST, plugin.settings().directionExpandAmount())) {
                openClaimManageMenu(player, claim);
            }
        } else if (slot == slot("claim-manage", "expand-east")) {
            playConfiguredSound(player, "claim-manage", "expand-east");
            if (claimActionService.expandClaim(player, claim, ClaimDirection.EAST, plugin.settings().directionExpandAmount())) {
                openClaimManageMenu(player, claim);
            }
        } else if (slot == slot("claim-manage", "trust")) {
            playConfiguredSound(player, "claim-manage", "trust");
            openTrustMenu(player, claim, 0);
        } else if (slot == slot("claim-manage", "teleport")) {
            playConfiguredSound(player, "claim-manage", "teleport");
            claimActionService.teleportToClaim(player, claim);
        } else if (slot == slot("claim-manage", "delete")) {
            playConfiguredSound(player, "claim-manage", "delete");
            if (removalConfirmationService.request(player, claim)) {
                player.closeInventory();
            }
        } else if (slot == slot("claim-manage", "back")) {
            playConfiguredSound(player, "claim-manage", "back");
            openClaimListMenu(player, 0);
        }
    }

    private void handleTrustMenu(Player player, TrustMenuHolder holder, int slot) {
        Claim claim = claimService.findClaimById(holder.claimId).orElse(null);
        if (claim == null) {
            player.closeInventory();
            player.sendMessage(plugin.message("claim-not-found"));
            return;
        }

        List<UUID> trustedPlayers = new ArrayList<>(claim.trustedMembers());
        List<Integer> trustedSlots = slots("trust", "trusted-entry");
        int start = holder.page * trustedSlots.size();
        int end = Math.min(trustedPlayers.size(), start + trustedSlots.size());
        for (int index = start; index < end; index++) {
            if (trustedSlots.get(index - start) == slot) {
                playConfiguredSound(player, "trust", "trusted-entry");
                OfflinePlayer target = Bukkit.getOfflinePlayer(trustedPlayers.get(index));
                if (claimActionService.untrustPlayer(player, claim, target)) {
                    openTrustMenu(player, claim, holder.page);
                }
                return;
            }
        }

        List<Integer> candidateSlots = slots("trust", "candidate-entry");
        List<Player> candidates = Bukkit.getOnlinePlayers().stream()
            .filter(online -> !online.getUniqueId().equals(claim.owner()))
            .filter(online -> !claim.isTrusted(online.getUniqueId()))
            .map(online -> (Player) online)
            .limit(candidateSlots.size())
            .toList();
        for (int index = 0; index < candidates.size(); index++) {
            if (candidateSlots.get(index) == slot) {
                playConfiguredSound(player, "trust", "candidate-entry");
                if (claimActionService.trustPlayer(player, claim, candidates.get(index))) {
                    openTrustMenu(player, claim, holder.page);
                }
                return;
            }
        }

        if (slot == slot("trust", "prev-page") && holder.page > 0) {
            playConfiguredSound(player, "trust", "prev-page");
            openTrustMenu(player, claim, holder.page - 1);
        } else if (slot == slot("trust", "back")) {
            playConfiguredSound(player, "trust", "back");
            openClaimManageMenu(player, claim);
        } else if (slot == slot("trust", "next-page") && (holder.page + 1) * trustedSlots.size() < trustedPlayers.size()) {
            playConfiguredSound(player, "trust", "next-page");
            openTrustMenu(player, claim, holder.page + 1);
        }
    }

    private void handleCoreMenu(Player player, CoreMenuHolder holder, int slot) {
        Claim claim = claimService.findClaimById(holder.claimId).orElse(null);
        if (claim == null) {
            player.closeInventory();
            player.sendMessage(plugin.message("claim-not-found"));
            return;
        }
        if (slot == slot("core", "teleport")) {
            playConfiguredSound(player, "core", "teleport");
            claimActionService.teleportToClaim(player, claim);
        } else if (slot == slot("core", "settings")) {
            playConfiguredSound(player, "core", "settings");
            openClaimManageMenu(player, claim);
        } else if (slot == slot("core", "hide")) {
            playConfiguredSound(player, "core", "hide");
            if (claimActionService.hideClaimCore(player, claim)) {
                player.closeInventory();
            }
        } else if (slot == slot("core", "close")) {
            playConfiguredSound(player, "core", "close");
            player.closeInventory();
        }
    }

    private void fill(Inventory inventory, String menuKey, String itemKey) {
        ItemStack filler = configuredItem(menuKey, itemKey);
        List<Integer> fillerSlots = slots(menuKey, itemKey);
        if (fillerSlots.isEmpty()) {
            for (int slot = 0; slot < inventory.getSize(); slot++) {
                inventory.setItem(slot, filler.clone());
            }
            return;
        }
        for (int slot : fillerSlots) {
            inventory.setItem(slot, filler.clone());
        }
    }

    private FileConfiguration menu(String menuKey) {
        return plugin.menuConfig(menuKey);
    }

    private int menuSize(String menuKey) {
        List<String> layout = menu(menuKey).getStringList("GuiPlain");
        if (!layout.isEmpty()) {
            return layout.size() * 9;
        }
        return menu(menuKey).getInt("size", 27);
    }

    private String menuTitle(String menuKey, String... replacements) {
        return plugin.color(apply(menu(menuKey).getString("title", menuKey), replacements));
    }

    private int slot(String menuKey, String itemKey) {
        ConfigurationSection section = menu(menuKey).getConfigurationSection("items." + itemKey);
        if (section == null) {
            return 0;
        }
        if (section.contains("slot")) {
            return section.getInt("slot", 0);
        }
        List<Integer> slots = slots(menuKey, itemKey);
        return slots.isEmpty() ? 0 : slots.get(0);
    }

    private List<Integer> slots(String menuKey, String itemKey) {
        ConfigurationSection section = menu(menuKey).getConfigurationSection("items." + itemKey);
        List<Integer> result = new ArrayList<>();
        if (section == null) {
            return result;
        }
        if (section.contains("slot")) {
            result.add(section.getInt("slot", 0));
            return result;
        }
        String rawChar = section.getString("char", "");
        if (rawChar.isBlank()) {
            return result;
        }
        char symbol = rawChar.charAt(0);
        List<String> layout = menu(menuKey).getStringList("GuiPlain");
        for (int row = 0; row < layout.size(); row++) {
            String line = padLayout(layout.get(row));
            for (int column = 0; column < 9; column++) {
                if (line.charAt(column) == symbol) {
                    result.add(row * 9 + column);
                }
            }
        }
        return result;
    }

    private String itemName(String menuKey, String itemKey, String... replacements) {
        return plugin.color(apply(menu(menuKey).getString("items." + itemKey + ".name", itemKey), replacements));
    }

    private List<String> itemLore(String menuKey, String itemKey, String... replacements) {
        List<String> source = menu(menuKey).getStringList("items." + itemKey + ".lore");
        List<String> result = new ArrayList<>();
        for (String line : source) {
            result.add(plugin.color(apply(line, replacements)));
        }
        return result;
    }

    private ItemStack configuredItem(String menuKey, String itemKey, String... replacements) {
        ConfigurationSection section = menu(menuKey).getConfigurationSection("items." + itemKey);
        if (section == null) {
            return item(Material.BARRIER, "&cMissing: " + menuKey + "." + itemKey);
        }
        Material material = Material.matchMaterial(section.getString("material", "BARRIER"));
        if (material == null) {
            material = Material.BARRIER;
        }
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return item;
        }
        meta.setDisplayName(plugin.color(apply(section.getString("name", itemKey), replacements)));
        List<String> lore = section.getStringList("lore");
        if (!lore.isEmpty()) {
            List<String> lines = new ArrayList<>();
            for (String line : lore) {
                lines.add(plugin.color(apply(line, replacements)));
            }
            meta.setLore(lines);
        }
        if (section.contains("custom-model-data")) {
            meta.setCustomModelData(section.getInt("custom-model-data"));
        }
        if (section.getBoolean("glow", false)) {
            meta.addEnchant(Enchantment.LUCK, 1, true);
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        }
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        item.setItemMeta(meta);
        if (material == Material.PLAYER_HEAD && item.getItemMeta() instanceof SkullMeta skullMeta) {
            String owner = section.getString("skull-owner", "");
            String texture = section.getString("skull-texture", "");
            if (!owner.isBlank()) {
                skullMeta.setOwningPlayer(Bukkit.getOfflinePlayer(owner));
                item.setItemMeta(skullMeta);
            } else if (!texture.isBlank()) {
                applySkullTexture(skullMeta, texture);
                item.setItemMeta(skullMeta);
            }
        }
        return item;
    }

    private void playConfiguredSound(Player player, String menuKey, String itemKey) {
        ConfigurationSection section = menu(menuKey).getConfigurationSection("items." + itemKey);
        if (section == null) {
            return;
        }
        String rawSound = section.getString("sound", "");
        if (rawSound == null || rawSound.isBlank()) {
            return;
        }
        try {
            Sound sound = Sound.valueOf(rawSound.toUpperCase());
            float volume = (float) section.getDouble("sound-volume", 1D);
            float pitch = (float) section.getDouble("sound-pitch", 1D);
            player.playSound(player.getLocation(), sound, volume, pitch);
        } catch (IllegalArgumentException ignored) {
        }
    }

    private void applySkullTexture(SkullMeta meta, String texture) {
        try {
            Class<?> gameProfileClass = Class.forName("com.mojang.authlib.GameProfile");
            Class<?> propertyClass = Class.forName("com.mojang.authlib.properties.Property");
            Object gameProfile = gameProfileClass
                .getConstructor(UUID.class, String.class)
                .newInstance(UUID.nameUUIDFromBytes(texture.getBytes()), "coreclaim_head");
            Object property = propertyClass
                .getConstructor(String.class, String.class)
                .newInstance("textures", normalizeTexture(texture));
            Method getProperties = gameProfileClass.getMethod("getProperties");
            Object propertyMap = getProperties.invoke(gameProfile);
            Method put = propertyMap.getClass().getMethod("put", Object.class, Object.class);
            put.invoke(propertyMap, "textures", property);

            Field profileField = meta.getClass().getDeclaredField("profile");
            profileField.setAccessible(true);
            profileField.set(meta, gameProfile);
        } catch (Throwable ignored) {
        }
    }

    private String normalizeTexture(String texture) {
        if (texture.startsWith("http://") || texture.startsWith("https://")) {
            String payload = "{\"textures\":{\"SKIN\":{\"url\":\"" + texture + "\"}}}";
            return Base64.getEncoder().encodeToString(payload.getBytes());
        }
        return texture;
    }

    private String padLayout(String line) {
        String value = line == null ? "" : line;
        if (value.length() >= 9) {
            return value.substring(0, 9);
        }
        return String.format("%-9s", value);
    }

    private String apply(String text, String... replacements) {
        String result = text == null ? "" : text;
        for (int index = 0; index + 1 < replacements.length; index += 2) {
            result = result.replace(replacements[index], replacements[index + 1]);
        }
        return result;
    }

    private ItemStack item(Material material, String name, String... lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return item;
        }
        meta.setDisplayName(plugin.color(name));
        if (lore.length > 0) {
            List<String> lines = new ArrayList<>();
            for (String line : lore) {
                lines.add(plugin.color(line));
            }
            meta.setLore(lines);
        }
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack playerHead(UUID playerId, String name, List<String> lore) {
        ItemStack item = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) item.getItemMeta();
        if (meta == null) {
            return item(Material.PAPER, name, lore.toArray(String[]::new));
        }
        Player online = Bukkit.getPlayer(playerId);
        if (online != null) {
            meta.setOwningPlayer(online);
        }
        meta.setDisplayName(plugin.color(name));
        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private String playerName(UUID playerId) {
        OfflinePlayer player = Bukkit.getOfflinePlayer(playerId);
        return player.getName() == null ? playerId.toString() : player.getName();
    }

    private abstract static class BaseHolder implements InventoryHolder {
        protected Inventory inventory;

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }

    private static final class MainMenuHolder extends BaseHolder {
    }

    private static final class ClaimListHolder extends BaseHolder {
        private final int page;

        private ClaimListHolder(int page) {
            this.page = page;
        }
    }

    private static final class ClaimManageHolder extends BaseHolder {
        private final int claimId;

        private ClaimManageHolder(int claimId) {
            this.claimId = claimId;
        }
    }

    private static final class TrustMenuHolder extends BaseHolder {
        private final int claimId;
        private final int page;

        private TrustMenuHolder(int claimId, int page) {
            this.claimId = claimId;
            this.page = page;
        }
    }

    private static final class CoreMenuHolder extends BaseHolder {
        private final int claimId;

        private CoreMenuHolder(int claimId) {
            this.claimId = claimId;
        }
    }
}
