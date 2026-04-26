package com.coreclaim.command;

import java.util.List;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;

final class ClaimTabCompletionService {

    private final CoreClaimCommand command;

    ClaimTabCompletionService(CoreClaimCommand command) {
        this.command = command;
    }

    List<String> complete(CommandSender sender, Command command, String alias, String[] args) {
        return this.command.completeTab(sender, command, alias, args);
    }
}
