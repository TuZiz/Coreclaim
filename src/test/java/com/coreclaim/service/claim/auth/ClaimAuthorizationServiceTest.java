package com.coreclaim.service.claim.auth;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.coreclaim.model.Claim;
import com.coreclaim.model.ClaimFlag;
import com.coreclaim.model.ClaimFlagState;
import com.coreclaim.model.ClaimPermission;
import com.coreclaim.service.claim.ClaimRuntime;
import java.util.HashMap;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ClaimAuthorizationServiceTest {

    private final ClaimAuthorizationService authorizationService =
        new ClaimAuthorizationService(new ClaimRuntime(null, null, null, new HashMap<>(), new Object()));

    @Test
    void publicVisitorsUseClaimDefaultPermissionsWhenDenyAllIsOff() {
        UUID owner = UUID.randomUUID();
        UUID visitor = UUID.randomUUID();
        Claim claim = claim(owner);
        claim.setPermission(ClaimPermission.BREAK, true);
        claim.setPermission(ClaimPermission.INTERACT, true);

        assertTrue(authorizationService.canAccess(claim, visitor));
        assertTrue(authorizationService.hasPermission(claim, visitor, ClaimPermission.BREAK));
        assertTrue(authorizationService.hasPermission(claim, visitor, ClaimPermission.INTERACT));
    }

    @Test
    void denyAllStillBlocksPublicVisitorsEvenWhenDefaultPermissionIsAllowed() {
        UUID owner = UUID.randomUUID();
        UUID visitor = UUID.randomUUID();
        Claim claim = claim(owner);
        claim.setPermission(ClaimPermission.BREAK, true);
        claim.setPermission(ClaimPermission.INTERACT, true);
        claim.setDenyAll(true);

        assertFalse(authorizationService.canAccess(claim, visitor));
        assertFalse(authorizationService.hasPermission(claim, visitor, ClaimPermission.BREAK));
        assertFalse(authorizationService.hasPermission(claim, visitor, ClaimPermission.INTERACT));
    }

    @Test
    void deniedPlayersStayBlockedEvenIfClaimIsOtherwisePublic() {
        UUID owner = UUID.randomUUID();
        UUID denied = UUID.randomUUID();
        Claim claim = claim(owner);
        claim.setPermission(ClaimPermission.INTERACT, true);
        claim.addDeniedMember(denied);

        assertFalse(authorizationService.canAccess(claim, denied));
        assertFalse(authorizationService.hasPermission(claim, denied, ClaimPermission.INTERACT));
    }

    @Test
    void trustedMembersDefaultToFullPermissionsWhenPublicDefaultsAreDenied() {
        UUID owner = UUID.randomUUID();
        UUID trusted = UUID.randomUUID();
        Claim claim = claim(owner);
        claim.addTrustedMember(trusted);
        claim.setPermission(ClaimPermission.BREAK, false);
        claim.setPermission(ClaimPermission.INTERACT, false);
        claim.setFlagState(ClaimFlag.LIQUID_FLOW, ClaimFlagState.UNSET);

        assertTrue(authorizationService.canAccess(claim, trusted));
        assertTrue(authorizationService.hasPermission(claim, trusted, ClaimPermission.BREAK));
        assertTrue(authorizationService.hasPermission(claim, trusted, ClaimPermission.INTERACT));
        assertTrue(authorizationService.hasFlagPermission(claim, trusted, ClaimFlag.LIQUID_FLOW));
    }

    @Test
    void publicFlagAccessFallsBackToClaimPermissionDefaults() {
        UUID owner = UUID.randomUUID();
        UUID visitor = UUID.randomUUID();
        Claim claim = claim(owner);
        claim.setPermission(ClaimPermission.INTERACT, true);
        claim.setFlagState(ClaimFlag.LIQUID_FLOW, ClaimFlagState.UNSET);

        assertTrue(authorizationService.hasFlagPermission(claim, visitor, ClaimFlag.LIQUID_FLOW));

        claim.setPermission(ClaimPermission.INTERACT, false);
        assertFalse(authorizationService.hasFlagPermission(claim, visitor, ClaimFlag.LIQUID_FLOW));
    }

    @Test
    void publicVisitorsCannotTeleportWhenTeleportPermissionIsDenied() {
        UUID owner = UUID.randomUUID();
        UUID visitor = UUID.randomUUID();
        Claim claim = claim(owner);
        claim.setPermission(ClaimPermission.TELEPORT, false);

        assertFalse(authorizationService.hasPermission(claim, visitor, ClaimPermission.TELEPORT));
    }

    @Test
    void publicVisitorsCannotBreakWhenBreakPermissionIsDenied() {
        UUID owner = UUID.randomUUID();
        UUID visitor = UUID.randomUUID();
        Claim claim = claim(owner);
        claim.setPermission(ClaimPermission.BREAK, false);

        assertFalse(authorizationService.hasPermission(claim, visitor, ClaimPermission.BREAK));
    }

    @Test
    void trustedMembersCanTeleportWhenPublicTeleportIsDenied() {
        UUID owner = UUID.randomUUID();
        UUID trusted = UUID.randomUUID();
        Claim claim = claim(owner);
        claim.addTrustedMember(trusted);
        claim.setPermission(ClaimPermission.TELEPORT, false);

        assertTrue(authorizationService.hasPermission(claim, trusted, ClaimPermission.TELEPORT));
    }

    private Claim claim(UUID owner) {
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
            false,
            false,
            false,
            false,
            false,
            true,
            true,
            false,
            false,
            false,
            true,
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
