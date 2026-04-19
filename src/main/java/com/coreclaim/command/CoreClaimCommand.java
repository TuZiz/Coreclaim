package com.coreclaim.command;

import com.coreclaim.CoreClaimPlugin;
import com.coreclaim.config.ClaimGroup;
import com.coreclaim.gui.MenuService;
import com.coreclaim.model.Claim;
import com.coreclaim.model.ClaimDirection;
import com.coreclaim.model.ClaimFlag;
import com.coreclaim.model.ClaimFlagState;
import com.coreclaim.model.ClaimPermission;
import com.coreclaim.model.PlayerProfile;
import com.coreclaim.service.ClaimActionService;
import com.coreclaim.service.ClaimSelectionService;
import com.coreclaim.service.ClaimVisualService;
import com.coreclaim.service.ClaimService;
import com.coreclaim.service.ClaimTransferService;
import com.coreclaim.service.ProfileService;
import com.coreclaim.service.RemovalConfirmationService;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

public final class CoreClaimCommand implements TabExecutor {

    private final CoreClaimPlugin plugin;
    private final ClaimService claimService;
    private final ProfileService profileService;
    private final ClaimActionService claimActionService;
    private final ClaimVisualService claimVisualService;
    private final ClaimSelectionService claimSelectionService;
    private final MenuService menuService;
    private final RemovalConfirmationService removalConfirmationService;
    private final ClaimTransferService claimTransferService;
    public CoreClaimCommand(
        CoreClaimPlugin plugin,
        ClaimService claimService,
        ProfileService profileService,
        ClaimActionService claimActionService,
        ClaimVisualService claimVisualService,
        ClaimSelectionService claimSelectionService,
        MenuService menuService,
        RemovalConfirmationService removalConfirmationService,
        ClaimTransferService claimTransferService
    ) {
        this.plugin = plugin;
        this.claimService = claimService;
        this.profileService = profileService;
        this.claimActionService = claimActionService;
        this.claimVisualService = claimVisualService;
        this.claimSelectionService = claimSelectionService;
        this.menuService = menuService;
        this.removalConfirmationService = removalConfirmationService;
        this.claimTransferService = claimTransferService;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("coreclaim.use")) {
            sender.sendMessage(plugin.message("no-permission"));
            return true;
        }

        if (args.length == 0) {
            return handleMenu(sender);
        }

