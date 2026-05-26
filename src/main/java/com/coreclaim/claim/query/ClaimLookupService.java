package com.coreclaim.claim.query;

import com.coreclaim.model.Claim;
import com.coreclaim.service.ClaimService;
import com.coreclaim.claim.persistence.LegacyClaimServerIdRepairReport;
import com.coreclaim.claim.persistence.ClaimPersistenceRepository;
import com.coreclaim.claim.ClaimRuntime;
import java.util.logging.Logger;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;
import org.bukkit.Location;
import org.bukkit.Bukkit;

public final class ClaimLookupService {

    private final ClaimRuntime runtime;
    private final ClaimPersistenceRepository persistenceRepository;

    public ClaimLookupService(ClaimRuntime runtime, ClaimPersistenceRepository persistenceRepository) {
        this.runtime = runtime;
        this.persistenceRepository = persistenceRepository;
    }

    public String currentServerId() {
        return runtime.plugin().settings().serverId();
    }

    public String effectiveServerId(Claim claim) {
        if (claim == null) {
            return null;
        }
        return effectiveServerId(claim.serverId());
    }

    public String displayServerId(Claim claim) {
        String effectiveServerId = effectiveServerId(claim);
        return effectiveServerId == null || effectiveServerId.isBlank() ? "unknown" : effectiveServerId;
    }

    public boolean isLocalClaim(Claim claim) {
        String effectiveServerId = effectiveServerId(claim);
        return effectiveServerId != null && runtime.plugin().settings().isCurrentServer(effectiveServerId);
    }

    public ClaimIndexExplanation explainClaimIndexState(int claimId) {
        return explainClaimIndexState(claimId, null);
    }

    public ClaimIndexExplanation explainClaimIndexState(int claimId, Location currentLocation) {
        Claim claim = runtime.claims().get(claimId);
        if (claim == null) {
            return new ClaimIndexExplanation(
                claimId,
                "",
                "",
                null,
                currentServerId(),
                runtime.databaseManager() != null && runtime.databaseManager().isMySql(),
                false,
                false,
                false,
                currentLocation != null,
                false,
                null,
                "Claim is not loaded in memory. Use /claim reload, then check database rows for this id."
            );
        }
        String effectiveServerId = effectiveServerId(claim);
        boolean mysql = runtime.databaseManager() != null && runtime.databaseManager().isMySql();
        boolean localClaim = isLocalClaim(claim);
        boolean worldLoaded = Bukkit.getWorld(claim.world()) != null;
        boolean indexed = ClaimChunkIndex.containsClaim(runtime.claimChunkIndex(), claim);
        Optional<Claim> locationHit = currentLocation == null ? Optional.empty() : findClaim(currentLocation);
        return new ClaimIndexExplanation(
            claim.id(),
            claim.world(),
            claim.serverId(),
            effectiveServerId,
            currentServerId(),
            mysql,
            localClaim,
            worldLoaded,
            indexed,
            currentLocation != null,
            locationHit.map(hit -> hit.id() == claim.id()).orElse(false),
            locationHit.map(Claim::id).orElse(null),
            repairSuggestion(claim, effectiveServerId, localClaim, indexed, worldLoaded, mysql)
        );
    }

    public boolean countsTowardQuota(Claim claim) {
        return claim != null && !claim.systemManaged();
    }

    public String ruleProfileName(Claim claim) {
        return claim != null && claim.systemManaged() ? "系统公共规则" : "新领地默认规则";
    }

    public Optional<Claim> findClaim(Location location) {
        for (Claim claim : claimCandidates(location)) {
            if (claim.contains(location)) {
                return Optional.of(claim);
            }
        }
        return Optional.empty();
    }

    public Optional<Claim> findPlayerPresenceClaim(Location location) {
        List<Claim> candidates = claimCandidates(location);
        for (Claim claim : candidates) {
            if (claim.contains(location)) {
                return Optional.of(claim);
            }
        }

        Claim horizontalMatch = null;
        for (Claim claim : candidates) {
            if (claim.fullHeight() || !claim.containsHorizontally(location)) {
                continue;
            }
            if (horizontalMatch != null) {
                return Optional.empty();
            }
            horizontalMatch = claim;
        }
        return Optional.ofNullable(horizontalMatch);
    }

