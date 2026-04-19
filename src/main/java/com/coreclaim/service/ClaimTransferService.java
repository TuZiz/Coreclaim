package com.coreclaim.service;

import com.coreclaim.CoreClaimPlugin;
import com.coreclaim.config.ClaimGroup;
import com.coreclaim.model.Claim;
import com.coreclaim.platform.PlatformScheduler;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public final class ClaimTransferService {

    private final CoreClaimPlugin plugin;
    private final ClaimService claimService;
    private final ProfileService profileService;
    private final Map<UUID, PendingTransfer> pendingTransfers = new ConcurrentHashMap<>();

    public ClaimTransferService(CoreClaimPlugin plugin, ClaimService claimService, ProfileService profileService) {
        this.plugin = plugin;
        this.claimService = claimService;
        this.profileService = profileService;
    }

    public boolean requestTransfer(Player sender, Claim claim, Player target) {
        if (claim == null) {
            sender.sendMessage(plugin.message("claim-not-found"));
            return false;
        }
        if (claim.systemManaged()) {
            sender.sendMessage(chatMessage("system-claim-transfer-denied", "&c&l! &7系统领地不能被普通转让。"));
            return false;
        }
        if (!claim.owner().equals(sender.getUniqueId())) {
            sender.sendMessage(plugin.message("trust-no-permission"));
            return false;
        }
        if (target == null || !target.isOnline()) {
            sender.sendMessage(plugin.message("target-must-online"));
            return false;
        }
        if (sender.getUniqueId().equals(target.getUniqueId())) {
            sender.sendMessage(plugin.message("transfer-self"));
            return false;
        }

        cancelPending(target.getUniqueId());
        long timeoutSeconds = Math.max(1L, plugin.getConfig().getLong("transfer.request-timeout-seconds", 60L));
        PendingTransfer request = new PendingTransfer(
            claim.id(),
            claim.owner(),
            sender.getUniqueId(),
            sender.getName(),
            target.getUniqueId(),
            target.getName(),
            System.currentTimeMillis() + timeoutSeconds * 1000L
        );
        PlatformScheduler.TaskHandle task = plugin.platformScheduler().runLater(() -> expire(request), timeoutSeconds * 20L);
        request.timeoutTask = task;
        pendingTransfers.put(target.getUniqueId(), request);

        sender.sendMessage(plugin.message(
            "transfer-request-sent",
            "{name}", claim.name(),
            "{player}", target.getName(),
            "{seconds}", String.valueOf(timeoutSeconds)
        ));
        String targetMessage = plugin.message(
            "transfer-request-received",
            "{name}", claim.name(),
            "{owner}", sender.getName(),
            "{seconds}", String.valueOf(timeoutSeconds)
        );
        plugin.platformScheduler().runPlayerTask(target, () -> target.sendMessage(targetMessage));
        return true;
    }

    public boolean accept(Player target) {
        PendingTransfer request = pendingTransfers.remove(target.getUniqueId());
        if (request == null) {
            target.sendMessage(plugin.message("transfer-no-pending"));
            return false;
        }
        request.cancel();
        if (System.currentTimeMillis() > request.expiresAt) {
            target.sendMessage(plugin.message("transfer-expired"));
            return false;
        }

        Claim claim = claimService.findClaimByIdFresh(request.claimId).orElse(null);
        if (claim == null || !claim.owner().equals(request.originalOwner)) {
            target.sendMessage(plugin.message("transfer-expired"));
            return false;
        }
        if (!hasClaimSlot(target)) {
            target.sendMessage(plugin.message("transfer-target-no-slot"));
            notifySender(request, plugin.message(
                "transfer-target-no-slot-sender",
                "{player}", target.getName(),
                "{name}", claim.name()
            ));
            return false;
        }
        if (!claimService.transferClaim(claim, target.getUniqueId(), target.getName())) {
            target.sendMessage(plugin.message("transfer-failed"));
            return false;
        }

        target.sendMessage(plugin.message("transfer-accepted-target", "{name}", claim.name()));
        notifySender(request, plugin.message("transfer-accepted-owner", "{player}", target.getName(), "{name}", claim.name()));
        return true;
    }

    public boolean deny(Player target) {
        PendingTransfer request = pendingTransfers.remove(target.getUniqueId());
        if (request == null) {
            target.sendMessage(plugin.message("transfer-no-pending"));
            return false;
        }
        request.cancel();
        Claim claim = claimService.findClaimByIdFresh(request.claimId).orElse(null);
        String claimName = claim == null ? String.valueOf(request.claimId) : claim.name();
        target.sendMessage(plugin.message("transfer-denied-target", "{name}", claimName));
        notifySender(request, plugin.message("transfer-denied-owner", "{player}", target.getName(), "{name}", claimName));
        return true;
    }

    public boolean forceTransfer(CommandSender sender, Claim claim, Player target) {
        if (claim == null) {
            sender.sendMessage(plugin.message("claim-not-found"));
            return false;
        }
        if (claim.systemManaged()) {
            sender.sendMessage(chatMessage("system-claim-transfer-denied", "&c&l! &7系统领地不能被转让。"));
            return false;
        }
        if (target == null || !target.isOnline()) {
            sender.sendMessage(plugin.message("target-must-online"));
            return false;
        }
        if (claim.owner().equals(target.getUniqueId())) {
            sender.sendMessage(plugin.message("transfer-self"));
            return false;
        }
        if (!hasClaimSlot(target)) {
            sender.sendMessage(plugin.message("transfer-target-no-slot"));
            return false;
        }
        String oldOwner = claim.ownerName();
        if (!claimService.transferClaim(claim, target.getUniqueId(), target.getName())) {
            sender.sendMessage(plugin.message("transfer-failed"));
            return false;
        }
        sender.sendMessage(plugin.message(
            "admin-transfer-success",
            "{name}", claim.name(),
            "{old_owner}", oldOwner,
            "{player}", target.getName()
        ));
        String targetMessage = plugin.message("transfer-admin-received", "{name}", claim.name());
        plugin.platformScheduler().runPlayerTask(target, () -> target.sendMessage(targetMessage));
        return true;
    }

    public void clear() {
        for (PendingTransfer request : pendingTransfers.values()) {
            request.cancel();
        }
        pendingTransfers.clear();
    }

    private boolean hasClaimSlot(Player target) {
        ClaimGroup group = plugin.groups().resolve(target);
        int maxClaims = group.maxClaims();
        return claimService.countClaims(target.getUniqueId()) < maxClaims;
    }

    private String chatMessage(String path, String fallback, String... replacements) {
        String prefix = plugin.messagesConfig().getString("prefix", "&8[&6Claim&8] &f");
        String body = plugin.messagesConfig().contains(path) ? plugin.messagesConfig().getString(path, fallback) : fallback;
        String message = plugin.color(prefix + body);
        for (int index = 0; index + 1 < replacements.length; index += 2) {
            message = message.replace(replacements[index], replacements[index + 1]);
        }
        return message;
    }

    private void expire(PendingTransfer request) {
        if (!pendingTransfers.remove(request.targetId, request)) {
            return;
        }
        Player target = Bukkit.getPlayer(request.targetId);
        if (target != null) {
            plugin.platformScheduler().runPlayerTask(target, () -> target.sendMessage(plugin.message("transfer-expired")));
        }
        notifySender(request, plugin.message("transfer-expired-owner", "{player}", request.targetName));
    }

    private void cancelPending(UUID targetId) {
        PendingTransfer oldRequest = pendingTransfers.remove(targetId);
        if (oldRequest != null) {
            oldRequest.cancel();
        }
    }

    private void notifySender(PendingTransfer request, String message) {
        Player sender = Bukkit.getPlayer(request.senderId);
        if (sender != null) {
            plugin.platformScheduler().runPlayerTask(sender, () -> sender.sendMessage(message));
        }
    }

    private static final class PendingTransfer {
        private final int claimId;
        private final UUID originalOwner;
        private final UUID senderId;
        private final String senderName;
        private final UUID targetId;
        private final String targetName;
        private final long expiresAt;
        private PlatformScheduler.TaskHandle timeoutTask;

        private PendingTransfer(
            int claimId,
            UUID originalOwner,
            UUID senderId,
            String senderName,
            UUID targetId,
            String targetName,
            long expiresAt
        ) {
            this.claimId = claimId;
            this.originalOwner = originalOwner;
            this.senderId = senderId;
            this.senderName = senderName;
            this.targetId = targetId;
            this.targetName = targetName;
            this.expiresAt = expiresAt;
        }

        private void cancel() {
            if (timeoutTask != null) {
                timeoutTask.cancel();
            }
        }
    }
}
