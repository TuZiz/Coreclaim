package com.coreclaim;

import java.util.ArrayList;
import java.util.List;
import org.bukkit.configuration.file.FileConfiguration;

final class MessageDefaultsRepair {

    private static final String OLD_ADMIN_USAGE =
        "&#FF6B6B鐢ㄦ硶 &8| &7/claim admin <create|info|playerclaims|diagnose|add|unadd|deny|undeny|permission|flag|cleanup|setserver> ...";

    private MessageDefaultsRepair() {
    }

    static boolean applyKnownReplacements(FileConfiguration messagesConfig, FileConfiguration defaults, String resourcePath) {
        boolean changed = false;
        String newAdminUsage = defaults.getString("admin-usage", OLD_ADMIN_USAGE);
        if (OLD_ADMIN_USAGE.equals(messagesConfig.getString("admin-usage"))) {
            messagesConfig.set("admin-usage", newAdminUsage);
            changed = true;
        }
        if (CoreClaimPlugin.MESSAGE_RESOURCE_PATH.equals(resourcePath)) {
            changed |= replaceMessageIfExact(messagesConfig, defaults, "prefix", "&#55FFAA&l[领地系统] &#F8FAFC");
            changed |= replaceMessageIfExact(messagesConfig, defaults, "starter-core-join-reminder", "&#4CC9F0领地 &#475569| &#CBD5E1累计在线满 &#F8FAFC{minutes} &#CBD5E1分钟可获得第一块领地核心，当前还差 &#F8FAFC{remaining} &#CBD5E1分钟。");
            changed |= replaceMessageIfExact(messagesConfig, defaults, "starter-core-reminder", "&#FFD166提醒 &#475569| &#CBD5E1累计在线满 &#F8FAFC{minutes} &#CBD5E1分钟会自动发放新人核心，当前还需 &#F8FAFC{remaining} &#CBD5E1分钟。");
            changed |= replaceMessageIfExact(messagesConfig, defaults, "deny-usage", "&#FF6B6B用法 &#475569| &#CBD5E1/claim deny <玩家> &#475569或 &#CBD5E1/claim deny *");
            changed |= replaceMessageIfExact(messagesConfig, defaults, "undeny-usage", "&#FF6B6B用法 &#475569| &#CBD5E1/claim undeny <玩家> &#475569或 &#CBD5E1/claim undeny *");
            changed |= replaceMessageIfExact(messagesConfig, defaults, "admin-deny-usage", "&#FF6B6B用法 &#475569| &#CBD5E1/claim admin deny <玩家|*>");
            changed |= replaceMessageIfExact(messagesConfig, defaults, "admin-undeny-usage", "&#FF6B6B用法 &#475569| &#CBD5E1/claim admin undeny <玩家|*>");
            changed |= replaceMessageIfExact(messagesConfig, defaults, "claim-deny-all-enabled", "&#55FFAA封闭模式 &#475569| &#CBD5E1已为领地 &#F8FAFC{name} &#CBD5E1开启 &#F8FAFCdeny *&#CBD5E1。");
            changed |= replaceMessageIfExact(messagesConfig, defaults, "claim-deny-all-disabled", "&#55FFAA封闭模式 &#475569| &#CBD5E1已为领地 &#F8FAFC{name} &#CBD5E1关闭 &#F8FAFCdeny *&#CBD5E1。");
            changed |= replaceMessageIfExact(messagesConfig, defaults, "admin-permission-container-deprecated", "&#FFD166提示 &#475569| &#CBD5E1container 已迁移为交互旗标，请使用 &#F8FAFC/claim admin flag container <allow|deny|unset>&#CBD5E1。");
            changed |= replaceMessageIfExact(messagesConfig, defaults, "claim-detail-permissions", "&#55FFAA详情 &#475569| &#CBD5E1默认权限: &#94A3B8放置 {place} &#475569| &#94A3B8破坏 {break} &#475569| &#94A3B8交互 {interact} &#475569| &#94A3B8传送 {teleport} &#475569| &#94A3B8飞行 {flight}");
            changed |= replaceMessageIfExact(messagesConfig, defaults, "claim-detail-flags", "&#55FFAA详情 &#475569| &#CBD5E1交互旗标: {flags}");
            changed |= replaceMessageIfExact(messagesConfig, defaults, "flag-summary-header", "&#55FFAA旗标 &#475569| &#CBD5E1交互旗标 - &#F8FAFC{name}");
            changed |= replaceListEntryIfExact(messagesConfig, "help-player", "&#FACC15/claim flag [list] &#CBD5E1查看或调整交互旗标", "&#FACC15/claim flag [list] &#CBD5E1查看或调整细分权限");
            changed |= replaceListEntryIfExact(messagesConfig, "help-admin", "&#FACC15/claim admin flag <flag> <allow|deny|unset> &#CBD5E1强制修改交互旗标", "&#FACC15/claim admin flag <flag> <allow|deny|unset> &#CBD5E1强制修改细分权限");
        } else if (CoreClaimPlugin.ENGLISH_MESSAGE_RESOURCE_PATH.equals(resourcePath)) {
            changed |= replaceMessageIfExact(messagesConfig, defaults, "prefix", "&#55FFAA&l[CoreClaim] &#F8FAFC");
            changed |= replaceMessageIfExact(messagesConfig, defaults, "starter-core-join-reminder", "&#4CC9F0Claim &#475569| &#CBD5E1Stay online for &#F8FAFC{minutes} &#CBD5E1minutes to receive your first claim core. &#F8FAFC{remaining} &#CBD5E1minutes remaining.");
            changed |= replaceMessageIfExact(messagesConfig, defaults, "starter-core-reminder", "&#FFD166Reminder &#475569| &#CBD5E1A starter core will be granted after &#F8FAFC{minutes} &#CBD5E1minutes online. &#F8FAFC{remaining} &#CBD5E1minutes remaining.");
            changed |= replaceMessageIfExact(messagesConfig, defaults, "deny-usage", "&#FF6B6BUsage &#475569| &#CBD5E1/claim deny <player> &#475569or &#CBD5E1/claim deny *");
            changed |= replaceMessageIfExact(messagesConfig, defaults, "undeny-usage", "&#FF6B6BUsage &#475569| &#CBD5E1/claim undeny <player> &#475569or &#CBD5E1/claim undeny *");
            changed |= replaceMessageIfExact(messagesConfig, defaults, "admin-deny-usage", "&#FF6B6BUsage &#475569| &#CBD5E1/claim admin deny <player|*>");
            changed |= replaceMessageIfExact(messagesConfig, defaults, "admin-undeny-usage", "&#FF6B6BUsage &#475569| &#CBD5E1/claim admin undeny <player|*>");
            changed |= replaceMessageIfExact(messagesConfig, defaults, "claim-deny-all-enabled", "&#55FFAADeny Mode &#475569| &#CBD5E1Enabled &#F8FAFCdeny * &#CBD5E1for claim &#F8FAFC{name}&#CBD5E1.");
            changed |= replaceMessageIfExact(messagesConfig, defaults, "claim-deny-all-disabled", "&#55FFAADeny Mode &#475569| &#CBD5E1Disabled &#F8FAFCdeny * &#CBD5E1for claim &#F8FAFC{name}&#CBD5E1.");
            changed |= replaceMessageIfExact(messagesConfig, defaults, "admin-permission-container-deprecated", "&#FFD166Tip &#475569| &#CBD5E1container moved to interaction flags. Use &#F8FAFC/claim admin flag container <allow|deny|unset>&#CBD5E1.");
            changed |= replaceMessageIfExact(messagesConfig, defaults, "claim-detail-permissions", "&#55FFAADetail &#475569| &#CBD5E1Default Permissions: &#94A3B8Place {place} &#475569| &#94A3B8Break {break} &#475569| &#94A3B8Interact {interact} &#475569| &#94A3B8Teleport {teleport} &#475569| &#94A3B8Flight {flight}");
            changed |= replaceMessageIfExact(messagesConfig, defaults, "claim-detail-flags", "&#55FFAADetail &#475569| &#CBD5E1Interaction Flags: {flags}");
            changed |= replaceMessageIfExact(messagesConfig, defaults, "flag-summary-header", "&#55FFAAFlag &#475569| &#CBD5E1Interaction Flags - &#F8FAFC{name}");
            changed |= replaceListEntryIfExact(messagesConfig, "help-player", "&#FACC15/claim flag [list] &#CBD5E1View or update interaction flags", "&#FACC15/claim flag [list] &#CBD5E1View or update detailed permissions");
            changed |= replaceListEntryIfExact(messagesConfig, "help-admin", "&#FACC15/claim admin flag <flag> <allow|deny|unset> &#CBD5E1Force-update interaction flags", "&#FACC15/claim admin flag <flag> <allow|deny|unset> &#CBD5E1Force-update detailed permissions");
        }
        return changed;
    }

    private static boolean replaceMessageIfExact(
        FileConfiguration messagesConfig,
        FileConfiguration defaults,
        String path,
        String oldValue
    ) {
        if (!oldValue.equals(messagesConfig.getString(path))) {
            return false;
        }
        messagesConfig.set(path, defaults.getString(path, oldValue));
        return true;
    }

    private static boolean replaceListEntryIfExact(
        FileConfiguration messagesConfig,
        String path,
        String oldValue,
        String newValue
    ) {
        List<String> values = messagesConfig.getStringList(path);
        if (values.isEmpty()) {
            return false;
        }
        boolean changed = false;
        List<String> updated = new ArrayList<>(values.size());
        for (String value : values) {
            if (oldValue.equals(value)) {
                updated.add(newValue);
                changed = true;
            } else {
                updated.add(value);
            }
        }
        if (!changed) {
            return false;
        }
        messagesConfig.set(path, updated);
        return true;
    }
}
