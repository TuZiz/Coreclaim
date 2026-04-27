package com.coreclaim;

import com.coreclaim.config.ResourceConfig;
import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

final class PluginResourceManager {

    private static final String LEGACY_MESSAGE_RESOURCE_PATH = "messages.yml";
    private static final List<String> BUNDLED_MESSAGE_RESOURCE_PATHS = List.of(
        CoreClaimPlugin.MESSAGE_RESOURCE_PATH,
        CoreClaimPlugin.ENGLISH_MESSAGE_RESOURCE_PATH
    );
    private static final List<String> MENU_RESOURCE_PATHS = List.of(
        "gui/claim-list.yml",
        "gui/claim-view.yml",
        "gui/claim-manage.yml",
        "gui/trust.yml",
        "gui/trust-online-add.yml",
        "gui/claim-permissions.yml",
        "gui/selection-create.yml",
        "gui/claim-expand-amount.yml",
        "gui/claim-expand-confirm.yml",
        "gui/core.yml"
    );
    private static final Map<String, String> OUTDATED_LAYOUT_MARKERS = Map.ofEntries(
        Map.entry("gui/claim-list.yml", "layout-version: 2"),
        Map.entry("gui/claim-view.yml", "layout-version: 1"),
        Map.entry("gui/claim-manage.yml", "layout-version: 2"),
        Map.entry("gui/claim-expand-amount.yml", "layout-version: 1"),
        Map.entry("gui/claim-expand-confirm.yml", "layout-version: 1"),
        Map.entry("gui/trust-online-add.yml", "layout-version: 2"),
        Map.entry("gui/core.yml", "layout-version: 5"),
        Map.entry("gui/claim-permissions.yml", "layout-version: 7"),
        Map.entry("gui/trust.yml", "layout-version: 5"),
        Map.entry("gui/selection-create.yml", "layout-version: 3")
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
        ensureConfigDefaults();
        ensureRulesDefaults();
        ensureMessagesDefaults();
        ensureHealthyGuiResources();
        messageResource = new ResourceConfig(plugin, messageResourcePath());
        groupsResource = new ResourceConfig(plugin, "groups.yml");
        rulesResource = new ResourceConfig(plugin, "rules.yml");
        loadMenuResources();
        return new PreparedResources(messageResource, groupsResource, rulesResource);
    }

