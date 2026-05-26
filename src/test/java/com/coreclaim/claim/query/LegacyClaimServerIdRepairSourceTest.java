package com.coreclaim.claim.query;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class LegacyClaimServerIdRepairSourceTest {

    @Test
    void reloadClaimsRepairsOrWarnsBeforeLoadingClaimsAndRebuildingIndex() throws IOException {
        String source = Files.readString(Path.of("src/main/java/com/coreclaim/claim/query/ClaimLookupService.java"));
        String reloadClaims = source.substring(
            source.indexOf("public int reloadClaims()"),
            source.indexOf("public boolean isClaimNameTaken")
        );

        int repairIndex = reloadClaims.indexOf("inspectAndRepairMissingServerIds");
        int loadIndex = reloadClaims.indexOf("loadClaimsFromDatabase");
        int rebuildIndex = reloadClaims.indexOf("rebuildClaimChunkIndex");

        assertTrue(repairIndex > 0);
        assertTrue(loadIndex > repairIndex);
        assertTrue(rebuildIndex > loadIndex);
    }

    @Test
    void mysqlMissingServerIdLogsSevereAndSqlRepairExample() throws IOException {
        String source = Files.readString(Path.of("src/main/java/com/coreclaim/claim/query/ClaimLookupService.java"));

        assertTrue(source.contains("logger.severe(\"Detected \" + report.missingCount()"));
        assertTrue(source.contains("SQL example: UPDATE claims SET server_id = '"));
        assertTrue(source.contains("will not enter this server's protection index"));
    }
}
