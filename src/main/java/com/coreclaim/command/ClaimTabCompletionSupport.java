package com.coreclaim.command;

import com.coreclaim.model.Claim;
import com.coreclaim.model.ClaimFlag;
import com.coreclaim.model.ClaimPermission;
import com.coreclaim.profile.ProfileService;
import com.coreclaim.service.ClaimActionService;
import com.coreclaim.service.ClaimService;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

final class ClaimTabCompletionSupport {

    private final ClaimService claimService;
    private final ProfileService profileService;
    private final ClaimActionService claimActionService;

    ClaimTabCompletionSupport(
        ClaimService claimService,
        ProfileService profileService,
        ClaimActionService claimActionService
    ) {
        this.claimService = claimService;
        this.profileService = profileService;
        this.claimActionService = claimActionService;
    }

    List<String> knownPlayerNames() {
        return onlinePlayerNames();
    }

    List<String> onlinePlayerNames() {
        ArrayList<String> names = new ArrayList<>();
        for (Player online : Bukkit.getOnlinePlayers()) {
            names.add(online.getName());
        }
        return names;
    }

    List<String> currentEditableClaimMemberNames(Player player) {
        Claim claim = claimActionService.findCurrentPresenceClaim(player);
        if (claim == null || !claimActionService.canManageMembers(player, claim)) {
            return List.of();
        }
        return trustedMemberNames(claim);
    }

    List<String> currentAdminClaimMemberNames(Player player) {
        Claim claim = claimActionService.findCurrentPresenceClaim(player);
        if (claim == null || !claimActionService.canManageMembers(player, claim)) {
            return List.of();
        }
        return trustedMemberNames(claim);
    }

    List<String> trustedMemberNames(Claim claim) {
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

    List<String> claimIdOptions() {
        return claimService.allClaims().stream()
            .map(claim -> String.valueOf(claim.id()))
            .distinct()
            .toList();
    }

    List<String> filter(List<String> options, String input) {
        String lowered = input.toLowerCase(Locale.ROOT);
        return options.stream()
            .distinct()
            .filter(option -> option.toLowerCase(Locale.ROOT).startsWith(lowered))
            .toList();
    }

    List<String> filterByJoinedInput(List<String> options, String[] args, int startInclusive) {
        return filter(options, joinArgs(args, startInclusive));
    }

    List<String> claimNames(List<Claim> claims) {
        return claims.stream().map(Claim::name).distinct().toList();
    }

    boolean hasUniqueMatchingClaim(List<Claim> claims, String rawName) {
        List<Claim> matchingClaims = claimService.findClaimsByNameFresh(rawName);
        return claims.stream().filter(matchingClaims::contains).count() == 1L;
    }

    List<String> permissionKeys() {
        ArrayList<String> keys = new ArrayList<>(Arrays.stream(ClaimPermission.values())
            .map(ClaimPermission::key)
            .toList());
        keys.addAll(flagKeys());
        return keys;
    }

    List<String> claimSelectorOptions(List<Claim> claims) {
        ArrayList<String> selectors = new ArrayList<>();
        for (Claim claim : claims) {
            selectors.add("#" + claim.id());
            selectors.add(claim.name());
        }
        return selectors.stream().distinct().toList();
    }

    String joinArgs(String[] args, int startInclusive) {
        return joinArgs(args, startInclusive, args.length);
    }

    String joinArgs(String[] args, int startInclusive, int endExclusive) {
        if (args == null || startInclusive >= endExclusive || startInclusive < 0 || endExclusive > args.length) {
            return "";
        }
        return String.join(" ", Arrays.copyOfRange(args, startInclusive, endExclusive)).trim();
    }

    private String displayName(OfflinePlayer player) {
        return player.getName() == null ? player.getUniqueId().toString() : player.getName();
    }

    private List<String> flagKeys() {
        return Arrays.stream(ClaimFlag.values()).map(ClaimFlag::key).toList();
    }
}