    public boolean hasClaimCandidateAt(Location location) {
        return ClaimChunkIndex.hasCandidates(runtime.claimChunkIndex(), location);
    }

    public boolean hasClaimCandidateAt(String worldName, int blockX, int blockZ) {
        return ClaimChunkIndex.hasCandidates(runtime.claimChunkIndex(), worldName, blockX, blockZ);
    }

    public Optional<Claim> findClaimById(int id) {
        return Optional.ofNullable(runtime.claims().get(id));
    }

    public Optional<Claim> findClaimByIdOrLoad(int id) {
        Claim claim = runtime.claims().get(id);
        if (claim != null) {
            return Optional.of(claim);
        }
        return refreshClaimFromDatabase(id);
    }

    public Optional<Claim> findClaimByIdFresh(int id) {
        if (!runtime.databaseManager().isMySql()) {
            return findClaimByIdOrLoad(id);
        }
        reloadClaim(id);
        return findClaimById(id);
    }

    public Optional<Claim> refreshClaimFromDatabase(int id) {
        ClaimService.ClaimRefreshResult refreshed = reloadClaim(id);
        return refreshed.currentClaim() == null ? Optional.empty() : findClaimById(id);
    }

    public ClaimService.ClaimRefreshResult reloadClaim(int id) {
        synchronized (runtime.mutationLock()) {
            Optional<Claim> loadedClaim = persistenceRepository.loadClaimFromDatabase(id);
            Claim previousClaim = runtime.claims().get(id);
            Claim previousSnapshot = persistenceRepository.snapshotClaim(previousClaim);
            if (loadedClaim.isPresent()) {
                runtime.claims().put(id, loadedClaim.get());
                rebuildClaimChunkIndex();
                return new ClaimService.ClaimRefreshResult(previousSnapshot, persistenceRepository.snapshotClaim(loadedClaim.get()));
            }
            if (previousClaim != null) {
                runtime.claims().remove(id);
                rebuildClaimChunkIndex();
            }
            return new ClaimService.ClaimRefreshResult(previousSnapshot, null);
        }
    }

    public List<Claim> claimsOf(UUID owner, boolean includeSystem) {
        List<Claim> result = new ArrayList<>();
        for (Claim claim : runtime.claims().values()) {
            if (claim.owner().equals(owner) && (includeSystem || countsTowardQuota(claim))) {
                result.add(claim);
            }
        }
        result.sort(Comparator.comparingInt(Claim::id));
        return result;
    }

    public List<Claim> claimsOfFresh(UUID owner, boolean includeSystem) {
        if (!runtime.databaseManager().isMySql()) {
            return claimsOf(owner, includeSystem);
        }
        List<Integer> freshIds = runtime.databaseManager().query(
            includeSystem
                ? "SELECT id FROM claims WHERE owner_uuid = ? ORDER BY id"
                : "SELECT id FROM claims WHERE owner_uuid = ? AND system_managed = 0 ORDER BY id",
            statement -> statement.setString(1, owner.toString()),
            resultSet -> {
                List<Integer> ids = new ArrayList<>();
                while (resultSet.next()) {
                    ids.add(resultSet.getInt("id"));
                }
                return ids;
            }
        );
        List<Claim> refreshedClaims = new ArrayList<>();
        for (int claimId : freshIds) {
            findClaimByIdFresh(claimId).ifPresent(refreshedClaims::add);
        }
        Set<Integer> idSet = Set.copyOf(freshIds);
        for (Claim cachedClaim : new ArrayList<>(runtime.claims().values())) {
            if (owner.equals(cachedClaim.owner())
                && (includeSystem || countsTowardQuota(cachedClaim))
                && !idSet.contains(cachedClaim.id())) {
                reloadClaim(cachedClaim.id());
            }
        }
        refreshedClaims.sort(Comparator.comparingInt(Claim::id));
        return refreshedClaims;
    }

