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
        if (claim.isTrusted(playerId) || isGloballyTrusted(claim, playerId)) {
            return true;
        }
        return !claim.denyAll();
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
        boolean globallyTrusted = isGloballyTrusted(claim, playerId);
        if (claim.isTrusted(playerId) || globallyTrusted) {
            return claim.memberPermission(playerId, permission, true);
        }
        if (claim.denyAll()) {
            return false;
        }
        return claim.permission(permission);
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
        boolean globallyTrusted = isGloballyTrusted(claim, playerId);
        if (claim.isTrusted(playerId) || globallyTrusted) {
            return state.resolve(claim.memberPermission(playerId, flag.fallbackPermission(), true));
        }
        if (claim.denyAll()) {
            return false;
        }
        return state.resolve(claim.permission(flag.fallbackPermission()));
    }

    private boolean isGloballyTrusted(Claim claim, UUID playerId) {
        return runtime != null
            && runtime.profileService() != null
            && claim != null
            && playerId != null
            && runtime.profileService().isGloballyTrusted(claim.owner(), playerId);
    }
}
