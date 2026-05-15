package com.coreclaim.selection;

import com.coreclaim.service.OnlineRewardService;
import com.coreclaim.service.HologramService;
import com.coreclaim.service.ClaimVisualService;
import com.coreclaim.service.ClaimService;
import com.coreclaim.CoreClaimPlugin;
import com.coreclaim.claim.mutation.ClaimCreationOptions;
import com.coreclaim.economy.EconomyHook;
import com.coreclaim.config.ClaimGroup;
import com.coreclaim.model.Claim;
import java.text.DecimalFormat;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;
import org.bukkit.entity.Player;

final class ClaimSelectionCreator {

    private static final DecimalFormat MONEY = new DecimalFormat("0.##");

    private final CoreClaimPlugin plugin;
    private final ClaimService claimService;
    private final ClaimVisualService claimVisualService;
    private final HologramService hologramService;
    private final EconomyHook economyHook;
    private final OnlineRewardService onlineRewardService;
    private final ClaimSelectionPreviewBuilder previewBuilder;
    private final Map<UUID, ClaimSelectionSession> sessions;

    ClaimSelectionCreator(
        CoreClaimPlugin plugin,
        ClaimService claimService,
        ClaimVisualService claimVisualService,
        HologramService hologramService,
        EconomyHook economyHook,
        OnlineRewardService onlineRewardService,
        ClaimSelectionPreviewBuilder previewBuilder,
        Map<UUID, ClaimSelectionSession> sessions
    ) {
        this.plugin = plugin;
        this.claimService = claimService;
        this.claimVisualService = claimVisualService;
        this.hologramService = hologramService;
        this.economyHook = economyHook;
        this.onlineRewardService = onlineRewardService;
        this.previewBuilder = previewBuilder;
        this.sessions = sessions;
    }

    boolean createClaim(Player player, String rawName) {
        ClaimSelectionService.SelectionPreview preview = previewBuilder.preview(player, false);
        if (!validatePreview(player, preview)) {
            return false;
        }
        String name = validateName(player, rawName);
        if (name == null) {
            return false;
        }
        if (!withdrawCost(player, preview.cost())) {
            return false;
        }

        boolean firstOrdinaryClaim = claimService.countClaims(player.getUniqueId()) == 0;
        ClaimGroup group = plugin.groups().resolve(player);
        try {
            plugin.platformScheduler().runLocationTask(preview.coreLocation(), () -> createClaimOnRegion(player, name, preview, group, firstOrdinaryClaim));
        } catch (RuntimeException exception) {
            refundCost(player, preview.cost());
            sendCreationFailure(player, exception, name);
            return false;
        }
        return true;
    }

    boolean createSystemClaim(Player player, String rawName) {
        ClaimSelectionService.SelectionPreview preview = previewBuilder.preview(player, true);
        if (!validatePreview(player, preview)) {
            return false;
        }
        String name = validateName(player, rawName);
        if (name == null) {
            return false;
        }

        ClaimGroup group = plugin.groups().resolve(player);
        try {
            plugin.platformScheduler().runLocationTask(preview.coreLocation(), () -> createSystemClaimOnRegion(player, name, preview, group));
        } catch (RuntimeException exception) {
            sendCreationFailure(player, exception, name);
            return false;
        }
        return true;
    }

    private void createClaimOnRegion(Player player, String name, ClaimSelectionService.SelectionPreview preview, ClaimGroup group, boolean firstOrdinaryClaim) {
        Claim claim;
        try {
            claim = claimService.createClaimFromBounds(
                player.getUniqueId(),
                player.getName(),
                name,
                preview.coreLocation(),
                preview.minY(),
                preview.maxY(),
                preview.east(),
                preview.south(),
                preview.west(),
                preview.north(),
                false,
                selectionOptions(group, false)
            );
        } catch (RuntimeException exception) {
            refundCost(player, preview.cost());
            sendCreationFailure(player, exception, name);
            return;
        }
        finishCreatedClaim(player, claim, preview);
        onlineRewardService.markOrdinaryClaimCreated(player);
        player.sendMessage(plugin.message(
            "selection-create-success",
            "{name}", claim.name(),
            "{width}", String.valueOf(claim.width()),
            "{height}", String.valueOf(claim.height()),
            "{depth}", String.valueOf(claim.depth()),
            "{volume}", String.valueOf(preview.volume()),
            "{cost}", MONEY.format(preview.cost())
        ));
        if (firstOrdinaryClaim) {
            player.sendMessage(plugin.message("second-claim-selection-tip"));
        }
    }

