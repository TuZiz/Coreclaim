package com.coreclaim.service;

import com.coreclaim.CoreClaimPlugin;
import java.util.List;
import java.util.Objects;
import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

final class ClaimSelectionToolSupport {

    private final CoreClaimPlugin plugin;
    private final NamespacedKey selectionToolMarkerKey;

    ClaimSelectionToolSupport(CoreClaimPlugin plugin) {
        this.plugin = plugin;
        this.selectionToolMarkerKey = new NamespacedKey(plugin, "claim_selection_tool_marker");
    }

    boolean isSelectionTool(ItemStack item) {
        if (item == null || item.getType().isAir()) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return false;
        }
        if (hasSelectionToolMarker(meta)) {
            return true;
        }
        return isLegacySelectionTool(item, meta);
    }

    boolean canUseSelectionTool(ItemStack item) {
        return item != null
            && !item.getType().isAir()
            && item.getType() == plugin.settings().selectionToolMaterial();
    }

    ItemStack normalizeSelectionTool(ItemStack item) {
        if (item == null || item.getType().isAir()) {
            return item;
        }
        if (!canUseSelectionTool(item)) {
            return item;
        }
        ItemStack clone = item.clone();
        ItemMeta meta = clone.getItemMeta();
        if (meta == null) {
            return clone;
        }
        meta.setDisplayName(plugin.color(plugin.settings().selectionToolName()));
        List<String> lore = plugin.settings().selectionToolLore().stream().map(plugin::color).toList();
        if (!lore.isEmpty()) {
            meta.setLore(lore);
        }
        if (plugin.settings().selectionToolCustomModelData() > 0) {
            meta.setCustomModelData(plugin.settings().selectionToolCustomModelData());
        } else {
            meta.setCustomModelData(null);
        }
        if (plugin.settings().selectionToolGlow()) {
            meta.addEnchant(Enchantment.LUCK, 1, true);
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        }
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        meta.getPersistentDataContainer().set(selectionToolMarkerKey, PersistentDataType.STRING, "true");
        clone.setItemMeta(meta);
        return clone;
    }

    private boolean hasSelectionToolMarker(ItemMeta meta) {
        String marker = meta.getPersistentDataContainer().get(selectionToolMarkerKey, PersistentDataType.STRING);
        return "true".equals(marker);
    }

    private boolean isLegacySelectionTool(ItemStack item, ItemMeta meta) {
        if (item.getType() != plugin.settings().selectionToolMaterial()) {
            return false;
        }

        String configuredName = plugin.color(plugin.settings().selectionToolName());
        List<String> configuredLore = plugin.settings().selectionToolLore().stream().map(plugin::color).toList();

        boolean nameMatches = meta.hasDisplayName() && Objects.equals(meta.getDisplayName(), configuredName);
        boolean loreMatches = meta.hasLore() && Objects.equals(meta.getLore(), configuredLore);
        boolean customModelMatches = plugin.settings().selectionToolCustomModelData() > 0
            && meta.hasCustomModelData()
            && meta.getCustomModelData() == plugin.settings().selectionToolCustomModelData();

        return nameMatches || loreMatches || customModelMatches;
    }
}
