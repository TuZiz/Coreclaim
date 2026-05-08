package com.coreclaim.input;

import com.coreclaim.service.ClaimService;
import com.coreclaim.profile.ProfileService;
import com.coreclaim.CoreClaimPlugin;
import com.coreclaim.model.Claim;
import com.coreclaim.platform.PlatformScheduler;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.entity.Player;

public final class ClaimInputService {

    private static final int NOTIFY_MAX_LENGTH = 48;

    private final CoreClaimPlugin plugin;
    private final ClaimService claimService;
    private final ProfileService profileService;
    private final Map<UUID, PendingInput> pendingInputs = new ConcurrentHashMap<>();
    private final Map<UUID, PlatformScheduler.TaskHandle> timeoutTasks = new ConcurrentHashMap<>();

    public ClaimInputService(CoreClaimPlugin plugin, ClaimService claimService, ProfileService profileService) {
        this.plugin = plugin;
        this.claimService = claimService;
        this.profileService = profileService;
    }

    public boolean hasPending(UUID playerId) {
        return pendingInputs.containsKey(playerId);
    }

    public void requestRename(Player player, Claim claim) {
        if (!canManageClaim(player, claim)) {
            player.sendMessage(plugin.message("trust-no-permission"));
            return;
        }
        pendingInputs.put(player.getUniqueId(), new PendingInput(claim.id(), InputMode.RENAME));
        scheduleTimeout(player.getUniqueId());
        player.closeInventory();
        player.sendMessage(plugin.message("input-rename-prompt", "{seconds}", String.valueOf(plugin.settings().chatInputTimeoutSeconds())));
    }

    public void requestEnterMessage(Player player, Claim claim) {
        if (!canManageClaim(player, claim)) {
            player.sendMessage(plugin.message("trust-no-permission"));
            return;
        }
        pendingInputs.put(player.getUniqueId(), new PendingInput(claim.id(), InputMode.ENTER_MESSAGE));
        scheduleTimeout(player.getUniqueId());
        player.closeInventory();
        player.sendMessage(plugin.message("input-enter-prompt", "{seconds}", String.valueOf(plugin.settings().chatInputTimeoutSeconds())));
    }

    public void requestLeaveMessage(Player player, Claim claim) {
        if (!canManageClaim(player, claim)) {
            player.sendMessage(plugin.message("trust-no-permission"));
            return;
        }
        pendingInputs.put(player.getUniqueId(), new PendingInput(claim.id(), InputMode.LEAVE_MESSAGE));
        scheduleTimeout(player.getUniqueId());
        player.closeInventory();
        player.sendMessage(plugin.message("input-leave-prompt", "{seconds}", String.valueOf(plugin.settings().chatInputTimeoutSeconds())));
    }

    public void cancel(Player player, boolean notify) {
        cancelTimeout(player.getUniqueId());
        if (pendingInputs.remove(player.getUniqueId()) != null && notify) {
            player.sendMessage(plugin.message("input-cancelled"));
        }
    }

    public void handleInput(Player player, String rawMessage) {
        PendingInput pending = pendingInputs.remove(player.getUniqueId());
        cancelTimeout(player.getUniqueId());
        if (pending == null) {
            return;
        }

        String message = rawMessage == null ? "" : rawMessage.trim();
        if (isCancel(message)) {
            player.sendMessage(plugin.message("input-cancelled"));
            return;
        }

        Claim claim = claimService.findClaimByIdFresh(pending.claimId()).orElse(null);
        if (claim == null) {
            player.sendMessage(plugin.message("claim-not-found"));
            return;
        }
        if (!canManageClaim(player, claim)) {
            player.sendMessage(plugin.message("trust-no-permission"));
            return;
        }

        switch (pending.mode()) {
            case RENAME -> handleRename(player, claim, message);
            case ENTER_MESSAGE -> handleEnterMessage(player, claim, message);
            case LEAVE_MESSAGE -> handleLeaveMessage(player, claim, message);
        }
    }

