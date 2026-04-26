package com.coreclaim.gui.controller;

import com.coreclaim.gui.MenuService;
import com.coreclaim.gui.holder.ClaimExpandAmountHolder;
import com.coreclaim.gui.holder.ClaimExpandConfirmHolder;
import com.coreclaim.model.Claim;
import com.coreclaim.model.ClaimDirection;
import com.coreclaim.service.ClaimActionService;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;

public final class ClaimExpansionMenuController {

    private final MenuService menu;

    public ClaimExpansionMenuController(MenuService menu) {
        this.menu = menu;
    }

    public void openAmount(Player player, Claim claim, ClaimDirection direction, int amount) {
        int normalizedAmount = menu.expansionSupport().normalizeAmount(player, claim, direction, amount);
        ClaimActionService.ExpansionPreview preview = menu.claimActionService().previewExpansion(player, claim, direction, normalizedAmount);
        ClaimExpandAmountHolder holder = new ClaimExpandAmountHolder(claim.id(), direction, normalizedAmount);
        Inventory inventory = Bukkit.createInventory(holder, menu.menuSize("claim-expand-amount"), menu.menuTitle("claim-expand-amount",
            "{name}", claim.name(),
            "{direction}", menu.expansionSupport().directionLabel(direction)
        ));
        holder.inventory = inventory;
        menu.fill(inventory, "claim-expand-amount", "filler");

        String[] replacements = menu.expansionSupport().replacements(player, claim, direction, normalizedAmount, preview);
        inventory.setItem(menu.slot("claim-expand-amount", "info"), menu.configuredItem("claim-expand-amount", "info", replacements));
        inventory.setItem(menu.slot("claim-expand-amount", "minus-10"), menu.configuredItem("claim-expand-amount", "minus-10", replacements));
        inventory.setItem(menu.slot("claim-expand-amount", "minus-5"), menu.configuredItem("claim-expand-amount", "minus-5", replacements));
        inventory.setItem(menu.slot("claim-expand-amount", "minus-1"), menu.configuredItem("claim-expand-amount", "minus-1", replacements));
        inventory.setItem(menu.slot("claim-expand-amount", "plus-1"), menu.configuredItem("claim-expand-amount", "plus-1", replacements));
        inventory.setItem(menu.slot("claim-expand-amount", "plus-5"), menu.configuredItem("claim-expand-amount", "plus-5", replacements));
        inventory.setItem(menu.slot("claim-expand-amount", "plus-10"), menu.configuredItem("claim-expand-amount", "plus-10", replacements));
        inventory.setItem(menu.slot("claim-expand-amount", "max"), menu.configuredItem("claim-expand-amount", "max", replacements));
        inventory.setItem(menu.slot("claim-expand-amount", "confirm"), menu.configuredItem("claim-expand-amount", "confirm", replacements));
        inventory.setItem(menu.slot("claim-expand-amount", "back"), menu.configuredItem("claim-expand-amount", "back", replacements));
        player.openInventory(inventory);
    }

    public void openConfirm(Player player, Claim claim, ClaimDirection direction, int amount) {
        int normalizedAmount = menu.expansionSupport().normalizeAmount(player, claim, direction, amount);
        ClaimActionService.ExpansionPreview preview = menu.claimActionService().previewExpansion(player, claim, direction, normalizedAmount);
        ClaimExpandConfirmHolder holder = new ClaimExpandConfirmHolder(claim.id(), direction, normalizedAmount);
        Inventory inventory = Bukkit.createInventory(holder, menu.menuSize("claim-expand-confirm"), menu.menuTitle("claim-expand-confirm",
            "{name}", claim.name(),
            "{direction}", menu.expansionSupport().directionLabel(direction)
        ));
        holder.inventory = inventory;
        menu.fill(inventory, "claim-expand-confirm", "filler");

        String[] replacements = menu.expansionSupport().replacements(player, claim, direction, normalizedAmount, preview);
        inventory.setItem(menu.slot("claim-expand-confirm", "info"), menu.configuredItem("claim-expand-confirm", "info", replacements));
        inventory.setItem(menu.slot("claim-expand-confirm", "confirm"), menu.configuredItem("claim-expand-confirm", "confirm", replacements));
        inventory.setItem(menu.slot("claim-expand-confirm", "back"), menu.configuredItem("claim-expand-confirm", "back", replacements));
        inventory.setItem(menu.slot("claim-expand-confirm", "cancel"), menu.configuredItem("claim-expand-confirm", "cancel", replacements));
        player.openInventory(inventory);
    }