    void reloadResources() {
        ensureConfigDefaults();
        ensureRulesDefaults();
        ensureMessagesDefaults();
        ensureHealthyGuiResources();
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

    private void ensureConfigDefaults() {
        try (InputStream inputStream = plugin.getResource("config.yml")) {
            if (inputStream == null) {
                return;
            }
            FileConfiguration defaults = YamlConfiguration.loadConfiguration(new InputStreamReader(inputStream, StandardCharsets.UTF_8));
            FileConfiguration config = plugin.getConfig();
            List<String> missingPaths = ConfigurationDefaults.missingPaths(defaults, config);
            if (missingPaths.isEmpty()) {
                return;
            }
            config.setDefaults(defaults);
            config.options().copyDefaults(true);
            plugin.saveConfig();
            plugin.getLogger().info("Added missing config defaults: " + String.join(", ", missingPaths));
        } catch (Exception exception) {
            plugin.getLogger().warning("Failed to merge config defaults: " + exception.getMessage());
        }
    }

    private void ensureRulesDefaults() {
        try (InputStream inputStream = plugin.getResource("rules.yml")) {
            if (inputStream == null) {
                return;
            }
            if (!plugin.getDataFolder().exists() && !plugin.getDataFolder().mkdirs()) {
                throw new IllegalStateException("Unable to create plugin data folder.");
            }
            File file = new File(plugin.getDataFolder(), "rules.yml");
            boolean existed = file.exists();
            FileConfiguration defaults = YamlConfiguration.loadConfiguration(new InputStreamReader(inputStream, StandardCharsets.UTF_8));
            FileConfiguration rulesConfig = existed ? YamlConfiguration.loadConfiguration(file) : new YamlConfiguration();
            boolean changed = false;
            File legacyFlagsFile = new File(plugin.getDataFolder(), "flags.yml");
            if (legacyFlagsFile.exists()) {
                FileConfiguration legacyFlags = YamlConfiguration.loadConfiguration(legacyFlagsFile);
                changed |= migrateLegacySection(legacyFlags.getConfigurationSection("new-claim-defaults"), rulesConfig, "new-claim-defaults.flags");
                changed |= migrateLegacySection(legacyFlags.getConfigurationSection("system-claim-defaults"), rulesConfig, "system-claim-defaults.flags");
                if (changed) {
                    plugin.getLogger().info("Migrated legacy flag defaults from flags.yml to rules.yml");
                }
            }
            changed |= migrateLegacySection(plugin.getConfig().getConfigurationSection("flags.new-claim-defaults"), rulesConfig, "new-claim-defaults.flags");
            changed |= migrateLegacySection(plugin.getConfig().getConfigurationSection("flags.system-claim-defaults"), rulesConfig, "system-claim-defaults.flags");
            changed |= migrateLegacySection(plugin.getConfig().getConfigurationSection("permissions.new-claim-defaults"), rulesConfig, "new-claim-defaults.permissions");
            changed |= migrateLegacySection(plugin.getConfig().getConfigurationSection("permissions.system-claim-defaults"), rulesConfig, "system-claim-defaults.permissions");
            List<String> missingPaths = ConfigurationDefaults.missingPaths(defaults, rulesConfig);
            if (!missingPaths.isEmpty()) {
                rulesConfig.setDefaults(defaults);
                rulesConfig.options().copyDefaults(true);
                changed = true;
            }
            if (!existed || changed) {
                rulesConfig.save(file);
                if (!missingPaths.isEmpty()) {
                    plugin.getLogger().info("Added missing rules defaults: " + String.join(", ", missingPaths));
                }
            }
        } catch (Exception exception) {
            plugin.getLogger().warning("Failed to prepare rules defaults: " + exception.getMessage());
        }
    }

    private void ensureMessagesDefaults() {
        for (String resourcePath : BUNDLED_MESSAGE_RESOURCE_PATHS) {
            ensureMessageDefaults(resourcePath, CoreClaimPlugin.MESSAGE_RESOURCE_PATH.equals(resourcePath));
        }
    }

    private void ensureMessageDefaults(String resourcePath, boolean migrateLegacyMessages) {
        try (InputStream inputStream = plugin.getResource(resourcePath)) {
            if (inputStream == null) {
                return;
            }
            if (!plugin.getDataFolder().exists() && !plugin.getDataFolder().mkdirs()) {
                throw new IllegalStateException("Unable to create plugin data folder.");
            }
            File file = new File(plugin.getDataFolder(), resourcePath);
            if (migrateLegacyMessages) {
                migrateLegacyMessagesFile(file);
            }
            boolean existed = file.exists();
            FileConfiguration defaults = YamlConfiguration.loadConfiguration(new InputStreamReader(inputStream, StandardCharsets.UTF_8));
            FileConfiguration messagesConfig = existed ? YamlConfiguration.loadConfiguration(file) : new YamlConfiguration();
            boolean changed = false;
            List<String> missingPaths = ConfigurationDefaults.missingPaths(defaults, messagesConfig);
            if (!missingPaths.isEmpty()) {
                messagesConfig.setDefaults(defaults);
                messagesConfig.options().copyDefaults(true);
                changed = true;
            }

            changed |= MessageDefaultsRepair.applyKnownReplacements(messagesConfig, defaults, resourcePath);

            if (!existed || changed) {
                messagesConfig.save(file);
                if (!missingPaths.isEmpty()) {
                    plugin.getLogger().info("Added missing messages defaults to " + resourcePath + ": " + String.join(", ", missingPaths));
                }
            }
        } catch (Exception exception) {
            plugin.getLogger().warning("Failed to prepare messages defaults for " + resourcePath + ": " + exception.getMessage());
        }
    }

    private void migrateLegacyMessagesFile(File targetFile) throws Exception {
        File legacyFile = new File(plugin.getDataFolder(), LEGACY_MESSAGE_RESOURCE_PATH);
        if (!legacyFile.exists() || targetFile.exists()) {
            return;
        }
        File parent = targetFile.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            throw new IllegalStateException("Unable to create language directory: " + parent.getAbsolutePath());
        }
        Files.move(legacyFile.toPath(), targetFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
        plugin.getLogger().info("Migrated legacy messages.yml to " + CoreClaimPlugin.MESSAGE_RESOURCE_PATH);
    }

    private boolean migrateLegacySection(ConfigurationSection source, FileConfiguration target, String prefix) {
        if (source == null) {
            return false;
        }
        return migrateLegacyValues(source, target, prefix);
    }

    private boolean migrateLegacyValues(ConfigurationSection source, FileConfiguration target, String prefix) {
        boolean changed = false;
        for (String key : source.getKeys(false)) {
            String path = prefix.isEmpty() ? key : prefix + "." + key;
            Object value = source.get(key);
            if (value instanceof ConfigurationSection section) {
                changed |= migrateLegacyValues(section, target, path);
            } else if (!target.isSet(path)) {
                target.set(path, value);
                changed = true;
            }
        }
        return changed;
    }

    private void loadMenuResources() {
        menuResources.put("claim-list", new ResourceConfig(plugin, "gui/claim-list.yml"));
        menuResources.put("claim-view", new ResourceConfig(plugin, "gui/claim-view.yml"));
        menuResources.put("claim-manage", new ResourceConfig(plugin, "gui/claim-manage.yml"));
        menuResources.put("trust", new ResourceConfig(plugin, "gui/trust.yml"));
        menuResources.put("trust-online-add", new ResourceConfig(plugin, "gui/trust-online-add.yml"));
        menuResources.put("claim-permissions", new ResourceConfig(plugin, "gui/claim-permissions.yml"));
        menuResources.put("selection-create", new ResourceConfig(plugin, "gui/selection-create.yml"));
        menuResources.put("claim-expand-amount", new ResourceConfig(plugin, "gui/claim-expand-amount.yml"));
        menuResources.put("claim-expand-confirm", new ResourceConfig(plugin, "gui/claim-expand-confirm.yml"));
        menuResources.put("core", new ResourceConfig(plugin, "gui/core.yml"));
    }

    private void ensureHealthyGuiResources() {
        for (String resource : MENU_RESOURCE_PATHS) {
            repairCorruptedGuiResource(resource);
        }
    }

    private void repairCorruptedGuiResource(String fileName) {
        try {
            File file = new File(plugin.getDataFolder(), fileName);
            if (!file.exists()) {
                return;
            }
            String content = Files.readString(file.toPath(), StandardCharsets.UTF_8);
            if (!looksLikeGuiMojibake(content) && !looksLikeOutdatedGuiLayout(fileName, content)) {
                return;
            }
            plugin.saveResource(fileName, true);
            plugin.getLogger().warning("Detected outdated or corrupted GUI content in " + fileName + ". Replaced it with the bundled resource.");
        } catch (Exception exception) {
            plugin.getLogger().warning("Failed to verify GUI resource " + fileName + ": " + exception.getMessage());
        }
    }

    private boolean looksLikeGuiMojibake(String content) {
        if (content == null || content.isBlank()) {
            return false;
        }
        return content.contains("GuiPlain:")
            && content.contains("custom-model-data:")
            && java.util.regex.Pattern.compile("[\\u4E00-\\u9FFF]{3,}\\?").matcher(content).find();
    }

    private boolean looksLikeOutdatedGuiLayout(String fileName, String content) {
        if (content == null || content.isBlank()) {
            return false;
        }
        String marker = OUTDATED_LAYOUT_MARKERS.get(fileName);
        return marker != null && !content.contains(marker);
    }

    record PreparedResources(
        ResourceConfig messageResource,
        ResourceConfig groupsResource,
        ResourceConfig rulesResource
    ) {
    }
}
