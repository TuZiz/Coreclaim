package com.coreclaim.listener;

import com.coreclaim.claim.reservation.PendingCoreReservation;
import com.coreclaim.claim.reservation.PendingCoreReservationService;
import com.coreclaim.listener.PendingCoreProtectionPolicy.Decision;
import java.util.Iterator;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockFadeEvent;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityExplodeEvent;

public final class PendingCoreProtectionListener implements Listener {

    public static final String BYPASS_PERMISSION = "coreclaim.admin.pendingcore.bypass";

    private final PendingCoreReservationService reservationService;

    public PendingCoreProtectionListener(PendingCoreReservationService reservationService) {
        this.reservationService = reservationService;
    }

    @EventHandler(ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        handlePlayerBlockMutation(event.getBlock().getLocation(), event.getPlayer(), event::setCancelled, "bypass-break");
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        handlePlayerBlockMutation(event.getBlockPlaced().getLocation(), event.getPlayer(), event::setCancelled, "bypass-place");
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        removeReservedBlocks(event.blockList().iterator());
    }

    @EventHandler(ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        removeReservedBlocks(event.blockList().iterator());
    }

    @EventHandler(ignoreCancelled = true)
    public void onPistonExtend(BlockPistonExtendEvent event) {
        for (Block block : event.getBlocks()) {
            if (isReserved(block.getLocation()) || isReserved(block.getRelative(event.getDirection()).getLocation())) {
                event.setCancelled(true);
                return;
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onPistonRetract(BlockPistonRetractEvent event) {
        for (Block block : event.getBlocks()) {
            if (isReserved(block.getLocation()) || isReserved(block.getRelative(event.getDirection().getOppositeFace()).getLocation())) {
                event.setCancelled(true);
                return;
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockFromTo(BlockFromToEvent event) {
        if (PendingCoreProtectionPolicy.environmentalMutation(isReserved(event.getToBlock().getLocation())) == Decision.CANCEL) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockBurn(BlockBurnEvent event) {
        if (PendingCoreProtectionPolicy.environmentalMutation(isReserved(event.getBlock().getLocation())) == Decision.CANCEL) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockFade(BlockFadeEvent event) {
        if (PendingCoreProtectionPolicy.environmentalMutation(isReserved(event.getBlock().getLocation())) == Decision.CANCEL) {
            event.setCancelled(true);
        }
    }

    private void handlePlayerBlockMutation(Location location, Player player, java.util.function.Consumer<Boolean> cancellation, String reason) {
        PendingCoreReservation reservation = reservationService.reservationAt(location).orElse(null);
        Decision decision = PendingCoreProtectionPolicy.blockMutation(
            reservation != null && reservation.valid(),
            player != null && player.hasPermission(BYPASS_PERMISSION)
        );
        if (decision == Decision.CANCEL) {
            cancellation.accept(true);
            return;
        }
        if (decision == Decision.INVALIDATE_AND_ALLOW) {
            reservationService.markInvalid(reservation, reason);
        }
    }

    private void removeReservedBlocks(Iterator<Block> iterator) {
        while (iterator.hasNext()) {
            if (PendingCoreProtectionPolicy.environmentalMutation(isReserved(iterator.next().getLocation())) == Decision.CANCEL) {
                iterator.remove();
            }
        }
    }

    private boolean isReserved(Location location) {
        return reservationService.isReserved(location);
    }
}
