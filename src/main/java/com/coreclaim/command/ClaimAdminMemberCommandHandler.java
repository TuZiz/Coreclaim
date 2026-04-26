package com.coreclaim.command;

import com.coreclaim.CoreClaimPlugin;
import com.coreclaim.model.Claim;
import com.coreclaim.service.ClaimActionService;
import com.coreclaim.service.ClaimService;
import com.coreclaim.util.AdminAccess;
import java.util.UUID;
import java.util.function.Predicate;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

final class ClaimAdminMemberCommandHandler {

    private final CoreClaimPlugin plugin;
    private final ClaimService claimService;
    private final ClaimActionService claimActionService;
    private final ClaimCommandResolver resolver;

    ClaimAdminMemberCommandHandler(
        CoreClaimPlugin plugin,
        ClaimService claimService,
        ClaimActionService claimActionService,
        ClaimCommandResolver resolver
    ) {
        this.plugin = plugin;
        this.claimService = claimService;
        this.claimActionService = claimActionService;
        this.resolver = resolver;
    }

    boolean handleDeny(CommandSender sender, String[] args) {
        String targetArg;
        Claim claim;
        if (!AdminAccess.hasMemberManageAccess(sender)) {
            sender.sendMessage(plugin.message("no-permission"));
            return true;
        }
        if (args.length == 3 && sender instanceof Player player) {
            claim = resolveCurrentAdminClaim(player, "/claim admin deny", current -> claimActionService.canManageMembers(player, current));
            if (claim == null) {
                return true;
            }
            targetArg = args[2];
        } else if (args.length >= 4) {
            claim = resolver.resolveAdminClaimSelector(sender, resolver.joinArgs(args, 2, args.length - 1));
            if (claim == null) {
                return true;
            }
            targetArg = args[args.length - 1];
        } else {
            sender.sendMessage(plugin.message("admin-deny-usage"));
            return true;
        }
        if ("*".equals(targetArg)) {
            claimService.updateDenyAll(claim, true, actorId(sender));
            sender.sendMessage(plugin.message("admin-deny-all-enabled", "{name}", claim.name()));
            return true;
        }
        OfflinePlayer target = resolver.resolveKnownPlayer(targetArg);
        if (target == null || target.getUniqueId() == null) {
            sender.sendMessage(plugin.message("trust-no-target"));
            return true;
        }
        if (!claimService.addDeniedMember(claim, target.getUniqueId(), actorId(sender))) {
            sender.sendMessage(plugin.message("admin-deny-exists", "{player}", resolver.displayName(target), "{name}", claim.name()));
            return true;
        }
        sender.sendMessage(plugin.message("admin-deny-added", "{player}", resolver.displayName(target), "{name}", claim.name()));
        return true;
    }

    boolean handleUndeny(CommandSender sender, String[] args) {
        String targetArg;
        Claim claim;
        if (!AdminAccess.hasMemberManageAccess(sender)) {
            sender.sendMessage(plugin.message("no-permission"));
            return true;
        }
        if (args.length == 3 && sender instanceof Player player) {
            claim = resolveCurrentAdminClaim(player, "/claim admin undeny", current -> claimActionService.canManageMembers(player, current));
            if (claim == null) {
                return true;
            }
            targetArg = args[2];
        } else if (args.length >= 4) {
            claim = resolver.resolveAdminClaimSelector(sender, resolver.joinArgs(args, 2, args.length - 1));
            if (claim == null) {
                return true;
            }
            targetArg = args[args.length - 1];
        } else {
            sender.sendMessage(plugin.message("admin-undeny-usage"));
            return true;
        }
        if ("*".equals(targetArg)) {
            claimService.updateDenyAll(claim, false, actorId(sender));
            sender.sendMessage(plugin.message("admin-deny-all-disabled", "{name}", claim.name()));
            return true;
        }
        OfflinePlayer target = resolver.resolveKnownPlayer(targetArg);
        if (target == null || target.getUniqueId() == null) {
            sender.sendMessage(plugin.message("trust-no-target"));
            return true;
        }
        if (!claimService.removeDeniedMember(claim, target.getUniqueId(), actorId(sender))) {
            sender.sendMessage(plugin.message("admin-deny-missing", "{player}", resolver.displayName(target), "{name}", claim.name()));
            return true;
        }
        sender.sendMessage(plugin.message("admin-deny-removed", "{player}", resolver.displayName(target), "{name}", claim.name()));
        return true;
    }

    boolean handleAdd(CommandSender sender, String[] args) {
        if (!AdminAccess.hasMemberManageAccess(sender)) {
            sender.sendMessage(plugin.message("no-permission"));
            return true;
        }
        if (args.length < 3) {
            sender.sendMessage(plugin.message("admin-add-usage"));
            return true;
        }
        if (!(sender instanceof Player player)) {
            sender.sendMessage(plugin.message("player-only"));
            return true;
        }
        Claim claim = resolveCurrentAdminClaim(player, "/claim admin add", current -> claimActionService.canManageMembers(player, current));
        if (claim == null) {
            return true;
        }
        OfflinePlayer target = resolver.resolveKnownPlayer(resolver.joinArgs(args, 2));
        if (target == null || target.getUniqueId() == null) {
            sender.sendMessage(plugin.message("trust-no-target"));
            return true;
        }
        if (!claimService.addTrustedMember(claim, target.getUniqueId(), player.getUniqueId())) {
            sender.sendMessage(plugin.message("admin-trust-exists", "{player}", resolver.displayName(target), "{name}", claim.name()));
            return true;
        }
        sender.sendMessage(plugin.message("admin-trust-added", "{player}", resolver.displayName(target), "{name}", claim.name()));
        return true;
    }

    boolean handleUnadd(CommandSender sender, String[] args) {
        if (!AdminAccess.hasMemberManageAccess(sender)) {
            sender.sendMessage(plugin.message("no-permission"));
            return true;
        }
        if (args.length < 3) {
            sender.sendMessage(plugin.message("admin-unadd-usage"));
            return true;
        }
        if (!(sender instanceof Player player)) {
            sender.sendMessage(plugin.message("player-only"));
            return true;
        }
        Claim claim = resolveCurrentAdminClaim(player, "/claim admin unadd", current -> claimActionService.canManageMembers(player, current));
        if (claim == null) {
            return true;
        }
        OfflinePlayer target = resolver.resolveKnownPlayer(resolver.joinArgs(args, 2));
        if (target == null || target.getUniqueId() == null) {
            sender.sendMessage(plugin.message("trust-no-target"));
            return true;
        }
        if (!claimService.removeTrustedMember(claim, target.getUniqueId(), player.getUniqueId())) {
            sender.sendMessage(plugin.message("admin-untrust-missing", "{player}", resolver.displayName(target), "{name}", claim.name()));
            return true;
        }
        sender.sendMessage(plugin.message("admin-untrust-removed", "{player}", resolver.displayName(target), "{name}", claim.name()));
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

    private UUID actorId(CommandSender sender) {
        return sender instanceof Player player ? player.getUniqueId() : null;
    }
}
