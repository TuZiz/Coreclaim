package com.coreclaim.service;

import com.coreclaim.CoreClaimPlugin;
import com.coreclaim.config.ClaimGroup;
import com.coreclaim.item.ClaimCoreFactory;
import com.coreclaim.model.Claim;
import com.coreclaim.model.PlayerProfile;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;

public final class PendingClaimService {

    private final CoreClaimPlugin plugin;
    private final ClaimService claimService;
    private final ProfileService profileService;
    private final ClaimCoreFactory claimCoreFactory;
    private final HologramService hologramService;
    private final Map<UUID, PendingClaim> pendingClaims = new ConcurrentHashMap<>();

    public PendingClaimService(
        CoreClaimPlugin plugin,
        ClaimService claimService,
        ProfileService profileService,
        ClaimCoreFactory claimCoreFactory,
        HologramService hologramService
    ) {
        this.plugin = plugin;
        this.claimService = claimService;
        this.profileService = profileService;
        this.claimCoreFactory = claimCoreFactory;
        this.hologramService = hologramService;
    }

    public boolean beginClaimCreation(Player player, Location coreLocation) {
        PlayerProfile profile = profileService.getOrCreate(player.getUniqueId(), player.getName());
        ClaimGroup group = plugin.groups().resolve(player);
        int maxClaims = group.claimSlotsForActivity(profile.activityPoints());
        if (claimService.countClaims(player.getUniqueId()) >= maxClaims) {
            player.sendMessage(plugin.message("claim-no-slot"));
            return false;
        }

        int initialDistance = group.initialDistance();
        int minX = coreLocation.getBlockX() - initialDistance;
        int maxX = coreLocation.getBlockX() + initialDistance;
        int minZ = coreLocation.getBlockZ() - initialDistance;
        int maxZ = coreLocation.getBlockZ() + initialDistance;
        if (claimService.overlaps(coreLocation.getWorld().getName(), minX, maxX, minZ, maxZ, null)) {
            player.sendMessage(plugin.message("claim-overlap"));
            return false;
        }
        if (claimService.hasCoreWithinSpacing(
            coreLocation.getWorld().getName(),
            coreLocation.getBlockX(),
            coreLocation.getBlockZ(),
            plugin.settings().minimumCoreSpacing(),
            null
        )) {
            player.sendMessage(plugin.message("claim-core-too-close"));
            return false;
        }

        cancelPendingClaim(player, false);
        pendingClaims.put(player.getUniqueId(), new PendingClaim(player.getUniqueId(), coreLocation));
        hologramService.spawnPendingHologram(player.getUniqueId(), coreLocation);
        player.sendMessage(plugin.message("claim-name-prompt"));
        return true;
    }

    public boolean hasPendingClaim(UUID playerId) {
        return pendingClaims.containsKey(playerId);
    }

    public Claim completeClaim(Player player, String inputName) {
        PendingClaim pending = pendingClaims.remove(player.getUniqueId());
        if (pending == null) {
            return null;
        }
        hologramService.removePendingHologram(player.getUniqueId());

        String name = inputName == null ? "" : inputName.trim();
        if (name.isEmpty()) {
            restoreCore(pending);
            player.sendMessage(plugin.message("claim-name-empty"));
            return null;
        }
        if (name.length() > plugin.settings().claimNameMaxLength()) {
            restoreCore(pending);
            player.sendMessage(plugin.message("claim-name-too-long", "{max}", String.valueOf(plugin.settings().claimNameMaxLength())));
            return null;
        }

        ClaimGroup group = plugin.groups().resolve(player);
        Claim claim = claimService.createClaim(player.getUniqueId(), player.getName(), name, pending.coreLocation(), group.initialDistance());
        hologramService.spawnClaimHologram(claim);
        player.sendMessage(plugin.message(
            "claim-name-created",
            "{name}", claim.name(),
            "{width}", String.valueOf(claim.width()),
            "{depth}", String.valueOf(claim.depth())
        ));
        return claim;
    }

    public void cancelPendingClaim(Player player, boolean notify) {
        PendingClaim pending = pendingClaims.remove(player.getUniqueId());
        if (pending == null) {
            return;
        }
        hologramService.removePendingHologram(player.getUniqueId());
        restoreCore(pending);
        if (notify) {
            player.sendMessage(plugin.message("claim-name-cancelled"));
        }
    }

    public void timeoutPendingClaim(UUID playerId) {
        PendingClaim pending = pendingClaims.remove(playerId);
        if (pending == null) {
            return;
        }
        hologramService.removePendingHologram(playerId);
        restoreCore(pending);
        Player player = plugin.getServer().getPlayer(playerId);
        if (player != null) {
            player.sendMessage(plugin.message("claim-name-timeout"));
        }
    }

    private void restoreCore(PendingClaim pending) {
        Location location = pending.coreLocation();
        if (location.getBlock().getType() == plugin.settings().coreMaterial()) {
            location.getBlock().setType(Material.AIR, false);
        }
        Player player = plugin.getServer().getPlayer(pending.ownerId());
        if (player != null) {
            claimCoreFactory.giveClaimCore(player, 1);
        } else if (location.getWorld() != null) {
            location.getWorld().dropItemNaturally(location.clone().add(0.5D, 0.5D, 0.5D), claimCoreFactory.createClaimCore(1));
        }
    }

    public record PendingClaim(UUID ownerId, Location coreLocation) {
    }
}
