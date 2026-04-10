package com.coreclaim.service;

import com.coreclaim.CoreClaimPlugin;
import com.coreclaim.model.Claim;
import com.coreclaim.platform.PlatformScheduler;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.entity.Player;

public final class ClaimVisualService {

    private final CoreClaimPlugin plugin;

    public ClaimVisualService(CoreClaimPlugin plugin) {
        this.plugin = plugin;
    }

    public void showClaim(Player player, Claim claim) {
        if (player == null || claim == null) {
            return;
        }
        World world = Bukkit.getWorld(claim.world());
        if (world == null) {
            return;
        }

        int iterations = Math.max(1, (plugin.settings().claimVisualDurationSeconds() * 20) / plugin.settings().claimVisualIntervalTicks());
        Particle.DustOptions dust = new Particle.DustOptions(
            Color.fromRGB(
                plugin.settings().claimVisualRed(),
                plugin.settings().claimVisualGreen(),
                plugin.settings().claimVisualBlue()
            ),
            plugin.settings().claimVisualSize()
        );

        final int[] remaining = {iterations};
        final PlatformScheduler.TaskHandle[] handle = new PlatformScheduler.TaskHandle[1];
        handle[0] = plugin.platformScheduler().runRepeating(() -> {
            if (!player.isOnline() || remaining[0]-- <= 0) {
                if (handle[0] != null) {
                    handle[0].cancel();
                }
                return;
            }
            render(player, claim, world, dust);
        }, 0L, plugin.settings().claimVisualIntervalTicks());
    }

    public void showPendingLocation(Player player, Location location) {
        if (player == null || location == null || location.getWorld() == null) {
            return;
        }

        Particle.DustOptions dust = new Particle.DustOptions(
            Color.fromRGB(255, 214, 64),
            Math.max(0.8F, plugin.settings().claimVisualSize() - 0.2F)
        );
        final int[] remaining = {12};
        final PlatformScheduler.TaskHandle[] handle = new PlatformScheduler.TaskHandle[1];
        handle[0] = plugin.platformScheduler().runRepeating(() -> {
            if (!player.isOnline() || remaining[0]-- <= 0) {
                if (handle[0] != null) {
                    handle[0].cancel();
                }
                return;
            }
            renderPendingRing(player, location, dust);
        }, 0L, 8L);
    }

    public void showSelection(Player player, World world, int minX, int maxX, int minY, int maxY, int minZ, int maxZ) {
        if (player == null || world == null) {
            return;
        }
        Particle.DustOptions dust = new Particle.DustOptions(
            Color.fromRGB(255, 214, 64),
            Math.max(0.9F, plugin.settings().claimVisualSize() - 0.1F)
        );
        final int[] remaining = {8};
        final PlatformScheduler.TaskHandle[] handle = new PlatformScheduler.TaskHandle[1];
        handle[0] = plugin.platformScheduler().runRepeating(() -> {
            if (!player.isOnline() || remaining[0]-- <= 0) {
                if (handle[0] != null) {
                    handle[0].cancel();
                }
                return;
            }
            renderCuboid(
                player,
                world,
                minX + 0.5D,
                maxX + 0.5D,
                minY + 0.15D,
                maxY + 1.15D,
                minZ + 0.5D,
                maxZ + 0.5D,
                dust
            );
        }, 0L, 8L);
    }

    private void render(Player player, Claim claim, World world, Particle.DustOptions dust) {
        double minX = claim.centerX() - claim.west() + 0.5D;
        double maxX = claim.centerX() + claim.east() + 0.5D;
        double minZ = claim.centerZ() - claim.north() + 0.5D;
        double maxZ = claim.centerZ() + claim.south() + 0.5D;
        if (claim.fullHeight()) {
            renderFlatBounds(player, world, minX, maxX, minZ, maxZ, claim.centerY() + 0.15D, dust);
            return;
        }
        renderCuboid(
            player,
            world,
            minX,
            maxX,
            claim.minY() + 0.15D,
            claim.maxY() + 1.15D,
            minZ,
            maxZ,
            dust
        );
    }

