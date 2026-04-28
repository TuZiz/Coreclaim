package com.coreclaim.service.claim.defaults;

import com.coreclaim.model.Claim;
import com.coreclaim.model.ClaimFlag;
import com.coreclaim.model.ClaimFlagState;
import com.coreclaim.model.ClaimMemberSettings;
import com.coreclaim.model.ClaimPermission;
import com.coreclaim.service.claim.ClaimRuntime;
import com.coreclaim.service.claim.persistence.ClaimPersistenceRepository;
import java.util.Map;

public final class ClaimDefaultsService {

    private final ClaimRuntime runtime;
    private final ClaimPersistenceRepository persistenceRepository;

    public ClaimDefaultsService(ClaimRuntime runtime, ClaimPersistenceRepository persistenceRepository) {
        this.runtime = runtime;
        this.persistenceRepository = persistenceRepository;
    }

    public boolean matchesConfiguredDefaults(Claim claim) {
        return claim != null && matchesPermissionDefaults(claim) && matchesFlagDefaults(claim);
    }

    public boolean hasManualRuleOverrides(Claim claim) {
        return claim != null && !matchesConfiguredDefaults(claim);
    }

    public void applyClaimDefaults(Claim claim) {
        applyClaimPermissionDefaults(claim);
        applyClaimFlagDefaults(claim);
    }

    public void applyClaimPermissionDefaults(Claim claim) {
        for (ClaimPermission permission : ClaimPermission.values()) {
            claim.setPermission(permission, runtime.plugin().settings().claimPermissionDefault(permission, claim.systemManaged()));
        }
        claim.setDenyAll(false);
        persistenceRepository.updatePermissionDefaults(claim);
    }

    public void applyClaimFlagDefaults(Claim claim) {
        for (Map.Entry<ClaimFlag, ClaimFlagState> entry : defaultFlagStates(claim).entrySet()) {
            ClaimFlagState state = entry.getValue();
            if (state == null || state == ClaimFlagState.UNSET) {
                continue;
            }
            claim.setFlagState(entry.getKey(), state);
            persistenceRepository.saveFlagState(claim.id(), entry.getKey(), state);
        }
    }

    public ClaimMemberSettings createMemberSettings(Claim claim) {
        return new ClaimMemberSettings(
            claim.permission(ClaimPermission.PLACE),
            claim.permission(ClaimPermission.BREAK),
            claim.permission(ClaimPermission.INTERACT),
            claim.permission(ClaimPermission.INTERACT),
            claim.permission(ClaimPermission.REDSTONE),
            claim.permission(ClaimPermission.EXPLOSION),
            claim.permission(ClaimPermission.BUCKET),
            claim.permission(ClaimPermission.TELEPORT),
            claim.permission(ClaimPermission.FLIGHT)
        );
    }

    public Map<ClaimFlag, ClaimFlagState> defaultFlagStates(Claim claim) {
        return claim != null && claim.systemManaged()
            ? runtime.plugin().settings().systemClaimFlagDefaults()
            : runtime.plugin().settings().newClaimFlagDefaults();
    }

    private boolean matchesPermissionDefaults(Claim claim) {
        for (ClaimPermission permission : ClaimPermission.values()) {
            if (claim.permission(permission) != runtime.plugin().settings().claimPermissionDefault(permission, claim.systemManaged())) {
                return false;
            }
        }
        return !claim.denyAll();
    }

    private boolean matchesFlagDefaults(Claim claim) {
        Map<ClaimFlag, ClaimFlagState> defaults = defaultFlagStates(claim);
        for (ClaimFlag flag : ClaimFlag.values()) {
            if (claim.flagState(flag) != defaults.getOrDefault(flag, ClaimFlagState.UNSET)) {
                return false;
            }
        }
        return true;
    }
}
