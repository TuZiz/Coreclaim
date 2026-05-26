package com.coreclaim.command;

import com.coreclaim.CoreClaimPlugin;
import com.coreclaim.model.Claim;
import com.coreclaim.model.ClaimPermission;
import com.coreclaim.service.ClaimActionService;
import com.coreclaim.selection.ClaimSelectionService;
import com.coreclaim.service.ClaimService;
import com.coreclaim.claim.auth.ClaimAuthorizationService.AuthorizationDecision;
import com.coreclaim.service.RemovalConfirmationService;
import com.coreclaim.claim.query.ClaimIndexExplanation;
import com.coreclaim.util.AdminAccess;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

final class ClaimAdminClaimCommandHandler {

    private final CoreClaimPlugin plugin;
    private final ClaimService claimService;
    private final ClaimActionService claimActionService;
    private final ClaimSelectionService claimSelectionService;
    private final RemovalConfirmationService removalConfirmationService;
    private final ClaimCommandFormatter formatter;
    private final ClaimCommandResolver resolver;

    ClaimAdminClaimCommandHandler(
        CoreClaimPlugin plugin,
        ClaimService claimService,
        ClaimActionService claimActionService,
        ClaimSelectionService claimSelectionService,
        RemovalConfirmationService removalConfirmationService,
        ClaimCommandFormatter formatter,
        ClaimCommandResolver resolver
    ) {
        this.plugin = plugin;
        this.claimService = claimService;
        this.claimActionService = claimActionService;
        this.claimSelectionService = claimSelectionService;
        this.removalConfirmationService = removalConfirmationService;
        this.formatter = formatter;
        this.resolver = resolver;
    }

    boolean handleCreate(CommandSender sender, String[] args) {
        if (!AdminAccess.hasCreateSystemAccess(sender)) {
            sender.sendMessage(plugin.message("no-permission"));
            return true;
        }
        if (!(sender instanceof Player player)) {
            sender.sendMessage(plugin.message("player-only"));
            return true;
        }
        if (args.length < 4 || !args[2].equalsIgnoreCase("system")) {
            sender.sendMessage(plugin.message("admin-create-system-usage"));
            return true;
        }
        String claimName = resolver.joinArgs(args, 3);
        if (claimName.isBlank()) {
            sender.sendMessage(plugin.message("admin-create-system-usage"));
            return true;
        }
        return claimSelectionService.createSystemClaim(player, claimName);
    }

    boolean handleInfo(CommandSender sender, String[] args) {
        if (!AdminAccess.hasViewAccess(sender)) {
            sender.sendMessage(plugin.message("no-permission"));
            return true;
        }
        if (args.length < 3) {
            sender.sendMessage(plugin.message("admin-info-usage"));
            return true;
        }
        Claim claim = resolver.resolveAdminClaimSelector(sender, resolver.joinArgs(args, 2));
        if (claim == null) {
            return true;
        }
        formatter.sendEnhancedClaimDetails(sender, claim, true);
        return true;
    }

    boolean handlePlayerClaims(CommandSender sender, String[] args) {
        if (!AdminAccess.hasViewAccess(sender)) {
            sender.sendMessage(plugin.message("no-permission"));
            return true;
        }
        if (args.length < 3) {
            sender.sendMessage(plugin.message("admin-playerclaims-usage"));
            return true;
        }
        OfflinePlayer target = resolver.resolveKnownPlayer(resolver.joinArgs(args, 2));
        if (target == null || target.getUniqueId() == null) {
            sender.sendMessage(plugin.message("trust-no-target"));
            return true;
        }
        List<Claim> claims = claimService.claimsOfFresh(target.getUniqueId(), true);
        if (claims.isEmpty()) {
            sender.sendMessage(plugin.message("admin-playerclaims-empty", "{player}", resolver.displayName(target)));
            return true;
        }
        sender.sendMessage(plugin.color("&6[Claim] &f玩家 &e" + resolver.displayName(target) + " &f名下领地:"));
        for (Claim claim : claims) {
            sender.sendMessage(plugin.color("&7- &f#" + claim.id() + " &e" + (claim.systemManaged() ? "[SYSTEM] " : "") + claim.name() + " &8@ &b" + claimService.displayServerId(claim) + " &7[" + claim.world() + " " + claim.centerX() + ", " + claim.centerZ() + "]"));
        }
        return true;
    }

    boolean handleDiagnose(CommandSender sender, String[] args) {
        if (!AdminAccess.hasViewAccess(sender)) {
            sender.sendMessage(plugin.message("no-permission"));
            return true;
        }
        DiagnoseRequest request = diagnoseRequest(sender, args);
        if (request == null) {
            return true;
        }
        Claim claim = resolver.resolveAdminClaimSelector(sender, request.claimSelector());
        if (claim == null) {
            return true;
        }
        sendClaimDiagnostics(sender, claim, request.target());
        return true;
    }

