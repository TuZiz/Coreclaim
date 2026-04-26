package com.coreclaim.gui.controller;

import com.coreclaim.gui.MenuService;
import com.coreclaim.gui.holder.SelectionCreateHolder;
import com.coreclaim.service.ClaimActionService;
import com.coreclaim.service.ClaimSelectionService;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;

public final class SelectionCreateMenuController {

    private final MenuService menu;

    public SelectionCreateMenuController(MenuService menu) {
        this.menu = menu;
    }

    public void open(Player player, String claimName, ClaimSelectionService.SelectionPreview preview) {
        SelectionCreateHolder holder = new SelectionCreateHolder(claimName);
        Inventory inventory = Bukkit.createInventory(holder, menu.menuSize("selection-create"), menu.menuTitle("selection-create", "{name}", claimName));
        holder.inventory = inventory;
        menu.fill(inventory, "selection-create", "filler");

        String status = preview.allowed() ? "&#55FFAA可创建" : "&#FF6B6B" + menu.stripMessagePrefix(preview.failureMessage());
        inventory.setItem(menu.slot("selection-create", "info"), menu.configuredItem("selection-create", "info",
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
            "{status}", menu.plugin().color(status)
        ));
        inventory.setItem(menu.slot("selection-create", "refresh"), menu.configuredItem("selection-create", "refresh"));
        inventory.setItem(menu.slot("selection-create", "confirm"), menu.configuredItem("selection-create", "confirm",
            "{name}", claimName,
            "{cost}", ClaimActionService.formatMoney(preview.cost()),
            "{width}", String.valueOf(preview.width()),
            "{height}", String.valueOf(preview.height()),
            "{depth}", String.valueOf(preview.depth())
        ));
        inventory.setItem(menu.slot("selection-create", "cancel"), menu.configuredItem("selection-create", "cancel"));
        player.openInventory(inventory);
    }

    public void handle(Player player, SelectionCreateHolder holder, int slot) {
        ClaimSelectionService.SelectionPreview preview = menu.claimSelectionService().preview(player);
        if (slot == menu.slot("selection-create", "refresh")) {
            menu.playConfiguredSound(player, "selection-create", "refresh");
            if (preview == null || !preview.ready()) {
                player.closeInventory();
            player.sendMessage(menu.plugin().color(menu.plugin().messagesConfig().getString("prefix", "&#64748B[&#A7F3D0Claim&#64748B] &#CBD5E1") + "&#FF6B6B请先重新选择两个对角点。"));
                return;
            }
            menu.openSelectionCreateMenu(player, holder.claimName, preview);
            return;
        }
        if (slot == menu.slot("selection-create", "confirm")) {
            menu.playConfiguredSound(player, "selection-create", "confirm");
            if (menu.claimSelectionService().createClaim(player, holder.claimName)) {
                player.closeInventory();
            } else if (preview != null && preview.ready()) {
                menu.openSelectionCreateMenu(player, holder.claimName, menu.claimSelectionService().preview(player));
            }
            return;
        }
        if (slot == menu.slot("selection-create", "cancel")) {
            menu.playConfiguredSound(player, "selection-create", "cancel");
            menu.claimSelectionService().clear(player);
            player.closeInventory();
        }
    }
}
