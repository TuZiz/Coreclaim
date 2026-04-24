package com.coreclaim.command;

import org.bukkit.command.CommandSender;

final class ClaimAdminCommandHandler {

    private final CoreClaimCommand command;

    ClaimAdminCommandHandler(CoreClaimCommand command) {
        this.command = command;
    }

    boolean handle(CommandSender sender, String[] args) {
        return command.dispatchAdminCommand(sender, args);
    }
}
