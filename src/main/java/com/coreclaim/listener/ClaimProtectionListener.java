package com.coreclaim.listener;

import com.coreclaim.CoreClaimPlugin;
import com.coreclaim.item.ClaimCoreFactory;
import com.coreclaim.model.Claim;
import com.coreclaim.service.ClaimService;
import java.util.Iterator;
import java.util.Optional;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.projectiles.ProjectileSource;

public final class ClaimProtectionListener implements Listener {

    private final CoreClaimPlugin plugin;
    private final ClaimService claimService;
    private final ClaimCoreFactory claimCoreFactory;

    public ClaimProtectionListener(CoreClaimPlugin plugin, ClaimService claimService, ClaimCoreFactory claimCoreFactory) {
        this.plugin = plugin;
        this.claimService = claimService;
        this.claimCoreFactory = claimCoreFactory;
    }

    @EventHandler(ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        Optional<Claim> claim = claimService.findClaim(event.getBlock().getLocation());
        if (claim.isEmpty()) {
            return;
        }

        Player player = event.getPlayer();
        if (isBypassing(player)) {
            return;
        }
        if (!claimService.canAccess(claim.get(), player.getUniqueId())) {
            event.setCancelled(true);
            player.sendMessage(plugin.message("protection-deny"));
            return;
        }

        if (isCoreBlock(event.getBlock(), claim.get())) {
            return;
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        ItemStack item = event.getItemInHand();
        if (claimCoreFactory.isClaimCore(item)) {
            return;
        }

        Optional<Claim> claim = claimService.findClaim(event.getBlockPlaced().getLocation());
        if (claim.isPresent() && !isBypassing(event.getPlayer()) && !claimService.canAccess(claim.get(), event.getPlayer().getUniqueId())) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(plugin.message("protection-deny"));
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

        Optional<Claim> claim = claimService.findClaim(event.getClickedBlock().getLocation());
        if (claim.isPresent() && isCoreBlock(event.getClickedBlock(), claim.get())) {
            return;
        }
        if (claim.isPresent() && !isBypassing(event.getPlayer()) && !claimService.canAccess(claim.get(), event.getPlayer().getUniqueId())) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(plugin.message("protection-deny"));
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onBucketEmpty(PlayerBucketEmptyEvent event) {
        Location target = event.getBlockClicked().getRelative(event.getBlockFace()).getLocation();
        Optional<Claim> claim = claimService.findClaim(target);
        if (claim.isPresent() && !isBypassing(event.getPlayer()) && !claimService.canAccess(claim.get(), event.getPlayer().getUniqueId())) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(plugin.message("protection-deny"));
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onBucketFill(PlayerBucketFillEvent event) {
        Optional<Claim> claim = claimService.findClaim(event.getBlockClicked().getLocation());
        if (claim.isPresent() && !isBypassing(event.getPlayer()) && !claimService.canAccess(claim.get(), event.getPlayer().getUniqueId())) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(plugin.message("protection-deny"));
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onEntityInteract(PlayerInteractEntityEvent event) {
        Optional<Claim> claim = claimService.findClaim(event.getRightClicked().getLocation());
        if (claim.isPresent() && !isBypassing(event.getPlayer()) && !claimService.canAccess(claim.get(), event.getPlayer().getUniqueId())) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(plugin.message("protection-deny"));
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onEntityDamage(EntityDamageByEntityEvent event) {
        Player attacker = resolvePlayer(event.getDamager());
        if (attacker == null || event.getEntity() instanceof Player || isBypassing(attacker)) {
            return;
        }

        Optional<Claim> claim = claimService.findClaim(event.getEntity().getLocation());
        if (claim.isPresent() && !claimService.canAccess(claim.get(), attacker.getUniqueId())) {
            event.setCancelled(true);
            attacker.sendMessage(plugin.message("protection-deny"));
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onExplode(EntityExplodeEvent event) {
        Iterator<Block> iterator = event.blockList().iterator();
        while (iterator.hasNext()) {
            if (claimService.findClaim(iterator.next().getLocation()).isPresent()) {
                iterator.remove();
            }
        }
    }

    private boolean isBypassing(Player player) {
        return player.hasPermission("coreclaim.admin");
    }

    private boolean isCoreBlock(Block block, Claim claim) {
        return block.getType() == plugin.settings().coreMaterial()
            && block.getX() == claim.centerX()
            && block.getY() == claim.centerY()
            && block.getZ() == claim.centerZ();
    }

    private Player resolvePlayer(Entity entity) {
        if (entity instanceof Player player) {
            return player;
        }
        if (entity instanceof Projectile projectile) {
            ProjectileSource source = projectile.getShooter();
            if (source instanceof Player player) {
                return player;
            }
        }
        return null;
    }
}
