package com.coreclaim.command;

import org.bukkit.command.CommandSender;

final class ClaimRuleCommandHandler {

    private final CoreClaimCommand command;

    ClaimRuleCommandHandler(CoreClaimCommand command) {
        this.command = command;
    }

    boolean handleFlag(CommandSender sender, String[] args) {
        sender.sendMessage(command.plugin().message("flag-command-migrated"));
        return true;
    }
}
