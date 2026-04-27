package com.coreclaim.util;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Proxy;
import java.util.Collections;
import java.util.Set;
import org.bukkit.permissions.Permissible;
import org.bukkit.permissions.Permission;
import org.junit.jupiter.api.Test;

class AdminAccessTest {

    @Test
    void viewAccessDoesNotGrantClaimWrites() {
        Permissible permissible = permissible("coreclaim.admin.view");

        assertTrue(AdminAccess.hasAnyAdminNode(permissible));
        assertTrue(AdminAccess.hasViewAccess(permissible));
        assertFalse(AdminAccess.hasForceBypass(permissible));
        assertFalse(AdminAccess.hasClaimManageAccess(permissible));
        assertFalse(AdminAccess.hasMemberManageAccess(permissible));
        assertFalse(AdminAccess.hasPermissionManageAccess(permissible));
        assertFalse(AdminAccess.hasFlagManageAccess(permissible));
        assertFalse(AdminAccess.hasAnyClaimWriteAccess(permissible));
    }

    @Test
    void operationNodesStaySeparated() {
        Permissible memberManager = permissible("coreclaim.admin.member.manage");

        assertTrue(AdminAccess.hasAnyAdminNode(memberManager));
        assertTrue(AdminAccess.hasMemberManageAccess(memberManager));
        assertFalse(AdminAccess.hasClaimManageAccess(memberManager));
        assertFalse(AdminAccess.hasPermissionManageAccess(memberManager));
        assertFalse(AdminAccess.hasFlagManageAccess(memberManager));
        assertTrue(AdminAccess.hasAnyClaimWriteAccess(memberManager));
    }

    @Test
    void baseAdminKeepsFullAccess() {
        Permissible admin = permissible("coreclaim.admin");

        assertTrue(AdminAccess.hasViewAccess(admin));
        assertTrue(AdminAccess.hasForceBypass(admin));
        assertTrue(AdminAccess.hasClaimManageAccess(admin));
        assertTrue(AdminAccess.hasMemberManageAccess(admin));
        assertTrue(AdminAccess.hasPermissionManageAccess(admin));
        assertTrue(AdminAccess.hasFlagManageAccess(admin));
        assertTrue(AdminAccess.hasAnyClaimWriteAccess(admin));
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
}
