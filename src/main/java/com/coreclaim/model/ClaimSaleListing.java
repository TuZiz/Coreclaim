package com.coreclaim.model;

import java.util.UUID;

public record ClaimSaleListing(
    int claimId,
    UUID sellerId,
    String sellerName,
    double price,
    long createdAt
) {
}
