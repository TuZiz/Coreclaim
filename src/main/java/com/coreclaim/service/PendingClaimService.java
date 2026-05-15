package com.coreclaim.service;

import com.coreclaim.profile.ProfileService;
import com.coreclaim.CoreClaimPlugin;
import com.coreclaim.claim.mutation.ClaimCreationOptions;
import com.coreclaim.claim.mutation.ClaimCreationRequest;
import com.coreclaim.claim.mutation.ClaimCreationResult;
import com.coreclaim.claim.reservation.ClaimCreationMode;
import com.coreclaim.claim.reservation.PendingCoreReservation;
import com.coreclaim.claim.reservation.PendingCoreReservationService;
import com.coreclaim.config.ClaimGroup;
import com.coreclaim.economy.EconomyHook;
import com.coreclaim.item.ClaimCoreFactory;
import com.coreclaim.model.Claim;
import com.coreclaim.profile.PlayerProfile;
import com.coreclaim.storage.DatabaseAsyncExecutor;
import java.util.logging.Level;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;

public final class PendingClaimService {

    private final CoreClaimPlugin plugin;
    private final ClaimService claimService;
    private final ProfileService profileService;
    private final ClaimCoreFactory claimCoreFactory;
    private final HologramService hologramService;
    private final ClaimVisualService claimVisualService;
    private final EconomyHook economyHook;
    private final OnlineRewardService onlineRewardService;
    private final DatabaseAsyncExecutor databaseAsyncExecutor;
    private final PendingCoreReservationService pendingCoreReservationService;
    private final Map<UUID, PendingClaim> pendingClaims = new ConcurrentHashMap<>();
    private final Map<UUID, com.coreclaim.platform.PlatformScheduler.TaskHandle> timeoutTasks = new ConcurrentHashMap<>();

    public PendingClaimService(
        CoreClaimPlugin plugin,
        ClaimService claimService,
        ProfileService profileService,
        ClaimCoreFactory claimCoreFactory,
        HologramService hologramService,
        ClaimVisualService claimVisualService,
        EconomyHook economyHook,
        OnlineRewardService onlineRewardService,
        DatabaseAsyncExecutor databaseAsyncExecutor,
        PendingCoreReservationService pendingCoreReservationService
    ) {
        this.plugin = plugin;
        this.claimService = claimService;
        this.profileService = profileService;
        this.claimCoreFactory = claimCoreFactory;
        this.hologramService = hologramService;
        this.claimVisualService = claimVisualService;
        this.economyHook = economyHook;
        this.onlineRewardService = onlineRewardService;
        this.databaseAsyncExecutor = databaseAsyncExecutor;
        this.pendingCoreReservationService = pendingCoreReservationService;
    }

    public boolean beginClaimCreation(Player player, Location coreLocation, boolean starterCore) {
        ValidationResult validation = validateCreation(player, coreLocation, starterCore);
        if (!validation.allowed()) {
            player.sendMessage(validation.message());
            return false;
        }

        if (plugin.settings().warnOnSecondClaim() && validation.claimCount() == 1) {
            player.sendMessage(plugin.message("second-claim-warning"));
        }

        cancelPendingClaim(player, false);
        pendingClaims.put(player.getUniqueId(), new PendingClaim(player.getUniqueId(), coreLocation, starterCore));
        scheduleTimeout(player.getUniqueId());
        hologramService.spawnPendingHologram(player.getUniqueId(), player.getName(), coreLocation);
        claimVisualService.showPendingLocation(player, coreLocation);
        player.sendMessage(plugin.message("claim-name-prompt", "{seconds}", String.valueOf(plugin.settings().chatInputTimeoutSeconds())));
        return true;
    }

    public boolean hasPendingClaim(UUID playerId) {
        return pendingClaims.containsKey(playerId);
    }

