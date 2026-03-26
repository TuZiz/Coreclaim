package com.coreclaim.service;

import com.coreclaim.CoreClaimPlugin;
import com.coreclaim.model.Claim;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;

public final class HologramService {

    private static final String TAG = "coreclaim_hologram";
    private static final String PENDING_TAG = "coreclaim_pending_hologram";

    private final CoreClaimPlugin plugin;
    private final Map<Integer, UUID> claimHolograms = new HashMap<>();
    private final Map<UUID, UUID> pendingHolograms = new HashMap<>();

    public HologramService(CoreClaimPlugin plugin) {
        this.plugin = plugin;
    }

    public void refreshAll(ClaimService claimService) {
        clearAllLoadedHolograms();
        claimHolograms.clear();
        for (Claim claim : claimService.allClaims()) {
            if (claim.coreVisible()) {
                ensureCoreBlock(claim);
                spawnClaimHologram(claim);
            }
        }
    }

    public void spawnClaimHologram(Claim claim) {
        removeClaimHologram(claim.id());
        ensureCoreBlock(claim);
        World world = Bukkit.getWorld(claim.world());
        if (world == null) {
            return;
        }
        ArmorStand armorStand = createHologram(
            new Location(world, claim.centerX() + 0.5D, claim.centerY() + 1.8D, claim.centerZ() + 0.5D),
            plugin.color("&6" + claim.name())
        );
        armorStand.addScoreboardTag(TAG);
        armorStand.addScoreboardTag("claim:" + claim.id());
        claimHolograms.put(claim.id(), armorStand.getUniqueId());
    }

    public void spawnPendingHologram(UUID playerId, Location location) {
        removePendingHologram(playerId);
        ArmorStand armorStand = createHologram(
            location.clone().add(0.5D, 1.8D, 0.5D),
            plugin.color("&e请在聊天栏输入领地名字")
        );
        armorStand.addScoreboardTag(PENDING_TAG);
        pendingHolograms.put(playerId, armorStand.getUniqueId());
    }

    public void removePendingHologram(UUID playerId) {
        UUID entityId = pendingHolograms.remove(playerId);
        if (entityId != null) {
            removeEntity(entityId);
        }
    }

    public void removeClaimHologram(int claimId) {
        UUID entityId = claimHolograms.remove(claimId);
        if (entityId != null) {
            removeEntity(entityId);
        }
    }

    public void clearAllLoadedHolograms() {
        for (World world : Bukkit.getWorlds()) {
            for (ArmorStand armorStand : world.getEntitiesByClass(ArmorStand.class)) {
                if (armorStand.getScoreboardTags().contains(TAG) || armorStand.getScoreboardTags().contains(PENDING_TAG)) {
                    armorStand.remove();
                }
            }
        }
    }

    private ArmorStand createHologram(Location location, String name) {
        World world = location.getWorld();
        if (world == null) {
            throw new IllegalStateException("Cannot create hologram without a world");
        }
        return world.spawn(location, ArmorStand.class, stand -> {
            stand.setInvisible(true);
            stand.setGravity(false);
            stand.setMarker(true);
            stand.setSmall(true);
            stand.setCustomNameVisible(true);
            stand.setCustomName(name);
            stand.setPersistent(false);
            stand.setInvulnerable(true);
        });
    }

    private void removeEntity(UUID entityId) {
        for (World world : Bukkit.getWorlds()) {
            for (Entity entity : world.getEntities()) {
                if (entity.getUniqueId().equals(entityId)) {
                    entity.remove();
                    return;
                }
            }
        }
    }

    private void ensureCoreBlock(Claim claim) {
        World world = Bukkit.getWorld(claim.world());
        if (world == null) {
            return;
        }
        Location location = new Location(world, claim.centerX(), claim.centerY(), claim.centerZ());
        if (location.getBlock().getType().isAir()) {
            location.getBlock().setType(plugin.settings().coreMaterial(), false);
        }
    }
}
