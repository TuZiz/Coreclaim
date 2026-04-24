package com.coreclaim.command;

import org.bukkit.command.CommandSender;

final class ClaimUserCommandHandler {

    private final CoreClaimCommand command;

    ClaimUserCommandHandler(CoreClaimCommand command) {
        this.command = command;
    }

    boolean handle(CommandSender sender, String sub, String[] args) {
        return command.dispatchUserCommand(sender, sub, args);
    }
}
