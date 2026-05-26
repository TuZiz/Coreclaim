package com.coreclaim.claim.query;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.coreclaim.claim.ClaimRuntime;
import com.coreclaim.model.Claim;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ClaimLookupServiceTest {

    @Test
    void newClaimsStillRejectDuplicateNames() {
        Map<Integer, Claim> claims = new HashMap<>();
        claims.put(1, claim(1, "基地"));
        ClaimLookupService lookupService = new ClaimLookupService(
            new ClaimRuntime(null, null, null, claims, new Object()),
            null
        );

        org.junit.jupiter.api.Assertions.assertThrows(
            IllegalArgumentException.class,
            () -> lookupService.validateAvailableClaimName("基地", null)
        );
    }

    @Test
    void legacyDuplicateClaimNamesReceiveNumericSuffix() {
        Map<Integer, Claim> claims = new HashMap<>();
        claims.put(1, claim(1, "基地"));
        claims.put(2, claim(2, "基地2"));
        ClaimLookupService lookupService = new ClaimLookupService(
            new ClaimRuntime(null, null, null, claims, new Object()),
            null
        );

        assertEquals("基地3", lookupService.nextAvailableLegacyName("基地", java.util.Set.of("基地", "基地2")));
    }

    @Test
    void renameCanKeepCurrentClaimName() {
        Map<Integer, Claim> claims = new HashMap<>();
        claims.put(1, claim(1, "基地"));
        ClaimLookupService lookupService = new ClaimLookupService(
            new ClaimRuntime(null, null, null, claims, new Object()),
            null
        );

        assertEquals("基地", lookupService.validateAvailableClaimName("基地", 1));
    }

    @Test
    void hasClaimCandidateAtUsesChunkIndexWithoutPreciseContainment() {
        Map<Integer, Claim> claims = new HashMap<>();
        claims.put(1, claim(1, "基地"));
        ClaimRuntime runtime = new ClaimRuntime(null, null, null, claims, new Object());
        runtime.setClaimChunkIndex(ClaimChunkIndex.rebuild(claims.values(), ignored -> true));
        ClaimLookupService lookupService = new ClaimLookupService(runtime, null);

        assertTrue(lookupService.hasClaimCandidateAt("world", 0, 0));
        assertFalse(lookupService.hasClaimCandidateAt("world", 1000, 1000));
        assertFalse(lookupService.hasClaimCandidateAt("other", 0, 0));
    }

    @Test
    void sqliteMissingServerIdFallsBackToCurrentServerAndIndexes() {
        Claim claim = claim(1, "旧领地", "");
        Map<Integer, Claim> claims = Map.of(1, claim);
        Map<String, Map<Long, java.util.List<Claim>>> index = ClaimChunkIndex.rebuild(
            claims.values(),
            candidate -> "local".equals(ClaimLookupService.resolveEffectiveServerId(candidate.serverId(), false, "local"))
        );

        assertEquals("local", ClaimLookupService.resolveEffectiveServerId("", false, "local"));
        assertTrue(ClaimChunkIndex.containsClaim(index, claim));
    }

    @Test
    void mysqlMissingServerIdIsNotSilentlyLocalAndStaysOutOfIndex() {
        Claim claim = claim(1, "旧领地", "");
        Map<Integer, Claim> claims = Map.of(1, claim);
        Map<String, Map<Long, java.util.List<Claim>>> index = ClaimChunkIndex.rebuild(
            claims.values(),
            candidate -> "local".equals(ClaimLookupService.resolveEffectiveServerId(candidate.serverId(), true, "local"))
        );

        assertEquals(null, ClaimLookupService.resolveEffectiveServerId("", true, "local"));
        assertFalse(ClaimChunkIndex.containsClaim(index, claim));
    }

    @Test
    void mysqlDefaultServerIdRepairTargetLetsLegacyClaimEnterIndex() {
        com.coreclaim.config.LegacyClaimServerIdRepairSettings settings =
            new com.coreclaim.config.LegacyClaimServerIdRepairSettings(true, "local", Map.of());
        Claim repairedClaim = claim(1, "旧领地", settings.targetServerId("world"));
        Map<String, Map<Long, java.util.List<Claim>>> index = ClaimChunkIndex.rebuild(
            java.util.List.of(repairedClaim),
            candidate -> "local".equals(ClaimLookupService.resolveEffectiveServerId(candidate.serverId(), true, "local"))
        );

        assertEquals("local", settings.targetServerId("world"));
        assertTrue(ClaimChunkIndex.containsClaim(index, repairedClaim));
    }

    @Test
    void worldMismatchDiagnosisSuggestsLoadedWorldProblem() {
        Claim claim = claim(1, "旧领地", "local");

        String suggestion = ClaimLookupService.indexRepairSuggestion(
            claim,
            "local",
            true,
            false,
            false,
            true,
            "local"
        );

        assertTrue(suggestion.contains("world"));
        assertTrue(suggestion.contains("not loaded"));
    }

    private Claim claim(int id, String name) {
        return claim(id, name, "local");
    }

    private Claim claim(int id, String name, String serverId) {
        return new Claim(
            id,
            UUID.randomUUID(),
            "owner",
            name,
            serverId,
            "world",
            0,
            64,
            0,
            -64,
            319,
            true,
            1,
            1,
            1,
            1,
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
