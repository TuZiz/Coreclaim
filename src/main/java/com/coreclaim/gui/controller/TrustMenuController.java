package com.coreclaim.gui.controller;

import com.coreclaim.gui.MenuService;
import com.coreclaim.gui.holder.TrustMenuHolder;
import com.coreclaim.gui.holder.TrustOnlineAddHolder;
import com.coreclaim.gui.holder.TrustOnlineTargetSlotEntry;
import com.coreclaim.model.Claim;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;

public final class TrustMenuController {

    private final MenuService menu;

    public TrustMenuController(MenuService menu) {
        this.menu = menu;
    }

    public void openTrust(Player player, Claim claim, int page) {
        TrustMenuHolder holder = new TrustMenuHolder(claim.id(), page);
        Inventory inventory = Bukkit.createInventory(holder, menu.menuSize("trust"), menu.menuTitle("trust", "{name}", claim.name()));
        holder.inventory = inventory;
        menu.fill(inventory, "trust", "filler");

        List<UUID> trustedPlayers = new ArrayList<>(claim.trustedMembers());
        List<Integer> entrySlots = menu.slots("trust", "trusted-entry");
        int start = Math.max(0, page) * entrySlots.size();
        int end = Math.min(trustedPlayers.size(), start + entrySlots.size());
        for (int index = start; index < end; index++) {
            UUID trustedId = trustedPlayers.get(index);
            inventory.setItem(entrySlots.get(index - start), menu.playerHead("trust", "trusted-entry", trustedId,
                "{player}", menu.playerName(trustedId), "{name}", claim.name()
            ));
        }
        if (menu.hasItem("trust", "info")) {
            inventory.setItem(menu.slot("trust", "info"), menu.configuredItem("trust", "info",
                "{name}", claim.name(),
                "{trusted_count}", String.valueOf(claim.trustedCount())
            ));
        }
        if (menu.hasItem("trust", "add-online")) {
            inventory.setItem(menu.slot("trust", "add-online"), menu.configuredItem("trust", "add-online", "{name}", claim.name()));
        }
        if (trustedPlayers.isEmpty() && menu.hasItem("trust", "empty")) {
            inventory.setItem(menu.slot("trust", "empty"), menu.configuredItem("trust", "empty", "{name}", claim.name()));
        }
        if (menu.hasItem("trust", "prev-page")) {
            inventory.setItem(menu.slot("trust", "prev-page"), menu.configuredItem("trust", "prev-page"));
        }
        if (menu.hasItem("trust", "back")) {
            inventory.setItem(menu.slot("trust", "back"), menu.configuredItem("trust", "back"));
        }
        if (menu.hasItem("trust", "next-page")) {
            inventory.setItem(menu.slot("trust", "next-page"), menu.configuredItem("trust", "next-page"));
        }
        player.openInventory(inventory);
    }

    public void openOnlineAdd(Player player, Claim claim, int page, int returnPage) {
        TrustOnlineAddHolder holder = new TrustOnlineAddHolder(claim.id(), page, returnPage);
        Inventory inventory = Bukkit.createInventory(
            holder,
            menu.menuSize("trust-online-add"),
            menu.menuTitle("trust-online-add", "{name}", claim.name())
        );
        holder.inventory = inventory;
        menu.fill(inventory, "trust-online-add", "filler");

        List<Player> onlineTargets = availableOnlineTrustTargets(player, claim);
        List<Integer> entrySlots = menu.slots("trust-online-add", "online-entry");
        int start = Math.max(0, page) * entrySlots.size();
        int end = Math.min(onlineTargets.size(), start + entrySlots.size());
        for (int index = start; index < end; index++) {
            Player target = onlineTargets.get(index);
            int targetSlot = entrySlots.get(index - start);
            holder.entries.add(new TrustOnlineTargetSlotEntry(targetSlot, target.getUniqueId()));
            inventory.setItem(targetSlot, menu.playerHead(
                "trust-online-add",
                "online-entry",
                target.getUniqueId(),
                "{player}", target.getName(),
                "{name}", claim.name()
            ));
        }
        if (menu.hasItem("trust-online-add", "info")) {
            inventory.setItem(menu.slot("trust-online-add", "info"), menu.configuredItem("trust-online-add", "info",
                "{name}", claim.name(),
                "{available_count}", String.valueOf(onlineTargets.size())
            ));
        }
        if (onlineTargets.isEmpty() && menu.hasItem("trust-online-add", "empty")) {
            inventory.setItem(menu.slot("trust-online-add", "empty"), menu.configuredItem("trust-online-add", "empty", "{name}", claim.name()));
        }
        if (menu.hasItem("trust-online-add", "prev-page")) {
            inventory.setItem(menu.slot("trust-online-add", "prev-page"), menu.configuredItem("trust-online-add", "prev-page"));
        }
        if (menu.hasItem("trust-online-add", "back")) {
            inventory.setItem(menu.slot("trust-online-add", "back"), menu.configuredItem("trust-online-add", "back"));
        }
        if (menu.hasItem("trust-online-add", "next-page")) {
            inventory.setItem(menu.slot("trust-online-add", "next-page"), menu.configuredItem("trust-online-add", "next-page"));
        }
        player.openInventory(inventory);
    }

