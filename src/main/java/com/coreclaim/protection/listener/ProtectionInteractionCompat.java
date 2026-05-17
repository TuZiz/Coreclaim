package com.coreclaim.protection.listener;

import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Directional;
import org.bukkit.block.data.Orientable;
import org.bukkit.block.data.Waterlogged;
import org.bukkit.block.data.type.Cake;
import org.bukkit.block.data.type.Campfire;
import org.bukkit.block.data.type.Chest;
import org.bukkit.block.data.type.Slab;
import org.bukkit.block.data.type.Stairs;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

final class ProtectionInteractionCompat {

    private ProtectionInteractionCompat() {
    }

    static void allowCakeConsumption(PlayerInteractEvent event) {
        event.setCancelled(false);
        event.setUseInteractedBlock(Event.Result.ALLOW);
        event.setUseItemInHand(Event.Result.DEFAULT);
    }

    static boolean applyCakeConsumption(PlayerInteractEvent event) {
        Block block = event.getClickedBlock();
        if (block == null || block.getType() != Material.CAKE || !(block.getBlockData() instanceof Cake cake)) {
            return false;
        }
        Player player = event.getPlayer();
        return applyCakeConsumption(new CakeConsumptionAccess() {
            @Override
            public GameMode gameMode() {
                return player.getGameMode();
            }

            @Override
            public int foodLevel() {
                return player.getFoodLevel();
            }

            @Override
            public void foodLevel(int foodLevel) {
                player.setFoodLevel(foodLevel);
            }

            @Override
            public float saturation() {
                return player.getSaturation();
            }

            @Override
            public void saturation(float saturation) {
                player.setSaturation(saturation);
            }

            @Override
            public Cake cake() {
                return cake;
            }

            @Override
            public void updateCake(Cake cake) {
                block.setBlockData(cake, true);
            }

            @Override
            public void removeCake() {
                block.setType(Material.AIR, true);
            }

            @Override
            public void playEatSound() {
                playSound(block, "ENTITY_GENERIC_EAT", "ENTITY_PLAYER_BURP");
            }

            @Override
            public void allowVanilla() {
                allowCakeConsumption(event);
            }

            @Override
            public void finishApplied() {
                event.setUseInteractedBlock(Event.Result.DENY);
                event.setUseItemInHand(Event.Result.DENY);
                event.setCancelled(true);
            }
        });
    }

    static boolean applyCakeConsumption(CakeConsumptionAccess access) {
        Cake cake = access.cake();
        if (cake == null) {
            return false;
        }
        if (access.gameMode() == GameMode.SPECTATOR) {
            access.allowVanilla();
            return false;
        }

        if (access.gameMode() != GameMode.CREATIVE && access.foodLevel() < 20) {
            access.foodLevel(Math.min(20, access.foodLevel() + 2));
            access.saturation(Math.min(access.foodLevel(), access.saturation() + 0.4F));
        }
        if (cake.getBites() >= cake.getMaximumBites()) {
            access.removeCake();
        } else {
            cake.setBites(cake.getBites() + 1);
            access.updateCake(cake);
        }
        access.playEatSound();
        access.finishApplied();
        return true;
    }

    interface CakeConsumptionAccess {
        GameMode gameMode();

        int foodLevel();

        void foodLevel(int foodLevel);

        float saturation();

        void saturation(float saturation);

        Cake cake();

        void updateCake(Cake cake);

        void removeCake();

        void playEatSound();

        void allowVanilla();

        void finishApplied();
    }

    static boolean applyAxeStripping(PlayerInteractEvent event, ProtectionRuleSupport support) {
        Block block = event.getClickedBlock();
        if (block == null) {
            return false;
        }
        Material strippedMaterial = support.strippedWoodMaterial(block.getType());
        if (strippedMaterial == null) {
            event.setUseInteractedBlock(Event.Result.ALLOW);
            event.setUseItemInHand(Event.Result.ALLOW);
            return false;
        }
        BlockData oldData = block.getBlockData();
        BlockData newData = strippedMaterial.createBlockData();
        copyCommonState(oldData, newData);
        block.setBlockData(newData, true);
        playSound(block, "ITEM_AXE_STRIP", "ITEM_AXE_SCRAPE");
        event.setUseInteractedBlock(Event.Result.DENY);
        event.setUseItemInHand(Event.Result.DENY);
        event.setCancelled(true);
        return true;
    }

