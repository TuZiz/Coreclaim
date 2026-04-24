package com.coreclaim.bootstrap;

import com.coreclaim.CoreClaimPlugin;
import com.coreclaim.command.CoreClaimCommand;
import org.bukkit.command.PluginCommand;

public final class CommandRegistrar {

    public void registerClaimCommand(CoreClaimPlugin plugin, PluginBootstrap.BootstrapResult bootstrap) {
        PluginCommand command = plugin.getCommand("claim");
        if (command == null) {
            return;
        }
        CoreClaimCommand executor = new CoreClaimCommand(
            plugin,
            bootstrap.claimService(),
            bootstrap.profileService(),
            bootstrap.claimActionService(),
            bootstrap.claimVisualService(),
            bootstrap.claimSelectionService(),
            bootstrap.menuService(),
            bootstrap.removalConfirmationService(),
            bootstrap.claimTransferService(),
            bootstrap.claimCleanupService()
        );
        command.setExecutor(executor);
        command.setTabCompleter(executor);
    }
}
