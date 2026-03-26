package com.coreclaim.gui;

import com.coreclaim.CoreClaimPlugin;
import com.coreclaim.config.ClaimGroup;
import com.coreclaim.model.Claim;
import com.coreclaim.model.ClaimMemberSettings;
import com.coreclaim.model.ClaimDirection;
import com.coreclaim.model.ClaimPermission;
import com.coreclaim.model.PlayerProfile;
import com.coreclaim.service.ClaimActionService;
import com.coreclaim.service.ClaimInputService;
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

    public MenuService(
        CoreClaimPlugin plugin,
        ClaimService claimService,
        ProfileService profileService,
        ClaimActionService claimActionService,
        RemovalConfirmationService removalConfirmationService,
        ClaimInputService claimInputService
    ) {
        this.plugin = plugin;
        this.claimService = claimService;
        this.profileService = profileService;
        this.claimActionService = claimActionService;
        this.removalConfirmationService = removalConfirmationService;
        this.claimInputService = claimInputService;
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
            "{area}", String.valueOf(claim.area())
        ));
        inventory.setItem(slot("claim-manage", "expand-north"), configuredItem("claim-manage", "expand-north",
            "{amount}", String.valueOf(amount), "{price}", north.costText(), "{current}", String.valueOf(claim.north()), "{target}", String.valueOf(north.targetDistance())));
        inventory.setItem(slot("claim-manage", "expand-south"), configuredItem("claim-manage", "expand-south",
            "{amount}", String.valueOf(amount), "{price}", south.costText(), "{current}", String.valueOf(claim.south()), "{target}", String.valueOf(south.targetDistance())));
        inventory.setItem(slot("claim-manage", "expand-west"), configuredItem("claim-manage", "expand-west",
            "{amount}", String.valueOf(amount), "{price}", west.costText(), "{current}", String.valueOf(claim.west()), "{target}", String.valueOf(west.targetDistance())));
        inventory.setItem(slot("claim-manage", "expand-east"), configuredItem("claim-manage", "expand-east",
            "{amount}", String.valueOf(amount), "{price}", east.costText(), "{current}", String.valueOf(claim.east()), "{target}", String.valueOf(east.targetDistance())));
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
            "{enter_message}", displayMessage(claim.enterMessage(), "默认进入提示"),
            "{leave_message}", displayMessage(claim.leaveMessage(), "默认离开提示")
        ));
        inventory.setItem(slot("core", "expand"), configuredItem("core", "expand"));
        inventory.setItem(slot("core", "claim-list"), configuredItem("core", "claim-list", "{total}", String.valueOf(claimCount)));
        inventory.setItem(slot("core", "trust"), configuredItem("core", "trust", "{name}", claim.name(), "{trusted_count}", String.valueOf(claim.trustedCount())));
        inventory.setItem(slot("core", "permissions"), configuredItem("core", "permissions"));
        inventory.setItem(slot("core", "rename"), configuredItem("core", "rename", "{name}", claim.name()));
        inventory.setItem(slot("core", "notify"), configuredItem("core", "notify",
            "{name}", claim.name(),
            "{enter_current}", displayMessage(claim.enterMessage(), "默认进入提示"),
            "{leave_current}", displayMessage(claim.leaveMessage(), "默认离开提示")
        ));
        inventory.setItem(slot("core", "hide"), configuredItem("core", "hide"));
        inventory.setItem(slot("core", "teleport"), configuredItem("core", "teleport"));
        inventory.setItem(slot("core", "close"), configuredItem("core", "close"));
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
            "{perm_bucket}", stateText(claim.permission(ClaimPermission.BUCKET)),
            "{perm_teleport}", stateText(claim.permission(ClaimPermission.TELEPORT))
        ));
        inventory.setItem(slot("claim-permissions", "perm-place"), configuredItem("claim-permissions", "perm-place", "{state}", stateText(claim.permission(ClaimPermission.PLACE))));
        inventory.setItem(slot("claim-permissions", "perm-break"), configuredItem("claim-permissions", "perm-break", "{state}", stateText(claim.permission(ClaimPermission.BREAK))));
        inventory.setItem(slot("claim-permissions", "perm-interact"), configuredItem("claim-permissions", "perm-interact", "{state}", stateText(claim.permission(ClaimPermission.INTERACT))));
        inventory.setItem(slot("claim-permissions", "perm-bucket"), configuredItem("claim-permissions", "perm-bucket", "{state}", stateText(claim.permission(ClaimPermission.BUCKET))));
        inventory.setItem(slot("claim-permissions", "perm-teleport"), configuredItem("claim-permissions", "perm-teleport", "{state}", stateText(claim.permission(ClaimPermission.TELEPORT))));
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
            "{perm_bucket}", stateText(settings.permission(ClaimPermission.BUCKET)),
            "{perm_teleport}", stateText(settings.permission(ClaimPermission.TELEPORT))
        ));
        inventory.setItem(slot("trust-member-permissions", "perm-place"), configuredItem("trust-member-permissions", "perm-place", "{state}", stateText(settings.permission(ClaimPermission.PLACE))));
        inventory.setItem(slot("trust-member-permissions", "perm-break"), configuredItem("trust-member-permissions", "perm-break", "{state}", stateText(settings.permission(ClaimPermission.BREAK))));
        inventory.setItem(slot("trust-member-permissions", "perm-interact"), configuredItem("trust-member-permissions", "perm-interact", "{state}", stateText(settings.permission(ClaimPermission.INTERACT))));
        inventory.setItem(slot("trust-member-permissions", "perm-bucket"), configuredItem("trust-member-permissions", "perm-bucket", "{state}", stateText(settings.permission(ClaimPermission.BUCKET))));
        inventory.setItem(slot("trust-member-permissions", "perm-teleport"), configuredItem("trust-member-permissions", "perm-teleport", "{state}", stateText(settings.permission(ClaimPermission.TELEPORT))));
        inventory.setItem(slot("trust-member-permissions", "back"), configuredItem("trust-member-permissions", "back"));
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
        if (slot == slot("claim-permissions", "perm-bucket")) {
            togglePermission(player, claim, ClaimPermission.BUCKET, "perm-bucket");
            return;
        }
        if (slot == slot("claim-permissions", "perm-teleport")) {
            togglePermission(player, claim, ClaimPermission.TELEPORT, "perm-teleport");
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
        if (slot == slot("trust-member-permissions", "perm-bucket")) {
            toggleMemberPermission(player, claim, holder.memberId, ClaimPermission.BUCKET, "perm-bucket");
            return;
        }
        if (slot == slot("trust-member-permissions", "perm-teleport")) {
            toggleMemberPermission(player, claim, holder.memberId, ClaimPermission.TELEPORT, "perm-teleport");
            return;
        }
        if (slot == slot("trust-member-permissions", "back")) {
            playConfiguredSound(player, "trust-member-permissions", "back");
            openTrustMenu(player, claim, 0);
        }
    }

    private void togglePermission(Player player, Claim claim, ClaimPermission permission, String itemKey) {
        playConfiguredSound(player, "claim-permissions", itemKey);
        claimService.updatePermission(claim, permission, !claim.permission(permission));
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

    private String displayMessage(String raw, String fallback) {
        return raw == null || raw.isBlank() ? fallback : raw;
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
}