    public void handleAmount(Player player, ClaimExpandAmountHolder holder, int slot) {
        Claim claim = menu.claimService().findClaimByIdFresh(holder.claimId).orElse(null);
        if (claim == null) {
            player.closeInventory();
            player.sendMessage(menu.plugin().message("claim-not-found"));
            return;
        }
        if (!menu.claimActionService().canManageClaim(player, claim)) {
            player.closeInventory();
            player.sendMessage(menu.plugin().message("trust-no-permission"));
            return;
        }

        if (slot == menu.slot("claim-expand-amount", "minus-10")) {
            menu.playConfiguredSound(player, "claim-expand-amount", "minus-10");
            menu.openClaimExpandAmountMenu(player, claim, holder.direction, holder.amount - 10);
            return;
        }
        if (slot == menu.slot("claim-expand-amount", "minus-5")) {
            menu.playConfiguredSound(player, "claim-expand-amount", "minus-5");
            menu.openClaimExpandAmountMenu(player, claim, holder.direction, holder.amount - 5);
            return;
        }
        if (slot == menu.slot("claim-expand-amount", "minus-1")) {
            menu.playConfiguredSound(player, "claim-expand-amount", "minus-1");
            menu.openClaimExpandAmountMenu(player, claim, holder.direction, holder.amount - 1);
            return;
        }
        if (slot == menu.slot("claim-expand-amount", "plus-1")) {
            menu.playConfiguredSound(player, "claim-expand-amount", "plus-1");
            menu.openClaimExpandAmountMenu(player, claim, holder.direction, holder.amount + 1);
            return;
        }
        if (slot == menu.slot("claim-expand-amount", "plus-5")) {
            menu.playConfiguredSound(player, "claim-expand-amount", "plus-5");
            menu.openClaimExpandAmountMenu(player, claim, holder.direction, holder.amount + 5);
            return;
        }
        if (slot == menu.slot("claim-expand-amount", "plus-10")) {
            menu.playConfiguredSound(player, "claim-expand-amount", "plus-10");
            menu.openClaimExpandAmountMenu(player, claim, holder.direction, holder.amount + 10);
            return;
        }
        if (slot == menu.slot("claim-expand-amount", "max")) {
            menu.playConfiguredSound(player, "claim-expand-amount", "max");
            menu.openClaimExpandAmountMenu(player, claim, holder.direction, menu.expansionSupport().maxAmount(player, claim, holder.direction));
            return;
        }
        if (slot == menu.slot("claim-expand-amount", "confirm")) {
            menu.playConfiguredSound(player, "claim-expand-amount", "confirm");
            menu.openClaimExpandConfirmMenu(player, claim, holder.direction, holder.amount);
            return;
        }
        if (slot == menu.slot("claim-expand-amount", "back")) {
            menu.playConfiguredSound(player, "claim-expand-amount", "back");
            menu.openClaimManageMenu(player, claim);
        }
    }

    public void handleConfirm(Player player, ClaimExpandConfirmHolder holder, int slot) {
        Claim claim = menu.claimService().findClaimByIdFresh(holder.claimId).orElse(null);
        if (claim == null) {
            player.closeInventory();
            player.sendMessage(menu.plugin().message("claim-not-found"));
            return;
        }
        if (!menu.claimActionService().canManageClaim(player, claim)) {
            player.closeInventory();
            player.sendMessage(menu.plugin().message("trust-no-permission"));
            return;
        }

        if (slot == menu.slot("claim-expand-confirm", "back")) {
            menu.playConfiguredSound(player, "claim-expand-confirm", "back");
            menu.openClaimExpandAmountMenu(player, claim, holder.direction, holder.amount);
            return;
        }
        if (slot == menu.slot("claim-expand-confirm", "cancel")) {
            menu.playConfiguredSound(player, "claim-expand-confirm", "cancel");
            menu.openClaimManageMenu(player, claim);
            return;
        }
        if (slot != menu.slot("claim-expand-confirm", "confirm")) {
            return;
        }

        menu.playConfiguredSound(player, "claim-expand-confirm", "confirm");
        ClaimActionService.ExpansionPreview preview = menu.claimActionService().previewExpansion(player, claim, holder.direction, holder.amount);
        if (!preview.allowed()) {
            player.sendMessage(menu.plugin().message(preview.hitMax() ? "claim-max-size" : "claim-overlap"));
            menu.openClaimExpandAmountMenu(player, claim, holder.direction, holder.amount);
            return;
        }
        if (!menu.claimActionService().canPayExpansionCost(player, preview)) {
            String key = menu.claimActionService().hasExpansionEconomy(preview) ? "economy-not-enough" : "economy-missing";
            player.sendMessage(menu.plugin().message(key, "{cost}", ClaimActionService.formatMoney(preview.cost())));
            menu.openClaimExpandConfirmMenu(player, claim, holder.direction, holder.amount);
            return;
        }
        if (menu.claimActionService().expandClaim(player, claim, holder.direction, holder.amount)) {
            Claim updated = menu.claimService().findClaimByIdFresh(claim.id()).orElse(claim);
            menu.openClaimManageMenu(player, updated);
        } else {
            menu.openClaimExpandAmountMenu(player, claim, holder.direction, holder.amount);
        }
    }
}
