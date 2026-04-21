package com.coreclaim.model;

public final class ClaimCleanupState {

    private final int claimId;
    private boolean hasBuildEvidence;
    private boolean hasInteractionEvidence;
    private long graceMarkedAt;
    private long deleteAfterAt;
    private boolean skipCleanup;
    private boolean legacyUnknown;
    private String lastReason;

    public ClaimCleanupState(int claimId) {
        this(claimId, false, false, 0L, 0L, false, false, "");
    }

    public ClaimCleanupState(
        int claimId,
        boolean hasBuildEvidence,
        boolean hasInteractionEvidence,
        long graceMarkedAt,
        long deleteAfterAt,
        boolean skipCleanup,
        boolean legacyUnknown,
        String lastReason
    ) {
        this.claimId = claimId;
        this.hasBuildEvidence = hasBuildEvidence;
        this.hasInteractionEvidence = hasInteractionEvidence;
        this.graceMarkedAt = Math.max(0L, graceMarkedAt);
        this.deleteAfterAt = Math.max(0L, deleteAfterAt);
        this.skipCleanup = skipCleanup;
        this.legacyUnknown = legacyUnknown;
        this.lastReason = lastReason == null ? "" : lastReason;
    }

    public int claimId() {
        return claimId;
    }

    public int getClaimId() {
        return claimId();
    }

    public boolean hasBuildEvidence() {
        return hasBuildEvidence;
    }

    public boolean isHasBuildEvidence() {
        return hasBuildEvidence();
    }

    public void setHasBuildEvidence(boolean hasBuildEvidence) {
        this.hasBuildEvidence = hasBuildEvidence;
    }

    public boolean hasInteractionEvidence() {
        return hasInteractionEvidence;
    }

    public boolean isHasInteractionEvidence() {
        return hasInteractionEvidence();
    }

    public void setHasInteractionEvidence(boolean hasInteractionEvidence) {
        this.hasInteractionEvidence = hasInteractionEvidence;
    }

    public long graceMarkedAt() {
        return graceMarkedAt;
    }

    public long getGraceMarkedAt() {
        return graceMarkedAt();
    }

    public void setGraceMarkedAt(long graceMarkedAt) {
        this.graceMarkedAt = Math.max(0L, graceMarkedAt);
    }

    public long deleteAfterAt() {
        return deleteAfterAt;
    }

    public long getDeleteAfterAt() {
        return deleteAfterAt();
    }

    public void setDeleteAfterAt(long deleteAfterAt) {
        this.deleteAfterAt = Math.max(0L, deleteAfterAt);
    }

    public boolean skipCleanup() {
        return skipCleanup;
    }

    public boolean isSkipCleanup() {
        return skipCleanup();
    }

    public void setSkipCleanup(boolean skipCleanup) {
        this.skipCleanup = skipCleanup;
    }

    public boolean legacyUnknown() {
        return legacyUnknown;
    }

    public boolean isLegacyUnknown() {
        return legacyUnknown();
    }

    public void setLegacyUnknown(boolean legacyUnknown) {
        this.legacyUnknown = legacyUnknown;
    }

    public String lastReason() {
        return lastReason;
    }

    public String getLastReason() {
        return lastReason();
    }

    public void setLastReason(String lastReason) {
        this.lastReason = lastReason == null ? "" : lastReason;
    }
}