    private void createSystemClaimOnRegion(Player player, String name, ClaimSelectionService.SelectionPreview preview, ClaimGroup group) {
        Claim claim;
        try {
            claim = claimService.createClaimFromBounds(
                player.getUniqueId(),
                player.getName(),
                name,
                preview.coreLocation(),
                preview.minY(),
                preview.maxY(),
                preview.east(),
                preview.south(),
                preview.west(),
                preview.north(),
                true,
                selectionOptions(group, true)
            );
        } catch (RuntimeException exception) {
            sendCreationFailure(player, exception, name);
            return;
        }
        finishCreatedClaim(player, claim, preview);
        player.sendMessage(plugin.message(
            "selection-create-system-success",
            "{name}", claim.name(),
            "{width}", String.valueOf(claim.width()),
            "{height}", String.valueOf(claim.height()),
            "{depth}", String.valueOf(claim.depth())
        ));
    }

    private boolean validatePreview(Player player, ClaimSelectionService.SelectionPreview preview) {
        if (preview == null || !preview.ready()) {
            player.sendMessage(plugin.message("selection-missing-points"));
            return false;
        }
        if (!preview.allowed()) {
            player.sendMessage(preview.failureMessage());
            return false;
        }
        return true;
    }

    private String validateName(Player player, String rawName) {
        String name = rawName == null ? "" : rawName.trim();
        if (name.isEmpty()) {
            player.sendMessage(plugin.message("claim-name-empty"));
            return null;
        }
        if (name.length() > plugin.settings().claimNameMaxLength()) {
            player.sendMessage(plugin.message("claim-name-too-long", "{max}", String.valueOf(plugin.settings().claimNameMaxLength())));
            return null;
        }
        if (claimService.isClaimNameTaken(name)) {
            player.sendMessage(plugin.message("claim-name-exists", "{name}", name));
            return null;
        }
        return name;
    }

    private boolean withdrawCost(Player player, double cost) {
        if (cost <= 0D) {
            return true;
        }
        if (!economyHook.available()) {
            player.sendMessage(plugin.message("economy-missing"));
            return false;
        }
        if (!economyHook.has(player, cost)) {
            player.sendMessage(plugin.message("economy-not-enough", "{cost}", MONEY.format(cost)));
            return false;
        }
        if (!economyHook.withdraw(player, cost)) {
            player.sendMessage(plugin.message("economy-missing"));
            return false;
        }
        return true;
    }

    private void refundCost(Player player, double cost) {
        if (cost > 0D && economyHook.available()) {
            economyHook.deposit(player, cost);
        }
    }

    private void finishCreatedClaim(Player player, Claim claim, ClaimSelectionService.SelectionPreview preview) {
        hologramService.spawnClaimHologram(claim);
        claimVisualService.showClaim(player, claim);
        sessions.remove(player.getUniqueId());
    }

    private ClaimCreationOptions selectionOptions(ClaimGroup group, boolean systemManaged) {
        return ClaimCreationOptions.selectionClaim(
            group.maxClaims(),
            group.maxDistance(),
            plugin.settings().minimumGap(),
            plugin.settings().selectionMinimumGap(),
            plugin.settings().minimumCoreSpacing(),
            systemManaged
        );
    }

    private void sendCreationFailure(Player player, RuntimeException exception, String name) {
        String reason = rootReason(exception);
        switch (reason) {
            case "claim-overlap" -> player.sendMessage(plugin.message("claim-overlap"));
            case "selection-claim-too-close" -> player.sendMessage(plugin.message("selection-claim-too-close", "{gap}", String.valueOf(plugin.settings().selectionMinimumGap())));
            case "claim-core-too-close" -> player.sendMessage(plugin.message("claim-core-too-close"));
            case "selection-core-blocked", "claim-core-blocked" -> player.sendMessage(plugin.message("selection-core-blocked"));
            case "claim-no-slot" -> player.sendMessage(plugin.message("claim-no-slot"));
            case "claim-world-only" -> player.sendMessage(plugin.message("claim-world-only", "{world}", plugin.settings().claimWorldsDisplay()));
            case "selection-too-large" -> player.sendMessage(plugin.message("selection-too-large", "{max}", String.valueOf(plugin.groups().resolve(player).maxDistance() * 2 + 1)));
            case "claim-name-empty" -> player.sendMessage(plugin.message("claim-name-empty"));
            case "claim-name-exists" -> player.sendMessage(plugin.message("claim-name-exists", "{name}", name));
            default -> {
                plugin.getLogger().log(Level.WARNING, "Failed to create selection claim for " + player.getName(), exception);
                player.sendMessage(plugin.message("claim-create-failed"));
            }
        }
    }

    private String rootReason(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof IllegalArgumentException && current.getMessage() != null) {
                return current.getMessage();
            }
            current = current.getCause();
        }
        return "";
    }
}
