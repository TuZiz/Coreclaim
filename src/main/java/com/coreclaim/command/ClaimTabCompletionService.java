package com.coreclaim.command;

import com.coreclaim.model.Claim;
import com.coreclaim.model.ClaimFlag;
import com.coreclaim.model.ClaimPermission;
import com.coreclaim.service.ClaimActionService;
import com.coreclaim.service.ClaimService;
import com.coreclaim.service.ProfileService;
import com.coreclaim.util.AdminAccess;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

final class ClaimTabCompletionService {

    private final ClaimService claimService;
    private final ProfileService profileService;
    private final ClaimActionService claimActionService;

    ClaimTabCompletionService(CoreClaimCommand command) {
        this.claimService = command.claimService();
        this.profileService = command.profileService();
        this.claimActionService = command.claimActionService();
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
            return filter(options, args[1]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("add")) {
            options.addAll(knownPlayerNames());
            return filter(options, args[1]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("unadd") && sender instanceof Player player) {
            options.addAll(currentEditableClaimMemberNames(player));
            return filter(options, args[1]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("remove") && sender instanceof Player player) {
            options.addAll(claimNames(claimService.claimsOf(player.getUniqueId())));
            return filterByJoinedInput(options, args, 1);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("transfer")) {
            options.addAll(knownPlayerNames());
            if (sender instanceof Player player) {
                options.add("accept");
                options.add("deny");
                options.addAll(claimNames(claimService.claimsOf(player.getUniqueId())));
            }
            return filter(options, args[1]);
        }
        if (args.length == 2 && (args[0].equalsIgnoreCase("deny") || args[0].equalsIgnoreCase("undeny"))) {
            options.addAll(ClaimDenyTargets.allTargetOptions());
            options.addAll(knownPlayerNames());
            return filter(options, args[1]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("edit") && hasAdminClaimManagePermission(sender)) {
            options.addAll(claimNames(claimService.allClaims()));
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
        if (args.length == 3 && args[0].equalsIgnoreCase("admin") && (args[1].equalsIgnoreCase("playerclaims") || args[1].equalsIgnoreCase("claims"))) {
            options.addAll(knownPlayerNames());
            return filter(options, args[2]);
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("admin") && args[1].equalsIgnoreCase("remove")) {
            options.addAll(claimSelectorOptions(claimService.allClaims()));
            return filterByJoinedInput(options, args, 2);
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("admin") && args[1].equalsIgnoreCase("add")) {
            options.addAll(knownPlayerNames());
            return filter(options, args[2]);
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("admin") && args[1].equalsIgnoreCase("unadd") && sender instanceof Player player) {
            options.addAll(currentAdminClaimMemberNames(player));
            return filter(options, args[2]);
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("admin") && (args[1].equalsIgnoreCase("deny") || args[1].equalsIgnoreCase("undeny"))) {
            options.addAll(ClaimDenyTargets.allTargetOptions());
            options.addAll(knownPlayerNames());
            return filter(options, args[2]);
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("admin") && (args[1].equalsIgnoreCase("permission") || args[1].equalsIgnoreCase("perm"))) {
            options.addAll(permissionKeys());
            return filter(options, args[2]);
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("admin") && args[1].equalsIgnoreCase("cleanup")) {
            options.add("list");
            options.add("run");
            options.add("skip");
            options.add("baseline");
            return filter(options, args[2]);
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("admin") && (args[1].equalsIgnoreCase("info") || args[1].equalsIgnoreCase("transfer") || args[1].equalsIgnoreCase("diagnose"))) {
            options.addAll(claimSelectorOptions(claimService.allClaims()));
            return filterByJoinedInput(options, args, 2);
        }
        if (args.length == 4 && args[0].equalsIgnoreCase("admin") && (args[1].equalsIgnoreCase("permission") || args[1].equalsIgnoreCase("perm"))) {
            options.add("allow");
            options.add("deny");
            if (ClaimFlag.fromKey(args[2]) != null) {
                options.add("unset");
            }
            return filter(options, args[3]);
        }
        if (args.length == 4 && args[0].equalsIgnoreCase("admin") && args[1].equalsIgnoreCase("cleanup")) {
            if (args[2].equalsIgnoreCase("skip") || args[2].equalsIgnoreCase("baseline")) {
                options.addAll(claimSelectorOptions(claimService.allClaims()));
            }
            return filterByJoinedInput(options, args, 3);
        }
        if (args.length == 5 && args[0].equalsIgnoreCase("admin") && args[1].equalsIgnoreCase("cleanup") && args[2].equalsIgnoreCase("baseline")) {
            options.add("empty");
            options.add("used");
            options.add("skip");
            return filter(options, args[4]);
        }
        if (args.length > 2 && (args[0].equalsIgnoreCase("show") || args[0].equalsIgnoreCase("tp") || args[0].equalsIgnoreCase("edit") || args[0].equalsIgnoreCase("remove"))) {
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
                } else if (args[0].equalsIgnoreCase("remove")) {
                    options.addAll(claimNames(claimService.claimsOf(player.getUniqueId())));
                }
            } else if (args[0].equalsIgnoreCase("edit") && hasAdminClaimManagePermission(sender)) {
                options.addAll(claimNames(claimService.allClaims()));
            }
            return filterByJoinedInput(options, args, 1);
        }
        if (args.length > 3 && args[0].equalsIgnoreCase("admin") && (args[1].equalsIgnoreCase("info") || args[1].equalsIgnoreCase("remove") || args[1].equalsIgnoreCase("transfer") || args[1].equalsIgnoreCase("diagnose") || args[1].equalsIgnoreCase("cleanup") && (args[2].equalsIgnoreCase("skip") || args[2].equalsIgnoreCase("baseline")))) {
            String candidateName;
            if (args[1].equalsIgnoreCase("transfer")) {
                candidateName = joinArgs(args, 2, args.length - 1);
                if (hasUniqueMatchingClaim(claimService.allClaims(), candidateName)) {
                    options.addAll(knownPlayerNames());
                    return filter(options, args[args.length - 1]);
                }
            }
            if (args[1].equalsIgnoreCase("cleanup") && args[2].equalsIgnoreCase("baseline")) {
                candidateName = joinArgs(args, 3, args.length - 1);
                if (hasUniqueMatchingClaim(claimService.allClaims(), candidateName)) {
                    options.add("empty");
                    options.add("used");
                    options.add("skip");
                    return filter(options, args[args.length - 1]);
                }
            }
            options.addAll(claimNames(claimService.allClaims()));
            return filterByJoinedInput(options, args, args[1].equalsIgnoreCase("cleanup") ? 3 : 2);
        }
        if (args.length > 2 && args[0].equalsIgnoreCase("transfer") && sender instanceof Player player) {
            String candidateName;
            List<Claim> ownedClaims = claimService.claimsOf(player.getUniqueId());
            if (hasUniqueMatchingClaim(ownedClaims, candidateName = joinArgs(args, 1, args.length - 1))) {
                options.addAll(knownPlayerNames());
                return filter(options, args[args.length - 1]);
            }
            options.addAll(claimNames(ownedClaims));
            return filterByJoinedInput(options, args, 1);
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

    private List<String> knownPlayerNames() {
        LinkedHashSet<String> names = new LinkedHashSet<>();
        for (Player online : Bukkit.getOnlinePlayers()) {
            names.add(online.getName());
        }
        names.addAll(profileService.knownPlayerNames());
        return new ArrayList<>(names);
    }

    private List<String> onlinePlayerNames() {
        ArrayList<String> names = new ArrayList<>();
        for (Player online : Bukkit.getOnlinePlayers()) {
            names.add(online.getName());
        }
        return names;
    }

    private List<String> currentEditableClaimMemberNames(Player player) {
        Claim claim = claimActionService.findCurrentPresenceClaim(player);
        if (claim == null || !claimActionService.canManageMembers(player, claim)) {
            return List.of();
        }
        return trustedMemberNames(claim);
    }

    private List<String> currentAdminClaimMemberNames(Player player) {
        Claim claim = claimActionService.findCurrentPresenceClaim(player);
        if (claim == null || !claimActionService.canManageMembers(player, claim)) {
            return List.of();
        }
        return trustedMemberNames(claim);
    }

    private List<String> trustedMemberNames(Claim claim) {
        if (claim == null || claim.trustedMembers().isEmpty()) {
            return List.of();
        }
        return claim.trustedMembers().stream()
            .map(Bukkit::getOfflinePlayer)
            .map(this::displayName)
            .distinct()
            .sorted(String.CASE_INSENSITIVE_ORDER)
            .toList();
    }

    private List<String> claimIdOptions() {
        return claimService.allClaims().stream()
            .map(claim -> String.valueOf(claim.id()))
            .distinct()
            .toList();
    }

    private String displayName(OfflinePlayer player) {
        return player.getName() == null ? player.getUniqueId().toString() : player.getName();
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
        return claims.stream().map(Claim::name).distinct().toList();
    }

    private boolean hasUniqueMatchingClaim(List<Claim> claims, String rawName) {
        List<Claim> matchingClaims = claimService.findClaimsByNameFresh(rawName);
        return claims.stream().filter(matchingClaims::contains).count() == 1L;
    }

    private List<String> flagKeys() {
        return Arrays.stream(ClaimFlag.values()).map(ClaimFlag::key).toList();
    }

    private List<String> permissionKeys() {
        ArrayList<String> keys = new ArrayList<>(Arrays.stream(ClaimPermission.values())
            .map(permission -> permission.name().toLowerCase(Locale.ROOT))
            .toList());
        keys.addAll(flagKeys());
        return keys;
    }

    private List<String> claimSelectorOptions(List<Claim> claims) {
        ArrayList<String> selectors = new ArrayList<>();
        for (Claim claim : claims) {
            selectors.add("#" + claim.id());
            selectors.add(claim.name());
        }
        return selectors.stream().distinct().toList();
    }

    private String joinArgs(String[] args, int startInclusive) {
        return joinArgs(args, startInclusive, args.length);
    }

    private String joinArgs(String[] args, int startInclusive, int endExclusive) {
        if (args == null || startInclusive >= endExclusive || startInclusive < 0 || endExclusive > args.length) {
            return "";
        }
        return String.join(" ", Arrays.copyOfRange(args, startInclusive, endExclusive)).trim();
    }
}
