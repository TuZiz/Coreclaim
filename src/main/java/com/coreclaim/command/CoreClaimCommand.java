package com.coreclaim.command;

import com.coreclaim.CoreClaimPlugin;
import com.coreclaim.config.ClaimGroup;
import com.coreclaim.gui.MenuService;
import com.coreclaim.model.Claim;
import com.coreclaim.model.ClaimDirection;
import com.coreclaim.model.PlayerProfile;
import com.coreclaim.service.ClaimActionService;
import com.coreclaim.service.ClaimVisualService;
import com.coreclaim.service.ClaimService;
import com.coreclaim.service.ProfileService;
import com.coreclaim.service.RemovalConfirmationService;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;

public final class CoreClaimCommand implements TabExecutor {

    private final CoreClaimPlugin plugin;
    private final ClaimService claimService;
    private final ProfileService profileService;
    private final ClaimActionService claimActionService;
    private final ClaimVisualService claimVisualService;
    private final MenuService menuService;
    private final RemovalConfirmationService removalConfirmationService;

    public CoreClaimCommand(
        CoreClaimPlugin plugin,
        ClaimService claimService,
        ProfileService profileService,
        ClaimActionService claimActionService,
        ClaimVisualService claimVisualService,
        MenuService menuService,
        RemovalConfirmationService removalConfirmationService
    ) {
        this.plugin = plugin;
        this.claimService = claimService;
        this.profileService = profileService;
        this.claimActionService = claimActionService;
        this.claimVisualService = claimVisualService;
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
            case "list" -> handleList(sender);
            case "menu", "gui" -> handleMenu(sender);
            case "show" -> handleShow(sender, args);
            case "tp", "teleport" -> handleTeleport(sender, args);
            case "expand" -> handleExpand(sender, args);
            case "remove", "delete" -> handleRemove(sender, args);
            case "confirm" -> handleConfirm(sender);
            case "trust" -> handleTrust(sender, args);
            case "untrust" -> handleUntrust(sender, args);
            case "add", "globaladd" -> handleGlobalAdd(sender, args);
            case "unadd", "globalremove" -> handleGlobalRemove(sender, args);
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
            Player target = Bukkit.getPlayerExact(args[1]);
            if (target == null) {
                player.sendMessage(plugin.message("target-must-online"));
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
        Player target = Bukkit.getPlayerExact(args[2]);
        if (target == null) {
            player.sendMessage(plugin.message("target-must-online"));
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
            Player target = Bukkit.getPlayerExact(args[1]);
            if (target == null) {
                player.sendMessage(plugin.message("target-must-online"));
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
        Player target = Bukkit.getPlayerExact(args[2]);
        if (target == null) {
            player.sendMessage(plugin.message("target-must-online"));
            return true;
        }
        claimActionService.untrustPlayer(player, claim, target);
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
        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            player.sendMessage(plugin.message("target-must-online"));
            return true;
        }
        if (target.getUniqueId().equals(player.getUniqueId())) {
            player.sendMessage(plugin.message("trust-self"));
            return true;
        }
        if (!profileService.addGlobalTrustedMember(player.getUniqueId(), target.getUniqueId())) {
            player.sendMessage(plugin.message("global-add-exists", "{player}", target.getName()));
            return true;
        }
        profileService.save();
        player.sendMessage(plugin.message("global-add-success", "{player}", target.getName()));
        target.sendMessage(plugin.message("global-add-notify", "{owner}", player.getName()));
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
        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            player.sendMessage(plugin.message("target-must-online"));
            return true;
        }
        if (!profileService.removeGlobalTrustedMember(player.getUniqueId(), target.getUniqueId())) {
            player.sendMessage(plugin.message("global-remove-missing", "{player}", target.getName()));
            return true;
        }
        profileService.save();
        player.sendMessage(plugin.message("global-remove-success", "{player}", target.getName()));
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

    private Claim findOwnedClaimByName(Player player, String name) {
        return claimService.claimsOf(player.getUniqueId()).stream()
            .filter(claim -> claim.name().equalsIgnoreCase(name))
            .findFirst()
            .orElse(null);
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage(plugin.color("&6Claim 命令:"));
        sender.sendMessage(plugin.color("&e/claim info &7查看组别、活跃度和当前领地"));
        sender.sendMessage(plugin.color("&e/claim list &7查看你的所有领地"));
        sender.sendMessage(plugin.color("&e/claim menu &7打开图形菜单"));
        sender.sendMessage(plugin.color("&e/claim show [领地名字] &7显示当前或指定领地边界"));
        sender.sendMessage(plugin.color("&e/claim tp <领地名字> &7传送到你有权限进入的领地"));
        sender.sendMessage(plugin.color("&e/claim expand <east|south|west|north> &7向单个方向扩建 10 格"));
        sender.sendMessage(plugin.color("&e/claim remove <领地名字> &7删除指定领地，随后在聊天输入 confirm"));
        sender.sendMessage(plugin.color("&e/claim confirm &7直接确认待删除领地"));
        sender.sendMessage(plugin.color("&e/claim trust <玩家> 或 <领地名> <玩家> &7给予单个领地权限"));
        sender.sendMessage(plugin.color("&e/claim add <玩家> &7给予全部领地全局权限"));
        sender.sendMessage(plugin.color("&e/claim unadd <玩家> &7移除全部领地全局权限"));
        if (sender.hasPermission("coreclaim.admin")) {
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
            options.add("list");
            options.add("menu");
            options.add("gui");
            options.add("show");
            options.add("tp");
            options.add("teleport");
            options.add("expand");
            options.add("remove");
            options.add("delete");
            options.add("confirm");
            options.add("trust");
            options.add("untrust");
            options.add("add");
            options.add("unadd");
            if (sender.hasPermission("coreclaim.admin")) {
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
        if (args.length == 2 && args[0].equalsIgnoreCase("activity")) {
            options.add("get");
            options.add("set");
            options.add("add");
            options.add("take");
            return filter(options, args[1]);
        }
        if (args.length == 2 && (args[0].equalsIgnoreCase("trust")
            || args[0].equalsIgnoreCase("untrust")
            || args[0].equalsIgnoreCase("add")
            || args[0].equalsIgnoreCase("unadd")
            || args[0].equalsIgnoreCase("givecore"))) {
            for (Player player : Bukkit.getOnlinePlayers()) {
                options.add(player.getName());
            }
            return filter(options, args[1]);
        }
        if (args.length == 2 && (args[0].equalsIgnoreCase("remove") || args[0].equalsIgnoreCase("delete")) && sender instanceof Player player) {
            for (Claim claim : claimService.claimsOf(player.getUniqueId())) {
                options.add(claim.name());
            }
            return filter(options, args[1]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("show")) {
            for (Claim claim : claimService.allClaims()) {
                options.add(claim.name());
            }
            return filter(options, args[1]);
        }
        if (args.length == 2 && (args[0].equalsIgnoreCase("tp") || args[0].equalsIgnoreCase("teleport"))) {
            for (Claim claim : claimService.allClaims()) {
                options.add(claim.name());
            }
            return filter(options, args[1]);
        }
        if (args.length == 3 && (args[0].equalsIgnoreCase("trust") || args[0].equalsIgnoreCase("untrust"))) {
            for (Player player : Bukkit.getOnlinePlayers()) {
                options.add(player.getName());
            }
            return filter(options, args[2]);
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("activity")) {
            for (Player player : Bukkit.getOnlinePlayers()) {
                options.add(player.getName());
            }
            return filter(options, args[2]);
        }
        return options;
    }

    private List<String> filter(List<String> options, String input) {
        String lowered = input.toLowerCase(Locale.ROOT);
        return options.stream()
            .filter(option -> option.toLowerCase(Locale.ROOT).startsWith(lowered))
            .toList();
    }
}
