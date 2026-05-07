package com.coreclaim.command;

import com.coreclaim.model.Claim;
import com.coreclaim.model.ClaimFlag;
import com.coreclaim.model.ClaimPermission;
import com.coreclaim.service.ClaimService;
import com.coreclaim.util.AdminAccess;
import java.util.ArrayList;
import java.util.List;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

final class ClaimTabCompletionService {

    private final ClaimService claimService;
    private final ClaimTabCompletionSupport support;

    ClaimTabCompletionService(CoreClaimCommand command) {
        this.claimService = command.claimService();
        this.support = new ClaimTabCompletionSupport(
            command.claimService(),
            command.profileService(),
            command.claimActionService()
        );
    }

    List<String> complete(CommandSender sender, Command command, String alias, String[] args) {
        if (args == null) {
            return List.of();
        }
        ArrayList<String> options = new ArrayList<>();
        if (args.length == 1) {
            options.add("help");
            options.add("info");
            options.add("list");
            options.add("menu");
            options.add("set");
            options.add("create");
            options.add("tp");
            options.add("tpset");
            options.add("add");
            options.add("unadd");
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
            return support.filter(options, args[0]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("expand")) {
            options.add("east");
            options.add("south");
            options.add("west");
            options.add("north");
            return support.filter(options, args[1]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("givecore")) {
            options.addAll(support.onlinePlayerNames());
            return support.filter(options, args[1]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("activity")) {
            options.add("get");
            options.add("set");
            options.add("add");
            options.add("take");
            return support.filter(options, args[1]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("admin") && hasAnyAdminPermission(sender)) {
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
            options.add("cleanup");
            options.add("setserver");
            return support.filter(options, args[1]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("add")) {
            options.addAll(support.knownPlayerNames());
            return support.filter(options, args[1]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("unadd") && sender instanceof Player player) {
            options.addAll(support.currentEditableClaimMemberNames(player));
            return support.filter(options, args[1]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("remove") && sender instanceof Player player) {
            options.addAll(support.claimNames(claimService.claimsOf(player.getUniqueId())));
            return support.filterByJoinedInput(options, args, 1);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("transfer")) {
            options.addAll(support.knownPlayerNames());
            if (sender instanceof Player player) {
                options.add("accept");
                options.add("deny");
                options.addAll(support.claimNames(claimService.claimsOf(player.getUniqueId())));
            }
            return support.filter(options, args[1]);
        }
        if (args.length == 2 && (args[0].equalsIgnoreCase("deny") || args[0].equalsIgnoreCase("undeny"))) {
            options.addAll(ClaimDenyTargets.allTargetOptions());
            options.addAll(support.knownPlayerNames());
            return support.filter(options, args[1]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("edit") && hasAdminClaimManagePermission(sender)) {
            options.addAll(support.claimNames(claimService.allClaims()));
            return support.filterByJoinedInput(options, args, 1);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("show")) {
            options.add("auto");
            if (sender instanceof Player player) {
                options.addAll(support.claimNames(claimService.allClaims().stream()
                    .filter(claim -> hasAdminForcePermission(player)
                        || claim.owner().equals(player.getUniqueId())
                        || claimService.canAccess(claim, player.getUniqueId()))
                    .toList()));
            } else {
                options.addAll(support.claimNames(claimService.allClaims()));
            }
            return support.filterByJoinedInput(options, args, 1);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("tp")) {
            if (sender instanceof Player player) {
                options.addAll(support.claimNames(claimService.allClaims().stream()
                    .filter(claim -> hasAdminForcePermission(player)
                        || claimService.hasPermission(claim, player.getUniqueId(), ClaimPermission.TELEPORT))
                    .toList()));
            }
            return support.filterByJoinedInput(options, args, 1);
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("show") && args[1].equalsIgnoreCase("auto")) {
            options.add("on");
            options.add("off");
            return support.filter(options, args[2]);
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("activity")) {
            options.addAll(support.knownPlayerNames());
            return support.filter(options, args[2]);
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("admin") && args[1].equalsIgnoreCase("setserver")) {
            options.addAll(support.claimIdOptions());
            return support.filter(options, args[2]);
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("admin") && args[1].equalsIgnoreCase("create")) {
            options.add("system");
            return support.filter(options, args[2]);
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("admin") && (args[1].equalsIgnoreCase("playerclaims") || args[1].equalsIgnoreCase("claims"))) {
            options.addAll(support.knownPlayerNames());
            return support.filter(options, args[2]);
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("admin") && args[1].equalsIgnoreCase("remove")) {
            options.addAll(support.claimSelectorOptions(claimService.allClaims()));
            return support.filterByJoinedInput(options, args, 2);
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("admin") && args[1].equalsIgnoreCase("add")) {
            options.addAll(support.knownPlayerNames());
            return support.filter(options, args[2]);
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("admin") && args[1].equalsIgnoreCase("unadd") && sender instanceof Player player) {
            options.addAll(support.currentAdminClaimMemberNames(player));
            return support.filter(options, args[2]);
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("admin") && (args[1].equalsIgnoreCase("deny") || args[1].equalsIgnoreCase("undeny"))) {
            options.addAll(ClaimDenyTargets.allTargetOptions());
            options.addAll(support.knownPlayerNames());
            return support.filter(options, args[2]);
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("admin") && (args[1].equalsIgnoreCase("permission") || args[1].equalsIgnoreCase("perm"))) {
            options.addAll(support.permissionKeys());
            return support.filter(options, args[2]);
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("admin") && args[1].equalsIgnoreCase("cleanup")) {
            options.add("list");
            options.add("run");
            options.add("skip");
            options.add("baseline");
            return support.filter(options, args[2]);
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("admin") && (args[1].equalsIgnoreCase("info") || args[1].equalsIgnoreCase("transfer") || args[1].equalsIgnoreCase("diagnose"))) {
            options.addAll(support.claimSelectorOptions(claimService.allClaims()));
            return support.filterByJoinedInput(options, args, 2);
        }
        if (args.length == 4 && args[0].equalsIgnoreCase("admin") && (args[1].equalsIgnoreCase("permission") || args[1].equalsIgnoreCase("perm"))) {
            options.add("allow");
            options.add("deny");
            if (ClaimFlag.fromKey(args[2]) != null) {
                options.add("unset");
            }
            return support.filter(options, args[3]);
        }
        if (args.length == 4 && args[0].equalsIgnoreCase("admin") && args[1].equalsIgnoreCase("cleanup")) {
            if (args[2].equalsIgnoreCase("skip") || args[2].equalsIgnoreCase("baseline")) {
                options.addAll(support.claimSelectorOptions(claimService.allClaims()));
            }
            return support.filterByJoinedInput(options, args, 3);
        }
        if (args.length == 5 && args[0].equalsIgnoreCase("admin") && args[1].equalsIgnoreCase("cleanup") && args[2].equalsIgnoreCase("baseline")) {
            options.add("empty");
            options.add("used");
            options.add("skip");
            return support.filter(options, args[4]);
        }
        if (args.length > 2 && (args[0].equalsIgnoreCase("show") || args[0].equalsIgnoreCase("tp") || args[0].equalsIgnoreCase("edit") || args[0].equalsIgnoreCase("remove"))) {
            if (args[0].equalsIgnoreCase("show") && args[1].equalsIgnoreCase("auto")) {
                return options;
            }
            if (sender instanceof Player player) {
                if (args[0].equalsIgnoreCase("show")) {
                    options.addAll(support.claimNames(claimService.allClaims().stream()
                        .filter(claim -> hasAdminForcePermission(player)
                            || claim.owner().equals(player.getUniqueId())
                            || claimService.canAccess(claim, player.getUniqueId()))
                        .toList()));
                } else if (args[0].equalsIgnoreCase("tp")) {
                    options.addAll(support.claimNames(claimService.allClaims().stream()
                        .filter(claim -> hasAdminForcePermission(player)
                            || claimService.hasPermission(claim, player.getUniqueId(), ClaimPermission.TELEPORT))
                        .toList()));
                } else if (args[0].equalsIgnoreCase("edit") && hasAdminClaimManagePermission(sender)) {
                    options.addAll(support.claimNames(claimService.allClaims()));
                } else if (args[0].equalsIgnoreCase("remove")) {
                    options.addAll(support.claimNames(claimService.claimsOf(player.getUniqueId())));
                }
            } else if (args[0].equalsIgnoreCase("edit") && hasAdminClaimManagePermission(sender)) {
                options.addAll(support.claimNames(claimService.allClaims()));
            }
            return support.filterByJoinedInput(options, args, 1);
        }
        if (args.length > 3 && args[0].equalsIgnoreCase("admin") && (args[1].equalsIgnoreCase("info") || args[1].equalsIgnoreCase("remove") || args[1].equalsIgnoreCase("transfer") || args[1].equalsIgnoreCase("diagnose") || args[1].equalsIgnoreCase("cleanup") && (args[2].equalsIgnoreCase("skip") || args[2].equalsIgnoreCase("baseline")))) {
            String candidateName;
            if (args[1].equalsIgnoreCase("transfer")) {
                candidateName = support.joinArgs(args, 2, args.length - 1);
                if (support.hasUniqueMatchingClaim(claimService.allClaims(), candidateName)) {
                    options.addAll(support.knownPlayerNames());
                    return support.filter(options, args[args.length - 1]);
                }
            }
            if (args[1].equalsIgnoreCase("diagnose")) {
                if (args.length > 4 && (args[args.length - 2].equalsIgnoreCase("--player") || args[args.length - 2].equalsIgnoreCase("-p"))) {
                    options.addAll(support.knownPlayerNames());
                    return support.filter(options, args[args.length - 1]);
                }
                candidateName = support.joinArgs(args, 2, args.length - 1);
                if (support.hasUniqueMatchingClaim(claimService.allClaims(), candidateName)) {
                    options.add("--player");
                    return support.filter(options, args[args.length - 1]);
                }
            }
            if (args[1].equalsIgnoreCase("cleanup") && args[2].equalsIgnoreCase("baseline")) {
                candidateName = support.joinArgs(args, 3, args.length - 1);
                if (support.hasUniqueMatchingClaim(claimService.allClaims(), candidateName)) {
                    options.add("empty");
                    options.add("used");
                    options.add("skip");
                    return support.filter(options, args[args.length - 1]);
                }
            }
            options.addAll(support.claimNames(claimService.allClaims()));
            return support.filterByJoinedInput(options, args, args[1].equalsIgnoreCase("cleanup") ? 3 : 2);
        }
        if (args.length > 2 && args[0].equalsIgnoreCase("transfer") && sender instanceof Player player) {
            String candidateName;
            List<Claim> ownedClaims = claimService.claimsOf(player.getUniqueId());
            if (support.hasUniqueMatchingClaim(ownedClaims, candidateName = support.joinArgs(args, 1, args.length - 1))) {
                options.addAll(support.knownPlayerNames());
                return support.filter(options, args[args.length - 1]);
            }
            options.addAll(support.claimNames(ownedClaims));
            return support.filterByJoinedInput(options, args, 1);
        }
        return options;
    }

    private boolean hasAnyAdminPermission(CommandSender sender) {
        return AdminAccess.hasAnyAdminNode(sender);
    }

    private boolean hasAdminForcePermission(CommandSender sender) {
        return AdminAccess.hasForceBypass(sender);
    }

    private boolean hasAdminClaimManagePermission(CommandSender sender) {
        return AdminAccess.hasClaimManageAccess(sender);
    }
}
