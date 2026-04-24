package com.coreclaim.service;

import com.coreclaim.CoreClaimPlugin;
import com.coreclaim.model.Claim;
import com.coreclaim.util.AdminAccess;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.entity.Player;

public final class RemovalConfirmationService {

    private final CoreClaimPlugin plugin;
    private final ClaimActionService claimActionService;
    private final ClaimService claimService;
    private final Map<UUID, PendingRemoval> pendingRemovals = new ConcurrentHashMap<>();

    public RemovalConfirmationService(CoreClaimPlugin plugin, ClaimActionService claimActionService, ClaimService claimService) {
        this.plugin = plugin;
        this.claimActionService = claimActionService;
        this.claimService = claimService;
    }

    public boolean request(Player player, Claim claim) {
        return requestOwnerRemoval(player, claim);
    }

    public boolean requestOwnerRemoval(Player player, Claim claim) {
        if (!claim.owner().equals(player.getUniqueId())) {
            player.sendMessage(plugin.message("trust-no-permission"));
            return false;
        }
        pendingRemovals.put(player.getUniqueId(), new PendingRemoval(claim.id(), false));
        player.sendMessage(plugin.message("claim-remove-requested", "{name}", claim.name()));
        return true;
    }

    public boolean requestAdminRemoval(Player player, Claim claim) {
        if (!AdminAccess.hasClaimManageAccess(player)) {
            player.sendMessage(plugin.message("trust-no-permission"));
            return false;
        }
        pendingRemovals.put(player.getUniqueId(), new PendingRemoval(claim.id(), true));
        player.sendMessage(plugin.message("claim-remove-requested", "{name}", claim.name()));
        return true;
    }

    public boolean hasPending(UUID playerId) {
        return pendingRemovals.containsKey(playerId);
    }

    public boolean confirm(Player player) {
        PendingRemoval pendingRemoval = pendingRemovals.remove(player.getUniqueId());
        if (pendingRemoval == null) {
            return false;
        }
        Claim claim = claimService.findClaimByIdFresh(pendingRemoval.claimId()).orElse(null);
        if (claim == null) {
            player.sendMessage(plugin.message("claim-not-found"));
            return true;
        }
        if (pendingRemoval.adminMode()) {
            claimActionService.adminRemoveClaim(player, claim);
        } else {
            claimActionService.unclaim(player, claim);
        }
        return true;
    }

    public boolean cancel(Player player) {
        return pendingRemovals.remove(player.getUniqueId()) != null;
    }

    private record PendingRemoval(int claimId, boolean adminMode) {
    }
}
