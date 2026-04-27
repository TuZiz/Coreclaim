package com.coreclaim.gui.controller;

import com.coreclaim.gui.MenuService;
import com.coreclaim.gui.holder.ClaimManageHolder;
import com.coreclaim.model.Claim;
import com.coreclaim.model.ClaimDirection;
import com.coreclaim.service.ClaimActionService;
import com.coreclaim.util.AdminAccess;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;

public final class ClaimManageMenuController {

    private final MenuService menu;

    public ClaimManageMenuController(MenuService menu) {
        this.menu = menu;
    }

    public void open(Player player, Claim claim) {
        ClaimManageHolder holder = new ClaimManageHolder(claim.id());
        Inventory inventory = Bukkit.createInventory(holder, menu.menuSize("claim-manage"), menu.menuTitle("claim-manage", "{name}", claim.name()));
        holder.inventory = inventory;
        menu.fill(inventory, "claim-manage", "filler");

        ClaimActionService.ExpansionPreview north = menu.claimActionService().previewExpansion(player, claim, ClaimDirection.NORTH);
        ClaimActionService.ExpansionPreview south = menu.claimActionService().previewExpansion(player, claim, ClaimDirection.SOUTH);
        ClaimActionService.ExpansionPreview west = menu.claimActionService().previewExpansion(player, claim, ClaimDirection.WEST);
        ClaimActionService.ExpansionPreview east = menu.claimActionService().previewExpansion(player, claim, ClaimDirection.EAST);

        inventory.setItem(menu.slot("claim-manage", "info"), menu.configuredItem("claim-manage", "info",
            "{name}", claim.name(),
            "{server}", menu.claimService().displayServerId(claim),
            "{world}", claim.world(),
            "{x}", String.valueOf(claim.centerX()),
            "{z}", String.valueOf(claim.centerZ()),
            "{width}", String.valueOf(claim.width()),
            "{depth}", String.valueOf(claim.depth()),
            "{area}", String.valueOf(claim.area())
        ));
        inventory.setItem(menu.slot("claim-manage", "expand-north"), menu.configuredItem("claim-manage", "expand-north",
            "{amount}", String.valueOf(north.expandAmount()), "{price}", north.costText(), "{current}", String.valueOf(claim.north()), "{target}", String.valueOf(north.targetDistance())));
        inventory.setItem(menu.slot("claim-manage", "expand-south"), menu.configuredItem("claim-manage", "expand-south",
            "{amount}", String.valueOf(south.expandAmount()), "{price}", south.costText(), "{current}", String.valueOf(claim.south()), "{target}", String.valueOf(south.targetDistance())));
        inventory.setItem(menu.slot("claim-manage", "expand-west"), menu.configuredItem("claim-manage", "expand-west",
            "{amount}", String.valueOf(west.expandAmount()), "{price}", west.costText(), "{current}", String.valueOf(claim.west()), "{target}", String.valueOf(west.targetDistance())));
        inventory.setItem(menu.slot("claim-manage", "expand-east"), menu.configuredItem("claim-manage", "expand-east",
            "{amount}", String.valueOf(east.expandAmount()), "{price}", east.costText(), "{current}", String.valueOf(claim.east()), "{target}", String.valueOf(east.targetDistance())));
        inventory.setItem(menu.slot("claim-manage", "delete"), menu.configuredItem("claim-manage", "delete"));
        inventory.setItem(menu.slot("claim-manage", "back"), menu.configuredItem("claim-manage", "back"));
        player.openInventory(inventory);
    }

    public void handle(Player player, ClaimManageHolder holder, int slot) {
        Claim claim = menu.claimService().findClaimByIdFresh(holder.claimId).orElse(null);
        if (claim == null) {
            player.closeInventory();
            player.sendMessage(menu.plugin().message("claim-not-found"));
            return;
        }
        if (slot == menu.slot("claim-manage", "back")) {
            menu.playConfiguredSound(player, "claim-manage", "back");
            menu.openCoreMenu(player, claim);
            return;
        }
        if (!menu.claimActionService().canManageClaim(player, claim)) {
            player.closeInventory();
            player.sendMessage(menu.plugin().message("trust-no-permission"));
            return;
        }

        if (slot == menu.slot("claim-manage", "expand-north")) {
            menu.playConfiguredSound(player, "claim-manage", "expand-north");
            menu.openClaimExpandAmountMenu(player, claim, ClaimDirection.NORTH, 1);
            return;
        }
        if (slot == menu.slot("claim-manage", "expand-south")) {
            menu.playConfiguredSound(player, "claim-manage", "expand-south");
            menu.openClaimExpandAmountMenu(player, claim, ClaimDirection.SOUTH, 1);
            return;
        }
        if (slot == menu.slot("claim-manage", "expand-west")) {
            menu.playConfiguredSound(player, "claim-manage", "expand-west");
            menu.openClaimExpandAmountMenu(player, claim, ClaimDirection.WEST, 1);
            return;
        }
        if (slot == menu.slot("claim-manage", "expand-east")) {
            menu.playConfiguredSound(player, "claim-manage", "expand-east");
            menu.openClaimExpandAmountMenu(player, claim, ClaimDirection.EAST, 1);
            return;
        }
        if (slot == menu.slot("claim-manage", "delete")) {
            menu.playConfiguredSound(player, "claim-manage", "delete");
            boolean adminMode = !claim.owner().equals(player.getUniqueId()) && AdminAccess.hasClaimManageAccess(player);
            boolean requested = adminMode
                ? menu.removalConfirmationService().requestAdminRemoval(player, claim)
                : menu.removalConfirmationService().requestOwnerRemoval(player, claim);
            if (requested) {
                player.closeInventory();
            }
            return;
        }
    }
}
