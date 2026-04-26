package com.coreclaim.command;

import com.coreclaim.model.Claim;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

final class ClaimTransferCommandHandler {

    private final CoreClaimCommand command;

    ClaimTransferCommandHandler(CoreClaimCommand command) {
        this.command = command;
    }

    boolean handleTransfer(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(command.plugin().message("player-only"));
            return true;
        }
        if (!player.hasPermission("coreclaim.transfer")) {
            player.sendMessage(command.plugin().message("no-permission"));
            return true;
        }
        if (args.length < 2) {
            player.sendMessage(command.plugin().message("transfer-usage"));
            return true;
        }
        if (args.length == 2 && args[1].equalsIgnoreCase("accept")) {
            command.claimTransferService().accept(player);
            return true;
        }
        if (args.length == 2 && (args[1].equalsIgnoreCase("deny") || args[1].equalsIgnoreCase("cancel"))) {
            command.claimTransferService().deny(player);
            return true;
        }
        Claim claim;
        String targetName;
        if (args.length == 2) {
            claim = command.claimActionService().findOwnedClaim(player);
            if (claim == null) {
                player.sendMessage(command.plugin().message("claim-not-found"));
                return true;
            }
            targetName = args[1];
        } else {
            String claimName = command.resolver().joinArgs(args, 1, args.length - 1);
            if (claimName.isBlank()) {
                player.sendMessage(command.plugin().message("transfer-usage"));
                return true;
            }
            claim = command.resolver().resolveOwnedClaimByName(player, claimName);
            if (claim == null) {
                return true;
            }
            targetName = args[args.length - 1];
        }
        Player target = command.resolver().resolveOnlinePlayer(targetName);
        command.claimTransferService().requestTransfer(player, claim, target);
        return true;
    }
}
