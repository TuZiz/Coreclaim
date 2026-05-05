package com.coreclaim.gui.controller;

import com.coreclaim.gui.MenuService;
import com.coreclaim.gui.holder.ClaimPermissionHolder;
import com.coreclaim.model.Claim;
import com.coreclaim.model.ClaimFlag;
import com.coreclaim.model.ClaimFlagState;
import com.coreclaim.model.ClaimPermission;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;

public final class PermissionMenuController {

    private static final PermissionEntry[] PERMISSION_ENTRIES = {
        new PermissionEntry(ClaimPermission.PLACE, "perm-place"),
        new PermissionEntry(ClaimPermission.BREAK, "perm-break"),
        new PermissionEntry(ClaimPermission.INTERACT, "perm-interact"),
        new PermissionEntry(ClaimPermission.MOB_INTERACT, "perm-mob-interact"),
        new PermissionEntry(ClaimPermission.ANIMAL_SPAWN, "perm-animal-spawn"),
        new PermissionEntry(ClaimPermission.MONSTER_SPAWN, "perm-monster-spawn"),
        new PermissionEntry(ClaimPermission.REDSTONE, "perm-redstone"),
        new PermissionEntry(ClaimPermission.EXPLOSION, "perm-explosion"),
        new PermissionEntry(ClaimPermission.BUCKET, "perm-bucket"),
        new PermissionEntry(ClaimPermission.TELEPORT, "perm-teleport"),
        new PermissionEntry(ClaimPermission.FLIGHT, "perm-flight")
    };

    private final MenuService menu;

    public PermissionMenuController(MenuService menu) {
        this.menu = menu;
    }

    public void open(Player player, Claim claim) {
        ClaimPermissionHolder holder = new ClaimPermissionHolder(claim.id());
        Inventory inventory = Bukkit.createInventory(holder, menu.menuSize("claim-permissions"), menu.menuTitle("claim-permissions", "{name}", claim.name()));
        holder.inventory = inventory;
        menu.fill(inventory, "claim-permissions", "filler");

        inventory.setItem(menu.slot("claim-permissions", "info"), menu.configuredItem("claim-permissions", "info",
            "{name}", claim.name(),
            "{trusted_count}", String.valueOf(claim.trustedCount()),
            "{perm_place}", menu.stateText(claim.permission(ClaimPermission.PLACE)),
            "{perm_break}", menu.stateText(claim.permission(ClaimPermission.BREAK)),
            "{perm_interact}", menu.stateText(claim.permission(ClaimPermission.INTERACT)),
            "{perm_mob_interact}", menu.stateText(claim.permission(ClaimPermission.MOB_INTERACT)),
            "{perm_animal_spawn}", menu.stateText(claim.permission(ClaimPermission.ANIMAL_SPAWN)),
            "{perm_monster_spawn}", menu.stateText(claim.permission(ClaimPermission.MONSTER_SPAWN)),
            "{perm_redstone}", menu.stateText(claim.permission(ClaimPermission.REDSTONE)),
            "{perm_explosion}", menu.stateText(claim.permission(ClaimPermission.EXPLOSION)),
            "{perm_bucket}", menu.stateText(claim.permission(ClaimPermission.BUCKET)),
            "{perm_teleport}", menu.stateText(claim.permission(ClaimPermission.TELEPORT)),
            "{perm_flight}", menu.stateText(claim.permission(ClaimPermission.FLIGHT)),
            "{custom_count}", String.valueOf(menu.countCustomFlags(claim))
        ));
        for (PermissionEntry entry : PERMISSION_ENTRIES) {
            renderPermission(inventory, claim, entry);
        }
        for (ClaimFlag flag : ClaimFlag.values()) {
            String itemKey = menu.flagItemKey(flag);
            if (!menu.hasItem("claim-permissions", itemKey)) {
                continue;
            }
            ClaimFlagState state = claim.flagState(flag);
            inventory.setItem(menu.slot("claim-permissions", itemKey), menu.configuredItem("claim-permissions", itemKey,
                "{state}", menu.flagStateText(claim, flag, state)
            ));
        }
        inventory.setItem(menu.slot("claim-permissions", "disable-all"), menu.configuredItem("claim-permissions", "disable-all"));
        inventory.setItem(menu.slot("claim-permissions", "back"), menu.configuredItem("claim-permissions", "back"));
        player.openInventory(inventory);
    }

