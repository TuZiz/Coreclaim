package com.coreclaim.command;

import com.coreclaim.CoreClaimPlugin;
import com.coreclaim.model.Claim;
import com.coreclaim.model.ClaimFlag;
import com.coreclaim.model.ClaimFlagState;
import com.coreclaim.model.ClaimPermission;
import com.coreclaim.service.ClaimActionService;
import com.coreclaim.service.ClaimService;
import com.coreclaim.util.AdminAccess;
import java.util.UUID;
import java.util.function.Predicate;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

final class ClaimAdminRuleCommandHandler {

    private final CoreClaimPlugin plugin;
    private final ClaimService claimService;
    private final ClaimActionService claimActionService;
    private final ClaimCommandFormatter formatter;
    private final ClaimCommandResolver resolver;

    ClaimAdminRuleCommandHandler(
        CoreClaimPlugin plugin,
        ClaimService claimService,
        ClaimActionService claimActionService,
        ClaimCommandFormatter formatter,
        ClaimCommandResolver resolver
    ) {
        this.plugin = plugin;
        this.claimService = claimService;
        this.claimActionService = claimActionService;
        this.formatter = formatter;
        this.resolver = resolver;
    }

    boolean handlePermission(CommandSender sender, String[] args) {
        String stateInput;
        String permissionInput;
        if (args.length == 4 && sender instanceof Player player) {
            permissionInput = args[2];
            stateInput = args[3];
        } else if (args.length >= 5) {
            permissionInput = args[args.length - 2];
            stateInput = args[args.length - 1];
        } else {
            sender.sendMessage(plugin.message("admin-permission-usage"));
            return true;
        }
        ClaimPermission permission = resolver.parsePermission(permissionInput);
        ClaimFlag flag = permission == null ? ClaimFlag.fromKey(permissionInput) : null;
        if (permission == null && flag == null) {
            sender.sendMessage(plugin.message("admin-permission-invalid", "{permission}", permissionInput));
            return true;
        }

        boolean managesPermission = permission != null;
        if (managesPermission && !AdminAccess.hasPermissionManageAccess(sender)) {
            sender.sendMessage(plugin.message("no-permission"));
            return true;
        }
        if (!managesPermission && !AdminAccess.hasPermissionManageAccess(sender) && !AdminAccess.hasFlagManageAccess(sender)) {
            sender.sendMessage(plugin.message("no-permission"));
            return true;
        }

        Claim claim;
        if (args.length == 4 && sender instanceof Player player) {
            claim = resolveCurrentAdminClaim(player, "/claim admin permission", current ->
                managesPermission
                    ? claimActionService.canManagePermissions(player, current)
                    : claimActionService.canManagePermissions(player, current) || claimActionService.canManageFlags(player, current)
            );
            if (claim == null) {
                return true;
            }
        } else {
            claim = resolver.resolveAdminClaimSelector(sender, resolver.joinArgs(args, 2, args.length - 2));
            if (claim == null) {
                return true;
            }
        }

        UUID actorId = actorId(sender);
        if (managesPermission) {
            Boolean allowed = resolver.parseAllowDeny(stateInput);
            if (allowed == null) {
                sender.sendMessage(plugin.message("admin-permission-state-invalid"));
                return true;
            }
            claimService.updatePermission(claim, permission, allowed, actorId);
            sender.sendMessage(plugin.message(
                "admin-permission-updated",
                "{name}", claim.name(),
                "{permission}", permission.key(),
                "{state}", formatter.stateText(allowed)
            ));
            return true;
        }

        ClaimFlagState state = ClaimFlagState.fromInput(stateInput);
        if (state == null) {
            sender.sendMessage(plugin.message("admin-permission-state-invalid"));
            return true;
        }
        claimService.updateFlagState(claim, flag, state, actorId);
        sender.sendMessage(plugin.message(
            "admin-permission-updated",
            "{name}", claim.name(),
            "{permission}", flag.key(),
            "{state}", formatter.flagStateText(flag, state)
        ));
        return true;
    }

    boolean handleFlag(CommandSender sender, String[] args) {
        sender.sendMessage(plugin.message("admin-flag-command-migrated"));
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
