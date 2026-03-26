package com.coreclaim.economy;

import com.coreclaim.CoreClaimPlugin;
import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.entity.Player;
import org.bukkit.plugin.RegisteredServiceProvider;

public final class EconomyHook {

    private final CoreClaimPlugin plugin;
    private Economy economy;

    public EconomyHook(CoreClaimPlugin plugin) {
        this.plugin = plugin;
        setup();
    }

    private void setup() {
        if (plugin.getServer().getPluginManager().getPlugin("Vault") == null) {
            return;
        }
        RegisteredServiceProvider<Economy> registration = plugin.getServer()
            .getServicesManager()
            .getRegistration(Economy.class);
        if (registration != null) {
            this.economy = registration.getProvider();
        }
    }

    public boolean available() {
        return economy != null;
    }

    public boolean has(Player player, double amount) {
        return amount <= 0D || (economy != null && economy.has(player, amount));
    }

    public boolean withdraw(Player player, double amount) {
        if (amount <= 0D) {
            return true;
        }
        if (economy == null) {
            return false;
        }
        EconomyResponse response = economy.withdrawPlayer(player, amount);
        return response.transactionSuccess();
    }
}

