package com.coreclaim.service;

import com.coreclaim.CoreClaimPlugin;
import com.coreclaim.model.Claim;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.entity.Player;

public final class RemovalConfirmationService {

    private final CoreClaimPlugin plugin;
    private final ClaimActionService claimActionService;
    private final ClaimService claimService;
    private final Map<UUID, Integer> pendingRemovals = new ConcurrentHashMap<>();

    public RemovalConfirmationService(CoreClaimPlugin plugin, ClaimActionService claimActionService, ClaimService claimService) {
        this.plugin = plugin;
        this.claimActionService = claimActionService;
        this.claimService = claimService;
    }

    public boolean request(Player player, Claim claim) {
        if (!claim.owner().equals(player.getUniqueId())) {
            player.sendMessage(plugin.message("trust-no-permission"));
            return false;
        }
        pendingRemovals.put(player.getUniqueId(), claim.id());
        player.sendMessage(plugin.message("claim-remove-requested", "{name}", claim.name()));
        return true;
    }

    public boolean hasPending(UUID playerId) {
        return pendingRemovals.containsKey(playerId);
    }

    public boolean confirm(Player player) {
        Integer claimId = pendingRemovals.remove(player.getUniqueId());
        if (claimId == null) {
            return false;
        }
        Claim claim = claimService.findClaimById(claimId).orElse(null);
        if (claim == null) {
            player.sendMessage(plugin.message("claim-not-found"));
            return true;
        }
        claimActionService.unclaim(player, claim);
        return true;
    }

    public boolean cancel(Player player) {
        return pendingRemovals.remove(player.getUniqueId()) != null;
    }
}
