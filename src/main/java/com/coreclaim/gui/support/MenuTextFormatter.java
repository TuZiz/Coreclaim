package com.coreclaim.gui.support;

import com.coreclaim.CoreClaimPlugin;
import com.coreclaim.model.Claim;
import com.coreclaim.model.ClaimFlag;
import com.coreclaim.model.ClaimFlagState;
import com.coreclaim.service.ClaimService.ClaimListRelation;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;

import java.util.UUID;

public final class MenuTextFormatter {

    private final CoreClaimPlugin plugin;

    public MenuTextFormatter(CoreClaimPlugin plugin) {
        this.plugin = plugin;
    }

    public String menuTitle(String title, String... replacements) {
        return plugin.color(apply(title, replacements));
    }

    public String displayNotifyPreview(String raw, Claim claim, String fallback) {
        String base = raw == null || raw.isBlank() ? fallback : raw;
        String preview = base
            .replace("%claim_name%", claim.name())
            .replace("{claim_name}", claim.name())
            .replace("{name}", claim.name())
            .replace("%owner%", claim.ownerName())
            .replace("{owner}", claim.ownerName());
        if (preview.length() > 24) {
            return preview.substring(0, 24) + "...";
        }
        return preview;
    }

    public String notifyStateText(String raw) {
        return raw == null || raw.isBlank() ? "&#CBD5E1默认内容" : "&#FFD166已修改";
    }

    public String relationText(ClaimListRelation relation) {
        return relation == ClaimListRelation.OWNER ? "&#55FFAA我的领地" : "&#4CC9F0已授权领地";
    }

    public String leftClickActionText(ClaimListRelation relation) {
        return relation == ClaimListRelation.OWNER ? "&#CBD5E1左键打开核心管理" : "&#CBD5E1左键查看只读详情";
    }

    public String stripMessagePrefix(String message) {
        String prefix = plugin.color(plugin.messagesConfig().getString("prefix", ""));
        return message != null && message.startsWith(prefix) ? message.substring(prefix.length()) : (message == null ? "" : message);
    }

    public String stateText(boolean enabled) {
        return plugin.plainMessage(enabled ? "state-allow" : "state-deny");
    }

    public String defaultStateText(boolean enabled) {
        return plugin.plainMessage(enabled ? "state-default-allow" : "state-default-deny");
    }

    public String flagStateText(ClaimFlagState state) {
        return switch (state) {
            case ALLOW -> plugin.plainMessage("state-allow");
            case DENY -> plugin.plainMessage("state-deny");
            case UNSET -> plugin.plainMessage("state-unset");
        };
    }

    public String flagItemKey(ClaimFlag flag) {
        return switch (flag) {
            case TIME_CYCLE -> "perm-time-cycle";
            case LIQUID_FLOW -> "perm-liquid-flow";
        };
    }

    public String playerName(UUID playerId) {
        OfflinePlayer player = Bukkit.getOfflinePlayer(playerId);
        return player.getName() == null ? playerId.toString() : player.getName();
    }

    public String padLayout(String line) {
        String value = line == null ? "" : line;
        if (value.length() >= 9) {
            return value.substring(0, 9);
        }
        return String.format("%-9s", value);
    }

    public String apply(String text, String... replacements) {
        String result = text == null ? "" : text;
        for (int index = 0; index + 1 < replacements.length; index += 2) {
            result = result.replace(replacements[index], replacements[index + 1]);
        }
        return result;
    }
}
