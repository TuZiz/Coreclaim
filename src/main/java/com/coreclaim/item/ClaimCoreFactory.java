package com.coreclaim.item;

import com.coreclaim.CoreClaimPlugin;
import java.util.ArrayList;
import java.util.List;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

public final class ClaimCoreFactory {

    private final CoreClaimPlugin plugin;
    private final NamespacedKey coreMarkerKey;
    private final NamespacedKey starterMarkerKey;

    public ClaimCoreFactory(CoreClaimPlugin plugin) {
        this.plugin = plugin;
        this.coreMarkerKey = new NamespacedKey(plugin, "claim_core_marker");
        this.starterMarkerKey = new NamespacedKey(plugin, "claim_starter_core_marker");
    }

    public ItemStack createClaimCore(int amount) {
        FileConfiguration config = plugin.getConfig();
        List<String> lore = config.getStringList("claim-core.lore");
        if (lore.isEmpty()) {
            lore = config.getStringList("core-tool.lore");
        }
        return createConfiguredCore(
            amount,
            resolveMaterial(config.getString("claim-core.material", config.getString("core-tool.material", plugin.settings().coreMaterial().name()))),
            config.getString("claim-core.name", config.getString("core-tool.name", "&6领地核心")),
            lore,
            coreMarkerKey,
            config.getInt("claim-core.custom-model-data", 0),
            config.getBoolean("claim-core.glow", false)
        );
    }

    public ItemStack createStarterCore(int amount) {
        FileConfiguration config = plugin.getConfig();
        return createConfiguredCore(
            amount,
            resolveMaterial(config.getString("starter-tool.material", plugin.settings().coreMaterial().name())),
            config.getString("starter-tool.name", "&b&l新人核心"),
            config.getStringList("starter-tool.lore"),
            starterMarkerKey,
            config.getInt("starter-tool.custom-model-data", 1701),
            config.getBoolean("starter-tool.glow", true)
        );
    }

    public boolean isClaimCore(ItemStack stack) {
        return hasMarker(stack, coreMarkerKey);
    }

    public boolean isStarterCore(ItemStack stack) {
        return hasMarker(stack, starterMarkerKey);
    }

    public boolean isAnyClaimCore(ItemStack stack) {
        return isClaimCore(stack) || isStarterCore(stack);
    }

    public void giveClaimCore(Player player, int amount) {
        giveItem(player, createClaimCore(amount));
    }

    public void giveStarterCore(Player player, int amount) {
        giveItem(player, createStarterCore(amount));
    }

    public boolean hasStarterCore(Player player) {
        if (player == null) {
            return false;
        }
        return containsStarterCore(player.getInventory().getContents())
            || containsStarterCore(player.getEnderChest().getContents())
            || isStarterCore(player.getOpenInventory().getCursor());
    }

    private ItemStack createConfiguredCore(
        int amount,
        Material material,
        String rawName,
        List<String> lore,
        NamespacedKey markerKey,
        int customModelData,
        boolean glow
    ) {
        ItemStack stack = new ItemStack(material, Math.max(1, amount));
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) {
            return stack;
        }
        meta.setDisplayName(plugin.color(replacePlaceholders(rawName)));
        if (!lore.isEmpty()) {
            List<String> coloredLore = new ArrayList<>();
            for (String line : lore) {
                coloredLore.add(plugin.color(replacePlaceholders(line)));
            }
            meta.setLore(coloredLore);
        }
        if (customModelData > 0) {
            meta.setCustomModelData(customModelData);
        }
        if (glow) {
            meta.addEnchant(Enchantment.LUCK, 1, true);
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        }
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        meta.getPersistentDataContainer().set(markerKey, PersistentDataType.STRING, "true");
        stack.setItemMeta(meta);
        return stack;
    }

    private boolean hasMarker(ItemStack stack, NamespacedKey key) {
        if (stack == null || stack.getType().isAir()) {
            return false;
        }
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) {
            return false;
        }
        String marker = meta.getPersistentDataContainer().get(key, PersistentDataType.STRING);
        return "true".equals(marker);
    }

    private boolean containsStarterCore(ItemStack[] contents) {
        if (contents == null) {
            return false;
        }
        for (ItemStack item : contents) {
            if (isStarterCore(item)) {
                return true;
            }
        }
        return false;
    }

    private void giveItem(Player player, ItemStack stack) {
        player.getInventory().addItem(stack).values().forEach(leftover ->
            player.getWorld().dropItemNaturally(player.getLocation(), leftover)
        );
    }

    private Material resolveMaterial(String raw) {
        Material material = Material.matchMaterial(raw == null ? "" : raw);
        return material == null ? plugin.settings().coreMaterial() : material;
    }

    private String replacePlaceholders(String text) {
        if (text == null) {
            return "";
        }
        return text
            .replace("%starter-reward-minutes%", String.valueOf(plugin.settings().starterRewardMinutes()))
            .replace("%claim-world%", plugin.settings().claimWorldsDisplay());
    }
}