    public void handle(Player player, ClaimPermissionHolder holder, int slot, boolean rightClick) {
        Claim claim = menu.claimService().findClaimByIdFresh(holder.claimId).orElse(null);
        if (claim == null) {
            player.closeInventory();
            player.sendMessage(menu.plugin().message("claim-not-found"));
            return;
        }

        for (PermissionEntry entry : PERMISSION_ENTRIES) {
            if (menu.hasItem("claim-permissions", entry.itemKey()) && slot == menu.slot("claim-permissions", entry.itemKey())) {
                togglePermission(player, claim, entry.permission(), entry.itemKey());
                return;
            }
        }
        for (ClaimFlag flag : ClaimFlag.values()) {
            String itemKey = menu.flagItemKey(flag);
            if (menu.hasItem("claim-permissions", itemKey) && slot == menu.slot("claim-permissions", itemKey)) {
                if (!menu.claimActionService().canManageFlags(player, claim)) {
                    player.sendMessage(menu.plugin().message("trust-no-permission"));
                    return;
                }
                menu.playConfiguredSound(player, "claim-permissions", itemKey);
                ClaimFlagState currentState = claim.flagState(flag);
                ClaimFlagState nextState = nextFlagState(claim, flag, currentState, rightClick);
                menu.claimService().updateFlagState(claim, flag, nextState, player.getUniqueId());
                menu.openClaimPermissionsMenu(player, claim);
                return;
            }
        }
        if (slot == menu.slot("claim-permissions", "disable-all")) {
            disableDangerousPermissions(player, claim, "disable-all");
            return;
        }
        if (slot == menu.slot("claim-permissions", "back")) {
            menu.playConfiguredSound(player, "claim-permissions", "back");
            menu.openCoreMenu(player, claim);
        }
    }

    private void renderPermission(Inventory inventory, Claim claim, PermissionEntry entry) {
        if (!menu.hasItem("claim-permissions", entry.itemKey())) {
            return;
        }
        inventory.setItem(menu.slot("claim-permissions", entry.itemKey()), menu.configuredItem(
            "claim-permissions",
            entry.itemKey(),
            "{state}",
            menu.stateText(claim.permission(entry.permission()))
        ));
    }

    private void togglePermission(Player player, Claim claim, ClaimPermission permission, String itemKey) {
        if (!menu.claimActionService().canManagePermissions(player, claim)) {
            player.sendMessage(menu.plugin().message("trust-no-permission"));
            return;
        }
        menu.playConfiguredSound(player, "claim-permissions", itemKey);
        menu.claimService().updatePermission(claim, permission, !claim.permission(permission), player.getUniqueId());
        menu.openClaimPermissionsMenu(player, claim);
    }

    private void disableDangerousPermissions(Player player, Claim claim, String itemKey) {
        if (!menu.claimActionService().canManagePermissions(player, claim) || !menu.claimActionService().canManageFlags(player, claim)) {
            player.sendMessage(menu.plugin().message("trust-no-permission"));
            return;
        }
        menu.playConfiguredSound(player, "claim-permissions", itemKey);
        for (ClaimPermission permission : ClaimPermission.values()) {
            if (permission == ClaimPermission.TELEPORT || permission == ClaimPermission.FLIGHT) {
                continue;
            }
            menu.claimService().updatePermission(claim, permission, false, player.getUniqueId());
        }
        menu.claimService().updateFlagState(claim, ClaimFlag.LIQUID_FLOW, ClaimFlagState.DENY, player.getUniqueId());
        menu.openClaimPermissionsMenu(player, claim);
    }

    private ClaimFlagState nextFlagState(Claim claim, ClaimFlag flag, ClaimFlagState currentState, boolean rightClick) {
        if (rightClick) {
            return ClaimFlagState.UNSET;
        }
        if (flag == ClaimFlag.TIME_CYCLE) {
            return currentState.next();
        }
        return switch (currentState) {
            case ALLOW -> ClaimFlagState.DENY;
            case DENY -> ClaimFlagState.ALLOW;
            case UNSET -> claim.permission(flag.fallbackPermission()) ? ClaimFlagState.DENY : ClaimFlagState.ALLOW;
        };
    }

    private record PermissionEntry(ClaimPermission permission, String itemKey) {
    }
}
