package com.coreclaim.claim.query;

import com.coreclaim.model.Claim;
import java.util.Collection;
import java.util.function.Predicate;

final class ClaimSpatialQuery {

    private ClaimSpatialQuery() {
    }

    static boolean overlaps(
        Collection<Claim> claims,
        Predicate<Claim> localFilter,
        String world,
        int minX,
        int maxX,
        int minY,
        int maxY,
        int minZ,
        int maxZ,
        Integer ignoredId,
        boolean fullHeight
    ) {
        for (Claim claim : claims) {
            if (!includeClaim(claim, localFilter, null)) {
                continue;
            }
            if (claim.overlaps(world, minX, maxX, minY, maxY, minZ, maxZ, ignoredId, fullHeight)) {
                return true;
            }
        }
        return false;
    }

    static boolean hasCoreWithinSpacing(
        Collection<Claim> claims,
        Predicate<Claim> localFilter,
        String world,
        int centerX,
        int centerZ,
        int spacing,
        Integer ignoredId
    ) {
        for (Claim claim : claims) {
            if (!includeClaim(claim, localFilter, null)) {
                continue;
            }
            if (!claim.world().equals(world)) {
                continue;
            }
            if (ignoredId != null && ignoredId == claim.id()) {
                continue;
            }
            if (Math.abs(claim.centerX() - centerX) < spacing && Math.abs(claim.centerZ() - centerZ) < spacing) {
                return true;
            }
        }
        return false;
    }

    static boolean hasClaimWithinGap(
        Collection<Claim> claims,
        Predicate<Claim> localFilter,
        String world,
        int minX,
        int maxX,
        int minY,
        int maxY,
        int minZ,
        int maxZ,
        int gap,
        Integer ignoredId,
        boolean fullHeight,
        Predicate<Claim> filter
    ) {
        int expandedMinX = minX - Math.max(0, gap);
        int expandedMaxX = maxX + Math.max(0, gap);
        int expandedMinZ = minZ - Math.max(0, gap);
        int expandedMaxZ = maxZ + Math.max(0, gap);
        for (Claim claim : claims) {
            if (!includeClaim(claim, localFilter, filter)) {
                continue;
            }
            if (claim.overlaps(world, expandedMinX, expandedMaxX, minY, maxY, expandedMinZ, expandedMaxZ, ignoredId, fullHeight)) {
                return true;
            }
        }
        return false;
    }

    private static boolean includeClaim(Claim claim, Predicate<Claim> localFilter, Predicate<Claim> extraFilter) {
        if (localFilter != null && !localFilter.test(claim)) {
            return false;
        }
        return extraFilter == null || extraFilter.test(claim);
    }
}
