package com.coreclaim.listener;

import com.coreclaim.platform.PlatformScheduler;

final class PlayerFlightSession {

    Integer currentClaimId;
    Integer lastNotifyClaimId;
    boolean managingClaimFlight;
    boolean managingClaimTime;
    boolean baselineAllowFlight;
    boolean baselineFlying;
    boolean graceActive;
    PlatformScheduler.TaskHandle graceTask;

    void beginManagedFlight(boolean baselineAllowFlight, boolean baselineFlying) {
        this.managingClaimFlight = true;
        this.baselineAllowFlight = baselineAllowFlight;
        this.baselineFlying = baselineFlying;
    }
}
