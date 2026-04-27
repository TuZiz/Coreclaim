package com.coreclaim;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

class MessageDefaultsRepairTest {

    @Test
    void findsNestedMissingDefaultPaths() {
        YamlConfiguration defaults = new YamlConfiguration();
        YamlConfiguration existing = new YamlConfiguration();
        defaults.set("messages.prefix", "new");
        defaults.set("messages.help.line", "help");
        existing.set("messages.prefix", "old");

        assertEquals(List.of("messages.help.line"), ConfigurationDefaults.missingPaths(defaults, existing));
    }

    @Test
    void repairsKnownStaleChineseDefaults() {
        YamlConfiguration defaults = new YamlConfiguration();
        YamlConfiguration existing = new YamlConfiguration();
        defaults.set("admin-usage", "new admin usage");
        defaults.set("prefix", "new prefix");
        defaults.set("starter-core-join-reminder", "new join reminder");
        defaults.set("starter-core-reminder", "new reminder");
        defaults.set("deny-usage", "new deny usage");
        defaults.set("claim-deny-all-enabled", "new deny all enabled");
        defaults.set("claim-detail-flags", "new detailed permissions");
        existing.set("admin-usage", "&#FF6B6B鐢ㄦ硶 &8| &7/claim admin <create|info|playerclaims|diagnose|add|unadd|deny|undeny|permission|flag|cleanup|setserver> ...");
        existing.set("prefix", "&#55FFAA&l[领地系统] &#F8FAFC");
        existing.set("starter-core-join-reminder", "&#4CC9F0领地 &#475569| &#CBD5E1累计在线满 &#F8FAFC{minutes} &#CBD5E1分钟可获得第一块领地核心，当前还差 &#F8FAFC{remaining} &#CBD5E1分钟。");
        existing.set("starter-core-reminder", "&#FFD166提醒 &#475569| &#CBD5E1累计在线满 &#F8FAFC{minutes} &#CBD5E1分钟会自动发放新人核心，当前还需 &#F8FAFC{remaining} &#CBD5E1分钟。");
        existing.set("deny-usage", "&#FF6B6B用法 &#475569| &#CBD5E1/claim deny <玩家> &#475569或 &#CBD5E1/claim deny *");
        existing.set("claim-deny-all-enabled", "&#55FFAA封闭模式 &#475569| &#CBD5E1已为领地 &#F8FAFC{name} &#CBD5E1开启 &#F8FAFCdeny *&#CBD5E1。");
        existing.set("claim-detail-flags", "&#55FFAA详情 &#475569| &#CBD5E1交互旗标: {flags}");
        existing.set("help-player", List.of("&#FACC15/claim flag [list] &#CBD5E1查看或调整交互旗标"));

        assertTrue(MessageDefaultsRepair.applyKnownReplacements(existing, defaults, CoreClaimPlugin.MESSAGE_RESOURCE_PATH));
        assertEquals("new admin usage", existing.getString("admin-usage"));
        assertEquals("new prefix", existing.getString("prefix"));
        assertEquals("new join reminder", existing.getString("starter-core-join-reminder"));
        assertEquals("new reminder", existing.getString("starter-core-reminder"));
        assertEquals("new deny usage", existing.getString("deny-usage"));
        assertEquals("new deny all enabled", existing.getString("claim-deny-all-enabled"));
        assertEquals("new detailed permissions", existing.getString("claim-detail-flags"));
        assertEquals(List.of("&#FACC15/claim flag [list] &#CBD5E1查看或调整细分权限"), existing.getStringList("help-player"));
    }
}
