package com.coreclaim.service.claim;

import com.coreclaim.CoreClaimPlugin;
import com.coreclaim.model.Claim;
import com.coreclaim.service.ClaimCleanupService;
import com.coreclaim.service.ClaimSyncPublisher;
import com.coreclaim.service.ProfileService;
import com.coreclaim.storage.DatabaseManager;
import java.util.List;
import java.util.Map;

public final class ClaimRuntime {

    private final CoreClaimPlugin plugin;
    private final DatabaseManager databaseManager;
    private final ProfileService profileService;
    private final Map<Integer, Claim> claims;
    private final Object mutationLock;
    private volatile Map<String, Map<Long, List<Claim>>> claimChunkIndex = Map.of();
    private volatile ClaimSyncPublisher claimSyncPublisher = ClaimSyncPublisher.NO_OP;
    private volatile ClaimCleanupService claimCleanupService;

    public ClaimRuntime(
        CoreClaimPlugin plugin,
        DatabaseManager databaseManager,
        ProfileService profileService,
        Map<Integer, Claim> claims,
        Object mutationLock
    ) {
        this.plugin = plugin;
        this.databaseManager = databaseManager;
        this.profileService = profileService;
        this.claims = claims;
        this.mutationLock = mutationLock;
    }

    public CoreClaimPlugin plugin() {
        return plugin;
    }

    public DatabaseManager databaseManager() {
        return databaseManager;
    }

    public ProfileService profileService() {
        return profileService;
    }

    public Map<Integer, Claim> claims() {
        return claims;
    }

    public Object mutationLock() {
        return mutationLock;
    }

    public Map<String, Map<Long, List<Claim>>> claimChunkIndex() {
        return claimChunkIndex;
    }

    public void setClaimChunkIndex(Map<String, Map<Long, List<Claim>>> claimChunkIndex) {
        this.claimChunkIndex = claimChunkIndex == null ? Map.of() : claimChunkIndex;
    }

    public ClaimSyncPublisher claimSyncPublisher() {
        return claimSyncPublisher;
    }

    public void setClaimSyncPublisher(ClaimSyncPublisher claimSyncPublisher) {
        this.claimSyncPublisher = claimSyncPublisher == null ? ClaimSyncPublisher.NO_OP : claimSyncPublisher;
    }

    public ClaimCleanupService claimCleanupService() {
        return claimCleanupService;
    }

    public void setClaimCleanupService(ClaimCleanupService claimCleanupService) {
        this.claimCleanupService = claimCleanupService;
    }
}
