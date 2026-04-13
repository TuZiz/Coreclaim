package com.coreclaim.gui;

import com.coreclaim.CoreClaimPlugin;
import com.coreclaim.config.ClaimGroup;
import com.coreclaim.model.Claim;
import com.coreclaim.model.ClaimMemberSettings;
import com.coreclaim.model.ClaimDirection;
import com.coreclaim.model.ClaimPermission;
import com.coreclaim.model.ClaimSaleListing;
import com.coreclaim.model.PlayerProfile;
import com.coreclaim.service.ClaimActionService;
import com.coreclaim.service.ClaimInputService;
import com.coreclaim.service.ClaimMarketService;
import com.coreclaim.service.ClaimSelectionService;
import com.coreclaim.service.ClaimService;
import com.coreclaim.service.ProfileService;
import com.coreclaim.service.RemovalConfirmationService;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
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
    private final ClaimInputService claimInputService;
    private final ClaimSelectionService claimSelectionService;
    private final ClaimMarketService claimMarketService;

    public MenuService(
        CoreClaimPlugin plugin,
        ClaimService claimService,
        ProfileService profileService,
        ClaimActionService claimActionService,
        RemovalConfirmationService removalConfirmationService,
        ClaimInputService claimInputService,
        ClaimSelectionService claimSelectionService,
        ClaimMarketService claimMarketService
    ) {
        this.plugin = plugin;
        this.claimService = claimService;
        this.profileService = profileService;
        this.claimActionService = claimActionService;
        this.removalConfirmationService = removalConfirmationService;
        this.claimInputService = claimInputService;
        this.claimSelectionService = claimSelectionService;
        this.claimMarketService = claimMarketService;
    }

    public void openMainMenu(Player player) {
        Claim currentClaim = claimActionService.findOwnedClaim(player);
        if (currentClaim != null) {
            openCoreMenu(player, currentClaim);
            return;
        }
        openClaimListMenu(player, 0);
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

        inventory.setItem(slot("claim-list", "refresh"), configuredItem("claim-list", "refresh", "{total}", String.valueOf(claims.size())));
        inventory.setItem(slot("claim-list", "prev-page"), configuredItem("claim-list", "prev-page"));
        inventory.setItem(slot("claim-list", "back"), configuredItem("claim-list", "back"));
        inventory.setItem(slot("claim-list", "next-page"), configuredItem("claim-list", "next-page"));
        player.openInventory(inventory);
    }

    public void openClaimManageMenu(Player player, Claim claim) {
        ClaimManageHolder holder = new ClaimManageHolder(claim.id());
        Inventory inventory = Bukkit.createInventory(holder, menuSize("claim-manage"), menuTitle("claim-manage", "{name}", claim.name()));
        holder.inventory = inventory;
        fill(inventory, "claim-manage", "filler");

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
            "{area}", String.valueOf(claim.area())
        ));
        inventory.setItem(slot("claim-manage", "expand-north"), configuredItem("claim-manage", "expand-north",
            "{amount}", String.valueOf(north.expandAmount()), "{price}", north.costText(), "{current}", String.valueOf(claim.north()), "{target}", String.valueOf(north.targetDistance())));
        inventory.setItem(slot("claim-manage", "expand-south"), configuredItem("claim-manage", "expand-south",
            "{amount}", String.valueOf(south.expandAmount()), "{price}", south.costText(), "{current}", String.valueOf(claim.south()), "{target}", String.valueOf(south.targetDistance())));
        inventory.setItem(slot("claim-manage", "expand-west"), configuredItem("claim-manage", "expand-west",
            "{amount}", String.valueOf(west.expandAmount()), "{price}", west.costText(), "{current}", String.valueOf(claim.west()), "{target}", String.valueOf(west.targetDistance())));
        inventory.setItem(slot("claim-manage", "expand-east"), configuredItem("claim-manage", "expand-east",
            "{amount}", String.valueOf(east.expandAmount()), "{price}", east.costText(), "{current}", String.valueOf(claim.east()), "{target}", String.valueOf(east.targetDistance())));
        inventory.setItem(slot("claim-manage", "delete"), configuredItem("claim-manage", "delete"));
        inventory.setItem(slot("claim-manage", "back"), configuredItem("claim-manage", "back"));
        player.openInventory(inventory);
    }

    public void openCoreMenu(Player player, Claim claim) {
        CoreMenuHolder holder = new CoreMenuHolder(claim.id());
        Inventory inventory = Bukkit.createInventory(holder, menuSize("core"), menuTitle("core", "{name}", claim.name()));
        holder.inventory = inventory;
        fill(inventory, "core", "filler");

        PlayerProfile profile = profileService.getOrCreate(player.getUniqueId(), player.getName());
        ClaimGroup group = plugin.groups().resolve(player);
        int claimCount = claimService.countClaims(player.getUniqueId());
        int maxClaims = group.claimSlotsForActivity(profile.activityPoints());

        inventory.setItem(slot("core", "info"), configuredItem("core", "info",
            "{name}", claim.name(),
            "{claims}", String.valueOf(claimCount),
            "{max_claims}", String.valueOf(maxClaims),
            "{center_x}", String.valueOf(claim.centerX()),
            "{center_z}", String.valueOf(claim.centerZ()),
            "{width}", String.valueOf(claim.width()),
            "{depth}", String.valueOf(claim.depth()),
            "{area}", String.valueOf(claim.area()),
            "{activity}", String.valueOf(profile.activityPoints()),
            "{trusted_count}", String.valueOf(claim.trustedCount()),
            "{enter_message}", displayNotifyPreview(claim.enterMessage(), claim, "默认进入提示"),
            "{leave_message}", displayNotifyPreview(claim.leaveMessage(), claim, "默认离开提示")
        ));
        inventory.setItem(slot("core", "expand"), configuredItem("core", "expand"));
        inventory.setItem(slot("core", "claim-list"), configuredItem("core", "claim-list", "{total}", String.valueOf(claimCount)));
        inventory.setItem(slot("core", "trust"), configuredItem("core", "trust", "{name}", claim.name(), "{trusted_count}", String.valueOf(claim.trustedCount())));
        inventory.setItem(slot("core", "permissions"), configuredItem("core", "permissions"));
        inventory.setItem(slot("core", "rename"), configuredItem("core", "rename", "{name}", claim.name()));
        inventory.setItem(slot("core", "notify"), configuredItem("core", "notify",
            "{name}", claim.name(),
            "{enter_current}", displayNotifyPreview(claim.enterMessage(), claim, "默认进入提示"),
            "{leave_current}", displayNotifyPreview(claim.leaveMessage(), claim, "默认离开提示")
        ));
        inventory.setItem(slot("core", "hide"), configuredItem("core", "hide"));
        inventory.setItem(slot("core", "teleport"), configuredItem("core", "teleport"));
        if (hasItem("core", "sell")) {
            ClaimSaleListing listing = claimMarketService.listing(claim.id());
            inventory.setItem(slot("core", "sell"), configuredItem("core", "sell",
                "{name}", claim.name(),
                "{sale_status}", listing == null ? "&a未挂牌" : "&e已挂牌",
                "{sale_price}", listing == null ? "--" : ClaimActionService.formatMoney(listing.price())
            ));
        }
        inventory.setItem(slot("core", "close"), configuredItem("core", "close"));
        player.openInventory(inventory);
    }

    public void openClaimMarketMenu(Player player, int page) {
        ClaimMarketHolder holder = new ClaimMarketHolder(Math.max(0, page));
        Inventory inventory = Bukkit.createInventory(holder, menuSize("claim-market"), menuTitle("claim-market"));
        holder.inventory = inventory;
        fill(inventory, "claim-market", "filler");

        List<ClaimSaleListing> listings = claimMarketService.listings();
        List<Integer> entrySlots = slots("claim-market", "entry");
        if (entrySlots.isEmpty()) {
            warnMissingMarketEntrySlots(player);
            return;
        }
        int start = holder.page * entrySlots.size();
        int end = Math.min(listings.size(), start + entrySlots.size());
        for (int index = start; index < end; index++) {
            ClaimSaleListing listing = listings.get(index);
            Claim claim = claimService.findClaimByIdOrLoad(listing.claimId()).orElse(null);
            if (claim == null) {
                continue;
            }
            inventory.setItem(entrySlots.get(index - start), configuredItem("claim-market", "entry",
                "{name}", claim.name(),
                "{seller}", listing.sellerName(),
                "{world}", claim.world(),
                "{x}", String.valueOf(claim.centerX()),
                "{y}", String.valueOf(claim.centerY()),
                "{z}", String.valueOf(claim.centerZ()),
                "{width}", String.valueOf(claim.width()),
                "{depth}", String.valueOf(claim.depth()),
                "{area}", String.valueOf(claim.area()),
                "{price}", ClaimActionService.formatMoney(listing.price())
            ));
        }
        if (listings.isEmpty() && hasItem("claim-market", "empty")) {
            inventory.setItem(slot("claim-market", "empty"), configuredItem("claim-market", "empty"));
        }

        inventory.setItem(slot("claim-market", "refresh"), configuredItem("claim-market", "refresh", "{total}", String.valueOf(listings.size())));
        inventory.setItem(slot("claim-market", "prev-page"), configuredItem("claim-market", "prev-page"));
        inventory.setItem(slot("claim-market", "back"), configuredItem("claim-market", "back"));
        inventory.setItem(slot("claim-market", "next-page"), configuredItem("claim-market", "next-page"));
        player.openInventory(inventory);
    }

    public void openClaimSaleConfirmMenu(Player player, ClaimSaleListing listing, int returnPage) {
        Claim claim = claimService.findClaimByIdOrLoad(listing.claimId()).orElse(null);
        if (claim == null) {
            player.sendMessage(plugin.message("claim-not-found"));
            openClaimMarketMenu(player, returnPage);
            return;
        }
        SaleConfirmHolder holder = new SaleConfirmHolder(listing.claimId(), Math.max(0, returnPage));
        Inventory inventory = Bukkit.createInventory(
            holder,
            menuSize("claim-sale-confirm"),
            menuTitle("claim-sale-confirm", "{name}", claim.name())
        );
        holder.inventory = inventory;
        fill(inventory, "claim-sale-confirm", "filler");

        inventory.setItem(slot("claim-sale-confirm", "info"), configuredItem("claim-sale-confirm", "info",
            "{name}", claim.name(),
            "{seller}", listing.sellerName(),
            "{world}", claim.world(),
            "{x}", String.valueOf(claim.centerX()),
            "{y}", String.valueOf(claim.centerY()),
            "{z}", String.valueOf(claim.centerZ()),
            "{width}", String.valueOf(claim.width()),
            "{depth}", String.valueOf(claim.depth()),
            "{area}", String.valueOf(claim.area()),
            "{price}", ClaimActionService.formatMoney(listing.price())
        ));
        inventory.setItem(slot("claim-sale-confirm", "confirm"), configuredItem("claim-sale-confirm", "confirm",
            "{name}", claim.name(),
            "{seller}", listing.sellerName(),
            "{price}", ClaimActionService.formatMoney(listing.price())
        ));
        inventory.setItem(slot("claim-sale-confirm", "cancel"), configuredItem("claim-sale-confirm", "cancel"));
        player.openInventory(inventory);
    }

    public void openTrustMenu(Player player, Claim claim, int page) {
        TrustMenuHolder holder = new TrustMenuHolder(claim.id(), page);
        Inventory inventory = Bukkit.createInventory(holder, menuSize("trust"), menuTitle("trust", "{name}", claim.name()));
        holder.inventory = inventory;
        fill(inventory, "trust", "filler");

        List<UUID> trustedPlayers = new ArrayList<>(claim.trustedMembers());
        List<Integer> entrySlots = slots("trust", "trusted-entry");
        int start = Math.max(0, page) * entrySlots.size();
        int end = Math.min(trustedPlayers.size(), start + entrySlots.size());
        for (int index = start; index < end; index++) {
            UUID trustedId = trustedPlayers.get(index);
            inventory.setItem(entrySlots.get(index - start), playerHead("trust", "trusted-entry", trustedId,
                "{player}", playerName(trustedId), "{name}", claim.name()
            ));
        }
        if (trustedPlayers.isEmpty()) {
            inventory.setItem(slot("trust", "empty"), configuredItem("trust", "empty", "{name}", claim.name()));
        }

        inventory.setItem(slot("trust", "refresh"), configuredItem("trust", "refresh"));
        inventory.setItem(slot("trust", "add-player"), configuredItem("trust", "add-player", "{name}", claim.name(), "{trusted_count}", String.valueOf(claim.trustedCount())));
        inventory.setItem(slot("trust", "prev-page"), configuredItem("trust", "prev-page"));
        inventory.setItem(slot("trust", "back"), configuredItem("trust", "back"));
        inventory.setItem(slot("trust", "next-page"), configuredItem("trust", "next-page"));
        player.openInventory(inventory);
    }

    public void openTrustOnlineMenu(Player player, Claim claim, int page) {
        TrustOnlineHolder holder = new TrustOnlineHolder(claim.id(), page);
        Inventory inventory = Bukkit.createInventory(holder, menuSize("trust-online"), menuTitle("trust-online", "{name}", claim.name()));
        holder.inventory = inventory;
        fill(inventory, "trust-online", "filler");

        List<Player> candidates = onlineCandidates(claim);
        List<Integer> entrySlots = slots("trust-online", "entry");
        int start = Math.max(0, page) * entrySlots.size();
        int end = Math.min(candidates.size(), start + entrySlots.size());
        for (int index = start; index < end; index++) {
            Player candidate = candidates.get(index);
            inventory.setItem(entrySlots.get(index - start), playerHead("trust-online", "entry", candidate.getUniqueId(),
                "{player}", candidate.getName(), "{name}", claim.name()
            ));
        }
        if (candidates.isEmpty() && hasItem("trust-online", "empty")) {
            inventory.setItem(slot("trust-online", "empty"), configuredItem("trust-online", "empty"));
        }

        inventory.setItem(slot("trust-online", "refresh"), configuredItem("trust-online", "refresh"));
        inventory.setItem(slot("trust-online", "prev-page"), configuredItem("trust-online", "prev-page"));
        inventory.setItem(slot("trust-online", "back"), configuredItem("trust-online", "back"));
        inventory.setItem(slot("trust-online", "next-page"), configuredItem("trust-online", "next-page"));
        player.openInventory(inventory);
    }

    public void openClaimPermissionsMenu(Player player, Claim claim) {
        ClaimPermissionHolder holder = new ClaimPermissionHolder(claim.id());
        Inventory inventory = Bukkit.createInventory(holder, menuSize("claim-permissions"), menuTitle("claim-permissions", "{name}", claim.name()));
        holder.inventory = inventory;
        fill(inventory, "claim-permissions", "filler");

        inventory.setItem(slot("claim-permissions", "info"), configuredItem("claim-permissions", "info",
            "{name}", claim.name(),
            "{trusted_count}", String.valueOf(claim.trustedCount()),
            "{perm_place}", stateText(claim.permission(ClaimPermission.PLACE)),
            "{perm_break}", stateText(claim.permission(ClaimPermission.BREAK)),
            "{perm_interact}", stateText(claim.permission(ClaimPermission.INTERACT)),
            "{perm_container}", stateText(claim.permission(ClaimPermission.CONTAINER)),
            "{perm_redstone}", stateText(claim.permission(ClaimPermission.REDSTONE)),
            "{perm_explosion}", stateText(claim.permission(ClaimPermission.EXPLOSION)),
            "{perm_bucket}", stateText(claim.permission(ClaimPermission.BUCKET)),
            "{perm_teleport}", stateText(claim.permission(ClaimPermission.TELEPORT)),
            "{perm_flight}", stateText(claim.permission(ClaimPermission.FLIGHT))
        ));
        inventory.setItem(slot("claim-permissions", "perm-place"), configuredItem("claim-permissions", "perm-place", "{state}", stateText(claim.permission(ClaimPermission.PLACE))));
        inventory.setItem(slot("claim-permissions", "perm-break"), configuredItem("claim-permissions", "perm-break", "{state}", stateText(claim.permission(ClaimPermission.BREAK))));
        inventory.setItem(slot("claim-permissions", "perm-interact"), configuredItem("claim-permissions", "perm-interact", "{state}", stateText(claim.permission(ClaimPermission.INTERACT))));
        inventory.setItem(slot("claim-permissions", "perm-container"), configuredItem("claim-permissions", "perm-container", "{state}", stateText(claim.permission(ClaimPermission.CONTAINER))));
        inventory.setItem(slot("claim-permissions", "perm-redstone"), configuredItem("claim-permissions", "perm-redstone", "{state}", stateText(claim.permission(ClaimPermission.REDSTONE))));
        inventory.setItem(slot("claim-permissions", "perm-explosion"), configuredItem("claim-permissions", "perm-explosion", "{state}", stateText(claim.permission(ClaimPermission.EXPLOSION))));
        inventory.setItem(slot("claim-permissions", "perm-bucket"), configuredItem("claim-permissions", "perm-bucket", "{state}", stateText(claim.permission(ClaimPermission.BUCKET))));
        inventory.setItem(slot("claim-permissions", "perm-teleport"), configuredItem("claim-permissions", "perm-teleport", "{state}", stateText(claim.permission(ClaimPermission.TELEPORT))));
        if (hasItem("claim-permissions", "perm-flight")) {
            inventory.setItem(slot("claim-permissions", "perm-flight"), configuredItem("claim-permissions", "perm-flight", "{state}", stateText(claim.permission(ClaimPermission.FLIGHT))));
        }
        inventory.setItem(slot("claim-permissions", "disable-all"), configuredItem("claim-permissions", "disable-all"));
        inventory.setItem(slot("claim-permissions", "back"), configuredItem("claim-permissions", "back"));
        player.openInventory(inventory);
    }

    public void openTrustMemberPermissionMenu(Player player, Claim claim, UUID memberId) {
        TrustMemberPermissionHolder holder = new TrustMemberPermissionHolder(claim.id(), memberId);
        Inventory inventory = Bukkit.createInventory(
            holder,
            menuSize("trust-member-permissions"),
            menuTitle("trust-member-permissions", "{name}", claim.name(), "{player}", playerName(memberId))
        );
        holder.inventory = inventory;
        fill(inventory, "trust-member-permissions", "filler");

        ClaimMemberSettings settings = claimService.memberSettings(claim, memberId);
        inventory.setItem(slot("trust-member-permissions", "info"), configuredItem("trust-member-permissions", "info",
            "{name}", claim.name(),
            "{player}", playerName(memberId),
            "{perm_place}", stateText(settings.permission(ClaimPermission.PLACE)),
            "{perm_break}", stateText(settings.permission(ClaimPermission.BREAK)),
            "{perm_interact}", stateText(settings.permission(ClaimPermission.INTERACT)),
            "{perm_container}", stateText(settings.permission(ClaimPermission.CONTAINER)),
            "{perm_redstone}", stateText(settings.permission(ClaimPermission.REDSTONE)),
            "{perm_explosion}", stateText(settings.permission(ClaimPermission.EXPLOSION)),
            "{perm_bucket}", stateText(settings.permission(ClaimPermission.BUCKET)),
            "{perm_teleport}", stateText(settings.permission(ClaimPermission.TELEPORT)),
            "{perm_flight}", stateText(settings.permission(ClaimPermission.FLIGHT))
        ));
        inventory.setItem(slot("trust-member-permissions", "perm-place"), configuredItem("trust-member-permissions", "perm-place", "{state}", stateText(settings.permission(ClaimPermission.PLACE))));
        inventory.setItem(slot("trust-member-permissions", "perm-break"), configuredItem("trust-member-permissions", "perm-break", "{state}", stateText(settings.permission(ClaimPermission.BREAK))));
        inventory.setItem(slot("trust-member-permissions", "perm-interact"), configuredItem("trust-member-permissions", "perm-interact", "{state}", stateText(settings.permission(ClaimPermission.INTERACT))));
        if (hasItem("trust-member-permissions", "perm-container")) {
            inventory.setItem(slot("trust-member-permissions", "perm-container"), configuredItem("trust-member-permissions", "perm-container", "{state}", stateText(settings.permission(ClaimPermission.CONTAINER))));
        }
        if (hasItem("trust-member-permissions", "perm-redstone")) {
            inventory.setItem(slot("trust-member-permissions", "perm-redstone"), configuredItem("trust-member-permissions", "perm-redstone", "{state}", stateText(settings.permission(ClaimPermission.REDSTONE))));
        }
        if (hasItem("trust-member-permissions", "perm-explosion")) {
            inventory.setItem(slot("trust-member-permissions", "perm-explosion"), configuredItem("trust-member-permissions", "perm-explosion", "{state}", stateText(settings.permission(ClaimPermission.EXPLOSION))));
        }
        inventory.setItem(slot("trust-member-permissions", "perm-bucket"), configuredItem("trust-member-permissions", "perm-bucket", "{state}", stateText(settings.permission(ClaimPermission.BUCKET))));
        inventory.setItem(slot("trust-member-permissions", "perm-teleport"), configuredItem("trust-member-permissions", "perm-teleport", "{state}", stateText(settings.permission(ClaimPermission.TELEPORT))));
        if (hasItem("trust-member-permissions", "perm-flight")) {
            inventory.setItem(slot("trust-member-permissions", "perm-flight"), configuredItem("trust-member-permissions", "perm-flight", "{state}", stateText(settings.permission(ClaimPermission.FLIGHT))));
        }
        inventory.setItem(slot("trust-member-permissions", "back"), configuredItem("trust-member-permissions", "back"));
        player.openInventory(inventory);
    }

    public void openSelectionCreateMenu(Player player, String claimName, ClaimSelectionService.SelectionPreview preview) {
        SelectionCreateHolder holder = new SelectionCreateHolder(claimName);
        Inventory inventory = Bukkit.createInventory(holder, menuSize("selection-create"), menuTitle("selection-create", "{name}", claimName));
        holder.inventory = inventory;
        fill(inventory, "selection-create", "filler");

        String status = preview.allowed() ? "&a可创建" : "&c" + stripMessagePrefix(preview.failureMessage());
        inventory.setItem(slot("selection-create", "info"), configuredItem("selection-create", "info",
            "{name}", claimName,
            "{world}", preview.coreLocation() == null || preview.coreLocation().getWorld() == null ? plugin.settings().claimWorldsDisplay() : preview.coreLocation().getWorld().getName(),
            "{center_x}", preview.coreLocation() == null ? "-" : String.valueOf(preview.coreLocation().getBlockX()),
            "{center_y}", preview.coreLocation() == null ? "-" : String.valueOf(preview.coreLocation().getBlockY()),
            "{center_z}", preview.coreLocation() == null ? "-" : String.valueOf(preview.coreLocation().getBlockZ()),
            "{status}", plugin.color(status)
        ));
        inventory.setItem(slot("selection-create", "size"), configuredItem("selection-create", "size",
            "{width}", String.valueOf(preview.width()),
            "{height}", String.valueOf(preview.height()),
            "{depth}", String.valueOf(preview.depth()),
            "{area}", String.valueOf(preview.area()),
            "{volume}", String.valueOf(preview.volume())
        ));
        inventory.setItem(slot("selection-create", "price"), configuredItem("selection-create", "price",
            "{cost}", ClaimActionService.formatMoney(preview.cost())
        ));
        inventory.setItem(slot("selection-create", "bounds"), configuredItem("selection-create", "bounds",
            "{min_x}", String.valueOf(preview.minX()),
            "{max_x}", String.valueOf(preview.maxX()),
            "{min_y}", String.valueOf(preview.minY()),
            "{max_y}", String.valueOf(preview.maxY()),
            "{min_z}", String.valueOf(preview.minZ()),
            "{max_z}", String.valueOf(preview.maxZ())
        ));
        inventory.setItem(slot("selection-create", "tips"), configuredItem("selection-create", "tips"));
        inventory.setItem(slot("selection-create", "refresh"), configuredItem("selection-create", "refresh"));
        inventory.setItem(slot("selection-create", "confirm"), configuredItem("selection-create", "confirm",
            "{name}", claimName,
            "{cost}", ClaimActionService.formatMoney(preview.cost()),
            "{width}", String.valueOf(preview.width()),
            "{height}", String.valueOf(preview.height()),
            "{depth}", String.valueOf(preview.depth())
        ));
        if (hasItem("selection-create", "placeholder-left")) {
            inventory.setItem(slot("selection-create", "placeholder-left"), configuredItem("selection-create", "placeholder-left"));
        }
        if (hasItem("selection-create", "placeholder-right")) {
            inventory.setItem(slot("selection-create", "placeholder-right"), configuredItem("selection-create", "placeholder-right"));
        }
        inventory.setItem(slot("selection-create", "cancel"), configuredItem("selection-create", "cancel"));
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

        if (holder instanceof ClaimListHolder claimListHolder) {
            handleClaimListMenu(player, claimListHolder, slot, event.isRightClick());
        } else if (holder instanceof ClaimManageHolder claimManageHolder) {
            handleClaimManageMenu(player, claimManageHolder, slot);
        } else if (holder instanceof CoreMenuHolder coreMenuHolder) {
            handleCoreMenu(player, coreMenuHolder, slot, event.isRightClick());
        } else if (holder instanceof TrustMenuHolder trustMenuHolder) {
            handleTrustMenu(player, trustMenuHolder, slot, event.isRightClick());
        } else if (holder instanceof TrustOnlineHolder trustOnlineHolder) {
            handleTrustOnlineMenu(player, trustOnlineHolder, slot);
        } else if (holder instanceof ClaimPermissionHolder permissionHolder) {
            handlePermissionMenu(player, permissionHolder, slot);
        } else if (holder instanceof TrustMemberPermissionHolder permissionHolder) {
            handleTrustMemberPermissionMenu(player, permissionHolder, slot);
        } else if (holder instanceof SelectionCreateHolder selectionCreateHolder) {
            handleSelectionCreateMenu(player, selectionCreateHolder, slot);
        } else if (holder instanceof ClaimMarketHolder marketHolder) {
            handleClaimMarketMenu(player, marketHolder, slot, event.isRightClick());
        } else if (holder instanceof SaleConfirmHolder saleConfirmHolder) {
            handleSaleConfirmMenu(player, saleConfirmHolder, slot);
        }
    }

    private void handleClaimListMenu(Player player, ClaimListHolder holder, int slot, boolean rightClick) {
        List<Claim> claims = claimService.claimsOf(player.getUniqueId());
        List<Integer> entrySlots = slots("claim-list", "entry");
        int slotIndex = entrySlots.indexOf(slot);
        int index = holder.page * entrySlots.size() + slotIndex;
        if (slotIndex >= 0 && index < claims.size()) {
            Claim claim = claims.get(index);
            playConfiguredSound(player, "claim-list", "entry");
            if (rightClick) {
                claimActionService.teleportToClaim(player, claim);
            } else {
                openCoreMenu(player, claim);
            }
            return;
        }
        if (slot == slot("claim-list", "refresh")) {
            playConfiguredSound(player, "claim-list", "refresh");
            openClaimListMenu(player, holder.page);
            return;
        }
        if (slot == slot("claim-list", "prev-page") && holder.page > 0) {
            playConfiguredSound(player, "claim-list", "prev-page");
            openClaimListMenu(player, holder.page - 1);
            return;
        }
        if (slot == slot("claim-list", "back")) {
            playConfiguredSound(player, "claim-list", "back");
            player.closeInventory();
            return;
        }
        if (slot == slot("claim-list", "next-page") && (holder.page + 1) * entrySlots.size() < claims.size()) {
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
            return;
        }
        if (slot == slot("claim-manage", "expand-south")) {
            playConfiguredSound(player, "claim-manage", "expand-south");
            if (claimActionService.expandClaim(player, claim, ClaimDirection.SOUTH, plugin.settings().directionExpandAmount())) {
                openClaimManageMenu(player, claim);
            }
            return;
        }
        if (slot == slot("claim-manage", "expand-west")) {
            playConfiguredSound(player, "claim-manage", "expand-west");
            if (claimActionService.expandClaim(player, claim, ClaimDirection.WEST, plugin.settings().directionExpandAmount())) {
                openClaimManageMenu(player, claim);
            }
            return;
        }
        if (slot == slot("claim-manage", "expand-east")) {
            playConfiguredSound(player, "claim-manage", "expand-east");
            if (claimActionService.expandClaim(player, claim, ClaimDirection.EAST, plugin.settings().directionExpandAmount())) {
                openClaimManageMenu(player, claim);
            }
            return;
        }
        if (slot == slot("claim-manage", "delete")) {
            playConfiguredSound(player, "claim-manage", "delete");
            if (removalConfirmationService.request(player, claim)) {
                player.closeInventory();
            }
            return;
        }
        if (slot == slot("claim-manage", "back")) {
            playConfiguredSound(player, "claim-manage", "back");
            openCoreMenu(player, claim);
        }
    }

    private void handleCoreMenu(Player player, CoreMenuHolder holder, int slot, boolean rightClick) {
        Claim claim = claimService.findClaimById(holder.claimId).orElse(null);
        if (claim == null) {
            player.closeInventory();
            player.sendMessage(plugin.message("claim-not-found"));
            return;
        }

        if (slot == slot("core", "expand")) {
            playConfiguredSound(player, "core", "expand");
            openClaimManageMenu(player, claim);
            return;
        }
        if (slot == slot("core", "claim-list")) {
            playConfiguredSound(player, "core", "claim-list");
            openClaimListMenu(player, 0);
            return;
        }
        if (slot == slot("core", "trust")) {
            playConfiguredSound(player, "core", "trust");
            openTrustMenu(player, claim, 0);
            return;
        }
        if (slot == slot("core", "permissions")) {
            playConfiguredSound(player, "core", "permissions");
            openClaimPermissionsMenu(player, claim);
            return;
        }
        if (slot == slot("core", "rename")) {
            playConfiguredSound(player, "core", "rename");
            claimInputService.requestRename(player, claim);
            return;
        }
        if (slot == slot("core", "notify")) {
            playConfiguredSound(player, "core", "notify");
            if (rightClick) {
                claimInputService.requestLeaveMessage(player, claim);
            } else {
                claimInputService.requestEnterMessage(player, claim);
            }
            return;
        }
        if (slot == slot("core", "hide")) {
            playConfiguredSound(player, "core", "hide");
            if (claimActionService.hideClaimCore(player, claim)) {
                player.closeInventory();
            }
            return;
        }
        if (slot == slot("core", "teleport")) {
            playConfiguredSound(player, "core", "teleport");
            claimActionService.teleportToClaim(player, claim);
            return;
        }
        if (hasItem("core", "sell") && slot == slot("core", "sell")) {
            playConfiguredSound(player, "core", "sell");
            if (rightClick) {
                ClaimSaleListing listing = claimMarketService.listing(claim.id());
                if (listing != null) {
                    claimMarketService.cancelListing(player, claim);
                    openCoreMenu(player, claim);
                    return;
                }
            }
            openClaimMarketMenu(player, 0);
            return;
        }
        if (slot == slot("core", "close")) {
            playConfiguredSound(player, "core", "close");
            player.closeInventory();
        }
    }

    private void handleTrustMenu(Player player, TrustMenuHolder holder, int slot, boolean rightClick) {
        Claim claim = claimService.findClaimById(holder.claimId).orElse(null);
        if (claim == null) {
            player.closeInventory();
            player.sendMessage(plugin.message("claim-not-found"));
            return;
        }

        List<UUID> trustedPlayers = new ArrayList<>(claim.trustedMembers());
        List<Integer> entrySlots = slots("trust", "trusted-entry");
        int start = holder.page * entrySlots.size();
        int end = Math.min(trustedPlayers.size(), start + entrySlots.size());
        for (int index = start; index < end; index++) {
            if (entrySlots.get(index - start) == slot) {
                UUID targetId = trustedPlayers.get(index);
                playConfiguredSound(player, "trust", "trusted-entry");
                if (rightClick) {
                    OfflinePlayer target = Bukkit.getOfflinePlayer(targetId);
                    if (claimActionService.untrustPlayer(player, claim, target)) {
                        openTrustMenu(player, claim, holder.page);
                    }
                } else {
                    openTrustMemberPermissionMenu(player, claim, targetId);
                }
                return;
            }
        }

        if (slot == slot("trust", "refresh")) {
            playConfiguredSound(player, "trust", "refresh");
            openTrustMenu(player, claim, holder.page);
            return;
        }
        if (slot == slot("trust", "add-player")) {
            playConfiguredSound(player, "trust", "add-player");
            openTrustOnlineMenu(player, claim, 0);
            return;
        }
        if (slot == slot("trust", "prev-page") && holder.page > 0) {
            playConfiguredSound(player, "trust", "prev-page");
            openTrustMenu(player, claim, holder.page - 1);
            return;
        }
        if (slot == slot("trust", "back")) {
            playConfiguredSound(player, "trust", "back");
            openCoreMenu(player, claim);
            return;
        }
        if (slot == slot("trust", "next-page") && (holder.page + 1) * entrySlots.size() < trustedPlayers.size()) {
            playConfiguredSound(player, "trust", "next-page");
            openTrustMenu(player, claim, holder.page + 1);
        }
    }

    private void handleTrustOnlineMenu(Player player, TrustOnlineHolder holder, int slot) {
        Claim claim = claimService.findClaimById(holder.claimId).orElse(null);
        if (claim == null) {
            player.closeInventory();
            player.sendMessage(plugin.message("claim-not-found"));
            return;
        }

        List<Player> candidates = onlineCandidates(claim);
        List<Integer> entrySlots = slots("trust-online", "entry");
        int start = holder.page * entrySlots.size();
        int end = Math.min(candidates.size(), start + entrySlots.size());
        for (int index = start; index < end; index++) {
            if (entrySlots.get(index - start) == slot) {
                playConfiguredSound(player, "trust-online", "entry");
                if (claimActionService.trustPlayer(player, claim, candidates.get(index))) {
                    openTrustOnlineMenu(player, claim, holder.page);
                }
                return;
            }
        }

        if (slot == slot("trust-online", "refresh")) {
            playConfiguredSound(player, "trust-online", "refresh");
            openTrustOnlineMenu(player, claim, holder.page);
            return;
        }
        if (slot == slot("trust-online", "prev-page") && holder.page > 0) {
            playConfiguredSound(player, "trust-online", "prev-page");
            openTrustOnlineMenu(player, claim, holder.page - 1);
            return;
        }
        if (slot == slot("trust-online", "back")) {
            playConfiguredSound(player, "trust-online", "back");
            openTrustMenu(player, claim, 0);
            return;
        }
        if (slot == slot("trust-online", "next-page") && (holder.page + 1) * entrySlots.size() < candidates.size()) {
            playConfiguredSound(player, "trust-online", "next-page");
            openTrustOnlineMenu(player, claim, holder.page + 1);
        }
    }

    private void handlePermissionMenu(Player player, ClaimPermissionHolder holder, int slot) {
        Claim claim = claimService.findClaimById(holder.claimId).orElse(null);
        if (claim == null) {
            player.closeInventory();
            player.sendMessage(plugin.message("claim-not-found"));
            return;
        }

        if (slot == slot("claim-permissions", "perm-place")) {
            togglePermission(player, claim, ClaimPermission.PLACE, "perm-place");
            return;
        }
        if (slot == slot("claim-permissions", "perm-break")) {
            togglePermission(player, claim, ClaimPermission.BREAK, "perm-break");
            return;
        }
        if (slot == slot("claim-permissions", "perm-interact")) {
            togglePermission(player, claim, ClaimPermission.INTERACT, "perm-interact");
            return;
        }
        if (slot == slot("claim-permissions", "perm-container")) {
            togglePermission(player, claim, ClaimPermission.CONTAINER, "perm-container");
            return;
        }
        if (slot == slot("claim-permissions", "perm-redstone")) {
            togglePermission(player, claim, ClaimPermission.REDSTONE, "perm-redstone");
            return;
        }
        if (slot == slot("claim-permissions", "perm-explosion")) {
            togglePermission(player, claim, ClaimPermission.EXPLOSION, "perm-explosion");
            return;
        }
        if (slot == slot("claim-permissions", "perm-bucket")) {
            togglePermission(player, claim, ClaimPermission.BUCKET, "perm-bucket");
            return;
        }
        if (slot == slot("claim-permissions", "perm-teleport")) {
            togglePermission(player, claim, ClaimPermission.TELEPORT, "perm-teleport");
            return;
        }
        if (hasItem("claim-permissions", "perm-flight") && slot == slot("claim-permissions", "perm-flight")) {
            togglePermission(player, claim, ClaimPermission.FLIGHT, "perm-flight");
            return;
        }
        if (slot == slot("claim-permissions", "disable-all")) {
            setAllPermissions(player, claim, false, "disable-all");
            return;
        }
        if (slot == slot("claim-permissions", "back")) {
            playConfiguredSound(player, "claim-permissions", "back");
            openCoreMenu(player, claim);
        }
    }

    private void handleTrustMemberPermissionMenu(Player player, TrustMemberPermissionHolder holder, int slot) {
        Claim claim = claimService.findClaimById(holder.claimId).orElse(null);
        if (claim == null || !claim.isTrusted(holder.memberId)) {
            player.closeInventory();
            player.sendMessage(plugin.message("claim-not-found"));
            return;
        }

        if (slot == slot("trust-member-permissions", "perm-place")) {
            toggleMemberPermission(player, claim, holder.memberId, ClaimPermission.PLACE, "perm-place");
            return;
        }
        if (slot == slot("trust-member-permissions", "perm-break")) {
            toggleMemberPermission(player, claim, holder.memberId, ClaimPermission.BREAK, "perm-break");
            return;
        }
        if (slot == slot("trust-member-permissions", "perm-interact")) {
            toggleMemberPermission(player, claim, holder.memberId, ClaimPermission.INTERACT, "perm-interact");
            return;
        }
        if (hasItem("trust-member-permissions", "perm-container") && slot == slot("trust-member-permissions", "perm-container")) {
            toggleMemberPermission(player, claim, holder.memberId, ClaimPermission.CONTAINER, "perm-container");
            return;
        }
        if (hasItem("trust-member-permissions", "perm-redstone") && slot == slot("trust-member-permissions", "perm-redstone")) {
            toggleMemberPermission(player, claim, holder.memberId, ClaimPermission.REDSTONE, "perm-redstone");
            return;
        }
        if (hasItem("trust-member-permissions", "perm-explosion") && slot == slot("trust-member-permissions", "perm-explosion")) {
            toggleMemberPermission(player, claim, holder.memberId, ClaimPermission.EXPLOSION, "perm-explosion");
            return;
        }
        if (slot == slot("trust-member-permissions", "perm-bucket")) {
            toggleMemberPermission(player, claim, holder.memberId, ClaimPermission.BUCKET, "perm-bucket");
            return;
        }
        if (slot == slot("trust-member-permissions", "perm-teleport")) {
            toggleMemberPermission(player, claim, holder.memberId, ClaimPermission.TELEPORT, "perm-teleport");
            return;
        }
        if (hasItem("trust-member-permissions", "perm-flight") && slot == slot("trust-member-permissions", "perm-flight")) {
            toggleMemberPermission(player, claim, holder.memberId, ClaimPermission.FLIGHT, "perm-flight");
            return;
        }
        if (slot == slot("trust-member-permissions", "back")) {
            playConfiguredSound(player, "trust-member-permissions", "back");
            openTrustMenu(player, claim, 0);
        }
    }

    private void handleClaimMarketMenu(Player player, ClaimMarketHolder holder, int slot, boolean rightClick) {
        List<ClaimSaleListing> listings = claimMarketService.listings();
        List<Integer> entrySlots = slots("claim-market", "entry");
        if (entrySlots.isEmpty()) {
            warnMissingMarketEntrySlots(player);
            return;
        }
        int slotIndex = entrySlots.indexOf(slot);
        int index = holder.page * entrySlots.size() + slotIndex;
        if (slotIndex >= 0 && index < listings.size()) {
            ClaimSaleListing listing = listings.get(index);
            Claim claim = claimService.findClaimByIdOrLoad(listing.claimId()).orElse(null);
            if (claim == null) {
                player.sendMessage(plugin.message("sale-listing-missing"));
                openClaimMarketMenu(player, holder.page);
                return;
            }
            playConfiguredSound(player, "claim-market", "entry");
            if (rightClick) {
                player.sendMessage(plugin.message(
                    "market-listing-detail",
                    "{name}", claim.name(),
                    "{seller}", listing.sellerName(),
                    "{price}", ClaimActionService.formatMoney(listing.price()),
                    "{world}", claim.world(),
                    "{x}", String.valueOf(claim.centerX()),
                    "{z}", String.valueOf(claim.centerZ())
                ));
                return;
            }
            openClaimSaleConfirmMenu(player, listing, holder.page);
            return;
        }
        if (slot == slot("claim-market", "refresh")) {
            playConfiguredSound(player, "claim-market", "refresh");
            openClaimMarketMenu(player, holder.page);
            return;
        }
        if (slot == slot("claim-market", "prev-page") && holder.page > 0) {
            playConfiguredSound(player, "claim-market", "prev-page");
            openClaimMarketMenu(player, holder.page - 1);
            return;
        }
        if (slot == slot("claim-market", "back")) {
            playConfiguredSound(player, "claim-market", "back");
            openMainMenu(player);
            return;
        }
        if (slot == slot("claim-market", "next-page") && (holder.page + 1) * entrySlots.size() < listings.size()) {
            playConfiguredSound(player, "claim-market", "next-page");
            openClaimMarketMenu(player, holder.page + 1);
        }
    }

    private void warnMissingMarketEntrySlots(Player player) {
        player.sendMessage(plugin.message("market-config-invalid"));
        plugin.getLogger().warning("The claim-market GUI has no configured entry slots. Check gui/claim-market.yml items.entry.");
    }

    private void handleSaleConfirmMenu(Player player, SaleConfirmHolder holder, int slot) {
        if (slot == slot("claim-sale-confirm", "confirm")) {
            playConfiguredSound(player, "claim-sale-confirm", "confirm");
            if (claimMarketService.purchase(player, holder.claimId)) {
                player.closeInventory();
            } else {
                openClaimMarketMenu(player, holder.returnPage);
            }
            return;
        }
        if (slot == slot("claim-sale-confirm", "cancel")) {
            playConfiguredSound(player, "claim-sale-confirm", "cancel");
            openClaimMarketMenu(player, holder.returnPage);
        }
    }

    private void handleSelectionCreateMenu(Player player, SelectionCreateHolder holder, int slot) {
        ClaimSelectionService.SelectionPreview preview = claimSelectionService.preview(player);
        if (slot == slot("selection-create", "refresh")) {
            playConfiguredSound(player, "selection-create", "refresh");
            if (preview == null || !preview.ready()) {
                player.closeInventory();
                player.sendMessage(plugin.color(plugin.messagesConfig().getString("prefix", "&8[&6Claim&8] &f") + "&c请先重新选择两个对角点。"));
                return;
            }
            openSelectionCreateMenu(player, holder.claimName, preview);
            return;
        }
        if (slot == slot("selection-create", "confirm")) {
            playConfiguredSound(player, "selection-create", "confirm");
            if (claimSelectionService.createClaim(player, holder.claimName)) {
                player.closeInventory();
            } else if (preview != null && preview.ready()) {
                openSelectionCreateMenu(player, holder.claimName, claimSelectionService.preview(player));
            }
            return;
        }
        if (slot == slot("selection-create", "cancel")) {
            playConfiguredSound(player, "selection-create", "cancel");
            claimSelectionService.clear(player);
            player.closeInventory();
        }
    }

    private void togglePermission(Player player, Claim claim, ClaimPermission permission, String itemKey) {
        playConfiguredSound(player, "claim-permissions", itemKey);
        claimService.updatePermission(claim, permission, !claim.permission(permission));
        openClaimPermissionsMenu(player, claim);
    }

    private void setAllPermissions(Player player, Claim claim, boolean allowed, String itemKey) {
        playConfiguredSound(player, "claim-permissions", itemKey);
        for (ClaimPermission permission : ClaimPermission.values()) {
            claimService.updatePermission(claim, permission, allowed);
        }
        openClaimPermissionsMenu(player, claim);
    }

    private void toggleMemberPermission(Player player, Claim claim, UUID memberId, ClaimPermission permission, String itemKey) {
        playConfiguredSound(player, "trust-member-permissions", itemKey);
        ClaimMemberSettings settings = claimService.memberSettings(claim, memberId);
        claimService.updateMemberPermission(claim, memberId, permission, !settings.permission(permission));
        openTrustMemberPermissionMenu(player, claim, memberId);
    }

    private List<Player> onlineCandidates(Claim claim) {
        return Bukkit.getOnlinePlayers().stream()
            .filter(online -> !online.getUniqueId().equals(claim.owner()))
            .filter(online -> !claim.isTrusted(online.getUniqueId()))
            .map(online -> (Player) online)
            .sorted((left, right) -> left.getName().compareToIgnoreCase(right.getName()))
            .toList();
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

    private boolean hasItem(String menuKey, String itemKey) {
        return menu(menuKey).isConfigurationSection("items." + itemKey);
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

    private ItemStack playerHead(String menuKey, String itemKey, UUID playerId, String... replacements) {
        ItemStack item = configuredItem(menuKey, itemKey, replacements);
        if (!(item.getItemMeta() instanceof SkullMeta meta)) {
            return item;
        }
        Player online = Bukkit.getPlayer(playerId);
        if (online != null) {
            meta.setOwningPlayer(online);
            item.setItemMeta(meta);
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
            Object gameProfile = gameProfileClass.getConstructor(UUID.class, String.class)
                .newInstance(UUID.nameUUIDFromBytes(texture.getBytes()), "coreclaim_head");
            Object property = propertyClass.getConstructor(String.class, String.class)
                .newInstance("textures", normalizeTexture(texture));
            Object propertyMap = gameProfileClass.getMethod("getProperties").invoke(gameProfile);
            propertyMap.getClass().getMethod("put", Object.class, Object.class).invoke(propertyMap, "textures", property);
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

    private String displayNotifyPreview(String raw, Claim claim, String fallback) {
        String base = raw == null || raw.isBlank() ? fallback : raw;
        return base
            .replace("%claim_name%", claim.name())
            .replace("{claim_name}", claim.name())
            .replace("{name}", claim.name())
            .replace("%owner%", claim.ownerName())
            .replace("{owner}", claim.ownerName());
    }

    private String stripMessagePrefix(String message) {
        String prefix = plugin.color(plugin.messagesConfig().getString("prefix", ""));
        return message != null && message.startsWith(prefix) ? message.substring(prefix.length()) : (message == null ? "" : message);
    }

    private String stateText(boolean enabled) {
        return enabled ? "&a允许" : "&c禁止";
    }

    private String playerName(UUID playerId) {
        OfflinePlayer player = Bukkit.getOfflinePlayer(playerId);
        return player.getName() == null ? playerId.toString() : player.getName();
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

    private abstract static class BaseHolder implements InventoryHolder {
        protected Inventory inventory;

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }

    private static final class ClaimListHolder extends BaseHolder {
        private final int page;
        private ClaimListHolder(int page) { this.page = page; }
    }

    private static final class ClaimManageHolder extends BaseHolder {
        private final int claimId;
        private ClaimManageHolder(int claimId) { this.claimId = claimId; }
    }

    private static final class CoreMenuHolder extends BaseHolder {
        private final int claimId;
        private CoreMenuHolder(int claimId) { this.claimId = claimId; }
    }

    private static final class TrustMenuHolder extends BaseHolder {
        private final int claimId;
        private final int page;
        private TrustMenuHolder(int claimId, int page) { this.claimId = claimId; this.page = page; }
    }

    private static final class TrustOnlineHolder extends BaseHolder {
        private final int claimId;
        private final int page;
        private TrustOnlineHolder(int claimId, int page) { this.claimId = claimId; this.page = page; }
    }

    private static final class ClaimPermissionHolder extends BaseHolder {
        private final int claimId;
        private ClaimPermissionHolder(int claimId) { this.claimId = claimId; }
    }

    private static final class TrustMemberPermissionHolder extends BaseHolder {
        private final int claimId;
        private final UUID memberId;
        private TrustMemberPermissionHolder(int claimId, UUID memberId) { this.claimId = claimId; this.memberId = memberId; }
    }

    private static final class SelectionCreateHolder extends BaseHolder {
        private final String claimName;
        private SelectionCreateHolder(String claimName) { this.claimName = claimName; }
    }

    private static final class ClaimMarketHolder extends BaseHolder {
        private final int page;
        private ClaimMarketHolder(int page) { this.page = page; }
    }

    private static final class SaleConfirmHolder extends BaseHolder {
        private final int claimId;
        private final int returnPage;
        private SaleConfirmHolder(int claimId, int returnPage) {
            this.claimId = claimId;
            this.returnPage = returnPage;
        }
    }
}
