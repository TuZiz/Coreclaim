package com.coreclaim.service;

import com.coreclaim.CoreClaimPlugin;
import com.coreclaim.economy.EconomyHook;
import com.coreclaim.model.Claim;
import java.text.DecimalFormat;
import java.util.Map;
import java.util.UUID;
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
                preview.north()
            );
        } catch (IllegalArgumentException exception) {
            refundCost(player, preview.cost());
            player.sendMessage(plugin.message("claim-name-exists", "{name}", name));
            return false;
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
                true
            );
        } catch (IllegalArgumentException exception) {
            player.sendMessage(plugin.message("claim-name-exists", "{name}", name));
            return false;
        }
        finishCreatedClaim(player, claim, preview);
        player.sendMessage(plugin.message(
            "selection-create-system-success",
            "{name}", claim.name(),
            "{width}", String.valueOf(claim.width()),
            "{height}", String.valueOf(claim.height()),
            "{depth}", String.valueOf(claim.depth())
        ));
        return true;
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
        plugin.platformScheduler().runLocationTask(preview.coreLocation(), () -> preview.coreLocation().getBlock().setType(plugin.settings().coreMaterial(), false));
        hologramService.spawnClaimHologram(claim);
        claimVisualService.showClaim(player, claim);
        sessions.remove(player.getUniqueId());
    }
}
