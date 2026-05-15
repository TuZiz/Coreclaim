package com.coreclaim.service;

import com.coreclaim.CoreClaimPlugin;
import com.coreclaim.model.Claim;
import com.coreclaim.platform.PlatformScheduler;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;

public final class HologramService {

    private static final String TAG = "coreclaim_hologram";
    private static final String PENDING_TAG = "coreclaim_pending_hologram";

    private final CoreClaimPlugin plugin;
    private final Map<Integer, HologramRecord> claimHolograms = new ConcurrentHashMap<>();
    private final Map<Integer, LocationKey> claimCoreBlocks = new ConcurrentHashMap<>();
    private final Map<UUID, List<PendingHologramRecord>> pendingHolograms = new ConcurrentHashMap<>();
    private final Map<Integer, PlatformScheduler.TaskHandle> claimSpawnTasks = new ConcurrentHashMap<>();
    private final Map<UUID, PlatformScheduler.TaskHandle> pendingSpawnTasks = new ConcurrentHashMap<>();

    public HologramService(CoreClaimPlugin plugin) {
        this.plugin = plugin;
    }

    public void refreshAll(ClaimService claimService) {
        clearAllLoadedHolograms();
        removeTaggedHologramsInLoadedWorlds();
        claimCoreBlocks.clear();
        for (Claim claim : claimService.allClaims()) {
            if (claim.coreVisible() && claimService.isLocalClaim(claim)) {
                ensureCoreBlock(claim);
                spawnClaimHologram(claim);
            }
        }
    }

    public void reconcileLocalClaimArtifacts(Claim previousClaim, Claim currentClaim, ClaimService claimService) {
        boolean wasLocal = previousClaim != null && claimService.isLocalClaim(previousClaim);
        boolean isLocal = currentClaim != null && claimService.isLocalClaim(currentClaim);

        if (wasLocal && (!isLocal || currentClaim == null || !currentClaim.coreVisible())) {
            removeClaimHologram(previousClaim.id());
            clearCoreBlock(previousClaim);
        }
        if (!isLocal) {
            return;
        }
        if (!currentClaim.coreVisible()) {
            removeClaimHologram(currentClaim.id());
            clearCoreBlock(currentClaim);
            return;
        }
        ensureCoreBlock(currentClaim);
        spawnClaimHologram(currentClaim);
    }

    public void spawnClaimHologram(Claim claim) {
        removeClaimHologram(claim.id());
        ensureCoreBlock(claim);
        Location location = claimHologramLocation(claim);
        if (location == null) {
            return;
        }

        AtomicReference<PlatformScheduler.TaskHandle> taskRef = new AtomicReference<>();
        PlatformScheduler.TaskHandle handle = plugin.platformScheduler().runLocationLater(location, () -> {
            PlatformScheduler.TaskHandle currentHandle = taskRef.get();
            if (currentHandle != null && claimSpawnTasks.get(claim.id()) != currentHandle) {
                return;
            }
            HologramRecord record = createClaimHologram(claim);
            if (record != null) {
                claimHolograms.put(claim.id(), record);
            }
            if (currentHandle != null) {
                claimSpawnTasks.remove(claim.id(), currentHandle);
            }
        }, 1L);
        taskRef.set(handle);
        claimSpawnTasks.put(claim.id(), handle);
    }

    public void spawnPendingHologram(UUID playerId, String playerName, Location location) {
        removePendingHologram(playerId);
        Location anchor = location == null || location.getWorld() == null ? null : location.clone();
        if (anchor == null) {
            return;
        }

        AtomicReference<PlatformScheduler.TaskHandle> taskRef = new AtomicReference<>();
        PlatformScheduler.TaskHandle handle = plugin.platformScheduler().runLocationLater(anchor, () -> {
            PlatformScheduler.TaskHandle currentHandle = taskRef.get();
            if (currentHandle != null && pendingSpawnTasks.get(playerId) != currentHandle) {
                return;
            }
            List<PendingHologramRecord> records = new ArrayList<>();
            records.add(createPendingLine(anchor.clone().add(0.5D, 2.55D, 0.5D), plugin.color("&e&l输入领地名"), false));
            records.add(createPendingLine(anchor.clone().add(0.5D, 2.15D, 0.5D), plugin.color("&f玩家: &6" + playerName), false));
            records.add(createPendingLine(anchor.clone().add(0.5D, 1.75D, 0.5D), plugin.color("&c输入: 取消"), false));
            pendingHolograms.put(playerId, records);
            if (currentHandle != null) {
                pendingSpawnTasks.remove(playerId, currentHandle);
            }
        }, 1L);
        taskRef.set(handle);
        pendingSpawnTasks.put(playerId, handle);
    }

    public void removePendingHologram(UUID playerId) {
        PlatformScheduler.TaskHandle spawnTask = pendingSpawnTasks.remove(playerId);
        if (spawnTask != null) {
            spawnTask.cancel();
        }
        List<PendingHologramRecord> records = pendingHolograms.remove(playerId);
        if (records == null) {
            return;
        }
        for (PendingHologramRecord record : records) {
            removeEntity(record.location(), record.entityId());
        }
    }

    public void removeClaimHologram(int claimId) {
        PlatformScheduler.TaskHandle spawnTask = claimSpawnTasks.remove(claimId);
        if (spawnTask != null) {
            spawnTask.cancel();
        }
        HologramRecord record = claimHolograms.remove(claimId);
        if (record != null) {
            removeEntity(record.location(), record.entityId());
        }
    }

