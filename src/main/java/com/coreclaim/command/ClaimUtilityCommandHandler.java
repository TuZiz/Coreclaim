package com.coreclaim.command;

import com.coreclaim.model.PlayerProfile;
import java.util.Locale;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

final class ClaimUtilityCommandHandler {

    private final CoreClaimCommand command;

    ClaimUtilityCommandHandler(CoreClaimCommand command) {
        this.command = command;
    }

    boolean handleActivity(CommandSender sender, String[] args) {
        if (!command.hasAdminActivityManagePermission(sender)) {
            sender.sendMessage(command.plugin().message("no-permission"));
            return true;
        }
        if (args.length < 3) {
            sender.sendMessage(command.plugin().message("activity-usage"));
            return true;
        }
        String action = args[1].toLowerCase(Locale.ROOT);
        OfflinePlayer target = command.resolver().resolveKnownPlayer(args[2]);
        if (target == null) {
            sender.sendMessage(command.plugin().message("activity-player-unknown"));
            return true;
        }
        String name = command.resolver().displayName(target);
        PlayerProfile profile = command.profileService().getOrCreate(target.getUniqueId(), name);
        switch (action) {
            case "get": {
                sender.sendMessage(command.plugin().message(
                    "activity-get",
                    "{player}", profile.lastKnownName(),
                    "{value}", String.valueOf(profile.activityPoints())
                ));
                break;
            }
            case "set": {
                if (args.length < 4) {
                    sender.sendMessage(command.plugin().message("activity-value-missing"));
                    return true;
                }
                int value = command.resolver().parsePositiveInt(args[3], sender);
                if (value < 0) {
                    return true;
                }
                profile.setActivityPoints(value);
                command.profileService().saveProfile(profile);
                sender.sendMessage(command.plugin().message("activity-set", "{player}", profile.lastKnownName(), "{value}", String.valueOf(value)));
                break;
            }
            case "add": {
                if (args.length < 4) {
                    sender.sendMessage(command.plugin().message("activity-value-missing"));
                    return true;
                }
                int value = command.resolver().parsePositiveInt(args[3], sender);
                if (value < 0) {
                    return true;
                }
                profile.setActivityPoints(profile.activityPoints() + value);
                command.profileService().saveProfile(profile);
                sender.sendMessage(command.plugin().message("activity-add", "{player}", profile.lastKnownName(), "{value}", String.valueOf(value)));
                break;
            }
            case "take": {
                if (args.length < 4) {
                    sender.sendMessage(command.plugin().message("activity-value-missing"));
                    return true;
                }
                int value = command.resolver().parsePositiveInt(args[3], sender);
                if (value < 0) {
                    return true;
                }
                profile.setActivityPoints(Math.max(0, profile.activityPoints() - value));
                command.profileService().saveProfile(profile);
                sender.sendMessage(command.plugin().message("activity-take", "{player}", profile.lastKnownName(), "{value}", String.valueOf(value)));
                break;
            }
            default: {
                sender.sendMessage(command.plugin().message("activity-usage"));
            }
        }
        return true;
    }

    boolean handleReload(CommandSender sender) {
        if (!command.hasAdminOpsPermission(sender)) {
            sender.sendMessage(command.plugin().message("no-permission"));
            return true;
        }
        try {
            int claimCount = command.plugin().reloadPluginResources();
            sender.sendMessage(command.plugin().message("reload-success", "{claims}", String.valueOf(claimCount)));
        } catch (Exception exception) {
            sender.sendMessage(command.plugin().message("reload-failed", "{error}", exception.getMessage() == null ? exception.getClass().getSimpleName() : exception.getMessage()));
        }
        return true;
    }

    boolean handleGiveCore(CommandSender sender, String[] args) {
        if (!command.hasAdminRewardPermission(sender)) {
            sender.sendMessage(command.plugin().message("no-permission"));
            return true;
        }
        if (args.length < 2) {
            sender.sendMessage(command.plugin().message("givecore-usage"));
            return true;
        }
        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            sender.sendMessage(command.plugin().message("target-must-online"));
            return true;
        }
        int amount = 1;
        if (args.length >= 3 && (amount = command.resolver().parsePositiveInt(args[2], sender)) < 0) {
            return true;
        }
        command.plugin().claimCoreFactory().giveClaimCore(target, amount);
        sender.sendMessage(command.plugin().message("claim-core-given", "{player}", target.getName(), "{amount}", String.valueOf(amount)));
        return true;
    }
}
