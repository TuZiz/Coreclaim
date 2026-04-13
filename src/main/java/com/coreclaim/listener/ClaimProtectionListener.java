package com.coreclaim.listener;

import com.coreclaim.CoreClaimPlugin;
import com.coreclaim.item.ClaimCoreFactory;
import com.coreclaim.model.Claim;
import com.coreclaim.model.ClaimPermission;
import com.coreclaim.service.ClaimService;
import com.coreclaim.service.ExplosionAuthorizationService;
import java.util.Iterator;
import java.util.Optional;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.AreaEffectCloud;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Minecart;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.entity.Tameable;
import org.bukkit.event.Cancellable;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.AreaEffectCloudApplyEvent;
import org.bukkit.event.entity.EntityCombustByEntityEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.EntityMountEvent;
import org.bukkit.event.entity.ExplosionPrimeEvent;
import org.bukkit.event.entity.LingeringPotionSplashEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.entity.PlayerLeashEntityEvent;
import org.bukkit.event.entity.PotionSplashEvent;
import org.bukkit.event.hanging.HangingBreakByEntityEvent;
import org.bukkit.event.hanging.HangingPlaceEvent;
import org.bukkit.event.player.PlayerArmorStandManipulateEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerPortalEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.player.PlayerUnleashEntityEvent;
import org.bukkit.event.vehicle.VehicleDamageEvent;
import org.bukkit.event.vehicle.VehicleDestroyEvent;
import org.bukkit.event.vehicle.VehicleEnterEvent;
import org.bukkit.event.vehicle.VehicleMoveEvent;
import org.bukkit.entity.Vehicle;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.projectiles.ProjectileSource;

public final class ClaimProtectionListener implements Listener {

    private final CoreClaimPlugin plugin;
    private final ClaimService claimService;
    private final ClaimCoreFactory claimCoreFactory;
    private final ExplosionAuthorizationService explosionAuthorizationService;

    public ClaimProtectionListener(
        CoreClaimPlugin plugin,
        ClaimService claimService,
        ClaimCoreFactory claimCoreFactory,
        ExplosionAuthorizationService explosionAuthorizationService
    ) {
        this.plugin = plugin;
        this.claimService = claimService;
        this.claimCoreFactory = claimCoreFactory;
        this.explosionAuthorizationService = explosionAuthorizationService;
    }

