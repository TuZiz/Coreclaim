package com.coreclaim.service;

import static com.coreclaim.service.ClaimCleanupStateSupport.clearGrace;
import static com.coreclaim.service.ClaimCleanupStateSupport.hasGrace;
import static com.coreclaim.service.ClaimCleanupStateSupport.reasonFromState;

import com.coreclaim.CoreClaimPlugin;
import com.coreclaim.model.Claim;
import com.coreclaim.model.ClaimCleanupReason;
import com.coreclaim.model.ClaimCleanupState;
import com.coreclaim.model.PlayerProfile;
import com.coreclaim.platform.PlatformScheduler;
import com.coreclaim.storage.DatabaseManager;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class ClaimCleanupService {

    private final CoreClaimPlugin plugin;
    private final ClaimService claimService;
    private final ProfileService profileService;
    private final HologramService hologramService;
    private final PlatformScheduler platformScheduler;
    private final ClaimCleanupStateRepository stateRepository;
    private final Map<Integer, ClaimCleanupState> states = new ConcurrentHashMap<>();
    private PlatformScheduler.TaskHandle scanTask;

    public ClaimCleanupService(
        CoreClaimPlugin plugin,
        DatabaseManager databaseManager,
        ClaimService claimService,
        ProfileService profileService,
        HologramService hologramService,
        PlatformScheduler platformScheduler
    ) {
        this.plugin = plugin;
        this.claimService = claimService;
        this.profileService = profileService;
        this.hologramService = hologramService;
        this.platformScheduler = platformScheduler;
        this.stateRepository = new ClaimCleanupStateRepository(databaseManager);
        reload();
    }

    public void start() {
        stop();
        reschedule();
    }

    public void stop() {
        if (scanTask != null) {
            scanTask.cancel();
            scanTask = null;
        }
    }

    public void reload() {
        stop();
        loadStates();
        ensureTrackedClaims();
        reschedule();
    }

    public void trackNewClaim(Claim claim) {
        if (claim == null) {
            return;
        }
        ClaimCleanupState state = states.compute(claim.id(), (claimId, existing) -> {
            ClaimCleanupState next = existing == null ? new ClaimCleanupState(claimId) : existing;
            next.setLegacyUnknown(false);
            next.setSkipCleanup(false);
            next.setHasBuildEvidence(false);
            next.setHasInteractionEvidence(false);
            clearGrace(next);
            next.setLastReason(ClaimCleanupReason.NONE.key());
            return next;
        });
        stateRepository.persistState(state);
    }

    public void removeTracking(int claimId) {
        states.remove(claimId);
    }

    public void recordBuildActivity(Claim claim, UUID actorId) {
        if (!canRecordEvidence(claim, actorId)) {
            return;
        }
        updateEvidence(claim, true, true);
    }

    public void recordInteractionActivity(Claim claim, UUID actorId) {
        if (!canRecordEvidence(claim, actorId)) {
            return;
        }
        updateEvidence(claim, false, true);
    }

    public void revokeGraceForOwner(UUID ownerId) {
        if (ownerId == null) {
            return;
        }
        for (Claim claim : claimService.allClaims()) {
            if (!claim.owner().equals(ownerId)) {
                continue;
            }
            ClaimCleanupState state = states.get(claim.id());
            if (state == null || (state.getGraceMarkedAt() <= 0L && state.getDeleteAfterAt() <= 0L)) {
                continue;
            }
            clearGrace(state);
            state.setLastReason(reasonFromState(state).key());
            stateRepository.persistState(state);
        }
    }

    public CleanupRunResult runScanNow() {
        long now = System.currentTimeMillis();
        int scanned = 0;
        int marked = 0;
        int deleted = 0;
        int revoked = 0;

        for (Claim claim : claimService.allClaims()) {
            if (!claimService.isLocalClaim(claim)) {
                continue;
            }
            scanned++;
            Evaluation evaluation = evaluate(claim, now, true);
            if (evaluation.state().isLegacyUnknown()) {
                continue;
            }
            if (evaluation.shouldSkip()) {
                if (hasGrace(evaluation.state())) {
                    clearGrace(evaluation.state());
                    evaluation.state().setLastReason(reasonFromState(evaluation.state()).key());
                    stateRepository.persistState(evaluation.state());
                    revoked++;
                }
                continue;
            }

            if (!evaluation.eligible()) {
                if (hasGrace(evaluation.state())) {
                    clearGrace(evaluation.state());
                    evaluation.state().setLastReason(evaluation.reason().key());
                    stateRepository.persistState(evaluation.state());
                    revoked++;
                }
                continue;
            }

            if (!hasGrace(evaluation.state())) {
                long deleteAfterAt = now + gracePeriodMillis();
                evaluation.state().setGraceMarkedAt(now);
                evaluation.state().setDeleteAfterAt(deleteAfterAt);
                evaluation.state().setLastReason(evaluation.reason().key());
                stateRepository.persistState(evaluation.state());
                marked++;
                continue;
            }

            evaluation.state().setLastReason(evaluation.reason().key());
            stateRepository.persistState(evaluation.state());
            if (evaluation.state().getDeleteAfterAt() > now) {
                continue;
            }

            Evaluation rechecked = evaluate(claim, now, false);
            if (!rechecked.eligible() || rechecked.shouldSkip() || rechecked.state().isLegacyUnknown()) {
                clearGrace(rechecked.state());
                rechecked.state().setLastReason(rechecked.reason().key());
                stateRepository.persistState(rechecked.state());
                revoked++;
                continue;
            }

            hologramService.removeClaimHologram(claim.id());
            claimService.removeClaim(claim);
            removeTracking(claim.id());
            deleted++;
        }

        CleanupSnapshot snapshot = snapshot();
        return new CleanupRunResult(scanned, marked, deleted, revoked, snapshot.candidates().size(), snapshot.graceClaims().size());
    }

    public CleanupSnapshot snapshot() {
        long now = System.currentTimeMillis();
        List<CleanupEntry> candidates = new ArrayList<>();
        List<CleanupEntry> graceClaims = new ArrayList<>();
        List<CleanupEntry> legacyClaims = new ArrayList<>();
        for (Claim claim : claimService.allClaims()) {
            if (!claimService.isLocalClaim(claim)) {
                continue;
            }
            Evaluation evaluation = evaluate(claim, now, true);
            CleanupEntry entry = new CleanupEntry(claim, evaluation.state(), evaluation.reason(), evaluation.lastSeenAt());
            if (evaluation.state().isLegacyUnknown()) {
                legacyClaims.add(entry);
                continue;
            }
            if (evaluation.shouldSkip()) {
                continue;
            }
            if (evaluation.eligible()) {
                if (hasGrace(evaluation.state())) {
                    graceClaims.add(entry);
                } else {
                    candidates.add(entry);
                }
            }
        }
        Comparator<CleanupEntry> comparator = Comparator
            .comparingLong(CleanupEntry::lastSeenAt)
            .thenComparingInt(entry -> entry.claim().id());
        candidates.sort(comparator);
        graceClaims.sort(Comparator.<CleanupEntry>comparingLong(
                entry -> entry.state().getDeleteAfterAt() <= 0L ? Long.MAX_VALUE : entry.state().getDeleteAfterAt()
            )
            .thenComparingInt(entry -> entry.claim().id()));
        legacyClaims.sort(Comparator.comparingInt(entry -> entry.claim().id()));
        return new CleanupSnapshot(candidates, graceClaims, legacyClaims);
    }

    public ClaimCleanupState skipClaim(Claim claim) {
        if (claim == null) {
            return null;
        }
        ClaimCleanupState state = stateForClaim(claim, false);
        state.setSkipCleanup(true);
        state.setLegacyUnknown(false);
        clearGrace(state);
        state.setLastReason(ClaimCleanupReason.NONE.key());
        stateRepository.persistState(state);
        return state;
    }

    public ClaimCleanupState baselineClaim(Claim claim, ClaimCleanupBaselineMode mode) {
        if (claim == null || mode == null) {
            return null;
        }
        ClaimCleanupState state = stateForClaim(claim, false);
        state.setLegacyUnknown(false);
        clearGrace(state);
        switch (mode) {
            case EMPTY -> {
                state.setSkipCleanup(false);
                state.setHasBuildEvidence(false);
                state.setHasInteractionEvidence(false);
            }
            case USED -> {
                state.setSkipCleanup(false);
                state.setHasBuildEvidence(true);
                state.setHasInteractionEvidence(true);
            }
            case SKIP -> state.setSkipCleanup(true);
        }
        state.setLastReason(reasonFromState(state).key());
        stateRepository.persistState(state);
        return state;
    }

    private void updateEvidence(Claim claim, boolean buildEvidence, boolean interactionEvidence) {
        if (claim == null) {
            return;
        }
        ClaimCleanupState state = stateForClaim(claim, false);
        boolean changed = false;
        if (buildEvidence && !state.hasBuildEvidence()) {
            state.setHasBuildEvidence(true);
            changed = true;
        }
        if (interactionEvidence && !state.hasInteractionEvidence()) {
            state.setHasInteractionEvidence(true);
            changed = true;
        }
        if (hasGrace(state)) {
            clearGrace(state);
            changed = true;
        }
        ClaimCleanupReason reason = reasonFromState(state);
        if (!reason.key().equals(state.getLastReason())) {
            state.setLastReason(reason.key());
            changed = true;
        }
        if (changed) {
            stateRepository.persistState(state);
        }
    }

    private boolean canRecordEvidence(Claim claim, UUID actorId) {
        if (claim == null || actorId == null) {
            return false;
        }
        if (claim.owner().equals(actorId) || claim.isTrusted(actorId)) {
            return true;
        }
        return profileService.isGloballyTrusted(claim.owner(), actorId);
    }

    private void loadStates() {
        Map<Integer, ClaimCleanupState> loaded = stateRepository.loadStates();
        states.clear();
        states.putAll(loaded);
    }

    private void ensureTrackedClaims() {
        for (Claim claim : claimService.allClaims()) {
            stateForClaim(claim, true);
        }
    }

    private ClaimCleanupState stateForClaim(Claim claim, boolean defaultLegacyUnknown) {
        ClaimCleanupState state = states.computeIfAbsent(claim.id(), claimId -> {
            ClaimCleanupState created = new ClaimCleanupState(claimId);
            created.setLegacyUnknown(defaultLegacyUnknown);
            created.setLastReason(ClaimCleanupReason.NONE.key());
            stateRepository.persistState(created);
            return created;
        });
        if (state.getLastReason() == null) {
            state.setLastReason(ClaimCleanupReason.NONE.key());
            stateRepository.persistState(state);
        }
        return state;
    }

    private void reschedule() {
        if (!plugin.settings().inactiveClaimCleanupEnabled()) {
            return;
        }
        long intervalMinutes = Math.max(1L, plugin.settings().inactiveClaimCleanupScanIntervalMinutes());
        long intervalTicks = intervalMinutes * 60L * 20L;
        scanTask = platformScheduler.runRepeating(this::runScanNow, intervalTicks, intervalTicks);
    }

    private Evaluation evaluate(Claim claim, long now, boolean allowStateCreation) {
        ClaimCleanupState state = allowStateCreation
            ? stateForClaim(claim, true)
            : states.getOrDefault(claim.id(), stateForClaim(claim, true));
        long lastSeenAt = resolveLastSeenAt(claim.owner());
        ClaimCleanupReason reason = reasonFromState(state);
        boolean skip = shouldSkipClaim(claim, state);
        boolean inactive = lastSeenAt <= 0L || now - lastSeenAt >= inactiveThresholdMillis();
        boolean eligible = !state.isLegacyUnknown()
            && !skip
            && reason != ClaimCleanupReason.NONE
            && inactive;
        return new Evaluation(state, reason, lastSeenAt, skip, eligible);
    }

    private boolean shouldSkipClaim(Claim claim, ClaimCleanupState state) {
        if (claim == null || state == null) {
            return true;
        }
        if (state.isSkipCleanup()) {
            return true;
        }
        if (plugin.settings().inactiveClaimCleanupIgnoreSystemClaims() && claim.systemManaged()) {
            return true;
        }
        PlayerProfile profile = profileService.findProfile(claim.owner());
        if (profile == null) {
            return false;
        }
        if (plugin.settings().isInactiveClaimCleanupGroupExempt(profile.lastGroupKey())) {
            return true;
        }
        return profile.cleanupPermissionExempt();
    }

    private long resolveLastSeenAt(UUID ownerId) {
        PlayerProfile profile = profileService.findProfile(ownerId);
        return profile == null ? 0L : profile.lastSeenAt();
    }

    private long inactiveThresholdMillis() {
        return Math.max(1L, plugin.settings().inactiveClaimCleanupDays()) * 24L * 60L * 60L * 1000L;
    }

    private long gracePeriodMillis() {
        return Math.max(1L, plugin.settings().inactiveClaimCleanupGraceDays()) * 24L * 60L * 60L * 1000L;
    }

    public record CleanupEntry(Claim claim, ClaimCleanupState state, ClaimCleanupReason reason, long lastSeenAt) {
    }

    public record CleanupSnapshot(List<CleanupEntry> candidates, List<CleanupEntry> graceClaims, List<CleanupEntry> legacyClaims) {
    }

    public record CleanupRunResult(int scannedClaims, int markedGraceClaims, int deletedClaims, int revokedGraceClaims, int candidates, int graceClaims) {
    }

    private record Evaluation(ClaimCleanupState state, ClaimCleanupReason reason, long lastSeenAt, boolean shouldSkip, boolean eligible) {
    }
}
