package com.coreclaim.input;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.coreclaim.model.Claim;
import java.lang.reflect.Proxy;
import java.util.Collections;
import java.util.Set;
import java.util.UUID;
import org.bukkit.permissions.Permissible;
import org.bukkit.permissions.Permission;
import org.junit.jupiter.api.Test;

class ClaimInputAccessTest {

    @Test
    void ownerCanCommitPendingTextInput() {
        UUID owner = UUID.randomUUID();

        assertTrue(ClaimInputAccess.canCommitClaimText(owner, permissible(), claim(owner)));
    }

    @Test
    void viewOnlyAdminCannotCommitPendingTextInput() {
        UUID owner = UUID.randomUUID();
        UUID viewer = UUID.randomUUID();

        assertFalse(ClaimInputAccess.canCommitClaimText(
            viewer,
            permissible("coreclaim.admin.view"),
            claim(owner)
        ));
    }

    @Test
    void claimManagerCanCommitPendingTextInput() {
        UUID owner = UUID.randomUUID();
        UUID manager = UUID.randomUUID();

        assertTrue(ClaimInputAccess.canCommitClaimText(
            manager,
            permissible("coreclaim.admin.claim.manage"),
            claim(owner)
        ));
    }

    private Permissible permissible(String... permissions) {
        Set<String> granted = Set.of(permissions);
        return (Permissible) Proxy.newProxyInstance(
            Permissible.class.getClassLoader(),
            new Class<?>[] {Permissible.class},
            (proxy, method, args) -> switch (method.getName()) {
                case "hasPermission", "isPermissionSet" -> granted.contains(permissionName(args[0]));
                case "isOp" -> false;
                case "getEffectivePermissions" -> Collections.emptySet();
                case "setOp", "recalculatePermissions", "removeAttachment" -> null;
                case "addAttachment" -> null;
                default -> throw new UnsupportedOperationException(method.getName());
            }
        );
    }

    private String permissionName(Object value) {
        if (value instanceof Permission permission) {
            return permission.getName();
        }
        return String.valueOf(value);
    }

    private Claim claim(UUID owner) {
        return new Claim(
            1,
            owner,
            "Owner",
            "Home",
            "local",
            "world",
            0,
            64,
            0,
            -64,
            320,
            true,
            5,
            5,
            5,
            5,
            0L,
            true,
            "",
            "",
            true,
            true,
            true,
            true,
            true,
            true,
            true,
            true,
            true,
            true,
            true,
            true,
            false,
            false,
            null,
            null,
            null,
            null,
            null,
            0L
        );
    }
}
