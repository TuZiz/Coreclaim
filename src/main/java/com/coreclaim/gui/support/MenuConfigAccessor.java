package com.coreclaim.gui.support;

import com.coreclaim.CoreClaimPlugin;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

public final class MenuConfigAccessor {

    private final CoreClaimPlugin plugin;

    public MenuConfigAccessor(CoreClaimPlugin plugin) {
        this.plugin = plugin;
    }

    public FileConfiguration menu(String menuKey) {
        return plugin.menuConfig(menuKey);
    }

    public boolean hasItem(String menuKey, String itemKey) {
        return menu(menuKey).isConfigurationSection("items." + itemKey);
    }

    public int menuSize(String menuKey) {
        List<String> layout = layout(menuKey);
        if (!layout.isEmpty()) {
            return layout.size() * 9;
        }
        return menu(menuKey).getInt("size", 27);
    }

    public int slot(String menuKey, String itemKey, java.util.function.UnaryOperator<String> padLayout) {
        ConfigurationSection section = menu(menuKey).getConfigurationSection("items." + itemKey);
        if (section == null) {
            return 0;
        }
        if (section.contains("slot")) {
            return section.getInt("slot", 0);
        }
        List<Integer> slots = slots(menuKey, itemKey, padLayout);
        return slots.isEmpty() ? 0 : slots.get(0);
    }

    public List<Integer> slots(String menuKey, String itemKey, java.util.function.UnaryOperator<String> padLayout) {
        ConfigurationSection section = menu(menuKey).getConfigurationSection("items." + itemKey);
        List<Integer> result = new ArrayList<>();
        if (section == null) {
            return result;
        }
        if (section.contains("slot")) {
            result.add(section.getInt("slot", 0));
            return result;
        }
        Set<Character> symbols = symbols(section);
        if (symbols.isEmpty()) {
            return result;
        }
        List<String> layout = layout(menuKey);
        for (int row = 0; row < layout.size(); row++) {
            String line = padLayout.apply(layout.get(row));
            for (int column = 0; column < 9; column++) {
                if (symbols.contains(line.charAt(column))) {
                    result.add(row * 9 + column);
                }
            }
        }
        return result;
    }

    private Set<Character> symbols(ConfigurationSection section) {
        Set<Character> symbols = new LinkedHashSet<>();
        for (String rawChar : section.getStringList("chars")) {
            addSymbol(symbols, rawChar);
        }
        addSymbol(symbols, section.getString("char", ""));
        return symbols;
    }

    private void addSymbol(Set<Character> symbols, String rawChar) {
        if (rawChar != null && !rawChar.isBlank()) {
            symbols.add(rawChar.charAt(0));
        }
    }

    private List<String> layout(String menuKey) {
        FileConfiguration menu = menu(menuKey);
        List<String> layout = menu.getStringList("Shape");
        if (!layout.isEmpty()) {
            return layout;
        }
        layout = menu.getStringList("GuiPlain");
        if (!layout.isEmpty()) {
            return layout;
        }
        return menu.getStringList("layout");
    }
}