    public Claim completeClaim(Player player, String inputName) {
        PendingClaim pending = pendingClaims.remove(player.getUniqueId());
        cancelTimeout(player.getUniqueId());
        if (pending == null) {
            return null;
        }
        hologramService.removePendingHologram(player.getUniqueId());

        String name = inputName == null ? "" : inputName.trim();
        if (name.isEmpty()) {
            refundCore(pending);
            player.sendMessage(plugin.message("claim-name-empty"));
            return null;
        }
        if (name.length() > plugin.settings().claimNameMaxLength()) {
            refundCore(pending);
            player.sendMessage(plugin.message("claim-name-too-long", "{max}", String.valueOf(plugin.settings().claimNameMaxLength())));
            return null;
        }
        Location coreLocation = pending.coreLocation();
        ValidationResult validation = validateCreation(player, coreLocation, pending.starterCore());
        if (!validation.allowed()) {
            refundCore(pending);
            player.sendMessage(validation.message());
            return null;
        }
        ClaimGroup group = validation.group();
        double createCost = pending.starterCore() ? 0D : claimArea(group.initialDistance()) * group.coreCreatePricePerBlock();
        if (createCost > 0D) {
            if (!economyHook.available()) {
                refundCore(pending);
                player.sendMessage(plugin.message("economy-missing"));
                return null;
            }
            if (!economyHook.has(player, createCost)) {
                refundCore(pending);
                player.sendMessage(plugin.message("economy-not-enough", "{cost}", ClaimActionService.formatMoney(createCost)));
                return null;
            }
            if (!economyHook.withdraw(player, createCost)) {
                refundCore(pending);
                player.sendMessage(plugin.message("economy-missing"));
                return null;
            }
        }
        UUID ownerId = player.getUniqueId();
        String ownerName = player.getName();
        try {
            plugin.platformScheduler().runLocationTask(coreLocation, () -> completeClaimOnRegion(player, ownerId, ownerName, pending, name, coreLocation, group, createCost));
        } catch (RuntimeException exception) {
            rollbackFailedClaimCreation(coreLocation, pending, player, createCost);
            plugin.getLogger().log(Level.WARNING, "Failed to schedule pending claim creation for " + player.getName(), exception);
            player.sendMessage(plugin.message("claim-create-failed"));
        }
        return null;
    }

    private void completeClaimOnRegion(
        Player player,
        UUID ownerId,
        String ownerName,
        PendingClaim pending,
        String name,
        Location coreLocation,
        ClaimGroup group,
        double createCost
    ) {
        PendingCoreReservation reservation = null;
        try {
            ClaimCreationOptions options = ClaimCreationOptions.coreClaim(
                group.maxClaims(),
                group.maxDistance(),
                plugin.settings().minimumGap(),
                plugin.settings().minimumCoreSpacing()
            );
            reservation = pendingCoreReservationService.reserve(ownerId, coreLocation, ClaimCreationMode.CORE_CLAIM, options);
            ClaimCreationRequest request = ClaimCreationRequest.core(
                ownerId,
                ownerName,
                name,
                coreLocation,
                group.initialDistance(),
                options
            );
            completeClaimAsync(player, pending, request, reservation, coreLocation, createCost);
        } catch (RuntimeException exception) {
            if (reservation != null) {
                pendingCoreReservationService.releaseAndClear(reservation);
            }
            plugin.platformScheduler().runPlayerTask(player, () -> {
                rollbackFailedClaimCreation(coreLocation, pending, player, createCost);
                sendPendingCreationFailure(player, exception, requestName(name));
            });
        }
    }

    private void completeClaimAsync(
        Player player,
        PendingClaim pending,
        ClaimCreationRequest request,
        PendingCoreReservation reservation,
        Location coreLocation,
        double createCost
    ) {
        databaseAsyncExecutor.supply(() -> claimService.createClaim(request)).whenComplete((result, throwable) -> {
            if (throwable != null) {
                plugin.getLogger().log(Level.WARNING, "Database claim creation failed for pending claim " + request.name(), throwable);
                try {
                    plugin.platformScheduler().runLocationTask(coreLocation, () -> pendingCoreReservationService.releaseAndClear(reservation));
                } catch (RuntimeException cleanupException) {
                    plugin.getLogger().log(Level.WARNING, "Failed to schedule pending claim core cleanup after database failure.", cleanupException);
                }
                plugin.platformScheduler().runPlayerTask(player, () -> {
                    refundCreationPayment(player, createCost);
                    refundCore(pending);
                    sendPendingCreationFailure(player, asRuntimeException(throwable), request.name());
                });
                return;
            }
            try {
                plugin.platformScheduler().runLocationTask(coreLocation, () -> {
                    if (pendingCoreReservationService.validateStillReservedAndCorePresent(reservation)) {
                        pendingCoreReservationService.commit(reservation, result.claim());
                        finishPendingClaimAfterReservationCommit(player, pending, request, result, createCost);
                        return;
                    }
                    compensateCommittedClaimCreationFailure(result.claim(), reservation, player, pending, createCost, true, "pending-core-invalid");
                });
            } catch (RuntimeException scheduleException) {
                plugin.getLogger().log(Level.SEVERE, "Claim committed but failed to schedule pending reservation validation: " + result.claim().id(), scheduleException);
                compensateCommittedClaimCreationFailure(result.claim(), reservation, player, pending, createCost, true, "pending-core-validation-schedule-failed");
            }
        });
    }

