package com.coreclaim.listener;

import com.coreclaim.CoreClaimPlugin;
import com.coreclaim.model.Claim;
import com.coreclaim.service.ClaimService;
import com.coreclaim.service.ClaimVisualService;
import com.coreclaim.service.ProfileService;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public final class ClaimEnterLeaveListener implements Listener {

    private final CoreClaimPlugin plugin;
    private final ClaimService claimService;
    private final ProfileService profileService;
    private final ClaimVisualService claimVisualService;

    public ClaimEnterLeaveListener(
        CoreClaimPlugin plugin,
        ClaimService claimService,
        ProfileService profileService,
        ClaimVisualService claimVisualService
    ) {
        this.plugin = plugin;
        this.claimService = claimService;
        this.profileService = profileService;
        this.claimVisualService = claimVisualService;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        claimService.findClaim(event.getPlayer().getLocation());
    }

    @EventHandler(ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        if (event.getTo() == null) {
            return;
        }
        if (!plugin.getConfig().getBoolean("show-enter-leave-messages", true)) {
            return;
        }
        if (event.getFrom().getBlockX() == event.getTo().getBlockX()
            && event.getFrom().getBlockY() == event.getTo().getBlockY()
            && event.getFrom().getBlockZ() == event.getTo().getBlockZ()
            && event.getFrom().getWorld() == event.getTo().getWorld()) {
            return;
        }

        Claim fromClaim = claimService.findClaim(event.getFrom()).orElse(null);
        Claim toClaim = claimService.findClaim(event.getTo()).orElse(null);
        int fromId = fromClaim == null ? -1 : fromClaim.id();
        int toId = toClaim == null ? -1 : toClaim.id();
        if (fromId == toId) {
            return;
        }

        Player player = event.getPlayer();
        if (toClaim != null
            && !toClaim.owner().equals(player.getUniqueId())
            && toClaim.isBlacklisted(player.getUniqueId())
            && !player.hasPermission("coreclaim.admin")) {
            event.setTo(event.getFrom());
            player.sendMessage(plugin.message("blacklist-deny-enter", "{name}", toClaim.name()));
            return;
        }

        if (fromClaim != null) {
            sendActionBar(player, leaveMessage(fromClaim));
        }
        if (toClaim != null) {
            if (profileService.getOrCreate(player.getUniqueId(), player.getName()).autoShowBorders()) {
                claimVisualService.showClaim(player, toClaim);
            }
            sendActionBar(player, enterMessage(player, toClaim));
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        // no-op, reserved for future caching
    }

    private void sendActionBar(Player player, String message) {
        player.spigot().sendMessage(ChatMessageType.ACTION_BAR, TextComponent.fromLegacyText(message));
    }

    private String enterMessage(Player player, Claim claim) {
        String custom = claim.enterMessage();
        if (custom != null && !custom.isBlank()) {
            return applyNotifyPlaceholders(custom, claim);
        }
        if (claim.owner().equals(player.getUniqueId())) {
            return plugin.message("enter-own-claim", "{name}", claim.name());
        }
        return plugin.message("enter-trusted-claim", "{owner}", claim.ownerName(), "{name}", claim.name());
    }

    private String leaveMessage(Claim claim) {
        String custom = claim.leaveMessage();
        if (custom != null && !custom.isBlank()) {
            return applyNotifyPlaceholders(custom, claim);
        }
        return plugin.message("leave-claim", "{name}", claim.name());
    }

    private String applyNotifyPlaceholders(String text, Claim claim) {
        return plugin.color(text
            .replace("%claim_name%", claim.name())
            .replace("{claim_name}", claim.name())
            .replace("{name}", claim.name())
            .replace("%owner%", claim.ownerName())
            .replace("{owner}", claim.ownerName()));
    }
}
