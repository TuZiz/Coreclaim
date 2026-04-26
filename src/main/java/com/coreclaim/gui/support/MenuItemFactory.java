package com.coreclaim.gui.support;

import com.coreclaim.CoreClaimPlugin;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

public final class MenuItemFactory {

    private final CoreClaimPlugin plugin;
    private final MenuConfigAccessor configAccessor;
    private final MenuTextFormatter textFormatter;

    public MenuItemFactory(CoreClaimPlugin plugin, MenuConfigAccessor configAccessor, MenuTextFormatter textFormatter) {
        this.plugin = plugin;
        this.configAccessor = configAccessor;
        this.textFormatter = textFormatter;
    }

    public ItemStack configuredItem(String menuKey, String itemKey, String... replacements) {
        ConfigurationSection section = configAccessor.menu(menuKey).getConfigurationSection("items." + itemKey);
        if (section == null) {
            return item(Material.BARRIER, "&#FF6B6BMissing: " + menuKey + "." + itemKey);
        }
        Material material = Material.matchMaterial(section.getString("material", "BARRIER"));
        if (material == null) {
            material = Material.BARRIER;
        }
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return item;
        }
        meta.setDisplayName(plugin.color(textFormatter.apply(section.getString("name", itemKey), replacements)));
        List<String> lore = section.getStringList("lore");
        if (!lore.isEmpty()) {
            List<String> lines = new ArrayList<>();
            for (String line : lore) {
                lines.add(plugin.color(textFormatter.apply(line, replacements)));
            }
            meta.setLore(lines);
        }
        if (section.contains("custom-model-data")) {
            meta.setCustomModelData(section.getInt("custom-model-data"));
        }
        if (section.getBoolean("glow", false)) {
            meta.addEnchant(Enchantment.LUCK, 1, true);
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        }
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        item.setItemMeta(meta);
        if (material == Material.PLAYER_HEAD && item.getItemMeta() instanceof SkullMeta skullMeta) {
            String owner = section.getString("skull-owner", "");
            String texture = section.getString("skull-texture", "");
            if (!owner.isBlank()) {
                skullMeta.setOwningPlayer(Bukkit.getOfflinePlayer(owner));
                item.setItemMeta(skullMeta);
            } else if (!texture.isBlank()) {
                applySkullTexture(skullMeta, texture);
                item.setItemMeta(skullMeta);
            }
        }
        return item;
    }

    public ItemStack playerHead(String menuKey, String itemKey, UUID playerId, String... replacements) {
        ItemStack item = configuredItem(menuKey, itemKey, replacements);
        if (!(item.getItemMeta() instanceof SkullMeta meta)) {
            return item;
        }
        meta.setOwningPlayer(Bukkit.getOfflinePlayer(playerId));
        item.setItemMeta(meta);
        return item;
    }

    public void playConfiguredSound(Player player, String menuKey, String itemKey) {
        ConfigurationSection section = configAccessor.menu(menuKey).getConfigurationSection("items." + itemKey);
        if (section == null) {
            return;
        }
        String rawSound = section.getString("sound", "");
        if (rawSound == null || rawSound.isBlank()) {
            return;
        }
        try {
            Sound sound = Sound.valueOf(rawSound.toUpperCase());
            float volume = (float) section.getDouble("sound-volume", 1D);
            float pitch = (float) section.getDouble("sound-pitch", 1D);
            player.playSound(player.getLocation(), sound, volume, pitch);
        } catch (IllegalArgumentException ignored) {
        }
    }

    public ItemStack item(Material material, String name, String... lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return item;
        }
        meta.setDisplayName(plugin.color(name));
        if (lore.length > 0) {
            List<String> lines = new ArrayList<>();
            for (String line : lore) {
                lines.add(plugin.color(line));
            }
            meta.setLore(lines);
        }
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        item.setItemMeta(meta);
        return item;
    }

    private void applySkullTexture(SkullMeta meta, String texture) {
        try {
            Class<?> gameProfileClass = Class.forName("com.mojang.authlib.GameProfile");
            Class<?> propertyClass = Class.forName("com.mojang.authlib.properties.Property");
            Object gameProfile = gameProfileClass.getConstructor(UUID.class, String.class)
                .newInstance(UUID.nameUUIDFromBytes(texture.getBytes()), "coreclaim_head");
            Object property = propertyClass.getConstructor(String.class, String.class)
                .newInstance("textures", normalizeTexture(texture));
            Object propertyMap = gameProfileClass.getMethod("getProperties").invoke(gameProfile);
            propertyMap.getClass().getMethod("put", Object.class, Object.class).invoke(propertyMap, "textures", property);
            Field profileField = meta.getClass().getDeclaredField("profile");
            profileField.setAccessible(true);
            profileField.set(meta, gameProfile);
        } catch (Throwable ignored) {
        }
    }

    private String normalizeTexture(String texture) {
        if (texture.startsWith("http://") || texture.startsWith("https://")) {
            String payload = "{\"textures\":{\"SKIN\":{\"url\":\"" + texture + "\"}}}";
            return Base64.getEncoder().encodeToString(payload.getBytes());
        }
        return texture;
    }
}
