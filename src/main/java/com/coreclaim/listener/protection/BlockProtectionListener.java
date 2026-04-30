package com.coreclaim.listener.protection;

import com.coreclaim.model.Claim;
import com.coreclaim.model.ClaimPermission;
import java.util.Optional;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

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

    @EventHandler(ignoreCancelled = true)
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
            return;
        }
        Material clickedType = event.getClickedBlock().getType();
        boolean containerInteraction = support.isContainerMaterial(clickedType);
        if (claim.isPresent() && support.isComposterCompostInput(event.getClickedBlock(), event.getItem())) {
            if (!support.isBypassing(event.getPlayer())) {
                support.claimCleanupService().recordInteractionActivity(claim.get(), event.getPlayer().getUniqueId());
            }
            return;
        }
        ClaimPermission toolChangePermission = support.requiredPermissionForBlockToolChange(clickedType, event.getItem());
        if (claim.isPresent() && toolChangePermission != null) {
            boolean bypassing = support.isBypassing(event.getPlayer());
            boolean canUseTool = bypassing
                || support.claimService().hasPermission(claim.get(), event.getPlayer().getUniqueId(), toolChangePermission);
            if (!canUseTool) {
                if (containerInteraction
                    && support.claimService().hasPermission(claim.get(), event.getPlayer().getUniqueId(), ClaimPermission.INTERACT)) {
                    event.setUseItemInHand(Event.Result.DENY);
                    support.claimCleanupService().recordInteractionActivity(claim.get(), event.getPlayer().getUniqueId());
                    return;
                }
                event.setCancelled(true);
                support.sendProtectionDeny(event.getPlayer(), claim.get());
                return;
            }
            if (containerInteraction) {
                event.setUseInteractedBlock(Event.Result.DENY);
                event.setUseItemInHand(Event.Result.ALLOW);
            }
            if (!bypassing) {
                support.recordBlockInteraction(claim.get(), event.getPlayer(), toolChangePermission);
            }
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
            return;
        }
        if (claim.isPresent() && !support.isBypassing(event.getPlayer())) {
            support.recordBlockInteraction(claim.get(), event.getPlayer(), requiredPermission);
        }
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
