package com.coreclaim.listener.protection;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.coreclaim.model.Claim;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class VehicleProtectionListenerTest {

    @Test
    void mountedAnimalsCanEnterNormalClaimsLikeWalkingPlayers() {
        UUID owner = UUID.randomUUID();
        UUID visitor = UUID.randomUUID();

        assertTrue(VehicleProtectionListener.canMountedPlayerEnterClaim(claim(owner, false), visitor));
    }

    @Test
    void mountedAnimalsCannotEnterPrivateOrDeniedClaimsWithoutTeleportPermission() {
        UUID owner = UUID.randomUUID();
        UUID visitor = UUID.randomUUID();
        Claim privateClaim = claim(owner, true);
        Claim deniedClaim = claim(owner, false);
        deniedClaim.addDeniedMember(visitor);

        assertFalse(VehicleProtectionListener.canMountedPlayerEnterClaim(privateClaim, visitor));
        assertFalse(VehicleProtectionListener.canMountedPlayerEnterClaim(deniedClaim, visitor));
    }

    @Test
    void ownersAndTrustedMembersCanRideMountsIntoPrivateClaims() {
        UUID owner = UUID.randomUUID();
        UUID trusted = UUID.randomUUID();
        Claim claim = claim(owner, true);
        claim.addTrustedMember(trusted);

        assertTrue(VehicleProtectionListener.canMountedPlayerEnterClaim(claim, owner));
        assertTrue(VehicleProtectionListener.canMountedPlayerEnterClaim(claim, trusted));
    }

    private Claim claim(UUID owner, boolean denyAll) {
        return new Claim(
            1,
            owner,
            "Owner",
            "Home",
            "local",
            "world",
            0,
            64,
            0,
            -64,
            320,
            true,
            5,
            5,
            5,
            5,
            0L,
            true,
            "",
            "",
            true,
            true,
            true,
            true,
            true,
            true,
            true,
            true,
            true,
            true,
            true,
            true,
            false,
            denyAll,
            null,
            null,
            null,
            null,
            null,
            0L
        );
    }
}
