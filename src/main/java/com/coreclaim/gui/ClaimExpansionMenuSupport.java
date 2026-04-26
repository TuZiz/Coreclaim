package com.coreclaim.gui;

import com.coreclaim.CoreClaimPlugin;
import com.coreclaim.config.ClaimGroup;
import com.coreclaim.model.Claim;
import com.coreclaim.model.ClaimDirection;
import com.coreclaim.service.ClaimActionService;
import org.bukkit.entity.Player;

public final class ClaimExpansionMenuSupport {

    private final CoreClaimPlugin plugin;
    private final ClaimActionService claimActionService;

    ClaimExpansionMenuSupport(CoreClaimPlugin plugin, ClaimActionService claimActionService) {
        this.plugin = plugin;
        this.claimActionService = claimActionService;
    }

    public int normalizeAmount(Player player, Claim claim, ClaimDirection direction, int requestedAmount) {
        int maxAmount = maxAmount(player, claim, direction);
        if (maxAmount <= 0) {
            return 1;
        }
        return Math.max(1, Math.min(requestedAmount, maxAmount));
    }

    public int maxAmount(Player player, Claim claim, ClaimDirection direction) {
        ClaimGroup group = plugin.groups().resolve(player);
        return Math.max(0, group.maxDistance() - claim.distance(direction));
    }

    public String[] replacements(Player player, Claim claim, ClaimDirection direction, int amount, ClaimActionService.ExpansionPreview preview) {
        int maxAmount = maxAmount(player, claim, direction);
        return new String[] {
            "{name}", claim.name(),
            "{direction}", directionLabel(direction),
            "{amount}", String.valueOf(amount),
            "{max}", String.valueOf(maxAmount),
            "{current}", String.valueOf(claim.distance(direction)),
            "{target}", String.valueOf(preview.targetDistance()),
            "{price}", preview.costText(),
            "{width}", String.valueOf(preview.width()),
            "{depth}", String.valueOf(preview.depth()),
            "{status}", statusText(player, preview)
        };
    }

    public String directionLabel(ClaimDirection direction) {
        return switch (direction) {
            case NORTH -> "北";
            case SOUTH -> "南";
            case WEST -> "西";
            case EAST -> "东";
        };
    }

    private String statusText(Player player, ClaimActionService.ExpansionPreview preview) {
        if (preview.hitMax()) {
            return "&#FF6B6B已到达该方向上限";
        }
        if (preview.overlap()) {
            return "&#FF6B6B扩建后会与其他领地重叠";
        }
        if (!preview.allowed()) {
            return "&#FF6B6B当前无法扩建";
        }
        if (!claimActionService.hasExpansionEconomy(preview)) {
            return "&#FF6B6B经济系统不可用";
        }
        if (!claimActionService.canPayExpansionCost(player, preview)) {
            return "&#FF6B6B余额不足";
        }
        return "&#55FFAA可以扩建";
    }
}