    private void handleRename(Player player, Claim claim, String message) {
        if (message.isBlank()) {
            player.sendMessage(plugin.message("claim-name-empty"));
            return;
        }
        if (message.length() > plugin.settings().claimNameMaxLength()) {
            player.sendMessage(plugin.message("claim-name-too-long", "{max}", String.valueOf(plugin.settings().claimNameMaxLength())));
            return;
        }
        if (claimService.isClaimNameTaken(message, claim.id())) {
            player.sendMessage(plugin.message("claim-name-exists", "{name}", message.trim()));
            return;
        }
        try {
            claimService.renameClaim(claim, message, player.getUniqueId());
        } catch (IllegalArgumentException exception) {
            player.sendMessage(plugin.message("claim-name-exists", "{name}", message.trim()));
            return;
        }
        player.sendMessage(plugin.message("input-rename-success", "{name}", message.trim()));
    }

    private void handleEnterMessage(Player player, Claim claim, String message) {
        if (isClear(message)) {
            claimService.updateEnterMessage(claim, "", player.getUniqueId());
            player.sendMessage(plugin.message("input-enter-cleared"));
            return;
        }
        if (message.isBlank()) {
            player.sendMessage(plugin.message("input-enter-empty"));
            return;
        }
        if (message.length() > NOTIFY_MAX_LENGTH) {
            player.sendMessage(plugin.message("input-enter-too-long", "{max}", String.valueOf(NOTIFY_MAX_LENGTH)));
            return;
        }
        claimService.updateEnterMessage(claim, message, player.getUniqueId());
        player.sendMessage(plugin.message("input-enter-success"));
    }

    private void handleLeaveMessage(Player player, Claim claim, String message) {
        if (isClear(message)) {
            claimService.updateLeaveMessage(claim, "", player.getUniqueId());
            player.sendMessage(plugin.message("input-leave-cleared"));
            return;
        }
        if (message.isBlank()) {
            player.sendMessage(plugin.message("input-leave-empty"));
            return;
        }
        if (message.length() > NOTIFY_MAX_LENGTH) {
            player.sendMessage(plugin.message("input-leave-too-long", "{max}", String.valueOf(NOTIFY_MAX_LENGTH)));
            return;
        }
        claimService.updateLeaveMessage(claim, message, player.getUniqueId());
        player.sendMessage(plugin.message("input-leave-success"));
    }

    private boolean canManageClaim(Player player, Claim claim) {
        return ClaimInputAccess.canCommitClaimText(player, claim);
    }

    private void scheduleTimeout(UUID playerId) {
        cancelTimeout(playerId);
        long delayTicks = plugin.settings().chatInputTimeoutSeconds() * 20L;
        PlatformScheduler.TaskHandle handle = plugin.platformScheduler().runLater(() -> timeout(playerId), delayTicks);
        timeoutTasks.put(playerId, handle);
    }

    private void cancelTimeout(UUID playerId) {
        PlatformScheduler.TaskHandle handle = timeoutTasks.remove(playerId);
        if (handle != null) {
            handle.cancel();
        }
    }

    private void timeout(UUID playerId) {
        PendingInput pending = pendingInputs.remove(playerId);
        cancelTimeout(playerId);
        if (pending == null) {
            return;
        }
        Player player = plugin.getServer().getPlayer(playerId);
        if (player != null) {
            player.sendMessage(plugin.message("claim-input-timeout"));
        }
    }

    private boolean isCancel(String message) {
        return matchesKeyword(message, "input-cancel-keywords", "cancel");
    }

    private boolean isClear(String message) {
        return matchesKeyword(message, "input-clear-keywords", "clear");
    }

    private boolean matchesKeyword(String message, String path, String fallback) {
        List<String> keywords = plugin.messagesConfig().getStringList(path);
        if (keywords.isEmpty()) {
            keywords = List.of(fallback);
        }
        for (String keyword : keywords) {
            if (keyword.equalsIgnoreCase(message)) {
                return true;
            }
        }
        return false;
    }

    private record PendingInput(int claimId, InputMode mode) {
    }

    private enum InputMode {
        RENAME,
        ENTER_MESSAGE,
        LEAVE_MESSAGE
    }
}
