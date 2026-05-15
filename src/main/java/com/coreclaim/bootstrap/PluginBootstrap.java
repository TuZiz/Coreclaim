package com.coreclaim.bootstrap;

import com.coreclaim.CoreClaimPlugin;
import com.coreclaim.config.GroupConfig;
import com.coreclaim.config.PluginConfig;
import com.coreclaim.claim.mutation.ClaimCoreRegionService;
import com.coreclaim.economy.EconomyHook;
import com.coreclaim.gui.MenuService;
import com.coreclaim.item.ClaimCoreFactory;
import com.coreclaim.platform.PlatformScheduler;
import com.coreclaim.service.ClaimActionService;
import com.coreclaim.cleanup.ClaimCleanupService;
import com.coreclaim.input.ClaimInputService;
import com.coreclaim.selection.ClaimSelectionService;
import com.coreclaim.service.ClaimService;
import com.coreclaim.sync.ClaimSyncService;
import com.coreclaim.transfer.ClaimTransferService;
import com.coreclaim.service.ClaimVisualService;
import com.coreclaim.teleport.CrossServerTeleportService;
import com.coreclaim.service.ExplosionAuthorizationService;
import com.coreclaim.service.HologramService;
import com.coreclaim.service.OnlineRewardService;
import com.coreclaim.service.PendingClaimService;
import com.coreclaim.profile.ProfileService;
import com.coreclaim.service.RemovalConfirmationService;
import com.coreclaim.storage.DatabaseManager;
import com.coreclaim.storage.DatabaseAsyncExecutor;

public final class PluginBootstrap {

    public BootstrapResult initialize(CoreClaimPlugin plugin, PluginConfig pluginConfig, GroupConfig groupConfig) {
        PlatformScheduler platformScheduler = new PlatformScheduler(plugin);
        DatabaseManager databaseManager = new DatabaseManager(plugin);
        DatabaseAsyncExecutor databaseAsyncExecutor = new DatabaseAsyncExecutor();
        ClaimCoreRegionService claimCoreRegionService = new ClaimCoreRegionService(plugin);
        ClaimCoreFactory claimCoreFactory = new ClaimCoreFactory(plugin);
        ProfileService profileService = new ProfileService(databaseManager);
        ClaimService claimService = new ClaimService(plugin, databaseManager, profileService);
        EconomyHook economyHook = new EconomyHook(plugin);
        HologramService hologramService = new HologramService(plugin);
        ClaimCleanupService claimCleanupService = new ClaimCleanupService(
            plugin,
            databaseManager,
            claimService,
            profileService,
            hologramService,
            platformScheduler
        );
        claimService.setClaimCleanupService(claimCleanupService);
        ClaimVisualService claimVisualService = new ClaimVisualService(plugin);
        ClaimSyncService claimSyncService = new ClaimSyncService(plugin, databaseManager, claimService, hologramService);
        claimService.setClaimSyncPublisher(claimSyncService);
        CrossServerTeleportService crossServerTeleportService = new CrossServerTeleportService(
            plugin,
            databaseManager,
            claimService,
            claimVisualService
        );
        OnlineRewardService onlineRewardService = new OnlineRewardService(
            plugin,
            platformScheduler,
            profileService,
            claimService,
            claimCoreFactory,
            claimCleanupService
        );
        PendingClaimService pendingClaimService = new PendingClaimService(
            plugin,
            claimService,
            profileService,
            claimCoreFactory,
            hologramService,
            claimVisualService,
            economyHook,
            onlineRewardService,
            databaseAsyncExecutor,
            claimCoreRegionService
        );
        ClaimActionService claimActionService = new ClaimActionService(
            plugin,
            claimService,
            hologramService,
            claimVisualService,
            economyHook,
            crossServerTeleportService
        );
        ClaimSelectionService claimSelectionService = new ClaimSelectionService(
            plugin,
            claimService,
            profileService,
            claimVisualService,
            hologramService,
            economyHook,
            onlineRewardService,
            databaseAsyncExecutor,
            claimCoreRegionService
        );
        ClaimInputService claimInputService = new ClaimInputService(plugin, claimService, profileService);
        ClaimTransferService claimTransferService = new ClaimTransferService(plugin, claimService, profileService);
        RemovalConfirmationService removalConfirmationService = new RemovalConfirmationService(plugin, claimActionService, claimService);
        ExplosionAuthorizationService explosionAuthorizationService = new ExplosionAuthorizationService();
        MenuService menuService = new MenuService(
            plugin,
            claimService,
            profileService,
            claimActionService,
            removalConfirmationService,
            claimInputService,
            claimSelectionService
        );
        return new BootstrapResult(
            pluginConfig,
            groupConfig,
            platformScheduler,
            databaseManager,
            databaseAsyncExecutor,
            claimCoreRegionService,
            claimCoreFactory,
            profileService,
            claimService,
            economyHook,
            hologramService,
            claimCleanupService,
            pendingClaimService,
            claimActionService,
            claimVisualService,
            crossServerTeleportService,
            claimSyncService,
            claimSelectionService,
            claimInputService,
            claimTransferService,
            menuService,
            onlineRewardService,
            removalConfirmationService,
            explosionAuthorizationService
        );
    }

    public record BootstrapResult(
        PluginConfig pluginConfig,
        GroupConfig groupConfig,
        PlatformScheduler platformScheduler,
        DatabaseManager databaseManager,
        DatabaseAsyncExecutor databaseAsyncExecutor,
        ClaimCoreRegionService claimCoreRegionService,
        ClaimCoreFactory claimCoreFactory,
        ProfileService profileService,
        ClaimService claimService,
        EconomyHook economyHook,
        HologramService hologramService,
        ClaimCleanupService claimCleanupService,
        PendingClaimService pendingClaimService,
        ClaimActionService claimActionService,
        ClaimVisualService claimVisualService,
        CrossServerTeleportService crossServerTeleportService,
        ClaimSyncService claimSyncService,
        ClaimSelectionService claimSelectionService,
        ClaimInputService claimInputService,
        ClaimTransferService claimTransferService,
        MenuService menuService,
        OnlineRewardService onlineRewardService,
        RemovalConfirmationService removalConfirmationService,
        ExplosionAuthorizationService explosionAuthorizationService
    ) {
    }
}