    private void finishPendingClaimAfterReservationCommit(
        Player player,
        PendingClaim pending,
        ClaimCreationRequest request,
        ClaimCreationResult result,
        double createCost
    ) {
        if (!pending.starterCore()) {
            plugin.platformScheduler().runPlayerTask(player, () -> finishPendingClaim(player, pending, result, createCost));
            return;
        }
        databaseAsyncExecutor.run(() -> markStarterCoreUsedIfNeeded(pending, request)).whenComplete((ignored, throwable) -> {
            if (throwable != null) {
                plugin.getLogger().log(Level.WARNING, "Claim committed but starter core profile update failed for " + request.owner(), throwable);
            }
            plugin.platformScheduler().runPlayerTask(player, () -> finishPendingClaim(player, pending, result, createCost));
        });
    }

    private void compensateCommittedClaimCreationFailure(
        Claim claim,
        PendingCoreReservation reservation,
        Player player,
        PendingClaim pending,
        double createCost,
        boolean refundCore,
        String reason
    ) {
        try {
            databaseAsyncExecutor.run(() -> claimService.removeCommittedClaimRecord(claim)).whenComplete((ignored, throwable) -> {
            if (throwable != null) {
                plugin.getLogger().log(Level.SEVERE, "Failed to compensate committed pending claim " + claim.id() + " after " + reason, throwable);
                try {
                    claimService.reloadClaims();
                } catch (RuntimeException reloadException) {
                    plugin.getLogger().log(Level.SEVERE, "Failed to reload claims after pending compensation failure for " + claim.id(), reloadException);
                }
            }
            try {
                plugin.platformScheduler().runLocationTask(pending.coreLocation(), () -> pendingCoreReservationService.releaseAndClear(reservation));
            } catch (RuntimeException cleanupException) {
                plugin.getLogger().log(Level.WARNING, "Failed to schedule reservation cleanup after pending compensation: " + claim.id(), cleanupException);
            }
            plugin.platformScheduler().runPlayerTask(player, () -> {
                refundCreationPayment(player, createCost);
                if (refundCore) {
                    refundCore(pending);
                }
                sendPendingCreationFailure(player, new IllegalStateException("pending-core-invalid"), claim.name());
            });
        });
        } catch (RuntimeException exception) {
            handleCompensationSchedulingFailure(claim, reservation, player, pending, createCost, refundCore, reason, exception);
        }
    }

    private void handleCompensationSchedulingFailure(
        Claim claim,
        PendingCoreReservation reservation,
        Player player,
        PendingClaim pending,
        double createCost,
        boolean shouldRefundCore,
        String reason,
        RuntimeException exception
    ) {
        plugin.getLogger().log(Level.SEVERE, "Failed to schedule committed claim compensation for " + claim.id()
            + " after " + reason + ". Manual database repair may be required.", exception);
        try {
            claimService.reloadClaims();
        } catch (RuntimeException reloadException) {
            plugin.getLogger().log(Level.SEVERE, "Failed to reload claims after compensation scheduling failure for " + claim.id(), reloadException);
        }
        try {
            plugin.platformScheduler().runLocationTask(pending.coreLocation(), () -> pendingCoreReservationService.releaseAndClear(reservation));
        } catch (RuntimeException cleanupException) {
            plugin.getLogger().log(Level.WARNING, "Failed to schedule reservation cleanup after compensation scheduling failure: " + claim.id(), cleanupException);
        }
        plugin.platformScheduler().runPlayerTask(player, () -> {
            refundCreationPayment(player, createCost);
            if (shouldRefundCore) {
                refundCore(pending);
            }
            sendPendingCreationFailure(player, new IllegalStateException("pending-core-invalid"), claim.name());
        });
    }

