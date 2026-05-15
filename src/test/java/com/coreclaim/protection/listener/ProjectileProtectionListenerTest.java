package com.coreclaim.protection.listener;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.coreclaim.model.Claim;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ProjectileProtectionListenerTest {

    @Test
    void authorizedTntOriginCanOnlyAffectSameClaimWhenSourcePlayerIsLost() {
        Claim source = claim(1, true);
        Claim other = claim(2, true);

        assertTrue(ProjectileProtectionListener.canExplosionAffectClaim(source, source, false, false, true));
        assertFalse(ProjectileProtectionListener.canExplosionAffectClaim(source, other, false, false, true));
        assertFalse(ProjectileProtectionListener.canExplosionAffectClaim(source, source, false, false, false));
    }

    @Test
    void playerPermissionAndBypassStillAllowClaimExplosionDamage() {
        Claim source = claim(1, false);
        Claim target = claim(2, false);

        assertTrue(ProjectileProtectionListener.canExplosionAffectClaim(source, target, false, true, false));
        assertTrue(ProjectileProtectionListener.canExplosionAffectClaim(source, target, true, false, false));
        assertTrue(ProjectileProtectionListener.canExplosionAffectClaim(source, null, false, false, false));
    }

    private static Claim claim(int id, boolean allowExplosion) {
        return new Claim(
            id,
            UUID.randomUUID(),
            "owner",
            "claim-" + id,
            "server",
            "world",
            0,
            64,
            0,
            0,
            255,
            true,
            5,
            5,
            5,
            5,
            0L,
            true,
            "",
            "",
            false,
            false,
            false,
            false,
            false,
            false,
            false,
            false,
            allowExplosion,
            false,
            false,
            false,
            false,
            false,
            null,
            null,
            null,
            null,
            null,
            0L
        );
    }
}
