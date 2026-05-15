package com.coreclaim.service;

import com.coreclaim.teleport.CrossServerTeleportService;
import com.coreclaim.CoreClaimPlugin;
import com.coreclaim.config.ClaimGroup;
import com.coreclaim.economy.EconomyHook;
import com.coreclaim.model.Claim;
import com.coreclaim.model.ClaimDirection;
import com.coreclaim.model.ClaimPermission;
import com.coreclaim.util.AdminAccess;
import java.text.DecimalFormat;
import java.time.Instant;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public final class ClaimActionService {

    private static final DecimalFormat MONEY = new DecimalFormat("0.##");

    private final CoreClaimPlugin plugin;
    private final ClaimService claimService;
    private final HologramService hologramService;
    private final ClaimVisualService claimVisualService;
    private final EconomyHook economyHook;
    private final CrossServerTeleportService crossServerTeleportService;

    public ClaimActionService(
        CoreClaimPlugin plugin,
        ClaimService claimService,
        HologramService hologramService,
        ClaimVisualService claimVisualService,
        EconomyHook economyHook,
        CrossServerTeleportService crossServerTeleportService
    ) {
        this.plugin = plugin;
        this.claimService = claimService;
        this.hologramService = hologramService;
        this.claimVisualService = claimVisualService;
        this.economyHook = economyHook;
        this.crossServerTeleportService = crossServerTeleportService;
    }

    public Claim findOwnedClaim(Player player) {
        return claimService.findClaim(player.getLocation())
            .filter(claim -> claim.owner().equals(player.getUniqueId()))
            .orElse(null);
    }

    public Claim findCurrentClaim(Player player) {
        return claimService.findClaim(player.getLocation()).orElse(null);
    }

    public Claim findCurrentPresenceClaim(Player player) {
        return claimService.findPlayerPresenceClaim(player.getLocation()).orElse(null);
    }

    public ExpansionPreview previewExpansion(Player player, Claim claim, ClaimDirection direction) {
        return buildExpansionPreview(player, claim, direction, plugin.settings().directionExpandAmount());
    }

    public ExpansionPreview previewExpansion(Player player, Claim claim, ClaimDirection direction, int amount) {
        return buildExpansionPreview(player, claim, direction, amount);
    }

    public boolean canPayExpansionCost(Player player, ExpansionPreview preview) {
        if (preview == null || !preview.allowed()) {
            return false;
        }
        if (preview.cost() <= 0D) {
            return true;
        }
        return economyHook.available() && economyHook.has(player, preview.cost());
    }

    public boolean hasExpansionEconomy(ExpansionPreview preview) {
        return preview != null && (preview.cost() <= 0D || economyHook.available());
    }

    public boolean expandCurrentClaim(Player player, ClaimDirection direction) {
        Claim claim = findOwnedClaim(player);
        if (claim == null) {
            player.sendMessage(plugin.message("claim-not-found"));
            return false;
        }
        return expandClaim(player, claim, direction, plugin.settings().directionExpandAmount());
    }

    public boolean expandClaim(Player player, Claim claim, ClaimDirection direction, int amount) {
        if (!canManageClaim(player, claim)) {
            player.sendMessage(plugin.message("trust-no-permission"));
            return false;
        }
        long cooldownLeft = cooldownRemainingSeconds(claim);
        if (cooldownLeft > 0) {
            player.sendMessage(plugin.message("claim-expand-cooldown", "{seconds}", String.valueOf(cooldownLeft)));
            return false;
        }

        ExpansionPreview preview = buildExpansionPreview(player, claim, direction, amount);
        if (!preview.allowed()) {
            if (preview.hitMax()) {
                player.sendMessage(plugin.message("claim-max-size"));
            } else if (preview.overlap()) {
                player.sendMessage(plugin.message("claim-overlap"));
            } else {
                player.sendMessage(plugin.message("economy-missing"));
            }
            return false;
        }

        if (preview.cost() > 0D) {
            if (!economyHook.available()) {
                player.sendMessage(plugin.message("economy-missing"));
                return false;
            }
            if (!economyHook.has(player, preview.cost())) {
                player.sendMessage(plugin.message("economy-not-enough", "{cost}", MONEY.format(preview.cost())));
                return false;
            }
            if (!economyHook.withdraw(player, preview.cost())) {
                player.sendMessage(plugin.message("economy-missing"));
                return false;
            }
        }

        if (direction.vertical()) {
            claimService.updateHeightBounds(claim, preview.minY(), preview.maxY(), preview.fullHeight(), player.getUniqueId());
        } else {
            claimService.updateBounds(claim, preview.east(), preview.south(), preview.west(), preview.north(), player.getUniqueId());
        }
        player.sendMessage(plugin.message(
            "claim-expand-success",
            "{direction}", direction.displayName(),
            "{amount}", String.valueOf(preview.expandAmount()),
            "{cost}", MONEY.format(preview.cost()),
            "{width}", String.valueOf(preview.width()),
            "{height}", String.valueOf(preview.height()),
            "{depth}", String.valueOf(preview.depth())
        ));
        return true;
    }

    public boolean unclaimCurrent(Player player) {
        Claim claim = findOwnedClaim(player);
        if (claim == null) {
            player.sendMessage(plugin.message("claim-not-found"));
            return false;
        }
        return unclaim(player, claim);
    }

    public boolean unclaim(Player player, Claim claim) {
        if (!canManageClaim(player, claim)) {
            player.sendMessage(plugin.message("trust-no-permission"));
            return false;
        }
        hologramService.removeClaimHologram(claim.id());
        claimService.removeClaim(claim);
        player.sendMessage(plugin.message("claim-removed"));
        return true;
    }

    public boolean adminRemoveClaim(CommandSender sender, Claim claim) {
        if (claim == null) {
            sender.sendMessage(plugin.message("claim-not-found"));
            return false;
        }
        hologramService.removeClaimHologram(claim.id());
        claimService.removeClaim(claim);
        sender.sendMessage(plugin.message("admin-remove-success", "{name}", claim.name(), "{owner}", claim.ownerName()));
        return true;
    }

    public boolean hideClaimCore(Player player, Claim claim) {
        if (!claim.owner().equals(player.getUniqueId()) && !AdminAccess.hasClaimManageAccess(player)) {
            player.sendMessage(plugin.message("trust-no-permission"));
            return false;
        }
        if (!claim.coreVisible()) {
            player.sendMessage(plugin.message("claim-core-already-hidden"));
            return false;
        }
        hologramService.removeClaimHologram(claim.id());
        claimService.updateCoreVisibility(claim, false, player.getUniqueId());
        World world = claimService.isLocalClaim(claim) ? Bukkit.getWorld(claim.world()) : null;
        if (world != null) {
            Location coreLocation = new Location(world, claim.centerX(), claim.centerY(), claim.centerZ());
            plugin.platformScheduler().runLocationTask(coreLocation, () -> {
                if (coreLocation.getBlock().getType() == plugin.settings().coreMaterial()) {
                    coreLocation.getBlock().setType(org.bukkit.Material.AIR, false);
                }
            });
        }
        player.sendMessage(plugin.message("claim-core-hidden", "{name}", claim.name()));
        return true;
    }

    public void syncClaimCoreState(Claim claim) {
        if (claim == null || !claim.coreVisible()) {
            return;
        }
        World world = claimService.isLocalClaim(claim) ? Bukkit.getWorld(claim.world()) : null;
        if (world == null) {
            return;
        }
        Location coreLocation = new Location(world, claim.centerX(), claim.centerY(), claim.centerZ());
        if (coreLocation.getBlock().getType() == plugin.settings().coreMaterial()) {
            return;
        }
        hologramService.removeClaimHologram(claim.id());
        claimService.updateCoreVisibility(claim, false);
    }

    public boolean trustCurrentClaim(Player player, OfflinePlayer target) {
        Claim claim = findOwnedClaim(player);
        if (claim == null) {
            player.sendMessage(plugin.message("claim-not-found"));
            return false;
        }
        return trustPlayer(player, claim, target);
    }

    public boolean trustPlayer(Player player, Claim claim, OfflinePlayer target) {
        if (!canManageMembers(player, claim)) {
            player.sendMessage(plugin.message("trust-no-permission"));
            return false;
        }
        if (target == null || target.getUniqueId() == null) {
            player.sendMessage(plugin.message("trust-no-target"));
            return false;
        }
        if (player.getUniqueId().equals(target.getUniqueId())) {
            player.sendMessage(plugin.message("trust-self"));
            return false;
        }
        if (!claimService.addTrustedMember(claim, target.getUniqueId(), player.getUniqueId())) {
            player.sendMessage(plugin.message("trust-already", "{player}", displayName(target)));
            return false;
        }
        player.sendMessage(plugin.message("trust-added", "{player}", displayName(target), "{name}", claim.name()));
        Player onlineTarget = Bukkit.getPlayer(target.getUniqueId());
        if (onlineTarget != null) {
            onlineTarget.sendMessage(plugin.message("trust-added-notify", "{owner}", player.getName(), "{name}", claim.name()));
        }
        return true;
    }

    public boolean untrustCurrentClaim(Player player, OfflinePlayer target) {
        Claim claim = findOwnedClaim(player);
        if (claim == null) {
            player.sendMessage(plugin.message("claim-not-found"));
            return false;
        }
        return untrustPlayer(player, claim, target);
    }

    public boolean untrustPlayer(Player player, Claim claim, OfflinePlayer target) {
        if (!canManageMembers(player, claim)) {
            player.sendMessage(plugin.message("trust-no-permission"));
            return false;
        }
        if (target == null || target.getUniqueId() == null) {
            player.sendMessage(plugin.message("trust-no-target"));
            return false;
        }
        if (!claimService.removeTrustedMember(claim, target.getUniqueId(), player.getUniqueId())) {
            player.sendMessage(plugin.message("trust-missing", "{player}", displayName(target)));
            return false;
        }
        player.sendMessage(plugin.message("trust-removed", "{player}", displayName(target), "{name}", claim.name()));
        return true;
    }

    public boolean teleportToClaim(Player player, Claim claim) {
        Claim freshClaim = claimService.findClaimByIdFresh(claim.id()).orElse(null);
        if (freshClaim == null) {
            player.sendMessage(plugin.message("claim-not-found"));
            return false;
        }
        claim = freshClaim;
        if (!hasAdminForcePermission(player) && !claimService.hasPermission(claim, player.getUniqueId(), ClaimPermission.TELEPORT)) {
            player.sendMessage(plugin.message("trust-no-permission"));
            return false;
        }
        World world = claimService.isLocalClaim(claim) ? Bukkit.getWorld(claim.world()) : null;
        if (world == null) {
            if (crossServerTeleportService != null && crossServerTeleportService.transferToRemoteClaim(player, claim)) {
                return true;
            }
            player.sendMessage(plugin.message("world-missing"));
            return false;
        }
        ClaimService.TeleportTarget target = claimService.teleportTarget(claim, player.getLocation().getYaw(), player.getLocation().getPitch());
        Location destination = new Location(world, target.x(), target.y(), target.z(), target.yaw(), target.pitch());
        player.teleport(destination);
        claimVisualService.showClaim(player, claim);
        player.sendMessage(plugin.message("claim-teleported", "{name}", claim.name()));
        return true;
    }

    private ExpansionPreview buildExpansionPreview(Player player, Claim claim, ClaimDirection direction, int amount) {
        World world = claimService.isLocalClaim(claim) ? Bukkit.getWorld(claim.world()) : null;
        int worldMinY = world == null ? -64 : world.getMinHeight();
        int worldMaxY = world == null ? 319 : world.getMaxHeight() - 1;
        ClaimGroup group = plugin.groups().resolve(player);
        int east = claim.east();
        int south = claim.south();
        int west = claim.west();
        int north = claim.north();
        int minY = claim.minY();
        int maxY = claim.maxY();

        int currentDistance = claim.distance(direction);
        int expandAmount = clampExpandAmount(group, claim, direction, amount, worldMinY, worldMaxY);
        if (expandAmount <= 0) {
            return new ExpansionPreview(false, 0D, currentDistance, 0, claim.width(), claim.height(), claim.depth(), east, south, west, north, minY, maxY, claim.fullHeight(), true, false);
        }

        int targetDistance = currentDistance + expandAmount;
        switch (direction) {
            case EAST -> east = targetDistance;
            case SOUTH -> south = targetDistance;
            case WEST -> west = targetDistance;
            case NORTH -> north = targetDistance;
            case UP -> maxY = Math.min(worldMaxY, maxY + expandAmount);
            case DOWN -> minY = Math.max(worldMinY, minY - expandAmount);
        }
        boolean fullHeight = minY <= worldMinY && maxY >= worldMaxY;

        int minX = claim.centerX() - west;
        int maxX = claim.centerX() + east;
        int minZ = claim.centerZ() - north;
        int maxZ = claim.centerZ() + south;
        if (claimService.overlaps(claim.world(), minX, maxX, minY, maxY, minZ, maxZ, claim.id(), fullHeight)) {
            return new ExpansionPreview(false, 0D, currentDistance, 0, claim.width(), claim.height(), claim.depth(), east, south, west, north, minY, maxY, fullHeight, false, true);
        }

        int oldHeight = claim.height();
        long oldArea = claim.area();
        long newArea = (long) (east + west + 1) * (south + north + 1);
        int newHeight = maxY - minY + 1;
        long costBlocks = expansionCostBlocks(oldArea, oldHeight, newArea, newHeight);
        double cost = costBlocks * group.expandPricePerBlock();
        return new ExpansionPreview(true, cost, targetDistance, expandAmount, east + west + 1, newHeight, south + north + 1, east, south, west, north, minY, maxY, fullHeight, false, false);
    }

    static long expansionCostBlocks(long oldArea, int oldHeight, long newArea, int newHeight) {
        long oldVolume = Math.max(0L, oldArea) * Math.max(0, oldHeight);
        long newVolume = Math.max(0L, newArea) * Math.max(0, newHeight);
        return Math.max(0L, newVolume - oldVolume);
    }

    private int clampExpandAmount(ClaimGroup group, Claim claim, ClaimDirection direction, int amount, int worldMinY, int worldMaxY) {
        int positiveAmount = Math.max(0, amount);
        if (!direction.vertical()) {
            return group.clampExpandAmount(claim.distance(direction), positiveAmount);
        }
        int remaining = direction == ClaimDirection.UP ? worldMaxY - claim.maxY() : claim.minY() - worldMinY;
        return Math.min(positiveAmount, Math.max(0, remaining));
    }

    private String displayName(OfflinePlayer player) {
        return player.getName() == null ? player.getUniqueId().toString() : player.getName();
    }

    public boolean canManageClaim(Player player, Claim claim) {
        return isOwner(player, claim) || AdminAccess.hasClaimManageAccess(player);
    }

    public boolean canManageMembers(Player player, Claim claim) {
        return isOwner(player, claim) || AdminAccess.hasMemberManageAccess(player);
    }

    public boolean canManagePermissions(Player player, Claim claim) {
        return isOwner(player, claim) || AdminAccess.hasPermissionManageAccess(player);
    }

    public boolean canManageFlags(Player player, Claim claim) {
        return isOwner(player, claim) || AdminAccess.hasFlagManageAccess(player);
    }

    private boolean isOwner(Player player, Claim claim) {
        return player != null && claim != null && claim.owner().equals(player.getUniqueId());
    }

    private boolean hasAdminForcePermission(Player player) {
        return AdminAccess.hasForceBypass(player);
    }

    public static String formatMoney(double value) {
        return MONEY.format(value);
    }

    public long cooldownRemainingSeconds(Claim claim) {
        return 0L;
    }

    public record ExpansionPreview(
        boolean allowed,
        double cost,
        int targetDistance,
        int expandAmount,
        int width,
        int height,
        int depth,
        int east,
        int south,
        int west,
        int north,
        int minY,
        int maxY,
        boolean fullHeight,
        boolean hitMax,
        boolean overlap
    ) {
        public String costText() {
            return allowed ? formatMoney(cost) : "--";
        }
    }
}
