package com.coreclaim.command;

import com.coreclaim.CoreClaimPlugin;
import com.coreclaim.gui.MenuService;
import com.coreclaim.model.Claim;
import com.coreclaim.service.ClaimActionService;
import com.coreclaim.cleanup.ClaimCleanupService;
import com.coreclaim.selection.ClaimSelectionService;
import com.coreclaim.service.ClaimService;
import com.coreclaim.transfer.ClaimTransferService;
import com.coreclaim.service.ClaimVisualService;
import com.coreclaim.profile.ProfileService;
import com.coreclaim.service.RemovalConfirmationService;
import com.coreclaim.util.AdminAccess;
import java.util.List;
import java.util.Locale;
import java.util.function.Predicate;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.permissions.Permissible;

public final class CoreClaimCommand
implements TabExecutor {
    private final CoreClaimPlugin plugin;
    private final ClaimService claimService;
    private final ProfileService profileService;
    private final ClaimActionService claimActionService;
    private final ClaimVisualService claimVisualService;
    private final ClaimSelectionService claimSelectionService;
    private final MenuService menuService;
    private final RemovalConfirmationService removalConfirmationService;
    private final ClaimTransferService claimTransferService;
    private final ClaimCleanupService claimCleanupService;
    private final ClaimCommandFormatter formatter;
    private final ClaimCommandResolver resolver;
    private final ClaimAdminClaimCommandHandler adminClaimCommands;
    private final ClaimAdminRuleCommandHandler adminRuleCommands;
    private final ClaimAdminMemberCommandHandler adminMemberCommands;
    private final ClaimAdminCleanupCommandHandler adminCleanupCommands;
    private final ClaimCommandRouter commandRouter;
    private final ClaimTabCompletionService tabCompletionService;

    public CoreClaimCommand(CoreClaimPlugin plugin, ClaimService claimService, ProfileService profileService, ClaimActionService claimActionService, ClaimVisualService claimVisualService, ClaimSelectionService claimSelectionService, MenuService menuService, RemovalConfirmationService removalConfirmationService, ClaimTransferService claimTransferService, ClaimCleanupService claimCleanupService) {
        this.plugin = plugin;
        this.claimService = claimService;
        this.profileService = profileService;
        this.claimActionService = claimActionService;
        this.claimVisualService = claimVisualService;
        this.claimSelectionService = claimSelectionService;
        this.menuService = menuService;
        this.removalConfirmationService = removalConfirmationService;
        this.claimTransferService = claimTransferService;
        this.claimCleanupService = claimCleanupService;
        this.formatter = new ClaimCommandFormatter(plugin, claimService);
        this.resolver = new ClaimCommandResolver(plugin, claimService, profileService);
        this.adminClaimCommands = new ClaimAdminClaimCommandHandler(plugin, claimService, claimActionService, claimSelectionService, removalConfirmationService, formatter, resolver);
        this.adminRuleCommands = new ClaimAdminRuleCommandHandler(plugin, claimService, claimActionService, formatter, resolver);
        this.adminMemberCommands = new ClaimAdminMemberCommandHandler(plugin, claimService, claimActionService, resolver);
        this.adminCleanupCommands = new ClaimAdminCleanupCommandHandler(plugin, claimCleanupService, formatter, resolver);
        ClaimUserCommandHandler userCommandHandler = new ClaimUserCommandHandler(this);
        ClaimAdminCommandHandler adminCommandHandler = new ClaimAdminCommandHandler(this);
        this.commandRouter = new ClaimCommandRouter(this, userCommandHandler, adminCommandHandler);
        this.tabCompletionService = new ClaimTabCompletionService(this);
    }

    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        return this.commandRouter.route(sender, args);
    }

    CoreClaimPlugin plugin() {
        return this.plugin;
    }

    ClaimService claimService() {
        return this.claimService;
    }

    ProfileService profileService() {
        return this.profileService;
    }

    ClaimActionService claimActionService() {
        return this.claimActionService;
    }

    ClaimVisualService claimVisualService() {
        return this.claimVisualService;
    }

    ClaimSelectionService claimSelectionService() {
        return this.claimSelectionService;
    }

    MenuService menuService() {
        return this.menuService;
    }

    RemovalConfirmationService removalConfirmationService() {
        return this.removalConfirmationService;
    }

    ClaimTransferService claimTransferService() {
        return this.claimTransferService;
    }

    ClaimCommandFormatter formatter() {
        return this.formatter;
    }

    ClaimCommandResolver resolver() {
        return this.resolver;
    }

    boolean openDefaultMenu(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(this.plugin.message("player-only"));
            return true;
        }
        this.menuService.openMainMenu(player);
        return true;
    }

    void sendModernHelpPublic(CommandSender sender) {
        for (String line : this.plugin.messageList("help-player", "{starter_minutes}", String.valueOf(this.plugin.settings().starterRewardMinutes()))) {
            sender.sendMessage(line);
        }
        if (this.hasAnyAdminPermission(sender)) {
            for (String line : this.plugin.messageList("help-admin")) {
                sender.sendMessage(line);
            }
        }
    }

    boolean dispatchAdminCommand(CommandSender sender, String[] args) {
        return this.handleAdmin(sender, args);
    }

    private boolean handleAdmin(CommandSender sender, String[] args) {
        String action;
        if (!this.hasAnyAdminPermission(sender)) {
            sender.sendMessage(this.plugin.message("no-permission"));
            return true;
        }
        if (args.length < 2) {
            this.sendModernHelpPublic(sender);
            return true;
        }
        return switch (action = args[1].toLowerCase(Locale.ROOT)) {
            case "help", "?" -> {
                this.sendModernHelpPublic(sender);
                yield true;
            }
            case "create" -> this.handleAdminCreate(sender, args);
            case "info" -> this.handleAdminInfo(sender, args);
            case "playerclaims" -> this.handleAdminPlayerClaims(sender, args);
            case "diagnose" -> this.handleAdminDiagnose(sender, args);
            case "add" -> this.handleAdminAdd(sender, args);
            case "remove" -> this.handleAdminRemove(sender, args);
            case "transfer" -> this.handleAdminTransfer(sender, args);
            case "permission" -> this.handleAdminPermission(sender, args);
            case "flag" -> this.handleAdminFlag(sender, args);
            case "deny" -> this.handleAdminDeny(sender, args);
            case "undeny" -> this.handleAdminUndeny(sender, args);
            case "unadd" -> this.handleAdminUnadd(sender, args);
            case "cleanup" -> this.handleAdminCleanup(sender, args);
            case "setserver" -> this.handleAdminSetServer(sender, args);
            default -> {
                this.sendModernHelpPublic(sender);
                yield true;
            }
        };
    }

    private boolean handleAdminCreate(CommandSender sender, String[] args) {
        return this.adminClaimCommands.handleCreate(sender, args);
    }

    private boolean handleAdminInfo(CommandSender sender, String[] args) {
        return this.adminClaimCommands.handleInfo(sender, args);
    }

    private boolean handleAdminPlayerClaims(CommandSender sender, String[] args) {
        return this.adminClaimCommands.handlePlayerClaims(sender, args);
    }

    private boolean handleAdminDiagnose(CommandSender sender, String[] args) {
        return this.adminClaimCommands.handleDiagnose(sender, args);
    }

    private boolean handleAdminPermission(CommandSender sender, String[] args) {
        return this.adminRuleCommands.handlePermission(sender, args);
    }

    private boolean handleAdminFlag(CommandSender sender, String[] args) {
        return this.adminRuleCommands.handleFlag(sender, args);
    }

    private boolean handleAdminDeny(CommandSender sender, String[] args) {
        return this.adminMemberCommands.handleDeny(sender, args);
    }

    private boolean handleAdminUndeny(CommandSender sender, String[] args) {
        return this.adminMemberCommands.handleUndeny(sender, args);
    }

    private boolean handleAdminAdd(CommandSender sender, String[] args) {
        return this.adminMemberCommands.handleAdd(sender, args);
    }

    private boolean handleAdminRemove(CommandSender sender, String[] args) {
        return this.adminClaimCommands.handleRemove(sender, args);
    }

    private boolean handleAdminTransfer(CommandSender sender, String[] args) {
        if (!this.hasAdminClaimManagePermission(sender)) {
            sender.sendMessage(this.plugin.message("no-permission"));
            return true;
        }
        if (args.length < 4) {
            sender.sendMessage(this.plugin.message("admin-transfer-usage"));
            return true;
        }
        String claimName = this.resolver.joinArgs(args, 2, args.length - 1);
        Claim claim = this.resolver.resolveAdminClaimSelector(sender, claimName);
        if (claim == null) {
            return true;
        }
        OfflinePlayer target = this.resolver.resolveKnownPlayer(args[args.length - 1]);
        this.claimTransferService.forceTransfer(sender, claim, target);
        return true;
    }

    private boolean handleAdminUnadd(CommandSender sender, String[] args) {
        return this.adminMemberCommands.handleUnadd(sender, args);
    }

    private boolean handleAdminCleanup(CommandSender sender, String[] args) {
        if (!this.hasAdminClaimManagePermission(sender)) {
            sender.sendMessage(this.plugin.message("no-permission"));
            return true;
        }
        return this.adminCleanupCommands.handle(sender, args);
    }

    private boolean handleAdminSetServer(CommandSender sender, String[] args) {
        return this.adminClaimCommands.handleSetServer(sender, args);
    }

    boolean hasAnyAdminPermission(CommandSender sender) {
        return AdminAccess.hasAnyAdminNode((Permissible)sender);
    }

    boolean hasAdminViewPermission(CommandSender sender) {
        return AdminAccess.hasViewAccess((Permissible)sender);
    }

    boolean hasAdminForcePermission(CommandSender sender) {
        return AdminAccess.hasForceBypass((Permissible)sender);
    }

    boolean hasAdminOpsPermission(CommandSender sender) {
        return AdminAccess.hasOpsAccess((Permissible)sender);
    }

    boolean hasAdminCreateSystemPermission(CommandSender sender) {
        return AdminAccess.hasCreateSystemAccess((Permissible)sender);
    }

    boolean hasAdminMemberManagePermission(CommandSender sender) {
        return AdminAccess.hasMemberManageAccess((Permissible)sender);
    }

    boolean hasAdminPermissionManagePermission(CommandSender sender) {
        return AdminAccess.hasPermissionManageAccess((Permissible)sender);
    }

    boolean hasAdminFlagManagePermission(CommandSender sender) {
        return AdminAccess.hasFlagManageAccess((Permissible)sender);
    }

    boolean hasAdminClaimManagePermission(CommandSender sender) {
        return AdminAccess.hasClaimManageAccess((Permissible)sender);
    }

    boolean hasAdminActivityManagePermission(CommandSender sender) {
        return AdminAccess.hasActivityManageAccess((Permissible)sender);
    }

    boolean hasAdminRewardPermission(CommandSender sender) {
        return AdminAccess.hasRewardAccess((Permissible)sender);
    }

    boolean hasManageDenyPermission(CommandSender sender) {
        return sender.hasPermission("coreclaim.manage.deny") || this.hasAdminForcePermission(sender);
    }

    boolean hasManageTeleportPermission(CommandSender sender) {
        return sender.hasPermission("coreclaim.manage.tpset") || this.hasAdminForcePermission(sender);
    }

    Claim resolveCurrentEditableClaim(Player player, String usageLabel) {
        return this.resolveCurrentEditableClaim(player, usageLabel, current -> this.claimActionService.canManageClaim(player, current));
    }

    Claim resolveCurrentEditableClaim(Player player, String usageLabel, Predicate<Claim> accessCheck) {
        Claim claim = this.claimActionService.findCurrentPresenceClaim(player);
        if (claim == null) {
            player.sendMessage(this.plugin.message("claim-current-edit-required", "{usage}", usageLabel));
            return null;
        }
        if (!accessCheck.test(claim)) {
            player.sendMessage(this.plugin.message("trust-no-permission"));
            return null;
        }
        return claim;
    }

    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        return this.tabCompletionService.complete(sender, command, alias, args);
    }
}
