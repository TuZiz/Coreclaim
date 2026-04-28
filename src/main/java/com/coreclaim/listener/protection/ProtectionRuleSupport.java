package com.coreclaim.listener.protection;

import com.coreclaim.CoreClaimPlugin;
import com.coreclaim.item.ClaimCoreFactory;
import com.coreclaim.model.Claim;
import com.coreclaim.model.ClaimFlag;
import com.coreclaim.model.ClaimPermission;
import com.coreclaim.service.ClaimCleanupService;
import com.coreclaim.service.ClaimService;
import com.coreclaim.service.ExplosionAuthorizationService;
import com.coreclaim.util.AdminAccess;
import java.util.Optional;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.AreaEffectCloud;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.entity.Tameable;
import org.bukkit.entity.Vehicle;
import org.bukkit.event.Cancellable;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.projectiles.ProjectileSource;

public final class ProtectionRuleSupport {

    private final CoreClaimPlugin plugin;
    private final ClaimService claimService;
    private final ClaimCoreFactory claimCoreFactory;
    private final ExplosionAuthorizationService explosionAuthorizationService;
    private final ClaimCleanupService claimCleanupService;

    public ProtectionRuleSupport(
        CoreClaimPlugin plugin,
        ClaimService claimService,
        ClaimCoreFactory claimCoreFactory,
        ExplosionAuthorizationService explosionAuthorizationService,
        ClaimCleanupService claimCleanupService
    ) {
        this.plugin = plugin;
        this.claimService = claimService;
        this.claimCoreFactory = claimCoreFactory;
        this.explosionAuthorizationService = explosionAuthorizationService;
        this.claimCleanupService = claimCleanupService;
    }

    public CoreClaimPlugin plugin() {
        return plugin;
    }

    public ClaimService claimService() {
        return claimService;
    }

    public ClaimCoreFactory claimCoreFactory() {
        return claimCoreFactory;
    }

    public ExplosionAuthorizationService explosionAuthorizationService() {
        return explosionAuthorizationService;
    }

    public ClaimCleanupService claimCleanupService() {
        return claimCleanupService;
    }

    public boolean isBypassing(Player player) {
        return AdminAccess.hasForceBypass(player);
    }

    public int claimId(Optional<Claim> claim) {
        return claim.map(Claim::id).orElse(-1);
    }

    public boolean denyIfNeeded(Player player, Optional<Claim> claim, ClaimPermission permission, Cancellable cancellable) {
        if (claim.isEmpty() || isBypassing(player) || claimService.hasPermission(claim.get(), player.getUniqueId(), permission)) {
            return false;
        }
        cancellable.setCancelled(true);
        sendProtectionDeny(player, claim.get());
        return true;
    }

    public void recordBlockInteraction(Claim claim, Player player, ClaimPermission permission) {
        if (claim == null || player == null) {
            return;
        }
        if (permission == ClaimPermission.BREAK || permission == ClaimPermission.BUCKET || permission == ClaimPermission.PLACE) {
            claimCleanupService.recordBuildActivity(claim, player.getUniqueId());
            return;
        }
        claimCleanupService.recordInteractionActivity(claim, player.getUniqueId());
    }

    public void recordEntityInteraction(Claim claim, Player player, ClaimPermission permission) {
        if (claim == null || player == null) {
            return;
        }
        if (permission == ClaimPermission.BREAK) {
            claimCleanupService.recordBuildActivity(claim, player.getUniqueId());
            return;
        }
        claimCleanupService.recordInteractionActivity(claim, player.getUniqueId());
    }

    public void sendProtectionDeny(Player player, Claim claim) {
        player.sendMessage(plugin.message("protection-deny", "{owner}", claim.ownerName()));
    }

    public boolean isCoreBlock(Block block, Claim claim) {
        return block.getType() == plugin.settings().coreMaterial()
            && block.getX() == claim.centerX()
            && block.getY() == claim.centerY()
            && block.getZ() == claim.centerZ();
    }

    public Player resolvePlayer(Entity entity) {
        if (entity instanceof Player player) {
            return player;
        }
        if (entity instanceof AreaEffectCloud cloud) {
            ProjectileSource source = cloud.getSource();
            if (source instanceof Player player) {
                return player;
            }
            if (source instanceof Entity sourceEntity) {
                return resolveOwnedEntityPlayer(sourceEntity);
            }
        }
        if (entity instanceof Projectile projectile) {
            ProjectileSource source = projectile.getShooter();
            if (source instanceof Player player) {
                return player;
            }
            if (source instanceof Entity sourceEntity) {
                return resolveOwnedEntityPlayer(sourceEntity);
            }
        }
        if (entity instanceof TNTPrimed tnt && tnt.getSource() != null) {
            return resolveOwnedEntityPlayer(tnt.getSource());
        }
        return null;
    }

    public boolean isProjectileSensitiveBlock(Material material) {
        String name = material.name();
        return material == Material.BELL
            || name.endsWith("_BUTTON")
            || name.endsWith("_PRESSURE_PLATE")
            || name.endsWith("_DOOR")
            || name.endsWith("_TRAPDOOR")
            || name.endsWith("_FENCE_GATE")
            || material == Material.LEVER
            || material == Material.TARGET;
    }

