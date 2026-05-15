package com.coreclaim.selection;

import com.coreclaim.service.OnlineRewardService;
import com.coreclaim.service.HologramService;
import com.coreclaim.service.ClaimVisualService;
import com.coreclaim.service.ClaimService;
import com.coreclaim.CoreClaimPlugin;
import com.coreclaim.claim.mutation.ClaimCreationOptions;
import com.coreclaim.claim.mutation.ClaimCreationRequest;
import com.coreclaim.claim.mutation.ClaimCreationResult;
import com.coreclaim.claim.reservation.ClaimCreationMode;
import com.coreclaim.claim.reservation.PendingCoreReservation;
import com.coreclaim.claim.reservation.PendingCoreReservationService;
import com.coreclaim.economy.EconomyHook;
import com.coreclaim.config.ClaimGroup;
import com.coreclaim.model.Claim;
import com.coreclaim.storage.DatabaseAsyncExecutor;
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
    private final DatabaseAsyncExecutor databaseAsyncExecutor;
    private final PendingCoreReservationService pendingCoreReservationService;

    ClaimSelectionCreator(
        CoreClaimPlugin plugin,
        ClaimService claimService,
        ClaimVisualService claimVisualService,
        HologramService hologramService,
        EconomyHook economyHook,
        OnlineRewardService onlineRewardService,
        ClaimSelectionPreviewBuilder previewBuilder,
        Map<UUID, ClaimSelectionSession> sessions,
        DatabaseAsyncExecutor databaseAsyncExecutor,
        PendingCoreReservationService pendingCoreReservationService
    ) {
        this.plugin = plugin;
        this.claimService = claimService;
        this.claimVisualService = claimVisualService;
        this.hologramService = hologramService;
        this.economyHook = economyHook;
        this.onlineRewardService = onlineRewardService;
        this.previewBuilder = previewBuilder;
        this.sessions = sessions;
        this.databaseAsyncExecutor = databaseAsyncExecutor;
        this.pendingCoreReservationService = pendingCoreReservationService;
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

        ClaimGroup group = plugin.groups().resolve(player);
        UUID ownerId = player.getUniqueId();
        String ownerName = player.getName();
        try {
            plugin.platformScheduler().runLocationTask(preview.coreLocation(), () -> createClaimOnRegion(player, ownerId, ownerName, name, preview, group));
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
        UUID ownerId = player.getUniqueId();
        String ownerName = player.getName();
        try {
            plugin.platformScheduler().runLocationTask(preview.coreLocation(), () -> createSystemClaimOnRegion(player, ownerId, ownerName, name, preview, group));
        } catch (RuntimeException exception) {
            sendCreationFailure(player, exception, name);
            return false;
        }
        return true;
    }

    private void createClaimOnRegion(Player player, UUID ownerId, String ownerName, String name, ClaimSelectionService.SelectionPreview preview, ClaimGroup group) {
        ClaimCreationOptions options = selectionOptions(group, false);
        PendingCoreReservation reservation = null;
        try {
            reservation = pendingCoreReservationService.reserve(ownerId, preview.coreLocation(), ClaimCreationMode.SELECTION_CLAIM, options);
            ClaimCreationRequest request = ClaimCreationRequest.bounds(
                ownerId,
                ownerName,
                name,
                preview.coreLocation(),
                preview.minY(),
                preview.maxY(),
                preview.east(),
                preview.south(),
                preview.west(),
                preview.north(),
                false,
                options
            );
            createSelectionClaimAsync(player, name, preview, request, reservation, true);
        } catch (RuntimeException exception) {
            if (reservation != null) {
                pendingCoreReservationService.releaseAndClear(reservation);
            }
            plugin.platformScheduler().runPlayerTask(player, () -> {
                refundCost(player, preview.cost());
                sendCreationFailure(player, exception, name);
            });
        }
    }

    private void createSystemClaimOnRegion(Player player, UUID ownerId, String ownerName, String name, ClaimSelectionService.SelectionPreview preview, ClaimGroup group) {
        ClaimCreationOptions options = selectionOptions(group, true);
        PendingCoreReservation reservation = null;
        try {
            reservation = pendingCoreReservationService.reserve(ownerId, preview.coreLocation(), ClaimCreationMode.SYSTEM_SELECTION_CLAIM, options);
            ClaimCreationRequest request = ClaimCreationRequest.bounds(
                ownerId,
                ownerName,
                name,
                preview.coreLocation(),
                preview.minY(),
                preview.maxY(),
                preview.east(),
                preview.south(),
                preview.west(),
                preview.north(),
                true,
                options
            );
            createSelectionClaimAsync(player, name, preview, request, reservation, false);
        } catch (RuntimeException exception) {
            if (reservation != null) {
                pendingCoreReservationService.releaseAndClear(reservation);
            }
            plugin.platformScheduler().runPlayerTask(player, () -> sendCreationFailure(player, exception, name));
        }
    }

    private void createSelectionClaimAsync(
        Player player,
        String name,
        ClaimSelectionService.SelectionPreview preview,
        ClaimCreationRequest request,
        PendingCoreReservation reservation,
        boolean chargeCost
    ) {
        databaseAsyncExecutor.supply(() -> claimService.createClaim(request)).whenComplete((result, throwable) -> {
            if (throwable != null) {
                plugin.getLogger().log(Level.WARNING, "Database claim creation failed for selection claim " + name, throwable);
                try {
                    plugin.platformScheduler().runLocationTask(preview.coreLocation(), () -> pendingCoreReservationService.releaseAndClear(reservation));
                } catch (RuntimeException cleanupException) {
                    plugin.getLogger().log(Level.WARNING, "Failed to schedule selection claim core cleanup after database failure.", cleanupException);
                }
                plugin.platformScheduler().runPlayerTask(player, () -> {
                    if (chargeCost) {
                        refundCost(player, preview.cost());
                    }
                    sendCreationFailure(player, asRuntimeException(throwable), name);
                });
                return;
            }
            try {
                plugin.platformScheduler().runLocationTask(preview.coreLocation(), () -> {
                    if (pendingCoreReservationService.validateStillReserved(reservation)) {
                        pendingCoreReservationService.commit(reservation, result.claim());
                        plugin.platformScheduler().runPlayerTask(player, () -> finishSelectionClaim(player, result, preview, chargeCost));
                        return;
                    }
                    compensateCommittedClaimCreationFailure(result.claim(), reservation, player, preview, chargeCost, "pending-core-invalid");
                });
            } catch (RuntimeException scheduleException) {
                plugin.getLogger().log(Level.SEVERE, "Claim committed but failed to schedule selection reservation validation: " + result.claim().id(), scheduleException);
                compensateCommittedClaimCreationFailure(result.claim(), reservation, player, preview, chargeCost, "pending-core-validation-schedule-failed");
            }
        });
    }

    private void compensateCommittedClaimCreationFailure(
        Claim claim,
        PendingCoreReservation reservation,
        Player player,
        ClaimSelectionService.SelectionPreview preview,
        boolean shouldRefundCost,
        String reason
    ) {
        databaseAsyncExecutor.run(() -> claimService.removeCommittedClaimRecord(claim)).whenComplete((ignored, throwable) -> {
            if (throwable != null) {
                plugin.getLogger().log(Level.SEVERE, "Failed to compensate committed selection claim " + claim.id() + " after " + reason, throwable);
                try {
                    claimService.reloadClaims();
                } catch (RuntimeException reloadException) {
                    plugin.getLogger().log(Level.SEVERE, "Failed to reload claims after compensation failure for " + claim.id(), reloadException);
                }
            }
            try {
                plugin.platformScheduler().runLocationTask(preview.coreLocation(), () -> pendingCoreReservationService.releaseAndClear(reservation));
            } catch (RuntimeException cleanupException) {
                plugin.getLogger().log(Level.WARNING, "Failed to schedule reservation cleanup after selection compensation: " + claim.id(), cleanupException);
            }
            plugin.platformScheduler().runPlayerTask(player, () -> {
                if (shouldRefundCost) {
                    refundCost(player, preview.cost());
                }
                sendCreationFailure(player, new IllegalStateException("pending-core-invalid"), claim.name());
            });
        });
    }

    private void finishSelectionClaim(Player player, ClaimCreationResult result, ClaimSelectionService.SelectionPreview preview, boolean chargedCost) {
        Claim claim = result.claim();
        finishCreatedClaim(player, claim, preview);
        if (chargedCost) {
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
            if (result.previousOwnerClaimCount() == 0) {
                player.sendMessage(plugin.message("second-claim-selection-tip"));
            }
            return;
        }
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

    private RuntimeException asRuntimeException(Throwable throwable) {
        if (throwable instanceof RuntimeException runtimeException) {
            return runtimeException;
        }
        if (throwable.getCause() instanceof RuntimeException runtimeException) {
            return runtimeException;
        }
        return new IllegalStateException(throwable);
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