    public List<ClaimService.ClaimListEntry> visibleClaimsOf(UUID playerId, boolean includeSystem) {
        if (playerId == null) {
            return List.of();
        }
        Map<Integer, ClaimService.ClaimListEntry> entries = new HashMap<>();
        for (Claim claim : runtime.claims().values()) {
            toClaimListEntry(claim, playerId, includeSystem)
                .ifPresent(entry -> entries.put(entry.claim().id(), entry));
        }
        return sortClaimListEntries(new ArrayList<>(entries.values()));
    }

    public List<ClaimService.ClaimListEntry> visibleClaimsOfFresh(UUID playerId, boolean includeSystem) {
        if (playerId == null) {
            return List.of();
        }
        if (!runtime.databaseManager().isMySql()) {
            return visibleClaimsOf(playerId, includeSystem);
        }
        String ownerFilter = includeSystem ? "" : " AND system_managed = 0";
        String trustedFilter = includeSystem ? "" : " AND c.system_managed = 0";
        List<ClaimListRow> rows = runtime.databaseManager().query(
            """
            SELECT id, relation_order FROM (
                SELECT id, 0 AS relation_order
                FROM claims
                WHERE owner_uuid = ?%s
                UNION ALL
                SELECT c.id, 1 AS relation_order
                FROM claims c
                INNER JOIN claim_members m ON c.id = m.claim_id
                WHERE m.player_uuid = ? AND c.owner_uuid <> ?%s
            ) visible_claims
            ORDER BY relation_order, id
            """.formatted(ownerFilter, trustedFilter),
            statement -> {
                statement.setString(1, playerId.toString());
                statement.setString(2, playerId.toString());
                statement.setString(3, playerId.toString());
            },
            resultSet -> {
                List<ClaimListRow> entries = new ArrayList<>();
                while (resultSet.next()) {
                    entries.add(new ClaimListRow(
                        resultSet.getInt("id"),
                        resultSet.getInt("relation_order") == 0 ? ClaimService.ClaimListRelation.OWNER : ClaimService.ClaimListRelation.TRUSTED_MEMBER
                    ));
                }
                return entries;
            }
        );
        Map<Integer, ClaimService.ClaimListEntry> refreshedEntries = new HashMap<>();
        for (ClaimListRow row : rows) {
            findClaimByIdFresh(row.claimId())
                .flatMap(claim -> toClaimListEntry(claim, playerId, includeSystem))
                .ifPresent(entry -> refreshedEntries.put(entry.claim().id(), entry));
        }
        return sortClaimListEntries(new ArrayList<>(refreshedEntries.values()));
    }

    public Optional<ClaimService.ClaimListEntry> visibleClaimEntryFresh(UUID playerId, int claimId, boolean includeSystem) {
        if (playerId == null || claimId <= 0) {
            return Optional.empty();
        }
        return findClaimByIdFresh(claimId)
            .flatMap(claim -> toClaimListEntry(claim, playerId, includeSystem));
    }

    public List<Claim> allClaims() {
        return new ArrayList<>(runtime.claims().values());
    }

    public List<Claim> findClaimsByName(String rawName) {
        String normalizedName = ClaimNameNormalizer.normalize(rawName);
        if (normalizedName == null) {
            return List.of();
        }
        return runtime.claims().values().stream()
            .filter(claim -> normalizedName.equals(ClaimNameNormalizer.normalize(claim.name())))
            .sorted(Comparator.comparingInt(Claim::id))
            .toList();
    }

