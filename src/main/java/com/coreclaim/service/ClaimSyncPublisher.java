package com.coreclaim.service;

public interface ClaimSyncPublisher {

    ClaimSyncPublisher NO_OP = new ClaimSyncPublisher() {
        @Override
        public void publish(ClaimSyncEventType eventType, int claimId) {
        }

        @Override
        public void publishClaimsReloaded() {
        }
    };

    void publish(ClaimSyncEventType eventType, int claimId);

    void publishClaimsReloaded();
}
