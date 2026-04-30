package com.coreclaim.listener.protection;

import com.coreclaim.model.Claim;
import com.coreclaim.model.ClaimPermission;
import java.util.Optional;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityCombustByEntityEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;

public final class EntityProtectionListener implements Listener {

    private final ProtectionRuleSupport support;

    public EntityProtectionListener(ProtectionRuleSupport support) {
        this.support = support;
    }

    @EventHandler(ignoreCancelled = true)
    public void onEntityDamage(EntityDamageByEntityEvent event) {
        Player attacker = support.resolvePlayer(event.getDamager());
        if (attacker == null || event.getEntity() instanceof Player || support.isBypassing(attacker)) {
            return;
        }

        Optional<Claim> claim = support.claimService().findClaim(event.getEntity().getLocation());
        ClaimPermission permission = support.requiredPermissionForEntityDamage(event.getEntity());
        if (claim.isPresent() && !support.claimService().hasPermission(claim.get(), attacker.getUniqueId(), permission)) {
            event.setCancelled(true);
            support.sendProtectionDeny(attacker, claim.get());
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onEntityCombust(EntityCombustByEntityEvent event) {
        Player attacker = support.resolvePlayer(event.getCombuster());
        if (attacker == null || support.isBypassing(attacker)) {
            return;
        }
        Optional<Claim> claim = support.claimService().findClaim(event.getEntity().getLocation());
        ClaimPermission permission = support.isExplosionEntity(event.getCombuster())
            ? ClaimPermission.EXPLOSION
            : support.requiredPermissionForEntityDamage(event.getEntity());
        if (claim.isPresent() && !support.claimService().hasPermission(claim.get(), attacker.getUniqueId(), permission)) {
            event.setCancelled(true);
            support.sendProtectionDeny(attacker, claim.get());
        }
    }
}
