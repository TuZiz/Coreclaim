package com.coreclaim.command;

import com.coreclaim.model.Claim;
import com.coreclaim.model.ClaimFlag;
import com.coreclaim.model.ClaimFlagState;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

final class ClaimRuleCommandHandler {

    private final CoreClaimCommand command;

    ClaimRuleCommandHandler(CoreClaimCommand command) {
        this.command = command;
    }

    boolean handleFlag(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(command.plugin().message("player-only"));
            return true;
        }
        if (!player.hasPermission("coreclaim.manage.flags") && !command.hasAdminFlagManagePermission(player)) {
            player.sendMessage(command.plugin().message("no-permission"));
            return true;
        }
        Claim claim = command.resolveCurrentEditableClaim(player, "/claim flag", current -> command.claimActionService().canManageFlags(player, current));
        if (claim == null) {
            return true;
        }
        if (args.length == 1 || args.length == 2 && command.resolver().isFlagListInput(args[1])) {
            command.formatter().sendFlagSummary(player, claim);
            return true;
        }
        if (args.length < 3) {
            player.sendMessage(command.plugin().message("flag-usage"));
            return true;
        }
        ClaimFlag flag = ClaimFlag.fromKey(args[1]);
        if (flag == null) {
            player.sendMessage(command.plugin().message("flag-invalid", "{flag}", args[1]));
            return true;
        }
        ClaimFlagState state = ClaimFlagState.fromInput(args[2]);
        if (state == null) {
            player.sendMessage(command.plugin().message("flag-state-invalid"));
            return true;
        }
        command.claimService().updateFlagState(claim, flag, state, player.getUniqueId());
        player.sendMessage(command.plugin().message("flag-updated", "{name}", claim.name(), "{flag}", flag.key(), "{state}", command.formatter().flagStateText(flag, state)));
        return true;
    }
}
