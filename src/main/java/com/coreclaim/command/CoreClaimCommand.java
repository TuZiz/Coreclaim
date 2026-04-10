package com.coreclaim.command;

import com.coreclaim.CoreClaimPlugin;
import com.coreclaim.config.ClaimGroup;
import com.coreclaim.gui.MenuService;
import com.coreclaim.model.Claim;
import com.coreclaim.model.ClaimDirection;
import com.coreclaim.model.PlayerProfile;
import com.coreclaim.service.ClaimActionService;
import com.coreclaim.service.ClaimSelectionService;
import com.coreclaim.service.ClaimVisualService;
import com.coreclaim.service.ClaimService;
import com.coreclaim.service.ProfileService;
import com.coreclaim.service.RemovalConfirmationService;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
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

    public CoreClaimCommand(
        CoreClaimPlugin plugin,
        ClaimService claimService,
        ProfileService profileService,
        ClaimActionService claimActionService,
        ClaimVisualService claimVisualService,
        ClaimSelectionService claimSelectionService,
        MenuService menuService,
        RemovalConfirmationService removalConfirmationService
    ) {
        this.plugin = plugin;
        this.claimService = claimService;
        this.profileService = profileService;
        this.claimActionService = claimActionService;
        this.claimVisualService = claimVisualService;
        this.claimSelectionService = claimSelectionService;
        this.menuService = menuService;
        this.removalConfirmationService = removalConfirmationService;
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
            case "add", "globaladd" -> handleGlobalAdd(sender, args);
            case "unadd", "globalremove" -> handleGlobalRemove(sender, args);
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
            claim = claimService.allClaims().stream()
                .filter(found -> found.name().equalsIgnoreCase(args[1]))
                .findFirst()
                .orElse(null);
            if (claim == null) {
                player.sendMessage(plugin.message("claim-name-not-found", "{name}", args[1]));
                return true;
            }
            if (!claim.owner().equals(player.getUniqueId()) && !claimService.canAccess(claim, player.getUniqueId()) && !player.hasPermission("coreclaim.admin")) {
                player.sendMessage(plugin.message("trust-no-permission"));
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

        Claim claim = claimService.allClaims().stream()
            .filter(found -> found.name().equalsIgnoreCase(args[1]))
            .findFirst()
            .orElse(null);
        if (claim == null) {
            player.sendMessage(plugin.message("claim-name-not-found", "{name}", args[1]));
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

        Claim claim = findOwnedClaimByName(player, args[1]);
        if (claim == null) {
            player.sendMessage(plugin.message("claim-name-not-found", "{name}", args[1]));
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

        Claim claim = findOwnedClaimByName(player, args[1]);
        if (claim == null) {
            player.sendMessage(plugin.message("claim-name-not-found", "{name}", args[1]));
            return true;
        }
        OfflinePlayer target = resolveKnownPlayer(args[2]);
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

        Claim claim = findOwnedClaimByName(player, args[1]);
        if (claim == null) {
            player.sendMessage(plugin.message("claim-name-not-found", "{name}", args[1]));
            return true;
        }
        OfflinePlayer target = resolveKnownPlayer(args[2]);
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
            claim = findOwnedClaimByName(player, args[2]);
            if (claim == null) {
                player.sendMessage(plugin.message("claim-name-not-found", "{name}", args[2]));
                return true;
            }
            targetName = args[3];
        } else {
            player.sendMessage(plugin.message("blacklist-add-usage"));
            return true;
        }

        OfflinePlayer target = Bukkit.getOfflinePlayer(targetName);
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
            claim = findOwnedClaimByName(player, args[2]);
            if (claim == null) {
                player.sendMessage(plugin.message("claim-name-not-found", "{name}", args[2]));
                return true;
            }
            targetName = args[3];
        } else {
            player.sendMessage(plugin.message("blacklist-remove-usage"));
            return true;
        }

        OfflinePlayer target = Bukkit.getOfflinePlayer(targetName);
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
            claim = findOwnedClaimByName(player, args[2]);
            if (claim == null) {
                player.sendMessage(plugin.message("claim-name-not-found", "{name}", args[2]));
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

        Claim claim = claimService.allClaims().stream()
            .filter(found -> found.name().equalsIgnoreCase(args[2]))
            .findFirst()
            .orElse(null);
        if (claim == null) {
            sender.sendMessage(plugin.message("claim-name-not-found", "{name}", args[2]));
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

        Claim claim = claimService.allClaims().stream()
            .filter(found -> found.name().equalsIgnoreCase(args[2]))
            .findFirst()
            .orElse(null);
        if (claim == null) {
            sender.sendMessage(plugin.message("claim-name-not-found", "{name}", args[2]));
            return true;
        }
        return claimActionService.adminRemoveClaim(sender, claim);
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
            claim = findAnyClaimByName(args[1]);
            if (claim == null) {
                player.sendMessage(plugin.message("claim-name-not-found", "{name}", args[1]));
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
        if (!profileService.addGlobalTrustedMember(player.getUniqueId(), target.getUniqueId())) {
            player.sendMessage(plugin.message("global-add-exists", "{player}", displayName(target)));
            return true;
        }
        profileService.save();
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
        if (!profileService.removeGlobalTrustedMember(player.getUniqueId(), target.getUniqueId())) {
            player.sendMessage(plugin.message("global-remove-missing", "{player}", displayName(target)));
            return true;
        }
        profileService.save();
        player.sendMessage(plugin.message("global-remove-success", "{player}", displayName(target)));
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
        org.bukkit.OfflinePlayer target = Bukkit.getOfflinePlayer(args[2]);
        String name = target.getName() == null ? args[2] : target.getName();
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
                profileService.save();
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
                profileService.save();
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
                profileService.save();
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
            sender.sendMessage(plugin.color("&6[Claim] &f受信任玩家: " + joinPlayerNames(claim.trustedMembers())));
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

    private Claim findOwnedClaimByName(Player player, String name) {
        return claimService.claimsOf(player.getUniqueId()).stream()
            .filter(claim -> claim.name().equalsIgnoreCase(name))
            .findFirst()
            .orElse(null);
    }

    private Claim findAnyClaimByName(String name) {
        return claimService.allClaims().stream()
            .filter(claim -> claim.name().equalsIgnoreCase(name))
            .findFirst()
            .orElse(null);
    }

    private OfflinePlayer resolveKnownPlayer(String rawName) {
        if (rawName == null || rawName.isBlank()) {
            return null;
        }
        Player online = Bukkit.getPlayerExact(rawName);
        if (online != null) {
            return online;
        }
        for (OfflinePlayer offline : Bukkit.getOfflinePlayers()) {
            if (offline.getName() != null && offline.getName().equalsIgnoreCase(rawName)) {
                return offline;
            }
        }
        return null;
    }

    private List<String> knownPlayerNames() {
        LinkedHashSet<String> names = new LinkedHashSet<>();
        for (OfflinePlayer offline : Bukkit.getOfflinePlayers()) {
            if (offline.getName() != null && !offline.getName().isBlank()) {
                names.add(offline.getName());
            }
        }
        for (Player online : Bukkit.getOnlinePlayers()) {
            names.add(online.getName());
        }
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
        sender.sendMessage(plugin.color("&e/claim trust <玩家> 或 <领地名> <玩家> &7给予单个领地权限"));
        sender.sendMessage(plugin.color("&e/claim blacklist <add|remove|list> ... &7管理领地黑名单"));
        sender.sendMessage(plugin.color("&e/claim add <玩家> &7给予全部领地全局权限"));
        sender.sendMessage(plugin.color("&e/claim unadd <玩家> &7移除全部领地全局权限"));
        sender.sendMessage(plugin.color("&e/claim reclaimstarter &7未圈地时补领新人核心"));
        if (sender.hasPermission("coreclaim.admin")) {
            sender.sendMessage(plugin.color("&e/claim edit [领地名字] &7管理员编辑当前位置或指定领地"));
            sender.sendMessage(plugin.color("&e/claim admin info <领地名> &7管理员查看指定领地详情"));
            sender.sendMessage(plugin.color("&e/claim admin remove <领地名> &7管理员直接删除指定领地"));
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
            for (Claim claim : claimService.allClaims()) {
                options.add(claim.name());
            }
            return filter(options, args[1]);
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
            || args[0].equalsIgnoreCase("givecore"))) {
            options.addAll(knownPlayerNames());
            return filter(options, args[1]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("edit") && sender.hasPermission("coreclaim.admin")) {
            for (Claim claim : claimService.allClaims()) {
                options.add(claim.name());
            }
            return filter(options, args[1]);
        }
        if (args.length == 2 && (args[0].equalsIgnoreCase("remove") || args[0].equalsIgnoreCase("delete")) && sender instanceof Player player) {
            for (Claim claim : claimService.claimsOf(player.getUniqueId())) {
                options.add(claim.name());
            }
            return filter(options, args[1]);
        }
        if (args.length == 2 && (args[0].equalsIgnoreCase("tp") || args[0].equalsIgnoreCase("teleport"))) {
            if (sender instanceof Player player) {
                for (Claim claim : claimService.allClaims()) {
                    if (player.hasPermission("coreclaim.admin")
                        || claim.owner().equals(player.getUniqueId())
                        || profileService.isGloballyTrusted(claim.owner(), player.getUniqueId())) {
                        options.add(claim.name());
                    }
                }
            }
            return filter(options, args[1]);
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("show") && args[1].equalsIgnoreCase("auto")) {
            options.add("on");
            options.add("off");
            return filter(options, args[2]);
        }
        if (args.length == 3 && (args[0].equalsIgnoreCase("trust") || args[0].equalsIgnoreCase("untrust"))) {
            options.addAll(knownPlayerNames());
            return filter(options, args[2]);
        }
        if (args.length == 3 && (args[0].equalsIgnoreCase("blacklist") || args[0].equalsIgnoreCase("deny")) && args[1].equalsIgnoreCase("list") && sender instanceof Player player) {
            for (Claim claim : claimService.claimsOf(player.getUniqueId())) {
                options.add(claim.name());
            }
            return filter(options, args[2]);
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("activity")) {
            options.addAll(knownPlayerNames());
            return filter(options, args[2]);
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("admin")
            && (args[1].equalsIgnoreCase("remove") || args[1].equalsIgnoreCase("delete") || args[1].equalsIgnoreCase("info"))) {
            for (Claim claim : claimService.allClaims()) {
                options.add(claim.name());
            }
            return filter(options, args[2]);
        }
        if (args.length == 3 && (args[0].equalsIgnoreCase("blacklist") || args[0].equalsIgnoreCase("deny"))
            && (args[1].equalsIgnoreCase("add") || args[1].equalsIgnoreCase("remove")) && sender instanceof Player player) {
            for (Claim claim : claimService.claimsOf(player.getUniqueId())) {
                options.add(claim.name());
            }
            for (Player online : Bukkit.getOnlinePlayers()) {
                options.add(online.getName());
            }
            return filter(options, args[2]);
        }
        if (args.length == 4 && (args[0].equalsIgnoreCase("blacklist") || args[0].equalsIgnoreCase("deny"))
            && (args[1].equalsIgnoreCase("add") || args[1].equalsIgnoreCase("remove"))) {
            for (Player online : Bukkit.getOnlinePlayers()) {
                options.add(online.getName());
            }
            return filter(options, args[3]);
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
            .filter(option -> option.toLowerCase(Locale.ROOT).startsWith(lowered))
            .toList();
    }
}
