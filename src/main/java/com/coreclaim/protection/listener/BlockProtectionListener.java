package com.coreclaim.protection.listener;

import com.coreclaim.model.Claim;
import com.coreclaim.model.ClaimPermission;
import com.coreclaim.claim.auth.ClaimAuthorizationService.AuthorizationDecision;
import java.util.Optional;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.block.Block;

public final class BlockProtectionListener implements Listener {

    private final ProtectionRuleSupport support;

    public BlockProtectionListener(ProtectionRuleSupport support) {
        this.support = support;
    }

    @EventHandler(ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        Optional<Claim> claim = support.claimService().findClaim(event.getBlock().getLocation());
        if (claim.isEmpty()) {
            return;
        }

        if (support.isBypassing(event.getPlayer())) {
            return;
        }
        if (support.isCoreBlock(event.getBlock(), claim.get()) && !claim.get().owner().equals(event.getPlayer().getUniqueId())) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(support.plugin().message("trust-no-permission"));
            return;
        }
        if (!support.claimService().hasPermission(claim.get(), event.getPlayer().getUniqueId(), ClaimPermission.BREAK)) {
            event.setCancelled(true);
            support.sendProtectionDeny(event.getPlayer(), claim.get());
            return;
        }

        if (support.isCoreBlock(event.getBlock(), claim.get())) {
            return;
        }
        support.claimCleanupService().recordBuildActivity(claim.get(), event.getPlayer().getUniqueId());
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        ItemStack item = event.getItemInHand();
        if (support.isCoreItem(item)) {
            return;
        }

