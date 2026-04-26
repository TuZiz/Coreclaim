package com.coreclaim.listener.protection;

import com.coreclaim.model.Claim;
import com.coreclaim.model.ClaimFlag;
import com.coreclaim.model.ClaimPermission;
import java.util.Optional;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityMountEvent;
import org.bukkit.event.entity.PlayerLeashEntityEvent;
import org.bukkit.event.hanging.HangingBreakByEntityEvent;
import org.bukkit.event.hanging.HangingPlaceEvent;
import org.bukkit.event.player.PlayerArmorStandManipulateEvent;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerUnleashEntityEvent;
import org.bukkit.event.vehicle.VehicleDamageEvent;
import org.bukkit.event.vehicle.VehicleDestroyEvent;
import org.bukkit.event.vehicle.VehicleEnterEvent;
import org.bukkit.inventory.InventoryHolder;

public final class InteractionProtectionListener implements Listener {

    private final ProtectionRuleSupport support;

    public InteractionProtectionListener(ProtectionRuleSupport support) {
        this.support = support;
    }

    @EventHandler(ignoreCancelled = true)
    public void onEntityInteract(PlayerInteractEntityEvent event) {
        Optional<Claim> claim = support.claimService().findClaim(event.getRightClicked().getLocation());
        if (claim.isPresent()
            && event.getRightClicked() instanceof InventoryHolder
            && !support.isBypassing(event.getPlayer())) {
            if (!support.claimService().hasFlagPermission(claim.get(), event.getPlayer().getUniqueId(), ClaimFlag.CONTAINER)) {
                event.setCancelled(true);
                support.sendProtectionDeny(event.getPlayer(), claim.get());
                return;
            }
            support.claimCleanupService().recordInteractionActivity(claim.get(), event.getPlayer().getUniqueId());
            return;
        }
        ClaimPermission permission = support.requiredPermissionForEntityInteract(event.getPlayer(), event.getRightClicked());
        if (support.denyIfNeeded(event.getPlayer(), claim, permission, event)) {
            return;
        }
        if (claim.isPresent() && !support.isBypassing(event.getPlayer())) {
            support.recordEntityInteraction(claim.get(), event.getPlayer(), permission);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onEntityInteractAt(PlayerInteractAtEntityEvent event) {
        Optional<Claim> claim = support.claimService().findClaim(event.getRightClicked().getLocation());
        if (claim.isPresent()
            && event.getRightClicked() instanceof InventoryHolder
            && !support.isBypassing(event.getPlayer())) {
            if (!support.claimService().hasFlagPermission(claim.get(), event.getPlayer().getUniqueId(), ClaimFlag.CONTAINER)) {
                event.setCancelled(true);
                support.sendProtectionDeny(event.getPlayer(), claim.get());
                return;
            }
            support.claimCleanupService().recordInteractionActivity(claim.get(), event.getPlayer().getUniqueId());
            return;
        }
        ClaimPermission permission = support.requiredPermissionForEntityInteract(event.getPlayer(), event.getRightClicked());
        if (support.denyIfNeeded(event.getPlayer(), claim, permission, event)) {
            return;
        }
        if (claim.isPresent() && !support.isBypassing(event.getPlayer())) {
            support.recordEntityInteraction(claim.get(), event.getPlayer(), permission);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onArmorStandManipulate(PlayerArmorStandManipulateEvent event) {
        Optional<Claim> claim = support.claimService().findClaim(event.getRightClicked().getLocation());
        if (support.denyIfNeeded(event.getPlayer(), claim, ClaimPermission.BREAK, event)) {
            return;
        }
        if (claim.isPresent() && !support.isBypassing(event.getPlayer())) {
            support.claimCleanupService().recordBuildActivity(claim.get(), event.getPlayer().getUniqueId());
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onLeash(PlayerLeashEntityEvent event) {
        Optional<Claim> claim = support.claimService().findClaim(event.getEntity().getLocation());
        if (support.denyIfNeeded(event.getPlayer(), claim, ClaimPermission.INTERACT, event)) {
            return;
        }
        if (claim.isPresent() && !support.isBypassing(event.getPlayer())) {
            support.claimCleanupService().recordInteractionActivity(claim.get(), event.getPlayer().getUniqueId());
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onUnleash(PlayerUnleashEntityEvent event) {
        Optional<Claim> claim = support.claimService().findClaim(event.getEntity().getLocation());
        if (support.denyIfNeeded(event.getPlayer(), claim, ClaimPermission.INTERACT, event)) {
            return;
        }
        if (claim.isPresent() && !support.isBypassing(event.getPlayer())) {
            support.claimCleanupService().recordInteractionActivity(claim.get(), event.getPlayer().getUniqueId());
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onFish(PlayerFishEvent event) {
        Player player = event.getPlayer();
        if (support.isBypassing(player) || support.plugin().settings().allowFishingHookInteract()) {
            return;
        }
        Entity caught = event.getCaught();
        if (caught == null) {
            return;
        }
        Optional<Claim> claim = support.claimService().findClaim(caught.getLocation());
        ClaimPermission permission = support.requiredPermissionForEntityInteract(player, caught);
        if (claim.isPresent() && !support.claimService().hasPermission(claim.get(), player.getUniqueId(), permission)) {
            event.setCancelled(true);
            if (event.getHook() != null) {
                event.getHook().remove();
            }
            support.sendProtectionDeny(player, claim.get());
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onEntityMount(EntityMountEvent event) {
        Player rider = support.resolveOwnedEntityPlayer(event.getEntity());
        if (rider == null || support.isBypassing(rider) || event.getMount() instanceof org.bukkit.entity.Vehicle) {
            return;
        }
        Optional<Claim> claim = support.claimService().findClaim(event.getMount().getLocation());
        support.denyIfNeeded(rider, claim, ClaimPermission.INTERACT, event);
    }

    @EventHandler(ignoreCancelled = true)
    public void onHangingPlace(HangingPlaceEvent event) {
        Player player = event.getPlayer();
        if (player == null) {
            return;
        }
        Optional<Claim> claim = support.claimService().findClaim(event.getEntity().getLocation());
        support.denyIfNeeded(player, claim, ClaimPermission.PLACE, event);
    }

    @EventHandler(ignoreCancelled = true)
    public void onHangingBreak(HangingBreakByEntityEvent event) {
        Player player = support.resolvePlayer(event.getRemover());
        if (player == null) {
            return;
        }
        Optional<Claim> claim = support.claimService().findClaim(event.getEntity().getLocation());
        support.denyIfNeeded(player, claim, ClaimPermission.BREAK, event);
    }

    @EventHandler(ignoreCancelled = true)
    public void onVehicleEnter(VehicleEnterEvent event) {
        if (!(event.getEntered() instanceof Player player)) {
            return;
        }
        Optional<Claim> claim = support.claimService().findClaim(event.getVehicle().getLocation());
        support.denyIfNeeded(player, claim, ClaimPermission.INTERACT, event);
    }

    @EventHandler(ignoreCancelled = true)
    public void onVehicleDamage(VehicleDamageEvent event) {
        Player player = support.resolvePlayer(event.getAttacker());
        if (player == null) {
            return;
        }
        Optional<Claim> claim = support.claimService().findClaim(event.getVehicle().getLocation());
        support.denyIfNeeded(player, claim, ClaimPermission.BREAK, event);
    }

    @EventHandler(ignoreCancelled = true)
    public void onVehicleDestroy(VehicleDestroyEvent event) {
        Player player = support.resolvePlayer(event.getAttacker());
        if (player == null) {
            return;
        }
        Optional<Claim> claim = support.claimService().findClaim(event.getVehicle().getLocation());
        support.denyIfNeeded(player, claim, ClaimPermission.BREAK, event);
    }
}