    public void handleTrust(Player player, TrustMenuHolder holder, int slot) {
        Claim claim = menu.claimService().findClaimByIdFresh(holder.claimId).orElse(null);
        if (claim == null) {
            player.closeInventory();
            player.sendMessage(menu.plugin().message("claim-not-found"));
            return;
        }
        if (!menu.claimActionService().canManageMembers(player, claim)) {
            player.closeInventory();
            player.sendMessage(menu.plugin().message("trust-no-permission"));
            return;
        }
        if (menu.hasItem("trust", "add-online") && slot == menu.slot("trust", "add-online")) {
            menu.playConfiguredSound(player, "trust", "add-online");
            menu.openTrustOnlineAddMenu(player, claim, 0, holder.page);
            return;
        }

        List<UUID> trustedPlayers = new ArrayList<>(claim.trustedMembers());
        List<Integer> entrySlots = menu.slots("trust", "trusted-entry");
        int start = holder.page * entrySlots.size();
        int end = Math.min(trustedPlayers.size(), start + entrySlots.size());
        for (int index = start; index < end; index++) {
            if (entrySlots.get(index - start) == slot) {
                UUID targetId = trustedPlayers.get(index);
                menu.playConfiguredSound(player, "trust", "trusted-entry");
                OfflinePlayer target = Bukkit.getOfflinePlayer(targetId);
                if (menu.claimActionService().untrustPlayer(player, claim, target)) {
                    menu.openTrustMenu(player, claim, holder.page);
                }
                return;
            }
        }

        if (menu.hasItem("trust", "prev-page") && slot == menu.slot("trust", "prev-page") && holder.page > 0) {
            menu.playConfiguredSound(player, "trust", "prev-page");
            menu.openTrustMenu(player, claim, holder.page - 1);
            return;
        }
        if (menu.hasItem("trust", "back") && slot == menu.slot("trust", "back")) {
            menu.playConfiguredSound(player, "trust", "back");
            menu.openCoreMenu(player, claim);
            return;
        }
        if (menu.hasItem("trust", "next-page") && slot == menu.slot("trust", "next-page") && (holder.page + 1) * entrySlots.size() < trustedPlayers.size()) {
            menu.playConfiguredSound(player, "trust", "next-page");
            menu.openTrustMenu(player, claim, holder.page + 1);
        }
    }

    public void handleOnlineAdd(Player player, TrustOnlineAddHolder holder, int slot) {
        Claim claim = menu.claimService().findClaimByIdFresh(holder.claimId).orElse(null);
        if (claim == null) {
            player.closeInventory();
            player.sendMessage(menu.plugin().message("claim-not-found"));
            return;
        }
        if (!menu.claimActionService().canManageMembers(player, claim)) {
            player.closeInventory();
            player.sendMessage(menu.plugin().message("trust-no-permission"));
            return;
        }

        TrustOnlineTargetSlotEntry clickedEntry = holder.entries.stream()
            .filter(entry -> entry.slot() == slot)
            .findFirst()
            .orElse(null);
        if (clickedEntry != null) {
            menu.playConfiguredSound(player, "trust-online-add", "online-entry");
            Player target = Bukkit.getPlayer(clickedEntry.playerId());
            if (target == null || !target.isOnline()) {
                player.sendMessage(menu.plugin().message("trust-no-target"));
                menu.openTrustOnlineAddMenu(player, claim, holder.page, holder.returnPage);
                return;
            }
            UUID targetId = target.getUniqueId();
            if (targetId.equals(player.getUniqueId())) {
                player.sendMessage(menu.plugin().message("trust-self"));
                menu.openTrustOnlineAddMenu(player, claim, holder.page, holder.returnPage);
                return;
            }
            if (targetId.equals(claim.owner()) || claim.isTrusted(targetId)) {
                player.sendMessage(menu.plugin().message("trust-already", "{player}", target.getName()));
                menu.openTrustOnlineAddMenu(player, claim, holder.page, holder.returnPage);
                return;
            }
            if (claim.isDenied(targetId)) {
                player.sendMessage(menu.plugin().color("&#FF6B6B该玩家仍在禁足名单中，请先解除禁足再进行授权。"));
                menu.openTrustOnlineAddMenu(player, claim, holder.page, holder.returnPage);
                return;
            }
            if (menu.claimActionService().trustPlayer(player, claim, target)) {
                menu.openTrustMenu(player, claim, holder.returnPage);
            } else {
                menu.openTrustOnlineAddMenu(player, claim, holder.page, holder.returnPage);
            }
            return;
        }

        int pageSize = menu.slots("trust-online-add", "online-entry").size();
        int totalTargets = availableOnlineTrustTargets(player, claim).size();
        if (menu.hasItem("trust-online-add", "prev-page") && slot == menu.slot("trust-online-add", "prev-page") && holder.page > 0) {
            menu.playConfiguredSound(player, "trust-online-add", "prev-page");
            menu.openTrustOnlineAddMenu(player, claim, holder.page - 1, holder.returnPage);
            return;
        }
        if (menu.hasItem("trust-online-add", "back") && slot == menu.slot("trust-online-add", "back")) {
            menu.playConfiguredSound(player, "trust-online-add", "back");
            menu.openTrustMenu(player, claim, holder.returnPage);
            return;
        }
        if (menu.hasItem("trust-online-add", "next-page") && slot == menu.slot("trust-online-add", "next-page") && (holder.page + 1) * pageSize < totalTargets) {
            menu.playConfiguredSound(player, "trust-online-add", "next-page");
            menu.openTrustOnlineAddMenu(player, claim, holder.page + 1, holder.returnPage);
        }
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
}
