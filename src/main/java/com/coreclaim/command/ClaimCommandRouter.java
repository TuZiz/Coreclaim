package com.coreclaim.command;

import com.coreclaim.util.AdminAccess;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

final class ClaimCommandRouter {

    private final CoreClaimCommand command;
    private final ClaimUserCommandHandler userHandler;
    private final ClaimAdminCommandHandler adminHandler;

    ClaimCommandRouter(
        CoreClaimCommand command,
        ClaimUserCommandHandler userHandler,
        ClaimAdminCommandHandler adminHandler
    ) {
        this.command = command;
        this.userHandler = userHandler;
        this.adminHandler = adminHandler;
    }

    boolean route(CommandSender sender, String[] args) {
        if (!sender.hasPermission("coreclaim.use") && !AdminAccess.hasAnyAdminNode(sender)) {
            sender.sendMessage(command.plugin().message("no-permission"));
            return true;
        }

        if (args.length == 0) {
            if (sender instanceof Player) {
                return command.openDefaultMenu(sender);
            }
            command.sendModernHelpPublic(sender);
            return true;
        }

        if (args[0].equalsIgnoreCase("help")) {
            command.sendModernHelpPublic(sender);
            return true;
        }

        if (args[0].equalsIgnoreCase("admin")) {
            return adminHandler.handle(sender, args);
        }
        return userHandler.handle(sender, args[0], args);
    }
}
