package com.coreclaim.service;

import com.coreclaim.model.Claim;
import com.coreclaim.util.AdminAccess;
import java.util.UUID;
import org.bukkit.entity.Player;
import org.bukkit.permissions.Permissible;

final class ClaimInputAccess {

    private ClaimInputAccess() {
    }

    static boolean canCommitClaimText(Player player, Claim claim) {
        return player != null && canCommitClaimText(player.getUniqueId(), player, claim);
    }

    static boolean canCommitClaimText(UUID playerId, Permissible permissible, Claim claim) {
        return playerId != null && claim != null
            && (claim.owner().equals(playerId) || AdminAccess.hasClaimManageAccess(permissible));
    }
}