    @EventHandler(ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        Optional<Claim> claim = claimService.findClaim(event.getBlock().getLocation());
        if (claim.isEmpty()) {
            return;
        }

        Player player = event.getPlayer();
        if (isBypassing(player)) {
            return;
        }
        if (isCoreBlock(event.getBlock(), claim.get()) && !claim.get().owner().equals(player.getUniqueId())) {
            event.setCancelled(true);
            player.sendMessage(plugin.message("trust-no-permission"));
            return;
        }
        if (!claimService.hasPermission(claim.get(), player.getUniqueId(), ClaimPermission.BREAK)) {
            event.setCancelled(true);
            sendProtectionDeny(player, claim.get());
            return;
        }

        if (isCoreBlock(event.getBlock(), claim.get())) {
            return;
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        ItemStack item = event.getItemInHand();
        if (claimCoreFactory.isAnyClaimCore(item)) {
            return;
        }

        Optional<Claim> claim = claimService.findClaim(event.getBlockPlaced().getLocation());
        if (claim.isPresent() && !isBypassing(event.getPlayer()) && !claimService.hasPermission(claim.get(), event.getPlayer().getUniqueId(), ClaimPermission.PLACE)) {
            event.setCancelled(true);
            sendProtectionDeny(event.getPlayer(), claim.get());
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getClickedBlock() == null) {
            return;
        }
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK && event.getAction() != Action.PHYSICAL) {
            return;
        }

        Optional<Claim> claim = claimService.findClaim(event.getClickedBlock().getLocation());
        if (claim.isPresent() && isCoreBlock(event.getClickedBlock(), claim.get())) {
            return;
        }
        Material clickedType = event.getClickedBlock().getType();
        ClaimPermission requiredPermission = requiredPermissionForBlockInteract(clickedType, event.getItem());
        boolean allowListed = plugin.settings().isAllowedInteract(clickedType)
            && !(plugin.settings().strictRedstoneInteract() && plugin.settings().isAlwaysProtectedInteract(clickedType));
        if (claim.isPresent() && allowListed) {
            return;
        }
        if (claim.isPresent() && requiredPermission == ClaimPermission.EXPLOSION && !isBypassing(event.getPlayer())
            && claimService.hasPermission(claim.get(), event.getPlayer().getUniqueId(), ClaimPermission.EXPLOSION)) {
            explosionAuthorizationService.authorize(event.getClickedBlock().getLocation());
        }
        if (claim.isPresent() && !isBypassing(event.getPlayer()) && !claimService.hasPermission(claim.get(), event.getPlayer().getUniqueId(), requiredPermission)) {
            event.setCancelled(true);
            sendProtectionDeny(event.getPlayer(), claim.get());
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onBucketEmpty(PlayerBucketEmptyEvent event) {
        Location target = event.getBlockClicked().getRelative(event.getBlockFace()).getLocation();
        Optional<Claim> claim = claimService.findClaim(target);
        if (claim.isPresent() && !isBypassing(event.getPlayer()) && !claimService.hasPermission(claim.get(), event.getPlayer().getUniqueId(), ClaimPermission.BUCKET)) {
            event.setCancelled(true);
            sendProtectionDeny(event.getPlayer(), claim.get());
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onBucketFill(PlayerBucketFillEvent event) {
        Optional<Claim> claim = claimService.findClaim(event.getBlockClicked().getLocation());
        if (claim.isPresent() && !isBypassing(event.getPlayer()) && !claimService.hasPermission(claim.get(), event.getPlayer().getUniqueId(), ClaimPermission.BUCKET)) {
            event.setCancelled(true);
            sendProtectionDeny(event.getPlayer(), claim.get());
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onEntityInteract(PlayerInteractEntityEvent event) {
        Optional<Claim> claim = claimService.findClaim(event.getRightClicked().getLocation());
        denyIfNeeded(event.getPlayer(), claim, requiredPermissionForEntityInteract(event.getPlayer(), event.getRightClicked()), event);
    }

    @EventHandler(ignoreCancelled = true)
    public void onEntityInteractAt(PlayerInteractAtEntityEvent event) {
        Optional<Claim> claim = claimService.findClaim(event.getRightClicked().getLocation());
        denyIfNeeded(event.getPlayer(), claim, requiredPermissionForEntityInteract(event.getPlayer(), event.getRightClicked()), event);
    }

    @EventHandler(ignoreCancelled = true)
    public void onArmorStandManipulate(PlayerArmorStandManipulateEvent event) {
        Optional<Claim> claim = claimService.findClaim(event.getRightClicked().getLocation());
        denyIfNeeded(event.getPlayer(), claim, ClaimPermission.BREAK, event);
    }

    @EventHandler(ignoreCancelled = true)
    public void onLeash(PlayerLeashEntityEvent event) {
        Optional<Claim> claim = claimService.findClaim(event.getEntity().getLocation());
        denyIfNeeded(event.getPlayer(), claim, ClaimPermission.INTERACT, event);
    }

    @EventHandler(ignoreCancelled = true)
    public void onUnleash(PlayerUnleashEntityEvent event) {
        Optional<Claim> claim = claimService.findClaim(event.getEntity().getLocation());
        denyIfNeeded(event.getPlayer(), claim, ClaimPermission.INTERACT, event);
    }

    @EventHandler(ignoreCancelled = true)
    public void onFish(PlayerFishEvent event) {
        Player player = event.getPlayer();
        if (isBypassing(player) || plugin.settings().allowFishingHookInteract()) {
            return;
        }
        Entity caught = event.getCaught();
        if (caught == null) {
            return;
        }
        Optional<Claim> claim = claimService.findClaim(caught.getLocation());
        ClaimPermission permission = requiredPermissionForEntityInteract(player, caught);
        if (claim.isPresent() && !claimService.hasPermission(claim.get(), player.getUniqueId(), permission)) {
            event.setCancelled(true);
            if (event.getHook() != null) {
                event.getHook().remove();
            }
            sendProtectionDeny(player, claim.get());
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onEntityMount(EntityMountEvent event) {
        Player rider = resolveOwnedEntityPlayer(event.getEntity());
        if (rider == null || isBypassing(rider) || event.getMount() instanceof Vehicle) {
            return;
        }
        Optional<Claim> claim = claimService.findClaim(event.getMount().getLocation());
        denyIfNeeded(rider, claim, ClaimPermission.INTERACT, event);
    }

    @EventHandler(ignoreCancelled = true)
    public void onMountedMove(PlayerMoveEvent event) {
        if (event.getTo() == null || sameBlock(event.getFrom(), event.getTo()) || isBypassing(event.getPlayer())) {
            return;
        }
        Entity mount = event.getPlayer().getVehicle();
        if (mount == null || mount instanceof Vehicle) {
            return;
        }
        Optional<Claim> fromClaim = claimService.findClaim(event.getFrom());
        Optional<Claim> toClaim = claimService.findClaim(event.getTo());
        if (claimId(fromClaim) == claimId(toClaim) || toClaim.isEmpty()) {
            return;
        }
        if (claimService.hasPermission(toClaim.get(), event.getPlayer().getUniqueId(), ClaimPermission.TELEPORT)) {
            return;
        }
        event.setTo(event.getFrom());
        sendProtectionDeny(event.getPlayer(), toClaim.get());
    }

    @EventHandler(ignoreCancelled = true)
    public void onPearlTeleport(PlayerTeleportEvent event) {
        if (event.getCause() != PlayerTeleportEvent.TeleportCause.ENDER_PEARL
            || event.getTo() == null
            || isBypassing(event.getPlayer())
            || plugin.settings().allowEnderPearlEntry()) {
            return;
        }
        Optional<Claim> claim = claimService.findClaim(event.getTo());
        if (claim.isPresent() && !claimService.hasPermission(claim.get(), event.getPlayer().getUniqueId(), ClaimPermission.TELEPORT)) {
            event.setCancelled(true);
            sendProtectionDeny(event.getPlayer(), claim.get());
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onChorusTeleport(PlayerTeleportEvent event) {
        if (event.getCause() != PlayerTeleportEvent.TeleportCause.CHORUS_FRUIT
            || event.getTo() == null
            || isBypassing(event.getPlayer())
            || plugin.settings().allowChorusFruitEntry()) {
            return;
        }
        Optional<Claim> claim = claimService.findClaim(event.getTo());
        if (claim.isPresent() && !claimService.hasPermission(claim.get(), event.getPlayer().getUniqueId(), ClaimPermission.TELEPORT)) {
            event.setCancelled(true);
            sendProtectionDeny(event.getPlayer(), claim.get());
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onPortalTeleport(PlayerPortalEvent event) {
        if (event.getTo() == null || isBypassing(event.getPlayer()) || plugin.settings().allowPortalEntry()) {
            return;
        }
        Optional<Claim> claim = claimService.findClaim(event.getTo());
        if (claim.isPresent() && !claimService.hasPermission(claim.get(), event.getPlayer().getUniqueId(), ClaimPermission.TELEPORT)) {
            event.setCancelled(true);
            sendProtectionDeny(event.getPlayer(), claim.get());
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onHangingPlace(HangingPlaceEvent event) {
        Player player = event.getPlayer();
        if (player == null) {
            return;
        }
        Optional<Claim> claim = claimService.findClaim(event.getEntity().getLocation());
        denyIfNeeded(player, claim, ClaimPermission.PLACE, event);
    }

    @EventHandler(ignoreCancelled = true)
    public void onHangingBreak(HangingBreakByEntityEvent event) {
        Player player = resolvePlayer(event.getRemover());
        if (player == null) {
            return;
        }
        Optional<Claim> claim = claimService.findClaim(event.getEntity().getLocation());
        denyIfNeeded(player, claim, ClaimPermission.BREAK, event);
    }

    @EventHandler(ignoreCancelled = true)
    public void onVehicleEnter(VehicleEnterEvent event) {
        if (!(event.getEntered() instanceof Player player)) {
            return;
        }
        Optional<Claim> claim = claimService.findClaim(event.getVehicle().getLocation());
        denyIfNeeded(player, claim, ClaimPermission.INTERACT, event);
    }

    @EventHandler(ignoreCancelled = true)
    public void onVehicleDamage(VehicleDamageEvent event) {
        Player player = resolvePlayer(event.getAttacker());
        if (player == null) {
            return;
        }
        Optional<Claim> claim = claimService.findClaim(event.getVehicle().getLocation());
        denyIfNeeded(player, claim, ClaimPermission.BREAK, event);
    }

    @EventHandler(ignoreCancelled = true)
    public void onVehicleDestroy(VehicleDestroyEvent event) {
        Player player = resolvePlayer(event.getAttacker());
        if (player == null) {
            return;
        }
        Optional<Claim> claim = claimService.findClaim(event.getVehicle().getLocation());
        denyIfNeeded(player, claim, ClaimPermission.BREAK, event);
    }

    @EventHandler(ignoreCancelled = true)
    public void onVehicleMove(VehicleMoveEvent event) {
        if (plugin.settings().allowVehicleCrossBorder()
            || event.getVehicle() instanceof Minecart
            || event.getTo() == null
            || sameBlock(event.getFrom(), event.getTo())) {
            return;
        }
        Optional<Claim> fromClaim = claimService.findClaim(event.getFrom());
        Optional<Claim> toClaim = claimService.findClaim(event.getTo());
        if (claimId(fromClaim) == claimId(toClaim) || toClaim.isEmpty() || event.getVehicle().getPassengers().isEmpty()) {
            return;
        }
        Player authorizedPassenger = findAuthorizedPassenger(event.getVehicle(), toClaim.get());
        if (authorizedPassenger != null) {
            return;
        }
        event.getVehicle().teleport(event.getFrom());
        event.getVehicle().setVelocity(event.getVehicle().getVelocity().zero());
        Player notifier = findNotifiablePassenger(event.getVehicle());
        if (notifier != null) {
            sendProtectionDeny(notifier, toClaim.get());
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onEntityDamage(EntityDamageByEntityEvent event) {
        Player attacker = resolvePlayer(event.getDamager());
        if (attacker == null || event.getEntity() instanceof Player || isBypassing(attacker)) {
            return;
        }

        Optional<Claim> claim = claimService.findClaim(event.getEntity().getLocation());
        if (claim.isPresent() && !claimService.hasPermission(claim.get(), attacker.getUniqueId(), ClaimPermission.BREAK)) {
            event.setCancelled(true);
            sendProtectionDeny(attacker, claim.get());
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onProjectileHit(ProjectileHitEvent event) {
        Player shooter = resolvePlayer(event.getEntity());
        if (shooter == null || isBypassing(shooter)) {
            return;
        }
        if (event.getHitBlock() != null) {
            Optional<Claim> claim = claimService.findClaim(event.getHitBlock().getLocation());
            if (claim.isPresent()) {
                boolean projectileProtected = isProjectileSensitiveBlock(event.getHitBlock().getType())
                    && (plugin.settings().strictRedstoneInteract()
                    || !plugin.settings().isAllowedInteract(event.getHitBlock().getType()));
                if (projectileProtected
                    && !claimService.hasPermission(claim.get(), shooter.getUniqueId(), ClaimPermission.INTERACT)) {
                    event.setCancelled(true);
                    event.getEntity().remove();
                    sendProtectionDeny(shooter, claim.get());
                    return;
                }
                if (isHazardousProjectile(event.getEntity())
                    && !claimService.hasPermission(claim.get(), shooter.getUniqueId(), projectilePermission(event.getEntity()))) {
                    event.setCancelled(true);
                    event.getEntity().remove();
                    sendProtectionDeny(shooter, claim.get());
                    return;
                }
            }
        }
        if (event.getHitEntity() == null || !(event.getHitEntity() instanceof LivingEntity)) {
            return;
        }
        Optional<Claim> claim = claimService.findClaim(event.getHitEntity().getLocation());
        if (claim.isPresent() && isHazardousProjectile(event.getEntity())
            && !claimService.hasPermission(claim.get(), shooter.getUniqueId(), projectilePermission(event.getEntity()))) {
            event.setCancelled(true);
            event.getEntity().remove();
            sendProtectionDeny(shooter, claim.get());
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onPotionSplash(PotionSplashEvent event) {
        Player shooter = resolvePlayer(event.getEntity());
        if (shooter == null || isBypassing(shooter)) {
            return;
        }
        boolean blocked = false;
        Claim deniedClaim = null;
        for (LivingEntity entity : event.getAffectedEntities()) {
            Optional<Claim> claim = claimService.findClaim(entity.getLocation());
            if (claim.isPresent() && !claimService.hasPermission(claim.get(), shooter.getUniqueId(), projectilePermission(event.getEntity()))) {
                event.setIntensity(entity, 0D);
                blocked = true;
                if (deniedClaim == null) {
                    deniedClaim = claim.get();
                }
            }
        }
        if (blocked && deniedClaim != null) {
            sendProtectionDeny(shooter, deniedClaim);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onLingeringPotion(LingeringPotionSplashEvent event) {
        Player shooter = resolvePlayer(event.getEntity());
        if (shooter == null || isBypassing(shooter)) {
            return;
        }
        Optional<Claim> claim = claimService.findClaim(event.getAreaEffectCloud().getLocation());
        if (claim.isPresent() && !claimService.hasPermission(claim.get(), shooter.getUniqueId(), projectilePermission(event.getEntity()))) {
            event.setCancelled(true);
            event.getAreaEffectCloud().remove();
            sendProtectionDeny(shooter, claim.get());
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onAreaEffectCloudApply(AreaEffectCloudApplyEvent event) {
        Player shooter = resolvePlayer(event.getEntity());
        if (shooter == null || isBypassing(shooter)) {
            return;
        }
        Claim[] deniedClaim = new Claim[1];
        boolean blocked = event.getAffectedEntities().removeIf(entity -> {
            Optional<Claim> claim = claimService.findClaim(entity.getLocation());
            boolean denied = claim.isPresent() && !claimService.hasPermission(claim.get(), shooter.getUniqueId(), projectilePermission(event.getEntity()));
            if (denied && deniedClaim[0] == null) {
                deniedClaim[0] = claim.get();
            }
            return denied;
        });
        if (blocked && event.getAffectedEntities().isEmpty()) {
            event.setCancelled(true);
        }
        if (blocked && deniedClaim[0] != null) {
            sendProtectionDeny(shooter, deniedClaim[0]);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onExplosionPrime(ExplosionPrimeEvent event) {
        Player shooter = resolvePlayer(event.getEntity());
        if (shooter == null || isBypassing(shooter)) {
            return;
        }
        if (!isHazardousProjectile(event.getEntity())) {
            return;
        }
        Optional<Claim> claim = claimService.findClaim(event.getEntity().getLocation());
        if (claim.isPresent() && !claimService.hasPermission(claim.get(), shooter.getUniqueId(), projectilePermission(event.getEntity()))) {
            event.setCancelled(true);
            event.getEntity().remove();
            sendProtectionDeny(shooter, claim.get());
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onEntityCombust(EntityCombustByEntityEvent event) {
        Player attacker = resolvePlayer(event.getCombuster());
        if (attacker == null || isBypassing(attacker)) {
            return;
        }
        Optional<Claim> claim = claimService.findClaim(event.getEntity().getLocation());
        ClaimPermission permission = isExplosionEntity(event.getCombuster()) ? ClaimPermission.EXPLOSION : ClaimPermission.BREAK;
        if (claim.isPresent() && !claimService.hasPermission(claim.get(), attacker.getUniqueId(), permission)) {
            event.setCancelled(true);
            sendProtectionDeny(attacker, claim.get());
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onExplode(EntityExplodeEvent event) {
        Player sourcePlayer = resolvePlayer(event.getEntity());
        boolean bypassing = sourcePlayer != null && isBypassing(sourcePlayer);
        Iterator<Block> iterator = event.blockList().iterator();
        while (iterator.hasNext()) {
            Optional<Claim> claim = claimService.findClaim(iterator.next().getLocation());
            if (claim.isEmpty()) {
                continue;
            }
            if (!bypassing && (sourcePlayer == null || !claimService.hasPermission(claim.get(), sourcePlayer.getUniqueId(), ClaimPermission.EXPLOSION))) {
                iterator.remove();
            }
        }
    }

    private boolean isBypassing(Player player) {
        return player.hasPermission("coreclaim.admin");
    }

    private int claimId(Optional<Claim> claim) {
        return claim.map(Claim::id).orElse(-1);
    }

    private void denyIfNeeded(Player player, Optional<Claim> claim, ClaimPermission permission, Cancellable cancellable) {
        if (claim.isEmpty() || isBypassing(player) || claimService.hasPermission(claim.get(), player.getUniqueId(), permission)) {
            return;
        }
        cancellable.setCancelled(true);
        sendProtectionDeny(player, claim.get());
    }

    private void sendProtectionDeny(Player player, Claim claim) {
        player.sendMessage(plugin.message("protection-deny", "{owner}", claim.ownerName()));
    }

    private boolean isCoreBlock(Block block, Claim claim) {
        return block.getType() == plugin.settings().coreMaterial()
            && block.getX() == claim.centerX()
            && block.getY() == claim.centerY()
            && block.getZ() == claim.centerZ();
    }

    private Player resolvePlayer(Entity entity) {
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

    private boolean isProjectileSensitiveBlock(Material material) {
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

    private ClaimPermission requiredPermissionForBlockInteract(Material material, ItemStack item) {
        if (isAxe(item) && isStrippableWood(material)) {
            return ClaimPermission.BREAK;
        }
        if (isSpecialExplosiveMaterial(material)) {
            return ClaimPermission.EXPLOSION;
        }
        if (isContainerMaterial(material)) {
            return ClaimPermission.CONTAINER;
        }
        if (plugin.settings().isAlwaysProtectedInteract(material)) {
            return ClaimPermission.REDSTONE;
        }
        return ClaimPermission.INTERACT;
    }

    private boolean isAxe(ItemStack item) {
        return item != null && item.getType().name().endsWith("_AXE");
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

    private ClaimPermission requiredPermissionForEntityInteract(Player player, Entity entity) {
        if (entity instanceof ArmorStand) {
            return ClaimPermission.BREAK;
        }
        if (entity instanceof InventoryHolder) {
            return ClaimPermission.CONTAINER;
        }
        Material held = player.getInventory().getItemInMainHand().getType();
        return held == Material.NAME_TAG ? ClaimPermission.BREAK : ClaimPermission.INTERACT;
    }

    private boolean isHazardousProjectile(Entity entity) {
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

    private boolean isExplosionEntity(Entity entity) {
        String name = entity.getType().name();
        return entity instanceof TNTPrimed
            || name.contains("FIREBALL")
            || name.contains("WITHER_SKULL")
            || name.contains("WIND_CHARGE")
            || name.equals("DRAGON_FIREBALL")
            || name.equals("SMALL_FIREBALL");
    }

    private ClaimPermission projectilePermission(Entity entity) {
        return isExplosionEntity(entity) ? ClaimPermission.EXPLOSION : ClaimPermission.BREAK;
    }

    private Player resolveOwnedEntityPlayer(Entity entity) {
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

    private Player findAuthorizedPassenger(Entity vehicle, Claim claim) {
        for (Entity passenger : vehicle.getPassengers()) {
            Player resolved = resolveOwnedEntityPlayer(passenger);
            if (resolved != null && (isBypassing(resolved) || claimService.hasPermission(claim, resolved.getUniqueId(), ClaimPermission.TELEPORT))) {
                return resolved;
            }
        }
        return null;
    }

    private Player findNotifiablePassenger(Entity vehicle) {
        for (Entity passenger : vehicle.getPassengers()) {
            Player resolved = resolveOwnedEntityPlayer(passenger);
            if (resolved != null) {
                return resolved;
            }
        }
        return null;
    }

    private boolean sameBlock(Location from, Location to) {
        return from.getWorld() == to.getWorld()
            && from.getBlockX() == to.getBlockX()
            && from.getBlockY() == to.getBlockY()
            && from.getBlockZ() == to.getBlockZ();
    }

    private boolean isSpecialExplosiveMaterial(Material material) {
        if (!plugin.settings().blockSpecialExplosiveUse()) {
            return false;
        }
        String name = material.name();
        return material == Material.RESPAWN_ANCHOR || name.endsWith("_BED");
    }

    private boolean isContainerMaterial(Material material) {
        String name = material.name();
        return name.endsWith("CHEST")
            || name.endsWith("BARREL")
            || name.endsWith("SHULKER_BOX")
            || material == Material.HOPPER
            || material == Material.DISPENSER
            || material == Material.DROPPER
            || material == Material.FURNACE
            || material == Material.BLAST_FURNACE
            || material == Material.SMOKER
            || material == Material.BREWING_STAND
            || material == Material.CHISELED_BOOKSHELF
            || material == Material.LECTERN;
    }
}
