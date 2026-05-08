package com.coreclaim.command;

import com.coreclaim.CoreClaimPlugin;
import com.coreclaim.model.Claim;
import com.coreclaim.model.ClaimCleanupReason;
import com.coreclaim.model.ClaimFlag;
import com.coreclaim.model.ClaimFlagState;
import com.coreclaim.model.ClaimPermission;
import com.coreclaim.cleanup.ClaimCleanupBaselineMode;
import com.coreclaim.cleanup.ClaimCleanupService;
import com.coreclaim.service.ClaimService;
import com.coreclaim.util.AdminAccess;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

final class ClaimCommandFormatter {

    private final CoreClaimPlugin plugin;
    private final ClaimService claimService;

    ClaimCommandFormatter(CoreClaimPlugin plugin, ClaimService claimService) {
        this.plugin = plugin;
        this.claimService = claimService;
    }

    String claimListRelationText(ClaimService.ClaimListRelation relation) {
        return relation == ClaimService.ClaimListRelation.OWNER ? "&a我的" : "&b已授权";
    }

    void sendEnhancedClaimDetails(CommandSender sender, Claim claim, boolean adminView) {
        boolean canSeeSensitive = adminView;
        if (!canSeeSensitive && sender instanceof Player player) {
            canSeeSensitive = claim.owner().equals(player.getUniqueId()) || AdminAccess.hasViewAccess(player);
        }
        if (adminView) {
            sendDetail(sender, "claim-detail-system", "{value}", claim.systemManaged() ? plugin.plainMessage("state-yes") : plugin.plainMessage("state-no"));
            sendDetail(sender, "claim-detail-quota", "{value}", claimService.countsTowardQuota(claim) ? plugin.plainMessage("state-yes") : plugin.plainMessage("state-no"));
        }
        sendDetail(sender, "claim-detail-name", "{name}", (claim.systemManaged() ? "[SYSTEM] " : "") + claim.name());
        sendDetail(sender, "claim-detail-owner", "{owner}", claim.ownerName());
        sendDetail(sender, "claim-detail-world", "{world}", claim.world());
        sendDetail(sender, "claim-detail-core", "{x}", String.valueOf(claim.centerX()), "{y}", String.valueOf(claim.centerY()), "{z}", String.valueOf(claim.centerZ()));
        sendDetail(sender, "claim-detail-size", "{width}", String.valueOf(claim.width()), "{depth}", String.valueOf(claim.depth()), "{area}", String.valueOf(claim.area()));
        if (claim.fullHeight()) {
            sendDetail(sender, "claim-detail-height-full");
        } else {
            sendDetail(sender, "claim-detail-height-selection", "{min_y}", String.valueOf(claim.minY()), "{max_y}", String.valueOf(claim.maxY()), "{height}", String.valueOf(claim.height()));
        }
        if (claim.hasTeleportPoint()) {
            sendDetail(sender, "claim-detail-teleport-custom", "{point}", formatTeleportPoint(claim));
        } else {
            sendDetail(sender, "claim-detail-teleport-core");
        }
        sendDetail(sender, "claim-detail-deny", "{denied}", String.valueOf(claim.deniedMembers().size()), "{deny_all}", claim.denyAll() ? plugin.plainMessage("state-enabled") : plugin.plainMessage("state-disabled"));
        sendDetail(sender, "claim-detail-rules", "{source}", ruleSourceSummary(claim));
        sendDetail(
            sender,
            "claim-detail-permissions",
            "{place}", stateText(claim.permission(ClaimPermission.PLACE)),
            "{break}", stateText(claim.permission(ClaimPermission.BREAK)),
            "{interact}", stateText(claim.permission(ClaimPermission.INTERACT)),
            "{redstone}", stateText(claim.permission(ClaimPermission.REDSTONE))
        );
        sendDetail(
            sender,
            "claim-detail-permissions-extra",
            "{mob_interact}", stateText(claim.permission(ClaimPermission.MOB_INTERACT)),
            "{explosion}", stateText(claim.permission(ClaimPermission.EXPLOSION)),
            "{bucket}", stateText(claim.permission(ClaimPermission.BUCKET)),
            "{teleport}", stateText(claim.permission(ClaimPermission.TELEPORT)),
            "{flight}", stateText(claim.permission(ClaimPermission.FLIGHT))
        );
        sendDetail(
            sender,
            "claim-detail-permissions-spawn",
            "{animal_spawn}", stateText(claim.permission(ClaimPermission.ANIMAL_SPAWN)),
            "{monster_spawn}", stateText(claim.permission(ClaimPermission.MONSTER_SPAWN))
        );
        sendDetail(sender, "claim-detail-flags", "{flags}", summarizeFlags(claim));
        sendDetail(sender, "claim-detail-enter-message", "{message}", previewMessage(claim.enterMessage(), claim, plugin.plainMessage("claim-detail-default-enter")));
        sendDetail(sender, "claim-detail-leave-message", "{message}", previewMessage(claim.leaveMessage(), claim, plugin.plainMessage("claim-detail-default-leave")));
        if (canSeeSensitive) {
            sendDetail(sender, "claim-detail-trusted", "{players}", joinPlayerNames(claim.trustedMembers()));
            sendDetail(sender, "claim-detail-denied", "{players}", joinPlayerNames(claim.deniedMembers()));
        }
        if (adminView && claim.hasTeleportPoint()) {
            sendDetail(sender, "claim-detail-teleport-yaw-pitch", "{yaw}", formatYawPitch(claim.teleportYaw()), "{pitch}", formatYawPitch(claim.teleportPitch()));
        }
    }

