package com.coreclaim.listener;

import com.coreclaim.CoreClaimPlugin;
import com.coreclaim.model.Claim;
import org.bukkit.entity.Player;

final class ClaimEntryMessageFormatter {

    private final CoreClaimPlugin plugin;

    ClaimEntryMessageFormatter(CoreClaimPlugin plugin) {
        this.plugin = plugin;
    }

    String enterMessage(Player player, Claim claim) {
        String custom = claim.enterMessage();
        if (custom != null && !custom.isBlank()) {
            return applyPlaceholders(custom, claim);
        }
        if (claim.owner().equals(player.getUniqueId())) {
            return plugin.message("enter-own-claim", "{name}", claim.name());
        }
        return plugin.message("enter-trusted-claim", "{owner}", claim.ownerName(), "{name}", claim.name());
    }

    String leaveMessage(Claim claim) {
        String custom = claim.leaveMessage();
        if (custom != null && !custom.isBlank()) {
            return applyPlaceholders(custom, claim);
        }
        return plugin.message("leave-claim", "{name}", claim.name());
    }

    private String applyPlaceholders(String text, Claim claim) {
        return plugin.color(text
            .replace("%claim_name%", claim.name())
            .replace("{claim_name}", claim.name())
            .replace("{name}", claim.name())
            .replace("%owner%", claim.ownerName())
            .replace("{owner}", claim.ownerName()));
    }
}