    public void clearAllLoadedHolograms() {
        cancelScheduledSpawns();

        for (HologramRecord record : claimHolograms.values()) {
            removeEntity(record.location(), record.entityId());
        }
        claimHolograms.clear();

        for (List<PendingHologramRecord> records : pendingHolograms.values()) {
            for (PendingHologramRecord record : records) {
                removeEntity(record.location(), record.entityId());
            }
        }
        pendingHolograms.clear();
    }

    public void shutdown() {
        clearAllLoadedHolograms();
        removeTaggedHologramsInLoadedWorlds();
        claimCoreBlocks.clear();
    }

    public void cleanupLoadedTaggedHolograms() {
        removeTaggedHologramsInLoadedWorlds();
    }

    private void removeTaggedHologramsInLoadedWorlds() {
        if (!plugin.isEnabled()) {
            return;
        }
        for (World world : Bukkit.getWorlds()) {
            for (ArmorStand armorStand : world.getEntitiesByClass(ArmorStand.class)) {
                if (armorStand.getScoreboardTags().contains(TAG) || armorStand.getScoreboardTags().contains(PENDING_TAG)) {
                    Location location = armorStand.getLocation();
                    UUID entityId = armorStand.getUniqueId();
                    plugin.platformScheduler().runLocationTask(location, () -> {
                        Entity entity = Bukkit.getEntity(entityId);
                        if (entity != null) {
                            entity.remove();
                        }
                    });
                }
            }
        }
    }

    private void cancelScheduledSpawns() {
        for (PlatformScheduler.TaskHandle handle : claimSpawnTasks.values()) {
            handle.cancel();
        }
        claimSpawnTasks.clear();

        for (PlatformScheduler.TaskHandle handle : pendingSpawnTasks.values()) {
            handle.cancel();
        }
        pendingSpawnTasks.clear();
    }

    private PendingHologramRecord createPendingLine(Location location, String name, boolean small) {
        ArmorStand armorStand = createHologram(location, name, small);
        armorStand.addScoreboardTag(PENDING_TAG);
        return new PendingHologramRecord(
            armorStand.getUniqueId(),
            new LocationKey(location.getWorld().getName(), location.getBlockX(), location.getBlockY(), location.getBlockZ())
        );
    }

    private HologramRecord createClaimHologram(Claim claim) {
        Location location = claimHologramLocation(claim);
        if (location == null) {
            return null;
        }
        String hologramText = plugin.settings().centerCoreHologramText().replace("%claim_name%", claim.name());
        ArmorStand armorStand = createHologram(location, plugin.color(hologramText), true);
        armorStand.addScoreboardTag(TAG);
        armorStand.addScoreboardTag("claim:" + claim.id());
        return new HologramRecord(
            armorStand.getUniqueId(),
            new LocationKey(location.getWorld().getName(), location.getBlockX(), location.getBlockY(), location.getBlockZ())
        );
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

    private void removeEntity(LocationKey locationKey, UUID entityId) {
        if (locationKey == null || entityId == null) {
            return;
        }
        if (!plugin.isEnabled()) {
            return;
        }
        Location location = locationKey.toLocation();
        if (location == null) {
            return;
        }
        plugin.platformScheduler().runLocationTask(location, () -> {
            World world = location.getWorld();
            if (world == null) {
                return;
            }
            for (Entity entity : world.getNearbyEntities(location, 1.0D, 1.0D, 1.0D)) {
                if (entity.getUniqueId().equals(entityId)) {
                    entity.remove();
                    return;
                }
            }
        });
    }

    private void ensureCoreBlock(Claim claim) {
        if (!plugin.isEnabled()) {
            return;
        }
        Location location = claimCoreLocation(claim);
        if (location == null) {
            return;
        }
        plugin.platformScheduler().runLocationTask(location, () -> {
            if (location.getBlock().getType().isAir()) {
                location.getBlock().setType(plugin.settings().coreMaterial(), false);
            }
        });
        claimCoreBlocks.put(claim.id(), new LocationKey(claim.world(), claim.centerX(), claim.centerY(), claim.centerZ()));
    }

    private void clearCoreBlock(Claim claim) {
        claimCoreBlocks.remove(claim.id());
        if (!plugin.isEnabled()) {
            return;
        }
        Location location = claimCoreLocation(claim);
        if (location == null) {
            return;
        }
        plugin.platformScheduler().runLocationTask(location, () -> {
            if (location.getBlock().getType() == plugin.settings().coreMaterial()) {
                location.getBlock().setType(org.bukkit.Material.AIR, false);
            }
        });
    }

    private Location claimCoreLocation(Claim claim) {
        World world = Bukkit.getWorld(claim.world());
        if (world == null) {
            return null;
        }
        return new Location(
            world,
            claim.centerX() + 0.5D,
            claim.centerY(),
            claim.centerZ() + 0.5D
        );
    }

    private Location claimHologramLocation(Claim claim) {
        World world = Bukkit.getWorld(claim.world());
        if (world == null) {
            return null;
        }
        return new Location(
            world,
            claim.centerX() + 0.5D,
            claim.centerY() + plugin.settings().centerCoreHologramHeight() - 0.4D,
            claim.centerZ() + 0.5D
        );
    }

    private record LocationKey(String world, int x, int y, int z) {
        private Location toLocation() {
            World loadedWorld = Bukkit.getWorld(world);
            return loadedWorld == null ? null : new Location(loadedWorld, x, y, z);
        }
    }

    private record HologramRecord(UUID entityId, LocationKey location) {
    }

    private record PendingHologramRecord(UUID entityId, LocationKey location) {
    }
}
