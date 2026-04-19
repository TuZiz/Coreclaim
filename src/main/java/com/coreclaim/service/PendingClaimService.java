package com.coreclaim.service;

import com.coreclaim.CoreClaimPlugin;
import com.coreclaim.config.ClaimGroup;
import com.coreclaim.economy.EconomyHook;
import com.coreclaim.item.ClaimCoreFactory;
import com.coreclaim.model.Claim;
import com.coreclaim.model.PlayerProfile;
import java.util.logging.Level;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;

public final class PendingClaimService {

    private final CoreClaimPlugin plugin;
    private final ClaimService claimService;
    private final ProfileService profileService;
    private final ClaimCoreFactory claimCoreFactory;
    private final HologramService hologramService;
    private final ClaimVisualService claimVisualService;
    private final EconomyHook economyHook;
    private final OnlineRewardService onlineRewardService;
    private final Map<UUID, PendingClaim> pendingClaims = new ConcurrentHashMap<>();
    private final Map<UUID, com.coreclaim.platform.PlatformScheduler.TaskHandle> timeoutTasks = new ConcurrentHashMap<>();

    public PendingClaimService(
        CoreClaimPlugin plugin,
        ClaimService claimService,
        ProfileService profileService,
        ClaimCoreFactory claimCoreFactory,
        HologramService hologramService,
        ClaimVisualService claimVisualService,
        EconomyHook economyHook,
        OnlineRewardService onlineRewardService
    ) {
        this.plugin = plugin;
        this.claimService = claimService;
        this.profileService = profileService;
        this.claimCoreFactory = claimCoreFactory;
        this.hologramService = hologramService;
        this.claimVisualService = claimVisualService;
        this.economyHook = economyHook;
        this.onlineRewardService = onlineRewardService;
    }

    public boolean beginClaimCreation(Player player, Location coreLocation, boolean starterCore) {
        ValidationResult validation = validateCreation(player, coreLocation, starterCore);
        if (!validation.allowed()) {
            player.sendMessage(validation.message());
            return false;
        }

        if (plugin.settings().warnOnSecondClaim() && validation.claimCount() == 1) {
            player.sendMessage(plugin.message("second-claim-warning"));
        }

        cancelPendingClaim(player, false);
        pendingClaims.put(player.getUniqueId(), new PendingClaim(player.getUniqueId(), coreLocation, starterCore));
        scheduleTimeout(player.getUniqueId());
        hologramService.spawnPendingHologram(player.getUniqueId(), player.getName(), coreLocation);
        claimVisualService.showPendingLocation(player, coreLocation);
        player.sendMessage(plugin.message("claim-name-prompt", "{seconds}", String.valueOf(plugin.settings().chatInputTimeoutSeconds())));
        return true;
    }

    public boolean hasPendingClaim(UUID playerId) {
        return pendingClaims.containsKey(playerId);
    }

