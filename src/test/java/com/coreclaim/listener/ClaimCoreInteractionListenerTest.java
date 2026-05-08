package com.coreclaim.listener;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.coreclaim.model.Claim;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ClaimCoreInteractionListenerTest {

    @Test
    void trustedMembersCanOpenReadOnlyCoreMenu() {
        UUID owner = UUID.randomUUID();
        UUID trusted = UUID.randomUUID();
        Claim claim = claim(owner);
        claim.addTrustedMember(trusted);

        assertTrue(ClaimCoreInteractionListener.canOpenMemberCoreMenu(claim, trusted));
        assertFalse(ClaimCoreInteractionListener.canOpenMemberCoreMenu(claim, owner));
    }

    @Test
    void deniedAndPublicVisitorsCannotOpenCoreMenuAsMembers() {
        UUID owner = UUID.randomUUID();
        UUID trusted = UUID.randomUUID();
        UUID denied = UUID.randomUUID();
        UUID publicVisitor = UUID.randomUUID();
        Claim claim = claim(owner);
        claim.addTrustedMember(trusted);
        claim.addDeniedMember(denied);

        assertFalse(ClaimCoreInteractionListener.canOpenMemberCoreMenu(claim, denied));
        assertFalse(ClaimCoreInteractionListener.canOpenMemberCoreMenu(claim, publicVisitor));
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
