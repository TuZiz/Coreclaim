package com.coreclaim.command;

import com.coreclaim.CoreClaimPlugin;
import com.coreclaim.config.ClaimGroup;
import com.coreclaim.gui.MenuService;
import com.coreclaim.model.Claim;
import com.coreclaim.model.ClaimDirection;
import com.coreclaim.model.ClaimPermission;
import com.coreclaim.model.PlayerProfile;
import com.coreclaim.service.ClaimActionService;
import com.coreclaim.service.ClaimMarketService;
import com.coreclaim.service.ClaimSelectionService;
import com.coreclaim.service.ClaimVisualService;
import com.coreclaim.service.ClaimService;
import com.coreclaim.service.ClaimTransferService;
import com.coreclaim.service.ProfileService;
import com.coreclaim.service.RemovalConfirmationService;
import java.util.ArrayList;
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
    private final ClaimMarketService claimMarketService;

    public CoreClaimCommand(
        CoreClaimPlugin plugin,
        ClaimService claimService,
        ProfileService profileService,
        ClaimActionService claimActionService,
        ClaimVisualService claimVisualService,
        ClaimSelectionService claimSelectionService,
        MenuService menuService,
        RemovalConfirmationService removalConfirmationService,
        ClaimTransferService claimTransferService,
        ClaimMarketService claimMarketService
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
        this.claimMarketService = claimMarketService;
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
            sendHelp(sender);
            return true;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);
        return switch (sub) {
            case "info" -> handleInfo(sender);
            case "here", "current" -> handleCurrentClaimInfo(sender);
            case "list" -> handleList(sender);
            case "menu", "gui" -> handleMenu(sender);
            case "show" -> handleShow(sender, args);
            case "wand" -> handleWand(sender);
            case "create" -> handleCreate(sender, args);
            case "edit" -> handleEdit(sender, args);
            case "tp", "teleport" -> handleTeleport(sender, args);
            case "expand" -> handleExpand(sender, args);
            case "remove", "delete" -> handleRemove(sender, args);
            case "confirm" -> handleConfirm(sender);
            case "trust" -> handleTrust(sender, args);
            case "untrust" -> handleUntrust(sender, args);
            case "blacklist", "deny" -> handleBlacklist(sender, args);
            case "admin" -> handleAdmin(sender, args);
            case "add" -> handleAdd(sender, args);
            case "unadd" -> handleUnadd(sender, args);
            case "globaladd" -> handleGlobalAdd(sender, args);
            case "globalremove" -> handleGlobalRemove(sender, args);
            case "transfer" -> handleTransfer(sender, args);
            case "sell" -> handleSell(sender, args);
            case "market" -> handleMarket(sender);
            case "starter", "reclaim", "reclaimstarter" -> handleReclaimStarter(sender);
            case "activity" -> handleActivity(sender, args);
            case "reload" -> handleReload(sender);
            case "givecore" -> handleGiveCore(sender, args);
            default -> {
                sendHelp(sender);
                yield true;
            }
        };
    }

    private boolean handleInfo(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(plugin.message("player-only"));
            return true;
        }

        PlayerProfile profile = profileService.getOrCreate(player.getUniqueId(), player.getName());
        ClaimGroup group = plugin.groups().resolve(player);
        int claimCount = claimService.countClaims(player.getUniqueId());
        int maxClaims = group.claimSlotsForActivity(profile.activityPoints());

        player.sendMessage(plugin.color("&6[Claim] &f组别: &e" + plugin.color(group.displayName())));
        player.sendMessage(plugin.color("&6[Claim] &f活跃度: &e" + profile.activityPoints()));
        player.sendMessage(plugin.color("&6[Claim] &f领地数量: &e" + claimCount + "&f/&e" + maxClaims));
        player.sendMessage(plugin.color("&6[Claim] &f累计在线: &e" + profile.onlineMinutes() + " 分钟"));
        player.sendMessage(plugin.color("&6[Claim] &f单次方向扩建: &e" + plugin.settings().directionExpandAmount()
            + " &f格 | 单价: &e" + ClaimActionService.formatMoney(group.expandPricePerBlock())));

        claimService.findClaim(player.getLocation())
            .filter(claim -> claim.owner().equals(player.getUniqueId()))
            .ifPresent(claim -> player.sendMessage(plugin.color("&6[Claim] &f当前领地: &e" + claim.name()
                + " &f大小: &e" + claim.width() + "x" + claim.depth())));
        return true;
    }

    private boolean handleList(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(plugin.message("player-only"));
            return true;
        }

        List<Claim> claims = claimService.claimsOf(player.getUniqueId());
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

        sendClaimDetails(player, claim, false);
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
                .filter(found -> found.owner().equals(player.getUniqueId()) || claimService.canAccess(found, player.getUniqueId()) || player.hasPermission("coreclaim.admin"))
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

    private boolean handleWand(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(plugin.message("player-only"));
            return true;
        }
        claimSelectionService.activate(player);
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

    private boolean handleRemove(CommandSender sender, String[] args) {
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

    private boolean handleTrust(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(plugin.message("player-only"));
            return true;
        }
        if (args.length < 2) {
            player.sendMessage(plugin.message("trust-usage"));
            return true;
        }

        if (args.length == 2) {
            OfflinePlayer target = resolveKnownPlayer(args[1]);
            if (target == null) {
                player.sendMessage(plugin.message("trust-no-target"));
                return true;
            }
            claimActionService.trustCurrentClaim(player, target);
            return true;
        }

        String claimName = joinArgs(args, 1, args.length - 1);
        if (claimName.isBlank()) {
            player.sendMessage(plugin.message("trust-usage"));
            return true;
        }
        Claim claim = resolveOwnedClaimByName(player, claimName);
        if (claim == null) {
            return true;
        }
        OfflinePlayer target = resolveKnownPlayer(args[args.length - 1]);
        if (target == null) {
            player.sendMessage(plugin.message("trust-no-target"));
            return true;
        }
        claimActionService.trustPlayer(player, claim, target);
        return true;
    }

    private boolean handleUntrust(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(plugin.message("player-only"));
            return true;
        }
        if (args.length < 2) {
            player.sendMessage(plugin.message("untrust-usage"));
            return true;
        }

        if (args.length == 2) {
            OfflinePlayer target = resolveKnownPlayer(args[1]);
            if (target == null) {
                player.sendMessage(plugin.message("trust-no-target"));
                return true;
            }
            claimActionService.untrustCurrentClaim(player, target);
            return true;
        }

        String claimName = joinArgs(args, 1, args.length - 1);
        if (claimName.isBlank()) {
            player.sendMessage(plugin.message("untrust-usage"));
            return true;
        }
        Claim claim = resolveOwnedClaimByName(player, claimName);
        if (claim == null) {
            return true;
        }
        OfflinePlayer target = resolveKnownPlayer(args[args.length - 1]);
        if (target == null) {
            player.sendMessage(plugin.message("trust-no-target"));
            return true;
        }
        claimActionService.untrustPlayer(player, claim, target);
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

    private boolean handleUnadd(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(plugin.message("player-only"));
            return true;
        }
        if (args.length != 2) {
            player.sendMessage(plugin.color("&c\u7528\u6cd5: &7/claim unadd <\u73a9\u5bb6>"));
            return true;
        }
        Claim claim = claimActionService.findCurrentClaim(player);
        if (claim == null) {
            player.sendMessage(plugin.color("&c\u4f60\u5fc5\u987b\u7ad9\u5728\u4e00\u5757\u53ef\u7f16\u8f91\u7684\u9886\u5730\u5185\u624d\u80fd\u4f7f\u7528 /claim unadd"));
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

    private boolean handleBlacklist(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(plugin.message("player-only"));
            return true;
        }
        if (args.length < 2) {
            player.sendMessage(plugin.message("blacklist-usage"));
            return true;
        }

        String action = args[1].toLowerCase(Locale.ROOT);
        return switch (action) {
            case "add" -> handleBlacklistAdd(player, args);
            case "remove", "del", "delete" -> handleBlacklistRemove(player, args);
            case "list" -> handleBlacklistList(player, args);
            default -> {
                player.sendMessage(plugin.message("blacklist-usage"));
                yield true;
            }
        };
    }

    private boolean handleBlacklistAdd(Player player, String[] args) {
        Claim claim;
        String targetName;
        if (args.length == 3) {
            claim = claimActionService.findOwnedClaim(player);
            if (claim == null) {
                player.sendMessage(plugin.message("claim-not-found"));
                return true;
            }
            targetName = args[2];
        } else if (args.length >= 4) {
            String claimName = joinArgs(args, 2, args.length - 1);
            if (claimName.isBlank()) {
                player.sendMessage(plugin.message("blacklist-add-usage"));
                return true;
            }
            claim = resolveOwnedClaimByName(player, claimName);
            if (claim == null) {
                return true;
            }
            targetName = args[args.length - 1];
        } else {
            player.sendMessage(plugin.message("blacklist-add-usage"));
            return true;
        }

        OfflinePlayer target = resolveKnownPlayer(targetName);
        if (target == null) {
            player.sendMessage(plugin.message("trust-no-target"));
            return true;
        }
        if (target.getUniqueId().equals(player.getUniqueId())) {
            player.sendMessage(plugin.message("trust-self"));
            return true;
        }
        if (!claimService.addBlacklistedMember(claim, target.getUniqueId())) {
            player.sendMessage(plugin.message("blacklist-add-exists", "{player}", displayName(target), "{name}", claim.name()));
            return true;
        }
        player.sendMessage(plugin.message("blacklist-add-success", "{player}", displayName(target), "{name}", claim.name()));
        Player onlineTarget = Bukkit.getPlayer(target.getUniqueId());
        if (onlineTarget != null) {
            onlineTarget.sendMessage(plugin.message("blacklist-add-notify", "{owner}", player.getName(), "{name}", claim.name()));
        }
        return true;
    }

    private boolean handleBlacklistRemove(Player player, String[] args) {
        Claim claim;
        String targetName;
        if (args.length == 3) {
            claim = claimActionService.findOwnedClaim(player);
            if (claim == null) {
                player.sendMessage(plugin.message("claim-not-found"));
                return true;
            }
            targetName = args[2];
        } else if (args.length >= 4) {
            String claimName = joinArgs(args, 2, args.length - 1);
            if (claimName.isBlank()) {
                player.sendMessage(plugin.message("blacklist-remove-usage"));
                return true;
            }
            claim = resolveOwnedClaimByName(player, claimName);
            if (claim == null) {
                return true;
            }
            targetName = args[args.length - 1];
        } else {
            player.sendMessage(plugin.message("blacklist-remove-usage"));
            return true;
        }

        OfflinePlayer target = resolveKnownPlayer(targetName);
        if (target == null) {
            player.sendMessage(plugin.message("trust-no-target"));
            return true;
        }
        if (!claimService.removeBlacklistedMember(claim, target.getUniqueId())) {
            player.sendMessage(plugin.message("blacklist-remove-missing", "{player}", displayName(target), "{name}", claim.name()));
            return true;
        }
        player.sendMessage(plugin.message("blacklist-remove-success", "{player}", displayName(target), "{name}", claim.name()));
        return true;
    }

    private boolean handleBlacklistList(Player player, String[] args) {
        Claim claim;
        if (args.length == 2) {
            claim = claimActionService.findOwnedClaim(player);
            if (claim == null) {
                player.sendMessage(plugin.message("claim-not-found"));
                return true;
            }
        } else {
            claim = resolveOwnedClaimByName(player, joinArgs(args, 2));
            if (claim == null) {
                return true;
            }
        }

        if (claim.blacklistedMembers().isEmpty()) {
            player.sendMessage(plugin.message("blacklist-list-empty", "{name}", claim.name()));
            return true;
        }

        player.sendMessage(plugin.message("blacklist-list-header", "{name}", claim.name()));
        for (java.util.UUID memberId : claim.blacklistedMembers()) {
            player.sendMessage(plugin.color("&7- &c" + displayName(Bukkit.getOfflinePlayer(memberId))));
        }
        return true;
    }

    private boolean handleAdmin(CommandSender sender, String[] args) {
        if (!sender.hasPermission("coreclaim.admin")) {
            sender.sendMessage(plugin.message("no-permission"));
            return true;
        }
        if (args.length < 2) {
            sender.sendMessage(plugin.message("admin-usage"));
            return true;
        }

        String action = args[1].toLowerCase(Locale.ROOT);
        return switch (action) {
            case "info" -> handleAdminInfo(sender, args);
            case "remove", "delete" -> handleAdminRemove(sender, args);
            case "transfer" -> handleAdminTransfer(sender, args);
            default -> {
                sender.sendMessage(plugin.message("admin-usage"));
                yield true;
            }
        };
    }

    private boolean handleAdminInfo(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(plugin.message("admin-info-usage"));
            return true;
        }

        Claim claim = resolveAdminClaimByName(sender, joinArgs(args, 2));
        if (claim == null) {
            return true;
        }

        sendClaimDetails(sender, claim, true);
        return true;
    }

    private boolean handleAdminRemove(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(plugin.message("admin-remove-usage"));
            return true;
        }

        Claim claim = resolveAdminClaimByName(sender, joinArgs(args, 2));
        if (claim == null) {
            return true;
        }
        return claimActionService.adminRemoveClaim(sender, claim);
    }

    private boolean handleAdminTransfer(CommandSender sender, String[] args) {
        if (args.length < 4) {
            sender.sendMessage(plugin.message("admin-transfer-usage"));
            return true;
        }
        String claimName = joinArgs(args, 2, args.length - 1);
        if (claimName.isBlank()) {
            sender.sendMessage(plugin.message("admin-transfer-usage"));
            return true;
        }
        Claim claim = resolveAdminClaimByName(sender, claimName);
        if (claim == null) {
            return true;
        }
        Player target = resolveOnlinePlayer(args[args.length - 1]);
        claimTransferService.forceTransfer(sender, claim, target);
        return true;
    }

    private boolean handleEdit(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(plugin.message("player-only"));
            return true;
        }
        if (!player.hasPermission("coreclaim.admin")) {
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

    private boolean handleSell(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(plugin.message("player-only"));
            return true;
        }
        if (!player.hasPermission("coreclaim.sell")) {
            player.sendMessage(plugin.message("no-permission"));
            return true;
        }
        if (args.length < 2) {
            player.sendMessage(plugin.message("sell-usage"));
            return true;
        }

        if (args[1].equalsIgnoreCase("cancel")) {
            Claim claim;
            if (args.length == 2) {
                claim = claimActionService.findOwnedClaim(player);
                if (claim == null) {
                    player.sendMessage(plugin.message("claim-not-found"));
                    return true;
                }
            } else {
                claim = resolveOwnedClaimByName(player, joinArgs(args, 2));
                if (claim == null) {
                    return true;
                }
            }
            claimMarketService.cancelListing(player, claim);
            return true;
        }

        double price = parsePositiveDouble(args[args.length - 1], player);
        if (price <= 0D) {
            return true;
        }

        Claim claim;
        if (args.length == 2) {
            claim = claimActionService.findOwnedClaim(player);
            if (claim == null) {
                player.sendMessage(plugin.message("claim-not-found"));
                return true;
            }
        } else {
            String claimName = joinArgs(args, 1, args.length - 1);
            if (claimName.isBlank()) {
                player.sendMessage(plugin.message("sell-usage"));
                return true;
            }
            claim = resolveOwnedClaimByName(player, claimName);
            if (claim == null) {
                return true;
            }
        }
        claimMarketService.listClaim(player, claim, price);
        return true;
    }

    private boolean handleMarket(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(plugin.message("player-only"));
            return true;
        }
        if (!player.hasPermission("coreclaim.market")) {
            player.sendMessage(plugin.message("no-permission"));
            return true;
        }
        menuService.openClaimMarketMenu(player, 0);
        return true;
    }

    private boolean handleActivity(CommandSender sender, String[] args) {
        if (!sender.hasPermission("coreclaim.admin")) {
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
        if (!sender.hasPermission("coreclaim.admin")) {
            sender.sendMessage(plugin.message("no-permission"));
            return true;
        }
        try {
            plugin.reloadPluginResources();
            sender.sendMessage(plugin.message("reload-success"));
        } catch (Exception exception) {
            sender.sendMessage(plugin.message("reload-failed", "{error}", exception.getMessage() == null ? exception.getClass().getSimpleName() : exception.getMessage()));
        }
        return true;
    }

    private boolean handleReclaimStarter(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(plugin.message("player-only"));
            return true;
        }
        if (claimService.countClaims(player.getUniqueId()) > 0) {
            player.sendMessage(chatMessage("starter-core-reclaim-not-needed", "&c&l! &7你已经拥有领地，不需要补领新人核心。"));
            return true;
        }

        PlayerProfile profile = profileService.getOrCreate(player.getUniqueId(), player.getName());
        int requiredMinutes = plugin.settings().starterRewardMinutes();
        int currentMinutes = profile.onlineMinutes();
        if (currentMinutes < requiredMinutes) {
            player.sendMessage(chatMessage(
                "starter-core-reclaim-not-unlocked",
                "&c&l! &7在线满 &e{minutes} &7分钟后才能补领，当前还差 &e{remaining} &7分钟。",
                "{minutes}", String.valueOf(requiredMinutes),
                "{remaining}", String.valueOf(requiredMinutes - currentMinutes)
            ));
            return true;
        }

        if (!profile.starterCoreGranted()) {
            profile.setStarterCoreGranted(true);
            profileService.saveProfile(profile);
        }

        if (plugin.claimCoreFactory().hasStarterCore(player)) {
            player.sendMessage(chatMessage("starter-core-reclaim-existing", "&7你的背包或末影箱里已经有新人核心了。"));
            return true;
        }

        if (profile.starterCoreReclaimed()) {
            player.sendMessage(chatMessage("starter-core-reclaim-used", "&c&l! &7你已经补领过一次新人核心了，无法再次补领。"));
            return true;
        }

        profile.setStarterCoreReclaimed(true);
        profileService.saveProfile(profile);
        plugin.claimCoreFactory().giveStarterCore(player, 1);
        player.sendMessage(chatMessage("starter-core-reclaim-success", "&a&l补领: &7已为你补发 1 个新人核心，请尽快完成第一块领地创建。"));
        return true;
    }

    private boolean handleGiveCore(CommandSender sender, String[] args) {
        if (!sender.hasPermission("coreclaim.admin")) {
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

    private void sendClaimDetails(CommandSender sender, Claim claim, boolean adminView) {
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
        sender.sendMessage(plugin.color("&6[Claim] &f信任人数: &e" + claim.trustedCount() + " &8| &f黑名单: &c" + claim.blacklistedMembers().size()));
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
            sender.sendMessage(plugin.color("&6[Claim] &f黑名单玩家: " + joinPlayerNames(claim.blacklistedMembers())));
        }
    }

    private String previewMessage(String raw, Claim claim, String fallback) {
        return (raw == null || raw.isBlank() ? fallback : raw)
            .replace("%claim_name%", claim.name())
            .replace("{claim_name}", claim.name())
            .replace("%owner%", claim.ownerName())
            .replace("{owner}", claim.ownerName())
            .replace("{name}", claim.name());
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
            claim -> player.hasPermission("coreclaim.admin")
                || claim.owner().equals(player.getUniqueId())
                || claimService.canAccess(claim, player.getUniqueId())
        );
    }

    private Claim resolveTeleportClaimByName(Player player, String rawName) {
        return resolveClaimByName(
            player,
            rawName,
            claim -> player.hasPermission("coreclaim.admin")
                || claimService.hasPermission(claim, player.getUniqueId(), ClaimPermission.TELEPORT)
        );
    }

    private Claim resolveAdminClaimByName(CommandSender sender, String rawName) {
        return resolveClaimByName(sender, rawName, claim -> true);
    }

    private Claim resolveClaimByName(CommandSender sender, String rawName, java.util.function.Predicate<Claim> filter) {
        String claimName = normalizeQuery(rawName);
        if (claimName == null) {
            sender.sendMessage(plugin.message("claim-name-not-found", "{name}", rawName == null ? "" : rawName.trim()));
            return null;
        }
        List<Claim> matches = claimService.findClaimsByName(claimName).stream()
            .filter(filter)
            .toList();
        if (matches.isEmpty()) {
            sender.sendMessage(plugin.message("claim-name-not-found", "{name}", claimName));
            return null;
        }
        if (matches.size() > 1) {
            sender.sendMessage(plugin.message("claim-name-ambiguous", "{name}", claimName));
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

    private void sendHelp(CommandSender sender) {
        sender.sendMessage(plugin.color("&6Claim 命令:"));
        sender.sendMessage(plugin.color("&e/claim info &7查看组别、活跃度和当前领地"));
        sender.sendMessage(plugin.color("&e/claim here &7查看你脚下所在领地的信息"));
        sender.sendMessage(plugin.color("&e/claim list &7查看你的所有领地"));
        sender.sendMessage(plugin.color("&e/claim menu &7打开图形菜单"));
        sender.sendMessage(plugin.color("&e/claim show [领地名字] &7显示当前或指定领地边界"));
        sender.sendMessage(plugin.color("&e/claim show auto [on|off] &7设置进入领地时是否自动显示边界"));
        sender.sendMessage(plugin.color("&e/claim tp <领地名字> &7传送到你有权限进入的领地"));
        sender.sendMessage(plugin.color("&e/claim expand <east|south|west|north> &7向单个方向扩建 10 格"));
        sender.sendMessage(plugin.color("&e/claim remove <领地名字> &7删除指定领地，随后在聊天输入 confirm"));
        sender.sendMessage(plugin.color("&e/claim confirm &7直接确认待删除领地"));
        sender.sendMessage(plugin.color("&e/claim trust <\u73a9\u5bb6> \u6216 <\u9886\u5730\u540d> <\u73a9\u5bb6> &7\u7ed9\u4e88\u5355\u4e2a\u9886\u5730\u6743\u9650"));
        sender.sendMessage(plugin.color("&e/claim blacklist <add|remove|list> ... &7\u7ba1\u7406\u9886\u5730\u9ed1\u540d\u5355"));
        sender.sendMessage(plugin.color("&e/claim add <\u73a9\u5bb6> &7\u7ad9\u5728\u5f53\u524d\u9886\u5730\u5185\uff0c\u7ed9\u8fd9\u5757\u9886\u5730\u6dfb\u52a0\u6388\u6743\u73a9\u5bb6"));
        sender.sendMessage(plugin.color("&e/claim unadd <\u73a9\u5bb6> &7\u7ad9\u5728\u5f53\u524d\u9886\u5730\u5185\uff0c\u4ece\u8fd9\u5757\u9886\u5730\u79fb\u9664\u6388\u6743\u73a9\u5bb6"));
        sender.sendMessage(plugin.color("&e/claim globaladd <\u73a9\u5bb6> &7\u8ba9\u5bf9\u65b9\u6309\u4f60\u5404\u9886\u5730\u7684\u9ed8\u8ba4\u6210\u5458\u6743\u9650\u751f\u6548"));
        sender.sendMessage(plugin.color("&e/claim globalremove <\u73a9\u5bb6> &7\u79fb\u9664\u5bf9\u65b9\u5bf9\u4f60\u5168\u90e8\u9886\u5730\u7684\u9ed8\u8ba4\u6210\u5458\u6743\u9650\u7ee7\u627f"));
        sender.sendMessage(plugin.color("&e/claim transfer <玩家> &8或 &e/claim transfer <领地名> <玩家> &7转让领地，需要对方在线确认"));
        sender.sendMessage(plugin.color("&e/claim sell <价格> &8或 &e/claim sell <领地名> <价格> &7挂牌出售领地"));
        sender.sendMessage(plugin.color("&e/claim market &7打开玩家领地出售市场"));
        sender.sendMessage(plugin.color("&e/claim reclaimstarter &7\u672a\u5708\u5730\u65f6\u8865\u9886\u65b0\u4eba\u6838\u5fc3"));
        if (sender.hasPermission("coreclaim.admin")) {
            sender.sendMessage(plugin.color("&e/claim edit [领地名字] &7管理员编辑当前位置或指定领地"));
            sender.sendMessage(plugin.color("&e/claim admin info <领地名> &7管理员查看指定领地详情"));
            sender.sendMessage(plugin.color("&e/claim admin remove <领地名> &7管理员直接删除指定领地"));
            sender.sendMessage(plugin.color("&e/claim admin transfer <领地名> <玩家> &7管理员强制转让指定领地"));
            sender.sendMessage(plugin.color("&e/claim activity <get|set|add|take> <玩家> [值] &7管理活跃度"));
            sender.sendMessage(plugin.color("&e/claim reload &7重载配置与菜单"));
            sender.sendMessage(plugin.color("&e/claim givecore <玩家> [数量] &7手动发放领地核心"));
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> options = new ArrayList<>();
        if (args.length == 1) {
            options.add("help");
            options.add("info");
            options.add("here");
            options.add("current");
            options.add("list");
            options.add("menu");
            options.add("gui");
            options.add("wand");
            options.add("create");
            options.add("show");
            options.add("tp");
            options.add("teleport");
            options.add("expand");
            options.add("remove");
            options.add("delete");
            options.add("confirm");
            options.add("trust");
            options.add("untrust");
            options.add("blacklist");
            options.add("add");
            options.add("unadd");
            options.add("globaladd");
            options.add("globalremove");
            options.add("transfer");
            options.add("sell");
            options.add("market");
            options.add("starter");
            options.add("reclaim");
            options.add("reclaimstarter");
            if (sender.hasPermission("coreclaim.admin")) {
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
        if (args.length == 2 && (args[0].equalsIgnoreCase("show"))) {
            options.add("auto");
            if (sender instanceof Player player) {
                options.addAll(claimNames(claimService.allClaims().stream()
                    .filter(claim -> player.hasPermission("coreclaim.admin")
                        || claim.owner().equals(player.getUniqueId())
                        || claimService.canAccess(claim, player.getUniqueId()))
                    .toList()));
            } else {
                options.addAll(claimNames(claimService.allClaims()));
            }
            return filterByJoinedInput(options, args, 1);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("activity")) {
            options.add("get");
            options.add("set");
            options.add("add");
            options.add("take");
            return filter(options, args[1]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("admin") && sender.hasPermission("coreclaim.admin")) {
            options.add("info");
            options.add("remove");
            options.add("delete");
            options.add("transfer");
            return filter(options, args[1]);
        }
        if (args.length == 2 && (args[0].equalsIgnoreCase("blacklist") || args[0].equalsIgnoreCase("deny"))) {
            options.add("add");
            options.add("remove");
            options.add("list");
            return filter(options, args[1]);
        }
        if (args.length == 2 && (args[0].equalsIgnoreCase("trust")
            || args[0].equalsIgnoreCase("untrust")
            || args[0].equalsIgnoreCase("add")
            || args[0].equalsIgnoreCase("unadd")
            || args[0].equalsIgnoreCase("globaladd")
            || args[0].equalsIgnoreCase("globalremove")
            || args[0].equalsIgnoreCase("givecore")
            || args[0].equalsIgnoreCase("transfer"))) {
            options.addAll(knownPlayerNames());
            if (sender instanceof Player player && (args[0].equalsIgnoreCase("trust") || args[0].equalsIgnoreCase("untrust"))) {
                for (Claim claim : claimService.claimsOf(player.getUniqueId())) {
                    options.add(claim.name());
                }
            }
            if (sender instanceof Player player && args[0].equalsIgnoreCase("transfer")) {
                options.add("accept");
                options.add("deny");
                for (Claim claim : claimService.claimsOf(player.getUniqueId())) {
                    options.add(claim.name());
                }
            }
            return filter(options, args[1]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("sell") && sender instanceof Player player) {
            options.add("cancel");
            options.addAll(claimNames(claimService.claimsOf(player.getUniqueId())));
            return filterByJoinedInput(options, args, 1);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("edit") && sender.hasPermission("coreclaim.admin")) {
            options.addAll(claimNames(claimService.allClaims()));
            return filterByJoinedInput(options, args, 1);
        }
        if (args.length == 2 && (args[0].equalsIgnoreCase("remove") || args[0].equalsIgnoreCase("delete")) && sender instanceof Player player) {
            options.addAll(claimNames(claimService.claimsOf(player.getUniqueId())));
            return filterByJoinedInput(options, args, 1);
        }
        if (args.length == 2 && (args[0].equalsIgnoreCase("tp") || args[0].equalsIgnoreCase("teleport"))) {
            if (sender instanceof Player player) {
                options.addAll(claimNames(claimService.allClaims().stream()
                    .filter(claim -> player.hasPermission("coreclaim.admin")
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
        if (args.length == 3 && (args[0].equalsIgnoreCase("trust") || args[0].equalsIgnoreCase("untrust"))) {
            if (sender instanceof Player player) {
                List<Claim> ownedClaims = claimService.claimsOf(player.getUniqueId());
                String candidateName = joinArgs(args, 1, args.length - 1);
                if (hasUniqueMatchingClaim(ownedClaims, candidateName)) {
                    options.addAll(knownPlayerNames());
                    return filter(options, args[args.length - 1]);
                }
                options.addAll(claimNames(ownedClaims));
                return filterByJoinedInput(options, args, 1);
            }
            options.addAll(knownPlayerNames());
            return filter(options, args[2]);
        }
        if (args.length == 3 && (args[0].equalsIgnoreCase("blacklist") || args[0].equalsIgnoreCase("deny")) && args[1].equalsIgnoreCase("list") && sender instanceof Player player) {
            options.addAll(claimNames(claimService.claimsOf(player.getUniqueId())));
            return filterByJoinedInput(options, args, 2);
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("activity")) {
            options.addAll(knownPlayerNames());
            return filter(options, args[2]);
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("admin")
            && (args[1].equalsIgnoreCase("remove") || args[1].equalsIgnoreCase("delete") || args[1].equalsIgnoreCase("info") || args[1].equalsIgnoreCase("transfer"))) {
            options.addAll(claimNames(claimService.allClaims()));
            return filterByJoinedInput(options, args, 2);
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("sell") && args[1].equalsIgnoreCase("cancel") && sender instanceof Player player) {
            options.addAll(claimNames(claimService.claimsOf(player.getUniqueId())));
            return filterByJoinedInput(options, args, 2);
        }
        if (args.length == 3 && (args[0].equalsIgnoreCase("blacklist") || args[0].equalsIgnoreCase("deny"))
            && (args[1].equalsIgnoreCase("add") || args[1].equalsIgnoreCase("remove")) && sender instanceof Player player) {
            options.addAll(claimNames(claimService.claimsOf(player.getUniqueId())));
            options.addAll(knownPlayerNames());
            return filterByJoinedInput(options, args, 2);
        }
        if (args.length == 4 && (args[0].equalsIgnoreCase("blacklist") || args[0].equalsIgnoreCase("deny"))
            && (args[1].equalsIgnoreCase("add") || args[1].equalsIgnoreCase("remove"))) {
            if (sender instanceof Player player) {
                List<Claim> ownedClaims = claimService.claimsOf(player.getUniqueId());
                String candidateName = joinArgs(args, 2, args.length - 1);
                if (hasUniqueMatchingClaim(ownedClaims, candidateName)) {
                    options.addAll(knownPlayerNames());
                    return filter(options, args[args.length - 1]);
                }
                options.addAll(claimNames(ownedClaims));
                return filterByJoinedInput(options, args, 2);
            }
            options.addAll(knownPlayerNames());
            return filter(options, args[3]);
        }
        if (args.length > 2 && (args[0].equalsIgnoreCase("show")
            || args[0].equalsIgnoreCase("tp")
            || args[0].equalsIgnoreCase("teleport")
            || args[0].equalsIgnoreCase("edit")
            || args[0].equalsIgnoreCase("remove")
            || args[0].equalsIgnoreCase("delete"))) {
            if (args[0].equalsIgnoreCase("show") && args[1].equalsIgnoreCase("auto")) {
                return options;
            }
            if (sender instanceof Player player) {
                if (args[0].equalsIgnoreCase("show")) {
                    options.addAll(claimNames(claimService.allClaims().stream()
                        .filter(claim -> player.hasPermission("coreclaim.admin")
                            || claim.owner().equals(player.getUniqueId())
                            || claimService.canAccess(claim, player.getUniqueId()))
                        .toList()));
                } else if (args[0].equalsIgnoreCase("tp") || args[0].equalsIgnoreCase("teleport")) {
                    options.addAll(claimNames(claimService.allClaims().stream()
                        .filter(claim -> player.hasPermission("coreclaim.admin")
                            || claimService.hasPermission(claim, player.getUniqueId(), ClaimPermission.TELEPORT))
                        .toList()));
                } else if (args[0].equalsIgnoreCase("edit") && sender.hasPermission("coreclaim.admin")) {
                    options.addAll(claimNames(claimService.allClaims()));
                } else if (args[0].equalsIgnoreCase("remove") || args[0].equalsIgnoreCase("delete")) {
                    options.addAll(claimNames(claimService.claimsOf(player.getUniqueId())));
                }
            } else if (args[0].equalsIgnoreCase("edit") && sender.hasPermission("coreclaim.admin")) {
                options.addAll(claimNames(claimService.allClaims()));
            }
            return filterByJoinedInput(options, args, 1);
        }
        if (args.length > 3 && args[0].equalsIgnoreCase("admin")
            && (args[1].equalsIgnoreCase("remove") || args[1].equalsIgnoreCase("delete") || args[1].equalsIgnoreCase("info") || args[1].equalsIgnoreCase("transfer"))) {
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
        if (args.length > 3 && (args[0].equalsIgnoreCase("trust") || args[0].equalsIgnoreCase("untrust")) && sender instanceof Player player) {
            List<Claim> ownedClaims = claimService.claimsOf(player.getUniqueId());
            String candidateName = joinArgs(args, 1, args.length - 1);
            if (hasUniqueMatchingClaim(ownedClaims, candidateName)) {
                options.addAll(knownPlayerNames());
                return filter(options, args[args.length - 1]);
            }
            options.addAll(claimNames(ownedClaims));
            return filterByJoinedInput(options, args, 1);
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
        if (args.length > 2 && args[0].equalsIgnoreCase("sell") && sender instanceof Player player) {
            if (args[1].equalsIgnoreCase("cancel")) {
                options.addAll(claimNames(claimService.claimsOf(player.getUniqueId())));
                return filterByJoinedInput(options, args, 2);
            }
            options.addAll(claimNames(claimService.claimsOf(player.getUniqueId())));
            return filterByJoinedInput(options, args, 1);
        }
        if (args.length > 4 && (args[0].equalsIgnoreCase("blacklist") || args[0].equalsIgnoreCase("deny"))
            && (args[1].equalsIgnoreCase("add") || args[1].equalsIgnoreCase("remove")) && sender instanceof Player player) {
            List<Claim> ownedClaims = claimService.claimsOf(player.getUniqueId());
            String candidateName = joinArgs(args, 2, args.length - 1);
            if (hasUniqueMatchingClaim(ownedClaims, candidateName)) {
                options.addAll(knownPlayerNames());
                return filter(options, args[args.length - 1]);
            }
            options.addAll(claimNames(ownedClaims));
            return filterByJoinedInput(options, args, 2);
        }
        return options;
    }

    private String chatMessage(String path, String fallback, String... replacements) {
        String prefix = plugin.messagesConfig().getString("prefix", "&8[&6Claim&8] &f");
        String body = plugin.messagesConfig().contains(path) ? plugin.messagesConfig().getString(path, fallback) : fallback;
        String message = plugin.color(prefix + body);
        for (int index = 0; index + 1 < replacements.length; index += 2) {
            message = message.replace(replacements[index], replacements[index + 1]);
        }
        return message;
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
        List<Claim> matchingClaims = claimService.findClaimsByName(rawName);
        return claims.stream().filter(matchingClaims::contains).count() == 1;
    }
}
