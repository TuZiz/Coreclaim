package com.coreclaim.command;

import com.coreclaim.CoreClaimPlugin;
import com.coreclaim.command.ClaimAdminCommandHandler;
import com.coreclaim.command.ClaimCommandRouter;
import com.coreclaim.command.ClaimTabCompletionService;
import com.coreclaim.command.ClaimUserCommandHandler;
import com.coreclaim.gui.MenuService;
import com.coreclaim.model.Claim;
import com.coreclaim.model.ClaimCleanupReason;
import com.coreclaim.model.ClaimDirection;
import com.coreclaim.model.ClaimFlag;
import com.coreclaim.model.ClaimFlagState;
import com.coreclaim.model.ClaimPermission;
import com.coreclaim.model.PlayerProfile;
import com.coreclaim.service.ClaimActionService;
import com.coreclaim.service.ClaimCleanupService;
import com.coreclaim.service.ClaimSelectionService;
import com.coreclaim.service.ClaimService;
import com.coreclaim.service.ClaimTransferService;
import com.coreclaim.service.ClaimVisualService;
import com.coreclaim.service.ProfileService;
import com.coreclaim.service.RemovalConfirmationService;
import com.coreclaim.util.AdminAccess;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
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

    boolean openDefaultMenu(CommandSender sender) {
        return this.handleMenu(sender);
    }

    void sendModernHelpPublic(CommandSender sender) {
        this.sendModernHelp(sender);
    }

    boolean dispatchAdminCommand(CommandSender sender, String[] args) {
        return this.handleAdmin(sender, args);
    }

    boolean dispatchUserCommand(CommandSender sender, String sub, String[] args) {
        String normalizedSub;
        return switch (normalizedSub = sub.toLowerCase(Locale.ROOT)) {
            case "info" -> this.handleCurrentClaimInfo(sender);
            case "list" -> this.handleList(sender);
            case "menu" -> this.handleMenu(sender);
            case "show" -> this.handleShow(sender, args);
            case "create" -> this.handleCreate(sender, args);
            case "edit" -> this.handleEdit(sender, args);
            case "tp" -> this.handleTeleport(sender, args);
            case "tpset" -> this.handleTpSet(sender);
            case "expand" -> this.handleExpand(sender, args);
            case "remove" -> this.handleRemoveClaim(sender, args);
            case "unadd" -> this.handleUnadd(sender, args);
            case "confirm" -> this.handleConfirm(sender);
            case "deny" -> this.handleDeny(sender, args);
            case "undeny" -> this.handleUndeny(sender, args);
            case "flag" -> this.handleFlag(sender, args);
            case "add" -> this.handleAdd(sender, args);
            case "transfer" -> this.handleTransfer(sender, args);
            case "activity" -> this.handleActivity(sender, args);
            case "reload" -> this.handleReload(sender);
            case "givecore" -> this.handleGiveCore(sender, args);
            default -> {
                this.sendModernHelp(sender);
                yield true;
            }
        };
    }

    private boolean handleList(CommandSender sender) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(this.plugin.message("player-only"));
            return true;
        }
        Player player = (Player)sender;
        List<ClaimService.ClaimListEntry> claims = this.claimService.visibleClaimsOfFresh(player.getUniqueId());
        if (claims.isEmpty()) {
            player.sendMessage(this.chatMessage("claim-list-empty", "&e&o\u4f60\u76ee\u524d\u8fd8\u6ca1\u6709\u53ef\u67e5\u770b\u7684\u9886\u5730\u3002", new String[0]));
            return true;
        }
        player.sendMessage(this.plugin.color("&6[Claim] &f\u4f60\u7684\u9886\u5730\u5217\u8868:"));
        for (ClaimService.ClaimListEntry entry : claims) {
            Claim claim = entry.claim();
            player.sendMessage(this.plugin.color("&7[" + this.claimListRelationText(entry.relation()) + "&7] &e" + claim.name() + " &8| &7\u4e3b\u4eba: &b" + claim.ownerName() + " &8| &7\u6838\u5fc3: &f" + claim.centerX() + "," + claim.centerZ() + " &8| &7\u5927\u5c0f: &f" + claim.width() + "x" + claim.depth()));
        }
        return true;
    }

    private boolean handleMenu(CommandSender sender) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(this.plugin.message("player-only"));
            return true;
        }
        Player player = (Player)sender;
        this.menuService.openMainMenu(player);
        return true;
    }

    private boolean handleCurrentClaimInfo(CommandSender sender) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(this.plugin.message("player-only"));
            return true;
        }
        Player player = (Player)sender;
        Claim claim = this.claimService.findClaim(player.getLocation()).orElse(null);
        if (claim == null) {
            player.sendMessage(this.plugin.message("claim-not-found"));
            return true;
        }
        if (!(claim.owner().equals(player.getUniqueId()) || this.hasAdminViewPermission((CommandSender)player) || this.claimService.canAccess(claim, player.getUniqueId()))) {
            player.sendMessage(this.plugin.message("trust-no-permission"));
            return true;
        }
        this.sendEnhancedClaimDetails((CommandSender)player, claim, false);
        return true;
    }

    private boolean handleExpand(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(this.plugin.message("player-only"));
            return true;
        }
        Player player = (Player)sender;
        if (args.length < 2) {
            player.sendMessage(this.plugin.message("expand-usage"));
            return true;
        }
        ClaimDirection direction = ClaimDirection.fromInput(args[1]);
        if (direction == null) {
            player.sendMessage(this.plugin.message("expand-usage"));
            return true;
        }
        this.claimActionService.expandCurrentClaim(player, direction);
        return true;
    }

    private boolean handleShow(CommandSender sender, String[] args) {
        Claim claim;
        if (!(sender instanceof Player)) {
            sender.sendMessage(this.plugin.message("player-only"));
            return true;
        }
        Player player = (Player)sender;
        if (args.length >= 2 && args[1].equalsIgnoreCase("auto")) {
            return this.handleShowAuto(player, args);
        }
        if (args.length >= 2) {
            claim = this.resolveAccessibleClaimByName(player, this.joinArgs(args, 1));
            if (claim == null) {
                return true;
            }
        } else {
            claim = this.claimService.findClaim(player.getLocation()).filter(found -> found.owner().equals(player.getUniqueId()) || this.claimService.canAccess((Claim)found, player.getUniqueId()) || this.hasAdminForcePermission((CommandSender)player)).orElse(null);
            if (claim == null) {
                player.sendMessage(this.plugin.message("show-usage"));
                return true;
            }
        }
        this.claimVisualService.showClaim(player, claim);
        player.sendMessage(this.plugin.message("claim-show-success", "{name}", claim.name()));
        return true;
    }

    private boolean handleShowAuto(Player player, String[] args) {
        boolean enabled;
        PlayerProfile profile = this.profileService.getOrCreate(player.getUniqueId(), player.getName());
        if (args.length == 2) {
            player.sendMessage(this.plugin.message("show-auto-status", "{value}", profile.autoShowBorders() ? "\u5f00\u542f" : "\u5173\u95ed"));
            return true;
        }
        String mode = args[2].toLowerCase(Locale.ROOT);
        if (mode.equals("on") || mode.equals("enable")) {
            enabled = true;
        } else if (mode.equals("off") || mode.equals("disable")) {
            enabled = false;
        } else {
            player.sendMessage(this.plugin.message("show-auto-usage"));
            return true;
        }
        profile.setAutoShowBorders(enabled);
        this.profileService.saveProfile(profile);
        player.sendMessage(this.plugin.message(enabled ? "show-auto-enabled" : "show-auto-disabled"));
        return true;
    }

    private boolean handleCreate(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(this.plugin.message("player-only"));
            return true;
        }
        Player player = (Player)sender;
        if (args.length < 2) {
            player.sendMessage(this.plugin.color(this.plugin.messagesConfig().getString("prefix", "&8[&6Claim&8] &f") + "&c\u7528\u6cd5: &7/claim create <\u9886\u5730\u540d\u5b57>"));
            return true;
        }
        String name = String.join((CharSequence)" ", Arrays.copyOfRange(args, 1, args.length)).trim();
        ClaimSelectionService.SelectionPreview preview = this.claimSelectionService.preview(player);
        if (preview == null || !preview.ready()) {
            player.sendMessage(this.plugin.color(this.plugin.messagesConfig().getString("prefix", "&8[&6Claim&8] &f") + "&c\u8bf7\u5148\u7528\u5708\u5730\u5de5\u5177\u9009\u597d\u4e24\u4e2a\u5bf9\u89d2\u70b9\u3002"));
            return true;
        }
        if (!preview.allowed()) {
            player.sendMessage(preview.failureMessage());
            return true;
        }
        if (name.isEmpty()) {
            player.sendMessage(this.plugin.message("claim-name-empty"));
            return true;
        }
        if (name.length() > this.plugin.settings().claimNameMaxLength()) {
            player.sendMessage(this.plugin.message("claim-name-too-long", "{max}", String.valueOf(this.plugin.settings().claimNameMaxLength())));
            return true;
        }
        if (this.claimService.isClaimNameTaken(name)) {
            player.sendMessage(this.plugin.message("claim-name-exists", "{name}", name));
            return true;
        }
        this.menuService.openSelectionCreateMenu(player, name, preview);
        return true;
    }

    private boolean handleTeleport(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(this.plugin.message("player-only"));
            return true;
        }
        Player player = (Player)sender;
        if (args.length < 2) {
            player.sendMessage(this.plugin.message("teleport-usage"));
            return true;
        }
        Claim claim = this.resolveTeleportClaimByName(player, this.joinArgs(args, 1));
        if (claim == null) {
            return true;
        }
        this.claimActionService.teleportToClaim(player, claim);
        return true;
    }

    private boolean handleTpSet(CommandSender sender) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(this.plugin.message("player-only"));
            return true;
        }
        Player player = (Player)sender;
        if (!this.hasManageTeleportPermission((CommandSender)player)) {
            player.sendMessage(this.plugin.message("no-permission"));
            return true;
        }
        Claim claim = this.resolveCurrentEditableClaim(player, "/claim tpset");
        if (claim == null) {
            return true;
        }
        if (!this.claimService.isLocalClaim(claim)) {
            player.sendMessage(this.chatMessage("tpset-cross-server-denied", "&c&l! &7\u8bf7\u5728\u9886\u5730\u6240\u5c5e\u533a\u670d\u5185\u8bbe\u7f6e\u4f20\u9001\u70b9\u3002", new String[0]));
            return true;
        }
        this.claimService.updateTeleportPoint(claim, player.getLocation(), player.getUniqueId());
        player.sendMessage(this.chatMessage("claim-tpset-success", "&a&l\u4f20\u9001\u70b9: &7\u5df2\u5c06\u9886\u5730 &e{name} &7\u7684\u4f20\u9001\u70b9\u66f4\u65b0\u5230\u4f60\u5f53\u524d\u811a\u4e0b\u4f4d\u7f6e\u3002", "{name}", claim.name()));
        return true;
    }

    private boolean handleRemoveClaim(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(this.plugin.message("player-only"));
            return true;
        }
        Player player = (Player)sender;
        if (args.length < 2) {
            player.sendMessage(this.plugin.message("remove-usage"));
            return true;
        }
        Claim claim = this.resolveOwnedClaimByName(player, this.joinArgs(args, 1));
        if (claim == null) {
            return true;
        }
        this.removalConfirmationService.request(player, claim);
        return true;
    }

    private boolean handleUnadd(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(this.plugin.message("player-only"));
            return true;
        }
        Player player = (Player)sender;
        if (args.length != 2) {
            player.sendMessage(this.plugin.message("unadd-usage"));
            return true;
        }
        Claim claim = this.claimActionService.findCurrentPresenceClaim(player);
        if (claim == null) {
            player.sendMessage(this.plugin.color("&c\u6d63\u72b2\u7e40\u6924\u8364\u73ef\u9366\u3124\u7af4\u9367\u6940\u5f72\u7f02\u682c\u7deb\u9428\u52ef\ue56b\u9366\u677f\u5534\u93b5\u5d88\u5158\u6d63\u8de8\u6564 /claim unadd"));
            return true;
        }
        if (!this.claimActionService.canEditClaim(player, claim)) {
            player.sendMessage(this.plugin.message("trust-no-permission"));
            return true;
        }
        OfflinePlayer target = this.resolveKnownPlayer(args[1]);
        if (target == null) {
            player.sendMessage(this.plugin.message("trust-no-target"));
            return true;
        }
        this.claimActionService.untrustPlayer(player, claim, target);
        return true;
    }

    private boolean handleConfirm(CommandSender sender) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(this.plugin.message("player-only"));
            return true;
        }
        Player player = (Player)sender;
        if (!this.removalConfirmationService.confirm(player)) {
            player.sendMessage(this.plugin.message("confirm-nothing"));
        }
        return true;
    }

    private boolean handleAdd(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(this.plugin.message("player-only"));
            return true;
        }
        Player player = (Player)sender;
        if (args.length != 2) {
            player.sendMessage(this.plugin.color("&c\u7528\u6cd5: &7/claim add <\u73a9\u5bb6>"));
            return true;
        }
        Claim claim = this.claimActionService.findCurrentPresenceClaim(player);
        if (claim == null) {
            player.sendMessage(this.plugin.color("&c\u4f60\u5fc5\u987b\u7ad9\u5728\u4e00\u5757\u53ef\u7f16\u8f91\u7684\u9886\u5730\u5185\u624d\u80fd\u4f7f\u7528 /claim add"));
            return true;
        }
        if (!this.claimActionService.canEditClaim(player, claim)) {
            player.sendMessage(this.plugin.message("trust-no-permission"));
            return true;
        }
        OfflinePlayer target = this.resolveKnownPlayer(args[1]);
        if (target == null) {
            player.sendMessage(this.plugin.message("trust-no-target"));
            return true;
        }
        this.claimActionService.trustPlayer(player, claim, target);
        return true;
    }

    private boolean handleDeny(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(this.plugin.message("player-only"));
            return true;
        }
        Player player = (Player)sender;
        if (!this.hasManageDenyPermission((CommandSender)player)) {
            player.sendMessage(this.plugin.message("no-permission"));
            return true;
        }
        if (args.length != 2) {
            player.sendMessage(this.chatMessage("deny-usage", "&c\u7528\u6cd5: &7/claim deny <\u73a9\u5bb6> &8\u6216 &7/claim deny *", new String[0]));
            return true;
        }
        Claim claim = this.resolveCurrentEditableClaim(player, "/claim deny");
        if (claim == null) {
            return true;
        }
        String targetName = args[1].trim();
        if ("*".equals(targetName)) {
            if (claim.denyAll()) {
                player.sendMessage(this.chatMessage("claim-deny-all-already-enabled", "&e&l\u5c01\u95ed\u6a21\u5f0f: &7\u8fd9\u5757\u9886\u5730\u5df2\u7ecf\u5f00\u542f deny *\u3002", new String[0]));
                return true;
            }
            this.claimService.updateDenyAll(claim, true, player.getUniqueId());
            player.sendMessage(this.chatMessage("claim-deny-all-enabled", "&a&l\u5c01\u95ed\u6a21\u5f0f: &7\u5df2\u4e3a\u9886\u5730 &e{name} &7\u5f00\u542f deny *\u3002", "{name}", claim.name()));
            return true;
        }
        OfflinePlayer target = this.resolveKnownPlayer(targetName);
        if (target == null || target.getUniqueId() == null) {
            player.sendMessage(this.plugin.message("trust-no-target"));
            return true;
        }
        if (target.getUniqueId().equals(claim.owner())) {
            player.sendMessage(this.plugin.message("trust-self"));
            return true;
        }
        if (!this.claimService.addDeniedMember(claim, target.getUniqueId(), player.getUniqueId())) {
            player.sendMessage(this.chatMessage("claim-deny-exists", "&e{player} &7\u5df2\u7ecf\u5728\u9886\u5730 &e{name} &7\u7684 deny \u5217\u8868\u91cc\u4e86\u3002", "{player}", this.displayName(target), "{name}", claim.name()));
            return true;
        }
        player.sendMessage(this.chatMessage("claim-deny-added", "&a&lDeny: &7\u5df2\u5c06\u73a9\u5bb6 &e{player} &7\u52a0\u5165\u9886\u5730 &e{name} &7\u7684 deny \u5217\u8868\u3002", "{player}", this.displayName(target), "{name}", claim.name()));
        return true;
    }

    private boolean handleUndeny(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(this.plugin.message("player-only"));
            return true;
        }
        Player player = (Player)sender;
        if (!this.hasManageDenyPermission((CommandSender)player)) {
            player.sendMessage(this.plugin.message("no-permission"));
            return true;
        }
        if (args.length != 2) {
            player.sendMessage(this.chatMessage("undeny-usage", "&c\u7528\u6cd5: &7/claim undeny <\u73a9\u5bb6> &8\u6216 &7/claim undeny *", new String[0]));
            return true;
        }
        Claim claim = this.resolveCurrentEditableClaim(player, "/claim undeny");
        if (claim == null) {
            return true;
        }
        String targetName = args[1].trim();
        if ("*".equals(targetName)) {
            if (!claim.denyAll()) {
                player.sendMessage(this.chatMessage("claim-deny-all-already-disabled", "&e&l\u5c01\u95ed\u6a21\u5f0f: &7\u8fd9\u5757\u9886\u5730\u5f53\u524d\u6ca1\u6709\u5f00\u542f deny *\u3002", new String[0]));
                return true;
            }
            this.claimService.updateDenyAll(claim, false, player.getUniqueId());
            player.sendMessage(this.chatMessage("claim-deny-all-disabled", "&a&l\u5c01\u95ed\u6a21\u5f0f: &7\u5df2\u4e3a\u9886\u5730 &e{name} &7\u5173\u95ed deny *\u3002", "{name}", claim.name()));
            return true;
        }
        OfflinePlayer target = this.resolveKnownPlayer(targetName);
        if (target == null || target.getUniqueId() == null) {
            player.sendMessage(this.plugin.message("trust-no-target"));
            return true;
        }
        if (!this.claimService.removeDeniedMember(claim, target.getUniqueId(), player.getUniqueId())) {
            player.sendMessage(this.chatMessage("claim-deny-missing", "&e{player} &7\u4e0d\u5728\u9886\u5730 &e{name} &7\u7684 deny \u5217\u8868\u4e2d\u3002", "{player}", this.displayName(target), "{name}", claim.name()));
            return true;
        }
        player.sendMessage(this.chatMessage("claim-deny-removed", "&a&lUndeny: &7\u5df2\u5c06\u73a9\u5bb6 &e{player} &7\u4ece\u9886\u5730 &e{name} &7\u7684 deny \u5217\u8868\u79fb\u9664\u3002", "{player}", this.displayName(target), "{name}", claim.name()));
        return true;
    }

    private boolean handleFlag(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(this.plugin.message("player-only"));
            return true;
        }
        Player player = (Player)sender;
        if (!player.hasPermission("coreclaim.manage.flags") && !this.hasAnyAdminPermission((CommandSender)player)) {
            player.sendMessage(this.plugin.message("no-permission"));
            return true;
        }
        Claim claim = this.resolveCurrentEditableClaim(player, "/claim flag");
        if (claim == null) {
            return true;
        }
        if (args.length == 1 || args.length == 2 && this.isFlagListInput(args[1])) {
            this.sendFlagSummary((CommandSender)player, claim);
            return true;
        }
        if (args.length < 3) {
            player.sendMessage(this.chatMessage("flag-usage", "&c\u7528\u6cd5: &7/claim flag <flag> <allow|deny|unset>", new String[0]));
            return true;
        }
        ClaimFlag flag = ClaimFlag.fromKey(args[1]);
        if (flag == null) {
            player.sendMessage(this.chatMessage("flag-invalid", "&c\u672a\u77e5\u65d7\u6807: &e{flag}", "{flag}", args[1]));
            return true;
        }
        ClaimFlagState state = ClaimFlagState.fromInput(args[2]);
        if (state == null) {
            player.sendMessage(this.chatMessage("flag-state-invalid", "&c\u72b6\u6001\u53ea\u80fd\u662f &eallow &7/ &edeny &7/ &eunset", new String[0]));
            return true;
        }
        this.claimService.updateFlagState(claim, flag, state, player.getUniqueId());
        player.sendMessage(this.chatMessage("flag-updated", "&a\u5df2\u5c06\u9886\u5730 &e{name} &7\u7684\u65d7\u6807 &b{flag} &7\u8bbe\u7f6e\u4e3a {state}&7\u3002", "{name}", claim.name(), "{flag}", flag.key(), "{state}", this.flagStateText(state)));
        return true;
    }

    private boolean handleAdmin(CommandSender sender, String[] args) {
        String action;
        if (!this.hasAnyAdminPermission(sender)) {
            sender.sendMessage(this.plugin.message("no-permission"));
            return true;
        }
        if (args.length < 2) {
            sender.sendMessage(this.plugin.message("admin-usage"));
            return true;
        }
        return switch (action = args[1].toLowerCase(Locale.ROOT)) {
            case "create" -> this.handleAdminCreate(sender, args);
            case "info" -> this.handleAdminInfo(sender, args);
            case "playerclaims" -> this.handleAdminPlayerClaims(sender, args);
            case "diagnose" -> this.handleAdminDiagnose(sender, args);
            case "add" -> this.handleAdminAdd(sender, args);
            case "remove" -> this.handleAdminRemove(sender, args);
            case "permission" -> this.handleAdminPermission(sender, args);
            case "flag" -> this.handleAdminFlag(sender, args);
            case "deny" -> this.handleAdminDeny(sender, args);
            case "undeny" -> this.handleAdminUndeny(sender, args);
            case "unadd" -> this.handleAdminUnadd(sender, args);
            case "cleanup" -> this.handleAdminCleanup(sender, args);
            case "setserver" -> this.handleAdminSetServer(sender, args);
            default -> {
                sender.sendMessage(this.plugin.message("admin-usage"));
                yield true;
            }
        };
    }

    private boolean handleAdminCreate(CommandSender sender, String[] args) {
        if (!this.hasAdminCreateSystemPermission(sender)) {
            sender.sendMessage(this.plugin.message("no-permission"));
            return true;
        }
        if (!(sender instanceof Player)) {
            sender.sendMessage(this.plugin.message("player-only"));
            return true;
        }
        Player player = (Player)sender;
        if (args.length < 4 || !args[2].equalsIgnoreCase("system")) {
            sender.sendMessage(this.chatMessage("admin-create-system-usage", "&c\u9422\u3126\u7876: &7/claim admin create system <\u68f0\u55d7\u6e74\u935a?", new String[0]));
            return true;
        }
        String claimName = this.joinArgs(args, 3);
        if (claimName.isBlank()) {
            sender.sendMessage(this.chatMessage("admin-create-system-usage", "&c\u9422\u3126\u7876: &7/claim admin create system <\u68f0\u55d7\u6e74\u935a?", new String[0]));
            return true;
        }
        return this.claimSelectionService.createSystemClaim(player, claimName);
    }

    private boolean handleAdminInfo(CommandSender sender, String[] args) {
        if (!this.hasAdminViewPermission(sender)) {
            sender.sendMessage(this.plugin.message("no-permission"));
            return true;
        }
        if (args.length < 3) {
            sender.sendMessage(this.plugin.message("admin-info-usage"));
            return true;
        }
        Claim claim = this.resolveAdminClaimSelector(sender, this.joinArgs(args, 2));
        if (claim == null) {
            return true;
        }
        this.sendEnhancedClaimDetails(sender, claim, true);
        return true;
    }

    private boolean handleAdminPlayerClaims(CommandSender sender, String[] args) {
        if (!this.hasAdminViewPermission(sender)) {
            sender.sendMessage(this.plugin.message("no-permission"));
            return true;
        }
        if (args.length < 3) {
            sender.sendMessage(this.chatMessage("admin-playerclaims-usage", "&c\u7528\u6cd5: &7/claim admin playerclaims <\u73a9\u5bb6>", new String[0]));
            return true;
        }
        OfflinePlayer target = this.resolveKnownPlayer(this.joinArgs(args, 2));
        if (target == null || target.getUniqueId() == null) {
            sender.sendMessage(this.plugin.message("trust-no-target"));
            return true;
        }
        List<Claim> claims = this.claimService.claimsOfFresh(target.getUniqueId(), true);
        if (claims.isEmpty()) {
            sender.sendMessage(this.chatMessage("admin-playerclaims-empty", "&e{player} &7\u5f53\u524d\u6ca1\u6709\u4efb\u4f55\u9886\u5730\u3002", "{player}", this.displayName(target)));
            return true;
        }
        sender.sendMessage(this.plugin.color("&6[Claim] &f\u73a9\u5bb6 &e" + this.displayName(target) + " &f\u540d\u4e0b\u9886\u5730:"));
        for (Claim claim : claims) {
            sender.sendMessage(this.plugin.color("&7- &f#" + claim.id() + " &e" + (claim.systemManaged() ? "[SYSTEM] " : "") + claim.name() + " &8@ &b" + this.claimService.displayServerId(claim) + " &7[" + claim.world() + " " + claim.centerX() + ", " + claim.centerZ() + "]"));
        }
        return true;
    }

    private boolean handleAdminDiagnose(CommandSender sender, String[] args) {
        boolean worldLoaded;
        if (!this.hasAdminViewPermission(sender)) {
            sender.sendMessage(this.plugin.message("no-permission"));
            return true;
        }
        if (args.length < 3) {
            sender.sendMessage(this.chatMessage("admin-diagnose-usage", "&c\u7528\u6cd5: &7/claim admin diagnose <\u9886\u5730\u540d|#claimId>", new String[0]));
            return true;
        }
        Claim claim = this.resolveAdminClaimSelector(sender, this.joinArgs(args, 2));
        if (claim == null) {
            return true;
        }
        boolean localClaim = this.claimService.isLocalClaim(claim);
        boolean bl = worldLoaded = localClaim && Bukkit.getWorld((String)claim.world()) != null;
        String route = localClaim ? (worldLoaded ? "local-teleport" : "local-world-missing") : (this.plugin.settings().crossServerTeleportEnabled() ? "cross-server-teleport" : "cross-server-disabled");
        sender.sendMessage(this.plugin.color("&6[Claim] &f\u8bca\u65ad\u76ee\u6807: &e" + (claim.systemManaged() ? "[SYSTEM] " : "") + claim.name() + " &7(#" + claim.id() + ")"));
        sender.sendMessage(this.plugin.color("&6[Claim] &fOwner: &b" + claim.ownerName() + " &8| &fServer ID: &e" + this.claimService.displayServerId(claim)));
        sender.sendMessage(this.plugin.color("&6[Claim] &f\u5f53\u524d\u670d server-id: &e" + this.plugin.settings().serverId() + " &8| &f\u662f\u5426\u672c\u670d: " + (localClaim ? "&a\u662f" : "&c\u5426")));
        sender.sendMessage(this.plugin.color("&6[Claim] &f\u7cfb\u7edf\u9886\u5730: " + (claim.systemManaged() ? "&6\u662f&8| &f\u8ba1\u5165\u914d\u989d: &c\u5426" : "&7\u5426&8| &f\u8ba1\u5165\u914d\u989d: &a\u662f")));
        sender.sendMessage(this.plugin.color("&6[Claim] &f\u89c4\u5219\u6765\u6e90: " + this.ruleSourceSummary(claim)));
        sender.sendMessage(this.plugin.color("&6[Claim] &f\u4e16\u754c\u72b6\u6001: " + (worldLoaded ? "&a\u5df2\u52a0\u8f7d" : "&e\u672a\u52a0\u8f7d\u6216\u4e0d\u5728\u672c\u670d")));
        sender.sendMessage(this.plugin.color("&6[Claim] &fTP \u8def\u7531: &b" + route));
        sender.sendMessage(this.plugin.color("&6[Claim] &fdeny *: " + (claim.denyAll() ? "&c\u5f00\u542f" : "&a\u5173\u95ed") + " &8| &fDenied: &c" + claim.deniedMembers().size() + " &8| &fTrusted: &a" + claim.trustedCount()));
        sender.sendMessage(this.plugin.color("&6[Claim] &f\u4ea4\u4e92\u65d7\u6807: " + this.summarizeFlags(claim)));
        return true;
    }

    private boolean handleAdminPermission(CommandSender sender, String[] args) {
        String stateInput;
        String permissionInput;
        Claim claim;
        if (!this.hasAdminPermissionManagePermission(sender)) {
            sender.sendMessage(this.plugin.message("no-permission"));
            return true;
        }
        if (args.length == 4 && sender instanceof Player) {
            Player player = (Player)sender;
            claim = this.resolveCurrentAdminClaim(player, "/claim admin permission");
            if (claim == null) {
                return true;
            }
            permissionInput = args[2];
            stateInput = args[3];
        } else if (args.length >= 5) {
            claim = this.resolveAdminClaimSelector(sender, this.joinArgs(args, 2, args.length - 2));
            if (claim == null) {
                return true;
            }
            permissionInput = args[args.length - 2];
            stateInput = args[args.length - 1];
        } else {
            sender.sendMessage(this.chatMessage("admin-permission-usage", "&c\u7528\u6cd5: &7/claim admin permission <permission> <allow|deny>", new String[0]));
            return true;
        }
        ClaimPermission permission = this.parsePermission(permissionInput);
        if (permission == null) {
            sender.sendMessage(this.chatMessage("admin-permission-invalid", "&c\u672a\u77e5\u9ed8\u8ba4\u6743\u9650: &e{permission}", "{permission}", permissionInput));
            return true;
        }
        if (permission == ClaimPermission.CONTAINER) {
            sender.sendMessage(this.chatMessage("admin-permission-container-deprecated", "&eContainer access is now controlled by &7/claim admin flag container <allow|deny|unset>&e.", new String[0]));
            return true;
        }
        Boolean allowed = this.parseAllowDeny(stateInput);
        if (allowed == null) {
            sender.sendMessage(this.chatMessage("admin-permission-state-invalid", "&c\u72b6\u6001\u53ea\u80fd\u662f &eallow &7\u6216 &edeny", new String[0]));
            return true;
        }
        UUID actorId = this.actorId(sender);
        this.claimService.updatePermission(claim, permission, allowed, actorId);
        sender.sendMessage(this.chatMessage("admin-permission-updated", "&a\u5df2\u5c06\u9886\u5730 &e{name} &7\u7684\u9ed8\u8ba4\u6743\u9650 &b{permission} &7\u8bbe\u7f6e\u4e3a {state}&7\u3002", "{name}", claim.name(), "{permission}", permission.name().toLowerCase(Locale.ROOT), "{state}", this.stateText(allowed)));
        return true;
    }

    private boolean handleAdminFlag(CommandSender sender, String[] args) {
        String stateInput;
        String flagInput;
        Claim claim;
        if (!this.hasAdminFlagManagePermission(sender)) {
            sender.sendMessage(this.plugin.message("no-permission"));
            return true;
        }
        if (args.length == 4 && sender instanceof Player) {
            Player player = (Player)sender;
            claim = this.resolveCurrentAdminClaim(player, "/claim admin flag");
            if (claim == null) {
                return true;
            }
            flagInput = args[2];
            stateInput = args[3];
        } else if (args.length >= 5) {
            claim = this.resolveAdminClaimSelector(sender, this.joinArgs(args, 2, args.length - 2));
            if (claim == null) {
                return true;
            }
            flagInput = args[args.length - 2];
            stateInput = args[args.length - 1];
        } else {
            sender.sendMessage(this.chatMessage("admin-flag-usage", "&c\u7528\u6cd5: &7/claim admin flag <flag> <allow|deny|unset>", new String[0]));
            return true;
        }
        ClaimFlag flag = ClaimFlag.fromKey(flagInput);
        if (flag == null) {
            sender.sendMessage(this.chatMessage("admin-flag-invalid", "&c\u672a\u77e5\u65d7\u6807: &e{flag}", "{flag}", flagInput));
            return true;
        }
        ClaimFlagState state = ClaimFlagState.fromInput(stateInput);
        if (state == null) {
            sender.sendMessage(this.chatMessage("admin-flag-state-invalid", "&c\u72b6\u6001\u53ea\u80fd\u662f &eallow &7/ &edeny &7/ &eunset", new String[0]));
            return true;
        }
        UUID actorId = this.actorId(sender);
        this.claimService.updateFlagState(claim, flag, state, actorId);
        sender.sendMessage(this.chatMessage("admin-flag-updated", "&a\u5df2\u5c06\u9886\u5730 &e{name} &7\u7684\u65d7\u6807 &b{flag} &7\u8bbe\u7f6e\u4e3a {state}&7\u3002", "{name}", claim.name(), "{flag}", flag.key(), "{state}", this.flagStateText(state)));
        return true;
    }

    private boolean handleAdminDeny(CommandSender sender, String[] args) {
        String targetArg;
        Claim claim;
        if (!this.hasAdminMemberManagePermission(sender)) {
            sender.sendMessage(this.plugin.message("no-permission"));
            return true;
        }
        if (args.length == 3 && sender instanceof Player) {
            Player player = (Player)sender;
            claim = this.resolveCurrentAdminClaim(player, "/claim admin deny");
            if (claim == null) {
                return true;
            }
            targetArg = args[2];
        } else if (args.length >= 4) {
            claim = this.resolveAdminClaimSelector(sender, this.joinArgs(args, 2, args.length - 1));
            if (claim == null) {
                return true;
            }
            targetArg = args[args.length - 1];
        } else {
            sender.sendMessage(this.chatMessage("admin-deny-usage", "&c\u7528\u6cd5: &7/claim admin deny <\u73a9\u5bb6|*>", new String[0]));
            return true;
        }
        if ("*".equals(targetArg)) {
            UUID actorId = this.actorId(sender);
            this.claimService.updateDenyAll(claim, true, actorId);
            sender.sendMessage(this.chatMessage("admin-deny-all-enabled", "&a\u5df2\u4e3a\u9886\u5730 &e{name} &7\u5f00\u542f deny *\u3002", "{name}", claim.name()));
            return true;
        }
        OfflinePlayer target = this.resolveKnownPlayer(targetArg);
        if (target == null || target.getUniqueId() == null) {
            sender.sendMessage(this.plugin.message("trust-no-target"));
            return true;
        }
        UUID actorId = this.actorId(sender);
        if (!this.claimService.addDeniedMember(claim, target.getUniqueId(), actorId)) {
            sender.sendMessage(this.chatMessage("admin-deny-exists", "&e{player} &7\u5df2\u7ecf\u5728\u9886\u5730 &e{name} &7\u7684 deny \u5217\u8868\u4e2d\u3002", "{player}", this.displayName(target), "{name}", claim.name()));
            return true;
        }
        sender.sendMessage(this.chatMessage("admin-deny-added", "&a\u5df2\u5c06\u73a9\u5bb6 &e{player} &7\u52a0\u5165\u9886\u5730 &e{name} &7\u7684 deny \u5217\u8868\u3002", "{player}", this.displayName(target), "{name}", claim.name()));
        return true;
    }

    private boolean handleAdminUndeny(CommandSender sender, String[] args) {
        String targetArg;
        Claim claim;
        if (!this.hasAdminMemberManagePermission(sender)) {
            sender.sendMessage(this.plugin.message("no-permission"));
            return true;
        }
        if (args.length == 3 && sender instanceof Player) {
            Player player = (Player)sender;
            claim = this.resolveCurrentAdminClaim(player, "/claim admin undeny");
            if (claim == null) {
                return true;
            }
            targetArg = args[2];
        } else if (args.length >= 4) {
            claim = this.resolveAdminClaimSelector(sender, this.joinArgs(args, 2, args.length - 1));
            if (claim == null) {
                return true;
            }
            targetArg = args[args.length - 1];
        } else {
            sender.sendMessage(this.chatMessage("admin-undeny-usage", "&c\u7528\u6cd5: &7/claim admin undeny <\u73a9\u5bb6|*>", new String[0]));
            return true;
        }
        if ("*".equals(targetArg)) {
            UUID actorId = this.actorId(sender);
            this.claimService.updateDenyAll(claim, false, actorId);
            sender.sendMessage(this.chatMessage("admin-deny-all-disabled", "&a\u5df2\u4e3a\u9886\u5730 &e{name} &7\u5173\u95ed deny *\u3002", "{name}", claim.name()));
            return true;
        }
        OfflinePlayer target = this.resolveKnownPlayer(targetArg);
        if (target == null || target.getUniqueId() == null) {
            sender.sendMessage(this.plugin.message("trust-no-target"));
            return true;
        }
        UUID actorId = this.actorId(sender);
        if (!this.claimService.removeDeniedMember(claim, target.getUniqueId(), actorId)) {
            sender.sendMessage(this.chatMessage("admin-deny-missing", "&e{player} &7\u4e0d\u5728\u9886\u5730 &e{name} &7\u7684 deny \u5217\u8868\u4e2d\u3002", "{player}", this.displayName(target), "{name}", claim.name()));
            return true;
        }
        sender.sendMessage(this.chatMessage("admin-deny-removed", "&a\u5df2\u5c06\u73a9\u5bb6 &e{player} &7\u4ece\u9886\u5730 &e{name} &7\u7684 deny \u5217\u8868\u79fb\u9664\u3002", "{player}", this.displayName(target), "{name}", claim.name()));
        return true;
    }

    private boolean handleAdminAdd(CommandSender sender, String[] args) {
        if (!this.hasAdminMemberManagePermission(sender)) {
            sender.sendMessage(this.plugin.message("no-permission"));
            return true;
        }
        if (args.length < 3) {
            sender.sendMessage(this.chatMessage("admin-add-usage", "&c\u7528\u6cd5: &7/claim admin add <\u73a9\u5bb6>", new String[0]));
            return true;
        }
        if (!(sender instanceof Player)) {
            sender.sendMessage(this.plugin.message("player-only"));
            return true;
        }
        Player player = (Player)sender;
        Claim claim = this.resolveCurrentAdminClaim(player, "/claim admin add");
        if (claim == null) {
            return true;
        }
        OfflinePlayer target = this.resolveKnownPlayer(this.joinArgs(args, 2));
        if (target == null || target.getUniqueId() == null) {
            sender.sendMessage(this.plugin.message("trust-no-target"));
            return true;
        }
        if (!this.claimService.addTrustedMember(claim, target.getUniqueId(), player.getUniqueId())) {
            sender.sendMessage(this.chatMessage("admin-trust-exists", "&e{player} &7\u5df2\u7ecf\u662f\u9886\u5730 &e{name} &7\u7684\u6210\u5458\u3002", "{player}", this.displayName(target), "{name}", claim.name()));
            return true;
        }
        sender.sendMessage(this.chatMessage("admin-trust-added", "&a\u5df2\u5c06\u73a9\u5bb6 &e{player} &7\u52a0\u5165\u9886\u5730 &e{name} &7\u7684\u6210\u5458\u5217\u8868\u3002", "{player}", this.displayName(target), "{name}", claim.name()));
        return true;
    }

    private boolean handleAdminRemove(CommandSender sender, String[] args) {
        Claim claim;
        if (!this.hasAdminClaimManagePermission(sender)) {
            sender.sendMessage(this.plugin.message("no-permission"));
            return true;
        }
        if (args.length >= 3) {
            claim = this.resolveAdminClaimSelector(sender, this.joinArgs(args, 2));
        } else if (sender instanceof Player) {
            Player player = (Player)sender;
            claim = this.resolveCurrentAdminClaim(player, "/claim admin remove");
        } else {
            sender.sendMessage(this.chatMessage("admin-remove-usage", "&c\u7528\u6cd5: &7/claim admin remove [\u9886\u5730\u540d|#claimId]", new String[0]));
            return true;
        }
        if (claim == null) {
            return true;
        }
        if (sender instanceof Player) {
            Player player = (Player)sender;
            this.removalConfirmationService.requestAdminRemoval(player, claim);
            return true;
        }
        this.claimActionService.adminRemoveClaim(sender, claim);
        return true;
    }

    private boolean handleAdminUnadd(CommandSender sender, String[] args) {
        if (!this.hasAdminMemberManagePermission(sender)) {
            sender.sendMessage(this.plugin.message("no-permission"));
            return true;
        }
        if (args.length < 3) {
            sender.sendMessage(this.plugin.message("admin-unadd-usage"));
            return true;
        }
        if (!(sender instanceof Player)) {
            sender.sendMessage(this.plugin.message("player-only"));
            return true;
        }
        Player player = (Player)sender;
        Claim claim = this.resolveCurrentAdminClaim(player, "/claim admin unadd");
        if (claim == null) {
            return true;
        }
        OfflinePlayer target = this.resolveKnownPlayer(this.joinArgs(args, 2));
        if (target == null || target.getUniqueId() == null) {
            sender.sendMessage(this.plugin.message("trust-no-target"));
            return true;
        }
        if (!this.claimService.removeTrustedMember(claim, target.getUniqueId(), player.getUniqueId())) {
            sender.sendMessage(this.chatMessage("admin-untrust-missing", "&e{player} &7\u4e0d\u5728\u9886\u5730 &e{name} &7\u7684\u6210\u5458\u5217\u8868\u4e2d\u3002", "{player}", this.displayName(target), "{name}", claim.name()));
            return true;
        }
        sender.sendMessage(this.chatMessage("admin-untrust-removed", "&a\u5df2\u5c06\u73a9\u5bb6 &e{player} &7\u4ece\u9886\u5730 &e{name} &7\u7684\u6210\u5458\u5217\u8868\u79fb\u9664\u3002", "{player}", this.displayName(target), "{name}", claim.name()));
        return true;
    }

    private boolean handleAdminCleanup(CommandSender sender, String[] args) {
        if (!this.hasAdminClaimManagePermission(sender)) {
            sender.sendMessage(this.plugin.message("no-permission"));
            return true;
        }
        if (args.length < 3) {
            sender.sendMessage(this.chatMessage("admin-cleanup-usage", "&c\u7528\u6cd5: &7/claim admin cleanup <list|run|skip|baseline> ...", new String[0]));
            return true;
        }
        return switch (args[2].toLowerCase(Locale.ROOT)) {
            case "list" -> this.handleAdminCleanupList(sender);
            case "run" -> this.handleAdminCleanupRun(sender);
            case "skip" -> this.handleAdminCleanupSkip(sender, args);
            case "baseline" -> this.handleAdminCleanupBaseline(sender, args);
            default -> {
                sender.sendMessage(this.chatMessage("admin-cleanup-usage", "&c\u7528\u6cd5: &7/claim admin cleanup <list|run|skip|baseline> ...", new String[0]));
                yield true;
            }
        };
    }

    private boolean handleAdminCleanupList(CommandSender sender) {
        ClaimCleanupService.CleanupSnapshot snapshot = this.claimCleanupService.snapshot();
        if (!this.plugin.settings().inactiveClaimCleanupEnabled()) {
            sender.sendMessage(this.chatMessage("admin-cleanup-disabled", "&e\u81ea\u52a8\u7a7a\u5730\u6e05\u7406\u5f53\u524d\u5904\u4e8e\u5173\u95ed\u72b6\u6001\uff0c\u4e0b\u9762\u663e\u793a\u7684\u662f\u624b\u52a8\u626b\u63cf\u89c6\u56fe\u3002", new String[0]));
        }
        sender.sendMessage(this.chatMessage("admin-cleanup-list-header", "&6\u7a7a\u5730\u6e05\u7406: &7\u5019\u9009 &e{candidates} &7| \u5bbd\u9650\u4e2d &e{grace} &7| \u65e7\u5730\u5f85\u57fa\u7ebf &e{legacy}", "{candidates}", String.valueOf(snapshot.candidates().size()), "{grace}", String.valueOf(snapshot.graceClaims().size()), "{legacy}", String.valueOf(snapshot.legacyClaims().size())));
        if (snapshot.candidates().isEmpty() && snapshot.graceClaims().isEmpty() && snapshot.legacyClaims().isEmpty()) {
            sender.sendMessage(this.chatMessage("admin-cleanup-list-empty", "&7\u5f53\u524d\u6ca1\u6709\u4efb\u4f55\u7a7a\u5730\u6e05\u7406\u5019\u9009\u3002", new String[0]));
            return true;
        }
        this.sendCleanupEntries(sender, "\u5019\u9009\u5220\u9664", snapshot.candidates(), false);
        this.sendCleanupEntries(sender, "\u5bbd\u9650\u4e2d", snapshot.graceClaims(), true);
        this.sendCleanupEntries(sender, "\u65e7\u5730\u5f85\u57fa\u7ebf", snapshot.legacyClaims(), false);
        return true;
    }

    private boolean handleAdminCleanupRun(CommandSender sender) {
        ClaimCleanupService.CleanupRunResult result = this.claimCleanupService.runScanNow();
        sender.sendMessage(this.chatMessage("admin-cleanup-run-result", "&a\u7a7a\u5730\u6e05\u7406: &7\u672c\u6b21\u626b\u63cf &e{scanned} &7\u5757\uff0c\u672c\u6b21\u6253\u6807 &e{marked} &7\u5757\uff0c\u5220\u9664 &e{deleted} &7\u5757\uff0c\u64a4\u9500\u5bbd\u9650 &e{revoked} &7\u5757\u3002\u5f53\u524d\u5019\u9009 &e{candidates} &7\u5757\uff0c\u5bbd\u9650\u4e2d &e{grace} &7\u5757\u3002", "{scanned}", String.valueOf(result.scannedClaims()), "{marked}", String.valueOf(result.markedGraceClaims()), "{deleted}", String.valueOf(result.deletedClaims()), "{revoked}", String.valueOf(result.revokedGraceClaims()), "{candidates}", String.valueOf(result.candidates()), "{grace}", String.valueOf(result.graceClaims())));
        return true;
    }

    private boolean handleAdminCleanupSkip(CommandSender sender, String[] args) {
        if (args.length < 4) {
            sender.sendMessage(this.chatMessage("admin-cleanup-skip-usage", "&c\u7528\u6cd5: &7/claim admin cleanup skip <\u9886\u5730\u540d|#claimId>", new String[0]));
            return true;
        }
        Claim claim = this.resolveAdminClaimSelector(sender, this.joinArgs(args, 3));
        if (claim == null) {
            return true;
        }
        this.claimCleanupService.skipClaim(claim);
        sender.sendMessage(this.chatMessage("admin-cleanup-skip-success", "&a\u7a7a\u5730\u6e05\u7406: &7\u5df2\u5c06\u9886\u5730 &e{name} &7\u6807\u8bb0\u4e3a\u6c38\u4e45\u8df3\u8fc7\u81ea\u52a8\u6e05\u7406\u3002", "{name}", claim.name()));
        return true;
    }

    private boolean handleAdminCleanupBaseline(CommandSender sender, String[] args) {
        if (args.length < 5) {
            sender.sendMessage(this.chatMessage("admin-cleanup-baseline-usage", "&c\u7528\u6cd5: &7/claim admin cleanup baseline <\u9886\u5730\u540d|#claimId> <empty|used|skip>", new String[0]));
            return true;
        }
        ClaimCleanupService.BaselineMode mode = ClaimCleanupService.BaselineMode.fromInput(args[args.length - 1]);
        if (mode == null) {
            sender.sendMessage(this.chatMessage("admin-cleanup-baseline-usage", "&c\u7528\u6cd5: &7/claim admin cleanup baseline <\u9886\u5730\u540d|#claimId> <empty|used|skip>", new String[0]));
            return true;
        }
        Claim claim = this.resolveAdminClaimSelector(sender, this.joinArgs(args, 3, args.length - 1));
        if (claim == null) {
            return true;
        }
        this.claimCleanupService.baselineClaim(claim, mode);
        sender.sendMessage(this.chatMessage("admin-cleanup-baseline-success", "&a\u7a7a\u5730\u6e05\u7406: &7\u5df2\u5c06\u9886\u5730 &e{name} &7\u57fa\u7ebf\u8bbe\u4e3a &b{mode}&7\u3002", "{name}", claim.name(), "{mode}", this.cleanupBaselineModeText(mode)));
        return true;
    }

    private boolean handleAdminSetServer(CommandSender sender, String[] args) {
        String targetServerId;
        if (!this.hasAdminClaimManagePermission(sender)) {
            sender.sendMessage(this.plugin.message("no-permission"));
            return true;
        }
        if (args.length < 4) {
            sender.sendMessage(this.chatMessage("admin-setserver-usage", "&c\u7528\u6cd5: &7/claim admin setserver <claimId> <serverId>", new String[0]));
            return true;
        }
        int claimId = this.parseClaimId(args[2], sender);
        if (claimId <= 0) {
            return true;
        }
        String string = targetServerId = args[3] == null ? "" : args[3].trim();
        if (targetServerId.isEmpty()) {
            sender.sendMessage(this.chatMessage("admin-setserver-usage", "&c\u7528\u6cd5: &7/claim admin setserver <claimId> <serverId>", new String[0]));
            return true;
        }
        Claim claim = this.claimService.updateClaimServerId(claimId, targetServerId).orElse(null);
        if (claim == null) {
            sender.sendMessage(this.chatMessage("claim-server-update-failed", "&c&l\u5931\u8d25: &7\u627e\u4e0d\u5230 ID \u4e3a &e{id} &7\u7684\u9886\u5730\uff0c\u6216 server_id \u66f4\u65b0\u5931\u8d25\u3002", "{id}", String.valueOf(claimId)));
            return true;
        }
        sender.sendMessage(this.chatMessage("claim-server-updated", "&a&l\u5df2\u4fee\u590d: &7\u5df2\u5c06\u9886\u5730 &b{name} &7(#&f{id}&7) \u7684 server_id \u66f4\u65b0\u4e3a &e{server}&7\u3002", "{id}", String.valueOf(claim.id()), "{name}", claim.name(), "{server}", this.claimService.displayServerId(claim)));
        return true;
    }

    private boolean handleEdit(CommandSender sender, String[] args) {
        Claim claim;
        if (!(sender instanceof Player)) {
            sender.sendMessage(this.plugin.message("player-only"));
            return true;
        }
        Player player = (Player)sender;
        if (!this.hasAdminClaimManagePermission((CommandSender)player)) {
            player.sendMessage(this.plugin.message("no-permission"));
            return true;
        }
        if (args.length >= 2) {
            claim = this.resolveAdminClaimByName((CommandSender)player, this.joinArgs(args, 1));
            if (claim == null) {
                return true;
            }
        } else {
            claim = this.claimService.findClaim(player.getLocation()).orElse(null);
            if (claim == null) {
                player.sendMessage(this.plugin.message("claim-not-found"));
                return true;
            }
        }
        this.menuService.openCoreMenu(player, claim);
        player.sendMessage(this.chatMessage("admin-edit-opened", "&a\u7ba1\u7406\u5458\u6a21\u5f0f: &7\u5df2\u6253\u5f00\u9886\u5730 &e{name} &7\u7684\u7f16\u8f91\u83dc\u5355\u3002", "{name}", claim.name()));
        return true;
    }

    private boolean handleTransfer(CommandSender sender, String[] args) {
        String targetName;
        Claim claim;
        if (!(sender instanceof Player)) {
            sender.sendMessage(this.plugin.message("player-only"));
            return true;
        }
        Player player = (Player)sender;
        if (!player.hasPermission("coreclaim.transfer")) {
            player.sendMessage(this.plugin.message("no-permission"));
            return true;
        }
        if (args.length < 2) {
            player.sendMessage(this.plugin.message("transfer-usage"));
            return true;
        }
        if (args.length == 2 && args[1].equalsIgnoreCase("accept")) {
            this.claimTransferService.accept(player);
            return true;
        }
        if (args.length == 2 && (args[1].equalsIgnoreCase("deny") || args[1].equalsIgnoreCase("cancel"))) {
            this.claimTransferService.deny(player);
            return true;
        }
        if (args.length == 2) {
            claim = this.claimActionService.findOwnedClaim(player);
            if (claim == null) {
                player.sendMessage(this.plugin.message("claim-not-found"));
                return true;
            }
            targetName = args[1];
        } else {
            String claimName = this.joinArgs(args, 1, args.length - 1);
            if (claimName.isBlank()) {
                player.sendMessage(this.plugin.message("transfer-usage"));
                return true;
            }
            claim = this.resolveOwnedClaimByName(player, claimName);
            if (claim == null) {
                return true;
            }
            targetName = args[args.length - 1];
        }
        Player target = this.resolveOnlinePlayer(targetName);
        this.claimTransferService.requestTransfer(player, claim, target);
        return true;
    }

    private boolean handleActivity(CommandSender sender, String[] args) {
        if (!this.hasAdminActivityManagePermission(sender)) {
            sender.sendMessage(this.plugin.message("no-permission"));
            return true;
        }
        if (args.length < 3) {
            sender.sendMessage(this.plugin.message("activity-usage"));
            return true;
        }
        String action = args[1].toLowerCase(Locale.ROOT);
        OfflinePlayer target = this.resolveKnownPlayer(args[2]);
        if (target == null) {
            sender.sendMessage(this.plugin.message("activity-player-unknown"));
            return true;
        }
        String name = this.displayName(target);
        PlayerProfile profile = this.profileService.getOrCreate(target.getUniqueId(), name);
        switch (action) {
            case "get": {
                sender.sendMessage(this.plugin.color("&6[Claim] &f" + profile.lastKnownName() + " \u9428\u52ec\u693f\u74ba\u51a8\u5bb3\u6d93?&e" + profile.activityPoints()));
                break;
            }
            case "set": {
                if (args.length < 4) {
                    sender.sendMessage(this.plugin.message("activity-value-missing"));
                    return true;
                }
                int value = this.parsePositiveInt(args[3], sender);
                if (value < 0) {
                    return true;
                }
                profile.setActivityPoints(value);
                this.profileService.saveProfile(profile);
                sender.sendMessage(this.plugin.message("activity-set", "{player}", profile.lastKnownName(), "{value}", String.valueOf(value)));
                break;
            }
            case "add": {
                if (args.length < 4) {
                    sender.sendMessage(this.plugin.message("activity-value-missing"));
                    return true;
                }
                int value = this.parsePositiveInt(args[3], sender);
                if (value < 0) {
                    return true;
                }
                profile.setActivityPoints(profile.activityPoints() + value);
                this.profileService.saveProfile(profile);
                sender.sendMessage(this.plugin.message("activity-add", "{player}", profile.lastKnownName(), "{value}", String.valueOf(value)));
                break;
            }
            case "take": {
                if (args.length < 4) {
                    sender.sendMessage(this.plugin.message("activity-value-missing"));
                    return true;
                }
                int value = this.parsePositiveInt(args[3], sender);
                if (value < 0) {
                    return true;
                }
                profile.setActivityPoints(Math.max(0, profile.activityPoints() - value));
                this.profileService.saveProfile(profile);
                sender.sendMessage(this.plugin.message("activity-take", "{player}", profile.lastKnownName(), "{value}", String.valueOf(value)));
                break;
            }
            default: {
                sender.sendMessage(this.plugin.message("activity-usage"));
            }
        }
        return true;
    }

    private boolean handleReload(CommandSender sender) {
        if (!this.hasAdminOpsPermission(sender)) {
            sender.sendMessage(this.plugin.message("no-permission"));
            return true;
        }
        try {
            int claimCount = this.plugin.reloadPluginResources();
            sender.sendMessage(this.plugin.message("reload-success", "{claims}", String.valueOf(claimCount)));
        }
        catch (Exception exception) {
            sender.sendMessage(this.plugin.message("reload-failed", "{error}", exception.getMessage() == null ? exception.getClass().getSimpleName() : exception.getMessage()));
        }
        return true;
    }

    private boolean handleGiveCore(CommandSender sender, String[] args) {
        if (!this.hasAdminRewardPermission(sender)) {
            sender.sendMessage(this.plugin.message("no-permission"));
            return true;
        }
        if (args.length < 2) {
            sender.sendMessage(this.plugin.message("givecore-usage"));
            return true;
        }
        Player target = Bukkit.getPlayerExact((String)args[1]);
        if (target == null) {
            sender.sendMessage(this.plugin.message("target-must-online"));
            return true;
        }
        int amount = 1;
        if (args.length >= 3 && (amount = this.parsePositiveInt(args[2], sender)) < 0) {
            return true;
        }
        this.plugin.claimCoreFactory().giveClaimCore(target, amount);
        sender.sendMessage(this.plugin.message("claim-core-given", "{player}", target.getName(), "{amount}", String.valueOf(amount)));
        return true;
    }

    private int parsePositiveInt(String raw, CommandSender sender) {
        try {
            int value = Integer.parseInt(raw);
            if (value < 0) {
                sender.sendMessage(this.plugin.message("number-non-negative"));
                return -1;
            }
            return value;
        }
        catch (NumberFormatException exception) {
            sender.sendMessage(this.plugin.message("number-invalid"));
            return -1;
        }
    }

    private int parseClaimId(String raw, CommandSender sender) {
        try {
            int value = Integer.parseInt(raw);
            if (value <= 0) {
                sender.sendMessage(this.chatMessage("claim-id-invalid", "&c\u9886\u5730 ID &e{value} &7\u65e0\u6548\uff0c\u8bf7\u8f93\u5165\u5927\u4e8e 0 \u7684\u6570\u5b57\u3002", "{value}", raw));
                return -1;
            }
            return value;
        }
        catch (NumberFormatException exception) {
            sender.sendMessage(this.chatMessage("claim-id-invalid", "&c\u9886\u5730 ID &e{value} &7\u65e0\u6548\uff0c\u8bf7\u8f93\u5165\u5927\u4e8e 0 \u7684\u6570\u5b57\u3002", "{value}", raw));
            return -1;
        }
    }

    private double parsePositiveDouble(String raw, CommandSender sender) {
        try {
            double value = Double.parseDouble(raw);
            if (!Double.isFinite(value) || value <= 0.0) {
                sender.sendMessage(this.plugin.message("sale-price-invalid"));
                return -1.0;
            }
            return value;
        }
        catch (NumberFormatException exception) {
            sender.sendMessage(this.plugin.message("sale-price-invalid"));
            return -1.0;
        }
    }

    private String displayName(OfflinePlayer player) {
        return player.getName() == null ? player.getUniqueId().toString() : player.getName();
    }

    private Claim resolveCurrentAdminClaim(Player player, String usageLabel) {
        Claim claim = this.claimActionService.findCurrentPresenceClaim(player);
        if (claim == null) {
            player.sendMessage(this.plugin.color("&c\u6d63\u72b2\u7e40\u6924\u8364\u73ef\u9366\u3124\u7af4\u9367\u6940\u5f72\u7ee0\uff04\u608a\u9428\u52ef\ue56b\u9366\u677f\u5534\u93b5\u5d88\u5158\u6d63\u8de8\u6564 " + usageLabel));
            return null;
        }
        if (!this.claimActionService.canEditClaim(player, claim)) {
            player.sendMessage(this.plugin.message("trust-no-permission"));
            return null;
        }
        return claim;
    }

    private boolean hasAnyAdminPermission(CommandSender sender) {
        return AdminAccess.hasAnyAdminNode((Permissible)sender);
    }

    private boolean hasAdminViewPermission(CommandSender sender) {
        return AdminAccess.hasViewAccess((Permissible)sender);
    }

    private boolean hasAdminForcePermission(CommandSender sender) {
        return AdminAccess.hasForceBypass((Permissible)sender);
    }

    private boolean hasAdminOpsPermission(CommandSender sender) {
        return AdminAccess.hasOpsAccess((Permissible)sender);
    }

    private boolean hasAdminCreateSystemPermission(CommandSender sender) {
        return AdminAccess.hasCreateSystemAccess((Permissible)sender);
    }

    private boolean hasAdminMemberManagePermission(CommandSender sender) {
        return AdminAccess.hasMemberManageAccess((Permissible)sender);
    }

    private boolean hasAdminPermissionManagePermission(CommandSender sender) {
        return AdminAccess.hasPermissionManageAccess((Permissible)sender);
    }

    private boolean hasAdminFlagManagePermission(CommandSender sender) {
        return AdminAccess.hasFlagManageAccess((Permissible)sender);
    }

    private boolean hasAdminClaimManagePermission(CommandSender sender) {
        return AdminAccess.hasClaimManageAccess((Permissible)sender);
    }

    private boolean hasAdminActivityManagePermission(CommandSender sender) {
        return AdminAccess.hasActivityManageAccess((Permissible)sender);
    }

    private boolean hasAdminRewardPermission(CommandSender sender) {
        return AdminAccess.hasRewardAccess((Permissible)sender);
    }

    private boolean hasManageDenyPermission(CommandSender sender) {
        return sender.hasPermission("coreclaim.manage.deny") || this.hasAdminForcePermission(sender);
    }

    private boolean hasManageTeleportPermission(CommandSender sender) {
        return sender.hasPermission("coreclaim.manage.tpset") || this.hasAdminForcePermission(sender);
    }

    private Claim resolveCurrentEditableClaim(Player player, String usageLabel) {
        Claim claim = this.claimActionService.findCurrentPresenceClaim(player);
        if (claim == null) {
            player.sendMessage(this.plugin.color("&c\u6d63\u72b2\u7e40\u6924\u8364\u73ef\u9366\u3124\u7af4\u9367\u6940\u5f72\u7f02\u682c\u7deb\u9428\u52ef\ue56b\u9366\u677f\u5534\u93b5\u5d88\u5158\u6d63\u8de8\u6564 " + usageLabel));
            return null;
        }
        if (!this.claimActionService.canEditClaim(player, claim)) {
            player.sendMessage(this.plugin.message("trust-no-permission"));
            return null;
        }
        return claim;
    }

    private String formatTeleportPoint(Claim claim) {
        if (claim == null || !claim.hasTeleportPoint()) {
            return "-";
        }
        return this.trimDouble(claim.teleportX()) + ", " + this.trimDouble(claim.teleportY()) + ", " + this.trimDouble(claim.teleportZ());
    }

    private String ruleSourceSummary(Claim claim) {
        String profileName = this.claimService.ruleProfileName(claim);
        return this.claimService.hasManualRuleOverrides(claim) ? "&e" + profileName + " &8+ &6\u624b\u52a8\u8c03\u6574" : "&a" + profileName + " &7(\u9ed8\u8ba4\u751f\u6548)";
    }

    private String formatYawPitch(Float value) {
        return value == null ? "-" : this.trimDouble(Double.valueOf(value.floatValue()));
    }

    private String trimDouble(Double value) {
        if (value == null) {
            return "-";
        }
        double rounded = (double)Math.round(value * 100.0) / 100.0;
        if (Math.abs(rounded - Math.rint(rounded)) < 1.0E-5) {
            return String.valueOf((long)Math.rint(rounded));
        }
        return String.valueOf(rounded);
    }

    private void sendCleanupEntries(CommandSender sender, String title, List<ClaimCleanupService.CleanupEntry> entries, boolean showGrace) {
        if (entries.isEmpty()) {
            return;
        }
        sender.sendMessage(this.plugin.color("&6[Claim] &f" + title + " &8(" + entries.size() + ")"));
        for (ClaimCleanupService.CleanupEntry entry : entries) {
            sender.sendMessage(this.plugin.color("&7- &f#" + entry.claim().id() + " &e" + entry.claim().name() + " &8| &7\u4e3b\u4eba: &b" + entry.claim().ownerName() + " &8| &7\u6700\u540e\u4e0a\u7ebf: &e" + this.lastSeenText(entry.lastSeenAt()) + " &8| &7\u539f\u56e0: &c" + this.cleanupReasonText(entry.reason()) + (String)(showGrace ? " &8| &7\u5230\u671f: &6" + this.graceText(entry.state().getDeleteAfterAt()) : "")));
        }
    }

    private String cleanupReasonText(ClaimCleanupReason reason) {
        if (reason == null) {
            return "\u672a\u77e5";
        }
        return switch (reason) {
            default -> throw new IncompatibleClassChangeError();
            case NO_BUILD -> "\u65e0\u5efa\u7b51\u75d5\u8ff9";
            case NEVER_INTERACTED -> "\u4ece\u672a\u4ea4\u4e92";
            case NO_BUILD_AND_NEVER_INTERACTED -> "\u65e0\u5efa\u7b51\u4e14\u4ece\u672a\u4ea4\u4e92";
            case NONE -> "\u65e0";
        };
    }

    private String cleanupBaselineModeText(ClaimCleanupService.BaselineMode mode) {
        if (mode == null) {
            return "\u672a\u77e5";
        }
        return switch (mode) {
            default -> throw new IncompatibleClassChangeError();
            case EMPTY -> "empty(\u53ef\u8ffd\u8e2a\u7a7a\u5730)";
            case USED -> "used(\u5df2\u6709\u4f7f\u7528\u8bc1\u636e)";
            case SKIP -> "skip(\u6c38\u4e45\u8df3\u8fc7)";
        };
    }

    private String lastSeenText(long lastSeenAt) {
        if (lastSeenAt <= 0L) {
            return "\u65e0\u8bb0\u5f55";
        }
        long elapsedDays = Math.max(0L, (System.currentTimeMillis() - lastSeenAt) / 86400000L);
        return elapsedDays + "\u5929\u524d";
    }

    private String graceText(long deleteAfterAt) {
        if (deleteAfterAt <= 0L) {
            return "-";
        }
        long remainingDays = Math.max(0L, (deleteAfterAt - System.currentTimeMillis()) / 86400000L);
        return remainingDays + "\u5929\u540e";
    }

    private void sendEnhancedClaimDetails(CommandSender sender, Claim claim, boolean adminView) {
        boolean canSeeSensitive = adminView;
        if (!canSeeSensitive && sender instanceof Player) {
            Player player = (Player)sender;
            boolean bl = canSeeSensitive = claim.owner().equals(player.getUniqueId()) || this.hasAdminViewPermission((CommandSender)player);
        }
        if (adminView) {
            sender.sendMessage(this.plugin.color("&6[Claim] &fClaim ID: &e" + claim.id()));
            sender.sendMessage(this.plugin.color("&6[Claim] &fServer ID: &e" + this.claimService.displayServerId(claim)));
            sender.sendMessage(this.plugin.color("&6[Claim] &f\u7cfb\u7edf\u9886\u5730: " + (claim.systemManaged() ? "&6\u662f" : "&7\u5426")));
            sender.sendMessage(this.plugin.color("&6[Claim] &f\u8ba1\u5165\u914d\u989d: " + (this.claimService.countsTowardQuota(claim) ? "&a\u662f" : "&c\u5426")));
        }
        sender.sendMessage(this.plugin.color("&6[Claim] &f\u9886\u5730\u540d\u79f0: &e" + (claim.systemManaged() ? "[SYSTEM] " : "") + claim.name()));
        sender.sendMessage(this.plugin.color("&6[Claim] &f\u9886\u5730\u4e3b\u4eba: &b" + claim.ownerName()));
        sender.sendMessage(this.plugin.color("&6[Claim] &f\u6240\u5728\u4e16\u754c: &e" + claim.world()));
        sender.sendMessage(this.plugin.color("&6[Claim] &f\u6838\u5fc3\u5750\u6807: &f" + claim.centerX() + ", " + claim.centerY() + ", " + claim.centerZ()));
        sender.sendMessage(this.plugin.color("&6[Claim] &f\u9886\u5730\u5927\u5c0f: &e" + claim.width() + "x" + claim.depth() + " &7(\u9762\u79ef " + claim.area() + ")"));
        sender.sendMessage(this.plugin.color("&6[Claim] &f\u8fb9\u754c\u8303\u56f4: &7X " + claim.minX() + " ~ " + claim.maxX() + " &8| &7Z " + claim.minZ() + " ~ " + claim.maxZ()));
        sender.sendMessage(this.plugin.color("&6[Claim] &f\u9ad8\u5ea6\u6a21\u5f0f: " + (String)(claim.fullHeight() ? "&a\u5168\u9ad8\u5ea6\u4fdd\u62a4" : "&b\u9009\u533a\u9ad8\u5ea6 &7(Y " + claim.minY() + " ~ " + claim.maxY() + ", \u9ad8 " + claim.height() + ")")));
        sender.sendMessage(this.plugin.color("&6[Claim] &f\u4f20\u9001\u70b9: " + (String)(claim.hasTeleportPoint() ? "&a\u81ea\u5b9a\u4e49 &7(" + this.formatTeleportPoint(claim) + ")" : "&e\u6838\u5fc3")));
        sender.sendMessage(this.plugin.color("&6[Claim] &fDeny \u72b6\u6001: &c" + claim.deniedMembers().size() + " &7\u4eba &8| &fdeny *: " + (claim.denyAll() ? "&c\u5f00\u542f" : "&a\u5173\u95ed")));
        sender.sendMessage(this.plugin.color("&6[Claim] &f\u89c4\u5219\u6765\u6e90: " + this.ruleSourceSummary(claim)));
        sender.sendMessage(this.plugin.color("&6[Claim] &f\u9ed8\u8ba4\u6743\u9650: &7\u653e\u7f6e " + this.stateText(claim.permission(ClaimPermission.PLACE)) + " &8| &7\u7834\u574f " + this.stateText(claim.permission(ClaimPermission.BREAK)) + " &8| &7\u4ea4\u4e92 " + this.stateText(claim.permission(ClaimPermission.INTERACT)) + " &8| &7\u4f20\u9001 " + this.stateText(claim.permission(ClaimPermission.TELEPORT)) + " &8| &7\u98de\u884c " + this.stateText(claim.permission(ClaimPermission.FLIGHT))));
        sender.sendMessage(this.plugin.color("&6[Claim] &f\u4ea4\u4e92\u65d7\u6807: " + this.summarizeFlags(claim)));
        sender.sendMessage(this.plugin.color("&6[Claim] &f\u6838\u5fc3\u663e\u793a: " + (claim.coreVisible() ? "&a\u663e\u793a\u4e2d" : "&c\u5df2\u9690\u85cf")));
        sender.sendMessage(this.plugin.color("&6[Claim] &f\u8fdb\u5165\u63d0\u793a: &b" + this.previewMessage(claim.enterMessage(), claim, "\u9ed8\u8ba4\u8fdb\u5165\u63d0\u793a")));
        sender.sendMessage(this.plugin.color("&6[Claim] &f\u79bb\u5f00\u63d0\u793a: &e" + this.previewMessage(claim.leaveMessage(), claim, "\u9ed8\u8ba4\u79bb\u5f00\u63d0\u793a")));
        if (canSeeSensitive) {
            sender.sendMessage(this.plugin.color("&6[Claim] &f\u6210\u5458\u5217\u8868: " + this.joinPlayerNames(claim.trustedMembers())));
            sender.sendMessage(this.plugin.color("&6[Claim] &fDenied \u73a9\u5bb6: " + this.joinPlayerNames(claim.deniedMembers())));
        }
        if (adminView && claim.hasTeleportPoint()) {
            sender.sendMessage(this.plugin.color("&6[Claim] &fTP Yaw/Pitch: &e" + this.formatYawPitch(claim.teleportYaw()) + " &8/ &e" + this.formatYawPitch(claim.teleportPitch())));
        }
    }

    private void sendFlagSummary(CommandSender sender, Claim claim) {
        sender.sendMessage(this.plugin.color("&6[Claim] &f\u4ea4\u4e92\u65d7\u6807 - &e" + claim.name()));
        sender.sendMessage(this.plugin.color("&7- container: " + this.flagStateText(claim.flagState(ClaimFlag.CONTAINER))));
        sender.sendMessage(this.plugin.color("&7- use-button: " + this.flagStateText(claim.flagState(ClaimFlag.USE_BUTTON))));
        sender.sendMessage(this.plugin.color("&7- use-lever: " + this.flagStateText(claim.flagState(ClaimFlag.USE_LEVER))));
        sender.sendMessage(this.plugin.color("&7- use-pressure-plate: " + this.flagStateText(claim.flagState(ClaimFlag.USE_PRESSURE_PLATE))));
        sender.sendMessage(this.plugin.color("&7- use-door: " + this.flagStateText(claim.flagState(ClaimFlag.USE_DOOR))));
        sender.sendMessage(this.plugin.color("&7- use-trapdoor: " + this.flagStateText(claim.flagState(ClaimFlag.USE_TRAPDOOR))));
        sender.sendMessage(this.plugin.color("&7- use-fence-gate: " + this.flagStateText(claim.flagState(ClaimFlag.USE_FENCE_GATE))));
        sender.sendMessage(this.plugin.color("&7- use-bed: " + this.flagStateText(claim.flagState(ClaimFlag.USE_BED))));
    }

    private String summarizeFlags(Claim claim) {
        ArrayList<String> summary = new ArrayList<String>();
        for (ClaimFlag flag : ClaimFlag.values()) {
            ClaimFlagState state = claim.flagState(flag);
            if (state == ClaimFlagState.UNSET) continue;
            summary.add("&b" + flag.key() + "&7=" + this.flagStateText(state));
        }
        return summary.isEmpty() ? "&7\u672a\u8bbe\u7f6e" : String.join(this.plugin.color("&8, "), summary);
    }

    private String flagStateText(ClaimFlagState state) {
        return switch (state) {
            default -> throw new IncompatibleClassChangeError();
            case ALLOW -> "&a\u5141\u8bb8";
            case DENY -> "&c\u7981\u6b62";
            case UNSET -> "&7\u672a\u8bbe\u7f6e";
        };
    }

    private String previewMessage(String raw, Claim claim, String fallback) {
        return (raw == null || raw.isBlank() ? fallback : raw).replace("%claim_name%", claim.name()).replace("{claim_name}", claim.name()).replace("%owner%", claim.ownerName()).replace("{owner}", claim.ownerName()).replace("{name}", claim.name());
    }

    private String chatMessage(String path, String fallback, String ... replacements) {
        String prefix = this.plugin.messagesConfig().getString("prefix", "&6[CoreClaim] &f");
        String body = this.plugin.messagesConfig().getString(path, fallback);
        String message = this.plugin.color(prefix + body);
        int index = 0;
        while (index + 1 < replacements.length) {
            message = message.replace(replacements[index], replacements[index + 1]);
            index += 2;
        }
        return message;
    }

    private String stateText(boolean enabled) {
        return enabled ? "&a\u5141\u8bb8" : "&c\u7981\u6b62";
    }

    private String claimListRelationText(ClaimService.ClaimListRelation relation) {
        return relation == ClaimService.ClaimListRelation.OWNER ? "&a\u6211\u7684" : "&b\u5df2\u6388\u6743";
    }

    private UUID actorId(CommandSender sender) {
        UUID uUID;
        if (sender instanceof Player) {
            Player player = (Player)sender;
            uUID = player.getUniqueId();
        } else {
            uUID = null;
        }
        return uUID;
    }

    private String joinPlayerNames(Set<UUID> players) {
        if (players == null || players.isEmpty()) {
            return "&7\u65e0";
        }
        ArrayList<String> names = new ArrayList<String>();
        for (UUID playerId : players) {
            names.add("&e" + this.displayName(Bukkit.getOfflinePlayer((UUID)playerId)));
        }
        return String.join(this.plugin.color("&7, "), names);
    }

    private Claim resolveOwnedClaimByName(Player player, String rawName) {
        return this.resolveClaimByName((CommandSender)player, rawName, claim -> claim.owner().equals(player.getUniqueId()));
    }

    private Claim resolveAccessibleClaimByName(Player player, String rawName) {
        return this.resolveClaimByName((CommandSender)player, rawName, claim -> this.hasAdminForcePermission((CommandSender)player) || claim.owner().equals(player.getUniqueId()) || this.claimService.canAccess((Claim)claim, player.getUniqueId()));
    }

    private Claim resolveTeleportClaimByName(Player player, String rawName) {
        return this.resolveClaimByName((CommandSender)player, rawName, claim -> this.hasAdminForcePermission((CommandSender)player) || this.claimService.hasPermission((Claim)claim, player.getUniqueId(), ClaimPermission.TELEPORT));
    }

    private Claim resolveAdminClaimByName(CommandSender sender, String rawName) {
        return this.resolveClaimByName(sender, rawName, claim -> true);
    }

    private Claim resolveAdminClaimSelector(CommandSender sender, String rawSelector) {
        String numeric;
        String selector = this.normalizeQuery(rawSelector);
        if (selector == null) {
            sender.sendMessage(this.plugin.message("claim-not-found"));
            return null;
        }
        String string = numeric = selector.startsWith("#") ? selector.substring(1) : selector;
        if (numeric.chars().allMatch(Character::isDigit)) {
            int claimId = this.parseClaimId(numeric, sender);
            if (claimId <= 0) {
                return null;
            }
            Claim claim = this.claimService.findClaimByIdFresh(claimId).orElse(null);
            if (claim == null) {
                sender.sendMessage(this.plugin.message("claim-not-found"));
            }
            return claim;
        }
        return this.resolveAdminClaimByName(sender, selector);
    }

    private Claim resolveClaimByName(CommandSender sender, String rawName, Predicate<Claim> filter) {
        String claimName = this.normalizeQuery(rawName);
        if (claimName == null) {
            sender.sendMessage(this.plugin.message("claim-name-not-found", "{name}", rawName == null ? "" : rawName.trim()));
            return null;
        }
        List<Claim> matches = this.claimService.findClaimsByNameFresh(claimName).stream().filter(filter).toList();
        if (matches.isEmpty()) {
            sender.sendMessage(this.plugin.message("claim-name-not-found", "{name}", claimName));
            return null;
        }
        if (matches.size() > 1) {
            sender.sendMessage(this.plugin.message("claim-name-ambiguous", "{name}", claimName));
            for (Claim match : matches) {
                sender.sendMessage(this.plugin.color("&7- &f#" + match.id() + " &e" + match.name() + " &8@ &b" + this.claimService.displayServerId(match) + " &7[" + match.world() + " " + match.centerX() + ", " + match.centerZ() + "]"));
            }
            return null;
        }
        return matches.get(0);
    }

    private String joinArgs(String[] args, int startInclusive) {
        return this.joinArgs(args, startInclusive, args.length);
    }

    private String joinArgs(String[] args, int startInclusive, int endExclusive) {
        if (args == null || startInclusive >= endExclusive || startInclusive < 0 || endExclusive > args.length) {
            return "";
        }
        return String.join((CharSequence)" ", Arrays.copyOfRange(args, startInclusive, endExclusive)).trim();
    }

    private String normalizeQuery(String rawValue) {
        if (rawValue == null) {
            return null;
        }
        String trimmed = rawValue.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private boolean isFlagListInput(String rawValue) {
        if (rawValue == null) {
            return false;
        }
        String normalized = rawValue.trim().toLowerCase(Locale.ROOT);
        return normalized.equals("list") || normalized.equals("info") || normalized.equals("show");
    }

    private ClaimPermission parsePermission(String rawValue) {
        String normalized;
        if (rawValue == null || rawValue.isBlank()) {
            return null;
        }
        return switch (normalized = rawValue.trim().toLowerCase(Locale.ROOT).replace('-', '_')) {
            case "place" -> ClaimPermission.PLACE;
            case "break" -> ClaimPermission.BREAK;
            case "interact" -> ClaimPermission.INTERACT;
            case "container" -> ClaimPermission.CONTAINER;
            case "redstone" -> ClaimPermission.REDSTONE;
            case "explosion" -> ClaimPermission.EXPLOSION;
            case "bucket" -> ClaimPermission.BUCKET;
            case "teleport", "tp" -> ClaimPermission.TELEPORT;
            case "flight", "fly" -> ClaimPermission.FLIGHT;
            default -> null;
        };
    }

    private Boolean parseAllowDeny(String rawValue) {
        String normalized;
        if (rawValue == null || rawValue.isBlank()) {
            return null;
        }
        return switch (normalized = rawValue.trim().toLowerCase(Locale.ROOT)) {
            case "allow", "on", "true", "yes" -> true;
            case "deny", "off", "false", "no" -> false;
            default -> null;
        };
    }

    private OfflinePlayer resolveKnownPlayer(String rawName) {
        if (rawName == null || rawName.isBlank()) {
            return null;
        }
        Player online = Bukkit.getPlayerExact((String)rawName);
        if (online != null) {
            return online;
        }
        UUID playerId = this.profileService.findPlayerIdByName(rawName);
        return playerId == null ? null : Bukkit.getOfflinePlayer((UUID)playerId);
    }

    private Player resolveOnlinePlayer(String rawName) {
        if (rawName == null || rawName.isBlank()) {
            return null;
        }
        Player exact = Bukkit.getPlayerExact((String)rawName);
        if (exact != null) {
            return exact;
        }
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (!online.getName().equalsIgnoreCase(rawName)) continue;
            return online;
        }
        return null;
    }

    private List<String> knownPlayerNames() {
        LinkedHashSet<String> names = new LinkedHashSet<String>();
        for (Player online : Bukkit.getOnlinePlayers()) {
            names.add(online.getName());
        }
        names.addAll(this.profileService.knownPlayerNames());
        return new ArrayList<String>(names);
    }

    private List<String> onlinePlayerNames() {
        ArrayList<String> names = new ArrayList<String>();
        for (Player online : Bukkit.getOnlinePlayers()) {
            names.add(online.getName());
        }
        return names;
    }

    private List<String> currentEditableClaimMemberNames(Player player) {
        Claim claim = this.claimActionService.findCurrentPresenceClaim(player);
        if (claim == null || !this.claimActionService.canEditClaim(player, claim)) {
            return List.of();
        }
        return this.trustedMemberNames(claim);
    }

    private List<String> currentAdminClaimMemberNames(Player player) {
        Claim claim = this.claimActionService.findCurrentPresenceClaim(player);
        if (claim == null || !this.claimActionService.canEditClaim(player, claim)) {
            return List.of();
        }
        return this.trustedMemberNames(claim);
    }

    private List<String> trustedMemberNames(Claim claim) {
        if (claim == null || claim.trustedMembers().isEmpty()) {
            return List.of();
        }
        return claim.trustedMembers().stream().map(Bukkit::getOfflinePlayer).map(this::displayName).distinct().sorted(String.CASE_INSENSITIVE_ORDER).toList();
    }

    private List<String> claimIdOptions() {
        return this.claimService.allClaims().stream().map(claim -> String.valueOf(claim.id())).distinct().toList();
    }

    private void sendModernHelp(CommandSender sender) {
        sender.sendMessage(this.plugin.color("&6Claim \u547d\u4ee4:"));
        sender.sendMessage(this.plugin.color("&e/claim &7\u67e5\u770b\u73a9\u5bb6\u5e2e\u52a9"));
        sender.sendMessage(this.plugin.color("&e/claim info &7\u67e5\u770b\u811a\u4e0b\u5f53\u524d\u9886\u5730\u8be6\u60c5"));
        sender.sendMessage(this.plugin.color("&e/claim list &7\u67e5\u770b\u4f60\u5f53\u524d\u62e5\u6709\u7684\u9886\u5730\u5217\u8868"));
        sender.sendMessage(this.plugin.color("&e/claim menu &7\u6253\u5f00\u9886\u5730\u83dc\u5355"));
        sender.sendMessage(this.plugin.color("&e/claim create <\u9886\u5730\u540d> &7\u5148\u7528\u666e\u901a\u91d1\u9504\u5934\u5de6\u952e\u70b9 1\u3001\u53f3\u952e\u70b9 2\uff0c\u518d\u521b\u5efa"));
        sender.sendMessage(this.plugin.color("&e/claim tp <\u9886\u5730\u540d> &7\u4f20\u9001\u5230\u4f60\u6709\u6743\u9650\u8fdb\u5165\u7684\u9886\u5730"));
        sender.sendMessage(this.plugin.color("&e/claim tpset &7\u628a\u811a\u4e0b\u4f4d\u7f6e\u8bbe\u4e3a\u5f53\u524d\u9886\u5730\u4f20\u9001\u70b9"));
        sender.sendMessage(this.plugin.color("&e/claim add <\u73a9\u5bb6> &7\u7ad9\u5728\u5f53\u524d\u9886\u5730\u5185\u7ed9\u8fd9\u5757\u9886\u5730\u52a0\u6210\u5458"));
        sender.sendMessage(this.plugin.color("&e/claim unadd <\u73a9\u5bb6> &7\u7ad9\u5728\u5f53\u524d\u9886\u5730\u5185\u79fb\u9664\u8fd9\u5757\u9886\u5730\u6210\u5458"));
        sender.sendMessage(this.plugin.color("&e/claim remove <\u9886\u5730\u540d> &7\u5220\u9664\u4f60\u62e5\u6709\u7684\u9886\u5730"));
        sender.sendMessage(this.plugin.color("&e/claim deny <\u73a9\u5bb6> \u6216 /claim deny * &7\u7ad9\u5728\u5f53\u524d\u9886\u5730\u5185\u8bbe\u7f6e deny"));
        sender.sendMessage(this.plugin.color("&e/claim undeny <\u73a9\u5bb6> \u6216 /claim undeny * &7\u7ad9\u5728\u5f53\u524d\u9886\u5730\u5185\u53d6\u6d88 deny"));
        sender.sendMessage(this.plugin.color("&e/claim flag [list] &7\u67e5\u770b\u6216\u8c03\u6574\u811a\u4e0b\u9886\u5730\u7684\u4ea4\u4e92\u65d7\u6807"));
        sender.sendMessage(this.plugin.color("&7\u9996\u5757\u9886\u5730\uff1a\u5728\u7ebf &e" + this.plugin.settings().starterRewardMinutes() + " &7\u5206\u949f\u4f1a\u81ea\u52a8\u53d1\u65b0\u4eba\u6838\u5fc3\uff08\u9ed8\u8ba4\u5168\u683c\u4fdd\u62a4\uff09\uff0c\u4e5f\u53ef\u4ee5\u76f4\u63a5\u7528\u666e\u901a\u91d1\u9504\u5934\u9009\u533a\u521b\u5efa\u3002"));
        if (this.hasAnyAdminPermission(sender)) {
            sender.sendMessage(this.plugin.color("&6\u7ba1\u7406\u5458:"));
            sender.sendMessage(this.plugin.color("&e/claim admin create system <\u9886\u5730\u540d> &7\u6309\u5f53\u524d\u9009\u533a\u521b\u5efa\u7cfb\u7edf\u9886\u5730"));
            sender.sendMessage(this.plugin.color("&e/claim admin info <\u9886\u5730\u540d|#claimId> &7\u67e5\u770b\u9886\u5730\u5b8c\u6574\u8be6\u60c5"));
            sender.sendMessage(this.plugin.color("&e/claim admin playerclaims <\u73a9\u5bb6> &7\u67e5\u770b\u73a9\u5bb6\u540d\u4e0b\u5168\u90e8\u9886\u5730"));
            sender.sendMessage(this.plugin.color("&e/claim admin diagnose <\u9886\u5730\u540d|#claimId> &7\u67e5\u770b\u8de8\u670d\u4e0e TP \u8bca\u65ad"));
            sender.sendMessage(this.plugin.color("&e/claim admin add <\u73a9\u5bb6> &7\u7ad9\u5728\u5f53\u524d\u9886\u5730\u5185\u5f3a\u5236\u6dfb\u52a0\u6210\u5458"));
            sender.sendMessage(this.plugin.color("&e/claim admin remove [\u9886\u5730\u540d|#claimId] &7\u5220\u9664\u811a\u4e0b\u6216\u6307\u5b9a\u9886\u5730"));
            sender.sendMessage(this.plugin.color("&e/claim admin unadd <\u73a9\u5bb6> &7\u7ad9\u5728\u5f53\u524d\u9886\u5730\u5185\u5f3a\u5236\u79fb\u9664\u6210\u5458"));
            sender.sendMessage(this.plugin.color("&e/claim admin deny <\u73a9\u5bb6> \u6216 /claim admin deny * &7\u7ad9\u5728\u5f53\u524d\u9886\u5730\u5185\u5f3a\u5236\u6539 deny"));
            sender.sendMessage(this.plugin.color("&e/claim admin undeny <\u73a9\u5bb6> \u6216 /claim admin undeny * &7\u7ad9\u5728\u5f53\u524d\u9886\u5730\u5185\u53d6\u6d88 deny"));
            sender.sendMessage(this.plugin.color("&e/claim admin permission <permission> <allow|deny> &7\u7ad9\u5728\u5f53\u524d\u9886\u5730\u5185\u5f3a\u5236\u6539\u9ed8\u8ba4\u6743\u9650"));
            sender.sendMessage(this.plugin.color("&e/claim admin flag <flag> <allow|deny|unset> &7\u7ad9\u5728\u5f53\u524d\u9886\u5730\u5185\u5f3a\u5236\u6539\u4ea4\u4e92\u65d7\u6807"));
            sender.sendMessage(this.plugin.color("&e/claim admin setserver <claimId> <serverId> &7\u4fee\u590d\u65e7\u9886\u5730\u7684\u8de8\u670d server_id"));
            sender.sendMessage(this.plugin.color("&e/claim activity <get|set|add|take> <\u73a9\u5bb6> [\u503c] &7\u7ba1\u7406\u6d3b\u8dc3\u503c"));
            sender.sendMessage(this.plugin.color("&e/claim reload &7\u91cd\u8f7d\u914d\u7f6e\u4e0e\u7f13\u5b58"));
            sender.sendMessage(this.plugin.color("&e/claim givecore <\u73a9\u5bb6> [\u6570\u91cf] &7\u624b\u52a8\u53d1\u653e\u9886\u5730\u6838\u5fc3"));
        }
    }

    private void sendHelp(CommandSender sender) {
        this.sendModernHelp(sender);
    }

    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        return this.tabCompletionService.complete(sender, command, alias, args);
    }

    List<String> completeTab(CommandSender sender, Command command, String alias, String[] args) {
        if (args == null) {
            return List.of();
        }
        ArrayList<String> options = new ArrayList<String>();
        if (args.length == 1) {
            options.add("help");
            options.add("info");
            options.add("list");
            options.add("menu");
            options.add("create");
            options.add("tp");
            options.add("tpset");
            options.add("flag");
            options.add("add");
            options.add("unadd");
            options.add("remove");
            options.add("deny");
            options.add("undeny");
            if (this.hasAnyAdminPermission(sender)) {
                options.add("edit");
                options.add("admin");
                options.add("activity");
                options.add("reload");
                options.add("givecore");
            }
            return this.filter(options, args[0]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("expand")) {
            options.add("east");
            options.add("south");
            options.add("west");
            options.add("north");
            return this.filter(options, args[1]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("givecore")) {
            options.addAll(this.onlinePlayerNames());
            return this.filter(options, args[1]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("activity")) {
            options.add("get");
            options.add("set");
            options.add("add");
            options.add("take");
            return this.filter(options, args[1]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("flag")) {
            options.add("list");
            options.addAll(this.flagKeys());
            return this.filter(options, args[1]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("admin") && this.hasAnyAdminPermission(sender)) {
            options.add("create");
            options.add("info");
            options.add("playerclaims");
            options.add("diagnose");
            options.add("add");
            options.add("remove");
            options.add("unadd");
            options.add("deny");
            options.add("undeny");
            options.add("permission");
            options.add("flag");
            options.add("cleanup");
            options.add("setserver");
            return this.filter(options, args[1]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("add")) {
            options.addAll(this.knownPlayerNames());
            return this.filter(options, args[1]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("unadd") && sender instanceof Player) {
            Player player = (Player)sender;
            options.addAll(this.currentEditableClaimMemberNames(player));
            return this.filter(options, args[1]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("remove") && sender instanceof Player) {
            Player player = (Player)sender;
            options.addAll(this.claimNames(this.claimService.claimsOf(player.getUniqueId())));
            return this.filterByJoinedInput(options, args, 1);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("transfer")) {
            options.addAll(this.knownPlayerNames());
            if (sender instanceof Player) {
                Player player = (Player)sender;
                options.add("accept");
                options.add("deny");
                options.addAll(this.claimNames(this.claimService.claimsOf(player.getUniqueId())));
            }
            return this.filter(options, args[1]);
        }
        if (args.length == 2 && (args[0].equalsIgnoreCase("deny") || args[0].equalsIgnoreCase("undeny"))) {
            options.add("*");
            options.addAll(this.knownPlayerNames());
            return this.filter(options, args[1]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("edit") && this.hasAdminClaimManagePermission(sender)) {
            options.addAll(this.claimNames(this.claimService.allClaims()));
            return this.filterByJoinedInput(options, args, 1);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("show")) {
            options.add("auto");
            if (sender instanceof Player) {
                Player player = (Player)sender;
                options.addAll(this.claimNames(this.claimService.allClaims().stream().filter(claim -> this.hasAdminForcePermission((CommandSender)player) || claim.owner().equals(player.getUniqueId()) || this.claimService.canAccess((Claim)claim, player.getUniqueId())).toList()));
            } else {
                options.addAll(this.claimNames(this.claimService.allClaims()));
            }
            return this.filterByJoinedInput(options, args, 1);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("tp")) {
            if (sender instanceof Player) {
                Player player = (Player)sender;
                options.addAll(this.claimNames(this.claimService.allClaims().stream().filter(claim -> this.hasAdminForcePermission((CommandSender)player) || this.claimService.hasPermission((Claim)claim, player.getUniqueId(), ClaimPermission.TELEPORT)).toList()));
            }
            return this.filterByJoinedInput(options, args, 1);
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("show") && args[1].equalsIgnoreCase("auto")) {
            options.add("on");
            options.add("off");
            return this.filter(options, args[2]);
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("flag")) {
            options.add("allow");
            options.add("deny");
            options.add("unset");
            return this.filter(options, args[2]);
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("activity")) {
            options.addAll(this.knownPlayerNames());
            return this.filter(options, args[2]);
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("admin") && args[1].equalsIgnoreCase("setserver")) {
            options.addAll(this.claimIdOptions());
            return this.filter(options, args[2]);
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("admin") && args[1].equalsIgnoreCase("create")) {
            options.add("system");
            return this.filter(options, args[2]);
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("admin") && (args[1].equalsIgnoreCase("playerclaims") || args[1].equalsIgnoreCase("claims"))) {
            options.addAll(this.knownPlayerNames());
            return this.filter(options, args[2]);
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("admin") && args[1].equalsIgnoreCase("remove")) {
            options.addAll(this.claimSelectorOptions(this.claimService.allClaims()));
            return this.filterByJoinedInput(options, args, 2);
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("admin") && args[1].equalsIgnoreCase("add")) {
            options.addAll(this.knownPlayerNames());
            return this.filter(options, args[2]);
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("admin") && args[1].equalsIgnoreCase("unadd") && sender instanceof Player) {
            Player player = (Player)sender;
            options.addAll(this.currentAdminClaimMemberNames(player));
            return this.filter(options, args[2]);
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("admin") && (args[1].equalsIgnoreCase("deny") || args[1].equalsIgnoreCase("undeny"))) {
            options.add("*");
            options.addAll(this.knownPlayerNames());
            return this.filter(options, args[2]);
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("admin") && (args[1].equalsIgnoreCase("permission") || args[1].equalsIgnoreCase("perm"))) {
            options.addAll(this.permissionKeys());
            return this.filter(options, args[2]);
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("admin") && args[1].equalsIgnoreCase("flag")) {
            options.add("list");
            options.addAll(this.flagKeys());
            return this.filter(options, args[2]);
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("admin") && args[1].equalsIgnoreCase("cleanup")) {
            options.add("list");
            options.add("run");
            options.add("skip");
            options.add("baseline");
            return this.filter(options, args[2]);
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("admin") && (args[1].equalsIgnoreCase("info") || args[1].equalsIgnoreCase("transfer") || args[1].equalsIgnoreCase("diagnose"))) {
            options.addAll(this.claimSelectorOptions(this.claimService.allClaims()));
            return this.filterByJoinedInput(options, args, 2);
        }
        if (args.length == 4 && args[0].equalsIgnoreCase("admin") && (args[1].equalsIgnoreCase("permission") || args[1].equalsIgnoreCase("perm"))) {
            options.add("allow");
            options.add("deny");
            return this.filter(options, args[3]);
        }
        if (args.length == 4 && args[0].equalsIgnoreCase("admin") && args[1].equalsIgnoreCase("flag")) {
            options.add("allow");
            options.add("deny");
            options.add("unset");
            return this.filter(options, args[3]);
        }
        if (args.length == 4 && args[0].equalsIgnoreCase("admin") && args[1].equalsIgnoreCase("cleanup")) {
            if (args[2].equalsIgnoreCase("skip") || args[2].equalsIgnoreCase("baseline")) {
                options.addAll(this.claimSelectorOptions(this.claimService.allClaims()));
            }
            return this.filterByJoinedInput(options, args, 3);
        }
        if (args.length == 5 && args[0].equalsIgnoreCase("admin") && args[1].equalsIgnoreCase("cleanup") && args[2].equalsIgnoreCase("baseline")) {
            options.add("empty");
            options.add("used");
            options.add("skip");
            return this.filter(options, args[4]);
        }
        if (args.length > 2 && (args[0].equalsIgnoreCase("show") || args[0].equalsIgnoreCase("tp") || args[0].equalsIgnoreCase("edit") || args[0].equalsIgnoreCase("remove"))) {
            if (args[0].equalsIgnoreCase("show") && args[1].equalsIgnoreCase("auto")) {
                return options;
            }
            if (sender instanceof Player) {
                Player player = (Player)sender;
                if (args[0].equalsIgnoreCase("show")) {
                    options.addAll(this.claimNames(this.claimService.allClaims().stream().filter(claim -> this.hasAdminForcePermission((CommandSender)player) || claim.owner().equals(player.getUniqueId()) || this.claimService.canAccess((Claim)claim, player.getUniqueId())).toList()));
                } else if (args[0].equalsIgnoreCase("tp")) {
                    options.addAll(this.claimNames(this.claimService.allClaims().stream().filter(claim -> this.hasAdminForcePermission((CommandSender)player) || this.claimService.hasPermission((Claim)claim, player.getUniqueId(), ClaimPermission.TELEPORT)).toList()));
                } else if (args[0].equalsIgnoreCase("edit") && this.hasAdminClaimManagePermission(sender)) {
                    options.addAll(this.claimNames(this.claimService.allClaims()));
                } else if (args[0].equalsIgnoreCase("remove")) {
                    options.addAll(this.claimNames(this.claimService.claimsOf(player.getUniqueId())));
                }
            } else if (args[0].equalsIgnoreCase("edit") && this.hasAdminClaimManagePermission(sender)) {
                options.addAll(this.claimNames(this.claimService.allClaims()));
            }
            return this.filterByJoinedInput(options, args, 1);
        }
        if (args.length > 3 && args[0].equalsIgnoreCase("admin") && (args[1].equalsIgnoreCase("info") || args[1].equalsIgnoreCase("remove") || args[1].equalsIgnoreCase("transfer") || args[1].equalsIgnoreCase("diagnose") || args[1].equalsIgnoreCase("cleanup") && (args[2].equalsIgnoreCase("skip") || args[2].equalsIgnoreCase("baseline")))) {
            String candidateName;
            if (args[1].equalsIgnoreCase("transfer")) {
                candidateName = this.joinArgs(args, 2, args.length - 1);
                if (this.hasUniqueMatchingClaim(this.claimService.allClaims(), candidateName)) {
                    options.addAll(this.knownPlayerNames());
                    return this.filter(options, args[args.length - 1]);
                }
            }
            if (args[1].equalsIgnoreCase("cleanup") && args[2].equalsIgnoreCase("baseline")) {
                candidateName = this.joinArgs(args, 3, args.length - 1);
                if (this.hasUniqueMatchingClaim(this.claimService.allClaims(), candidateName)) {
                    options.add("empty");
                    options.add("used");
                    options.add("skip");
                    return this.filter(options, args[args.length - 1]);
                }
            }
            options.addAll(this.claimNames(this.claimService.allClaims()));
            return this.filterByJoinedInput(options, args, args[1].equalsIgnoreCase("cleanup") ? 3 : 2);
        }
        if (args.length > 2 && args[0].equalsIgnoreCase("transfer") && sender instanceof Player) {
            String candidateName;
            Player player = (Player)sender;
            List<Claim> ownedClaims = this.claimService.claimsOf(player.getUniqueId());
            if (this.hasUniqueMatchingClaim(ownedClaims, candidateName = this.joinArgs(args, 1, args.length - 1))) {
                options.addAll(this.knownPlayerNames());
                return this.filter(options, args[args.length - 1]);
            }
            options.addAll(this.claimNames(ownedClaims));
            return this.filterByJoinedInput(options, args, 1);
        }
        return options;
    }

    private List<String> filter(List<String> options, String input) {
        String lowered = input.toLowerCase(Locale.ROOT);
        return options.stream().distinct().filter(option -> option.toLowerCase(Locale.ROOT).startsWith(lowered)).toList();
    }

    private List<String> filterByJoinedInput(List<String> options, String[] args, int startInclusive) {
        return this.filter(options, this.joinArgs(args, startInclusive));
    }

    private List<String> claimNames(List<Claim> claims) {
        return claims.stream().map(Claim::name).distinct().toList();
    }

    private boolean hasUniqueMatchingClaim(List<Claim> claims, String rawName) {
        List<Claim> matchingClaims = this.claimService.findClaimsByNameFresh(rawName);
        return claims.stream().filter(matchingClaims::contains).count() == 1L;
    }

    private List<String> flagKeys() {
        return Arrays.stream(ClaimFlag.values()).map(ClaimFlag::key).toList();
    }

    private List<String> permissionKeys() {
        return Arrays.stream(ClaimPermission.values()).filter(permission -> permission != ClaimPermission.CONTAINER).map(permission -> permission.name().toLowerCase(Locale.ROOT)).toList();
    }

    private List<String> claimSelectorOptions(List<Claim> claims) {
        ArrayList<String> selectors = new ArrayList<String>();
        for (Claim claim : claims) {
            selectors.add("#" + claim.id());
            selectors.add(claim.name());
        }
        return selectors.stream().distinct().toList();
    }
}
