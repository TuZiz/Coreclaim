package com.coreclaim.service.claim.auth;

import com.coreclaim.model.Claim;
import com.coreclaim.model.ClaimFlag;
import com.coreclaim.model.ClaimFlagState;
import com.coreclaim.model.ClaimPermission;
import com.coreclaim.service.claim.ClaimRuntime;
import java.util.UUID;

public final class ClaimAuthorizationService {

    private final ClaimRuntime runtime;

    public ClaimAuthorizationService(ClaimRuntime runtime) {
        this.runtime = runtime;
    }

    public ClaimFlagState flagState(Claim claim, ClaimFlag flag) {
        if (claim == null || flag == null) {
            return ClaimFlagState.UNSET;
        }
        return claim.flagState(flag);
    }

    public boolean canAccess(Claim claim, UUID playerId) {
        if (claim == null || playerId == null) {
            return false;
        }
        if (claim.owner().equals(playerId)) {
            return true;
        }
        if (claim.isDenied(playerId)) {
            return false;
        }
        if (claim.canAccess(playerId)) {
            return true;
        }
        if (claim.denyAll()) {
            return false;
        }
        return runtime.profileService().isGloballyTrusted(claim.owner(), playerId);
    }

    public boolean hasPermission(Claim claim, UUID playerId, ClaimPermission permission) {
        if (claim == null || playerId == null || permission == null) {
            return false;
        }
        if (claim.owner().equals(playerId)) {
            return true;
        }
        if (claim.isDenied(playerId)) {
            return false;
        }
        if (claim.isTrusted(playerId)) {
            return true;
        }
        if (claim.denyAll()) {
            return false;
        }
        if (claim.systemManaged()) {
            return claim.permission(permission);
        }
        if (runtime.profileService().isGloballyTrusted(claim.owner(), playerId)) {
            return claim.permission(permission);
        }
        return false;
    }

    public boolean hasFlagPermission(Claim claim, UUID playerId, ClaimFlag flag) {
        if (claim == null || playerId == null || flag == null) {
            return false;
        }
        if (claim.owner().equals(playerId)) {
            return true;
        }
        if (claim.isDenied(playerId)) {
            return false;
        }

        ClaimFlagState state = claim.flagState(flag);
        if (claim.isTrusted(playerId)) {
            return true;
        }
        if (claim.denyAll()) {
            return false;
        }
        if (claim.systemManaged()) {
            return state.resolve(claim.permission(flag.fallbackPermission()));
        }
        if (runtime.profileService().isGloballyTrusted(claim.owner(), playerId)) {
            return state.resolve(claim.permission(flag.fallbackPermission()));
        }
        return false;
    }
}
