package com.coreclaim.protection.listener;

import com.coreclaim.model.ClaimPermission;
import java.util.Set;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

final class ProtectionMaterialRules {

    private static final Set<String> COMPOSTABLE_ITEM_NAMES = Set.of(
        "APPLE",
        "ALLIUM",
        "AZURE_BLUET",
        "AZALEA",
        "BAMBOO",
        "BAMBOO_SAPLING",
        "BAKED_POTATO",
        "BEETROOT",
        "BEETROOT_SEEDS",
        "BIG_DRIPLEAF",
        "BLUE_ORCHID",
        "BREAD",
        "BROWN_MUSHROOM",
        "BROWN_MUSHROOM_BLOCK",
        "CACTUS",
        "CAKE",
        "CARROT",
        "COCOA_BEANS",
        "CORNFLOWER",
        "COOKIE",
        "DANDELION",
        "DEAD_BUSH",
        "DRIED_KELP",
        "DRIED_KELP_BLOCK",
        "FERN",
        "FLOWERING_AZALEA",
        "FLOWERING_AZALEA_LEAVES",
        "GLOW_BERRIES",
        "GLOW_LICHEN",
        "GRASS",
        "HANGING_ROOTS",
        "HAY_BLOCK",
        "KELP",
        "LARGE_FERN",
        "LILAC",
        "LILY_OF_THE_VALLEY",
        "LILY_PAD",
        "MANGROVE_PROPAGULE",
        "MANGROVE_ROOTS",
        "MELON",
        "MELON_SEEDS",
        "MELON_SLICE",
        "MOSS_BLOCK",
        "MOSS_CARPET",
        "NETHER_SPROUTS",
        "NETHER_WART",
        "NETHER_WART_BLOCK",
        "OXEYE_DAISY",
        "PINK_PETALS",
        "PEONY",
        "PITCHER_POD",
        "POPPY",
        "POTATO",
        "PUMPKIN",
        "PUMPKIN_PIE",
        "PUMPKIN_SEEDS",
        "RED_MUSHROOM",
        "RED_MUSHROOM_BLOCK",
        "ROSE_BUSH",
        "SEA_PICKLE",
        "SEAGRASS",
        "SHORT_GRASS",
        "SHROOMLIGHT",
        "SMALL_DRIPLEAF",
        "SPORE_BLOSSOM",
        "SUGAR_CANE",
        "SUNFLOWER",
        "SWEET_BERRIES",
        "TALL_GRASS",
        "TORCHFLOWER_SEEDS",
        "VINE",
        "WARPED_WART_BLOCK",
        "WHEAT",
        "WHEAT_SEEDS",
        "WITHER_ROSE"
    );

    private ProtectionMaterialRules() {
    }

    static boolean isProjectileSensitiveBlock(Material material) {
        String name = material.name();
        return material == Material.BELL
            || name.endsWith("_BUTTON")
            || name.endsWith("_PRESSURE_PLATE")
            || name.endsWith("_DOOR")
            || name.endsWith("_TRAPDOOR")
            || name.endsWith("_FENCE_GATE")
            || material == Material.LEVER
            || material == Material.TARGET;
    }

    static ClaimPermission requiredPermissionForBlockToolChange(Material material, ItemStack item) {
        if (material == null) {
            return null;
        }
        if (isDirectRightClickStateBlock(material)) {
            return ClaimPermission.INTERACT;
        }
        if (item == null) {
            return null;
        }
        if (isAxe(item) && isStrippableWood(material)) {
            return ClaimPermission.BREAK;
        }
        if (isAxe(item) && isWeatheredOrWaxedCopper(material)) {
            return ClaimPermission.BREAK;
        }
        if (isShovel(item) && isShovelFlattenable(material)) {
            return ClaimPermission.BREAK;
        }
        if (isHoe(item) && isHoeTillable(material)) {
            return ClaimPermission.INTERACT;
        }
        if (isHoneycomb(item) && isWaxableCopper(material)) {
            return ClaimPermission.INTERACT;
        }
        if (isBoneMeal(item) && isBoneMealTarget(material)) {
            return ClaimPermission.INTERACT;
        }
        return null;
    }

    static boolean isComposterCompostInput(Material material, boolean composterFull, ItemStack item) {
        return material == Material.COMPOSTER
            && !composterFull
            && item != null
            && isCompostableMaterial(item.getType());
    }

    static boolean isCompostableMaterial(Material material) {
        if (material == null || material.isAir()) {
            return false;
        }
        String name = material.name();
        return COMPOSTABLE_ITEM_NAMES.contains(name)
            || name.endsWith("_SEEDS")
            || name.endsWith("_SAPLING")
            || name.endsWith("_LEAVES")
            || name.endsWith("_PETALS")
            || name.endsWith("_TULIP")
            || name.endsWith("_VINES")
            || name.endsWith("_VINES_PLANT")
            || name.endsWith("_FUNGUS")
            || name.endsWith("_ROOTS")
            || name.endsWith("_MUSHROOM")
            || name.endsWith("_MUSHROOM_BLOCK");
    }

