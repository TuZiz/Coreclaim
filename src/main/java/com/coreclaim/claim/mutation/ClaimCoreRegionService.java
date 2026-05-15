package com.coreclaim.claim.mutation;

import com.coreclaim.CoreClaimPlugin;
import org.bukkit.Location;
import org.bukkit.Material;

public final class ClaimCoreRegionService {

    private final CoreClaimPlugin plugin;

    public ClaimCoreRegionService(CoreClaimPlugin plugin) {
        this.plugin = plugin;
    }

    public boolean placeTemporaryCore(Location coreLocation, ClaimCreationOptions options) {
        if (options == null || !options.placeCoreBlock()) {
            return false;
        }
        if (coreLocation == null || coreLocation.getWorld() == null || !coreLocation.getBlock().getType().isAir()) {
            throw new IllegalArgumentException(options.coreBlockedFailureKey());
        }
        coreLocation.getBlock().setType(plugin.settings().coreMaterial(), false);
        if (coreLocation.getBlock().getType() != plugin.settings().coreMaterial()) {
            throw new IllegalArgumentException(options.coreBlockedFailureKey());
        }
        return true;
    }

    public void clearTemporaryCore(Location coreLocation) {
        if (coreLocation == null || coreLocation.getWorld() == null) {
            return;
        }
        if (coreLocation.getBlock().getType() == plugin.settings().coreMaterial()) {
            coreLocation.getBlock().setType(Material.AIR, false);
        }
    }

    public boolean isCoreStillPlaced(Location coreLocation) {
        return coreLocation != null
            && coreLocation.getWorld() != null
            && coreLocation.getBlock().getType() == plugin.settings().coreMaterial();
    }
}
