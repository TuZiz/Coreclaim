package com.coreclaim.command;

import com.coreclaim.model.Claim;
import com.coreclaim.model.ClaimDirection;
import com.coreclaim.model.PlayerProfile;
import com.coreclaim.service.ClaimSelectionService;
import com.coreclaim.service.ClaimService;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

final class ClaimPlayerCommandHandler {

    private final CoreClaimCommand command;

    ClaimPlayerCommandHandler(CoreClaimCommand command) {
        this.command = command;
    }

    boolean handleList(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(command.plugin().message("player-only"));
            return true;
        }
        List<ClaimService.ClaimListEntry> claims = command.claimService().visibleClaimsOfFresh(player.getUniqueId());
        if (claims.isEmpty()) {
            player.sendMessage(command.plugin().message("claim-list-empty"));
            return true;
        }
        player.sendMessage(command.plugin().message("claim-list-header"));
        for (ClaimService.ClaimListEntry entry : claims) {
            Claim claim = entry.claim();
            player.sendMessage(command.plugin().message(
                "claim-list-entry",
                "{relation}", command.formatter().claimListRelationText(entry.relation()),
                "{name}", claim.name(),
                "{owner}", claim.ownerName(),
                "{x}", String.valueOf(claim.centerX()),
                "{z}", String.valueOf(claim.centerZ()),
                "{width}", String.valueOf(claim.width()),
                "{depth}", String.valueOf(claim.depth())
            ));
        }
        return true;
    }

    boolean handleMenu(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(command.plugin().message("player-only"));
            return true;
        }
        command.menuService().openMainMenu(player);
        return true;
    }

    boolean handleCurrentClaimInfo(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(command.plugin().message("player-only"));
            return true;
        }
        Claim claim = command.claimService().findClaim(player.getLocation()).orElse(null);
        if (claim == null) {
            player.sendMessage(command.plugin().message("claim-not-found"));
            return true;
        }
        if (!(claim.owner().equals(player.getUniqueId())
            || command.hasAdminViewPermission(player)
            || command.claimService().canAccess(claim, player.getUniqueId()))) {
            player.sendMessage(command.plugin().message("trust-no-permission"));
            return true;
        }
        command.formatter().sendEnhancedClaimDetails(player, claim, false);
        return true;
    }

    boolean handleExpand(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(command.plugin().message("player-only"));
            return true;
        }
        if (args.length < 2) {
            player.sendMessage(command.plugin().message("expand-usage"));
            return true;
        }
        ClaimDirection direction = ClaimDirection.fromInput(args[1]);
        if (direction == null) {
            player.sendMessage(command.plugin().message("expand-usage"));
            return true;
        }
        command.claimActionService().expandCurrentClaim(player, direction);
        return true;
    }

    boolean handleShow(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(command.plugin().message("player-only"));
            return true;
        }
        if (args.length >= 2 && args[1].equalsIgnoreCase("auto")) {
            return handleShowAuto(player, args);
        }
        Claim claim;
        if (args.length >= 2) {
            claim = command.resolver().resolveAccessibleClaimByName(player, command.resolver().joinArgs(args, 1));
            if (claim == null) {
                return true;
            }
        } else {
            claim = command.claimService().findClaim(player.getLocation())
                .filter(found -> found.owner().equals(player.getUniqueId())
                    || command.claimService().canAccess(found, player.getUniqueId())
                    || command.hasAdminForcePermission(player))
                .orElse(null);
            if (claim == null) {
                player.sendMessage(command.plugin().message("show-usage"));
                return true;
            }
        }
        command.claimVisualService().showClaim(player, claim);
        player.sendMessage(command.plugin().message("claim-show-success", "{name}", claim.name()));
        return true;
    }

    boolean handleCreate(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(command.plugin().message("player-only"));
            return true;
        }
        if (args.length < 2) {
            player.sendMessage(command.plugin().message("selection-create-usage"));
            return true;
        }
        String name = String.join(" ", Arrays.copyOfRange(args, 1, args.length)).trim();
        ClaimSelectionService.SelectionPreview preview = command.claimSelectionService().preview(player);
        if (preview == null || !preview.ready()) {
            player.sendMessage(command.plugin().message("claim-create-selection-required"));
            return true;
        }
        if (!preview.allowed()) {
            player.sendMessage(preview.failureMessage());
            return true;
        }
        if (name.isEmpty()) {
            player.sendMessage(command.plugin().message("claim-name-empty"));
            return true;
        }
        if (name.length() > command.plugin().settings().claimNameMaxLength()) {
            player.sendMessage(command.plugin().message("claim-name-too-long", "{max}", String.valueOf(command.plugin().settings().claimNameMaxLength())));
            return true;
        }
        if (command.claimService().isClaimNameTaken(name)) {
            player.sendMessage(command.plugin().message("claim-name-exists", "{name}", name));
            return true;
        }
        command.menuService().openSelectionCreateMenu(player, name, preview);
        return true;
    }

    boolean handleTeleport(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(command.plugin().message("player-only"));
            return true;
        }
        if (args.length < 2) {
            player.sendMessage(command.plugin().message("teleport-usage"));
            return true;
        }
        Claim claim = command.resolver().resolveTeleportClaimByName(player, command.resolver().joinArgs(args, 1));
        if (claim == null) {
            return true;
        }
        command.claimActionService().teleportToClaim(player, claim);
        return true;
    }

    boolean handleTpSet(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(command.plugin().message("player-only"));
            return true;
        }
        if (!command.hasManageTeleportPermission(player)) {
            player.sendMessage(command.plugin().message("no-permission"));
            return true;
        }
        Claim claim = command.resolveCurrentEditableClaim(player, "/claim tpset");
        if (claim == null) {
            return true;
        }
        if (!command.claimService().isLocalClaim(claim)) {
            player.sendMessage(command.plugin().message("tpset-cross-server-denied"));
            return true;
        }
        command.claimService().updateTeleportPoint(claim, player.getLocation(), player.getUniqueId());
        player.sendMessage(command.plugin().message("claim-tpset-success", "{name}", claim.name()));
        return true;
    }

    boolean handleRemoveClaim(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(command.plugin().message("player-only"));
            return true;
        }
        if (args.length < 2) {
            player.sendMessage(command.plugin().message("remove-usage"));
            return true;
        }
        Claim claim = command.resolver().resolveOwnedClaimByName(player, command.resolver().joinArgs(args, 1));
        if (claim == null) {
            return true;
        }
        command.removalConfirmationService().request(player, claim);
        return true;
    }

    boolean handleConfirm(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(command.plugin().message("player-only"));
            return true;
        }
        if (!command.removalConfirmationService().confirm(player)) {
            player.sendMessage(command.plugin().message("confirm-nothing"));
        }
        return true;
    }

    boolean handleEdit(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(command.plugin().message("player-only"));
            return true;
        }
        if (!command.hasAdminClaimManagePermission(player)) {
            player.sendMessage(command.plugin().message("no-permission"));
            return true;
        }
        Claim claim;
        if (args.length >= 2) {
            claim = command.resolver().resolveAdminClaimByName(player, command.resolver().joinArgs(args, 1));
            if (claim == null) {
                return true;
            }
        } else {
            claim = command.claimService().findClaim(player.getLocation()).orElse(null);
            if (claim == null) {
                player.sendMessage(command.plugin().message("claim-not-found"));
                return true;
            }
        }
        command.menuService().openCoreMenu(player, claim);
        player.sendMessage(command.plugin().message("admin-edit-opened", "{name}", claim.name()));
        return true;
    }

    private boolean handleShowAuto(Player player, String[] args) {
        PlayerProfile profile = command.profileService().getOrCreate(player.getUniqueId(), player.getName());
        if (args.length == 2) {
            player.sendMessage(command.plugin().message(
                "show-auto-status",
                "{value}",
                command.plugin().plainMessage(profile.autoShowBorders() ? "show-auto-value-enabled" : "show-auto-value-disabled")
            ));
            return true;
        }
        String mode = args[2].toLowerCase(Locale.ROOT);
        boolean enabled;
        if (mode.equals("on") || mode.equals("enable")) {
            enabled = true;
        } else if (mode.equals("off") || mode.equals("disable")) {
            enabled = false;
        } else {
            player.sendMessage(command.plugin().message("show-auto-usage"));
            return true;
        }
        profile.setAutoShowBorders(enabled);
        command.profileService().saveProfile(profile);
        player.sendMessage(command.plugin().message(enabled ? "show-auto-enabled" : "show-auto-disabled"));
        return true;
    }
}
