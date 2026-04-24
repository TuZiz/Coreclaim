package com.coreclaim.util;

import org.bukkit.permissions.Permissible;

public final class AdminAccess {

    private static final String[] ADMIN_NODES = {
        "coreclaim.admin",
        "coreclaim.admin.view",
        "coreclaim.admin.force",
        "coreclaim.admin.ops",
        "coreclaim.admin.create.system",
        "coreclaim.admin.member.manage",
        "coreclaim.admin.permission.manage",
        "coreclaim.admin.flag.manage",
        "coreclaim.admin.claim.manage",
        "coreclaim.admin.market.manage",
        "coreclaim.admin.activity.manage",
        "coreclaim.admin.reward.givecore"
    };

    private AdminAccess() {
    }

    public static boolean hasAnyAdminNode(Permissible permissible) {
        if (permissible == null) {
            return false;
        }
        for (String node : ADMIN_NODES) {
            if (permissible.hasPermission(node)) {
                return true;
            }
        }
        return false;
    }

    public static boolean hasForceBypass(Permissible permissible) {
        return permissible != null
            && (permissible.hasPermission("coreclaim.admin")
            || permissible.hasPermission("coreclaim.admin.force"));
    }

    public static boolean hasViewAccess(Permissible permissible) {
        return hasBaseAdmin(permissible) || hasExact(permissible, "coreclaim.admin.view");
    }

    public static boolean hasOpsAccess(Permissible permissible) {
        return hasForceBypass(permissible) || hasExact(permissible, "coreclaim.admin.ops");
    }

    public static boolean hasCreateSystemAccess(Permissible permissible) {
        return hasForceBypass(permissible)
            || hasExact(permissible, "coreclaim.admin.create.system")
            || hasExact(permissible, "coreclaim.admin.claim.manage");
    }

    public static boolean hasMemberManageAccess(Permissible permissible) {
        return hasForceBypass(permissible) || hasExact(permissible, "coreclaim.admin.member.manage");
    }

    public static boolean hasPermissionManageAccess(Permissible permissible) {
        return hasForceBypass(permissible) || hasExact(permissible, "coreclaim.admin.permission.manage");
    }

    public static boolean hasFlagManageAccess(Permissible permissible) {
        return hasForceBypass(permissible) || hasExact(permissible, "coreclaim.admin.flag.manage");
    }

    public static boolean hasClaimManageAccess(Permissible permissible) {
        return hasForceBypass(permissible) || hasExact(permissible, "coreclaim.admin.claim.manage");
    }

    public static boolean hasMarketManageAccess(Permissible permissible) {
        return hasForceBypass(permissible) || hasExact(permissible, "coreclaim.admin.market.manage");
    }

    public static boolean hasActivityManageAccess(Permissible permissible) {
        return hasOpsAccess(permissible) || hasExact(permissible, "coreclaim.admin.activity.manage");
    }

    public static boolean hasRewardAccess(Permissible permissible) {
        return hasForceBypass(permissible) || hasExact(permissible, "coreclaim.admin.reward.givecore");
    }

    public static boolean hasClaimEditAccess(Permissible permissible) {
        return hasForceBypass(permissible)
            || hasExact(permissible, "coreclaim.admin.claim.manage")
            || hasExact(permissible, "coreclaim.admin.member.manage")
            || hasExact(permissible, "coreclaim.admin.permission.manage")
            || hasExact(permissible, "coreclaim.admin.flag.manage");
    }

    private static boolean hasBaseAdmin(Permissible permissible) {
        return permissible != null && permissible.hasPermission("coreclaim.admin");
    }

    private static boolean hasExact(Permissible permissible, String node) {
        return permissible != null && permissible.hasPermission(node);
    }
}
