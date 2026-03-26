package com.coreclaim.service;

import com.coreclaim.CoreClaimPlugin;
import com.coreclaim.model.Claim;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
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
    private final Map<UUID, List<UUID>> pendingHolograms = new HashMap<>();

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
        String hologramText = plugin.settings().centerCoreHologramText().replace("%claim_name%", claim.name());
        ArmorStand armorStand = createHologram(
            new Location(
                world,
                claim.centerX() + 0.5D,
                claim.centerY() + plugin.settings().centerCoreHologramHeight(),
                claim.centerZ() + 0.5D
            ),
            plugin.color(hologramText),
            true
        );
        armorStand.addScoreboardTag(TAG);
        armorStand.addScoreboardTag("claim:" + claim.id());
        claimHolograms.put(claim.id(), armorStand.getUniqueId());
    }

    public void spawnPendingHologram(UUID playerId, String playerName, Location location) {
        removePendingHologram(playerId);
        List<UUID> entityIds = new ArrayList<>();
        entityIds.add(createPendingLine(location.clone().add(0.5D, 2.55D, 0.5D), plugin.color("&e&l输入领地名"), false));
        entityIds.add(createPendingLine(location.clone().add(0.5D, 2.15D, 0.5D), plugin.color("&f玩家: &6" + playerName), false));
        entityIds.add(createPendingLine(location.clone().add(0.5D, 1.75D, 0.5D), plugin.color("&c输入: 取消"), false));
        pendingHolograms.put(playerId, entityIds);
    }

    public void removePendingHologram(UUID playerId) {
        List<UUID> entityIds = pendingHolograms.remove(playerId);
        if (entityIds == null) {
            return;
        }
        for (UUID entityId : entityIds) {
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

    private UUID createPendingLine(Location location, String name, boolean small) {
        ArmorStand armorStand = createHologram(location, name, small);
        armorStand.addScoreboardTag(PENDING_TAG);
        return armorStand.getUniqueId();
    }

    private ArmorStand createHologram(Location location, String name, boolean small) {
        World world = location.getWorld();
        if (world == null) {
            throw new IllegalStateException("Cannot create hologram without a world");
        }
        return world.spawn(location, ArmorStand.class, stand -> {
            stand.setInvisible(true);
            stand.setGravity(false);
            stand.setMarker(true);
            stand.setSmall(small);
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