    void sendFlagSummary(CommandSender sender, Claim claim) {
        sender.sendMessage(plugin.message("flag-summary-header", "{name}", claim.name()));
        for (ClaimFlag flag : ClaimFlag.values()) {
            sender.sendMessage(plugin.message("flag-summary-entry", "{flag}", flag.key(), "{state}", flagStateText(flag, claim.flagState(flag))));
        }
    }

    String ruleSourceSummary(Claim claim) {
        String profileName = claimService.ruleProfileName(claim);
        return claimService.hasManualRuleOverrides(claim) ? "&e" + profileName + " &8+ &6手动调整" : "&a" + profileName + " &7(默认生效)";
    }

    String summarizeFlags(Claim claim) {
        ArrayList<String> summary = new ArrayList<>();
        for (ClaimFlag flag : ClaimFlag.values()) {
            ClaimFlagState state = claim.flagState(flag);
            if (state == ClaimFlagState.UNSET) {
                continue;
            }
            summary.add("&#4CC9F0" + flag.key() + "&#94A3B8=" + flagStateText(flag, state));
        }
        return summary.isEmpty() ? plugin.plainMessage("state-not-set") : String.join(plugin.color("&#475569, "), summary);
    }

    String flagStateText(ClaimFlag flag, ClaimFlagState state) {
        if (flag != ClaimFlag.TIME_CYCLE) {
            return flagStateText(state);
        }
        return switch (state) {
            case ALLOW -> plugin.plainMessage("state-time-day");
            case DENY -> plugin.plainMessage("state-time-night");
            case UNSET -> plugin.plainMessage("state-time-world");
        };
    }

    String stateText(boolean enabled) {
        return plugin.plainMessage(enabled ? "state-allow" : "state-deny");
    }

    private void sendDetail(CommandSender sender, String path, String... replacements) {
        sender.sendMessage(plugin.plainMessage(path, replacements));
    }

    void sendCleanupEntries(CommandSender sender, String title, List<ClaimCleanupService.CleanupEntry> entries, boolean showGrace) {
        if (entries.isEmpty()) {
            return;
        }
        sender.sendMessage(plugin.color("&6[Claim] &f" + title + " &8(" + entries.size() + ")"));
        for (ClaimCleanupService.CleanupEntry entry : entries) {
            sender.sendMessage(plugin.color("&7- &f#" + entry.claim().id() + " &e" + entry.claim().name()
                + " &8| &7主人: &b" + entry.claim().ownerName()
                + " &8| &7最后上线: &e" + lastSeenText(entry.lastSeenAt())
                + " &8| &7原因: &c" + cleanupReasonText(entry.reason())
                + (showGrace ? " &8| &7到期: &6" + graceText(entry.state().getDeleteAfterAt()) : "")));
        }
    }

    String cleanupBaselineModeText(ClaimCleanupBaselineMode mode) {
        if (mode == null) {
            return "未知";
        }
        return switch (mode) {
            case EMPTY -> "empty(可追踪空地)";
            case USED -> "used(已有使用证据)";
            case SKIP -> "skip(永久跳过)";
        };
    }

    private String formatTeleportPoint(Claim claim) {
        if (claim == null || !claim.hasTeleportPoint()) {
            return "-";
        }
        return trimDouble(claim.teleportX()) + ", " + trimDouble(claim.teleportY()) + ", " + trimDouble(claim.teleportZ());
    }

    private String formatYawPitch(Float value) {
        return value == null ? "-" : trimDouble(Double.valueOf(value.floatValue()));
    }

    private String trimDouble(Double value) {
        if (value == null) {
            return "-";
        }
        double rounded = (double)Math.round(value * 100.0) / 100.0;
        if (Math.abs(rounded - Math.rint(rounded)) < 1.0E-5) {
            return String.valueOf((long)Math.rint(rounded));
        }
        return String.valueOf(rounded);
    }

    private String cleanupReasonText(ClaimCleanupReason reason) {
        if (reason == null) {
            return "未知";
        }
        return switch (reason) {
            case NO_BUILD -> "无建筑痕迹";
            case NEVER_INTERACTED -> "从未交互";
            case NO_BUILD_AND_NEVER_INTERACTED -> "无建筑且从未交互";
            case NONE -> "无";
        };
    }

    private String lastSeenText(long lastSeenAt) {
        if (lastSeenAt <= 0L) {
            return "无记录";
        }
        long elapsedDays = Math.max(0L, (System.currentTimeMillis() - lastSeenAt) / 86400000L);
        return elapsedDays + "天前";
    }

    private String graceText(long deleteAfterAt) {
        if (deleteAfterAt <= 0L) {
            return "-";
        }
        long remainingDays = Math.max(0L, (deleteAfterAt - System.currentTimeMillis()) / 86400000L);
        return remainingDays + "天后";
    }

    private String flagStateText(ClaimFlagState state) {
        return switch (state) {
            case ALLOW -> plugin.plainMessage("state-allow");
            case DENY -> plugin.plainMessage("state-deny");
            case UNSET -> plugin.plainMessage("state-unset");
        };
    }

    private String previewMessage(String raw, Claim claim, String fallback) {
        return (raw == null || raw.isBlank() ? fallback : raw)
            .replace("%claim_name%", claim.name())
            .replace("{claim_name}", claim.name())
            .replace("%owner%", claim.ownerName())
            .replace("{owner}", claim.ownerName())
            .replace("{name}", claim.name());
    }

    private String joinPlayerNames(Set<UUID> players) {
        if (players == null || players.isEmpty()) {
            return "&7无";
        }
        ArrayList<String> names = new ArrayList<>();
        for (UUID playerId : players) {
            names.add("&e" + displayName(Bukkit.getOfflinePlayer(playerId)));
        }
        return String.join(plugin.color("&7, "), names);
    }

    private String displayName(OfflinePlayer player) {
        return player.getName() == null ? player.getUniqueId().toString() : player.getName();
    }
}
