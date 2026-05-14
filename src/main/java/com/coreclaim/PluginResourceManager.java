package com.coreclaim;

import com.coreclaim.config.ResourceConfig;
import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.bukkit.configuration.file.FileConfiguration;

final class PluginResourceManager {

    private static final List<String> MENU_RESOURCE_PATHS = List.of(
        "gui/claim-list.yml",
        "gui/claim-view.yml",
        "gui/claim-manage.yml",
        "gui/trust.yml",
        "gui/trust-online-add.yml",
        "gui/claim-permissions.yml",
        "gui/selection-create.yml",
        "gui/claim-expand-confirm.yml",
        "gui/core.yml"
    );

    private final CoreClaimPlugin plugin;
    private final Map<String, ResourceConfig> menuResources = new HashMap<>();
    private ResourceConfig messageResource;
    private ResourceConfig groupsResource;
    private ResourceConfig rulesResource;

    PluginResourceManager(CoreClaimPlugin plugin) {
        this.plugin = plugin;
    }

    PreparedResources prepare() {
        plugin.saveDefaultConfig();
        messageResource = new ResourceConfig(plugin, messageResourcePath());
        groupsResource = new ResourceConfig(plugin, "groups.yml");
        rulesResource = new ResourceConfig(plugin, "rules.yml");
        loadMenuResources();
        return new PreparedResources(messageResource, groupsResource, rulesResource);
    }

    void reloadResources() {
        messageResource = new ResourceConfig(plugin, messageResourcePath());
        groupsResource.reload();
        rulesResource.reload();
        menuResources.values().forEach(ResourceConfig::reload);
    }

    FileConfiguration menuConfig(String menuKey) {
        ResourceConfig resource = menuResources.get(menuKey);
        if (resource == null) {
            throw new IllegalArgumentException("Unknown menu config: " + menuKey);
        }
        return resource.config();
    }

    FileConfiguration messagesConfig() {
        return messageResource.config();
    }

    FileConfiguration groupsConfig() {
        return groupsResource.config();
    }

    FileConfiguration rulesConfig() {
        return rulesResource.config();
    }

    String messageResourcePath() {
        return messageResourcePath(plugin);
    }

    static String messageResourcePath(CoreClaimPlugin plugin) {
        String language = plugin.getConfig().getString("language", "zh_cn");
        if (language == null || language.isBlank()) {
            return CoreClaimPlugin.MESSAGE_RESOURCE_PATH;
        }
        String normalized = language.trim().toLowerCase(Locale.ROOT).replace('-', '_');
        if ("zh_cn".equals(normalized)) {
            return CoreClaimPlugin.MESSAGE_RESOURCE_PATH;
        }
        if ("en_us".equals(normalized)) {
            return CoreClaimPlugin.ENGLISH_MESSAGE_RESOURCE_PATH;
        }
        plugin.getLogger().warning("Unknown language '" + language + "', falling back to zh_cn.");
        return CoreClaimPlugin.MESSAGE_RESOURCE_PATH;
    }

    void logLegacyRuleConfigWarnings() {
        File legacyFlagsFile = new File(plugin.getDataFolder(), "flags.yml");
        if (legacyFlagsFile.exists()) {
            plugin.getLogger().warning("Legacy flags.yml was found. CoreClaim now uses rules.yml; flags.yml is only kept as compatibility fallback.");
        }
        if (plugin.getConfig().getConfigurationSection("flags") != null) {
            plugin.getLogger().warning("Legacy flags config was found in config.yml. CoreClaim now uses rules.yml; config.yml.flags is only kept as compatibility fallback.");
        }
        if (plugin.getConfig().getConfigurationSection("permissions.new-claim-defaults") != null
            || plugin.getConfig().getConfigurationSection("permissions.system-claim-defaults") != null) {
            plugin.getLogger().warning("Legacy permission defaults were found in config.yml. CoreClaim now uses rules.yml; config.yml.permissions is only kept as compatibility fallback.");
        }
    }

    private void loadMenuResources() {
        menuResources.put("claim-list", new ResourceConfig(plugin, "gui/claim-list.yml"));
        menuResources.put("claim-view", new ResourceConfig(plugin, "gui/claim-view.yml"));
        menuResources.put("claim-manage", new ResourceConfig(plugin, "gui/claim-manage.yml"));
        menuResources.put("trust", new ResourceConfig(plugin, "gui/trust.yml"));
        menuResources.put("trust-online-add", new ResourceConfig(plugin, "gui/trust-online-add.yml"));
        menuResources.put("claim-permissions", new ResourceConfig(plugin, "gui/claim-permissions.yml"));
        menuResources.put("selection-create", new ResourceConfig(plugin, "gui/selection-create.yml"));
        menuResources.put("claim-expand-confirm", new ResourceConfig(plugin, "gui/claim-expand-confirm.yml"));
        menuResources.put("core", new ResourceConfig(plugin, "gui/core.yml"));
    }

    record PreparedResources(
        ResourceConfig messageResource,
        ResourceConfig groupsResource,
        ResourceConfig rulesResource
    ) {
    }
}
