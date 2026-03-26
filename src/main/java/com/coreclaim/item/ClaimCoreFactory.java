package com.coreclaim.item;

import com.coreclaim.CoreClaimPlugin;
import java.util.ArrayList;
import java.util.List;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

public final class ClaimCoreFactory {

    private final CoreClaimPlugin plugin;
    private final NamespacedKey coreMarkerKey;

    public ClaimCoreFactory(CoreClaimPlugin plugin) {
        this.plugin = plugin;
        this.coreMarkerKey = new NamespacedKey(plugin, "claim_core_marker");
    }

    public ItemStack createClaimCore(int amount) {
        FileConfiguration config = plugin.getConfig();
        ItemStack stack = new ItemStack(plugin.settings().coreMaterial(), Math.max(1, amount));
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) {
            return stack;
        }
        meta.setDisplayName(plugin.color(config.getString(
            "claim-core.name",
            config.getString("core-tool.name", "&6领地核心")
        )));
        List<String> lore = config.getStringList("claim-core.lore");
        if (lore.isEmpty()) {
            lore = config.getStringList("core-tool.lore");
        }
        if (!lore.isEmpty()) {
            List<String> coloredLore = new ArrayList<>();
            for (String line : lore) {
                coloredLore.add(plugin.color(line));
            }
            meta.setLore(coloredLore);
        }
        meta.getPersistentDataContainer().set(coreMarkerKey, PersistentDataType.STRING, "true");
        stack.setItemMeta(meta);
        return stack;
    }

    public boolean isClaimCore(ItemStack stack) {
        if (stack == null || stack.getType().isAir()) {
            return false;
        }
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) {
            return false;
        }
        String marker = meta.getPersistentDataContainer().get(coreMarkerKey, PersistentDataType.STRING);
        return "true".equals(marker);
    }

    public void giveClaimCore(Player player, int amount) {
        ItemStack stack = createClaimCore(amount);
        player.getInventory().addItem(stack).values().forEach(leftover ->
            player.getWorld().dropItemNaturally(player.getLocation(), leftover)
        );
    }
}

