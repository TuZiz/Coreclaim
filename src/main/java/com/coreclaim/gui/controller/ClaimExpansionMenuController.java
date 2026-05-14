package com.coreclaim.gui.controller;

import com.coreclaim.gui.MenuService;
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
            menu.openClaimManageMenu(player, claim);
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
            menu.openClaimExpandConfirmMenu(player, claim, holder.direction, holder.amount);
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
            menu.openClaimManageMenu(player, claim);
        }
    }
}
