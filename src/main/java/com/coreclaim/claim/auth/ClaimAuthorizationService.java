package com.coreclaim.claim.auth;

import com.coreclaim.model.Claim;
import com.coreclaim.model.ClaimFlag;
import com.coreclaim.model.ClaimFlagState;
import com.coreclaim.model.ClaimPermission;
import com.coreclaim.claim.ClaimRuntime;
import java.util.UUID;

public final class ClaimAuthorizationService {

    public ClaimAuthorizationService(ClaimRuntime runtime) {
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
        if (claim.isTrusted(playerId)) {
            return true;
        }
        return !claim.denyAll();
    }

    public boolean hasPermission(Claim claim, UUID playerId, ClaimPermission permission) {
        return permissionDecision(claim, playerId, permission, false).allowed();
    }

    public AuthorizationDecision permissionDecision(Claim claim, UUID playerId, ClaimPermission permission, boolean bypassing) {
        if (bypassing) {
            return new AuthorizationDecision(AuthorizationSource.BYPASS, true);
        }
        if (claim == null || playerId == null || permission == null) {
            return new AuthorizationDecision(AuthorizationSource.DENIED, false);
        }
        if (claim.owner().equals(playerId)) {
            return new AuthorizationDecision(AuthorizationSource.OWNER, true);
        }
        if (claim.isDenied(playerId)) {
            return new AuthorizationDecision(AuthorizationSource.DENIED, false);
        }
        if (claim.isTrusted(playerId)) {
            return new AuthorizationDecision(AuthorizationSource.TRUSTED, claim.memberPermission(playerId, permission, true));
        }
        if (claim.denyAll()) {
            return new AuthorizationDecision(AuthorizationSource.DENIED, false);
        }
        boolean publicPermission = claim.permission(permission);
        return new AuthorizationDecision(publicPermission ? AuthorizationSource.PUBLIC_PERMISSION : AuthorizationSource.DENIED, publicPermission);
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
            return state.resolve(claim.memberPermission(playerId, flag.fallbackPermission(), true));
        }
        if (claim.denyAll()) {
            return false;
        }
        return state.resolve(claim.permission(flag.fallbackPermission()));
    }

    public enum AuthorizationSource {
        BYPASS,
        OWNER,
        TRUSTED,
        PUBLIC_PERMISSION,
        DENIED
    }

    public record AuthorizationDecision(AuthorizationSource source, boolean allowed) {
    }
}
