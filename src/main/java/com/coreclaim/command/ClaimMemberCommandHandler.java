package com.coreclaim.command;

import com.coreclaim.model.Claim;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

final class ClaimMemberCommandHandler {

    private final CoreClaimCommand command;

    ClaimMemberCommandHandler(CoreClaimCommand command) {
        this.command = command;
    }

    boolean handleUnadd(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(command.plugin().message("player-only"));
            return true;
        }
        if (args.length != 2) {
            player.sendMessage(command.plugin().message("unadd-usage"));
            return true;
        }
        Claim claim = command.claimActionService().findCurrentPresenceClaim(player);
        if (claim == null) {
            player.sendMessage(command.plugin().message("claim-current-edit-required-unadd"));
            return true;
        }
        if (!command.claimActionService().canManageMembers(player, claim)) {
            player.sendMessage(command.plugin().message("trust-no-permission"));
            return true;
        }
        OfflinePlayer target = command.resolver().resolveKnownPlayer(args[1]);
        if (target == null || target.getUniqueId() == null) {
            player.sendMessage(command.plugin().message("trust-no-target"));
            return true;
        }
        command.claimActionService().untrustPlayer(player, claim, target);
        return true;
    }

    boolean handleAdd(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(command.plugin().message("player-only"));
            return true;
        }
        if (args.length != 2) {
            player.sendMessage(command.plugin().message("add-usage"));
            return true;
        }
        Claim claim = command.claimActionService().findCurrentPresenceClaim(player);
        if (claim == null) {
            player.sendMessage(command.plugin().message("claim-current-edit-required-add"));
            return true;
        }
        if (!command.claimActionService().canManageMembers(player, claim)) {
            player.sendMessage(command.plugin().message("trust-no-permission"));
            return true;
        }
        OfflinePlayer target = command.resolver().resolveKnownPlayer(args[1]);
        if (target == null) {
            player.sendMessage(command.plugin().message("trust-no-target"));
            return true;
        }
        command.claimActionService().trustPlayer(player, claim, target);
        return true;
    }

    boolean handleDeny(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(command.plugin().message("player-only"));
            return true;
        }
        if (!command.hasManageDenyPermission(player)) {
            player.sendMessage(command.plugin().message("no-permission"));
            return true;
        }
        if (args.length != 2) {
            player.sendMessage(command.plugin().message("deny-usage"));
            return true;
        }
        Claim claim = command.resolveCurrentEditableClaim(player, "/claim deny");
        if (claim == null) {
            return true;
        }
        String targetName = args[1].trim();
        if (ClaimDenyTargets.isAllTarget(targetName)) {
            if (claim.denyAll()) {
                player.sendMessage(command.plugin().message("claim-deny-all-already-enabled"));
                return true;
            }
            command.claimService().updateDenyAll(claim, true, player.getUniqueId());
            player.sendMessage(command.plugin().message("claim-deny-all-enabled", "{name}", claim.name()));
            return true;
        }
        OfflinePlayer target = command.resolver().resolveKnownPlayer(targetName);
        if (target == null || target.getUniqueId() == null) {
            player.sendMessage(command.plugin().message("trust-no-target"));
            return true;
        }
        if (target.getUniqueId().equals(claim.owner())) {
            player.sendMessage(command.plugin().message("trust-self"));
            return true;
        }
        if (!command.claimService().addDeniedMember(claim, target.getUniqueId(), player.getUniqueId())) {
            player.sendMessage(command.plugin().message("claim-deny-exists", "{player}", command.resolver().displayName(target), "{name}", claim.name()));
            return true;
        }
        player.sendMessage(command.plugin().message("claim-deny-added", "{player}", command.resolver().displayName(target), "{name}", claim.name()));
        return true;
    }

    boolean handleUndeny(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(command.plugin().message("player-only"));
            return true;
        }
        if (!command.hasManageDenyPermission(player)) {
            player.sendMessage(command.plugin().message("no-permission"));
            return true;
        }
        if (args.length != 2) {
            player.sendMessage(command.plugin().message("undeny-usage"));
            return true;
        }
        Claim claim = command.resolveCurrentEditableClaim(player, "/claim undeny");
        if (claim == null) {
            return true;
        }
        String targetName = args[1].trim();
        if (ClaimDenyTargets.isAllTarget(targetName)) {
            if (!claim.denyAll()) {
                player.sendMessage(command.plugin().message("claim-deny-all-already-disabled"));
                return true;
            }
            command.claimService().updateDenyAll(claim, false, player.getUniqueId());
            player.sendMessage(command.plugin().message("claim-deny-all-disabled", "{name}", claim.name()));
            return true;
        }
        OfflinePlayer target = command.resolver().resolveKnownPlayer(targetName);
        if (target == null || target.getUniqueId() == null) {
            player.sendMessage(command.plugin().message("trust-no-target"));
            return true;
        }
        if (!command.claimService().removeDeniedMember(claim, target.getUniqueId(), player.getUniqueId())) {
            player.sendMessage(command.plugin().message("claim-deny-missing", "{player}", command.resolver().displayName(target), "{name}", claim.name()));
            return true;
        }
        player.sendMessage(command.plugin().message("claim-deny-removed", "{player}", command.resolver().displayName(target), "{name}", claim.name()));
        return true;
    }
}