    private DiagnoseRequest diagnoseRequest(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(plugin.message("admin-diagnose-usage"));
            return null;
        }
        int playerOptionIndex = -1;
        for (int index = 2; index < args.length; index++) {
            if (args[index].equalsIgnoreCase("--player") || args[index].equalsIgnoreCase("-p")) {
                playerOptionIndex = index;
                break;
            }
        }
        if (playerOptionIndex < 0) {
            return new DiagnoseRequest(resolver.joinArgs(args, 2), null);
        }
        if (playerOptionIndex == 2 || playerOptionIndex + 2 != args.length) {
            sender.sendMessage(plugin.message("admin-diagnose-usage"));
            return null;
        }
        OfflinePlayer target = resolver.resolveKnownPlayer(args[playerOptionIndex + 1]);
        if (target == null || target.getUniqueId() == null) {
            sender.sendMessage(plugin.message("trust-no-target"));
            return null;
        }
        return new DiagnoseRequest(resolver.joinArgs(args, 2, playerOptionIndex), target);
    }

    private void sendClaimDiagnostics(CommandSender sender, Claim claim, OfflinePlayer target) {
        org.bukkit.Location senderLocation = sender instanceof Player player ? player.getLocation() : null;
        ClaimIndexExplanation indexState = claimService.explainClaimIndexState(claim.id(), senderLocation);
        boolean localClaim = claimService.isLocalClaim(claim);
        boolean worldLoaded = localClaim && Bukkit.getWorld(claim.world()) != null;
        String route = localClaim
            ? (worldLoaded ? "local-teleport" : "local-world-missing")
            : (plugin.settings().crossServerTeleportEnabled() ? "cross-server-teleport" : "cross-server-disabled");
        sender.sendMessage(plugin.color("&6[Claim] &f诊断目标: &e" + (claim.systemManaged() ? "[SYSTEM] " : "") + claim.name() + " &7(#" + claim.id() + ")"));
        sender.sendMessage(plugin.color("&6[Claim] &fOwner: &b" + claim.ownerName() + " &8| &fServer ID: &e" + claimService.displayServerId(claim)));
        sender.sendMessage(plugin.color("&6[Claim] &f当前服 server-id: &e" + plugin.settings().serverId() + " &8| &f是否本服: " + (localClaim ? "&a是" : "&c否")));
        sender.sendMessage(plugin.color("&6[Claim] &f索引字段: claimId=&e" + indexState.claimId()
            + " &8| &fworld=&b" + indexState.world()
            + " &8| &fcurrentServerId=&e" + indexState.currentServerId()
            + " &8| &fisLocalClaim=" + (indexState.localClaim() ? "&a是" : "&c否")));
        sender.sendMessage(plugin.color("&6[Claim] &f索引诊断: raw server_id=&e" + indexState.rawServerIdDisplay()
            + " &8| &feffective=&b" + indexState.effectiveServerIdDisplay()
            + " &8| &findexed=" + (indexState.indexed() ? "&a是" : "&c否")));
        sender.sendMessage(plugin.color("&6[Claim] &f当前位置 findClaim: &e" + indexState.currentLocationHitDisplay()
            + " &8| &fworldLoaded=" + (indexState.worldLoaded() ? "&a是" : "&c否")));
        sender.sendMessage(plugin.color("&6[Claim] &f修复建议: &e" + indexState.repairSuggestion()));
        if (!indexState.indexed()) {
            sender.sendMessage(plugin.message("admin-diagnose-index-warning"));
            if (indexState.mysql() && (indexState.rawServerId() == null || indexState.rawServerId().isBlank())) {
                sender.sendMessage(plugin.message("legacy-server-id-repair-hint"));
            }
        }
        sender.sendMessage(plugin.color("&6[Claim] &f系统领地: " + (claim.systemManaged() ? "&6是&8| &f计入配额: &c否" : "&7否&8| &f计入配额: &a是")));
        sender.sendMessage(plugin.color("&6[Claim] &f权限来源: " + formatter.ruleSourceSummary(claim)));
        sender.sendMessage(plugin.color("&6[Claim] &f世界状态: " + (worldLoaded ? "&a已加载" : "&e未加载或不在本服")));
        sender.sendMessage(plugin.color("&6[Claim] &fTP 路由: &b" + route));
        sender.sendMessage(plugin.color("&6[Claim] &fdeny *: " + (claim.denyAll() ? "&c开启" : "&a关闭") + " &8| &fDenied: &c" + claim.deniedMembers().size() + " &8| &fTrusted: &a" + claim.trustedCount()));
        sender.sendMessage(plugin.color("&6[Claim] &f公开权限: &cBREAK " + formatter.stateText(claim.permission(ClaimPermission.BREAK))
            + " &8| &bINTERACT " + formatter.stateText(claim.permission(ClaimPermission.INTERACT))));
        sender.sendMessage(plugin.color("&6[Claim] &f成员列表: " + formatMembers(claim.trustedMembers())));
        sender.sendMessage(plugin.color("&6[Claim] &f扩展权限: " + formatter.summarizeFlags(claim)));
        if (target != null) {
            sendTargetAuthorizationDiagnostics(sender, claim, target);
        }
    }

    private void sendTargetAuthorizationDiagnostics(CommandSender sender, Claim claim, OfflinePlayer target) {
        UUID playerId = target.getUniqueId();
        boolean bypassing = target instanceof Player player && AdminAccess.hasForceBypass(player);
        AuthorizationDecision breakDecision = claimService.permissionDecision(claim, playerId, ClaimPermission.BREAK, bypassing);
        AuthorizationDecision interactDecision = claimService.permissionDecision(claim, playerId, ClaimPermission.INTERACT, bypassing);
        sender.sendMessage(plugin.color("&6[Claim] &f目标玩家: &e" + resolver.displayName(target) + " &7(" + playerId + ")"));
        sender.sendMessage(plugin.color("&6[Claim] &f授权来源: &cBREAK " + formatDecision(breakDecision)
            + " &8| &bINTERACT " + formatDecision(interactDecision)));
    }

    private String formatDecision(AuthorizationDecision decision) {
        String state = decision.allowed() ? "&aALLOW" : "&cDENY";
        return "&e" + decision.source().name() + " " + state;
    }

    private String formatMembers(Set<UUID> members) {
        if (members.isEmpty()) {
            return "&7空";
        }
        return members.stream()
            .map(playerId -> {
                OfflinePlayer player = Bukkit.getOfflinePlayer(playerId);
                String name = player.getName() == null ? playerId.toString() : player.getName();
                return "&a" + name;
            })
            .limit(20)
            .reduce((left, right) -> left + "&8, " + right)
            .orElse("&7空");
    }

    private record DiagnoseRequest(String claimSelector, OfflinePlayer target) {
    }

    boolean handleRemove(CommandSender sender, String[] args) {
        Claim claim;
        if (!AdminAccess.hasClaimManageAccess(sender)) {
            sender.sendMessage(plugin.message("no-permission"));
            return true;
        }
        if (args.length >= 3) {
            claim = resolver.resolveAdminClaimSelector(sender, resolver.joinArgs(args, 2));
        } else if (sender instanceof Player player) {
            claim = resolveCurrentAdminClaim(player, "/claim admin remove", current -> claimActionService.canManageClaim(player, current));
        } else {
            sender.sendMessage(plugin.message("admin-remove-usage"));
            return true;
        }
        if (claim == null) {
            return true;
        }
        if (sender instanceof Player player) {
            removalConfirmationService.requestAdminRemoval(player, claim);
            return true;
        }
        claimActionService.adminRemoveClaim(sender, claim);
        return true;
    }

    boolean handleSetServer(CommandSender sender, String[] args) {
        if (!AdminAccess.hasClaimManageAccess(sender)) {
            sender.sendMessage(plugin.message("no-permission"));
            return true;
        }
        if (args.length < 4) {
            sender.sendMessage(plugin.message("admin-setserver-usage"));
            return true;
        }
        int claimId = resolver.parseClaimId(args[2], sender);
        if (claimId <= 0) {
            return true;
        }
        String targetServerId = args[3] == null ? "" : args[3].trim();
        if (targetServerId.isEmpty()) {
            sender.sendMessage(plugin.message("admin-setserver-usage"));
            return true;
        }
        Claim claim = claimService.updateClaimServerId(claimId, targetServerId).orElse(null);
        if (claim == null) {
            sender.sendMessage(plugin.message("claim-server-update-failed", "{id}", String.valueOf(claimId)));
            return true;
        }
        sender.sendMessage(plugin.message("claim-server-updated", "{id}", String.valueOf(claim.id()), "{name}", claim.name(), "{server}", claimService.displayServerId(claim)));
        return true;
    }

    private Claim resolveCurrentAdminClaim(Player player, String usageLabel, Predicate<Claim> accessCheck) {
        Claim claim = claimActionService.findCurrentPresenceClaim(player);
        if (claim == null) {
            player.sendMessage(plugin.message("claim-current-admin-required", "{usage}", usageLabel));
            return null;
        }
        if (!accessCheck.test(claim)) {
            player.sendMessage(plugin.message("trust-no-permission"));
            return null;
        }
        return claim;
    }
}