        Optional<Claim> claim = support.claimService().findClaim(event.getBlockPlaced().getLocation());
        if (claim.isPresent() && !support.isBypassing(event.getPlayer()) && !support.claimService().hasPermission(claim.get(), event.getPlayer().getUniqueId(), ClaimPermission.PLACE)) {
            event.setCancelled(true);
            support.sendProtectionDeny(event.getPlayer(), claim.get());
            return;
        }
        if (claim.isPresent() && !support.isBypassing(event.getPlayer())) {
            support.claimCleanupService().recordBuildActivity(claim.get(), event.getPlayer().getUniqueId());
        }
    }

    @EventHandler(ignoreCancelled = false)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getClickedBlock() == null) {
            return;
        }
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK && event.getAction() != Action.PHYSICAL) {
            return;
        }
        if (event.getAction() == Action.RIGHT_CLICK_BLOCK && event.getHand() != EquipmentSlot.HAND) {
            return;
        }

        Optional<Claim> claim = support.claimService().findClaim(event.getClickedBlock().getLocation());
        if (claim.isPresent() && support.isCoreBlock(event.getClickedBlock(), claim.get())) {
            debugInteract(event, claim, "core-block-skip", null);
            return;
        }
        Material clickedType = event.getClickedBlock().getType();
        boolean containerInteraction = support.isContainerMaterial(clickedType);
        debugInteract(event, claim, "start", null);
        if (claim.isPresent() && support.isBypassing(event.getPlayer())) {
            handleBypassKnownInteraction(event, claim, clickedType);
            return;
        }
        if (event.isCancelled()) {
            handlePreCancelledKnownInteraction(event, claim, clickedType);
            return;
        }
        if (claim.isPresent() && support.isCakeConsumption(clickedType, event.getItem())) {
            AuthorizationDecision interactDecision = support.claimService().permissionDecision(
                claim.get(),
                event.getPlayer().getUniqueId(),
                ClaimPermission.INTERACT,
                false
            );
            if (!interactDecision.allowed()) {
                event.setCancelled(true);
                support.sendProtectionDeny(event.getPlayer(), claim.get());
                debugInteract(event, claim, "cake-deny", "interact=" + formatDecision(interactDecision));
                return;
            }
            boolean applied = ProtectionInteractionCompat.applyCakeConsumption(event);
            support.recordBlockInteraction(claim.get(), event.getPlayer(), ClaimPermission.INTERACT);
            debugInteract(event, claim, "cake-allow", "interact=" + formatDecision(interactDecision) + " applied=" + applied);
            return;
        }
        if (claim.isPresent() && support.isComposterCompostInput(event.getClickedBlock(), event.getItem())) {
            if (!support.isBypassing(event.getPlayer())) {
                support.claimCleanupService().recordInteractionActivity(claim.get(), event.getPlayer().getUniqueId());
            }
            return;
        }
        ClaimPermission toolChangePermission = support.requiredPermissionForBlockToolChange(clickedType, event.getItem());
        if (claim.isPresent() && toolChangePermission != null) {
            boolean blockDrivenToolChange = support.isBlockDrivenToolChange(clickedType, event.getItem());
            if (support.isAxeStrippingWood(clickedType, event.getItem())) {
                boolean canAccess = support.claimService().canAccess(claim.get(), event.getPlayer().getUniqueId());
                if (!canAccess) {
                    event.setCancelled(true);
                    support.sendProtectionDeny(event.getPlayer(), claim.get());
                    debugInteract(event, claim, "axe-strip-deny", "canAccess=" + canAccess);
                    return;
                }
                ProtectionInteractionCompat.applyAxeStripping(event, support);
                support.recordBlockInteraction(claim.get(), event.getPlayer(), ClaimPermission.BREAK);
                debugInteract(event, claim, "axe-strip-allow", "canAccess=" + canAccess);
                return;
            }
            boolean canUseTool = support.claimService().hasPermission(claim.get(), event.getPlayer().getUniqueId(), toolChangePermission);
            if (!canUseTool) {
                if (containerInteraction
                    && support.claimService().hasPermission(claim.get(), event.getPlayer().getUniqueId(), ClaimPermission.INTERACT)) {
                    event.setUseItemInHand(Event.Result.DENY);
                    support.claimCleanupService().recordInteractionActivity(claim.get(), event.getPlayer().getUniqueId());
                    debugInteract(event, claim, "tool-change-container-interact", "permission=" + toolChangePermission);
                    return;
                }
                event.setCancelled(true);
                support.sendProtectionDeny(event.getPlayer(), claim.get());
                debugInteract(event, claim, "tool-change-deny", "permission=" + toolChangePermission);
                return;
            }
            if (containerInteraction && !blockDrivenToolChange) {
                event.setUseInteractedBlock(Event.Result.DENY);
                event.setUseItemInHand(Event.Result.ALLOW);
            }
            if (blockDrivenToolChange) {
                boolean applied = ProtectionInteractionCompat.applyBlockDrivenToolChange(event);
                debugInteract(event, claim, "tool-change-block-use-allow", "permission=" + toolChangePermission + " applied=" + applied);
            }
            support.recordBlockInteraction(claim.get(), event.getPlayer(), toolChangePermission);
            return;
        }
        ClaimPermission requiredPermission = support.requiredPermissionForBlockInteract(event.getClickedBlock(), clickedType, event.getItem());
        boolean allowListed = support.plugin().settings().isAllowedInteract(clickedType)
            && !(support.plugin().settings().strictRedstoneInteract() && support.plugin().settings().isAlwaysProtectedInteract(clickedType));
        if (claim.isPresent() && allowListed) {
            if (!support.isBypassing(event.getPlayer())) {
                support.recordBlockInteraction(claim.get(), event.getPlayer(), requiredPermission);
            }
            return;
        }
        if (claim.isPresent() && requiredPermission == ClaimPermission.EXPLOSION && !support.isBypassing(event.getPlayer())
            && support.claimService().hasPermission(claim.get(), event.getPlayer().getUniqueId(), ClaimPermission.EXPLOSION)) {
            support.explosionAuthorizationService().authorize(event.getClickedBlock().getLocation());
        }
        if (claim.isPresent() && !support.isBypassing(event.getPlayer()) && !support.claimService().hasPermission(claim.get(), event.getPlayer().getUniqueId(), requiredPermission)) {
            event.setCancelled(true);
            support.sendProtectionDeny(event.getPlayer(), claim.get());
            debugInteract(event, claim, "generic-deny", "permission=" + requiredPermission);
            return;
        }
        if (claim.isPresent() && !support.isBypassing(event.getPlayer())) {
            support.recordBlockInteraction(claim.get(), event.getPlayer(), requiredPermission);
        }
        debugInteract(event, claim, "generic-allow", "permission=" + requiredPermission + " allowListed=" + allowListed);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = false)
    public void onInteractMonitor(PlayerInteractEvent event) {
        if (!support.plugin().settings().protectionDebug()) {
            return;
        }
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getHand() != EquipmentSlot.HAND || event.getClickedBlock() == null) {
            return;
        }
        Material clickedType = event.getClickedBlock().getType();
        boolean cakeConsumption = support.isCakeConsumption(clickedType, event.getItem());
        boolean axeStrippingWood = support.isAxeStrippingWood(clickedType, event.getItem());
        boolean blockDrivenToolChange = support.isBlockDrivenToolChange(clickedType, event.getItem());
        if (!cakeConsumption && !axeStrippingWood && !blockDrivenToolChange) {
            return;
        }
        Optional<Claim> claim = support.claimService().findClaim(event.getClickedBlock().getLocation());
        debugInteract(
            event,
            claim,
            event.isCancelled() || event.useInteractedBlock() == Event.Result.DENY ? "monitor-blocked" : "monitor-final",
            "cake=" + cakeConsumption + " axeStrip=" + axeStrippingWood + " blockTool=" + blockDrivenToolChange
        );
    }

    private void handleBypassKnownInteraction(PlayerInteractEvent event, Optional<Claim> claim, Material clickedType) {
        if (support.isCakeConsumption(clickedType, event.getItem())) {
            boolean applied = ProtectionInteractionCompat.applyCakeConsumption(event);
            debugInteract(event, claim, "bypass-cake", "applied=" + applied);
            return;
        }
        if (support.isAxeStrippingWood(clickedType, event.getItem())) {
            boolean applied = ProtectionInteractionCompat.applyAxeStripping(event, support);
            debugInteract(event, claim, "bypass-axe-strip", "applied=" + applied);
            return;
        }
        if (support.isBlockDrivenToolChange(clickedType, event.getItem())) {
            boolean applied = ProtectionInteractionCompat.applyBlockDrivenToolChange(event);
            debugInteract(event, claim, "bypass-block-tool", "applied=" + applied);
            return;
        }
        debugInteract(event, claim, "bypass-skip", null);
    }

    private void handlePreCancelledKnownInteraction(PlayerInteractEvent event, Optional<Claim> claim, Material clickedType) {
        if (claim.isEmpty()) {
            debugInteract(event, claim, "pre-cancel-no-claim", null);
            return;
        }
        boolean cakeConsumption = support.isCakeConsumption(clickedType, event.getItem());
        boolean axeStrippingWood = support.isAxeStrippingWood(clickedType, event.getItem());
        boolean blockDrivenToolChange = support.isBlockDrivenToolChange(clickedType, event.getItem());
        if (!cakeConsumption && !axeStrippingWood && !blockDrivenToolChange) {
            debugInteract(event, claim, "pre-cancel-unknown", "clicked=" + clickedType);
            return;
        }

        boolean bypassing = support.isBypassing(event.getPlayer());
        AuthorizationDecision interactDecision = support.claimService().permissionDecision(
            claim.get(),
            event.getPlayer().getUniqueId(),
            ClaimPermission.INTERACT,
            bypassing
        );
        boolean hasInteractPermission = interactDecision.allowed();
        boolean canAccess = bypassing
            || support.claimService().canAccess(claim.get(), event.getPlayer().getUniqueId());
        ClaimPermission toolChangePermission = blockDrivenToolChange
            ? support.requiredPermissionForBlockToolChange(clickedType, event.getItem())
            : null;
        AuthorizationDecision toolChangeDecision = toolChangePermission == null
            ? interactDecision
            : support.claimService().permissionDecision(
                claim.get(),
                event.getPlayer().getUniqueId(),
                toolChangePermission,
                bypassing
            );
        boolean hasToolChangePermission = toolChangeDecision.allowed();
        PreCancelledInteractionResolution resolution = resolvePreCancelledInteraction(
            true,
            cakeConsumption,
            axeStrippingWood,
            blockDrivenToolChange,
            bypassing,
            hasInteractPermission,
            hasToolChangePermission,
            canAccess
        );
        debugInteract(
            event,
            claim,
            "pre-cancel-resolve",
            "cake=" + cakeConsumption
                + " axeStrip=" + axeStrippingWood
                + " blockTool=" + blockDrivenToolChange
                + " interact=" + formatDecision(interactDecision)
                + " tool=" + formatDecision(toolChangeDecision)
                + " canAccess=" + canAccess
                + " resolution=" + resolution
        );
        switch (resolution) {
            case ALLOW_CAKE_CONSUMPTION -> {
                boolean applied = ProtectionInteractionCompat.applyCakeConsumption(event);
                if (!bypassing) {
                    support.recordBlockInteraction(claim.get(), event.getPlayer(), ClaimPermission.INTERACT);
                }
                debugInteract(event, claim, "pre-cancel-cake-allow", "interact=" + formatDecision(interactDecision) + " applied=" + applied);
            }
            case ALLOW_AXE_STRIPPING -> {
                ProtectionInteractionCompat.applyAxeStripping(event, support);
                if (!bypassing) {
                    support.recordBlockInteraction(claim.get(), event.getPlayer(), ClaimPermission.BREAK);
                }
                debugInteract(event, claim, "pre-cancel-axe-strip-allow", "canAccess=" + canAccess);
            }
            case ALLOW_BLOCK_DRIVEN_TOOL_CHANGE -> {
                boolean applied = ProtectionInteractionCompat.applyBlockDrivenToolChange(event);
                if (!bypassing) {
                    support.recordBlockInteraction(
                        claim.get(),
                        event.getPlayer(),
                        toolChangePermission == null ? ClaimPermission.INTERACT : toolChangePermission
                    );
                }
                debugInteract(event, claim, "pre-cancel-block-tool-allow", "tool=" + formatDecision(toolChangeDecision) + " applied=" + applied);
            }
            case DENY -> {
                event.setCancelled(true);
                support.sendProtectionDeny(event.getPlayer(), claim.get());
                debugInteract(
                    event,
                    claim,
                    "pre-cancel-deny",
                    "interact=" + formatDecision(interactDecision)
                        + " tool=" + formatDecision(toolChangeDecision)
                        + " canAccess=" + canAccess
                );
            }
            case IGNORE -> {
                debugInteract(event, claim, "pre-cancel-ignore", null);
            }
        }
    }

    static PreCancelledInteractionResolution resolvePreCancelledInteraction(
        boolean claimPresent,
        boolean cakeConsumption,
        boolean axeStrippingWood,
        boolean bypassing,
        boolean hasInteractPermission,
        boolean canAccess
    ) {
        return resolvePreCancelledInteraction(
            claimPresent,
            cakeConsumption,
            axeStrippingWood,
            false,
            bypassing,
            hasInteractPermission,
            false,
            canAccess
        );
    }

    static PreCancelledInteractionResolution resolvePreCancelledInteraction(
        boolean claimPresent,
        boolean cakeConsumption,
        boolean axeStrippingWood,
        boolean blockDrivenToolChange,
        boolean bypassing,
        boolean hasInteractPermission,
        boolean hasToolChangePermission,
        boolean canAccess
    ) {
        if (!claimPresent) {
            return PreCancelledInteractionResolution.IGNORE;
        }
        if (cakeConsumption) {
            return bypassing || hasInteractPermission
                ? PreCancelledInteractionResolution.ALLOW_CAKE_CONSUMPTION
                : PreCancelledInteractionResolution.DENY;
        }
        if (axeStrippingWood) {
            return bypassing || canAccess
                ? PreCancelledInteractionResolution.ALLOW_AXE_STRIPPING
                : PreCancelledInteractionResolution.DENY;
        }
        if (blockDrivenToolChange) {
            return bypassing || hasToolChangePermission
                ? PreCancelledInteractionResolution.ALLOW_BLOCK_DRIVEN_TOOL_CHANGE
                : PreCancelledInteractionResolution.DENY;
        }
        return PreCancelledInteractionResolution.IGNORE;
    }

    enum PreCancelledInteractionResolution {
        IGNORE,
        DENY,
        ALLOW_CAKE_CONSUMPTION,
        ALLOW_AXE_STRIPPING,
        ALLOW_BLOCK_DRIVEN_TOOL_CHANGE
    }

    @EventHandler(ignoreCancelled = true)
    public void onBucketEmpty(PlayerBucketEmptyEvent event) {
        Location target = event.getBlockClicked().getRelative(event.getBlockFace()).getLocation();
        Optional<Claim> claim = support.claimService().findClaim(target);
        if (claim.isPresent() && !support.isBypassing(event.getPlayer()) && !support.claimService().hasPermission(claim.get(), event.getPlayer().getUniqueId(), ClaimPermission.BUCKET)) {
            event.setCancelled(true);
            support.sendProtectionDeny(event.getPlayer(), claim.get());
            return;
        }
        if (claim.isPresent() && !support.isBypassing(event.getPlayer())) {
            support.claimCleanupService().recordBuildActivity(claim.get(), event.getPlayer().getUniqueId());
        }
    }

    private void debugInteract(PlayerInteractEvent event, Optional<Claim> claim, String phase, String detail) {
        if (!support.plugin().settings().protectionDebug()) {
            return;
        }
        Block block = event.getClickedBlock();
        ItemStack item = event.getItem();
        if (!shouldLogProtectionDebug(event, claim, phase, block, item)) {
            return;
        }
        boolean bypassing = support.isBypassing(event.getPlayer());
        StringBuilder message = new StringBuilder("[ClaimProtection] phase=")
            .append(phase)
            .append(" player=").append(event.getPlayer().getName())
            .append("(").append(event.getPlayer().getUniqueId()).append(")")
            .append(" action=").append(event.getAction())
            .append(" hand=").append(event.getHand())
            .append(" clicked=").append(block == null ? "null" : block.getType())
            .append(" item=").append(item == null ? "AIR" : item.getType())
            .append(" claim=").append(formatClaim(claim))
            .append(" bypassing=").append(bypassing)
            .append(" cancelled=").append(event.isCancelled())
            .append(" useBlock=").append(event.useInteractedBlock())
            .append(" useItem=").append(event.useItemInHand())
            .append(" food=").append(event.getPlayer().getFoodLevel())
            .append(" gameMode=").append(event.getPlayer().getGameMode())
            .append(" sneaking=").append(event.getPlayer().isSneaking());
        if (claim.isPresent()) {
            AuthorizationDecision breakDecision = support.claimService().permissionDecision(
                claim.get(),
                event.getPlayer().getUniqueId(),
                ClaimPermission.BREAK,
                bypassing
            );
            AuthorizationDecision interactDecision = support.claimService().permissionDecision(
                claim.get(),
                event.getPlayer().getUniqueId(),
                ClaimPermission.INTERACT,
                bypassing
            );
            message.append(" break=").append(formatDecision(breakDecision))
                .append(" interact=").append(formatDecision(interactDecision))
                .append(" canAccess=").append(bypassing || support.claimService().canAccess(claim.get(), event.getPlayer().getUniqueId()));
        }
        if (detail != null && !detail.isBlank()) {
            message.append(" detail=").append(detail);
        }
        support.plugin().getLogger().info(message.toString());
    }

    private static String formatClaim(Optional<Claim> claim) {
        return claim.map(value -> value.id() + ":" + value.name()).orElse("none");
    }

    private static String formatDecision(AuthorizationDecision decision) {
        return decision.source() + "/" + decision.allowed();
    }

    private boolean shouldLogProtectionDebug(
        PlayerInteractEvent event,
        Optional<Claim> claim,
        String phase,
        Block block,
        ItemStack item
    ) {
        if (phase.contains("deny") || phase.startsWith("pre-cancel") || phase.equals("monitor-blocked") || phase.equals("monitor-final")) {
            return true;
        }
        if (phase.startsWith("cake")
            || phase.startsWith("axe-strip")
            || phase.startsWith("bypass-")
            || phase.equals("tool-change-block-use-allow")) {
            return true;
        }
        if (block == null) {
            return false;
        }
        return phase.equals("bypass-skip")
            && (support.isCakeConsumption(block.getType(), item)
            || support.isAxeStrippingWood(block.getType(), item)
            || support.isBlockDrivenToolChange(block.getType(), item));
    }

    @EventHandler(ignoreCancelled = true)
    public void onBucketFill(PlayerBucketFillEvent event) {
        Optional<Claim> claim = support.claimService().findClaim(event.getBlockClicked().getLocation());
        if (claim.isPresent() && !support.isBypassing(event.getPlayer()) && !support.claimService().hasPermission(claim.get(), event.getPlayer().getUniqueId(), ClaimPermission.BUCKET)) {
            event.setCancelled(true);
            support.sendProtectionDeny(event.getPlayer(), claim.get());
            return;
        }
        if (claim.isPresent() && !support.isBypassing(event.getPlayer())) {
            support.claimCleanupService().recordBuildActivity(claim.get(), event.getPlayer().getUniqueId());
        }
    }
}
