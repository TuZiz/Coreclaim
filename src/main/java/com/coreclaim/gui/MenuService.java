package com.coreclaim.gui;

import com.coreclaim.CoreClaimPlugin;
import com.coreclaim.config.ClaimGroup;
import com.coreclaim.model.Claim;
import com.coreclaim.model.ClaimFlag;
import com.coreclaim.model.ClaimFlagState;
import com.coreclaim.model.ClaimDirection;
import com.coreclaim.model.ClaimPermission;
import com.coreclaim.model.PlayerProfile;
import com.coreclaim.service.ClaimActionService;
import com.coreclaim.service.ClaimInputService;
import com.coreclaim.service.ClaimSelectionService;
import com.coreclaim.service.ClaimService;
import com.coreclaim.service.ClaimService.ClaimListEntry;
import com.coreclaim.service.ClaimService.ClaimListRelation;
import com.coreclaim.service.ProfileService;
import com.coreclaim.service.RemovalConfirmationService;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
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

    public MenuService(
        CoreClaimPlugin plugin,
        ClaimService claimService,
        ProfileService profileService,
        ClaimActionService claimActionService,
        RemovalConfirmationService removalConfirmationService,
        ClaimInputService claimInputService,
        ClaimSelectionService claimSelectionService
    ) {
        this.plugin = plugin;
        this.claimService = claimService;
        this.profileService = profileService;
        this.claimActionService = claimActionService;
        this.removalConfirmationService = removalConfirmationService;
        this.claimInputService = claimInputService;
        this.claimSelectionService = claimSelectionService;
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

        List<ClaimListEntry> claims = claimService.visibleClaimsOfFresh(player.getUniqueId());
        List<Integer> entrySlots = slots("claim-list", "entry");
        int start = Math.max(0, page) * entrySlots.size();
        int end = Math.min(claims.size(), start + entrySlots.size());
        for (int index = start; index < end; index++) {
            ClaimListEntry entry = claims.get(index);
            Claim claim = entry.claim();
            holder.entries.add(new ClaimListSlotEntry(entrySlots.get(index - start), claim.id()));
            inventory.setItem(entrySlots.get(index - start), configuredItem("claim-list", "entry",
                "{name}", claim.name(),
                "{owner}", claim.ownerName(),
                "{relation}", relationText(entry.relation()),
                "{server}", claimService.displayServerId(claim),
                "{world}", claim.world(),
                "{x}", String.valueOf(claim.centerX()),
                "{z}", String.valueOf(claim.centerZ()),
                "{width}", String.valueOf(claim.width()),
                "{depth}", String.valueOf(claim.depth()),
                "{area}", String.valueOf(claim.area()),
                "{trusted}", String.valueOf(claim.trustedCount()),
                "{left_action}", leftClickActionText(entry.relation())
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
            "{server}", claimService.displayServerId(claim),
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
        int maxClaims = group.maxClaims();
        int visibleClaimCount = claimService.visibleClaimsOfFresh(player.getUniqueId()).size();

        inventory.setItem(slot("core", "info"), configuredItem("core", "info",
            "{name}", claim.name(),
            "{server}", claimService.displayServerId(claim),
            "{claims}", String.valueOf(claimCount),
            "{max_claims}", String.valueOf(maxClaims),
            "{center_x}", String.valueOf(claim.centerX()),
            "{center_z}", String.valueOf(claim.centerZ()),
            "{width}", String.valueOf(claim.width()),
            "{depth}", String.valueOf(claim.depth()),
            "{area}", String.valueOf(claim.area()),
            "{activity}", String.valueOf(profile.activityPoints()),
            "{trusted_count}", String.valueOf(claim.trustedCount()),
            "{enter_state}", notifyStateText(claim.enterMessage()),
            "{leave_state}", notifyStateText(claim.leaveMessage())
        ));
        inventory.setItem(slot("core", "expand"), configuredItem("core", "expand"));
        inventory.setItem(slot("core", "claim-list"), configuredItem("core", "claim-list", "{total}", String.valueOf(visibleClaimCount)));
        inventory.setItem(slot("core", "trust"), configuredItem("core", "trust", "{name}", claim.name(), "{trusted_count}", String.valueOf(claim.trustedCount())));
        inventory.setItem(slot("core", "permissions"), configuredItem("core", "permissions"));
        inventory.setItem(slot("core", "rename"), configuredItem("core", "rename", "{name}", claim.name()));
        inventory.setItem(slot("core", "notify"), configuredItem("core", "notify",
            "{name}", claim.name(),
            "{enter_current}", displayNotifyPreview(claim.enterMessage(), claim, "\u9ed8\u8ba4\u8fdb\u5165\u63d0\u793a"),
            "{leave_current}", displayNotifyPreview(claim.leaveMessage(), claim, "\u9ed8\u8ba4\u79bb\u5f00\u63d0\u793a"),
            "{enter_state}", notifyStateText(claim.enterMessage()),
            "{leave_state}", notifyStateText(claim.leaveMessage())
        ));
        inventory.setItem(slot("core", "hide"), configuredItem("core", "hide"));
        inventory.setItem(slot("core", "teleport"), configuredItem("core", "teleport"));
        inventory.setItem(slot("core", "close"), configuredItem("core", "close"));
        player.openInventory(inventory);
    }

    public void openClaimViewMenu(Player player, int claimId, int page) {
        Claim claim = resolveTrustedViewClaim(player, claimId);
        if (claim == null) {
            return;
        }
        ClaimViewHolder holder = new ClaimViewHolder(claim.id(), page);
        Inventory inventory = Bukkit.createInventory(holder, menuSize("claim-view"), menuTitle("claim-view", "{name}", claim.name()));
        holder.inventory = inventory;
        fill(inventory, "claim-view", "filler");

        inventory.setItem(slot("claim-view", "info"), configuredItem("claim-view", "info",
            "{name}", claim.name(),
            "{owner}", claim.ownerName(),
            "{server}", claimService.displayServerId(claim),
            "{world}", claim.world(),
            "{center_x}", String.valueOf(claim.centerX()),
            "{center_z}", String.valueOf(claim.centerZ()),
            "{width}", String.valueOf(claim.width()),
            "{depth}", String.valueOf(claim.depth()),
            "{area}", String.valueOf(claim.area()),
            "{trusted_count}", String.valueOf(claim.trustedCount())
        ));
        if (hasItem("claim-view", "details")) {
            inventory.setItem(slot("claim-view", "details"), configuredItem("claim-view", "details",
                "{name}", claim.name(),
                "{owner}", claim.ownerName()
            ));
        }
        inventory.setItem(slot("claim-view", "teleport"), configuredItem("claim-view", "teleport"));
        inventory.setItem(slot("claim-view", "back"), configuredItem("claim-view", "back"));
        inventory.setItem(slot("claim-view", "close"), configuredItem("claim-view", "close"));
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
        if (hasItem("trust", "info")) {
            inventory.setItem(slot("trust", "info"), configuredItem("trust", "info",
                "{name}", claim.name(),
                "{trusted_count}", String.valueOf(claim.trustedCount())
            ));
        }
        if (hasItem("trust", "add-online")) {
            inventory.setItem(slot("trust", "add-online"), configuredItem("trust", "add-online", "{name}", claim.name()));
        }
        if (trustedPlayers.isEmpty() && hasItem("trust", "empty")) {
            inventory.setItem(slot("trust", "empty"), configuredItem("trust", "empty", "{name}", claim.name()));
        }
        if (hasItem("trust", "prev-page")) {
            inventory.setItem(slot("trust", "prev-page"), configuredItem("trust", "prev-page"));
        }
        if (hasItem("trust", "back")) {
            inventory.setItem(slot("trust", "back"), configuredItem("trust", "back"));
        }
        if (hasItem("trust", "next-page")) {
            inventory.setItem(slot("trust", "next-page"), configuredItem("trust", "next-page"));
        }
        player.openInventory(inventory);
    }

    public void openTrustOnlineAddMenu(Player player, Claim claim, int page, int returnPage) {
        TrustOnlineAddHolder holder = new TrustOnlineAddHolder(claim.id(), page, returnPage);
        Inventory inventory = Bukkit.createInventory(
            holder,
            menuSize("trust-online-add"),
            menuTitle("trust-online-add", "{name}", claim.name())
        );
        holder.inventory = inventory;
        fill(inventory, "trust-online-add", "filler");

        List<Player> onlineTargets = availableOnlineTrustTargets(player, claim);
        List<Integer> entrySlots = slots("trust-online-add", "online-entry");
        int start = Math.max(0, page) * entrySlots.size();
        int end = Math.min(onlineTargets.size(), start + entrySlots.size());
        for (int index = start; index < end; index++) {
            Player target = onlineTargets.get(index);
            int targetSlot = entrySlots.get(index - start);
            holder.entries.add(new TrustOnlineTargetSlotEntry(targetSlot, target.getUniqueId()));
            inventory.setItem(targetSlot, playerHead(
                "trust-online-add",
                "online-entry",
                target.getUniqueId(),
                "{player}", target.getName(),
                "{name}", claim.name()
            ));
        }
        if (hasItem("trust-online-add", "info")) {
            inventory.setItem(slot("trust-online-add", "info"), configuredItem("trust-online-add", "info",
                "{name}", claim.name(),
                "{available_count}", String.valueOf(onlineTargets.size())
            ));
        }
        if (onlineTargets.isEmpty() && hasItem("trust-online-add", "empty")) {
            inventory.setItem(slot("trust-online-add", "empty"), configuredItem("trust-online-add", "empty", "{name}", claim.name()));
        }
        if (hasItem("trust-online-add", "prev-page")) {
            inventory.setItem(slot("trust-online-add", "prev-page"), configuredItem("trust-online-add", "prev-page"));
        }
        if (hasItem("trust-online-add", "back")) {
            inventory.setItem(slot("trust-online-add", "back"), configuredItem("trust-online-add", "back"));
        }
        if (hasItem("trust-online-add", "next-page")) {
            inventory.setItem(slot("trust-online-add", "next-page"), configuredItem("trust-online-add", "next-page"));
        }
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
            "{perm_flight}", stateText(claim.permission(ClaimPermission.FLIGHT)),
            "{custom_count}", String.valueOf(countCustomFlags(claim))
        ));
        inventory.setItem(slot("claim-permissions", "perm-place"), configuredItem("claim-permissions", "perm-place", "{state}", stateText(claim.permission(ClaimPermission.PLACE))));
        inventory.setItem(slot("claim-permissions", "perm-break"), configuredItem("claim-permissions", "perm-break", "{state}", stateText(claim.permission(ClaimPermission.BREAK))));
        inventory.setItem(slot("claim-permissions", "perm-interact"), configuredItem("claim-permissions", "perm-interact", "{state}", stateText(claim.permission(ClaimPermission.INTERACT))));
        inventory.setItem(slot("claim-permissions", "perm-redstone"), configuredItem("claim-permissions", "perm-redstone", "{state}", stateText(claim.permission(ClaimPermission.REDSTONE))));
        inventory.setItem(slot("claim-permissions", "perm-explosion"), configuredItem("claim-permissions", "perm-explosion", "{state}", stateText(claim.permission(ClaimPermission.EXPLOSION))));
        inventory.setItem(slot("claim-permissions", "perm-bucket"), configuredItem("claim-permissions", "perm-bucket", "{state}", stateText(claim.permission(ClaimPermission.BUCKET))));
        inventory.setItem(slot("claim-permissions", "perm-teleport"), configuredItem("claim-permissions", "perm-teleport", "{state}", stateText(claim.permission(ClaimPermission.TELEPORT))));
        if (hasItem("claim-permissions", "perm-flight")) {
            inventory.setItem(slot("claim-permissions", "perm-flight"), configuredItem("claim-permissions", "perm-flight", "{state}", stateText(claim.permission(ClaimPermission.FLIGHT))));
        }
        for (ClaimFlag flag : ClaimFlag.values()) {
            String itemKey = flagItemKey(flag);
            if (!hasItem("claim-permissions", itemKey)) {
                continue;
            }
            ClaimFlagState state = claim.flagState(flag);
            inventory.setItem(slot("claim-permissions", itemKey), configuredItem("claim-permissions", itemKey,
                "{state}", flagStateText(state)
            ));
        }
        inventory.setItem(slot("claim-permissions", "disable-all"), configuredItem("claim-permissions", "disable-all"));
        inventory.setItem(slot("claim-permissions", "back"), configuredItem("claim-permissions", "back"));
        player.openInventory(inventory);
    }

    public void openSelectionCreateMenu(Player player, String claimName, ClaimSelectionService.SelectionPreview preview) {
        SelectionCreateHolder holder = new SelectionCreateHolder(claimName);
        Inventory inventory = Bukkit.createInventory(holder, menuSize("selection-create"), menuTitle("selection-create", "{name}", claimName));
        holder.inventory = inventory;
        fill(inventory, "selection-create", "filler");

        String status = preview.allowed() ? "&a\u53ef\u521b\u5efa" : "&c" + stripMessagePrefix(preview.failureMessage());
        inventory.setItem(slot("selection-create", "info"), configuredItem("selection-create", "info",
            "{name}", claimName,
            "{world}", preview.coreLocation() == null || preview.coreLocation().getWorld() == null ? player.getWorld().getName() : preview.coreLocation().getWorld().getName(),
            "{center_x}", preview.coreLocation() == null ? "-" : String.valueOf(preview.coreLocation().getBlockX()),
            "{center_y}", preview.coreLocation() == null ? "-" : String.valueOf(preview.coreLocation().getBlockY()),
            "{center_z}", preview.coreLocation() == null ? "-" : String.valueOf(preview.coreLocation().getBlockZ()),
            "{width}", String.valueOf(preview.width()),
            "{height}", String.valueOf(preview.height()),
            "{depth}", String.valueOf(preview.depth()),
            "{area}", String.valueOf(preview.area()),
            "{volume}", String.valueOf(preview.volume()),
            "{cost}", ClaimActionService.formatMoney(preview.cost()),
            "{status}", plugin.color(status)
        ));
        inventory.setItem(slot("selection-create", "refresh"), configuredItem("selection-create", "refresh"));
        inventory.setItem(slot("selection-create", "confirm"), configuredItem("selection-create", "confirm",
            "{name}", claimName,
            "{cost}", ClaimActionService.formatMoney(preview.cost()),
            "{width}", String.valueOf(preview.width()),
            "{height}", String.valueOf(preview.height()),
            "{depth}", String.valueOf(preview.depth())
        ));
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
        } else if (holder instanceof ClaimViewHolder claimViewHolder) {
            handleClaimViewMenu(player, claimViewHolder, slot);
        } else if (holder instanceof TrustMenuHolder trustMenuHolder) {
            handleTrustMenu(player, trustMenuHolder, slot);
        } else if (holder instanceof TrustOnlineAddHolder trustOnlineAddHolder) {
            handleTrustOnlineAddMenu(player, trustOnlineAddHolder, slot);
        } else if (holder instanceof ClaimPermissionHolder permissionHolder) {
            handlePermissionMenu(player, permissionHolder, slot, event.isRightClick());
        } else if (holder instanceof SelectionCreateHolder selectionCreateHolder) {
            handleSelectionCreateMenu(player, selectionCreateHolder, slot);
        }
    }

    private void handleClaimListMenu(Player player, ClaimListHolder holder, int slot, boolean rightClick) {
        List<Integer> entrySlots = slots("claim-list", "entry");
        ClaimListSlotEntry clickedEntry = holder.entries.stream()
            .filter(entry -> entry.slot() == slot)
            .findFirst()
            .orElse(null);
        if (clickedEntry != null) {
            ClaimListEntry claimEntry = resolveVisibleListEntry(player, clickedEntry.claimId());
            if (claimEntry == null) {
                return;
            }
            playConfiguredSound(player, "claim-list", "entry");
            if (rightClick) {
                claimActionService.teleportToClaim(player, claimEntry.claim());
            } else {
                if (claimEntry.relation() == ClaimListRelation.OWNER) {
                    openCoreMenu(player, claimEntry.claim());
                } else {
                    openClaimViewMenu(player, claimEntry.claim().id(), holder.page);
                }
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
        int totalClaims = claimService.visibleClaimsOfFresh(player.getUniqueId()).size();
        if (slot == slot("claim-list", "next-page") && (holder.page + 1) * entrySlots.size() < totalClaims) {
            playConfiguredSound(player, "claim-list", "next-page");
            openClaimListMenu(player, holder.page + 1);
        }
    }

    private void handleClaimManageMenu(Player player, ClaimManageHolder holder, int slot) {
        Claim claim = claimService.findClaimByIdFresh(holder.claimId).orElse(null);
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
        Claim claim = claimService.findClaimByIdFresh(holder.claimId).orElse(null);
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
        if (slot == slot("core", "close")) {
            playConfiguredSound(player, "core", "close");
            player.closeInventory();
        }
    }

    private void handleClaimViewMenu(Player player, ClaimViewHolder holder, int slot) {
        Claim claim = resolveTrustedViewClaim(player, holder.claimId);
        if (claim == null) {
            return;
        }
        if (hasItem("claim-view", "details") && slot == slot("claim-view", "details")) {
            playConfiguredSound(player, "claim-view", "details");
            sendClaimViewDetails(player, claim);
            return;
        }
        if (slot == slot("claim-view", "teleport")) {
            playConfiguredSound(player, "claim-view", "teleport");
            claimActionService.teleportToClaim(player, claim);
            return;
        }
        if (slot == slot("claim-view", "back")) {
            playConfiguredSound(player, "claim-view", "back");
            openClaimListMenu(player, holder.page);
            return;
        }
        if (slot == slot("claim-view", "close")) {
            playConfiguredSound(player, "claim-view", "close");
            player.closeInventory();
        }
    }

    private void handleTrustMenu(Player player, TrustMenuHolder holder, int slot) {
        Claim claim = claimService.findClaimByIdFresh(holder.claimId).orElse(null);
        if (claim == null) {
            player.closeInventory();
            player.sendMessage(plugin.message("claim-not-found"));
            return;
        }
        if (!claimActionService.canEditClaim(player, claim)) {
            player.closeInventory();
            player.sendMessage(plugin.message("trust-no-permission"));
            return;
        }
        if (hasItem("trust", "add-online") && slot == slot("trust", "add-online")) {
            playConfiguredSound(player, "trust", "add-online");
            openTrustOnlineAddMenu(player, claim, 0, holder.page);
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
                OfflinePlayer target = Bukkit.getOfflinePlayer(targetId);
                if (claimActionService.untrustPlayer(player, claim, target)) {
                    openTrustMenu(player, claim, holder.page);
                }
                return;
            }
        }

        if (hasItem("trust", "prev-page") && slot == slot("trust", "prev-page") && holder.page > 0) {
            playConfiguredSound(player, "trust", "prev-page");
            openTrustMenu(player, claim, holder.page - 1);
            return;
        }
        if (hasItem("trust", "back") && slot == slot("trust", "back")) {
            playConfiguredSound(player, "trust", "back");
            openCoreMenu(player, claim);
            return;
        }
        if (hasItem("trust", "next-page") && slot == slot("trust", "next-page") && (holder.page + 1) * entrySlots.size() < trustedPlayers.size()) {
            playConfiguredSound(player, "trust", "next-page");
            openTrustMenu(player, claim, holder.page + 1);
        }
    }

    private void handleTrustOnlineAddMenu(Player player, TrustOnlineAddHolder holder, int slot) {
        Claim claim = claimService.findClaimByIdFresh(holder.claimId).orElse(null);
        if (claim == null) {
            player.closeInventory();
            player.sendMessage(plugin.message("claim-not-found"));
            return;
        }
        if (!claimActionService.canEditClaim(player, claim)) {
            player.closeInventory();
            player.sendMessage(plugin.message("trust-no-permission"));
            return;
        }

        TrustOnlineTargetSlotEntry clickedEntry = holder.entries.stream()
            .filter(entry -> entry.slot() == slot)
            .findFirst()
            .orElse(null);
        if (clickedEntry != null) {
            playConfiguredSound(player, "trust-online-add", "online-entry");
            Player target = Bukkit.getPlayer(clickedEntry.playerId());
            if (target == null || !target.isOnline()) {
                player.sendMessage(plugin.message("trust-no-target"));
                openTrustOnlineAddMenu(player, claim, holder.page, holder.returnPage);
                return;
            }
            UUID targetId = target.getUniqueId();
            if (targetId.equals(player.getUniqueId())) {
                player.sendMessage(plugin.message("trust-self"));
                openTrustOnlineAddMenu(player, claim, holder.page, holder.returnPage);
                return;
            }
            if (targetId.equals(claim.owner()) || claim.isTrusted(targetId)) {
                player.sendMessage(plugin.message("trust-already", "{player}", target.getName()));
                openTrustOnlineAddMenu(player, claim, holder.page, holder.returnPage);
                return;
            }
            if (claim.isDenied(targetId)) {
                player.sendMessage(plugin.color("&c该玩家仍在 deny 列表中，请先解除 deny 再进行授权"));
                openTrustOnlineAddMenu(player, claim, holder.page, holder.returnPage);
                return;
            }
            if (claimActionService.trustPlayer(player, claim, target)) {
                openTrustMenu(player, claim, holder.returnPage);
            } else {
                openTrustOnlineAddMenu(player, claim, holder.page, holder.returnPage);
            }
            return;
        }

        int pageSize = slots("trust-online-add", "online-entry").size();
        int totalTargets = availableOnlineTrustTargets(player, claim).size();
        if (hasItem("trust-online-add", "prev-page") && slot == slot("trust-online-add", "prev-page") && holder.page > 0) {
            playConfiguredSound(player, "trust-online-add", "prev-page");
            openTrustOnlineAddMenu(player, claim, holder.page - 1, holder.returnPage);
            return;
        }
        if (hasItem("trust-online-add", "back") && slot == slot("trust-online-add", "back")) {
            playConfiguredSound(player, "trust-online-add", "back");
            openTrustMenu(player, claim, holder.returnPage);
            return;
        }
        if (hasItem("trust-online-add", "next-page") && slot == slot("trust-online-add", "next-page") && (holder.page + 1) * pageSize < totalTargets) {
            playConfiguredSound(player, "trust-online-add", "next-page");
            openTrustOnlineAddMenu(player, claim, holder.page + 1, holder.returnPage);
        }
    }

    private void handlePermissionMenu(Player player, ClaimPermissionHolder holder, int slot, boolean rightClick) {
        Claim claim = claimService.findClaimByIdFresh(holder.claimId).orElse(null);
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
        for (ClaimFlag flag : ClaimFlag.values()) {
            String itemKey = flagItemKey(flag);
            if (hasItem("claim-permissions", itemKey) && slot == slot("claim-permissions", itemKey)) {
                playConfiguredSound(player, "claim-permissions", itemKey);
                ClaimFlagState currentState = claim.flagState(flag);
                ClaimFlagState nextState = rightClick ? ClaimFlagState.UNSET : currentState.next();
                claimService.updateFlagState(claim, flag, nextState, player.getUniqueId());
                openClaimPermissionsMenu(player, claim);
                return;
            }
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

    private void handleSelectionCreateMenu(Player player, SelectionCreateHolder holder, int slot) {
        ClaimSelectionService.SelectionPreview preview = claimSelectionService.preview(player);
        if (slot == slot("selection-create", "refresh")) {
            playConfiguredSound(player, "selection-create", "refresh");
            if (preview == null || !preview.ready()) {
                player.closeInventory();
                player.sendMessage(plugin.color(plugin.messagesConfig().getString("prefix", "&8[&6Claim&8] &f") + "&c\u8bf7\u5148\u91cd\u65b0\u9009\u62e9\u4e24\u4e2a\u5bf9\u89d2\u70b9\u3002"));
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
        claimService.updatePermission(claim, permission, !claim.permission(permission), player.getUniqueId());
        openClaimPermissionsMenu(player, claim);
    }

    private void setAllPermissions(Player player, Claim claim, boolean allowed, String itemKey) {
        playConfiguredSound(player, "claim-permissions", itemKey);
        for (ClaimPermission permission : ClaimPermission.values()) {
            claimService.updatePermission(claim, permission, allowed, player.getUniqueId());
        }
        openClaimPermissionsMenu(player, claim);
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
        meta.setOwningPlayer(Bukkit.getOfflinePlayer(playerId));
        item.setItemMeta(meta);
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
        String preview = base
            .replace("%claim_name%", claim.name())
            .replace("{claim_name}", claim.name())
            .replace("{name}", claim.name())
            .replace("%owner%", claim.ownerName())
            .replace("{owner}", claim.ownerName());
        if (preview.length() > 24) {
            return preview.substring(0, 24) + "...";
        }
        return preview;
    }

    private String notifyStateText(String raw) {
        return raw == null || raw.isBlank() ? "&7\u9ed8\u8ba4\u5185\u5bb9" : "&e\u5df2\u4fee\u6539";
    }

    private ClaimListEntry resolveVisibleListEntry(Player player, int claimId) {
        Claim claim = claimService.findClaimByIdFresh(claimId).orElse(null);
        if (claim == null) {
            player.closeInventory();
            player.sendMessage(plugin.message("claim-not-found"));
            return null;
        }
        ClaimListEntry entry = claimService.visibleClaimEntryFresh(player.getUniqueId(), claimId).orElse(null);
        if (entry == null) {
            player.closeInventory();
            player.sendMessage(plugin.message("trust-no-permission"));
            return null;
        }
        return entry;
    }

    private Claim resolveTrustedViewClaim(Player player, int claimId) {
        Claim claim = claimService.findClaimByIdFresh(claimId).orElse(null);
        if (claim == null) {
            player.closeInventory();
            player.sendMessage(plugin.message("claim-not-found"));
            return null;
        }
        if (claim.owner().equals(player.getUniqueId())) {
            openCoreMenu(player, claim);
            return null;
        }
        if (!claimService.countsTowardQuota(claim) || claim.isDenied(player.getUniqueId()) || !claim.isTrusted(player.getUniqueId())) {
            player.closeInventory();
            player.sendMessage(plugin.message("trust-no-permission"));
            return null;
        }
        return claim;
    }

    private void sendClaimViewDetails(Player player, Claim claim) {
        player.sendMessage(plugin.color("&6[Claim] &f领地名称: &e" + claim.name()));
        player.sendMessage(plugin.color("&6[Claim] &f领地主人: &b" + claim.ownerName()));
        player.sendMessage(plugin.color("&6[Claim] &f所属区服: &e" + claimService.displayServerId(claim)));
        player.sendMessage(plugin.color("&6[Claim] &f所在世界: &e" + claim.world()));
        player.sendMessage(plugin.color("&6[Claim] &f核心坐标: &f" + claim.centerX() + ", " + claim.centerY() + ", " + claim.centerZ()));
        player.sendMessage(plugin.color("&6[Claim] &f领地大小: &e" + claim.width() + "x" + claim.depth() + " &7(面积 " + claim.area() + ")"));
        player.sendMessage(plugin.color("&6[Claim] &f传送点: " + (claim.hasTeleportPoint() ? "&a已设置" : "&e未设置，默认回核心")));
        player.sendMessage(plugin.color("&6[Claim] &f成员数量: &e" + claim.trustedCount()));
    }

    private String relationText(ClaimListRelation relation) {
        return relation == ClaimListRelation.OWNER ? "&a我的领地" : "&b已授权领地";
    }

    private String leftClickActionText(ClaimListRelation relation) {
        return relation == ClaimListRelation.OWNER ? "&7左键打开核心管理" : "&7左键查看只读详情";
    }

    private String stripMessagePrefix(String message) {
        String prefix = plugin.color(plugin.messagesConfig().getString("prefix", ""));
        return message != null && message.startsWith(prefix) ? message.substring(prefix.length()) : (message == null ? "" : message);
    }

    private String stateText(boolean enabled) {
        return enabled ? "&a\u5141\u8bb8" : "&c\u7981\u6b62";
    }

    private String flagStateText(ClaimFlagState state) {
        return switch (state) {
            case ALLOW -> "&a\u5141\u8bb8";
            case DENY -> "&c\u7981\u6b62";
            case UNSET -> "&7\u672a\u8bbe\u7f6e";
        };
    }

    private int countCustomFlags(Claim claim) {
        int count = 0;
        for (ClaimFlag flag : ClaimFlag.values()) {
            if (claim.flagState(flag) != ClaimFlagState.UNSET) {
                count++;
            }
        }
        return count;
    }

    private String flagItemKey(ClaimFlag flag) {
        return switch (flag) {
            case CONTAINER -> "flag-container";
            case USE_BUTTON -> "flag-use-button";
            case USE_LEVER -> "flag-use-lever";
            case USE_PRESSURE_PLATE -> "flag-use-pressure-plate";
            case USE_DOOR -> "flag-use-door";
            case USE_TRAPDOOR -> "flag-use-trapdoor";
            case USE_FENCE_GATE -> "flag-use-fence-gate";
            case USE_BED -> "flag-use-bed";
        };
    }

    private String playerName(UUID playerId) {
        OfflinePlayer player = Bukkit.getOfflinePlayer(playerId);
        return player.getName() == null ? playerId.toString() : player.getName();
    }

    private List<Player> availableOnlineTrustTargets(Player viewer, Claim claim) {
        List<Player> targets = new ArrayList<>();
        for (Player online : Bukkit.getOnlinePlayers()) {
            UUID targetId = online.getUniqueId();
            if (targetId.equals(viewer.getUniqueId())
                || targetId.equals(claim.owner())
                || claim.isTrusted(targetId)
                || claim.isDenied(targetId)) {
                continue;
            }
            targets.add(online);
        }
        targets.sort(Comparator.comparing(Player::getName, String.CASE_INSENSITIVE_ORDER));
        return targets;
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
        private final List<ClaimListSlotEntry> entries = new ArrayList<>();
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

    private static final class ClaimViewHolder extends BaseHolder {
        private final int claimId;
        private final int page;
        private ClaimViewHolder(int claimId, int page) {
            this.claimId = claimId;
            this.page = page;
        }
    }

    private static final class TrustMenuHolder extends BaseHolder {
        private final int claimId;
        private final int page;
        private TrustMenuHolder(int claimId, int page) { this.claimId = claimId; this.page = page; }
    }

    private static final class TrustOnlineAddHolder extends BaseHolder {
        private final int claimId;
        private final int page;
        private final int returnPage;
        private final List<TrustOnlineTargetSlotEntry> entries = new ArrayList<>();
        private TrustOnlineAddHolder(int claimId, int page, int returnPage) {
            this.claimId = claimId;
            this.page = page;
            this.returnPage = returnPage;
        }
    }

    private static final class ClaimPermissionHolder extends BaseHolder {
        private final int claimId;
        private ClaimPermissionHolder(int claimId) { this.claimId = claimId; }
    }

    private static final class SelectionCreateHolder extends BaseHolder {
        private final String claimName;
        private SelectionCreateHolder(String claimName) { this.claimName = claimName; }
    }

    private record ClaimListSlotEntry(int slot, int claimId) {
    }

    private record TrustOnlineTargetSlotEntry(int slot, UUID playerId) {
    }
}
