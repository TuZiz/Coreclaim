package com.coreclaim;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class PermissionEntryResourceTest {

    @Test
    void permissionMenuUsesPermissionItemKeysForExtendedPermissions() throws Exception {
        String menu = resourceText("/gui/claim-permissions.yml");

        assertTrue(menu.contains("perm-mob-interact:"));
        assertTrue(menu.contains("perm-liquid-flow:"));
        assertTrue(menu.contains("perm-time-cycle:"));
        assertFalse(menu.contains("flag-liquid-flow:"));
        assertFalse(menu.contains("flag-time-cycle:"));
    }

    @Test
    void playerHelpPointsToSetGuiInsteadOfFlagCommand() throws Exception {
        String zhCn = resourceText("/lang/zh_cn.yml");
        String enUs = resourceText("/lang/en_us.yml");

        assertTrue(zhCn.contains("/claim set"));
        assertTrue(enUs.contains("/claim set"));
        assertFalse(zhCn.contains("/claim flag [list]"));
        assertFalse(enUs.contains("/claim flag [list]"));
    }

    @Test
    void claimDetailsDoNotRepeatDetailPrefix() throws Exception {
        String zhCn = resourceText("/lang/zh_cn.yml");
        String enUs = resourceText("/lang/en_us.yml");

        assertFalse(zhCn.contains("&#55FFAA详情 &#475569|"));
        assertFalse(enUs.contains("&#55FFAADetail &#475569|"));
        assertTrue(zhCn.contains("claim-detail-permissions-extra:"));
        assertTrue(enUs.contains("claim-detail-permissions-extra:"));
        assertTrue(zhCn.contains("{mob_interact}"));
        assertTrue(enUs.contains("{mob_interact}"));
    }

    @Test
    void rulesExposeMobInteractionPermissionDefaults() throws Exception {
        String rules = resourceText("/rules.yml");

        assertTrue(rules.contains("mob-interact: false"));
    }

    private String resourceText(String path) throws Exception {
        try (InputStream input = PermissionEntryResourceTest.class.getResourceAsStream(path)) {
            assertNotNull(input, path);
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