    static boolean applyBlockDrivenToolChange(PlayerInteractEvent event) {
        Block block = event.getClickedBlock();
        if (block == null) {
            return false;
        }
        Material targetMaterial = ProtectionMaterialRules.scrapedOrWaxedCopperMaterial(block.getType(), event.getItem());
        if (targetMaterial != null) {
            return setBlockMaterial(event, targetMaterial, "ITEM_AXE_SCRAPE", "ITEM_AXE_WAX_OFF", "ITEM_HONEYCOMB_WAX_ON");
        }

        targetMaterial = ProtectionMaterialRules.extinguishedCampfireMaterial(block.getType(), event.getItem());
        if (targetMaterial != null && block.getBlockData() instanceof Campfire campfire && campfire.isLit()) {
            campfire.setLit(false);
            block.setBlockData(campfire, true);
            finishAppliedToolChange(event, "ITEM_SHOVEL_FLATTEN", "BLOCK_FIRE_EXTINGUISH");
            return true;
        }

        targetMaterial = ProtectionMaterialRules.flattenedPathMaterial(block.getType(), event.getItem());
        if (targetMaterial != null) {
            return setBlockMaterial(event, targetMaterial, "ITEM_SHOVEL_FLATTEN", "BLOCK_GRASS_BREAK");
        }

        targetMaterial = ProtectionMaterialRules.tilledSoilMaterial(block.getType(), event.getItem());
        if (targetMaterial != null) {
            return setBlockMaterial(event, targetMaterial, "ITEM_HOE_TILL", "BLOCK_GRAVEL_BREAK");
        }

        event.setUseInteractedBlock(Event.Result.ALLOW);
        event.setUseItemInHand(Event.Result.ALLOW);
        return false;
    }

    private static boolean setBlockMaterial(PlayerInteractEvent event, Material targetMaterial, String primarySound, String... fallbackSounds) {
        Block block = event.getClickedBlock();
        if (block == null || targetMaterial == null) {
            return false;
        }
        BlockData oldData = block.getBlockData();
        ItemStack[] inventoryContents = snapshotInventoryContents(block.getState());
        BlockData newData = targetMaterial.createBlockData();
        copyCommonState(oldData, newData);
        block.setBlockData(newData, true);
        restoreInventoryContents(block.getState(), inventoryContents);
        playSound(block, primarySound, fallbackSounds);
        finishAppliedToolChange(event);
        return true;
    }

    private static ItemStack[] snapshotInventoryContents(BlockState state) {
        if (!(state instanceof InventoryHolder holder)) {
            return null;
        }
        ItemStack[] contents = holder.getInventory().getContents();
        ItemStack[] copy = new ItemStack[contents.length];
        for (int i = 0; i < contents.length; i++) {
            copy[i] = contents[i] == null ? null : contents[i].clone();
        }
        return copy;
    }

    private static void restoreInventoryContents(BlockState state, ItemStack[] contents) {
        if (contents == null || !(state instanceof InventoryHolder holder)) {
            return;
        }
        Inventory inventory = holder.getInventory();
        ItemStack[] restored = inventory.getContents();
        int limit = Math.min(restored.length, contents.length);
        for (int i = 0; i < limit; i++) {
            restored[i] = contents[i] == null ? null : contents[i].clone();
        }
        inventory.setContents(restored);
        state.update(true, false);
    }

    private static void finishAppliedToolChange(PlayerInteractEvent event, String primarySound, String... fallbackSounds) {
        Block block = event.getClickedBlock();
        if (block != null) {
            playSound(block, primarySound, fallbackSounds);
        }
        finishAppliedToolChange(event);
    }

    private static void finishAppliedToolChange(PlayerInteractEvent event) {
        event.setUseInteractedBlock(Event.Result.DENY);
        event.setUseItemInHand(Event.Result.DENY);
        event.setCancelled(true);
    }

    private static void copyCommonState(BlockData oldData, BlockData newData) {
        if (oldData instanceof Orientable oldOrientable && newData instanceof Orientable newOrientable) {
            newOrientable.setAxis(oldOrientable.getAxis());
        }
        if (oldData instanceof Directional oldDirectional && newData instanceof Directional newDirectional
            && newDirectional.getFaces().contains(oldDirectional.getFacing())) {
            newDirectional.setFacing(oldDirectional.getFacing());
        }
        if (oldData instanceof Waterlogged oldWaterlogged && newData instanceof Waterlogged newWaterlogged) {
            newWaterlogged.setWaterlogged(oldWaterlogged.isWaterlogged());
        }
        if (oldData instanceof Chest oldChest && newData instanceof Chest newChest) {
            newChest.setType(oldChest.getType());
        }
        if (oldData instanceof Slab oldSlab && newData instanceof Slab newSlab) {
            newSlab.setType(oldSlab.getType());
        }
        if (oldData instanceof Stairs oldStairs && newData instanceof Stairs newStairs) {
            newStairs.setShape(oldStairs.getShape());
            newStairs.setHalf(oldStairs.getHalf());
        }
    }

    private static void playSound(Block block, String primary, String fallback) {
        playSound(block, primary, new String[] {fallback});
    }

    private static void playSound(Block block, String primary, String... fallbacks) {
        Sound sound = sound(primary);
        if (sound == null && fallbacks != null) {
            for (String fallback : fallbacks) {
                sound = sound(fallback);
                if (sound != null) {
                    break;
                }
            }
        }
        if (sound != null) {
            block.getWorld().playSound(block.getLocation(), sound, 1.0F, 1.0F);
        }
    }

    private static Sound sound(String name) {
        try {
            return Sound.valueOf(name);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }
}