    private void finishPendingClaim(Player player, PendingClaim pending, ClaimCreationResult result, double createCost) {
        Claim claim = result.claim();
        hologramService.spawnClaimHologram(claim);
        claimVisualService.showClaim(player, claim);
        onlineRewardService.markOrdinaryClaimCreated(player);
        player.sendMessage(plugin.message(
            "claim-name-created",
            "{name}", claim.name(),
            "{width}", String.valueOf(claim.width()),
            "{depth}", String.valueOf(claim.depth()),
            "{cost}", ClaimActionService.formatMoney(createCost)
        ));
        if (result.previousOwnerClaimCount() == 0) {
            player.sendMessage(chatMessage(
                "second-claim-selection-tip",
                "&6&l提示: &7第二块领地开始，直接拿普通金锄头左键点 1、右键点 2，再输入 &e/claim create <名字> &7即可。"
            ));
        }
    }

    private void markStarterCoreUsedIfNeeded(PendingClaim pending, ClaimCreationRequest request) {
        if (!pending.starterCore()) {
            return;
        }
        try {
            PlayerProfile profile = profileService.getOrCreate(request.owner(), request.ownerName());
            if (!profile.starterCoreUsed()) {
                profile.setStarterCoreUsed(true);
                profileService.saveProfile(profile);
            }
        } catch (RuntimeException exception) {
            throw exception;
        }
    }

    private void rollbackFailedClaimCreation(Location coreLocation, PendingClaim pending, Player player, double createCost) {
        clearPlacedCoreBlock(coreLocation);
        refundCreationPayment(player, createCost);
        refundCore(pending);
    }

    private void refundCreationPayment(Player player, double createCost) {
        if (createCost > 0D && economyHook.available()) {
            economyHook.deposit(player, createCost);
        }
    }

    private void clearPlacedCoreBlock(Location coreLocation) {
        if (coreLocation == null || coreLocation.getWorld() == null) {
            return;
        }
        plugin.platformScheduler().runLocationTask(coreLocation, () -> {
            if (coreLocation.getBlock().getType() == plugin.settings().coreMaterial()) {
                coreLocation.getBlock().setType(Material.AIR, false);
            }
        });
    }

    public void cancelPendingClaim(Player player, boolean notify) {
        PendingClaim pending = pendingClaims.remove(player.getUniqueId());
        cancelTimeout(player.getUniqueId());
        if (pending == null) {
            return;
        }
        hologramService.removePendingHologram(player.getUniqueId());
        refundCore(pending);
        if (notify) {
            player.sendMessage(plugin.message("claim-name-cancelled"));
        }
    }

    public void timeoutPendingClaim(UUID playerId) {
        PendingClaim pending = pendingClaims.remove(playerId);
        cancelTimeout(playerId);
        if (pending == null) {
            return;
        }
        hologramService.removePendingHologram(playerId);
        refundCore(pending);
        Player player = plugin.getServer().getPlayer(playerId);
        if (player != null) {
            plugin.platformScheduler().runPlayerTask(player, () -> player.sendMessage(plugin.message("claim-name-timeout")));
        }
    }

    private void scheduleTimeout(UUID playerId) {
        cancelTimeout(playerId);
        long delayTicks = plugin.settings().chatInputTimeoutSeconds() * 20L;
        com.coreclaim.platform.PlatformScheduler.TaskHandle handle =
            plugin.platformScheduler().runLater(() -> timeoutPendingClaim(playerId), delayTicks);
        timeoutTasks.put(playerId, handle);
    }

    private void cancelTimeout(UUID playerId) {
        com.coreclaim.platform.PlatformScheduler.TaskHandle handle = timeoutTasks.remove(playerId);
        if (handle != null) {
            handle.cancel();
        }
    }