        if (args[0].equalsIgnoreCase("help")) {
            sendModernHelp(sender);
            return true;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);
        return switch (sub) {
            case "info" -> handleCurrentClaimInfo(sender);
            case "list" -> handleList(sender);
            case "menu" -> handleMenu(sender);
            case "show" -> handleShow(sender, args);
            case "create" -> handleCreate(sender, args);
            case "edit" -> handleEdit(sender, args);
            case "tp" -> handleTeleport(sender, args);
            case "tpset" -> handleTpSet(sender);
            case "expand" -> handleExpand(sender, args);
            case "remove" -> handleRemove(sender, args);
            case "delete" -> handleDelete(sender, args);
            case "confirm" -> handleConfirm(sender);
            case "deny" -> handleDeny(sender, args);
            case "undeny" -> handleUndeny(sender, args);
            case "flag" -> handleFlag(sender, args);
            case "admin" -> handleAdmin(sender, args);
            case "add" -> handleAdd(sender, args);
            case "transfer" -> handleTransfer(sender, args);
            case "activity" -> handleActivity(sender, args);
            case "reload" -> handleReload(sender);
            case "givecore" -> handleGiveCore(sender, args);
            default -> {
                sendModernHelp(sender);
                yield true;
            }
        };
    }

    private boolean handleList(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(plugin.message("player-only"));
            return true;
        }

        List<Claim> claims = claimService.claimsOfFresh(player.getUniqueId());
        if (claims.isEmpty()) {
            player.sendMessage(plugin.message("claim-list-empty"));
            return true;
        }

        player.sendMessage(plugin.color("&6[Claim] &f你的领地列表:"));
        for (Claim claim : claims) {
            player.sendMessage(plugin.color("&7- &e" + claim.name()
                + " &7核心: &f" + claim.centerX() + "," + claim.centerZ()
                + " &7大小: &f" + claim.width() + "x" + claim.depth()));
        }
        return true;
    }

    private boolean handleMenu(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(plugin.message("player-only"));
            return true;
        }
        menuService.openMainMenu(player);
        return true;
    }

    private boolean handleCurrentClaimInfo(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(plugin.message("player-only"));
            return true;
        }

        Claim claim = claimService.findClaim(player.getLocation()).orElse(null);
        if (claim == null) {
            player.sendMessage(plugin.message("claim-not-found"));
            return true;
        }

        if (!claim.owner().equals(player.getUniqueId())
            && !hasAdminViewPermission(player)
            && !claimService.canAccess(claim, player.getUniqueId())) {
            player.sendMessage(plugin.message("trust-no-permission"));
            return true;
        }

        sendEnhancedClaimDetails(player, claim, false);
        return true;
    }

    private boolean handleExpand(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(plugin.message("player-only"));
            return true;
        }
        if (args.length < 2) {
            player.sendMessage(plugin.message("expand-usage"));
            return true;
        }

        ClaimDirection direction = ClaimDirection.fromInput(args[1]);
        if (direction == null) {
            player.sendMessage(plugin.message("expand-usage"));
            return true;
        }

        claimActionService.expandCurrentClaim(player, direction);
        return true;
    }

    private boolean handleShow(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(plugin.message("player-only"));
            return true;
        }
        if (args.length >= 2 && args[1].equalsIgnoreCase("auto")) {
            return handleShowAuto(player, args);
        }

        Claim claim;
        if (args.length >= 2) {
            claim = resolveAccessibleClaimByName(player, joinArgs(args, 1));
            if (claim == null) {
                return true;
            }
        } else {
            claim = claimService.findClaim(player.getLocation())
                .filter(found -> found.owner().equals(player.getUniqueId()) || claimService.canAccess(found, player.getUniqueId()) || hasAdminForcePermission(player))
                .orElse(null);
            if (claim == null) {
                player.sendMessage(plugin.message("show-usage"));
                return true;
            }
        }

        claimVisualService.showClaim(player, claim);
        player.sendMessage(plugin.message("claim-show-success", "{name}", claim.name()));
        return true;
    }

    private boolean handleShowAuto(Player player, String[] args) {
        PlayerProfile profile = profileService.getOrCreate(player.getUniqueId(), player.getName());
        if (args.length == 2) {
            player.sendMessage(plugin.message("show-auto-status", "{value}", profile.autoShowBorders() ? "开启" : "关闭"));
            return true;
        }

        String mode = args[2].toLowerCase(Locale.ROOT);
        boolean enabled;
        if (mode.equals("on") || mode.equals("enable")) {
            enabled = true;
        } else if (mode.equals("off") || mode.equals("disable")) {
            enabled = false;
        } else {
            player.sendMessage(plugin.message("show-auto-usage"));
            return true;
        }

        profile.setAutoShowBorders(enabled);
        profileService.saveProfile(profile);
        player.sendMessage(plugin.message(enabled ? "show-auto-enabled" : "show-auto-disabled"));
        return true;
    }

    private boolean handleCreate(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(plugin.message("player-only"));
            return true;
        }
        if (args.length < 2) {
            player.sendMessage(plugin.color(plugin.messagesConfig().getString("prefix", "&8[&6Claim&8] &f") + "&c用法: &7/claim create <领地名字>"));
            return true;
        }
        String name = String.join(" ", java.util.Arrays.copyOfRange(args, 1, args.length)).trim();
        ClaimSelectionService.SelectionPreview preview = claimSelectionService.preview(player);
        if (preview == null || !preview.ready()) {
            player.sendMessage(plugin.color(plugin.messagesConfig().getString("prefix", "&8[&6Claim&8] &f") + "&c请先用圈地工具选好两个对角点。"));
            return true;
        }
        if (!preview.allowed()) {
            player.sendMessage(preview.failureMessage());
            return true;
        }
        if (name.isEmpty()) {
            player.sendMessage(plugin.message("claim-name-empty"));
            return true;
        }
        if (name.length() > plugin.settings().claimNameMaxLength()) {
            player.sendMessage(plugin.message("claim-name-too-long", "{max}", String.valueOf(plugin.settings().claimNameMaxLength())));
            return true;
        }
        if (claimService.isClaimNameTaken(name)) {
            player.sendMessage(plugin.message("claim-name-exists", "{name}", name));
            return true;
        }
        menuService.openSelectionCreateMenu(player, name, preview);
        return true;
    }

    private boolean handleTeleport(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(plugin.message("player-only"));
            return true;
        }
        if (args.length < 2) {
            player.sendMessage(plugin.message("teleport-usage"));
            return true;
        }

        Claim claim = resolveTeleportClaimByName(player, joinArgs(args, 1));
        if (claim == null) {
            return true;
        }
        claimActionService.teleportToClaim(player, claim);
        return true;
    }

    private boolean handleTpSet(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(plugin.message("player-only"));
            return true;
        }
        if (!hasManageTeleportPermission(player)) {
            player.sendMessage(plugin.message("no-permission"));
            return true;
        }

        Claim claim = resolveCurrentEditableClaim(player, "/claim tpset");
        if (claim == null) {
            return true;
        }
        if (!claimService.isLocalClaim(claim)) {
            player.sendMessage(chatMessage(
                "tpset-cross-server-denied",
                "&c&l! &7请在领地所属区服内设置传送点。"
            ));
            return true;
        }

        claimService.updateTeleportPoint(claim, player.getLocation());
        player.sendMessage(chatMessage(
            "claim-tpset-success",
            "&a&l传送点: &7已将领地 &e{name} &7的传送点更新到你当前脚下位置。",
            "{name}", claim.name()
        ));
        return true;
    }

    private boolean handleRemove(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(plugin.message("player-only"));
            return true;
        }
        if (args.length != 2) {
            player.sendMessage(plugin.color("&c用法: &7/claim remove <玩家>"));
            return true;
        }
        Claim claim = claimActionService.findCurrentClaim(player);
        if (claim == null) {
            player.sendMessage(plugin.color("&c你必须站在一块可编辑的领地内才能使用 /claim remove"));
            return true;
        }
        if (!claimActionService.canEditClaim(player, claim)) {
            player.sendMessage(plugin.message("trust-no-permission"));
            return true;
        }
        OfflinePlayer target = resolveKnownPlayer(args[1]);
        if (target == null) {
            player.sendMessage(plugin.message("trust-no-target"));
            return true;
        }
        claimActionService.untrustPlayer(player, claim, target);
        return true;
    }

    private boolean handleDelete(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(plugin.message("player-only"));
            return true;
        }
        if (args.length < 2) {
            player.sendMessage(plugin.message("remove-usage"));
            return true;
        }

        Claim claim = resolveOwnedClaimByName(player, joinArgs(args, 1));
        if (claim == null) {
            return true;
        }

        removalConfirmationService.request(player, claim);
        return true;
    }

    private boolean handleConfirm(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(plugin.message("player-only"));
            return true;
        }
        if (!removalConfirmationService.confirm(player)) {
            player.sendMessage(plugin.message("confirm-nothing"));
        }
        return true;
    }

    private boolean handleAdd(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(plugin.message("player-only"));
            return true;
        }
        if (args.length != 2) {
            player.sendMessage(plugin.color("&c\u7528\u6cd5: &7/claim add <\u73a9\u5bb6>"));
            return true;
        }
        Claim claim = claimActionService.findCurrentClaim(player);
        if (claim == null) {
            player.sendMessage(plugin.color("&c\u4f60\u5fc5\u987b\u7ad9\u5728\u4e00\u5757\u53ef\u7f16\u8f91\u7684\u9886\u5730\u5185\u624d\u80fd\u4f7f\u7528 /claim add"));
            return true;
        }
        if (!claimActionService.canEditClaim(player, claim)) {
            player.sendMessage(plugin.message("trust-no-permission"));
            return true;
        }
        OfflinePlayer target = resolveKnownPlayer(args[1]);
        if (target == null) {
            player.sendMessage(plugin.message("trust-no-target"));
            return true;
        }
        claimActionService.trustPlayer(player, claim, target);
        return true;
    }

    private boolean handleDeny(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(plugin.message("player-only"));
            return true;
        }
        if (!hasManageDenyPermission(player)) {
            player.sendMessage(plugin.message("no-permission"));
            return true;
        }
        if (args.length != 2) {
            player.sendMessage(chatMessage("deny-usage", "&c用法: &7/claim deny <玩家> &8或 &7/claim deny *"));
            return true;
        }

        Claim claim = resolveCurrentEditableClaim(player, "/claim deny");
        if (claim == null) {
            return true;
        }
        String targetName = args[1].trim();
        if ("*".equals(targetName)) {
            if (claim.denyAll()) {
                player.sendMessage(chatMessage("claim-deny-all-already-enabled", "&e&l封闭模式: &7这块领地已经是 deny * 状态。"));
                return true;
            }
            claimService.updateDenyAll(claim, true);
            player.sendMessage(chatMessage(
                "claim-deny-all-enabled",
                "&a&l封闭模式: &7已为领地 &e{name} &7开启 deny *。",
                "{name}", claim.name()
            ));
            return true;
        }

        OfflinePlayer target = resolveKnownPlayer(targetName);
        if (target == null || target.getUniqueId() == null) {
            player.sendMessage(plugin.message("trust-no-target"));
            return true;
        }
        if (target.getUniqueId().equals(claim.owner())) {
            player.sendMessage(plugin.message("trust-self"));
            return true;
        }
        if (!claimService.addDeniedMember(claim, target.getUniqueId())) {
            player.sendMessage(chatMessage(
                "claim-deny-exists",
                "&e{player} &7已经在领地 &e{name} &7的 deny 列表里了。",
                "{player}", displayName(target),
                "{name}", claim.name()
            ));
            return true;
        }
        player.sendMessage(chatMessage(
            "claim-deny-added",
            "&a&lDeny: &7已将玩家 &e{player} &7加入领地 &e{name} &7的 deny 列表。",
            "{player}", displayName(target),
            "{name}", claim.name()
        ));
        return true;
    }

    private boolean handleUndeny(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(plugin.message("player-only"));
            return true;
        }
        if (!hasManageDenyPermission(player)) {
            player.sendMessage(plugin.message("no-permission"));
            return true;
        }
        if (args.length != 2) {
            player.sendMessage(chatMessage("undeny-usage", "&c用法: &7/claim undeny <玩家> &8或 &7/claim undeny *"));
            return true;
        }

        Claim claim = resolveCurrentEditableClaim(player, "/claim undeny");
        if (claim == null) {
            return true;
        }
        String targetName = args[1].trim();
        if ("*".equals(targetName)) {
            if (!claim.denyAll()) {
                player.sendMessage(chatMessage("claim-deny-all-already-disabled", "&e&l封闭模式: &7这块领地当前没有开启 deny *。"));
                return true;
            }
            claimService.updateDenyAll(claim, false);
            player.sendMessage(chatMessage(
                "claim-deny-all-disabled",
                "&a&l封闭模式: &7已为领地 &e{name} &7关闭 deny *。",
                "{name}", claim.name()
            ));
            return true;
        }

        OfflinePlayer target = resolveKnownPlayer(targetName);
        if (target == null || target.getUniqueId() == null) {
            player.sendMessage(plugin.message("trust-no-target"));
            return true;
        }
        if (!claimService.removeDeniedMember(claim, target.getUniqueId())) {
            player.sendMessage(chatMessage(
                "claim-deny-missing",
                "&e{player} &7不在领地 &e{name} &7的 deny 列表中。",
                "{player}", displayName(target),
                "{name}", claim.name()
            ));
            return true;
        }
        player.sendMessage(chatMessage(
            "claim-deny-removed",
            "&a&lUndeny: &7已将玩家 &e{player} &7从领地 &e{name} &7的 deny 列表移除。",
            "{player}", displayName(target),
            "{name}", claim.name()
        ));
        return true;
    }

    private boolean handleFlag(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(plugin.message("player-only"));
            return true;
        }
        if (!player.hasPermission("coreclaim.manage.flags") && !hasAdminForcePermission(player)) {
            player.sendMessage(plugin.message("no-permission"));
            return true;
        }

        Claim claim = resolveCurrentEditableClaim(player, "/claim flag");
        if (claim == null) {
            return true;
        }

        if (args.length == 1 || (args.length == 2 && isFlagListInput(args[1]))) {
            sendFlagSummary(player, claim);
            return true;
        }
        if (args.length < 3) {
            player.sendMessage(chatMessage("flag-usage", "&c用法: &7/claim flag <flag> <allow|deny|unset>"));
            return true;
        }

        ClaimFlag flag = ClaimFlag.fromKey(args[1]);
        if (flag == null) {
            player.sendMessage(chatMessage("flag-invalid", "&c未知旗标: &e{flag}", "{flag}", args[1]));
            return true;
        }
        ClaimFlagState state = ClaimFlagState.fromInput(args[2]);
        if (state == null) {
            player.sendMessage(chatMessage("flag-state-invalid", "&c状态只能是 &eallow &7/ &edeny &7/ &eunset"));
            return true;
        }

        claimService.updateFlagState(claim, flag, state);
        player.sendMessage(chatMessage(
            "flag-updated",
            "&a已将领地 &e{name} &7的旗标 &b{flag} &7设置为 {state}&7。",
            "{name}", claim.name(),
            "{flag}", flag.key(),
            "{state}", flagStateText(state)
        ));
        return true;
    }

    private boolean handleAdmin(CommandSender sender, String[] args) {
        if (!hasAnyAdminPermission(sender)) {
            sender.sendMessage(plugin.message("no-permission"));
            return true;
        }
        if (args.length < 2) {
            sender.sendMessage(chatMessage(
                "admin-usage",
                "&c用法: &7/claim admin <create|info|playerclaims|diagnose|add|remove|deny|undeny|permission|flag|setserver> ..."
            ));
            return true;
        }

        String action = args[1].toLowerCase(Locale.ROOT);
        return switch (action) {
            case "create" -> handleAdminCreate(sender, args);
            case "info" -> handleAdminInfo(sender, args);
            case "playerclaims" -> handleAdminPlayerClaims(sender, args);
            case "diagnose" -> handleAdminDiagnose(sender, args);
            case "add" -> handleAdminAdd(sender, args);
            case "permission" -> handleAdminPermission(sender, args);
            case "flag" -> handleAdminFlag(sender, args);
            case "deny" -> handleAdminDeny(sender, args);
            case "undeny" -> handleAdminUndeny(sender, args);
            case "remove" -> handleAdminRemoveMember(sender, args);
            case "setserver" -> handleAdminSetServer(sender, args);
            default -> {
                sender.sendMessage(chatMessage(
                    "admin-usage",
                    "&c用法: &7/claim admin <create|info|playerclaims|diagnose|add|remove|deny|undeny|permission|flag|setserver> ..."
                ));
                yield true;
            }
        };
    }

    private boolean handleAdminCreate(CommandSender sender, String[] args) {
        if (!hasAdminCreateSystemPermission(sender)) {
            sender.sendMessage(plugin.message("no-permission"));
            return true;
        }
        if (!(sender instanceof Player player)) {
            sender.sendMessage(plugin.message("player-only"));
            return true;
        }
        if (args.length < 4 || !args[2].equalsIgnoreCase("system")) {
            sender.sendMessage(chatMessage("admin-create-system-usage", "&c用法: &7/claim admin create system <领地名>"));
            return true;
        }
        String claimName = joinArgs(args, 3);
        if (claimName.isBlank()) {
            sender.sendMessage(chatMessage("admin-create-system-usage", "&c用法: &7/claim admin create system <领地名>"));
            return true;
        }
        return claimSelectionService.createSystemClaim(player, claimName);
    }

    private boolean handleAdminInfo(CommandSender sender, String[] args) {
        if (!hasAdminViewPermission(sender)) {
            sender.sendMessage(plugin.message("no-permission"));
            return true;
        }
        if (args.length < 3) {
            sender.sendMessage(plugin.message("admin-info-usage"));
            return true;
        }

        Claim claim = resolveAdminClaimSelector(sender, joinArgs(args, 2));
        if (claim == null) {
            return true;
        }

        sendEnhancedClaimDetails(sender, claim, true);
        return true;
    }

    private boolean handleAdminPlayerClaims(CommandSender sender, String[] args) {
        if (!hasAdminViewPermission(sender)) {
            sender.sendMessage(plugin.message("no-permission"));
            return true;
        }
        if (args.length < 3) {
            sender.sendMessage(chatMessage("admin-playerclaims-usage", "&c用法: &7/claim admin playerclaims <玩家>"));
            return true;
        }
        OfflinePlayer target = resolveKnownPlayer(joinArgs(args, 2));
        if (target == null || target.getUniqueId() == null) {
            sender.sendMessage(plugin.message("trust-no-target"));
            return true;
        }
        List<Claim> claims = claimService.claimsOfFresh(target.getUniqueId(), true);
        if (claims.isEmpty()) {
            sender.sendMessage(chatMessage(
                "admin-playerclaims-empty",
                "&e{player} &7名下当前没有领地。",
                "{player}", displayName(target)
            ));
            return true;
        }
        sender.sendMessage(plugin.color("&6[Claim] &f玩家 &e" + displayName(target) + " &f名下领地:"));
        for (Claim claim : claims) {
            sender.sendMessage(plugin.color("&7- &f#" + claim.id()
                + " &e" + (claim.systemManaged() ? "[SYSTEM] " : "") + claim.name()
                + " &8@ &b" + claimService.displayServerId(claim)
                + " &7[" + claim.world() + " " + claim.centerX() + ", " + claim.centerZ() + "]"));
        }
        return true;
    }

    private boolean handleAdminDiagnose(CommandSender sender, String[] args) {
        if (!hasAdminViewPermission(sender)) {
            sender.sendMessage(plugin.message("no-permission"));
            return true;
        }
        if (args.length < 3) {
            sender.sendMessage(chatMessage("admin-diagnose-usage", "&c用法: &7/claim admin diagnose <领地名|#claimId>"));
            return true;
        }
        Claim claim = resolveAdminClaimSelector(sender, joinArgs(args, 2));
        if (claim == null) {
            return true;
        }
        boolean localClaim = claimService.isLocalClaim(claim);
        boolean worldLoaded = localClaim && Bukkit.getWorld(claim.world()) != null;
        String route = localClaim
            ? (worldLoaded ? "local-teleport" : "local-world-missing")
            : (plugin.settings().crossServerTeleportEnabled() ? "cross-server-teleport" : "cross-server-disabled");
        sender.sendMessage(plugin.color("&6[Claim] &f诊断目标: &e" + (claim.systemManaged() ? "[SYSTEM] " : "") + claim.name() + " &7(#" + claim.id() + ")"));
        sender.sendMessage(plugin.color("&6[Claim] &fOwner: &b" + claim.ownerName() + " &8| &fServer ID: &e" + claimService.displayServerId(claim)));
        sender.sendMessage(plugin.color("&6[Claim] &f当前服 server-id: &e" + plugin.settings().serverId()
            + " &8| &f是否本服: " + (localClaim ? "&a是" : "&c否")));
        sender.sendMessage(plugin.color("&6[Claim] &f系统领地: " + (claim.systemManaged() ? "&6是 &8| &f计入配额: &c否" : "&7否 &8| &f计入配额: &a是")));
        sender.sendMessage(plugin.color("&6[Claim] &f规则来源: " + ruleSourceSummary(claim)));
        sender.sendMessage(plugin.color("&6[Claim] &f世界状态: " + (worldLoaded ? "&a已加载" : "&e未加载或不在本服")));
        sender.sendMessage(plugin.color("&6[Claim] &fTP 路由: &b" + route));
        sender.sendMessage(plugin.color("&6[Claim] &fdeny *: " + (claim.denyAll() ? "&c开启" : "&a关闭")
            + " &8| &fDenied: &c" + claim.deniedMembers().size()
            + " &8| &fTrusted: &a" + claim.trustedCount()));
        sender.sendMessage(plugin.color("&6[Claim] &f交互旗标: " + summarizeFlags(claim)));
        return true;
    }

    private boolean handleAdminPermission(CommandSender sender, String[] args) {
        if (!hasAdminPermissionManagePermission(sender)) {
            sender.sendMessage(plugin.message("no-permission"));
            return true;
        }
        Claim claim;
        String permissionInput;
        String stateInput;
        if (args.length == 4 && sender instanceof Player player) {
            claim = resolveCurrentAdminClaim(player, "/claim admin permission");
            if (claim == null) {
                return true;
            }
            permissionInput = args[2];
            stateInput = args[3];
        } else if (args.length >= 5) {
            claim = resolveAdminClaimSelector(sender, joinArgs(args, 2, args.length - 2));
            if (claim == null) {
                return true;
            }
            permissionInput = args[args.length - 2];
            stateInput = args[args.length - 1];
        } else {
            sender.sendMessage(chatMessage("admin-permission-usage", "&c用法: &7/claim admin permission <permission> <allow|deny>"));
            return true;
        }
        ClaimPermission permission = parsePermission(permissionInput);
        if (permission == null) {
            sender.sendMessage(chatMessage("admin-permission-invalid", "&c未知默认权限: &e{permission}", "{permission}", permissionInput));
            return true;
        }
        Boolean allowed = parseAllowDeny(stateInput);
        if (allowed == null) {
            sender.sendMessage(chatMessage("admin-permission-state-invalid", "&c状态只能是 &eallow &7或 &edeny"));
            return true;
        }
        claimService.updatePermission(claim, permission, allowed);
        sender.sendMessage(chatMessage(
            "admin-permission-updated",
            "&a已将领地 &e{name} &7的默认权限 &b{permission} &7设置为 {state}&7。",
            "{name}", claim.name(),
            "{permission}", permission.name().toLowerCase(Locale.ROOT),
            "{state}", stateText(allowed)
        ));
        return true;
    }

    private boolean handleAdminFlag(CommandSender sender, String[] args) {
        if (!hasAdminFlagManagePermission(sender)) {
            sender.sendMessage(plugin.message("no-permission"));
            return true;
        }
        Claim claim;
        String flagInput;
        String stateInput;
        if (args.length == 4 && sender instanceof Player player) {
            claim = resolveCurrentAdminClaim(player, "/claim admin flag");
            if (claim == null) {
                return true;
            }
            flagInput = args[2];
            stateInput = args[3];
        } else if (args.length >= 5) {
            claim = resolveAdminClaimSelector(sender, joinArgs(args, 2, args.length - 2));
            if (claim == null) {
                return true;
            }
            flagInput = args[args.length - 2];
            stateInput = args[args.length - 1];
        } else {
            sender.sendMessage(chatMessage("admin-flag-usage", "&c用法: &7/claim admin flag <flag> <allow|deny|unset>"));
            return true;
        }
        ClaimFlag flag = ClaimFlag.fromKey(flagInput);
        if (flag == null) {
            sender.sendMessage(chatMessage("admin-flag-invalid", "&c未知旗标: &e{flag}", "{flag}", flagInput));
            return true;
        }
        ClaimFlagState state = ClaimFlagState.fromInput(stateInput);
        if (state == null) {
            sender.sendMessage(chatMessage("admin-flag-state-invalid", "&c状态只能是 &eallow &7/ &edeny &7/ &eunset"));
            return true;
        }
        claimService.updateFlagState(claim, flag, state);
        sender.sendMessage(chatMessage(
            "admin-flag-updated",
            "&a已将领地 &e{name} &7的旗标 &b{flag} &7设置为 {state}&7。",
            "{name}", claim.name(),
            "{flag}", flag.key(),
            "{state}", flagStateText(state)
        ));
        return true;
    }

    private boolean handleAdminDeny(CommandSender sender, String[] args) {
        if (!hasAdminMemberManagePermission(sender)) {
            sender.sendMessage(plugin.message("no-permission"));
            return true;
        }
        Claim claim;
        String targetArg;
        if (args.length == 3 && sender instanceof Player player) {
            claim = resolveCurrentAdminClaim(player, "/claim admin deny");
            if (claim == null) {
                return true;
            }
            targetArg = args[2];
        } else if (args.length >= 4) {
            claim = resolveAdminClaimSelector(sender, joinArgs(args, 2, args.length - 1));
            if (claim == null) {
                return true;
            }
            targetArg = args[args.length - 1];
        } else {
            sender.sendMessage(chatMessage("admin-deny-usage", "&c用法: &7/claim admin deny <玩家|*>"));
            return true;
        }
        if ("*".equals(targetArg)) {
            claimService.updateDenyAll(claim, true);
            sender.sendMessage(chatMessage("admin-deny-all-enabled", "&a已为领地 &e{name} &7开启 deny *。", "{name}", claim.name()));
            return true;
        }
        OfflinePlayer target = resolveKnownPlayer(targetArg);
        if (target == null || target.getUniqueId() == null) {
            sender.sendMessage(plugin.message("trust-no-target"));
            return true;
        }
        if (!claimService.addDeniedMember(claim, target.getUniqueId())) {
            sender.sendMessage(chatMessage("admin-deny-exists", "&e{player} &7已经在领地 &e{name} &7的 deny 列表中。", "{player}", displayName(target), "{name}", claim.name()));
            return true;
        }
        sender.sendMessage(chatMessage("admin-deny-added", "&a已将玩家 &e{player} &7加入领地 &e{name} &7的 deny 列表。", "{player}", displayName(target), "{name}", claim.name()));
        return true;
    }

    private boolean handleAdminUndeny(CommandSender sender, String[] args) {
        if (!hasAdminMemberManagePermission(sender)) {
            sender.sendMessage(plugin.message("no-permission"));
            return true;
        }
        Claim claim;
        String targetArg;
        if (args.length == 3 && sender instanceof Player player) {
            claim = resolveCurrentAdminClaim(player, "/claim admin undeny");
            if (claim == null) {
                return true;
            }
            targetArg = args[2];
        } else if (args.length >= 4) {
            claim = resolveAdminClaimSelector(sender, joinArgs(args, 2, args.length - 1));
            if (claim == null) {
                return true;
            }
            targetArg = args[args.length - 1];
        } else {
            sender.sendMessage(chatMessage("admin-undeny-usage", "&c用法: &7/claim admin undeny <玩家|*>"));
            return true;
        }
        if ("*".equals(targetArg)) {
            claimService.updateDenyAll(claim, false);
            sender.sendMessage(chatMessage("admin-deny-all-disabled", "&a已为领地 &e{name} &7关闭 deny *。", "{name}", claim.name()));
            return true;
        }
        OfflinePlayer target = resolveKnownPlayer(targetArg);
        if (target == null || target.getUniqueId() == null) {
            sender.sendMessage(plugin.message("trust-no-target"));
            return true;
        }
        if (!claimService.removeDeniedMember(claim, target.getUniqueId())) {
            sender.sendMessage(chatMessage("admin-deny-missing", "&e{player} &7不在领地 &e{name} &7的 deny 列表中。", "{player}", displayName(target), "{name}", claim.name()));
            return true;
        }
        sender.sendMessage(chatMessage("admin-deny-removed", "&a已将玩家 &e{player} &7从领地 &e{name} &7的 deny 列表移除。", "{player}", displayName(target), "{name}", claim.name()));
        return true;
    }

    private boolean handleAdminAdd(CommandSender sender, String[] args) {
        if (!hasAdminMemberManagePermission(sender)) {
            sender.sendMessage(plugin.message("no-permission"));
            return true;
        }
        if (args.length < 3) {
            sender.sendMessage(chatMessage("admin-add-usage", "&c用法: &7/claim admin add <玩家>"));
            return true;
        }
        if (!(sender instanceof Player player)) {
            sender.sendMessage(plugin.message("player-only"));
            return true;
        }
        Claim claim = resolveCurrentAdminClaim(player, "/claim admin add");
        if (claim == null) {
            return true;
        }
        OfflinePlayer target = resolveKnownPlayer(joinArgs(args, 2));
        if (target == null || target.getUniqueId() == null) {
            sender.sendMessage(plugin.message("trust-no-target"));
            return true;
        }
        if (!claimService.addTrustedMember(claim, target.getUniqueId())) {
            sender.sendMessage(chatMessage("admin-trust-exists", "&e{player} &7已经是领地 &e{name} &7的成员。", "{player}", displayName(target), "{name}", claim.name()));
            return true;
        }
        sender.sendMessage(chatMessage("admin-trust-added", "&a已将玩家 &e{player} &7加入领地 &e{name} &7的成员列表。", "{player}", displayName(target), "{name}", claim.name()));
        return true;
    }

    private boolean handleAdminRemoveMember(CommandSender sender, String[] args) {
        if (!hasAdminMemberManagePermission(sender)) {
            sender.sendMessage(plugin.message("no-permission"));
            return true;
        }
        if (args.length < 3) {
            sender.sendMessage(chatMessage("admin-remove-member-usage", "&c用法: &7/claim admin remove <玩家>"));
            return true;
        }
        if (!(sender instanceof Player player)) {
            sender.sendMessage(plugin.message("player-only"));
            return true;
        }
        Claim claim = resolveCurrentAdminClaim(player, "/claim admin remove");
        if (claim == null) {
            return true;
        }
        OfflinePlayer target = resolveKnownPlayer(joinArgs(args, 2));
        if (target == null || target.getUniqueId() == null) {
            sender.sendMessage(plugin.message("trust-no-target"));
            return true;
        }
        if (!claimService.removeTrustedMember(claim, target.getUniqueId())) {
            sender.sendMessage(chatMessage("admin-untrust-missing", "&e{player} &7不在领地 &e{name} &7的成员列表中。", "{player}", displayName(target), "{name}", claim.name()));
            return true;
        }
        sender.sendMessage(chatMessage("admin-untrust-removed", "&a已将玩家 &e{player} &7从领地 &e{name} &7的成员列表移除。", "{player}", displayName(target), "{name}", claim.name()));
        return true;
    }

    private boolean handleAdminDeleteClaim(CommandSender sender, String[] args) {
        if (!hasAdminClaimManagePermission(sender)) {
            sender.sendMessage(plugin.message("no-permission"));
            return true;
        }
        if (args.length < 3) {
            sender.sendMessage(plugin.message("admin-remove-usage"));
            return true;
        }

        Claim claim = resolveAdminClaimSelector(sender, joinArgs(args, 2));
        if (claim == null) {
            return true;
        }
        return claimActionService.adminRemoveClaim(sender, claim);
    }

    private boolean handleAdminTransfer(CommandSender sender, String[] args) {
        if (!hasAdminClaimManagePermission(sender)) {
            sender.sendMessage(plugin.message("no-permission"));
            return true;
        }
        if (args.length < 4) {
            sender.sendMessage(plugin.message("admin-transfer-usage"));
            return true;
        }
        String claimName = joinArgs(args, 2, args.length - 1);
        if (claimName.isBlank()) {
            sender.sendMessage(plugin.message("admin-transfer-usage"));
            return true;
        }
        Claim claim = resolveAdminClaimSelector(sender, claimName);
        if (claim == null) {
            return true;
        }
        Player target = resolveOnlinePlayer(args[args.length - 1]);
        claimTransferService.forceTransfer(sender, claim, target);
        return true;
    }

    private boolean handleAdminSetServer(CommandSender sender, String[] args) {
        if (!hasAdminClaimManagePermission(sender)) {
            sender.sendMessage(plugin.message("no-permission"));
            return true;
        }
        if (args.length < 4) {
            sender.sendMessage(chatMessage("admin-setserver-usage", "&c用法: &7/claim admin setserver <claimId> <serverId>"));
            return true;
        }
        int claimId = parseClaimId(args[2], sender);
        if (claimId <= 0) {
            return true;
        }
        String targetServerId = args[3] == null ? "" : args[3].trim();
        if (targetServerId.isEmpty()) {
            sender.sendMessage(chatMessage("admin-setserver-usage", "&c用法: &7/claim admin setserver <claimId> <serverId>"));
            return true;
        }
        Claim claim = claimService.updateClaimServerId(claimId, targetServerId).orElse(null);
        if (claim == null) {
            sender.sendMessage(chatMessage(
                "claim-server-update-failed",
                "&c&l! &7找不到 ID 为 &e{id} &7的领地，或 server_id 更新失败。",
                "{id}", String.valueOf(claimId)
            ));
            return true;
        }
        sender.sendMessage(chatMessage(
            "claim-server-updated",
            "&a&l修复: &7已将领地 &b{name} &7(#&f{id}&7) 的 server_id 更新为 &e{server}&7。",
            "{id}", String.valueOf(claim.id()),
            "{name}", claim.name(),
            "{server}", claimService.displayServerId(claim)
        ));
        return true;
    }

    private boolean handleEdit(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(plugin.message("player-only"));
            return true;
        }
        if (!hasAdminClaimManagePermission(player)) {
            player.sendMessage(plugin.message("no-permission"));
            return true;
        }

        Claim claim;
        if (args.length >= 2) {
            claim = resolveAdminClaimByName(player, joinArgs(args, 1));
            if (claim == null) {
                return true;
            }
        } else {
            claim = claimService.findClaim(player.getLocation()).orElse(null);
            if (claim == null) {
                player.sendMessage(plugin.message("claim-not-found"));
                return true;
            }
        }

        menuService.openCoreMenu(player, claim);
        player.sendMessage(chatMessage("admin-edit-opened", "&a&l管理员: &7已打开领地 &e{name} &7的编辑菜单。", "{name}", claim.name()));
        return true;
    }

    private boolean handleGlobalAdd(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(plugin.message("player-only"));
            return true;
        }
        if (args.length < 2) {
            player.sendMessage(plugin.message("global-add-usage"));
            return true;
        }
        OfflinePlayer target = resolveKnownPlayer(args[1]);
        if (target == null) {
            player.sendMessage(plugin.message("trust-no-target"));
            return true;
        }
        if (target.getUniqueId().equals(player.getUniqueId())) {
            player.sendMessage(plugin.message("trust-self"));
            return true;
        }
        profileService.getOrCreate(player.getUniqueId(), player.getName());
        if (!profileService.addGlobalTrustedMember(player.getUniqueId(), target.getUniqueId())) {
            player.sendMessage(plugin.message("global-add-exists", "{player}", displayName(target)));
            return true;
        }
        player.sendMessage(plugin.message("global-add-success", "{player}", displayName(target)));
        Player onlineTarget = Bukkit.getPlayer(target.getUniqueId());
        if (onlineTarget != null) {
            onlineTarget.sendMessage(plugin.message("global-add-notify", "{owner}", player.getName()));
        }
        return true;
    }

    private boolean handleGlobalRemove(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(plugin.message("player-only"));
            return true;
        }
        if (args.length < 2) {
            player.sendMessage(plugin.message("global-remove-usage"));
            return true;
        }
        OfflinePlayer target = resolveKnownPlayer(args[1]);
        if (target == null) {
            player.sendMessage(plugin.message("trust-no-target"));
            return true;
        }
        profileService.getOrCreate(player.getUniqueId(), player.getName());
        if (!profileService.removeGlobalTrustedMember(player.getUniqueId(), target.getUniqueId())) {
            player.sendMessage(plugin.message("global-remove-missing", "{player}", displayName(target)));
            return true;
        }
        player.sendMessage(plugin.message("global-remove-success", "{player}", displayName(target)));
        return true;
    }

    private boolean handleTransfer(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(plugin.message("player-only"));
            return true;
        }
        if (!player.hasPermission("coreclaim.transfer")) {
            player.sendMessage(plugin.message("no-permission"));
            return true;
        }
        if (args.length < 2) {
            player.sendMessage(plugin.message("transfer-usage"));
            return true;
        }
        if (args.length == 2 && args[1].equalsIgnoreCase("accept")) {
            claimTransferService.accept(player);
            return true;
        }
        if (args.length == 2 && (args[1].equalsIgnoreCase("deny") || args[1].equalsIgnoreCase("cancel"))) {
            claimTransferService.deny(player);
            return true;
        }

        Claim claim;
        String targetName;
        if (args.length == 2) {
            claim = claimActionService.findOwnedClaim(player);
            if (claim == null) {
                player.sendMessage(plugin.message("claim-not-found"));
                return true;
            }
            targetName = args[1];
        } else {
            String claimName = joinArgs(args, 1, args.length - 1);
            if (claimName.isBlank()) {
                player.sendMessage(plugin.message("transfer-usage"));
                return true;
            }
            claim = resolveOwnedClaimByName(player, claimName);
            if (claim == null) {
                return true;
            }
            targetName = args[args.length - 1];
        }

        Player target = resolveOnlinePlayer(targetName);
        claimTransferService.requestTransfer(player, claim, target);
        return true;
    }

    private boolean handleActivity(CommandSender sender, String[] args) {
        if (!hasAdminActivityManagePermission(sender)) {
            sender.sendMessage(plugin.message("no-permission"));
            return true;
        }
        if (args.length < 3) {
            sender.sendMessage(plugin.message("activity-usage"));
            return true;
        }

        String action = args[1].toLowerCase(Locale.ROOT);
        OfflinePlayer target = resolveKnownPlayer(args[2]);
        if (target == null) {
            sender.sendMessage(plugin.message("activity-player-unknown"));
            return true;
        }
        String name = displayName(target);
        PlayerProfile profile = profileService.getOrCreate(target.getUniqueId(), name);

        switch (action) {
            case "get" -> sender.sendMessage(plugin.color("&6[Claim] &f" + profile.lastKnownName()
                + " 的活跃度为 &e" + profile.activityPoints()));
            case "set" -> {
                if (args.length < 4) {
                    sender.sendMessage(plugin.message("activity-value-missing"));
                    return true;
                }
                int value = parsePositiveInt(args[3], sender);
                if (value < 0) {
                    return true;
                }
                profile.setActivityPoints(value);
                profileService.saveProfile(profile);
                sender.sendMessage(plugin.message("activity-set", "{player}", profile.lastKnownName(), "{value}", String.valueOf(value)));
            }
            case "add" -> {
                if (args.length < 4) {
                    sender.sendMessage(plugin.message("activity-value-missing"));
                    return true;
                }
                int value = parsePositiveInt(args[3], sender);
                if (value < 0) {
                    return true;
                }
                profile.setActivityPoints(profile.activityPoints() + value);
                profileService.saveProfile(profile);
                sender.sendMessage(plugin.message("activity-add", "{player}", profile.lastKnownName(), "{value}", String.valueOf(value)));
            }
            case "take" -> {
                if (args.length < 4) {
                    sender.sendMessage(plugin.message("activity-value-missing"));
                    return true;
                }
                int value = parsePositiveInt(args[3], sender);
                if (value < 0) {
                    return true;
                }
                profile.setActivityPoints(Math.max(0, profile.activityPoints() - value));
                profileService.saveProfile(profile);
                sender.sendMessage(plugin.message("activity-take", "{player}", profile.lastKnownName(), "{value}", String.valueOf(value)));
            }
            default -> sender.sendMessage(plugin.message("activity-usage"));
        }
        return true;
    }

    private boolean handleReload(CommandSender sender) {
        if (!hasAdminRewardPermission(sender)) {
            sender.sendMessage(plugin.message("no-permission"));
            return true;
        }
        try {
            int claimCount = plugin.reloadPluginResources();
            sender.sendMessage(plugin.message("reload-success", "{claims}", String.valueOf(claimCount)));
        } catch (Exception exception) {
            sender.sendMessage(plugin.message("reload-failed", "{error}", exception.getMessage() == null ? exception.getClass().getSimpleName() : exception.getMessage()));
        }
        return true;
    }

    private boolean handleGiveCore(CommandSender sender, String[] args) {
        if (!hasAdminOpsPermission(sender)) {
            sender.sendMessage(plugin.message("no-permission"));
            return true;
        }
        if (args.length < 2) {
            sender.sendMessage(plugin.message("givecore-usage"));
            return true;
        }

        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            sender.sendMessage(plugin.message("target-must-online"));
            return true;
        }

        int amount = 1;
        if (args.length >= 3) {
            amount = parsePositiveInt(args[2], sender);
            if (amount < 0) {
                return true;
            }
        }

        plugin.claimCoreFactory().giveClaimCore(target, amount);
        sender.sendMessage(plugin.message("claim-core-given", "{player}", target.getName(), "{amount}", String.valueOf(amount)));
        return true;
    }

    private int parsePositiveInt(String raw, CommandSender sender) {
        try {
            int value = Integer.parseInt(raw);
            if (value < 0) {
                sender.sendMessage(plugin.message("number-non-negative"));
                return -1;
            }
            return value;
        } catch (NumberFormatException exception) {
            sender.sendMessage(plugin.message("number-invalid"));
            return -1;
        }
    }

    private int parseClaimId(String raw, CommandSender sender) {
        try {
            int value = Integer.parseInt(raw);
            if (value <= 0) {
                sender.sendMessage(chatMessage(
                    "claim-id-invalid",
                    "&c&l! &7领地 ID &e{value} &7无效，请输入大于 0 的数字。",
                    "{value}", raw
                ));
                return -1;
            }
            return value;
        } catch (NumberFormatException exception) {
            sender.sendMessage(chatMessage(
                "claim-id-invalid",
                "&c&l! &7领地 ID &e{value} &7无效，请输入大于 0 的数字。",
                "{value}", raw
            ));
            return -1;
        }
    }

    private double parsePositiveDouble(String raw, CommandSender sender) {
        try {
            double value = Double.parseDouble(raw);
            if (!Double.isFinite(value) || value <= 0D) {
                sender.sendMessage(plugin.message("sale-price-invalid"));
                return -1D;
            }
            return value;
        } catch (NumberFormatException exception) {
            sender.sendMessage(plugin.message("sale-price-invalid"));
            return -1D;
        }
    }

    private String displayName(OfflinePlayer player) {
        return player.getName() == null ? player.getUniqueId().toString() : player.getName();
    }

    private Claim resolveCurrentAdminClaim(Player player, String usageLabel) {
        Claim claim = claimActionService.findCurrentClaim(player);
        if (claim == null) {
            player.sendMessage(plugin.color("&c你必须站在一块可管理的领地内才能使用 " + usageLabel));
            return null;
        }
        if (!claimActionService.canEditClaim(player, claim)) {
            player.sendMessage(plugin.message("trust-no-permission"));
            return null;
        }
        return claim;
    }
    private boolean hasAnyAdminPermission(CommandSender sender) {
        return hasAdminViewPermission(sender)
            || hasAdminForcePermission(sender)
            || hasAdminOpsPermission(sender)
            || hasAdminCreateSystemPermission(sender)
            || hasAdminMemberManagePermission(sender)
            || hasAdminPermissionManagePermission(sender)
            || hasAdminFlagManagePermission(sender)
            || hasAdminClaimManagePermission(sender)
            || hasAdminActivityManagePermission(sender)
            || hasAdminRewardPermission(sender);
    }

    private boolean hasAdminViewPermission(CommandSender sender) {
        return sender.hasPermission("coreclaim.admin") || sender.hasPermission("coreclaim.admin.view");
    }

    private boolean hasAdminForcePermission(CommandSender sender) {
        return sender.hasPermission("coreclaim.admin") || sender.hasPermission("coreclaim.admin.force");
    }

    private boolean hasAdminOpsPermission(CommandSender sender) {
        return sender.hasPermission("coreclaim.admin") || sender.hasPermission("coreclaim.admin.ops");
    }

    private boolean hasAdminCreateSystemPermission(CommandSender sender) {
        return hasAdminForcePermission(sender)
            || sender.hasPermission("coreclaim.admin.create.system")
            || sender.hasPermission("coreclaim.admin.claim.manage");
    }

    private boolean hasAdminMemberManagePermission(CommandSender sender) {
        return hasAdminForcePermission(sender) || sender.hasPermission("coreclaim.admin.member.manage");
    }

    private boolean hasAdminPermissionManagePermission(CommandSender sender) {
        return hasAdminForcePermission(sender) || sender.hasPermission("coreclaim.admin.permission.manage");
    }

    private boolean hasAdminFlagManagePermission(CommandSender sender) {
        return hasAdminForcePermission(sender) || sender.hasPermission("coreclaim.admin.flag.manage");
    }

    private boolean hasAdminClaimManagePermission(CommandSender sender) {
        return hasAdminForcePermission(sender) || sender.hasPermission("coreclaim.admin.claim.manage");
    }

    private boolean hasAdminActivityManagePermission(CommandSender sender) {
        return hasAdminOpsPermission(sender) || sender.hasPermission("coreclaim.admin.activity.manage");
    }

    private boolean hasAdminRewardPermission(CommandSender sender) {
        return hasAdminOpsPermission(sender) || sender.hasPermission("coreclaim.admin.reward.givecore");
    }

    private boolean hasManageDenyPermission(CommandSender sender) {
        return sender.hasPermission("coreclaim.manage.deny") || hasAdminForcePermission(sender);
    }

    private boolean hasManageTeleportPermission(CommandSender sender) {
        return sender.hasPermission("coreclaim.manage.tpset") || hasAdminForcePermission(sender);
    }

    private Claim resolveCurrentEditableClaim(Player player, String usageLabel) {
        Claim claim = claimActionService.findCurrentClaim(player);
        if (claim == null) {
            player.sendMessage(plugin.color("&c你必须站在一块可编辑的领地内才能使用 " + usageLabel));
            return null;
        }
        if (!claimActionService.canEditClaim(player, claim)) {
            player.sendMessage(plugin.message("trust-no-permission"));
            return null;
        }
        return claim;
    }

    private String formatTeleportPoint(Claim claim) {
        if (claim == null || !claim.hasTeleportPoint()) {
            return "-";
        }
        return trimDouble(claim.teleportX()) + ", " + trimDouble(claim.teleportY()) + ", " + trimDouble(claim.teleportZ());
    }

    private String ruleSourceSummary(Claim claim) {
        String profileName = claimService.ruleProfileName(claim);
        return claimService.hasManualRuleOverrides(claim)
            ? "&e" + profileName + " &8+ &6手动调整"
            : "&a" + profileName + " &7(默认生效)";
    }

    private String formatYawPitch(Float value) {
        return value == null ? "-" : trimDouble((double) value);
    }

    private String trimDouble(Double value) {
        if (value == null) {
            return "-";
        }
        double rounded = Math.round(value * 100.0D) / 100.0D;
        if (Math.abs(rounded - Math.rint(rounded)) < 0.00001D) {
            return String.valueOf((long) Math.rint(rounded));
        }
        return String.valueOf(rounded);
    }

    private void sendClaimDetails(CommandSender sender, Claim claim, boolean adminView) {
        if (claim != null) {
            boolean canSeeSensitive = adminView;
            if (!canSeeSensitive && sender instanceof Player player) {
                canSeeSensitive = claim.owner().equals(player.getUniqueId()) || hasAdminViewPermission(player);
            }

            if (adminView) {
                sender.sendMessage(plugin.color("&6[Claim] &fClaim ID: &e" + claim.id()));
                sender.sendMessage(plugin.color("&6[Claim] &fServer ID: &e" + claimService.displayServerId(claim)));
            }
            sender.sendMessage(plugin.color("&6[Claim] &f领地名称: &e" + claim.name()));
            sender.sendMessage(plugin.color("&6[Claim] &f领地主人: &b" + claim.ownerName()));
            sender.sendMessage(plugin.color("&6[Claim] &f所在世界: &e" + claim.world()));
            sender.sendMessage(plugin.color("&6[Claim] &f核心坐标: &f" + claim.centerX() + ", " + claim.centerY() + ", " + claim.centerZ()));
            sender.sendMessage(plugin.color("&6[Claim] &f领地大小: &e" + claim.width() + "x" + claim.depth() + " &7(面积 " + claim.area() + ")"));
            sender.sendMessage(plugin.color("&6[Claim] &f边界范围: &7X " + claim.minX() + " ~ " + claim.maxX() + " &8| &7Z " + claim.minZ() + " ~ " + claim.maxZ()));
            sender.sendMessage(plugin.color("&6[Claim] &f高度模式: " + (claim.fullHeight()
                ? "&a全高度保护"
                : "&b选区高度 &7(Y " + claim.minY() + " ~ " + claim.maxY() + ", 高 " + claim.height() + ")")));
            sender.sendMessage(plugin.color("&6[Claim] &f传送点: " + (claim.hasTeleportPoint()
                ? "&a自定义 &7(" + formatTeleportPoint(claim) + ")"
                : "&e核心")));
            sender.sendMessage(plugin.color("&6[Claim] &fDeny 状态: &e" + claim.deniedMembers().size()
                + " &7人 &8| &fdeny *: " + (claim.denyAll() ? "&c开启" : "&a关闭")));
            sender.sendMessage(plugin.color("&6[Claim] &f默认权限: &7放置 " + stateText(claim.permission(ClaimPermission.PLACE))
                + " &8| &7破坏 " + stateText(claim.permission(ClaimPermission.BREAK))
                + " &8| &7交互 " + stateText(claim.permission(ClaimPermission.INTERACT))
                + " &8| &7传送 " + stateText(claim.permission(ClaimPermission.TELEPORT))));
            sender.sendMessage(plugin.color("&6[Claim] &f核心显示: " + (claim.coreVisible() ? "&a显示中" : "&c已隐藏")));
            sender.sendMessage(plugin.color("&6[Claim] &f进入提示: &b" + previewMessage(claim.enterMessage(), claim, "默认进入提示")));
            sender.sendMessage(plugin.color("&6[Claim] &f离开提示: &e" + previewMessage(claim.leaveMessage(), claim, "默认离开提示")));
            if (canSeeSensitive) {
                sender.sendMessage(plugin.color("&6[Claim] &f成员列表: " + joinPlayerNames(claim.trustedMembers())));
                sender.sendMessage(plugin.color("&6[Claim] &fDeny 玩家: " + joinPlayerNames(claim.deniedMembers())));
            }
            if (adminView && claim.hasTeleportPoint()) {
                sender.sendMessage(plugin.color("&6[Claim] &fTP Yaw/Pitch: &e" + formatYawPitch(claim.teleportYaw())
                    + " &8/ &e" + formatYawPitch(claim.teleportPitch())));
            }
            return;
        }

        if (adminView) {
            sender.sendMessage(plugin.color("&6[Claim] &fClaim ID: &e" + claim.id()));
            sender.sendMessage(plugin.color("&6[Claim] &fServer ID: &e" + claimService.displayServerId(claim)));
        }
        sender.sendMessage(plugin.color("&6[Claim] &f领地名称: &e" + claim.name()));
        sender.sendMessage(plugin.color("&6[Claim] &f领地主人: &b" + claim.ownerName()));
        sender.sendMessage(plugin.color("&6[Claim] &f所在世界: &e" + claim.world()));
        sender.sendMessage(plugin.color("&6[Claim] &f核心坐标: &f" + claim.centerX() + ", " + claim.centerY() + ", " + claim.centerZ()));
        sender.sendMessage(plugin.color("&6[Claim] &f领地大小: &e" + claim.width() + "x" + claim.depth() + " &7(面积 " + claim.area() + ")"));
        sender.sendMessage(plugin.color("&6[Claim] &f边界范围: &7X " + claim.minX() + " ~ " + claim.maxX() + " &8| &7Z " + claim.minZ() + " ~ " + claim.maxZ()));
        if (claim.fullHeight()) {
            sender.sendMessage(plugin.color("&6[Claim] &f高度模式: &a全高度保护"));
        } else {
            sender.sendMessage(plugin.color("&6[Claim] &f高度模式: &b选区高度 &7(Y " + claim.minY() + " ~ " + claim.maxY() + ", 高 " + claim.height() + ")"));
        }
        sender.sendMessage(plugin.color("&6[Claim] &f成员人数: &e" + claim.trustedCount() + " &8| &fDeny 玩家: &c" + claim.deniedMembers().size()));
        sender.sendMessage(plugin.color("&6[Claim] &f核心显示: " + (claim.coreVisible() ? "&a显示中" : "&c已隐藏")));
        sender.sendMessage(plugin.color("&6[Claim] &f进入提示: &b" + previewMessage(claim.enterMessage(), claim, "默认进入提示")));
        sender.sendMessage(plugin.color("&6[Claim] &f离开提示: &e" + previewMessage(claim.leaveMessage(), claim, "默认离开提示")));
        if (adminView) {
            sender.sendMessage(plugin.color("&6[Claim] &f默认权限明细:"));
            sender.sendMessage(plugin.color("&7- 放置: " + stateText(claim.permission(com.coreclaim.model.ClaimPermission.PLACE))));
            sender.sendMessage(plugin.color("&7- 破坏: " + stateText(claim.permission(com.coreclaim.model.ClaimPermission.BREAK))));
            sender.sendMessage(plugin.color("&7- 交互: " + stateText(claim.permission(com.coreclaim.model.ClaimPermission.INTERACT))));
            sender.sendMessage(plugin.color("&7- 容器: " + stateText(claim.permission(com.coreclaim.model.ClaimPermission.CONTAINER))));
            sender.sendMessage(plugin.color("&7- 红石: " + stateText(claim.permission(com.coreclaim.model.ClaimPermission.REDSTONE))));
            sender.sendMessage(plugin.color("&7- 爆炸: " + stateText(claim.permission(com.coreclaim.model.ClaimPermission.EXPLOSION))));
            sender.sendMessage(plugin.color("&7- 桶: " + stateText(claim.permission(com.coreclaim.model.ClaimPermission.BUCKET))));
            sender.sendMessage(plugin.color("&7- 传送: " + stateText(claim.permission(com.coreclaim.model.ClaimPermission.TELEPORT))));
            sender.sendMessage(plugin.color("&6[Claim] &f\u6388\u6743\u73a9\u5bb6: " + joinPlayerNames(claim.trustedMembers())));
            sender.sendMessage(plugin.color("&6[Claim] &fDeny 玩家: " + joinPlayerNames(claim.deniedMembers())));
        }
    }

    private void sendEnhancedClaimDetails(CommandSender sender, Claim claim, boolean adminView) {
        boolean canSeeSensitive = adminView;
        if (!canSeeSensitive && sender instanceof Player player) {
            canSeeSensitive = claim.owner().equals(player.getUniqueId()) || hasAdminViewPermission(player);
        }

        if (adminView) {
            sender.sendMessage(plugin.color("&6[Claim] &fClaim ID: &e" + claim.id()));
            sender.sendMessage(plugin.color("&6[Claim] &fServer ID: &e" + claimService.displayServerId(claim)));
            sender.sendMessage(plugin.color("&6[Claim] &fSystem Managed: " + (claim.systemManaged() ? "&6Yes" : "&7No")));
            sender.sendMessage(plugin.color("&6[Claim] &fCounts Toward Quota: " + (claimService.countsTowardQuota(claim) ? "&aYes" : "&cNo")));
        }
        sender.sendMessage(plugin.color("&6[Claim] &f领地名称: &e" + (claim.systemManaged() ? "[SYSTEM] " : "") + claim.name()));
        sender.sendMessage(plugin.color("&6[Claim] &f领地主人: &b" + claim.ownerName()));
        sender.sendMessage(plugin.color("&6[Claim] &f所在世界: &e" + claim.world()));
        sender.sendMessage(plugin.color("&6[Claim] &f核心坐标: &f" + claim.centerX() + ", " + claim.centerY() + ", " + claim.centerZ()));
        sender.sendMessage(plugin.color("&6[Claim] &f领地大小: &e" + claim.width() + "x" + claim.depth() + " &7(面积 " + claim.area() + ")"));
        sender.sendMessage(plugin.color("&6[Claim] &f边界范围: &7X " + claim.minX() + " ~ " + claim.maxX() + " &8| &7Z " + claim.minZ() + " ~ " + claim.maxZ()));
        sender.sendMessage(plugin.color("&6[Claim] &f高度模式: " + (claim.fullHeight()
            ? "&a全高度保护"
            : "&b选区高度 &7(Y " + claim.minY() + " ~ " + claim.maxY() + ", 高 " + claim.height() + ")")));
        sender.sendMessage(plugin.color("&6[Claim] &f传送点: " + (claim.hasTeleportPoint()
            ? "&a自定义 &7(" + formatTeleportPoint(claim) + ")"
            : "&e核心")));
        sender.sendMessage(plugin.color("&6[Claim] &fdeny 状态: &c" + claim.deniedMembers().size()
            + " &7人 &8| &fdeny *: " + (claim.denyAll() ? "&c开启" : "&a关闭")));
        sender.sendMessage(plugin.color("&6[Claim] &f规则来源: " + ruleSourceSummary(claim)));
        sender.sendMessage(plugin.color("&6[Claim] &f默认权限: &7放置 " + stateText(claim.permission(ClaimPermission.PLACE))
            + " &8| &7破坏 " + stateText(claim.permission(ClaimPermission.BREAK))
            + " &8| &7交互 " + stateText(claim.permission(ClaimPermission.INTERACT))
            + " &8| &7传送 " + stateText(claim.permission(ClaimPermission.TELEPORT))
            + " &8| &7飞行 " + stateText(claim.permission(ClaimPermission.FLIGHT))));
        sender.sendMessage(plugin.color("&6[Claim] &f交互旗标: " + summarizeFlags(claim)));
        sender.sendMessage(plugin.color("&6[Claim] &f核心显示: " + (claim.coreVisible() ? "&a显示中" : "&c已隐藏")));
        sender.sendMessage(plugin.color("&6[Claim] &f进入提示: &b" + previewMessage(claim.enterMessage(), claim, "默认进入提示")));
        sender.sendMessage(plugin.color("&6[Claim] &f离开提示: &e" + previewMessage(claim.leaveMessage(), claim, "默认离开提示")));
        if (canSeeSensitive) {
            sender.sendMessage(plugin.color("&6[Claim] &f成员列表: " + joinPlayerNames(claim.trustedMembers())));
            sender.sendMessage(plugin.color("&6[Claim] &fDenied 玩家: " + joinPlayerNames(claim.deniedMembers())));
        }
        if (adminView && claim.hasTeleportPoint()) {
            sender.sendMessage(plugin.color("&6[Claim] &fTP Yaw/Pitch: &e" + formatYawPitch(claim.teleportYaw())
                + " &8/ &e" + formatYawPitch(claim.teleportPitch())));
        }
    }

    private void sendFlagSummary(CommandSender sender, Claim claim) {
        sender.sendMessage(plugin.color("&6[Claim] &f交互旗标 - &e" + claim.name()));
        sender.sendMessage(plugin.color("&7- container: " + flagStateText(claim.flagState(ClaimFlag.CONTAINER))));
        sender.sendMessage(plugin.color("&7- use-button: " + flagStateText(claim.flagState(ClaimFlag.USE_BUTTON))));
        sender.sendMessage(plugin.color("&7- use-lever: " + flagStateText(claim.flagState(ClaimFlag.USE_LEVER))));
        sender.sendMessage(plugin.color("&7- use-pressure-plate: " + flagStateText(claim.flagState(ClaimFlag.USE_PRESSURE_PLATE))));
        sender.sendMessage(plugin.color("&7- use-door: " + flagStateText(claim.flagState(ClaimFlag.USE_DOOR))));
        sender.sendMessage(plugin.color("&7- use-trapdoor: " + flagStateText(claim.flagState(ClaimFlag.USE_TRAPDOOR))));
        sender.sendMessage(plugin.color("&7- use-fence-gate: " + flagStateText(claim.flagState(ClaimFlag.USE_FENCE_GATE))));
        sender.sendMessage(plugin.color("&7- use-bed: " + flagStateText(claim.flagState(ClaimFlag.USE_BED))));
    }

    private String summarizeFlags(Claim claim) {
        List<String> summary = new ArrayList<>();
        for (ClaimFlag flag : ClaimFlag.values()) {
            ClaimFlagState state = claim.flagState(flag);
            if (state == ClaimFlagState.UNSET) {
                continue;
            }
            summary.add("&b" + flag.key() + "&7=" + flagStateText(state));
        }
        return summary.isEmpty() ? "&7未设置" : String.join(plugin.color("&8, "), summary);
    }

    private String flagStateText(ClaimFlagState state) {
        return switch (state) {
            case ALLOW -> "&aALLOW";
            case DENY -> "&cDENY";
            case UNSET -> "&7UNSET";
        };
    }

    private String previewMessage(String raw, Claim claim, String fallback) {
        return (raw == null || raw.isBlank() ? fallback : raw)
            .replace("%claim_name%", claim.name())
            .replace("{claim_name}", claim.name())
            .replace("%owner%", claim.ownerName())
            .replace("{owner}", claim.ownerName())
            .replace("{name}", claim.name());
    }

    private String chatMessage(String path, String fallback, String... replacements) {
        String prefix = plugin.messagesConfig().getString("prefix", "&6[CoreClaim] &f");
        String body = plugin.messagesConfig().getString(path, fallback);
        String message = plugin.color(prefix + body);
        for (int index = 0; index + 1 < replacements.length; index += 2) {
            message = message.replace(replacements[index], replacements[index + 1]);
        }
        return message;
    }

    private String stateText(boolean enabled) {
        return enabled ? "&a允许" : "&c禁止";
    }

    private String joinPlayerNames(java.util.Set<java.util.UUID> players) {
        if (players == null || players.isEmpty()) {
            return "&7无";
        }
        List<String> names = new ArrayList<>();
        for (java.util.UUID playerId : players) {
            names.add("&e" + displayName(Bukkit.getOfflinePlayer(playerId)));
        }
        return String.join(plugin.color("&7, "), names);
    }

    private Claim resolveOwnedClaimByName(Player player, String rawName) {
        return resolveClaimByName(
            player,
            rawName,
            claim -> claim.owner().equals(player.getUniqueId())
        );
    }

    private Claim resolveAccessibleClaimByName(Player player, String rawName) {
        return resolveClaimByName(
            player,
            rawName,
            claim -> hasAdminForcePermission(player)
                || claim.owner().equals(player.getUniqueId())
                || claimService.canAccess(claim, player.getUniqueId())
        );
    }

    private Claim resolveTeleportClaimByName(Player player, String rawName) {
        return resolveClaimByName(
            player,
            rawName,
            claim -> hasAdminForcePermission(player)
                || claimService.hasPermission(claim, player.getUniqueId(), ClaimPermission.TELEPORT)
        );
    }

    private Claim resolveAdminClaimByName(CommandSender sender, String rawName) {
        return resolveClaimByName(sender, rawName, claim -> true);
    }

    private Claim resolveAdminClaimSelector(CommandSender sender, String rawSelector) {
        String selector = normalizeQuery(rawSelector);
        if (selector == null) {
            sender.sendMessage(plugin.message("claim-not-found"));
            return null;
        }
        String numeric = selector.startsWith("#") ? selector.substring(1) : selector;
        if (numeric.chars().allMatch(Character::isDigit)) {
            int claimId = parseClaimId(numeric, sender);
            if (claimId <= 0) {
                return null;
            }
            Claim claim = claimService.findClaimByIdFresh(claimId).orElse(null);
            if (claim == null) {
                sender.sendMessage(plugin.message("claim-not-found"));
            }
            return claim;
        }
        return resolveAdminClaimByName(sender, selector);
    }

    private Claim resolveClaimByName(CommandSender sender, String rawName, java.util.function.Predicate<Claim> filter) {
        String claimName = normalizeQuery(rawName);
        if (claimName == null) {
            sender.sendMessage(plugin.message("claim-name-not-found", "{name}", rawName == null ? "" : rawName.trim()));
            return null;
        }
        List<Claim> matches = claimService.findClaimsByNameFresh(claimName).stream()
            .filter(filter)
            .toList();
        if (matches.isEmpty()) {
            sender.sendMessage(plugin.message("claim-name-not-found", "{name}", claimName));
            return null;
        }
        if (matches.size() > 1) {
            sender.sendMessage(plugin.message("claim-name-ambiguous", "{name}", claimName));
            for (Claim match : matches) {
                sender.sendMessage(plugin.color(
                    "&7- &f#" + match.id() + " &e" + match.name()
                        + " &8@ &b" + claimService.displayServerId(match)
                        + " &7[" + match.world() + " "
                        + match.centerX() + ", " + match.centerZ() + "]"
                ));
            }
            return null;
        }
        return matches.get(0);
    }

    private String joinArgs(String[] args, int startInclusive) {
        return joinArgs(args, startInclusive, args.length);
    }

    private String joinArgs(String[] args, int startInclusive, int endExclusive) {
        if (args == null || startInclusive >= endExclusive || startInclusive < 0 || endExclusive > args.length) {
            return "";
        }
        return String.join(" ", java.util.Arrays.copyOfRange(args, startInclusive, endExclusive)).trim();
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
        if (rawValue == null || rawValue.isBlank()) {
            return null;
        }
        String normalized = rawValue.trim().toLowerCase(Locale.ROOT).replace('-', '_');
        return switch (normalized) {
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
        if (rawValue == null || rawValue.isBlank()) {
            return null;
        }
        String normalized = rawValue.trim().toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "allow", "on", "true", "yes" -> true;
            case "deny", "off", "false", "no" -> false;
            default -> null;
        };
    }

    private OfflinePlayer resolveKnownPlayer(String rawName) {
        if (rawName == null || rawName.isBlank()) {
            return null;
        }
        Player online = Bukkit.getPlayerExact(rawName);
        if (online != null) {
            return online;
        }
        UUID playerId = profileService.findPlayerIdByName(rawName);
        return playerId == null ? null : Bukkit.getOfflinePlayer(playerId);
    }

    private Player resolveOnlinePlayer(String rawName) {
        if (rawName == null || rawName.isBlank()) {
            return null;
        }
        Player exact = Bukkit.getPlayerExact(rawName);
        if (exact != null) {
            return exact;
        }
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (online.getName().equalsIgnoreCase(rawName)) {
                return online;
            }
        }
        return null;
    }

    private List<String> knownPlayerNames() {
        LinkedHashSet<String> names = new LinkedHashSet<>();
        for (Player online : Bukkit.getOnlinePlayers()) {
            names.add(online.getName());
        }
        names.addAll(profileService.knownPlayerNames());
        return new ArrayList<>(names);
    }

    private List<String> onlinePlayerNames() {
        List<String> names = new ArrayList<>();
        for (Player online : Bukkit.getOnlinePlayers()) {
            names.add(online.getName());
        }
        return names;
    }

    private List<String> claimIdOptions() {
        return claimService.allClaims().stream()
            .map(claim -> String.valueOf(claim.id()))
            .distinct()
            .toList();
    }

    private void sendModernHelp(CommandSender sender) {
        sender.sendMessage(plugin.color("&6Claim 命令:"));
        sender.sendMessage(plugin.color("&e/claim &7打开领地主菜单"));
        sender.sendMessage(plugin.color("&e/claim info &7查看脚下当前领地详情"));
        sender.sendMessage(plugin.color("&e/claim list &7查看你当前拥有的领地列表"));
        sender.sendMessage(plugin.color("&e/claim menu &7打开领地菜单"));
        sender.sendMessage(plugin.color("&e/claim create <领地名> &7先用普通金锄头左键点 1、右键点 2，再创建"));
        sender.sendMessage(plugin.color("&e/claim tp <领地名> &7传送到你有权限进入的领地"));
        sender.sendMessage(plugin.color("&e/claim tpset &7把脚下位置设为当前领地传送点"));
        sender.sendMessage(plugin.color("&e/claim add <玩家> &7站在当前领地内给这块领地加成员"));
        sender.sendMessage(plugin.color("&e/claim remove <玩家> &7站在当前领地内移除这块领地成员"));
        sender.sendMessage(plugin.color("&e/claim deny <玩家> 或 /claim deny * &7站在当前领地内设置 deny"));
        sender.sendMessage(plugin.color("&e/claim undeny <玩家> 或 /claim undeny * &7站在当前领地内取消 deny"));
        sender.sendMessage(plugin.color("&e/claim flag [list] &7查看或调整脚下领地的交互旗标"));
        sender.sendMessage(plugin.color("&7首块领地：在线 &e" + plugin.settings().starterRewardMinutes() + " &7分钟会自动发新人核心（默认全格保护），也可以直接用普通金锄头选区创建。"));
        if (hasAnyAdminPermission(sender)) {
            sender.sendMessage(plugin.color("&6管理员:"));
            sender.sendMessage(plugin.color("&e/claim admin create system <领地名> &7按当前选区创建系统领地"));
            sender.sendMessage(plugin.color("&e/claim admin info <领地名|#claimId> &7查看领地完整详情"));
            sender.sendMessage(plugin.color("&e/claim admin playerclaims <玩家> &7查看玩家名下全部领地"));
            sender.sendMessage(plugin.color("&e/claim admin diagnose <领地名|#claimId> &7查看跨服与 TP 诊断"));
            sender.sendMessage(plugin.color("&e/claim admin add <玩家> &7站在当前领地内强制添加成员"));
            sender.sendMessage(plugin.color("&e/claim admin remove <玩家> &7站在当前领地内强制移除成员"));
            sender.sendMessage(plugin.color("&e/claim admin deny <玩家> 或 /claim admin deny * &7站在当前领地内强制改 deny"));
            sender.sendMessage(plugin.color("&e/claim admin undeny <玩家> 或 /claim admin undeny * &7站在当前领地内取消 deny"));
            sender.sendMessage(plugin.color("&e/claim admin permission <permission> <allow|deny> &7站在当前领地内强制改默认权限"));
            sender.sendMessage(plugin.color("&e/claim admin flag <flag> <allow|deny|unset> &7站在当前领地内强制改交互旗标"));
            sender.sendMessage(plugin.color("&e/claim admin setserver <claimId> <serverId> &7修复旧领地的跨服 server_id"));
            sender.sendMessage(plugin.color("&e/claim activity <get|set|add|take> <玩家> [值] &7管理活跃度"));
            sender.sendMessage(plugin.color("&e/claim reload &7重载配置与缓存"));
            sender.sendMessage(plugin.color("&e/claim givecore <玩家> [数量] &7手动发放领地核心"));
        }
    }

    private void sendHelp(CommandSender sender) {
        sendModernHelp(sender);
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args == null) {
            return List.of();
        }

        List<String> options = new ArrayList<>();
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
            options.add("remove");
            options.add("deny");
            options.add("undeny");
            if (hasAnyAdminPermission(sender)) {
                options.add("edit");
                options.add("admin");
                options.add("activity");
                options.add("reload");
                options.add("givecore");
            }
            return filter(options, args[0]);
        }

        if (args.length == 2 && args[0].equalsIgnoreCase("expand")) {
            options.add("east");
            options.add("south");
            options.add("west");
            options.add("north");
            return filter(options, args[1]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("givecore")) {
            options.addAll(onlinePlayerNames());
            return filter(options, args[1]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("activity")) {
            options.add("get");
            options.add("set");
            options.add("add");
            options.add("take");
            return filter(options, args[1]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("flag")) {
            options.add("list");
            options.addAll(flagKeys());
            return filter(options, args[1]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("admin") && hasAnyAdminPermission(sender)) {
            options.add("create");
            options.add("info");
            options.add("playerclaims");
            options.add("diagnose");
            options.add("add");
            options.add("remove");
            options.add("deny");
            options.add("undeny");
            options.add("permission");
            options.add("flag");
            options.add("setserver");
            return filter(options, args[1]);
        }
        if (args.length == 2 && (args[0].equalsIgnoreCase("add")
            || args[0].equalsIgnoreCase("remove")
            || args[0].equalsIgnoreCase("transfer"))) {
            options.addAll(knownPlayerNames());
            if (sender instanceof Player player && args[0].equalsIgnoreCase("transfer")) {
                options.add("accept");
                options.add("deny");
                options.addAll(claimNames(claimService.claimsOf(player.getUniqueId())));
            }
            return filter(options, args[1]);
        }
        if (args.length == 2 && (args[0].equalsIgnoreCase("deny") || args[0].equalsIgnoreCase("undeny"))) {
            options.add("*");
            options.addAll(knownPlayerNames());
            return filter(options, args[1]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("edit") && hasAdminClaimManagePermission(sender)) {
            options.addAll(claimNames(claimService.allClaims()));
            return filterByJoinedInput(options, args, 1);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("delete") && sender instanceof Player player) {
            options.addAll(claimNames(claimService.claimsOf(player.getUniqueId())));
            return filterByJoinedInput(options, args, 1);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("show")) {
            options.add("auto");
            if (sender instanceof Player player) {
                options.addAll(claimNames(claimService.allClaims().stream()
                    .filter(claim -> hasAdminForcePermission(player)
                        || claim.owner().equals(player.getUniqueId())
                        || claimService.canAccess(claim, player.getUniqueId()))
                    .toList()));
            } else {
                options.addAll(claimNames(claimService.allClaims()));
            }
            return filterByJoinedInput(options, args, 1);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("tp")) {
            if (sender instanceof Player player) {
                options.addAll(claimNames(claimService.allClaims().stream()
                    .filter(claim -> hasAdminForcePermission(player)
                        || claimService.hasPermission(claim, player.getUniqueId(), ClaimPermission.TELEPORT))
                    .toList()));
            }
            return filterByJoinedInput(options, args, 1);
        }

        if (args.length == 3 && args[0].equalsIgnoreCase("show") && args[1].equalsIgnoreCase("auto")) {
            options.add("on");
            options.add("off");
            return filter(options, args[2]);
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("flag")) {
            options.add("allow");
            options.add("deny");
            options.add("unset");
            return filter(options, args[2]);
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("activity")) {
            options.addAll(knownPlayerNames());
            return filter(options, args[2]);
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("admin") && args[1].equalsIgnoreCase("setserver")) {
            options.addAll(claimIdOptions());
            return filter(options, args[2]);
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("admin") && args[1].equalsIgnoreCase("create")) {
            options.add("system");
            return filter(options, args[2]);
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("admin")
            && (args[1].equalsIgnoreCase("playerclaims") || args[1].equalsIgnoreCase("claims"))) {
            options.addAll(knownPlayerNames());
            return filter(options, args[2]);
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("admin")
            && (args[1].equalsIgnoreCase("add") || args[1].equalsIgnoreCase("remove"))) {
            options.addAll(knownPlayerNames());
            return filter(options, args[2]);
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("admin")
            && (args[1].equalsIgnoreCase("deny") || args[1].equalsIgnoreCase("undeny"))) {
            options.add("*");
            options.addAll(knownPlayerNames());
            return filter(options, args[2]);
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("admin")
            && (args[1].equalsIgnoreCase("permission") || args[1].equalsIgnoreCase("perm"))) {
            options.addAll(permissionKeys());
            return filter(options, args[2]);
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("admin") && args[1].equalsIgnoreCase("flag")) {
            options.add("list");
            options.addAll(flagKeys());
            return filter(options, args[2]);
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("admin")
            && (args[1].equalsIgnoreCase("info")
            || args[1].equalsIgnoreCase("delete")
            || args[1].equalsIgnoreCase("transfer")
            || args[1].equalsIgnoreCase("diagnose"))) {
            options.addAll(claimSelectorOptions(claimService.allClaims()));
            return filterByJoinedInput(options, args, 2);
        }

        if (args.length == 4 && args[0].equalsIgnoreCase("admin")
            && (args[1].equalsIgnoreCase("permission") || args[1].equalsIgnoreCase("perm"))) {
            options.add("allow");
            options.add("deny");
            return filter(options, args[3]);
        }
        if (args.length == 4 && args[0].equalsIgnoreCase("admin") && args[1].equalsIgnoreCase("flag")) {
            options.add("allow");
            options.add("deny");
            options.add("unset");
            return filter(options, args[3]);
        }

        if (args.length > 2 && (args[0].equalsIgnoreCase("show")
            || args[0].equalsIgnoreCase("tp")
            || args[0].equalsIgnoreCase("edit")
            || args[0].equalsIgnoreCase("delete"))) {
            if (args[0].equalsIgnoreCase("show") && args[1].equalsIgnoreCase("auto")) {
                return options;
            }
            if (sender instanceof Player player) {
                if (args[0].equalsIgnoreCase("show")) {
                    options.addAll(claimNames(claimService.allClaims().stream()
                        .filter(claim -> hasAdminForcePermission(player)
                            || claim.owner().equals(player.getUniqueId())
                            || claimService.canAccess(claim, player.getUniqueId()))
                        .toList()));
                } else if (args[0].equalsIgnoreCase("tp")) {
                    options.addAll(claimNames(claimService.allClaims().stream()
                        .filter(claim -> hasAdminForcePermission(player)
                            || claimService.hasPermission(claim, player.getUniqueId(), ClaimPermission.TELEPORT))
                        .toList()));
                } else if (args[0].equalsIgnoreCase("edit") && hasAdminClaimManagePermission(sender)) {
                    options.addAll(claimNames(claimService.allClaims()));
                } else if (args[0].equalsIgnoreCase("delete")) {
                    options.addAll(claimNames(claimService.claimsOf(player.getUniqueId())));
                }
            } else if (args[0].equalsIgnoreCase("edit") && hasAdminClaimManagePermission(sender)) {
                options.addAll(claimNames(claimService.allClaims()));
            }
            return filterByJoinedInput(options, args, 1);
        }
        if (args.length > 3 && args[0].equalsIgnoreCase("admin")
            && (args[1].equalsIgnoreCase("info")
            || args[1].equalsIgnoreCase("delete")
            || args[1].equalsIgnoreCase("transfer")
            || args[1].equalsIgnoreCase("diagnose"))) {
            if (args[1].equalsIgnoreCase("transfer")) {
                String candidateName = joinArgs(args, 2, args.length - 1);
                if (hasUniqueMatchingClaim(claimService.allClaims(), candidateName)) {
                    options.addAll(knownPlayerNames());
                    return filter(options, args[args.length - 1]);
                }
            }
            options.addAll(claimNames(claimService.allClaims()));
            return filterByJoinedInput(options, args, 2);
        }
        if (args.length > 2 && args[0].equalsIgnoreCase("transfer") && sender instanceof Player player) {
            List<Claim> ownedClaims = claimService.claimsOf(player.getUniqueId());
            String candidateName = joinArgs(args, 1, args.length - 1);
            if (hasUniqueMatchingClaim(ownedClaims, candidateName)) {
                options.addAll(knownPlayerNames());
                return filter(options, args[args.length - 1]);
            }
            options.addAll(claimNames(ownedClaims));
            return filterByJoinedInput(options, args, 1);
        }
        return options;
    }
    private List<String> filter(List<String> options, String input) {
        String lowered = input.toLowerCase(Locale.ROOT);
        return options.stream()
            .distinct()
            .filter(option -> option.toLowerCase(Locale.ROOT).startsWith(lowered))
            .toList();
    }

    private List<String> filterByJoinedInput(List<String> options, String[] args, int startInclusive) {
        return filter(options, joinArgs(args, startInclusive));
    }

    private List<String> claimNames(List<Claim> claims) {
        return claims.stream()
            .map(Claim::name)
            .distinct()
            .toList();
    }

    private boolean hasUniqueMatchingClaim(List<Claim> claims, String rawName) {
        List<Claim> matchingClaims = claimService.findClaimsByNameFresh(rawName);
        return claims.stream().filter(matchingClaims::contains).count() == 1;
    }

    private List<String> flagKeys() {
        return Arrays.stream(ClaimFlag.values())
            .map(ClaimFlag::key)
            .toList();
    }

    private List<String> permissionKeys() {
        return Arrays.stream(ClaimPermission.values())
            .map(permission -> permission.name().toLowerCase(Locale.ROOT))
            .toList();
    }

    private List<String> claimSelectorOptions(List<Claim> claims) {
        List<String> selectors = new ArrayList<>();
        for (Claim claim : claims) {
            selectors.add("#" + claim.id());
            selectors.add(claim.name());
        }
        return selectors.stream().distinct().toList();
    }
}