    public List<Claim> findClaimsByNameFresh(String rawName) {
        String normalizedName = ClaimNameNormalizer.normalize(rawName);
        if (normalizedName == null || !runtime.databaseManager().isMySql()) {
            return findClaimsByName(rawName);
        }
        List<Integer> freshIds = runtime.databaseManager().query(
            "SELECT id FROM claims WHERE name_key = ? ORDER BY id",
            statement -> statement.setString(1, normalizedName),
            resultSet -> {
                List<Integer> ids = new ArrayList<>();
                while (resultSet.next()) {
                    ids.add(resultSet.getInt("id"));
                }
                return ids;
            }
        );
        List<Claim> refreshedClaims = new ArrayList<>();
        for (int claimId : freshIds) {
            findClaimByIdFresh(claimId).ifPresent(refreshedClaims::add);
        }
        Set<Integer> idSet = Set.copyOf(freshIds);
        for (Claim cachedClaim : new ArrayList<>(runtime.claims().values())) {
            if (normalizedName.equals(ClaimNameNormalizer.normalize(cachedClaim.name())) && !idSet.contains(cachedClaim.id())) {
                reloadClaim(cachedClaim.id());
            }
        }
        refreshedClaims.sort(Comparator.comparingInt(Claim::id));
        return refreshedClaims;
    }

    public int reloadClaims() {
        synchronized (runtime.mutationLock()) {
            persistenceRepository.backfillMissingServerIds(currentServerId());
            LegacyClaimServerIdRepairReport repairReport = persistenceRepository.inspectAndRepairMissingServerIds(
                runtime.plugin().settings().legacyClaimServerIdRepair()
            );
            logLegacyServerIdRepairReport(repairReport);
            Map<Integer, Claim> loadedClaims = persistenceRepository.loadClaimsFromDatabase();
            renameDuplicateLoadedClaims(loadedClaims);
            runtime.claims().clear();
            runtime.claims().putAll(loadedClaims);
            rebuildClaimChunkIndex();
            return runtime.claims().size();
        }
    }

    public boolean isClaimNameTaken(String rawName, Integer excludedClaimId) {
        String normalizedName = ClaimNameNormalizer.normalize(rawName);
        if (normalizedName == null) {
            return false;
        }
        if (runtime.databaseManager() != null && runtime.databaseManager().isMySql()) {
            return runtime.databaseManager().query(
                excludedClaimId == null
                    ? "SELECT id FROM claims WHERE name_key = ? LIMIT 1"
                    : "SELECT id FROM claims WHERE name_key = ? AND id <> ? LIMIT 1",
                statement -> {
                    statement.setString(1, normalizedName);
                    if (excludedClaimId != null) {
                        statement.setInt(2, excludedClaimId);
                    }
                },
                java.sql.ResultSet::next
            );
        }
        for (Claim claim : runtime.claims().values()) {
            if (excludedClaimId != null && excludedClaimId == claim.id()) {
                continue;
            }
            if (normalizedName.equals(ClaimNameNormalizer.normalize(claim.name()))) {
                return true;
            }
        }
        return false;
    }

    public String validateAvailableClaimName(String rawName, Integer excludedClaimId) {
        String sanitizedName = ClaimNameNormalizer.sanitize(rawName);
        String normalizedName = ClaimNameNormalizer.normalize(sanitizedName);
        if (normalizedName == null) {
            throw new IllegalArgumentException("claim-name-empty");
        }
        if (isClaimNameTaken(normalizedName, excludedClaimId)) {
            throw new IllegalArgumentException("claim-name-exists");
        }
        return sanitizedName;
    }

    public String nextAvailableLegacyName(String rawName, Set<String> reservedNames) {
        String sanitizedName = ClaimNameNormalizer.sanitize(rawName);
        String normalizedName = ClaimNameNormalizer.normalize(sanitizedName);
        if (normalizedName == null) {
            throw new IllegalArgumentException("claim-name-empty");
        }
        Set<String> reserved = reservedNames == null ? Set.of() : reservedNames;
        for (int suffix = 2; suffix < 10000; suffix++) {
            String candidateName = sanitizedName + suffix;
            String normalizedCandidate = ClaimNameNormalizer.normalize(candidateName);
            if (!reserved.contains(normalizedCandidate) && !isClaimNameTaken(candidateName, null)) {
                return candidateName;
            }
        }
        throw new IllegalArgumentException("claim-name-exists");
    }

