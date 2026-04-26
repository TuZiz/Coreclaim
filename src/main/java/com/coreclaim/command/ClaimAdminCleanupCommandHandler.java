package com.coreclaim.command;

import com.coreclaim.CoreClaimPlugin;
import com.coreclaim.model.Claim;
import com.coreclaim.service.ClaimCleanupBaselineMode;
import com.coreclaim.service.ClaimCleanupService;
import java.util.Locale;
import org.bukkit.command.CommandSender;

final class ClaimAdminCleanupCommandHandler {

    private final CoreClaimPlugin plugin;
    private final ClaimCleanupService claimCleanupService;
    private final ClaimCommandFormatter formatter;
    private final ClaimCommandResolver resolver;

    ClaimAdminCleanupCommandHandler(
        CoreClaimPlugin plugin,
        ClaimCleanupService claimCleanupService,
        ClaimCommandFormatter formatter,
        ClaimCommandResolver resolver
    ) {
        this.plugin = plugin;
        this.claimCleanupService = claimCleanupService;
        this.formatter = formatter;
        this.resolver = resolver;
    }

    boolean handle(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(plugin.message("admin-cleanup-usage"));
            return true;
        }
        return switch (args[2].toLowerCase(Locale.ROOT)) {
            case "list" -> list(sender);
            case "run" -> run(sender);
            case "skip" -> skip(sender, args);
            case "baseline" -> baseline(sender, args);
            default -> {
                sender.sendMessage(plugin.message("admin-cleanup-usage"));
                yield true;
            }
        };
    }

    private boolean list(CommandSender sender) {
        ClaimCleanupService.CleanupSnapshot snapshot = claimCleanupService.snapshot();
        if (!plugin.settings().inactiveClaimCleanupEnabled()) {
            sender.sendMessage(plugin.message("admin-cleanup-disabled"));
        }
        sender.sendMessage(plugin.message(
            "admin-cleanup-list-header",
            "{candidates}", String.valueOf(snapshot.candidates().size()),
            "{grace}", String.valueOf(snapshot.graceClaims().size()),
            "{legacy}", String.valueOf(snapshot.legacyClaims().size())
        ));
        if (snapshot.candidates().isEmpty() && snapshot.graceClaims().isEmpty() && snapshot.legacyClaims().isEmpty()) {
            sender.sendMessage(plugin.message("admin-cleanup-list-empty"));
            return true;
        }
        formatter.sendCleanupEntries(sender, "候选删除", snapshot.candidates(), false);
        formatter.sendCleanupEntries(sender, "宽限中", snapshot.graceClaims(), true);
        formatter.sendCleanupEntries(sender, "旧地待基线", snapshot.legacyClaims(), false);
        return true;
    }

    private boolean run(CommandSender sender) {
        ClaimCleanupService.CleanupRunResult result = claimCleanupService.runScanNow();
        sender.sendMessage(plugin.message(
            "admin-cleanup-run-result",
            "{scanned}", String.valueOf(result.scannedClaims()),
            "{marked}", String.valueOf(result.markedGraceClaims()),
            "{deleted}", String.valueOf(result.deletedClaims()),
            "{revoked}", String.valueOf(result.revokedGraceClaims()),
            "{candidates}", String.valueOf(result.candidates()),
            "{grace}", String.valueOf(result.graceClaims())
        ));
        return true;
    }

    private boolean skip(CommandSender sender, String[] args) {
        if (args.length < 4) {
            sender.sendMessage(plugin.message("admin-cleanup-skip-usage"));
            return true;
        }
        Claim claim = resolver.resolveAdminClaimSelector(sender, resolver.joinArgs(args, 3));
        if (claim == null) {
            return true;
        }
        claimCleanupService.skipClaim(claim);
        sender.sendMessage(plugin.message("admin-cleanup-skip-success", "{name}", claim.name()));
        return true;
    }

    private boolean baseline(CommandSender sender, String[] args) {
        if (args.length < 5) {
            sender.sendMessage(plugin.message("admin-cleanup-baseline-usage"));
            return true;
        }
        ClaimCleanupBaselineMode mode = ClaimCleanupBaselineMode.fromInput(args[args.length - 1]);
        if (mode == null) {
            sender.sendMessage(plugin.message("admin-cleanup-baseline-usage"));
            return true;
        }
        Claim claim = resolver.resolveAdminClaimSelector(sender, resolver.joinArgs(args, 3, args.length - 1));
        if (claim == null) {
            return true;
        }
        claimCleanupService.baselineClaim(claim, mode);
        sender.sendMessage(plugin.message("admin-cleanup-baseline-success", "{name}", claim.name(), "{mode}", formatter.cleanupBaselineModeText(mode)));
        return true;
    }
}
