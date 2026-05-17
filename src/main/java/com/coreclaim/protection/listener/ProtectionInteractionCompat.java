package com.coreclaim.protection.listener;

import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.block.Container;
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
        if (setDoubleChestMaterialPreservingContainers(event, block, targetMaterial)) {
            playSound(block, primarySound, fallbackSounds);
            finishAppliedToolChange(event);
            return true;
        }
        setSingleBlockMaterialPreservingContainer(block, targetMaterial);
        playSound(block, primarySound, fallbackSounds);
        finishAppliedToolChange(event);
        return true;
    }

    private static void setSingleBlockMaterialPreservingContainer(Block block, Material targetMaterial) {
        BlockData oldData = block.getBlockData();
        ContainerSnapshot snapshot = ContainerSnapshot.capture(block);
        BlockData newData = targetMaterial.createBlockData();
        copyCommonState(oldData, newData);
        block.setBlockData(newData, true);
        if (snapshot != null) {
            snapshot.restore(block);
        }
    }

    private static boolean setDoubleChestMaterialPreservingContainers(PlayerInteractEvent event, Block block, Material targetMaterial) {
        BlockData oldData = block.getBlockData();
        if (!(oldData instanceof Chest clickedChest) || clickedChest.getType() == Chest.Type.SINGLE) {
            return false;
        }
        BlockFace pairFace = pairedChestFace(clickedChest.getType(), clickedChest.getFacing());
        if (pairFace == null) {
            return false;
        }
        Block pairedBlock = block.getRelative(pairFace);
        BlockData pairedData = pairedBlock.getBlockData();
        if (!(pairedData instanceof Chest pairedChest) || !isMatchingDoubleChestHalf(clickedChest, pairedChest)) {
            return false;
        }
        Material pairedTarget = ProtectionMaterialRules.scrapedOrWaxedCopperMaterial(pairedBlock.getType(), event.getItem());
        if (pairedTarget != targetMaterial) {
            return false;
        }

        ContainerSnapshot clickedSnapshot = ContainerSnapshot.capture(block);
        ContainerSnapshot pairedSnapshot = ContainerSnapshot.capture(pairedBlock);
        BlockData newData = targetMaterial.createBlockData();
        BlockData pairedNewData = targetMaterial.createBlockData();
        copyCommonState(oldData, newData);
        copyCommonState(pairedData, pairedNewData);
        block.setBlockData(newData, true);
        pairedBlock.setBlockData(pairedNewData, true);
        if (clickedSnapshot != null) {
            clickedSnapshot.restore(block);
        }
        if (pairedSnapshot != null) {
            pairedSnapshot.restore(pairedBlock);
        }
        return true;
    }

    static BlockFace pairedChestFace(Chest.Type type, BlockFace facing) {
        return switch (type) {
            case LEFT -> rotateYClockwise(facing);
            case RIGHT -> rotateYCounterClockwise(facing);
            case SINGLE -> null;
        };
    }

    private static boolean isMatchingDoubleChestHalf(Chest clickedChest, Chest pairedChest) {
        if (clickedChest.getFacing() != pairedChest.getFacing()) {
            return false;
        }
        return clickedChest.getType() == Chest.Type.LEFT && pairedChest.getType() == Chest.Type.RIGHT
            || clickedChest.getType() == Chest.Type.RIGHT && pairedChest.getType() == Chest.Type.LEFT;
    }

    private static BlockFace rotateYClockwise(BlockFace face) {
        return switch (face) {
            case NORTH -> BlockFace.EAST;
            case EAST -> BlockFace.SOUTH;
            case SOUTH -> BlockFace.WEST;
            case WEST -> BlockFace.NORTH;
            default -> null;
        };
    }

    private static BlockFace rotateYCounterClockwise(BlockFace face) {
        return switch (face) {
            case NORTH -> BlockFace.WEST;
            case WEST -> BlockFace.SOUTH;
            case SOUTH -> BlockFace.EAST;
            case EAST -> BlockFace.NORTH;
            default -> null;
        };
    }

    static ItemStack[] cloneContentsForSize(ItemStack[] contents, int size) {
        ItemStack[] copy = new ItemStack[Math.max(0, size)];
        if (contents == null || copy.length == 0) {
            return copy;
        }
        int limit = Math.min(copy.length, contents.length);
        for (int i = 0; i < limit; i++) {
            copy[i] = contents[i] == null ? null : contents[i].clone();
        }
        return copy;
    }

    private static final class ContainerSnapshot {
        private final ItemStack[] contents;
        private final String customName;
        private final String lock;

        private ContainerSnapshot(ItemStack[] contents, String customName, String lock) {
            this.contents = contents;
            this.customName = customName;
            this.lock = lock;
        }

        private static ContainerSnapshot capture(Block block) {
            BlockState state = block.getState();
            if (!(state instanceof Container container)) {
                return null;
            }
            Inventory inventory = container.getSnapshotInventory();
            return new ContainerSnapshot(
                cloneContentsForSize(inventory.getContents(), inventory.getSize()),
                container.getCustomName(),
                container.getLock()
            );
        }

        private void restore(Block block) {
            BlockState state = block.getState();
            if (!(state instanceof Container container)) {
                return;
            }
            Inventory inventory = container.getSnapshotInventory();
            inventory.setContents(cloneContentsForSize(contents, inventory.getSize()));
            container.setCustomName(customName);
            container.setLock(lock);
            container.update(true, false);
        }
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