    public Claim completeClaim(Player player, String inputName) {
        PendingClaim pending = pendingClaims.remove(player.getUniqueId());
        cancelTimeout(player.getUniqueId());
        if (pending == null) {
            return null;
        }
        hologramService.removePendingHologram(player.getUniqueId());

        String name = inputName == null ? "" : inputName.trim();
        if (name.isEmpty()) {
            refundCore(pending);
            player.sendMessage(plugin.message("claim-name-empty"));
            return null;
        }
        if (name.length() > plugin.settings().claimNameMaxLength()) {
            refundCore(pending);
            player.sendMessage(plugin.message("claim-name-too-long", "{max}", String.valueOf(plugin.settings().claimNameMaxLength())));
            return null;
        }
        if (claimService.isClaimNameTaken(name)) {
            refundCore(pending);
            player.sendMessage(plugin.message("claim-name-exists", "{name}", name));
            return null;
        }

        Location coreLocation = pending.coreLocation();
        ValidationResult validation = validateCreation(player, coreLocation, pending.starterCore());
        if (!validation.allowed()) {
            refundCore(pending);
            player.sendMessage(validation.message());
            return null;
        }
        ClaimGroup group = validation.group();
        double createCost = pending.starterCore() ? 0D : claimArea(group.initialDistance()) * group.coreCreatePricePerBlock();
        if (!coreLocation.getBlock().getType().isAir()) {
            refundCore(pending);
            player.sendMessage(plugin.message("claim-core-blocked"));
            return null;
        }
        if (createCost > 0D) {
            if (!economyHook.available()) {
                refundCore(pending);
                player.sendMessage(plugin.message("economy-missing"));
                return null;
            }
            if (!economyHook.has(player, createCost)) {
                refundCore(pending);
                player.sendMessage(plugin.message("economy-not-enough", "{cost}", ClaimActionService.formatMoney(createCost)));
                return null;
            }
            if (!economyHook.withdraw(player, createCost)) {
                refundCore(pending);
                player.sendMessage(plugin.message("economy-missing"));
                return null;
            }
        }
        boolean firstOrdinaryClaim = validation.claimCount() == 0;
        Claim claim = null;
        coreLocation.getBlock().setType(plugin.settings().coreMaterial(), false);
        try {
            claim = claimService.createClaim(player.getUniqueId(), player.getName(), name, coreLocation, group.initialDistance());
            hologramService.spawnClaimHologram(claim);
            claimVisualService.showClaim(player, claim);
            if (pending.starterCore()) {
                PlayerProfile profile = profileService.getOrCreate(player.getUniqueId(), player.getName());
                if (!profile.starterCoreUsed()) {
                    profile.setStarterCoreUsed(true);
                    profileService.saveProfile(profile);
                }
            }
            onlineRewardService.markOrdinaryClaimCreated(player);
            player.sendMessage(plugin.message(
                "claim-name-created",
                "{name}", claim.name(),
                "{width}", String.valueOf(claim.width()),
                "{depth}", String.valueOf(claim.depth()),
                "{cost}", ClaimActionService.formatMoney(createCost)
            ));
            if (firstOrdinaryClaim) {
                player.sendMessage(chatMessage(
                    "second-claim-selection-tip",
                    "&6&l提示: &7第二块领地开始，直接拿普通金锄头左键点 1、右键点 2，再输入 &e/claim create <名字> &7即可。"
                ));
            }
            return claim;
        } catch (RuntimeException exception) {
            rollbackFailedClaimCreation(claim, coreLocation, pending, player, createCost);
            if (exception instanceof IllegalArgumentException illegalArgumentException
                && "claim-name-exists".equals(illegalArgumentException.getMessage())) {
                player.sendMessage(plugin.message("claim-name-exists", "{name}", name));
            } else {
                plugin.getLogger().log(Level.WARNING, "Failed to complete pending claim creation for " + player.getName(), exception);
                player.sendMessage(plugin.message("claim-create-failed"));
            }
            return null;
        }
    }

    private void rollbackFailedClaimCreation(Claim claim, Location coreLocation, PendingClaim pending, Player player, double createCost) {
        if (claim != null) {
            claimService.removeClaim(claim);
        } else {
            clearPlacedCoreBlock(coreLocation);
        }
        if (createCost > 0D && economyHook.available()) {
            economyHook.deposit(player, createCost);
        }
        refundCore(pending);
    }

    private void clearPlacedCoreBlock(Location coreLocation) {
        if (coreLocation == null || coreLocation.getWorld() == null) {
            return;
        }
        if (coreLocation.getBlock().getType() == plugin.settings().coreMaterial()) {
            coreLocation.getBlock().setType(Material.AIR, false);
        }
    }

    public void cancelPendingClaim(Player player, boolean notify) {
        PendingClaim pending = pendingClaims.remove(player.getUniqueId());
        cancelTimeout(player.getUniqueId());
        if (pending == null) {
            return;
        }
        hologramService.removePendingHologram(player.getUniqueId());
        refundCore(pending);
        if (notify) {
            player.sendMessage(plugin.message("claim-name-cancelled"));
        }
    }

    public void timeoutPendingClaim(UUID playerId) {
        PendingClaim pending = pendingClaims.remove(playerId);
        cancelTimeout(playerId);
        if (pending == null) {
            return;
        }
        hologramService.removePendingHologram(playerId);
        refundCore(pending);
        Player player = plugin.getServer().getPlayer(playerId);
        if (player != null) {
            player.sendMessage(plugin.message("claim-name-timeout"));
        }
    }

    private void scheduleTimeout(UUID playerId) {
        cancelTimeout(playerId);
        long delayTicks = plugin.settings().chatInputTimeoutSeconds() * 20L;
        com.coreclaim.platform.PlatformScheduler.TaskHandle handle =
            plugin.platformScheduler().runLater(() -> timeoutPendingClaim(playerId), delayTicks);
        timeoutTasks.put(playerId, handle);
    }

