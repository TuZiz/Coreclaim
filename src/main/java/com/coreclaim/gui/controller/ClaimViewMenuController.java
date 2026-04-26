package com.coreclaim.gui.controller;

import com.coreclaim.gui.MenuService;
import com.coreclaim.gui.holder.ClaimViewHolder;
import com.coreclaim.model.Claim;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;

public final class ClaimViewMenuController {

    private final MenuService menu;

    public ClaimViewMenuController(MenuService menu) {
        this.menu = menu;
    }

    public void open(Player player, int claimId, int page) {
        Claim claim = menu.resolveTrustedViewClaim(player, claimId);
        if (claim == null) {
            return;
        }
        ClaimViewHolder holder = new ClaimViewHolder(claim.id(), page);
        Inventory inventory = Bukkit.createInventory(holder, menu.menuSize("claim-view"), menu.menuTitle("claim-view", "{name}", claim.name()));
        holder.inventory = inventory;
        menu.fill(inventory, "claim-view", "filler");

        inventory.setItem(menu.slot("claim-view", "info"), menu.configuredItem("claim-view", "info",
            "{name}", claim.name(),
            "{owner}", claim.ownerName(),
            "{server}", menu.claimService().displayServerId(claim),
            "{world}", claim.world(),
            "{center_x}", String.valueOf(claim.centerX()),
            "{center_z}", String.valueOf(claim.centerZ()),
            "{width}", String.valueOf(claim.width()),
            "{depth}", String.valueOf(claim.depth()),
            "{area}", String.valueOf(claim.area()),
            "{trusted_count}", String.valueOf(claim.trustedCount())
        ));
        if (menu.hasItem("claim-view", "details")) {
            inventory.setItem(menu.slot("claim-view", "details"), menu.configuredItem("claim-view", "details",
                "{name}", claim.name(),
                "{owner}", claim.ownerName()
            ));
        }
        inventory.setItem(menu.slot("claim-view", "teleport"), menu.configuredItem("claim-view", "teleport"));
        inventory.setItem(menu.slot("claim-view", "back"), menu.configuredItem("claim-view", "back"));
        inventory.setItem(menu.slot("claim-view", "close"), menu.configuredItem("claim-view", "close"));
        player.openInventory(inventory);
    }

    public void handle(Player player, ClaimViewHolder holder, int slot) {
        Claim claim = menu.resolveTrustedViewClaim(player, holder.claimId);
        if (claim == null) {
            return;
        }
        if (menu.hasItem("claim-view", "details") && slot == menu.slot("claim-view", "details")) {
            menu.playConfiguredSound(player, "claim-view", "details");
            menu.sendClaimViewDetails(player, claim);
            return;
        }
        if (slot == menu.slot("claim-view", "teleport")) {
            menu.playConfiguredSound(player, "claim-view", "teleport");
            menu.claimActionService().teleportToClaim(player, claim);
            return;
        }
        if (slot == menu.slot("claim-view", "back")) {
            menu.playConfiguredSound(player, "claim-view", "back");
            menu.openClaimListMenu(player, holder.page);
            return;
        }
        if (slot == menu.slot("claim-view", "close")) {
            menu.playConfiguredSound(player, "claim-view", "close");
            player.closeInventory();
        }
    }
}