    private void renameDuplicateLoadedClaims(Map<Integer, Claim> loadedClaims) {
        Map<String, List<Claim>> claimsByName = new HashMap<>();
        for (Claim claim : loadedClaims.values()) {
            String normalizedName = ClaimNameNormalizer.normalize(claim.name());
            if (normalizedName == null) {
                continue;
            }
            claimsByName.computeIfAbsent(normalizedName, ignored -> new ArrayList<>()).add(claim);
        }
        Set<String> reservedNames = new java.util.HashSet<>(claimsByName.keySet());
        int renamed = 0;
        for (List<Claim> duplicateClaims : claimsByName.values()) {
            if (duplicateClaims.size() <= 1) {
                continue;
            }
            duplicateClaims.sort(Comparator.comparingInt(Claim::id));
            for (int index = 1; index < duplicateClaims.size(); index++) {
                Claim duplicateClaim = duplicateClaims.get(index);
                String newName = nextAvailableLegacyName(duplicateClaims.get(0).name(), reservedNames);
                reservedNames.add(ClaimNameNormalizer.normalize(newName));
                duplicateClaim.setName(newName);
                persistenceRepository.updateClaimName(duplicateClaim.id(), newName);
                renamed++;
            }
        }
        if (renamed > 0 && runtime.plugin() != null) {
            runtime.plugin().getLogger().info("Renamed " + renamed + " duplicate legacy claim names.");
        }
    }

    public boolean overlaps(String world, int minX, int maxX, int minY, int maxY, int minZ, int maxZ, Integer ignoredId, boolean fullHeight) {
        return ClaimSpatialQuery.overlaps(runtime.claims().values(), this::isLocalClaim, world, minX, maxX, minY, maxY, minZ, maxZ, ignoredId, fullHeight);
    }

    public boolean hasCoreWithinSpacing(String world, int centerX, int centerZ, int spacing, Integer ignoredId) {
        return ClaimSpatialQuery.hasCoreWithinSpacing(runtime.claims().values(), this::isLocalClaim, world, centerX, centerZ, spacing, ignoredId);
    }

    public boolean hasClaimWithinGap(
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
        return ClaimSpatialQuery.hasClaimWithinGap(runtime.claims().values(), this::isLocalClaim, world, minX, maxX, minY, maxY, minZ, maxZ, gap, ignoredId, fullHeight, filter);
    }

    public void rebuildClaimChunkIndex() {
        runtime.setClaimChunkIndex(ClaimChunkIndex.rebuild(runtime.claims().values(), this::isLocalClaim));
    }

    static String resolveEffectiveServerId(String explicitServerId, boolean mysql, String currentServerId) {
        if (explicitServerId != null && !explicitServerId.isBlank()) {
            return explicitServerId.trim();
        }
        return mysql ? null : currentServerId;
    }

    private String effectiveServerId(String explicitServerId) {
        return resolveEffectiveServerId(
            explicitServerId,
            runtime.databaseManager() != null && runtime.databaseManager().isMySql(),
            currentServerId()
        );
    }

    private List<Claim> claimCandidates(Location location) {
        return ClaimChunkIndex.candidates(runtime.claimChunkIndex(), location);
    }

    private void logLegacyServerIdRepairReport(LegacyClaimServerIdRepairReport report) {
        if (report == null || !report.mysql() || !report.hasMissingRows() || runtime.plugin() == null) {
            return;
        }
        Logger logger = runtime.plugin().getLogger();
        String worlds = report.worlds().isEmpty() ? "<none>" : String.join(", ", report.worlds());
        if (report.repairEnabled()) {
            if (report.repairedCount() > 0) {
                logger.warning("Legacy claims.server_id repair updated " + report.repairedCount()
                    + " old MySQL claim(s); unrepaired=" + report.unrepairedCount()
                    + "; worlds=[" + worlds + "]. Claim cache and chunk index are being rebuilt.");
            }
            if (report.unrepairedCount() > 0) {
                logger.severe("Legacy claims.server_id repair left " + report.unrepairedCount()
                    + " old MySQL claim(s) unrepaired because no world-map/default-server-id matched. These claims will not enter this server's protection index. Worlds=["
                    + worlds + "]. Configure legacy-claim-server-id-repair.world-map or default-server-id, then run /claim reload.");
                logLegacyServerIdSqlExamples(logger, worlds);
            }
            return;
        }
        logger.severe("Detected " + report.missingCount()
            + " MySQL claim(s) with empty claims.server_id. They cannot be assigned to a backend server and will not enter this server's protection index. Worlds=["
            + worlds + "].");
        logger.warning("Enable legacy-claim-server-id-repair in config.yml or repair the database manually, then run /claim reload.");
        logLegacyServerIdSqlExamples(logger, worlds);
    }

