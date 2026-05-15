package com.coreclaim.claim.reservation;

import com.coreclaim.CoreClaimPlugin;
import com.coreclaim.claim.mutation.ClaimCoreRegionService;
import com.coreclaim.claim.mutation.ClaimCreationOptions;
import com.coreclaim.model.Claim;
import com.coreclaim.claim.reservation.CoreBlockPresenceChecker.Presence;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Level;
import org.bukkit.Location;

public final class PendingCoreReservationService {

    private final CoreClaimPlugin plugin;
    private final ClaimCoreRegionService coreRegionService;
    private final PendingCoreReservationRegistry registry = new PendingCoreReservationRegistry();
    private final CoreBlockPresenceChecker coreBlockPresenceChecker;

    public PendingCoreReservationService(CoreClaimPlugin plugin, ClaimCoreRegionService coreRegionService) {
        this(plugin, coreRegionService, reservation -> {
            if (plugin == null) {
                return Presence.WORLD_UNLOADED;
            }
            org.bukkit.World world = plugin.getServer().getWorld(reservation.world());
            if (world == null) {
                return Presence.WORLD_UNLOADED;
            }
            Location location = new Location(world, reservation.x(), reservation.y(), reservation.z());
            return location.getBlock().getType() == plugin.settings().coreMaterial()
                ? Presence.PRESENT
                : Presence.MISSING_OR_REPLACED;
        });
    }

    PendingCoreReservationService(
        CoreClaimPlugin plugin,
        ClaimCoreRegionService coreRegionService,
        CoreBlockPresenceChecker coreBlockPresenceChecker
    ) {
        this.plugin = plugin;
        this.coreRegionService = coreRegionService;
        this.coreBlockPresenceChecker = coreBlockPresenceChecker;
    }

    public PendingCoreReservation reserve(UUID ownerId, Location location, ClaimCreationMode mode, ClaimCreationOptions options) {
        LocationKey key = LocationKey.from(location);
        PendingCoreReservation reservation = new PendingCoreReservation(
            UUID.randomUUID(),
            ownerId,
            key.world(),
            key.x(),
            key.y(),
            key.z(),
            System.currentTimeMillis(),
            mode
        );
        registry.put(reservation);
        try {
            coreRegionService.placeTemporaryCore(location, options);
            return reservation;
        } catch (RuntimeException exception) {
            registry.release(reservation);
            throw exception;
        }
    }

    public boolean isReserved(Location location) {
        return registry.isReserved(LocationKey.from(location));
    }

    public Optional<PendingCoreReservation> reservationAt(Location location) {
        return registry.at(LocationKey.from(location));
    }

    public boolean isReservedBy(Location location, UUID reservationId) {
        return registry.isReservedBy(LocationKey.from(location), reservationId);
    }

    public boolean validateStillReserved(PendingCoreReservation reservation) {
        return registry.validateStillReserved(reservation);
    }

    public boolean validateStillReservedAndCorePresent(PendingCoreReservation reservation) {
        if (!registry.validateStillReserved(reservation)) {
            return false;
        }
        Presence presence = coreBlockPresenceChecker.check(reservation);
        if (presence == Presence.PRESENT) {
            return true;
        }
        markInvalid(reservation, presence == Presence.WORLD_UNLOADED ? "world-unloaded" : "core-missing-or-replaced");
        return false;
    }

    public void markInvalid(PendingCoreReservation reservation, String reason) {
        registry.markInvalid(reservation, reason);
    }

    public void commit(PendingCoreReservation reservation, Claim claim) {
        registry.commit(reservation, claim.id());
    }

    public void release(PendingCoreReservation reservation) {
        registry.release(reservation);
    }

    public void releaseAndClear(PendingCoreReservation reservation) {
        if (reservation == null) {
            return;
        }
        registry.release(reservation);
        Location location = toLocation(reservation);
        if (location != null) {
            coreRegionService.clearTemporaryCore(location);
        }
    }

    public void shutdown() {
        for (PendingCoreReservation reservation : registry.snapshot()) {
            try {
                plugin.platformScheduler().runLocationTask(toLocation(reservation), () -> releaseAndClear(reservation));
            } catch (RuntimeException exception) {
                plugin.getLogger().log(Level.WARNING, "Failed to schedule pending core cleanup during shutdown: " + reservation.reservationId(), exception);
            }
        }
    }

    private Location toLocation(PendingCoreReservation reservation) {
        if (reservation == null) {
            return null;
        }
        org.bukkit.World world = plugin.getServer().getWorld(reservation.world());
        if (world == null) {
            return null;
        }
        return new Location(world, reservation.x(), reservation.y(), reservation.z());
    }

    void register(PendingCoreReservation reservation) {
        registry.put(reservation);
    }
}