    private void cancelTimeout(UUID playerId) {
        com.coreclaim.platform.PlatformScheduler.TaskHandle handle = timeoutTasks.remove(playerId);
        if (handle != null) {
            handle.cancel();
        }
    }

    private ValidationResult validateCreation(Player player, Location coreLocation, boolean starterCore) {
        if (player == null || coreLocation == null || coreLocation.getWorld() == null) {
            return ValidationResult.denied(plugin.message("world-missing"));
        }
        PlayerProfile profile = profileService.getOrCreate(player.getUniqueId(), player.getName());
        int claimCount = claimService.countClaims(player.getUniqueId());
        if (starterCore && claimCount > 0) {
            return ValidationResult.denied(plugin.message("starter-core-first-only"));
        }
        if (starterCore && profile.starterCoreUsed()) {
            return ValidationResult.denied(chatMessage(
                "starter-core-already-used",
                "&c&l! &7你已经成功使用过一次新人核心了，后续请直接用普通金锄头选区创建领地。"
            ));
        }
        World world = coreLocation.getWorld();
        if (!plugin.settings().isClaimWorld(world.getName())) {
            return ValidationResult.denied(plugin.message("claim-world-only", "{world}", plugin.settings().claimWorldsDisplay()));
        }
        ClaimGroup group = plugin.groups().resolve(player);
        int maxClaims = group.maxClaims();
        if (claimCount >= maxClaims) {
            return ValidationResult.denied(plugin.message("claim-no-slot"));
        }

        int initialDistance = group.initialDistance();
        int minX = coreLocation.getBlockX() - initialDistance;
        int maxX = coreLocation.getBlockX() + initialDistance;
        int minZ = coreLocation.getBlockZ() - initialDistance;
        int maxZ = coreLocation.getBlockZ() + initialDistance;
        if (claimService.overlaps(world.getName(), minX, maxX, world.getMinHeight(), world.getMaxHeight() - 1, minZ, maxZ, null, true)) {
            return ValidationResult.denied(plugin.message("claim-overlap"));
        }
        if (claimService.hasCoreWithinSpacing(
            world.getName(),
            coreLocation.getBlockX(),
            coreLocation.getBlockZ(),
            plugin.settings().minimumCoreSpacing(),
            null
        )) {
            return ValidationResult.denied(plugin.message("claim-core-too-close"));
        }
        return ValidationResult.allowed(group, claimCount);
    }

    private void refundCore(PendingClaim pending) {
        Location location = pending.coreLocation();
        Player player = plugin.getServer().getPlayer(pending.ownerId());
        if (player != null) {
            if (pending.starterCore()) {
                claimCoreFactory.giveStarterCore(player, 1);
            } else {
                claimCoreFactory.giveClaimCore(player, 1);
            }
        } else if (location.getWorld() != null) {
            location.getWorld().dropItemNaturally(
                location.clone().add(0.5D, 0.5D, 0.5D),
                pending.starterCore() ? claimCoreFactory.createStarterCore(1) : claimCoreFactory.createClaimCore(1)
            );
        }
    }

    public record PendingClaim(UUID ownerId, Location coreLocation, boolean starterCore) {
    }

    private record ValidationResult(boolean allowed, String message, ClaimGroup group, int claimCount) {
        private static ValidationResult denied(String message) {
            return new ValidationResult(false, message, null, 0);
        }

        private static ValidationResult allowed(ClaimGroup group, int claimCount) {
            return new ValidationResult(true, "", group, claimCount);
        }
    }

    private long claimArea(int initialDistance) {
        int edge = initialDistance * 2 + 1;
        return (long) edge * edge;
    }

    private String chatMessage(String path, String fallback, String... replacements) {
        String prefix = plugin.messagesConfig().getString("prefix", "&8[&6Claim&8] &f");
        String body = plugin.messagesConfig().contains(path) ? plugin.messagesConfig().getString(path, fallback) : fallback;
        String message = plugin.color(prefix + body);
        for (int index = 0; index + 1 < replacements.length; index += 2) {
            message = message.replace(replacements[index], replacements[index + 1]);
        }
        return message;
    }
}
