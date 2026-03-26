package com.coreclaim.service;

import com.coreclaim.CoreClaimPlugin;
import com.coreclaim.config.ClaimGroup;
import com.coreclaim.economy.EconomyHook;
import com.coreclaim.model.Claim;
import com.coreclaim.model.ClaimDirection;
import java.text.DecimalFormat;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.World;
import org.bukkit.entity.Player;

public final class ClaimActionService {

    private static final DecimalFormat MONEY = new DecimalFormat("0.##");

    private final CoreClaimPlugin plugin;
    private final ClaimService claimService;
    private final HologramService hologramService;
    private final ClaimVisualService claimVisualService;
    private final EconomyHook economyHook;

    public ClaimActionService(
        CoreClaimPlugin plugin,
        ClaimService claimService,
        HologramService hologramService,
        ClaimVisualService claimVisualService,
        EconomyHook economyHook
    ) {
        this.plugin = plugin;
        this.claimService = claimService;
        this.hologramService = hologramService;
        this.claimVisualService = claimVisualService;
        this.economyHook = economyHook;
    }

    public Claim findOwnedClaim(Player player) {
        return claimService.findClaim(player.getLocation())
            .filter(claim -> claim.owner().equals(player.getUniqueId()))
            .orElse(null);
    }

    public ExpansionPreview previewExpansion(Player player, Claim claim, ClaimDirection direction) {
        return buildExpansionPreview(player, claim, direction, plugin.settings().directionExpandAmount());
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
        if (!claim.owner().equals(player.getUniqueId())) {
            player.sendMessage(plugin.message("trust-no-permission"));
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

        claimService.updateBounds(claim, preview.east(), preview.south(), preview.west(), preview.north());
        player.sendMessage(plugin.message(
            "claim-expand-success",
            "{direction}", direction.displayName(),
            "{amount}", String.valueOf(amount),
            "{cost}", MONEY.format(preview.cost()),
            "{width}", String.valueOf(preview.width()),
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
        if (!claim.owner().equals(player.getUniqueId())) {
            player.sendMessage(plugin.message("trust-no-permission"));
            return false;
        }
        hologramService.removeClaimHologram(claim.id());
        claimService.removeClaim(claim);
        player.sendMessage(plugin.message("claim-removed"));
        return true;
    }

    public boolean hideClaimCore(Player player, Claim claim) {
        if (!claim.owner().equals(player.getUniqueId()) && !player.hasPermission("coreclaim.admin")) {
            player.sendMessage(plugin.message("trust-no-permission"));
            return false;
        }
        if (!claim.coreVisible()) {
            player.sendMessage(plugin.message("claim-core-already-hidden"));
            return false;
        }
        hologramService.removeClaimHologram(claim.id());
        claimService.updateCoreVisibility(claim, false);
        World world = Bukkit.getWorld(claim.world());
        if (world != null) {
            Location coreLocation = new Location(world, claim.centerX(), claim.centerY(), claim.centerZ());
            if (coreLocation.getBlock().getType() == plugin.settings().coreMaterial()) {
                coreLocation.getBlock().setType(org.bukkit.Material.AIR, false);
            }
        }
        player.sendMessage(plugin.message("claim-core-hidden", "{name}", claim.name()));
        return true;
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
        if (!claim.owner().equals(player.getUniqueId())) {
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
        if (!claimService.addTrustedMember(claim, target.getUniqueId())) {
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
        if (!claim.owner().equals(player.getUniqueId())) {
            player.sendMessage(plugin.message("trust-no-permission"));
            return false;
        }
        if (target == null || target.getUniqueId() == null) {
            player.sendMessage(plugin.message("trust-no-target"));
            return false;
        }
        if (!claimService.removeTrustedMember(claim, target.getUniqueId())) {
            player.sendMessage(plugin.message("trust-missing", "{player}", displayName(target)));
            return false;
        }
        player.sendMessage(plugin.message("trust-removed", "{player}", displayName(target), "{name}", claim.name()));
        return true;
    }

    public boolean teleportToClaim(Player player, Claim claim) {
        if (!claimService.canAccess(claim, player.getUniqueId())) {
            player.sendMessage(plugin.message("trust-no-permission"));
            return false;
        }
        World world = Bukkit.getWorld(claim.world());
        if (world == null) {
            player.sendMessage(plugin.message("world-missing"));
            return false;
        }
        Location destination = new Location(world, claim.centerX() + 0.5D, claim.centerY() + 1D, claim.centerZ() + 0.5D);
        player.teleport(destination);
        claimVisualService.showClaim(player, claim);
        player.sendMessage(plugin.message("claim-teleported", "{name}", claim.name()));
        return true;
    }

    private ExpansionPreview buildExpansionPreview(Player player, Claim claim, ClaimDirection direction, int amount) {
        ClaimGroup group = plugin.groups().resolve(player);
        int east = claim.east();
        int south = claim.south();
        int west = claim.west();
        int north = claim.north();

        switch (direction) {
            case EAST -> east += amount;
            case SOUTH -> south += amount;
            case WEST -> west += amount;
            case NORTH -> north += amount;
        }

        int targetDistance = switch (direction) {
            case EAST -> east;
            case SOUTH -> south;
            case WEST -> west;
            case NORTH -> north;
        };
        if (targetDistance > group.maxDistance()) {
            return new ExpansionPreview(false, 0D, claim.distance(direction), claim.width(), claim.depth(), east, south, west, north, true, false);
        }

        int minX = claim.centerX() - west;
        int maxX = claim.centerX() + east;
        int minZ = claim.centerZ() - north;
        int maxZ = claim.centerZ() + south;
        if (claimService.overlaps(claim.world(), minX, maxX, minZ, maxZ, claim.id())) {
            return new ExpansionPreview(false, 0D, claim.distance(direction), claim.width(), claim.depth(), east, south, west, north, false, true);
        }

        long oldArea = claim.area();
        long newArea = (long) (east + west + 1) * (south + north + 1);
        double cost = (newArea - oldArea) * group.expandPricePerBlock();
        return new ExpansionPreview(true, cost, targetDistance, east + west + 1, south + north + 1, east, south, west, north, false, false);
    }

    private String displayName(OfflinePlayer player) {
        return player.getName() == null ? player.getUniqueId().toString() : player.getName();
    }

    public static String formatMoney(double value) {
        return MONEY.format(value);
    }

    public record ExpansionPreview(
        boolean allowed,
        double cost,
        int targetDistance,
        int width,
        int depth,
        int east,
        int south,
        int west,
        int north,
        boolean hitMax,
        boolean overlap
    ) {
        public String costText() {
            return allowed ? formatMoney(cost) : "--";
        }
    }
}
