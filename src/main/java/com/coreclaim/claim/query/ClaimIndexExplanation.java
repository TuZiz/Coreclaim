package com.coreclaim.claim.query;

public record ClaimIndexExplanation(
    int claimId,
    String world,
    String rawServerId,
    String effectiveServerId,
    String currentServerId,
    boolean mysql,
    boolean localClaim,
    boolean worldLoaded,
    boolean indexed,
    boolean currentLocationChecked,
    boolean currentLocationFindClaimHit,
    Integer currentLocationClaimId,
    String repairSuggestion
) {

    public String rawServerIdDisplay() {
        return rawServerId == null || rawServerId.isBlank() ? "<empty>" : rawServerId;
    }

    public String effectiveServerIdDisplay() {
        return effectiveServerId == null || effectiveServerId.isBlank() ? "<none>" : effectiveServerId;
    }

    public String currentLocationHitDisplay() {
        if (!currentLocationChecked) {
            return "N/A";
        }
        return currentLocationFindClaimHit
            ? "yes #" + currentLocationClaimId
            : "no";
    }
}