    private void logLegacyServerIdSqlExamples(Logger logger, String worlds) {
        String current = currentServerId().replace("'", "''");
        logger.warning("SQL example: UPDATE claims SET server_id = '" + current + "' WHERE server_id IS NULL OR TRIM(server_id) = '';");
        logger.warning("World-specific SQL example: UPDATE claims SET server_id = '" + current + "' WHERE (server_id IS NULL OR TRIM(server_id) = '') AND world = '<world-name>'; Worlds detected: " + worlds);
    }

    private String repairSuggestion(Claim claim, String effectiveServerId, boolean localClaim, boolean indexed, boolean worldLoaded, boolean mysql) {
        return indexRepairSuggestion(claim, effectiveServerId, localClaim, indexed, worldLoaded, mysql, currentServerId());
    }

    static String indexRepairSuggestion(
        Claim claim,
        String effectiveServerId,
        boolean localClaim,
        boolean indexed,
        boolean worldLoaded,
        boolean mysql,
        String currentServerId
    ) {
        if (!mysql) {
            return indexed ? "SQLite claim is indexed locally." : "Run /claim reload to rebuild the local chunk index.";
        }
        if (claim.serverId() == null || claim.serverId().isBlank()) {
            return "server_id is empty in MySQL. Use /claim admin setserver " + claim.id() + " " + currentServerId
                + " or enable legacy-claim-server-id-repair, then run /claim reload.";
        }
        if (effectiveServerId == null || effectiveServerId.isBlank()) {
            return "No effective server id could be resolved. Check claims.server_id and server-id in config.yml.";
        }
        if (!localClaim) {
            return "Claim belongs to server_id '" + effectiveServerId + "'. This server protects only '" + currentServerId + "'. Use /claim admin setserver if this is wrong.";
        }
        if (!worldLoaded) {
            return "Claim server_id is local, but world '" + claim.world() + "' is not loaded on this server.";
        }
        if (!indexed) {
            return "Claim is local but absent from chunk index. Run /claim reload; if it remains absent, inspect claim bounds/world.";
        }
        return "Claim is local and present in the protection chunk index.";
    }

    private Optional<ClaimService.ClaimListEntry> toClaimListEntry(Claim claim, UUID playerId, boolean includeSystem) {
        if (claim == null || playerId == null) {
            return Optional.empty();
        }
        if (!includeSystem && !countsTowardQuota(claim)) {
            return Optional.empty();
        }
        if (claim.owner().equals(playerId)) {
            return Optional.of(new ClaimService.ClaimListEntry(claim, ClaimService.ClaimListRelation.OWNER));
        }
        if (claim.isDenied(playerId)) {
            return Optional.empty();
        }
        if (claim.isTrusted(playerId)) {
            return Optional.of(new ClaimService.ClaimListEntry(claim, ClaimService.ClaimListRelation.TRUSTED_MEMBER));
        }
        return Optional.empty();
    }

    private List<ClaimService.ClaimListEntry> sortClaimListEntries(List<ClaimService.ClaimListEntry> entries) {
        entries.sort(Comparator
            .comparingInt((ClaimService.ClaimListEntry entry) -> entry.relation() == ClaimService.ClaimListRelation.OWNER ? 0 : 1)
            .thenComparingInt(entry -> entry.claim().id()));
        return entries;
    }

    private record ClaimListRow(int claimId, ClaimService.ClaimListRelation relation) {
    }
}
