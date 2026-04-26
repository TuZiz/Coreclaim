package com.coreclaim.command;

import org.bukkit.command.CommandSender;

final class ClaimUserCommandHandler {

    private final CoreClaimCommand command;
    private final ClaimPlayerCommandHandler playerCommands;
    private final ClaimMemberCommandHandler memberCommands;
    private final ClaimRuleCommandHandler ruleCommands;
    private final ClaimTransferCommandHandler transferCommands;
    private final ClaimUtilityCommandHandler utilityCommands;

    ClaimUserCommandHandler(CoreClaimCommand command) {
        this.command = command;
        this.playerCommands = new ClaimPlayerCommandHandler(command);
        this.memberCommands = new ClaimMemberCommandHandler(command);
        this.ruleCommands = new ClaimRuleCommandHandler(command);
        this.transferCommands = new ClaimTransferCommandHandler(command);
        this.utilityCommands = new ClaimUtilityCommandHandler(command);
    }

    boolean handle(CommandSender sender, String sub, String[] args) {
        return switch (sub.toLowerCase(java.util.Locale.ROOT)) {
            case "info" -> playerCommands.handleCurrentClaimInfo(sender);
            case "list" -> playerCommands.handleList(sender);
            case "menu" -> playerCommands.handleMenu(sender);
            case "show" -> playerCommands.handleShow(sender, args);
            case "create" -> playerCommands.handleCreate(sender, args);
            case "edit" -> playerCommands.handleEdit(sender, args);
            case "tp" -> playerCommands.handleTeleport(sender, args);
            case "tpset" -> playerCommands.handleTpSet(sender);
            case "expand" -> playerCommands.handleExpand(sender, args);
            case "remove" -> playerCommands.handleRemoveClaim(sender, args);
            case "unadd" -> memberCommands.handleUnadd(sender, args);
            case "confirm" -> playerCommands.handleConfirm(sender);
            case "deny" -> memberCommands.handleDeny(sender, args);
            case "undeny" -> memberCommands.handleUndeny(sender, args);
            case "flag" -> ruleCommands.handleFlag(sender, args);
            case "add" -> memberCommands.handleAdd(sender, args);
            case "transfer" -> transferCommands.handleTransfer(sender, args);
            case "activity" -> utilityCommands.handleActivity(sender, args);
            case "reload" -> utilityCommands.handleReload(sender);
            case "givecore" -> utilityCommands.handleGiveCore(sender, args);
            default -> {
                command.sendModernHelpPublic(sender);
                yield true;
            }
        };
    }
}
