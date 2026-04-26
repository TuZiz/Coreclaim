package com.coreclaim.service.claim.query;

import com.coreclaim.model.Claim;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import org.bukkit.Location;

final class ClaimChunkIndex {

    private ClaimChunkIndex() {
    }

    static Map<String, Map<Long, List<Claim>>> rebuild(Collection<Claim> claims, Predicate<Claim> localFilter) {
        Map<String, Map<Long, List<Claim>>> rebuilt = new HashMap<>();
        for (Claim claim : claims) {
            if (localFilter != null && !localFilter.test(claim)) {
                continue;
            }
            Map<Long, List<Claim>> worldIndex = rebuilt.computeIfAbsent(claim.world(), ignored -> new HashMap<>());
            int minChunkX = claim.minX() >> 4;
            int maxChunkX = claim.maxX() >> 4;
            int minChunkZ = claim.minZ() >> 4;
            int maxChunkZ = claim.maxZ() >> 4;
            for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
                for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
                    worldIndex.computeIfAbsent(chunkKey(chunkX, chunkZ), ignored -> new ArrayList<>()).add(claim);
                }
            }
        }

        Map<String, Map<Long, List<Claim>>> finalized = new HashMap<>();
        for (Map.Entry<String, Map<Long, List<Claim>>> worldEntry : rebuilt.entrySet()) {
            Map<Long, List<Claim>> buckets = new HashMap<>();
            for (Map.Entry<Long, List<Claim>> bucketEntry : worldEntry.getValue().entrySet()) {
                List<Claim> candidates = bucketEntry.getValue();
                candidates.sort(Comparator.comparingLong(Claim::area));
                buckets.put(bucketEntry.getKey(), List.copyOf(candidates));
            }
            finalized.put(worldEntry.getKey(), Map.copyOf(buckets));
        }
        return Map.copyOf(finalized);
    }

    static List<Claim> candidates(Map<String, Map<Long, List<Claim>>> index, Location location) {
        if (location == null || location.getWorld() == null) {
            return List.of();
        }
        Map<Long, List<Claim>> worldIndex = index.get(location.getWorld().getName());
        if (worldIndex == null || worldIndex.isEmpty()) {
            return List.of();
        }
        List<Claim> candidates = worldIndex.get(chunkKey(location.getBlockX() >> 4, location.getBlockZ() >> 4));
        if (candidates == null || candidates.isEmpty()) {
            return List.of();
        }
        return candidates;
    }

    private static long chunkKey(int chunkX, int chunkZ) {
        return (((long) chunkX) << 32) ^ (chunkZ & 0xffffffffL);
    }
}