    public ClaimPermission requiredPermissionForBlockInteract(Material material, ItemStack item) {
        return requiredPermissionForBlockInteract(null, material, item);
    }

    public ClaimPermission requiredPermissionForBlockInteract(Block block, Material material, ItemStack item) {
        ClaimPermission toolChangePermission = requiredPermissionForBlockToolChange(material, item);
        if (toolChangePermission != null) {
            return toolChangePermission;
        }
        if (isSpecialExplosiveUse(block, material)) {
            return ClaimPermission.EXPLOSION;
        }
        if (isRedstoneControl(material)) {
            return ClaimPermission.REDSTONE;
        }
        if (isConfiguredRedstoneInteract(material)) {
            return ClaimPermission.REDSTONE;
        }
        return ClaimPermission.INTERACT;
    }

    public ClaimPermission requiredPermissionForBlockToolChange(Material material, ItemStack item) {
        if (material == null) {
            return null;
        }
        if (isDirectRightClickStateBlock(material)) {
            return ClaimPermission.INTERACT;
        }
        if (item == null) {
            return null;
        }
        if (isAxe(item) && isStrippableWood(material)) {
            return ClaimPermission.INTERACT;
        }
        if (isAxe(item) && isWeatheredOrWaxedCopper(material)) {
            return ClaimPermission.BREAK;
        }
        if (isShovel(item) && isShovelFlattenable(material)) {
            return ClaimPermission.BREAK;
        }
        if (isHoe(item) && isHoeTillable(material)) {
            return ClaimPermission.INTERACT;
        }
        if (isHoneycomb(item) && isWaxableCopper(material)) {
            return ClaimPermission.INTERACT;
        }
        if (isBoneMeal(item) && isBoneMealTarget(material)) {
            return ClaimPermission.INTERACT;
        }
        return null;
    }

    public ClaimPermission requiredPermissionForEntityInteract(Player player, Entity entity) {
        if (entity instanceof ArmorStand) {
            return ClaimPermission.BREAK;
        }
        if (entity instanceof InventoryHolder) {
            return ClaimPermission.INTERACT;
        }
        Material held = player.getInventory().getItemInMainHand().getType();
        return held == Material.NAME_TAG ? ClaimPermission.BREAK : ClaimPermission.INTERACT;
    }

    public boolean isHazardousProjectile(Entity entity) {
        if (!(entity instanceof Projectile)) {
            return false;
        }
        String name = entity.getType().name();
        return name.contains("POTION")
            || name.contains("FIREBALL")
            || name.contains("WITHER_SKULL")
            || name.contains("WIND_CHARGE")
            || name.contains("SPIT")
            || name.equals("DRAGON_FIREBALL")
            || name.equals("SMALL_FIREBALL");
    }

    public boolean isExplosionEntity(Entity entity) {
        String name = entity.getType().name();
        return entity instanceof TNTPrimed
            || name.contains("FIREBALL")
            || name.contains("WITHER_SKULL")
            || name.contains("WIND_CHARGE")
            || name.equals("DRAGON_FIREBALL")
            || name.equals("SMALL_FIREBALL");
    }

    public ClaimPermission projectilePermission(Entity entity) {
        return isExplosionEntity(entity) ? ClaimPermission.EXPLOSION : ClaimPermission.BREAK;
    }

    public Player resolveOwnedEntityPlayer(Entity entity) {
        if (entity instanceof Player player) {
            return player;
        }
        if (entity instanceof Tameable tameable && tameable.getOwner() instanceof Player player) {
            return player;
        }
        for (Entity passenger : entity.getPassengers()) {
            Player resolved = resolveOwnedEntityPlayer(passenger);
            if (resolved != null) {
                return resolved;
            }
        }
        return null;
    }

    public Player findAuthorizedPassenger(Entity vehicle, Claim claim) {
        for (Entity passenger : vehicle.getPassengers()) {
            Player resolved = resolveOwnedEntityPlayer(passenger);
            if (resolved != null && (isBypassing(resolved) || claimService.hasPermission(claim, resolved.getUniqueId(), ClaimPermission.TELEPORT))) {
                return resolved;
            }
        }
        return null;
    }

    public Player findNotifiablePassenger(Entity vehicle) {
        for (Entity passenger : vehicle.getPassengers()) {
            Player resolved = resolveOwnedEntityPlayer(passenger);
            if (resolved != null) {
                return resolved;
            }
        }
        return null;
    }

    public boolean sameBlock(Location from, Location to) {
        return from.getWorld() == to.getWorld()
            && from.getBlockX() == to.getBlockX()
            && from.getBlockY() == to.getBlockY()
            && from.getBlockZ() == to.getBlockZ();
    }