    private void renderFlatBounds(Player player, World world, double minX, double maxX, double minZ, double maxZ, double baseY, Particle.DustOptions dust) {
        renderPillar(player, world, minX, baseY, baseY + plugin.settings().claimVisualPillarHeight(), minZ, dust);
        renderPillar(player, world, minX, baseY, baseY + plugin.settings().claimVisualPillarHeight(), maxZ, dust);
        renderPillar(player, world, maxX, baseY, baseY + plugin.settings().claimVisualPillarHeight(), minZ, dust);
        renderPillar(player, world, maxX, baseY, baseY + plugin.settings().claimVisualPillarHeight(), maxZ, dust);
        renderLine(player, world, minX, baseY, minZ, maxX, baseY, minZ, dust);
        renderLine(player, world, minX, baseY, maxZ, maxX, baseY, maxZ, dust);
        renderLine(player, world, minX, baseY, minZ, minX, baseY, maxZ, dust);
        renderLine(player, world, maxX, baseY, minZ, maxX, baseY, maxZ, dust);
    }

    private void renderCuboid(
        Player player,
        World world,
        double minX,
        double maxX,
        double minY,
        double maxY,
        double minZ,
        double maxZ,
        Particle.DustOptions dust
    ) {
        renderPillar(player, world, minX, minY, maxY, minZ, dust);
        renderPillar(player, world, minX, minY, maxY, maxZ, dust);
        renderPillar(player, world, maxX, minY, maxY, minZ, dust);
        renderPillar(player, world, maxX, minY, maxY, maxZ, dust);

        renderLine(player, world, minX, minY, minZ, maxX, minY, minZ, dust);
        renderLine(player, world, minX, minY, maxZ, maxX, minY, maxZ, dust);
        renderLine(player, world, minX, minY, minZ, minX, minY, maxZ, dust);
        renderLine(player, world, maxX, minY, minZ, maxX, minY, maxZ, dust);

        renderLine(player, world, minX, maxY, minZ, maxX, maxY, minZ, dust);
        renderLine(player, world, minX, maxY, maxZ, maxX, maxY, maxZ, dust);
        renderLine(player, world, minX, maxY, minZ, minX, maxY, maxZ, dust);
        renderLine(player, world, maxX, maxY, minZ, maxX, maxY, maxZ, dust);
    }

    private void renderPillar(Player player, World world, double x, double minY, double maxY, double z, Particle.DustOptions dust) {
        double step = plugin.settings().claimVisualPillarStep();
        for (double currentY = minY; currentY <= maxY; currentY += step) {
            player.spawnParticle(Particle.REDSTONE, x, currentY, z, 1, 0D, 0D, 0D, 0D, dust);
        }
    }

    private void renderLine(
        Player player,
        World world,
        double startX,
        double startY,
        double startZ,
        double endX,
        double endY,
        double endZ,
        Particle.DustOptions dust
    ) {
        double dx = endX - startX;
        double dy = endY - startY;
        double dz = endZ - startZ;
        double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (distance <= 0D) {
            player.spawnParticle(Particle.REDSTONE, startX, startY, startZ, 1, 0D, 0D, 0D, 0D, dust);
            return;
        }

        double step = plugin.settings().claimVisualLineStep();
        int points = Math.max(1, (int) Math.ceil(distance / step));
        for (int index = 0; index <= points; index++) {
            double progress = index / (double) points;
            double x = startX + (dx * progress);
            double y = startY + (dy * progress);
            double z = startZ + (dz * progress);
            player.spawnParticle(Particle.REDSTONE, x, y, z, 1, 0D, 0D, 0D, 0D, dust);
        }
    }

    private void renderPendingRing(Player player, Location location, Particle.DustOptions dust) {
        double centerX = location.getBlockX() + 0.5D;
        double centerY = location.getBlockY() + 0.1D;
        double centerZ = location.getBlockZ() + 0.5D;
        double radius = 0.95D;
        int points = 24;
        for (int index = 0; index < points; index++) {
            double angle = (Math.PI * 2D * index) / points;
            double x = centerX + (Math.cos(angle) * radius);
            double z = centerZ + (Math.sin(angle) * radius);
            player.spawnParticle(Particle.REDSTONE, x, centerY, z, 1, 0D, 0D, 0D, 0D, dust);
        }
    }
}
