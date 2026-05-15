package com.coreclaim.gui.controller;

import com.coreclaim.gui.MenuService;
import com.coreclaim.gui.holder.ClaimManageHolder;
import com.coreclaim.model.Claim;
import com.coreclaim.model.ClaimDirection;
import com.coreclaim.service.ClaimActionService;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
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

        ClaimActionService.ExpansionPreview north = menu.claimActionService().previewExpansion(player, claim, ClaimDirection.NORTH, 1);
        ClaimActionService.ExpansionPreview south = menu.claimActionService().previewExpansion(player, claim, ClaimDirection.SOUTH, 1);
        ClaimActionService.ExpansionPreview west = menu.claimActionService().previewExpansion(player, claim, ClaimDirection.WEST, 1);
        ClaimActionService.ExpansionPreview east = menu.claimActionService().previewExpansion(player, claim, ClaimDirection.EAST, 1);
        ClaimActionService.ExpansionPreview up = menu.claimActionService().previewExpansion(player, claim, ClaimDirection.UP, 1);
        ClaimActionService.ExpansionPreview down = menu.claimActionService().previewExpansion(player, claim, ClaimDirection.DOWN, 1);

        inventory.setItem(menu.slot("claim-manage", "info"), menu.configuredItem("claim-manage", "info",
            "{name}", claim.name(),
            "{server}", menu.claimService().displayServerId(claim),
            "{world}", claim.world(),
            "{x}", String.valueOf(claim.centerX()),
            "{z}", String.valueOf(claim.centerZ()),
            "{width}", String.valueOf(claim.width()),
            "{height}", String.valueOf(claim.height()),
            "{depth}", String.valueOf(claim.depth()),
            "{area}", String.valueOf(claim.area()),
            "{min_y}", String.valueOf(claim.minY()),
            "{max_y}", String.valueOf(claim.maxY())
        ));
        setDirectionItem(inventory, player, claim, "expand-north", ClaimDirection.NORTH, north);
        setDirectionItem(inventory, player, claim, "expand-south", ClaimDirection.SOUTH, south);
        setDirectionItem(inventory, player, claim, "expand-west", ClaimDirection.WEST, west);
        setDirectionItem(inventory, player, claim, "expand-east", ClaimDirection.EAST, east);
        setDirectionItem(inventory, player, claim, "expand-up", ClaimDirection.UP, up);
        setDirectionItem(inventory, player, claim, "expand-down", ClaimDirection.DOWN, down);
        inventory.setItem(menu.slot("claim-manage", "back"), menu.configuredItem("claim-manage", "back"));
        player.openInventory(inventory);
    }

    public void handle(Player player, ClaimManageHolder holder, int slot, ClickType clickType) {
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
            openDirectionConfirm(player, claim, ClaimDirection.NORTH, clickType, "expand-north");
            return;
        }
        if (slot == menu.slot("claim-manage", "expand-south")) {
            openDirectionConfirm(player, claim, ClaimDirection.SOUTH, clickType, "expand-south");
            return;
        }
        if (slot == menu.slot("claim-manage", "expand-west")) {
            openDirectionConfirm(player, claim, ClaimDirection.WEST, clickType, "expand-west");
            return;
        }
        if (slot == menu.slot("claim-manage", "expand-east")) {
            openDirectionConfirm(player, claim, ClaimDirection.EAST, clickType, "expand-east");
            return;
        }
        if (slot == menu.slot("claim-manage", "expand-up")) {
            openDirectionConfirm(player, claim, ClaimDirection.UP, clickType, "expand-up");
            return;
        }
        if (slot == menu.slot("claim-manage", "expand-down")) {
            openDirectionConfirm(player, claim, ClaimDirection.DOWN, clickType, "expand-down");
        }
    }

    private void setDirectionItem(Inventory inventory, Player player, Claim claim, String itemKey, ClaimDirection direction, ClaimActionService.ExpansionPreview preview) {
        inventory.setItem(menu.slot("claim-manage", itemKey), menu.configuredItem(
            "claim-manage",
            itemKey,
            menu.expansionSupport().directionReplacements(player, claim, direction, preview)
        ));
    }

    private void openDirectionConfirm(Player player, Claim claim, ClaimDirection direction, ClickType clickType, String itemKey) {
        menu.playConfiguredSound(player, "claim-manage", itemKey);
        menu.openClaimExpandConfirmMenu(player, claim, direction, amountForClick(player, claim, direction, clickType));
    }

    private int amountForClick(Player player, Claim claim, ClaimDirection direction, ClickType clickType) {
        if (clickType != null && clickType.isShiftClick() && clickType.isRightClick()) {
            return menu.expansionSupport().maxButtonAmount(player, claim, direction, menu.plugin().settings().directionExpandAmount());
        }
        if (clickType != null && clickType.isShiftClick()) {
            return 50;
        }
        if (clickType != null && clickType.isRightClick()) {
            return 10;
        }
        return 1;
    }
}
