package com.coreclaim.command;

import com.coreclaim.CoreClaimPlugin;
import com.coreclaim.model.Claim;
import com.coreclaim.model.ClaimPermission;
import com.coreclaim.service.ClaimService;
import com.coreclaim.service.ProfileService;
import com.coreclaim.util.AdminAccess;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.function.Predicate;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

final class ClaimCommandResolver {

    private final CoreClaimPlugin plugin;
    private final ClaimService claimService;
    private final ProfileService profileService;

    ClaimCommandResolver(CoreClaimPlugin plugin, ClaimService claimService, ProfileService profileService) {
        this.plugin = plugin;
        this.claimService = claimService;
        this.profileService = profileService;
    }

    int parsePositiveInt(String raw, CommandSender sender) {
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

    int parseClaimId(String raw, CommandSender sender) {
        try {
            int value = Integer.parseInt(raw);
            if (value <= 0) {
                sender.sendMessage(plugin.message("claim-id-invalid", "{value}", raw));
                return -1;
            }
            return value;
        } catch (NumberFormatException exception) {
            sender.sendMessage(plugin.message("claim-id-invalid", "{value}", raw));
            return -1;
        }
    }

    String displayName(OfflinePlayer player) {
        return player.getName() == null ? player.getUniqueId().toString() : player.getName();
    }

    Claim resolveOwnedClaimByName(Player player, String rawName) {
        return resolveClaimByName(player, rawName, claim -> claim.owner().equals(player.getUniqueId()));
    }

    Claim resolveAccessibleClaimByName(Player player, String rawName) {
        return resolveClaimByName(player, rawName, claim -> AdminAccess.hasForceBypass(player)
            || claim.owner().equals(player.getUniqueId())
            || claimService.canAccess(claim, player.getUniqueId()));
    }

    Claim resolveTeleportClaimByName(Player player, String rawName) {
        return resolveClaimByName(player, rawName, claim -> AdminAccess.hasForceBypass(player)
            || claimService.hasPermission(claim, player.getUniqueId(), ClaimPermission.TELEPORT));
    }

    Claim resolveAdminClaimByName(CommandSender sender, String rawName) {
        return resolveClaimByName(sender, rawName, claim -> true);
    }

    Claim resolveAdminClaimSelector(CommandSender sender, String rawSelector) {
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

    String joinArgs(String[] args, int startInclusive) {
        return joinArgs(args, startInclusive, args.length);
    }

    String joinArgs(String[] args, int startInclusive, int endExclusive) {
        if (args == null || startInclusive >= endExclusive || startInclusive < 0 || endExclusive > args.length) {
            return "";
        }
        return String.join(" ", Arrays.copyOfRange(args, startInclusive, endExclusive)).trim();
    }

    boolean isFlagListInput(String rawValue) {
        if (rawValue == null) {
            return false;
        }
        String normalized = rawValue.trim().toLowerCase(Locale.ROOT);
        return normalized.equals("list") || normalized.equals("info") || normalized.equals("show");
    }

    ClaimPermission parsePermission(String rawValue) {
        if (rawValue == null || rawValue.isBlank()) {
            return null;
        }
        return switch (rawValue.trim().toLowerCase(Locale.ROOT).replace('_', '-')) {
            case "place" -> ClaimPermission.PLACE;
            case "break" -> ClaimPermission.BREAK;
            case "interact" -> ClaimPermission.INTERACT;
            case "mob-interact", "mob", "entity", "entity-interact", "mob-damage", "entity-damage" -> ClaimPermission.MOB_INTERACT;
            case "animal-spawn", "animal", "animals", "passive-spawn", "passive-mob-spawn" -> ClaimPermission.ANIMAL_SPAWN;
            case "monster-spawn", "monster", "monsters", "hostile-spawn", "hostile-mob-spawn" -> ClaimPermission.MONSTER_SPAWN;
            case "redstone" -> ClaimPermission.REDSTONE;
            case "explosion" -> ClaimPermission.EXPLOSION;
            case "bucket" -> ClaimPermission.BUCKET;
            case "teleport", "tp" -> ClaimPermission.TELEPORT;
            case "flight", "fly" -> ClaimPermission.FLIGHT;
            default -> null;
        };
    }

    Boolean parseAllowDeny(String rawValue) {
        if (rawValue == null || rawValue.isBlank()) {
            return null;
        }
        return switch (rawValue.trim().toLowerCase(Locale.ROOT)) {
            case "allow", "on", "true", "yes" -> true;
            case "deny", "off", "false", "no" -> false;
            default -> null;
        };
    }

    OfflinePlayer resolveKnownPlayer(String rawName) {
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

    Player resolveOnlinePlayer(String rawName) {
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

    private Claim resolveClaimByName(CommandSender sender, String rawName, Predicate<Claim> filter) {
        String claimName = normalizeQuery(rawName);
        if (claimName == null) {
            sender.sendMessage(plugin.message("claim-name-not-found", "{name}", rawName == null ? "" : rawName.trim()));
            return null;
        }
        List<Claim> matches = claimService.findClaimsByNameFresh(claimName).stream().filter(filter).toList();
        if (matches.isEmpty()) {
            sender.sendMessage(plugin.message("claim-name-not-found", "{name}", claimName));
            return null;
        }
        if (matches.size() > 1) {
            sender.sendMessage(plugin.message("claim-name-ambiguous", "{name}", claimName));
            for (Claim match : matches) {
                sender.sendMessage(plugin.color("&7- &f#" + match.id() + " &e" + match.name() + " &8@ &b" + claimService.displayServerId(match) + " &7[" + match.world() + " " + match.centerX() + ", " + match.centerZ() + "]"));
            }
            return null;
        }
        return matches.get(0);
    }

    private String normalizeQuery(String rawValue) {
        if (rawValue == null) {
            return null;
        }
        String trimmed = rawValue.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
