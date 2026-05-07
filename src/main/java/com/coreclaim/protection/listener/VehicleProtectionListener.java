package com.coreclaim.protection.listener;

import com.coreclaim.model.Claim;
import com.coreclaim.model.ClaimPermission;
import java.util.Optional;
import java.util.UUID;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Minecart;
import org.bukkit.entity.Player;
import org.bukkit.entity.Vehicle;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerPortalEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.vehicle.VehicleMoveEvent;

public final class VehicleProtectionListener implements Listener {

    private final ProtectionRuleSupport support;

    public VehicleProtectionListener(ProtectionRuleSupport support) {
        this.support = support;
    }

    @EventHandler(ignoreCancelled = true)
    public void onMountedMove(PlayerMoveEvent event) {
        if (event.getTo() == null || support.sameBlock(event.getFrom(), event.getTo()) || support.isBypassing(event.getPlayer())) {
            return;
        }
        Entity mount = event.getPlayer().getVehicle();
        if (mount == null || mount instanceof Vehicle) {
            return;
        }
        Optional<Claim> fromClaim = support.claimService().findClaim(event.getFrom());
        Optional<Claim> toClaim = support.claimService().findClaim(event.getTo());
        if (support.claimId(fromClaim) == support.claimId(toClaim) || toClaim.isEmpty()) {
            return;
        }
        if (canMountedPlayerEnterClaim(toClaim.get(), event.getPlayer().getUniqueId())) {
            return;
        }
        if (support.claimService().hasPermission(toClaim.get(), event.getPlayer().getUniqueId(), ClaimPermission.TELEPORT)) {
            return;
        }
        event.setTo(event.getFrom());
        support.sendProtectionDeny(event.getPlayer(), toClaim.get());
    }

    @EventHandler(ignoreCancelled = true)
    public void onPearlTeleport(PlayerTeleportEvent event) {
        if (event.getCause() != PlayerTeleportEvent.TeleportCause.ENDER_PEARL
            || event.getTo() == null
            || support.isBypassing(event.getPlayer())
            || support.plugin().settings().allowEnderPearlEntry()) {
            return;
        }
        Optional<Claim> claim = support.claimService().findClaim(event.getTo());
        if (claim.isPresent() && !support.claimService().hasPermission(claim.get(), event.getPlayer().getUniqueId(), ClaimPermission.TELEPORT)) {
            event.setCancelled(true);
            support.sendProtectionDeny(event.getPlayer(), claim.get());
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onChorusTeleport(PlayerTeleportEvent event) {
        if (event.getCause() != PlayerTeleportEvent.TeleportCause.CHORUS_FRUIT
            || event.getTo() == null
            || support.isBypassing(event.getPlayer())
            || support.plugin().settings().allowChorusFruitEntry()) {
            return;
        }
        Optional<Claim> claim = support.claimService().findClaim(event.getTo());
        if (claim.isPresent() && !support.claimService().hasPermission(claim.get(), event.getPlayer().getUniqueId(), ClaimPermission.TELEPORT)) {
            event.setCancelled(true);
            support.sendProtectionDeny(event.getPlayer(), claim.get());
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onPortalTeleport(PlayerPortalEvent event) {
        if (event.getTo() == null || support.isBypassing(event.getPlayer()) || support.plugin().settings().allowPortalEntry()) {
            return;
        }
        Optional<Claim> claim = support.claimService().findClaim(event.getTo());
        if (claim.isPresent() && !support.claimService().hasPermission(claim.get(), event.getPlayer().getUniqueId(), ClaimPermission.TELEPORT)) {
            event.setCancelled(true);
            support.sendProtectionDeny(event.getPlayer(), claim.get());
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onVehicleMove(VehicleMoveEvent event) {
        if (support.plugin().settings().allowVehicleCrossBorder()
            || event.getVehicle() instanceof Minecart
            || event.getTo() == null
            || support.sameBlock(event.getFrom(), event.getTo())) {
            return;
        }
        Optional<Claim> fromClaim = support.claimService().findClaim(event.getFrom());
        Optional<Claim> toClaim = support.claimService().findClaim(event.getTo());
        if (support.claimId(fromClaim) == support.claimId(toClaim) || toClaim.isEmpty() || event.getVehicle().getPassengers().isEmpty()) {
            return;
        }
        Player authorizedPassenger = support.findAuthorizedPassenger(event.getVehicle(), toClaim.get());
        if (authorizedPassenger != null) {
            return;
        }
        event.getVehicle().teleport(event.getFrom());
        event.getVehicle().setVelocity(event.getVehicle().getVelocity().zero());
        Player notifier = support.findNotifiablePassenger(event.getVehicle());
        if (notifier != null) {
            support.sendProtectionDeny(notifier, toClaim.get());
        }
    }

    static boolean canMountedPlayerEnterClaim(Claim claim, UUID playerId) {
        if (claim == null || playerId == null) {
            return false;
        }
        if (claim.owner().equals(playerId) || claim.isTrusted(playerId)) {
            return true;
        }
        if (claim.isDenied(playerId)) {
            return false;
        }
        return !claim.denyAll();
    }
}
