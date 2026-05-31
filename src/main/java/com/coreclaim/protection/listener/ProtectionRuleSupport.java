package com.coreclaim.protection.listener;

import com.coreclaim.CoreClaimPlugin;
import com.coreclaim.item.ClaimCoreFactory;
import com.coreclaim.model.Claim;
import com.coreclaim.model.ClaimFlag;
import com.coreclaim.model.ClaimPermission;
import com.coreclaim.cleanup.ClaimCleanupService;
import com.coreclaim.service.ClaimService;
import com.coreclaim.service.ExplosionAuthorizationService;
import com.coreclaim.util.AdminAccess;
import java.util.Optional;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.data.Levelled;
import org.bukkit.entity.AreaEffectCloud;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
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
        return ProtectionMaterialRules.isProjectileSensitiveBlock(material);
    }

    public ClaimPermission requiredPermissionForBlockInteract(Material material, ItemStack item) {
        return requiredPermissionForBlockInteract(null, material, item);
    }

    public ClaimPermission requiredPermissionForBlockInteract(Block block, Material material, ItemStack item) {
        if (isTntIgnition(material, item)) {
            return ClaimPermission.EXPLOSION;
        }
        ClaimPermission toolChangePermission = requiredPermissionForBlockToolChange(material, item);
        if (toolChangePermission != null) {
            return toolChangePermission;
        }
        if (isSpecialExplosiveUse(block, material)) {
            return ClaimPermission.EXPLOSION;
        }
        if (isUtilityInteractBlock(material)) {
            return ClaimPermission.UTILITY_INTERACT;
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
        return ProtectionMaterialRules.requiredPermissionForBlockToolChange(material, item);
    }

    public boolean isComposterCompostInput(Block block, ItemStack item) {
        if (block == null || block.getType() != Material.COMPOSTER) {
            return false;
        }
        if (!(block.getBlockData() instanceof Levelled levelled)) {
            return false;
        }
        return isComposterCompostInput(block.getType(), levelled.getLevel() >= levelled.getMaximumLevel(), item);
    }

    public boolean isComposterCompostInput(Material material, boolean composterFull, ItemStack item) {
        return ProtectionMaterialRules.isComposterCompostInput(material, composterFull, item);
    }

    public boolean isCompostableMaterial(Material material) {
        return ProtectionMaterialRules.isCompostableMaterial(material);
    }

    public boolean isUtilityInteractBlock(Material material) {
        return ProtectionMaterialRules.isUtilityInteractBlock(material);
    }

    public boolean isCakeConsumption(Material material, ItemStack item) {
        return ProtectionMaterialRules.isCakeConsumption(material, item);
    }

    public boolean isAxeStrippingWood(Material material, ItemStack item) {
        return ProtectionMaterialRules.isAxeStrippingWood(material, item);
    }

    public boolean isBlockDrivenToolChange(Material material, ItemStack item) {
        return ProtectionMaterialRules.isBlockDrivenToolChange(material, item);
    }

    public boolean isTntIgnition(Material material, ItemStack item) {
        return ProtectionMaterialRules.isTntIgnition(material, item);
    }

    public Material strippedWoodMaterial(Material material) {
        return ProtectionMaterialRules.strippedWoodMaterial(material);
    }

    public ClaimPermission requiredPermissionForEntityInteract(Player player, Entity entity) {
        if (entity instanceof ArmorStand) {
            return ClaimPermission.BREAK;
        }
        if (isMobEntity(entity)) {
            return ClaimPermission.MOB_INTERACT;
        }
        if (entity instanceof InventoryHolder) {
            return ClaimPermission.INTERACT;
        }
        Material held = player.getInventory().getItemInMainHand().getType();
        return held == Material.NAME_TAG ? ClaimPermission.BREAK : ClaimPermission.INTERACT;
    }

    public ClaimPermission requiredPermissionForEntityDamage(Entity entity) {
        return isMobEntity(entity) ? ClaimPermission.MOB_INTERACT : ClaimPermission.BREAK;
    }

    public boolean isMobEntity(Entity entity) {
        return isMobEntityType(entity instanceof LivingEntity, entity instanceof Player, entity instanceof ArmorStand);
    }

    static boolean isMobEntityType(boolean livingEntity, boolean player, boolean armorStand) {
        return livingEntity && !player && !armorStand;
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

    public ClaimPermission projectileEntityPermission(Entity projectile, Entity target) {
        return isExplosionEntity(projectile) ? ClaimPermission.EXPLOSION : requiredPermissionForEntityDamage(target);
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
        return isSpecialExplosiveUse(
            true,
            block == null || block.getWorld() == null ? null : block.getWorld().getEnvironment(),
            material
        );
    }

    static boolean isSpecialExplosiveUse(boolean enabled, org.bukkit.World.Environment environment, Material material) {
        if (!enabled || material == null) {
            return false;
        }
        if (material == Material.RESPAWN_ANCHOR) {
            return environment != org.bukkit.World.Environment.NETHER;
        }
        if (material.name().endsWith("_BED")) {
            return environment != null && environment != org.bukkit.World.Environment.NORMAL;
        }
        return false;
    }

    public boolean isContainerMaterial(Material material) {
        return ClaimFlag.isContainerMaterial(material);
    }

    public boolean isRedstoneControl(Material material) {
        return ProtectionMaterialRules.isRedstoneControl(material);
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

}
