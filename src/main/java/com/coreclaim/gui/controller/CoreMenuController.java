package com.coreclaim.gui.controller;

import com.coreclaim.config.ClaimGroup;
import com.coreclaim.gui.MenuService;
import com.coreclaim.gui.holder.CoreMenuHolder;
import com.coreclaim.model.Claim;
import com.coreclaim.model.PlayerProfile;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;

public final class CoreMenuController {

    private final MenuService menu;

    public CoreMenuController(MenuService menu) {
        this.menu = menu;
    }

    public void open(Player player, Claim claim) {
        CoreMenuHolder holder = new CoreMenuHolder(claim.id());
        Inventory inventory = Bukkit.createInventory(holder, menu.menuSize("core"), menu.menuTitle("core", "{name}", claim.name()));
        holder.inventory = inventory;
        menu.fill(inventory, "core", "filler");

        PlayerProfile profile = menu.profileService().getOrCreate(player.getUniqueId(), player.getName());
        ClaimGroup group = menu.plugin().groups().resolve(player);
        int claimCount = menu.claimService().countClaims(player.getUniqueId());
        int maxClaims = group.maxClaims();
        int visibleClaimCount = menu.claimService().visibleClaimsOfFresh(player.getUniqueId()).size();

        inventory.setItem(menu.slot("core", "info"), menu.configuredItem("core", "info",
            "{name}", claim.name(),
            "{server}", menu.claimService().displayServerId(claim),
            "{claims}", String.valueOf(claimCount),
            "{max_claims}", String.valueOf(maxClaims),
            "{center_x}", String.valueOf(claim.centerX()),
            "{center_z}", String.valueOf(claim.centerZ()),
            "{width}", String.valueOf(claim.width()),
            "{depth}", String.valueOf(claim.depth()),
            "{area}", String.valueOf(claim.area()),
            "{activity}", String.valueOf(profile.activityPoints()),
            "{trusted_count}", String.valueOf(claim.trustedCount()),
            "{enter_state}", menu.notifyStateText(claim.enterMessage()),
            "{leave_state}", menu.notifyStateText(claim.leaveMessage())
        ));
        inventory.setItem(menu.slot("core", "expand"), menu.configuredItem("core", "expand"));
        inventory.setItem(menu.slot("core", "claim-list"), menu.configuredItem("core", "claim-list", "{total}", String.valueOf(visibleClaimCount)));
        inventory.setItem(menu.slot("core", "trust"), menu.configuredItem("core", "trust", "{name}", claim.name(), "{trusted_count}", String.valueOf(claim.trustedCount())));
        inventory.setItem(menu.slot("core", "permissions"), menu.configuredItem("core", "permissions"));
        inventory.setItem(menu.slot("core", "rename"), menu.configuredItem("core", "rename", "{name}", claim.name()));
        inventory.setItem(menu.slot("core", "notify"), menu.configuredItem("core", "notify",
            "{name}", claim.name(),
            "{enter_current}", menu.displayNotifyPreview(claim.enterMessage(), claim, "默认进入提示"),
            "{leave_current}", menu.displayNotifyPreview(claim.leaveMessage(), claim, "默认离开提示"),
            "{enter_state}", menu.notifyStateText(claim.enterMessage()),
            "{leave_state}", menu.notifyStateText(claim.leaveMessage())
        ));
        inventory.setItem(menu.slot("core", "hide"), menu.configuredItem("core", "hide"));
        inventory.setItem(menu.slot("core", "teleport"), menu.configuredItem("core", "teleport"));
        inventory.setItem(menu.slot("core", "close"), menu.configuredItem("core", "close"));
        player.openInventory(inventory);
    }

    public void handle(Player player, CoreMenuHolder holder, int slot, boolean rightClick) {
        Claim claim = menu.claimService().findClaimByIdFresh(holder.claimId).orElse(null);
        if (claim == null) {
            player.closeInventory();
            player.sendMessage(menu.plugin().message("claim-not-found"));
            return;
        }

        if (slot == menu.slot("core", "expand")) {
            menu.playConfiguredSound(player, "core", "expand");
            menu.openClaimManageMenu(player, claim);
            return;
        }
        if (slot == menu.slot("core", "claim-list")) {
            menu.playConfiguredSound(player, "core", "claim-list");
            menu.openClaimListMenu(player, 0);
            return;
        }
        if (slot == menu.slot("core", "trust")) {
            menu.playConfiguredSound(player, "core", "trust");
            menu.openTrustMenu(player, claim, 0);
            return;
        }
        if (slot == menu.slot("core", "permissions")) {
            menu.playConfiguredSound(player, "core", "permissions");
            menu.openClaimPermissionsMenu(player, claim);
            return;
        }
        if (slot == menu.slot("core", "rename")) {
            menu.playConfiguredSound(player, "core", "rename");
            menu.claimInputService().requestRename(player, claim);
            return;
        }
        if (slot == menu.slot("core", "notify")) {
            menu.playConfiguredSound(player, "core", "notify");
            if (rightClick) {
                menu.claimInputService().requestLeaveMessage(player, claim);
            } else {
                menu.claimInputService().requestEnterMessage(player, claim);
            }
            return;
        }
        if (slot == menu.slot("core", "hide")) {
            menu.playConfiguredSound(player, "core", "hide");
            if (menu.claimActionService().hideClaimCore(player, claim)) {
                player.closeInventory();
            }
            return;
        }
        if (slot == menu.slot("core", "teleport")) {
            menu.playConfiguredSound(player, "core", "teleport");
            menu.claimActionService().teleportToClaim(player, claim);
            return;
        }
        if (slot == menu.slot("core", "close")) {
            menu.playConfiguredSound(player, "core", "close");
            player.closeInventory();
        }
    }
}