    static boolean isCakeConsumption(Material material, ItemStack item) {
        if (!isCakeBlock(material)) {
            return false;
        }
        if (material != Material.CAKE) {
            return true;
        }
        if (item == null || item.getType().isAir()) {
            return true;
        }
        String name = item.getType().name();
        return !name.equals("CANDLE") && !name.endsWith("_CANDLE");
    }

    static boolean isAxeStrippingWood(Material material, ItemStack item) {
        return material != null && isAxe(item) && isStrippableWood(material);
    }

    static boolean isRedstoneControl(Material material) {
        if (material == null) {
            return false;
        }
        String name = material.name();
        return material == Material.LEVER
            || name.endsWith("_BUTTON")
            || name.endsWith("_PRESSURE_PLATE");
    }

    private static boolean isAxe(ItemStack item) {
        return item != null && item.getType().name().endsWith("_AXE");
    }

    private static boolean isShovel(ItemStack item) {
        return item != null && item.getType().name().endsWith("_SHOVEL");
    }

    private static boolean isHoe(ItemStack item) {
        return item != null && item.getType().name().endsWith("_HOE");
    }

    private static boolean isHoneycomb(ItemStack item) {
        return item != null && item.getType() == Material.HONEYCOMB;
    }

    private static boolean isBoneMeal(ItemStack item) {
        return item != null && item.getType() == Material.BONE_MEAL;
    }

    private static boolean isDirectRightClickStateBlock(Material material) {
        String name = material.name();
        return material == Material.CAKE
            || material == Material.CANDLE
            || name.endsWith("_CANDLE")
            || name.equals("CANDLE_CAKE")
            || name.endsWith("_CANDLE_CAKE")
            || material == Material.CAMPFIRE
            || material == Material.SOUL_CAMPFIRE
            || material == Material.COMPOSTER
            || name.endsWith("CAULDRON")
            || material == Material.BEEHIVE
            || material == Material.BEE_NEST;
    }

    private static boolean isCakeBlock(Material material) {
        if (material == null) {
            return false;
        }
        String name = material.name();
        return material == Material.CAKE
            || name.equals("CANDLE_CAKE")
            || name.endsWith("_CANDLE_CAKE");
    }

    private static boolean isStrippableWood(Material material) {
        String name = material.name();
        if (name.startsWith("STRIPPED_")) {
            return false;
        }
        return name.endsWith("_LOG")
            || name.endsWith("_WOOD")
            || name.endsWith("_STEM")
            || name.endsWith("_HYPHAE")
            || name.equals("BAMBOO_BLOCK");
    }

    private static boolean isWeatheredOrWaxedCopper(Material material) {
        String name = material.name();
        return name.contains("COPPER")
            && (name.startsWith("EXPOSED_")
            || name.startsWith("WEATHERED_")
            || name.startsWith("OXIDIZED_")
            || name.startsWith("WAXED_"));
    }

    private static boolean isWaxableCopper(Material material) {
        String name = material.name();
        if (!name.contains("COPPER") || name.startsWith("WAXED_")) {
            return false;
        }
        if (name.contains("ORE") || name.startsWith("RAW_")) {
            return false;
        }
        return name.equals("COPPER_BLOCK")
            || name.endsWith("_COPPER")
            || name.contains("CUT_COPPER")
            || name.contains("COPPER_");
    }

    private static boolean isShovelFlattenable(Material material) {
        return switch (material.name()) {
            case "GRASS_BLOCK", "DIRT", "PODZOL", "COARSE_DIRT", "MYCELIUM", "ROOTED_DIRT" -> true;
            default -> false;
        };
    }

    private static boolean isHoeTillable(Material material) {
        return switch (material.name()) {
            case "GRASS_BLOCK", "DIRT", "DIRT_PATH", "COARSE_DIRT", "ROOTED_DIRT" -> true;
            default -> false;
        };
    }

    private static boolean isBoneMealTarget(Material material) {
        String name = material.name();
        return switch (name) {
            case "GRASS_BLOCK", "MOSS_BLOCK", "PALE_MOSS_BLOCK", "MANGROVE_PROPAGULE",
                "WHEAT", "CARROTS", "POTATOES", "BEETROOTS", "NETHER_WART", "COCOA",
                "SWEET_BERRY_BUSH", "CAVE_VINES", "CAVE_VINES_PLANT", "KELP", "KELP_PLANT",
                "SEAGRASS", "SEA_PICKLE", "BAMBOO", "BAMBOO_SAPLING", "SUGAR_CANE",
                "CACTUS", "PITCHER_CROP", "TORCHFLOWER_CROP" -> true;
            default -> name.endsWith("_SAPLING") || name.endsWith("_NYLIUM");
        };
    }
}