    public boolean isSpecialExplosiveUse(Block block, Material material) {
        if (plugin == null || !plugin.settings().blockSpecialExplosiveUse() || material == null) {
            return false;
        }
        if (block == null || block.getWorld() == null) {
            return material == Material.RESPAWN_ANCHOR;
        }
        String name = material.name();
        if (name.endsWith("_BED")) {
            return block.getWorld().getEnvironment() != org.bukkit.World.Environment.NORMAL;
        }
        if (material == Material.RESPAWN_ANCHOR) {
            return block.getWorld().getEnvironment() != org.bukkit.World.Environment.NETHER;
        }
        return false;
    }

    public boolean isContainerMaterial(Material material) {
        return ClaimFlag.isContainerMaterial(material);
    }

    public boolean isRedstoneControl(Material material) {
        if (material == null) {
            return false;
        }
        String name = material.name();
        return material == Material.LEVER
            || name.endsWith("_BUTTON")
            || name.endsWith("_PRESSURE_PLATE");
    }

    public ClaimPermission projectileSensitivePermission(Material material) {
        return isRedstoneControl(material) || isConfiguredRedstoneInteract(material)
            ? ClaimPermission.REDSTONE
            : ClaimPermission.INTERACT;
    }

    private boolean isConfiguredRedstoneInteract(Material material) {
        if (plugin == null || material == null) {
            return false;
        }
        String name = material.name();
        if (name.endsWith("_DOOR") || name.endsWith("_TRAPDOOR") || name.endsWith("_FENCE_GATE") || name.endsWith("_BED")) {
            return false;
        }
        return plugin.settings().isAlwaysProtectedInteract(material);
    }

    public boolean isCoreItem(ItemStack item) {
        return claimCoreFactory.isAnyClaimCore(item);
    }

    private boolean isAxe(ItemStack item) {
        return item != null && item.getType().name().endsWith("_AXE");
    }

    private boolean isShovel(ItemStack item) {
        return item != null && item.getType().name().endsWith("_SHOVEL");
    }

    private boolean isHoe(ItemStack item) {
        return item != null && item.getType().name().endsWith("_HOE");
    }

    private boolean isHoneycomb(ItemStack item) {
        return item != null && item.getType() == Material.HONEYCOMB;
    }

    private boolean isBoneMeal(ItemStack item) {
        return item != null && item.getType() == Material.BONE_MEAL;
    }

    private boolean isDirectRightClickStateBlock(Material material) {
        String name = material.name();
        return material == Material.CAKE
            || material == Material.CANDLE
            || name.endsWith("_CANDLE")
            || name.equals("CANDLE_CAKE")
            || name.endsWith("_CANDLE_CAKE")
            || material == Material.CAMPFIRE
            || material == Material.SOUL_CAMPFIRE
            || material == Material.COMPOSTER
            || name.endsWith("CAULDRON")
            || material == Material.BEEHIVE
            || material == Material.BEE_NEST;
    }

    private boolean isStrippableWood(Material material) {
        String name = material.name();
        if (name.startsWith("STRIPPED_")) {
            return false;
        }
        return name.endsWith("_LOG")
            || name.endsWith("_WOOD")
            || name.endsWith("_STEM")
            || name.endsWith("_HYPHAE")
            || name.equals("BAMBOO_BLOCK");
    }

    private boolean isWeatheredOrWaxedCopper(Material material) {
        String name = material.name();
        return name.contains("COPPER")
            && (name.startsWith("EXPOSED_")
            || name.startsWith("WEATHERED_")
            || name.startsWith("OXIDIZED_")
            || name.startsWith("WAXED_"));
    }

    private boolean isWaxableCopper(Material material) {
        String name = material.name();
        if (!name.contains("COPPER") || name.startsWith("WAXED_")) {
            return false;
        }
        if (name.contains("ORE") || name.startsWith("RAW_")) {
            return false;
        }
        return name.equals("COPPER_BLOCK")
            || name.endsWith("_COPPER")
            || name.contains("CUT_COPPER")
            || name.contains("COPPER_");
    }

    private boolean isShovelFlattenable(Material material) {
        return switch (material.name()) {
            case "GRASS_BLOCK", "DIRT", "PODZOL", "COARSE_DIRT", "MYCELIUM", "ROOTED_DIRT" -> true;
            default -> false;
        };
    }

    private boolean isHoeTillable(Material material) {
        return switch (material.name()) {
            case "GRASS_BLOCK", "DIRT", "DIRT_PATH", "COARSE_DIRT", "ROOTED_DIRT" -> true;
            default -> false;
        };
    }

    private boolean isBoneMealTarget(Material material) {
        String name = material.name();
        return switch (name) {
            case "GRASS_BLOCK", "MOSS_BLOCK", "PALE_MOSS_BLOCK", "MANGROVE_PROPAGULE",
                "WHEAT", "CARROTS", "POTATOES", "BEETROOTS", "NETHER_WART", "COCOA",
                "SWEET_BERRY_BUSH", "CAVE_VINES", "CAVE_VINES_PLANT", "KELP", "KELP_PLANT",
                "SEAGRASS", "SEA_PICKLE", "BAMBOO", "BAMBOO_SAPLING", "SUGAR_CANE",
                "CACTUS", "PITCHER_CROP", "TORCHFLOWER_CROP" -> true;
            default -> name.endsWith("_SAPLING") || name.endsWith("_NYLIUM");
        };
    }
}
