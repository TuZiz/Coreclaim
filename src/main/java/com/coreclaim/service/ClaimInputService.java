package com.coreclaim.service;

import com.coreclaim.CoreClaimPlugin;
import com.coreclaim.model.Claim;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.entity.Player;

public final class ClaimInputService {

    private static final int NOTIFY_MAX_LENGTH = 48;

    private final CoreClaimPlugin plugin;
    private final ClaimService claimService;
    private final Map<UUID, PendingInput> pendingInputs = new ConcurrentHashMap<>();
    private final Map<UUID, com.coreclaim.platform.PlatformScheduler.TaskHandle> timeoutTasks = new ConcurrentHashMap<>();

    public ClaimInputService(CoreClaimPlugin plugin, ClaimService claimService) {
        this.plugin = plugin;
        this.claimService = claimService;
    }

    public boolean hasPending(UUID playerId) {
        return pendingInputs.containsKey(playerId);
    }

    public void requestRename(Player player, Claim claim) {
        pendingInputs.put(player.getUniqueId(), new PendingInput(claim.id(), InputMode.RENAME));
        scheduleTimeout(player.getUniqueId());
        player.closeInventory();
        player.sendMessage(plugin.color("&6[Claim] &f请在聊天栏输入新的领地名字，输入 &c取消 &f取消。&7(" + plugin.settings().chatInputTimeoutSeconds() + "秒后自动取消)"));
    }

    public void requestEnterMessage(Player player, Claim claim) {
        pendingInputs.put(player.getUniqueId(), new PendingInput(claim.id(), InputMode.ENTER_MESSAGE));
        scheduleTimeout(player.getUniqueId());
        player.closeInventory();
        player.sendMessage(plugin.color("&8[Claim] &7可用变量: &f{claim_name} &7/ &f{owner} &7/ &f{name} &8(也支持 %claim_name% / %owner%)"));
        player.sendMessage(plugin.color("&6[Claim] &f请在聊天栏输入进入提示，输入 &e清空 &f恢复默认，输入 &c取消 &f取消。&7(" + plugin.settings().chatInputTimeoutSeconds() + "秒后自动取消)"));
    }

    public void requestLeaveMessage(Player player, Claim claim) {
        pendingInputs.put(player.getUniqueId(), new PendingInput(claim.id(), InputMode.LEAVE_MESSAGE));
        scheduleTimeout(player.getUniqueId());
        player.closeInventory();
        player.sendMessage(plugin.color("&8[Claim] &7可用变量: &f{claim_name} &7/ &f{owner} &7/ &f{name} &8(也支持 %claim_name% / %owner%)"));
        player.sendMessage(plugin.color("&6[Claim] &f请在聊天栏输入离开提示，输入 &e清空 &f恢复默认，输入 &c取消 &f取消。&7(" + plugin.settings().chatInputTimeoutSeconds() + "秒后自动取消)"));
    }

    public void cancel(Player player, boolean notify) {
        cancelTimeout(player.getUniqueId());
        if (pendingInputs.remove(player.getUniqueId()) != null && notify) {
            player.sendMessage(plugin.color("&6[Claim] &f已取消当前设置输入。"));
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
            player.sendMessage(plugin.color("&6[Claim] &f已取消当前设置输入。"));
            return;
        }

        Claim claim = claimService.findClaimById(pending.claimId()).orElse(null);
        if (claim == null) {
            player.sendMessage(plugin.message("claim-not-found"));
            return;
        }
        if (!claim.owner().equals(player.getUniqueId()) && !player.hasPermission("coreclaim.admin")) {
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
            player.sendMessage(plugin.color("&6[Claim] &c领地名字不能为空。"));
            return;
        }
        if (message.length() > plugin.settings().claimNameMaxLength()) {
            player.sendMessage(plugin.color("&6[Claim] &c领地名字太长，最多 " + plugin.settings().claimNameMaxLength() + " 个字符。"));
            return;
        }
        if (claimService.isClaimNameTaken(message, claim.id())) {
            player.sendMessage(plugin.message("claim-name-exists", "{name}", message.trim()));
            return;
        }
        try {
            claimService.renameClaim(claim, message);
        } catch (IllegalArgumentException exception) {
            player.sendMessage(plugin.message("claim-name-exists", "{name}", message.trim()));
            return;
        }
        player.sendMessage(plugin.color("&6[Claim] &a已将领地重命名为 &e" + message.trim() + "&a。"));
    }

    private void handleEnterMessage(Player player, Claim claim, String message) {
        if (isClear(message)) {
            claimService.updateEnterMessage(claim, "");
            player.sendMessage(plugin.color("&6[Claim] &a已恢复默认进入提示。"));
            return;
        }
        if (message.isBlank()) {
            player.sendMessage(plugin.color("&6[Claim] &c进入提示不能为空，或输入 &e清空 &c恢复默认。"));
            return;
        }
        if (message.length() > NOTIFY_MAX_LENGTH) {
            player.sendMessage(plugin.color("&6[Claim] &c进入提示太长，最多 " + NOTIFY_MAX_LENGTH + " 个字符。"));
            return;
        }
        claimService.updateEnterMessage(claim, message);
        player.sendMessage(plugin.color("&6[Claim] &a已更新进入提示。"));
    }

    private void handleLeaveMessage(Player player, Claim claim, String message) {
        if (isClear(message)) {
            claimService.updateLeaveMessage(claim, "");
            player.sendMessage(plugin.color("&6[Claim] &a已恢复默认离开提示。"));
            return;
        }
        if (message.isBlank()) {
            player.sendMessage(plugin.color("&6[Claim] &c离开提示不能为空，或输入 &e清空 &c恢复默认。"));
            return;
        }
        if (message.length() > NOTIFY_MAX_LENGTH) {
            player.sendMessage(plugin.color("&6[Claim] &c离开提示太长，最多 " + NOTIFY_MAX_LENGTH + " 个字符。"));
            return;
        }
        claimService.updateLeaveMessage(claim, message);
        player.sendMessage(plugin.color("&6[Claim] &a已更新离开提示。"));
    }

    private void scheduleTimeout(UUID playerId) {
        cancelTimeout(playerId);
        long delayTicks = plugin.settings().chatInputTimeoutSeconds() * 20L;
        com.coreclaim.platform.PlatformScheduler.TaskHandle handle = plugin.platformScheduler().runLater(() -> timeout(playerId), delayTicks);
        timeoutTasks.put(playerId, handle);
    }

    private void cancelTimeout(UUID playerId) {
        com.coreclaim.platform.PlatformScheduler.TaskHandle handle = timeoutTasks.remove(playerId);
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
        return "取消".equals(message) || "cancel".equalsIgnoreCase(message);
    }

    private boolean isClear(String message) {
        return "清空".equals(message) || "clear".equalsIgnoreCase(message);
    }

    private record PendingInput(int claimId, InputMode mode) {
    }

    private enum InputMode {
        RENAME,
        ENTER_MESSAGE,
        LEAVE_MESSAGE
    }
}