    private ValidationResult validateCreation(Player player, Location coreLocation, boolean starterCore) {
        if (player == null || coreLocation == null || coreLocation.getWorld() == null) {
            return ValidationResult.denied(plugin.message("world-missing"));
        }
        PlayerProfile profile = profileService.getOrCreate(player.getUniqueId(), player.getName());
        int claimCount = claimService.claimsOf(player.getUniqueId()).size();
        if (starterCore && claimCount > 0) {
            return ValidationResult.denied(plugin.message("starter-core-first-only"));
        }
        if (starterCore && profile.starterCoreUsed()) {
            return ValidationResult.denied(chatMessage(
                "starter-core-already-used",
                "&c&l! &7你已经成功使用过一次新人核心了，后续请直接用普通金锄头选区创建领地。"
            ));
        }
        World world = coreLocation.getWorld();
        if (!plugin.settings().isClaimWorld(world.getName())) {
            return ValidationResult.denied(plugin.message("claim-world-only", "{world}", plugin.settings().claimWorldsDisplay()));
        }
        ClaimGroup group = plugin.groups().resolve(player);
        int maxClaims = group.maxClaims();
        if (claimCount >= maxClaims) {
            return ValidationResult.denied(plugin.message("claim-no-slot"));
        }

        return ValidationResult.allowed(group, claimCount);
    }

    private void refundCore(PendingClaim pending) {
        Location location = pending.coreLocation();
        Player player = plugin.getServer().getPlayer(pending.ownerId());
        if (player != null) {
            plugin.platformScheduler().runPlayerTask(player, () -> giveRefundedCore(player, pending));
        } else if (location.getWorld() != null) {
            plugin.platformScheduler().runLocationTask(location, () -> location.getWorld().dropItemNaturally(
                location.clone().add(0.5D, 0.5D, 0.5D),
                pending.starterCore() ? claimCoreFactory.createStarterCore(1) : claimCoreFactory.createClaimCore(1)
            ));
        }
    }

    private void giveRefundedCore(Player player, PendingClaim pending) {
        if (pending.starterCore()) {
            claimCoreFactory.giveStarterCore(player, 1);
        } else {
            claimCoreFactory.giveClaimCore(player, 1);
        }
    }

    public record PendingClaim(UUID ownerId, Location coreLocation, boolean starterCore) {
    }

    private record ValidationResult(boolean allowed, String message, ClaimGroup group, int claimCount) {
        private static ValidationResult denied(String message) {
            return new ValidationResult(false, message, null, 0);
        }

        private static ValidationResult allowed(ClaimGroup group, int claimCount) {
            return new ValidationResult(true, "", group, claimCount);
        }
    }

    private long claimArea(int initialDistance) {
        int edge = initialDistance * 2 + 1;
        return (long) edge * edge;
    }

    private String chatMessage(String path, String fallback, String... replacements) {
        String prefix = plugin.messagesConfig().getString("prefix", "&#64748B[&#A7F3D0Claim&#64748B] &#CBD5E1");
        String body = plugin.messagesConfig().contains(path) ? plugin.messagesConfig().getString(path, fallback) : fallback;
        String message = plugin.color(prefix + body);
        for (int index = 0; index + 1 < replacements.length; index += 2) {
            message = message.replace(replacements[index], replacements[index + 1]);
        }
        return message;
    }

    private void sendPendingCreationFailure(Player player, RuntimeException exception, String name) {
        String reason = rootReason(exception);
        switch (reason) {
            case "claim-name-exists" -> player.sendMessage(plugin.message("claim-name-exists", "{name}", name));
            case "claim-overlap" -> player.sendMessage(plugin.message("claim-overlap"));
            case "claim-core-blocked" -> player.sendMessage(plugin.message("claim-core-blocked"));
            case "claim-core-too-close" -> player.sendMessage(plugin.message("claim-core-too-close"));
            case "claim-no-slot" -> player.sendMessage(plugin.message("claim-no-slot"));
            case "claim-world-only" -> player.sendMessage(plugin.message("claim-world-only", "{world}", plugin.settings().claimWorldsDisplay()));
            case "selection-too-large" -> player.sendMessage(plugin.message("selection-too-large", "{max}", String.valueOf(plugin.groups().resolve(player).maxDistance() * 2 + 1)));
            default -> {
                plugin.getLogger().log(Level.WARNING, "Failed to complete pending claim creation for " + player.getName(), exception);
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

    private String requestName(String name) {
        return name == null ? "" : name;
    }
}
