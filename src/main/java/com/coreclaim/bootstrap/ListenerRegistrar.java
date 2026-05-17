package com.coreclaim.bootstrap;

import com.coreclaim.CoreClaimPlugin;
import com.coreclaim.listener.ClaimCoreInteractionListener;
import com.coreclaim.listener.ClaimCoreListener;
import com.coreclaim.listener.ClaimEnterLeaveListener;
import com.coreclaim.listener.ClaimEnvironmentProtectionListener;
import com.coreclaim.listener.ClaimInputListener;
import com.coreclaim.listener.ClaimNamingListener;
import com.coreclaim.listener.ClaimSelectionListener;
import com.coreclaim.listener.CrossServerTeleportListener;
import com.coreclaim.listener.MenuListener;
import com.coreclaim.listener.PendingCoreProtectionListener;
import com.coreclaim.listener.RemovalConfirmListener;
import com.coreclaim.listener.SelectionToolListener;
import com.coreclaim.protection.listener.BlockProtectionListener;
import com.coreclaim.protection.listener.EntityProtectionListener;
import com.coreclaim.protection.listener.InteractionProtectionListener;
import com.coreclaim.protection.listener.ProjectileProtectionListener;
import com.coreclaim.protection.listener.ProtectionRuleSupport;
import com.coreclaim.protection.listener.VehicleProtectionListener;

public final class ListenerRegistrar {

    public ClaimEnterLeaveListener registerAll(CoreClaimPlugin plugin, PluginBootstrap.BootstrapResult bootstrap) {
        ProtectionRuleSupport protectionRuleSupport = new ProtectionRuleSupport(
            plugin,
            bootstrap.claimService(),
            bootstrap.claimCoreFactory(),
            bootstrap.explosionAuthorizationService(),
            bootstrap.claimCleanupService()
        );
        plugin.getServer().getPluginManager().registerEvents(
            new ClaimCoreListener(plugin, bootstrap.claimCoreFactory(), bootstrap.pendingClaimService()),
            plugin
        );
        plugin.getServer().getPluginManager().registerEvents(new BlockProtectionListener(protectionRuleSupport), plugin);
        plugin.getServer().getPluginManager().registerEvents(new PendingCoreProtectionListener(bootstrap.pendingCoreReservationService()), plugin);
        plugin.getServer().getPluginManager().registerEvents(new InteractionProtectionListener(protectionRuleSupport), plugin);
        plugin.getServer().getPluginManager().registerEvents(new EntityProtectionListener(protectionRuleSupport), plugin);
        plugin.getServer().getPluginManager().registerEvents(new ProjectileProtectionListener(protectionRuleSupport), plugin);
        plugin.getServer().getPluginManager().registerEvents(new VehicleProtectionListener(protectionRuleSupport), plugin);
        plugin.getServer().getPluginManager().registerEvents(new ClaimSelectionListener(bootstrap.claimSelectionService()), plugin);
        plugin.getServer().getPluginManager().registerEvents(
            new SelectionToolListener(bootstrap.claimSelectionService(), bootstrap.onlineRewardService()),
            plugin
        );
        plugin.getServer().getPluginManager().registerEvents(
            new ClaimEnvironmentProtectionListener(
                bootstrap.claimService(),
                bootstrap.explosionAuthorizationService(),
                bootstrap.claimCleanupService(),
                plugin.settings()
            ),
            plugin
        );
        plugin.getServer().getPluginManager().registerEvents(
            new ClaimCoreInteractionListener(
                plugin,
                bootstrap.claimService(),
                bootstrap.pendingClaimService(),
                bootstrap.claimActionService(),
                bootstrap.menuService()
            ),
            plugin
        );
        plugin.getServer().getPluginManager().registerEvents(new ClaimNamingListener(plugin, bootstrap.pendingClaimService()), plugin);
        plugin.getServer().getPluginManager().registerEvents(new ClaimInputListener(plugin, bootstrap.claimInputService()), plugin);
        plugin.getServer().getPluginManager().registerEvents(new MenuListener(bootstrap.menuService()), plugin);
        ClaimEnterLeaveListener claimEnterLeaveListener = new ClaimEnterLeaveListener(
            plugin,
            bootstrap.claimService(),
            bootstrap.profileService(),
            bootstrap.claimVisualService()
        );
        plugin.getServer().getPluginManager().registerEvents(claimEnterLeaveListener, plugin);
        plugin.getServer().getPluginManager().registerEvents(
            new CrossServerTeleportListener(plugin, bootstrap.crossServerTeleportService()),
            plugin
        );
        plugin.getServer().getPluginManager().registerEvents(
            new RemovalConfirmListener(plugin, bootstrap.removalConfirmationService()),
            plugin
        );
        return claimEnterLeaveListener;
    }
}
