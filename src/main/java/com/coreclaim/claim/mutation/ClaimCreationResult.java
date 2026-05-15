package com.coreclaim.claim.mutation;

import com.coreclaim.model.Claim;

public record ClaimCreationResult(Claim claim, int previousOwnerClaimCount) {
}
