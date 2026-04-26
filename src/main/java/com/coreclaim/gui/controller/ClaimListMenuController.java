package com.coreclaim.gui.controller;

import com.coreclaim.gui.MenuService;
import com.coreclaim.gui.holder.ClaimListHolder;
import com.coreclaim.gui.holder.ClaimListSlotEntry;
import com.coreclaim.model.Claim;
import com.coreclaim.service.ClaimService.ClaimListEntry;
import com.coreclaim.service.ClaimService.ClaimListRelation;
import java.util.List;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;

public final class ClaimListMenuController {

    private final MenuService menu;

    public ClaimListMenuController(MenuService menu) {
        this.menu = menu;
    }

    public void open(Player player, int page) {
        ClaimListHolder holder = new ClaimListHolder(page);
        Inventory inventory = Bukkit.createInventory(holder, menu.menuSize("claim-list"), menu.menuTitle("claim-list"));
        holder.inventory = inventory;
        menu.fill(inventory, "claim-list", "filler");

        List<ClaimListEntry> claims = menu.claimService().visibleClaimsOfFresh(player.getUniqueId());
        List<Integer> entrySlots = menu.slots("claim-list", "entry");
        int start = Math.max(0, page) * entrySlots.size();
        int end = Math.min(claims.size(), start + entrySlots.size());
        for (int index = start; index < end; index++) {
            ClaimListEntry entry = claims.get(index);
            Claim claim = entry.claim();
            int entrySlot = entrySlots.get(index - start);
            holder.entries.add(new ClaimListSlotEntry(entrySlot, claim.id()));
            inventory.setItem(entrySlot, menu.configuredItem("claim-list", "entry",
                "{name}", claim.name(),
                "{owner}", claim.ownerName(),
                "{relation}", menu.relationText(entry.relation()),
                "{server}", menu.claimService().displayServerId(claim),
                "{world}", claim.world(),
                "{x}", String.valueOf(claim.centerX()),
                "{z}", String.valueOf(claim.centerZ()),
                "{width}", String.valueOf(claim.width()),
                "{depth}", String.valueOf(claim.depth()),
                "{area}", String.valueOf(claim.area()),
                "{trusted}", String.valueOf(claim.trustedCount()),
                "{left_action}", menu.leftClickActionText(entry.relation())
            ));
        }

        inventory.setItem(menu.slot("claim-list", "refresh"), menu.configuredItem("claim-list", "refresh", "{total}", String.valueOf(claims.size())));
        inventory.setItem(menu.slot("claim-list", "prev-page"), menu.configuredItem("claim-list", "prev-page"));
        inventory.setItem(menu.slot("claim-list", "back"), menu.configuredItem("claim-list", "back"));
        inventory.setItem(menu.slot("claim-list", "next-page"), menu.configuredItem("claim-list", "next-page"));
        player.openInventory(inventory);
    }

    public void handle(Player player, ClaimListHolder holder, int slot, boolean rightClick) {
        List<Integer> entrySlots = menu.slots("claim-list", "entry");
        ClaimListSlotEntry clickedEntry = holder.entries.stream()
            .filter(entry -> entry.slot() == slot)
            .findFirst()
            .orElse(null);
        if (clickedEntry != null) {
            ClaimListEntry claimEntry = menu.resolveVisibleListEntry(player, clickedEntry.claimId());
            if (claimEntry == null) {
                return;
            }
            menu.playConfiguredSound(player, "claim-list", "entry");
            if (rightClick) {
                menu.claimActionService().teleportToClaim(player, claimEntry.claim());
            } else if (claimEntry.relation() == ClaimListRelation.OWNER) {
                menu.openCoreMenu(player, claimEntry.claim());
            } else {
                menu.openClaimViewMenu(player, claimEntry.claim().id(), holder.page);
            }
            return;
        }
        if (slot == menu.slot("claim-list", "refresh")) {
            menu.playConfiguredSound(player, "claim-list", "refresh");
            menu.openClaimListMenu(player, holder.page);
            return;
        }
        if (slot == menu.slot("claim-list", "prev-page") && holder.page > 0) {
            menu.playConfiguredSound(player, "claim-list", "prev-page");
            menu.openClaimListMenu(player, holder.page - 1);
            return;
        }
        if (slot == menu.slot("claim-list", "back")) {
            menu.playConfiguredSound(player, "claim-list", "back");
            player.closeInventory();
            return;
        }
        int totalClaims = menu.claimService().visibleClaimsOfFresh(player.getUniqueId()).size();
        if (slot == menu.slot("claim-list", "next-page") && (holder.page + 1) * entrySlots.size() < totalClaims) {
            menu.playConfiguredSound(player, "claim-list", "next-page");
            menu.openClaimListMenu(player, holder.page + 1);
        }
    }
}
