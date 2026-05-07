package com.coreclaim.protection.listener;

import com.coreclaim.model.Claim;
import com.coreclaim.model.ClaimPermission;
import java.util.Iterator;
import java.util.Optional;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.AreaEffectCloudApplyEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.ExplosionPrimeEvent;
import org.bukkit.event.entity.LingeringPotionSplashEvent;
import org.bukkit.event.entity.PotionSplashEvent;
import org.bukkit.event.entity.ProjectileHitEvent;

public final class ProjectileProtectionListener implements Listener {

    private final ProtectionRuleSupport support;

    public ProjectileProtectionListener(ProtectionRuleSupport support) {
        this.support = support;
    }

    @EventHandler(ignoreCancelled = true)
    public void onProjectileHit(ProjectileHitEvent event) {
        Player shooter = support.resolvePlayer(event.getEntity());
        if (shooter == null || support.isBypassing(shooter)) {
            return;
        }
        if (event.getHitBlock() != null) {
            Optional<Claim> claim = support.claimService().findClaim(event.getHitBlock().getLocation());
            if (claim.isPresent()) {
                boolean projectileProtected = support.isProjectileSensitiveBlock(event.getHitBlock().getType())
                    && (support.plugin().settings().strictRedstoneInteract()
                    || !support.plugin().settings().isAllowedInteract(event.getHitBlock().getType()));
                if (projectileProtected
                    && !support.claimService().hasPermission(claim.get(), shooter.getUniqueId(), support.projectileSensitivePermission(event.getHitBlock().getType()))) {
                    event.setCancelled(true);
                    event.getEntity().remove();
                    support.sendProtectionDeny(shooter, claim.get());
                    return;
                }
                if (support.isHazardousProjectile(event.getEntity())
                    && !support.claimService().hasPermission(claim.get(), shooter.getUniqueId(), support.projectilePermission(event.getEntity()))) {
                    event.setCancelled(true);
                    event.getEntity().remove();
                    support.sendProtectionDeny(shooter, claim.get());
                    return;
                }
            }
        }
        if (event.getHitEntity() == null || !(event.getHitEntity() instanceof LivingEntity)) {
            return;
        }
        Optional<Claim> claim = support.claimService().findClaim(event.getHitEntity().getLocation());
        if (claim.isPresent() && support.isHazardousProjectile(event.getEntity())
            && !support.claimService().hasPermission(claim.get(), shooter.getUniqueId(), support.projectileEntityPermission(event.getEntity(), event.getHitEntity()))) {
            event.setCancelled(true);
            event.getEntity().remove();
            support.sendProtectionDeny(shooter, claim.get());
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onPotionSplash(PotionSplashEvent event) {
        Player shooter = support.resolvePlayer(event.getEntity());
        if (shooter == null || support.isBypassing(shooter)) {
            return;
        }
        boolean blocked = false;
        Claim deniedClaim = null;
        for (LivingEntity entity : event.getAffectedEntities()) {
            Optional<Claim> claim = support.claimService().findClaim(entity.getLocation());
            if (claim.isPresent() && !support.claimService().hasPermission(claim.get(), shooter.getUniqueId(), support.projectileEntityPermission(event.getEntity(), entity))) {
                event.setIntensity(entity, 0D);
                blocked = true;
                if (deniedClaim == null) {
                    deniedClaim = claim.get();
                }
            }
        }
        if (blocked && deniedClaim != null) {
            support.sendProtectionDeny(shooter, deniedClaim);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onLingeringPotion(LingeringPotionSplashEvent event) {
        Player shooter = support.resolvePlayer(event.getEntity());
        if (shooter == null || support.isBypassing(shooter)) {
            return;
        }
        Optional<Claim> claim = support.claimService().findClaim(event.getAreaEffectCloud().getLocation());
        if (claim.isPresent() && !support.claimService().hasPermission(claim.get(), shooter.getUniqueId(), support.projectilePermission(event.getEntity()))) {
            event.setCancelled(true);
            event.getAreaEffectCloud().remove();
            support.sendProtectionDeny(shooter, claim.get());
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onAreaEffectCloudApply(AreaEffectCloudApplyEvent event) {
        Player shooter = support.resolvePlayer(event.getEntity());
        if (shooter == null || support.isBypassing(shooter)) {
            return;
        }
        Claim[] deniedClaim = new Claim[1];
        boolean blocked = event.getAffectedEntities().removeIf(entity -> {
            Optional<Claim> claim = support.claimService().findClaim(entity.getLocation());
            boolean denied = claim.isPresent() && !support.claimService().hasPermission(claim.get(), shooter.getUniqueId(), support.projectileEntityPermission(event.getEntity(), entity));
            if (denied && deniedClaim[0] == null) {
                deniedClaim[0] = claim.get();
            }
            return denied;
        });
        if (blocked && event.getAffectedEntities().isEmpty()) {
            event.setCancelled(true);
        }
        if (blocked && deniedClaim[0] != null) {
            support.sendProtectionDeny(shooter, deniedClaim[0]);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onExplosionPrime(ExplosionPrimeEvent event) {
        Player shooter = support.resolvePlayer(event.getEntity());
        if (shooter == null || support.isBypassing(shooter)) {
            return;
        }
        if (!support.isHazardousProjectile(event.getEntity())) {
            return;
        }
        Optional<Claim> claim = support.claimService().findClaim(event.getEntity().getLocation());
        if (claim.isPresent() && !support.claimService().hasPermission(claim.get(), shooter.getUniqueId(), support.projectilePermission(event.getEntity()))) {
            event.setCancelled(true);
            event.getEntity().remove();
            support.sendProtectionDeny(shooter, claim.get());
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onExplode(EntityExplodeEvent event) {
        Player sourcePlayer = support.resolvePlayer(event.getEntity());
        boolean bypassing = sourcePlayer != null && support.isBypassing(sourcePlayer);
        Iterator<Block> iterator = event.blockList().iterator();
        while (iterator.hasNext()) {
            Optional<Claim> claim = support.claimService().findClaim(iterator.next().getLocation());
            if (claim.isEmpty()) {
                continue;
            }
            if (!bypassing && (sourcePlayer == null || !support.claimService().hasPermission(claim.get(), sourcePlayer.getUniqueId(), ClaimPermission.EXPLOSION))) {
                iterator.remove();
            }
        }
    }
}
