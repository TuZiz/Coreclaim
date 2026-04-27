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
        existing.set("admin-usage", "&#FF6B6B鐢ㄦ硶 &8| &7/claim admin <create|info|playerclaims|diagnose|add|unadd|deny|undeny|permission|flag|cleanup|setserver> ...");
        existing.set("prefix", "&#55FFAA&l[领地系统] &#F8FAFC");
        existing.set("starter-core-join-reminder", "&#4CC9F0领地 &#475569| &#CBD5E1累计在线满 &#F8FAFC{minutes} &#CBD5E1分钟可获得第一块领地核心，当前还差 &#F8FAFC{remaining} &#CBD5E1分钟。");
        existing.set("starter-core-reminder", "&#FFD166提醒 &#475569| &#CBD5E1累计在线满 &#F8FAFC{minutes} &#CBD5E1分钟会自动发放新人核心，当前还需 &#F8FAFC{remaining} &#CBD5E1分钟。");

        assertTrue(MessageDefaultsRepair.applyKnownReplacements(existing, defaults, CoreClaimPlugin.MESSAGE_RESOURCE_PATH));
        assertEquals("new admin usage", existing.getString("admin-usage"));
        assertEquals("new prefix", existing.getString("prefix"));
        assertEquals("new join reminder", existing.getString("starter-core-join-reminder"));
        assertEquals("new reminder", existing.getString("starter-core-reminder"));
    }
}
