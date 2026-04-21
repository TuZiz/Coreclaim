package com.coreclaim;

import com.coreclaim.command.CoreClaimCommand;
import com.coreclaim.config.GroupConfig;
import com.coreclaim.config.PluginConfig;
import com.coreclaim.config.ResourceConfig;
import com.coreclaim.economy.EconomyHook;
import com.coreclaim.gui.MenuService;
import com.coreclaim.item.ClaimCoreFactory;
import com.coreclaim.listener.ClaimEnterLeaveListener;
import com.coreclaim.listener.ClaimEnvironmentProtectionListener;
import com.coreclaim.listener.ClaimInputListener;
import com.coreclaim.listener.ClaimCoreListener;
import com.coreclaim.listener.ClaimCoreInteractionListener;
import com.coreclaim.listener.ClaimNamingListener;
import com.coreclaim.listener.ClaimProtectionListener;
import com.coreclaim.listener.ClaimSelectionListener;
import com.coreclaim.listener.CrossServerTeleportListener;
import com.coreclaim.listener.MenuListener;
import com.coreclaim.listener.RemovalConfirmListener;
import com.coreclaim.listener.SelectionToolListener;
import com.coreclaim.papi.CoreClaimPlaceholderExpansion;
import com.coreclaim.platform.PlatformScheduler;
import com.coreclaim.service.ClaimActionService;
import com.coreclaim.service.ClaimCleanupService;
import com.coreclaim.service.ClaimService;
import com.coreclaim.service.ClaimSelectionService;
import com.coreclaim.service.ClaimSyncService;
import com.coreclaim.service.ClaimTransferService;
import com.coreclaim.service.ClaimInputService;
import com.coreclaim.service.ClaimVisualService;
import com.coreclaim.service.CrossServerTeleportService;
import com.coreclaim.service.ExplosionAuthorizationService;
import com.coreclaim.service.HologramService;
import com.coreclaim.service.OnlineRewardService;
import com.coreclaim.service.PendingClaimService;
import com.coreclaim.service.ProfileService;
import com.coreclaim.service.RemovalConfirmationService;
import com.coreclaim.storage.DatabaseManager;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.File;
import java.nio.file.Files;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.md_5.bungee.api.ChatColor;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.command.PluginCommand;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

public final class CoreClaimPlugin extends JavaPlugin {

    private static final Pattern AMPERSAND_HEX_PATTERN = Pattern.compile("(?i)&#([0-9A-F]{6})");
    private static final Pattern MINI_HEX_PATTERN = Pattern.compile("(?i)<#([0-9A-F]{6})>");

    private PluginConfig pluginConfig;
    private GroupConfig groupConfig;
    private ResourceConfig messageResource;
    private ResourceConfig groupsResource;
    private ResourceConfig rulesResource;
    private final Map<String, ResourceConfig> menuResources = new HashMap<>();
    private PlatformScheduler platformScheduler;
    private DatabaseManager databaseManager;
    private ClaimCoreFactory claimCoreFactory;
    private ProfileService profileService;
    private ClaimService claimService;
    private EconomyHook economyHook;
    private HologramService hologramService;
    private ClaimCleanupService claimCleanupService;
    private PendingClaimService pendingClaimService;
    private ClaimActionService claimActionService;
    private ClaimVisualService claimVisualService;
    private CrossServerTeleportService crossServerTeleportService;
    private ClaimSyncService claimSyncService;
    private ClaimSelectionService claimSelectionService;
    private ClaimInputService claimInputService;
    private ClaimTransferService claimTransferService;
    private MenuService menuService;
    private OnlineRewardService onlineRewardService;
    private RemovalConfirmationService removalConfirmationService;
    private ExplosionAuthorizationService explosionAuthorizationService;
    private ClaimEnterLeaveListener claimEnterLeaveListener;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        ensureConfigDefaults();
        ensureRulesDefaults();
        ensureHealthyGuiResources();
        this.messageResource = new ResourceConfig(this, "messages.yml");
        this.groupsResource = new ResourceConfig(this, "groups.yml");
        this.rulesResource = new ResourceConfig(this, "rules.yml");
        loadMenuResources();

        this.pluginConfig = new PluginConfig(getConfig(), rulesResource.config());
        this.groupConfig = new GroupConfig(groupsResource.config());
        this.platformScheduler = new PlatformScheduler(this);
        this.databaseManager = new DatabaseManager(this);
        this.claimCoreFactory = new ClaimCoreFactory(this);
        this.profileService = new ProfileService(databaseManager);
        this.claimService = new ClaimService(this, databaseManager, profileService);
        this.economyHook = new EconomyHook(this);
        this.hologramService = new HologramService(this);
        this.claimCleanupService = new ClaimCleanupService(this, databaseManager, claimService, profileService, hologramService, platformScheduler);
        this.claimService.setClaimCleanupService(claimCleanupService);
        this.claimVisualService = new ClaimVisualService(this);
        this.claimSyncService = new ClaimSyncService(this, databaseManager, claimService, hologramService);
        this.claimService.setClaimSyncPublisher(claimSyncService);
        this.crossServerTeleportService = new CrossServerTeleportService(this, databaseManager, claimService, claimVisualService);
        this.onlineRewardService = new OnlineRewardService(this, platformScheduler, profileService, claimService, claimCoreFactory, claimCleanupService);
        this.pendingClaimService = new PendingClaimService(
            this,
            claimService,
            profileService,
            claimCoreFactory,
            hologramService,
            claimVisualService,
            economyHook,
            onlineRewardService
        );
        this.claimActionService = new ClaimActionService(this, claimService, hologramService, claimVisualService, economyHook, crossServerTeleportService);
        this.claimSelectionService = new ClaimSelectionService(
            this,
            claimService,
            profileService,
            claimVisualService,
            hologramService,
            economyHook,
            onlineRewardService
        );
        this.claimInputService = new ClaimInputService(this, claimService, profileService);
        this.claimTransferService = new ClaimTransferService(this, claimService, profileService);
        this.removalConfirmationService = new RemovalConfirmationService(this, claimActionService, claimService);
        this.explosionAuthorizationService = new ExplosionAuthorizationService();
        this.menuService = new MenuService(
            this,
            claimService,
            profileService,
            claimActionService,
            removalConfirmationService,
            claimInputService,
            claimSelectionService
        );
        getServer().getPluginManager().registerEvents(
            new ClaimCoreListener(this, claimCoreFactory, pendingClaimService),
            this
        );
        getServer().getPluginManager().registerEvents(
            new ClaimProtectionListener(this, claimService, claimCoreFactory, explosionAuthorizationService, claimCleanupService),
            this
        );
        getServer().getPluginManager().registerEvents(new ClaimSelectionListener(claimSelectionService), this);
        getServer().getPluginManager().registerEvents(new SelectionToolListener(claimSelectionService, onlineRewardService), this);
        getServer().getPluginManager().registerEvents(
            new ClaimEnvironmentProtectionListener(claimService, explosionAuthorizationService, claimCleanupService),
            this
        );
        getServer().getPluginManager().registerEvents(
            new ClaimCoreInteractionListener(this, claimService, pendingClaimService, claimActionService, menuService),
            this
        );
        getServer().getPluginManager().registerEvents(new ClaimNamingListener(this, pendingClaimService), this);
        getServer().getPluginManager().registerEvents(new ClaimInputListener(this, claimInputService), this);
        getServer().getPluginManager().registerEvents(new MenuListener(menuService), this);
        this.claimEnterLeaveListener = new ClaimEnterLeaveListener(this, claimService, profileService, claimVisualService);
        getServer().getPluginManager().registerEvents(claimEnterLeaveListener, this);
        getServer().getPluginManager().registerEvents(new CrossServerTeleportListener(this, crossServerTeleportService), this);
        getServer().getPluginManager().registerEvents(new RemovalConfirmListener(this, removalConfirmationService), this);

        getServer().getMessenger().registerOutgoingPluginChannel(this, "BungeeCord");

        PluginCommand command = getCommand("claim");
        if (command != null) {
            CoreClaimCommand executor = new CoreClaimCommand(
                this,
                claimService,
                profileService,
                claimActionService,
                claimVisualService,
                claimSelectionService,
                menuService,
                removalConfirmationService,
                claimTransferService,
                claimCleanupService
            );
            command.setExecutor(executor);
            command.setTabCompleter(executor);
        }

        if (getServer().getPluginManager().getPlugin("PlaceholderAPI") != null) {
            new CoreClaimPlaceholderExpansion(this, profileService, claimService).register();
        }

        hologramService.refreshAll(claimService);
        claimSyncService.start();
        claimCleanupService.start();
        onlineRewardService.start();
        logSharedModeWarnings();
        logLegacyRuleConfigWarnings();
        getLogger().info(message("database-ready", "{file}", databaseManager.displayName()));
        getLogger().info("CoreClaim enabled in " + (platformScheduler.isFolia() ? "Folia" : "Spigot/Bukkit") + " mode.");
    }

    @Override
    public void onDisable() {
        if (onlineRewardService != null) {
            onlineRewardService.stop();
        }
        if (claimCleanupService != null) {
            claimCleanupService.stop();
        }
        if (claimTransferService != null) {
            claimTransferService.clear();
        }
        if (claimSyncService != null) {
            claimSyncService.stop();
        }
        if (profileService != null) {
            profileService.save();
        }
        if (claimService != null) {
            claimService.save();
        }
        if (claimEnterLeaveListener != null) {
            claimEnterLeaveListener.shutdown();
        }
        if (hologramService != null) {
            hologramService.clearAllLoadedHolograms();
        }
        if (databaseManager != null) {
            databaseManager.close();
        }
        getServer().getMessenger().unregisterOutgoingPluginChannel(this, "BungeeCord");
    }

    public PluginConfig settings() {
        return pluginConfig;
    }

    public FileConfiguration menuConfig(String menuKey) {
        ResourceConfig resource = menuResources.get(menuKey);
        if (resource == null) {
            throw new IllegalArgumentException("Unknown menu config: " + menuKey);
        }
        return resource.config();
    }

    public FileConfiguration messagesConfig() {
        return messageResource.config();
    }

    public FileConfiguration rulesConfig() {
        return rulesResource.config();
    }

    public GroupConfig groups() {
        return groupConfig;
    }

    public ClaimCoreFactory claimCoreFactory() {
        return claimCoreFactory;
    }

    public ProfileService profileService() {
        return profileService;
    }

    public ClaimService claimService() {
        return claimService;
    }

    public ClaimCleanupService claimCleanupService() {
        return claimCleanupService;
    }

    public EconomyHook economy() {
        return economyHook;
    }

    public PlatformScheduler platformScheduler() {
        return platformScheduler;
    }

    public String color(String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        String colored = applyHexColors(text, AMPERSAND_HEX_PATTERN);
        colored = applyHexColors(colored, MINI_HEX_PATTERN);
        return colored.replace('&', '\u00A7');
    }

    public String message(String path) {
        return message(path, new String[0]);
    }

    public String message(String path, String... replacements) {
        String prefix = messagesConfig().getString("prefix", "&6[CoreClaim] &f");
        String body = messagesConfig().getString(path, path);
        String message = prefix + body;
        for (int index = 0; index + 1 < replacements.length; index += 2) {
            message = message.replace(replacements[index], replacements[index + 1]);
        }
        return color(message);
    }

    private String applyHexColors(String input, Pattern pattern) {
        Matcher matcher = pattern.matcher(input);
        StringBuffer buffer = new StringBuffer();
        while (matcher.find()) {
            matcher.appendReplacement(buffer, Matcher.quoteReplacement(ChatColor.of("#" + matcher.group(1)).toString()));
        }
        matcher.appendTail(buffer);
        return buffer.toString();
    }

    public int reloadPluginResources() {
        reloadConfig();
        ensureConfigDefaults();
        ensureRulesDefaults();
        ensureHealthyGuiResources();
        reloadConfig();
        messageResource.reload();
        groupsResource.reload();
        rulesResource.reload();
        menuResources.values().forEach(ResourceConfig::reload);
        this.pluginConfig = new PluginConfig(getConfig(), rulesResource.config());
        this.groupConfig = new GroupConfig(groupsResource.config());
        int claimCount = claimService == null ? 0 : claimService.reloadClaims();
        if (claimCleanupService != null) {
            claimCleanupService.reload();
        }
        if (claimSyncService != null) {
            claimSyncService.reloadSettings();
        }
        if (crossServerTeleportService != null) {
            crossServerTeleportService.reloadSettings();
        }
        if (hologramService != null && claimService != null) {
            hologramService.refreshAll(claimService);
        }
        if (claimSyncService != null) {
            claimSyncService.publishClaimsReloaded();
        }
        logSharedModeWarnings();
        logLegacyRuleConfigWarnings();
        return claimCount;
    }

    private void ensureConfigDefaults() {
        try (InputStream inputStream = getResource("config.yml")) {
            if (inputStream == null) {
                return;
            }
            FileConfiguration defaults = YamlConfiguration.loadConfiguration(new InputStreamReader(inputStream, StandardCharsets.UTF_8));
            FileConfiguration config = getConfig();
            List<String> missingPaths = new ArrayList<>();
            collectMissingConfigPaths(defaults, config, "", missingPaths);
            if (missingPaths.isEmpty()) {
                return;
            }
            config.setDefaults(defaults);
            config.options().copyDefaults(true);
            saveConfig();
            getLogger().info("Added missing config defaults: " + String.join(", ", missingPaths));
        } catch (Exception exception) {
            getLogger().warning("Failed to merge config defaults: " + exception.getMessage());
        }
    }

    private void collectMissingConfigPaths(ConfigurationSection defaults, FileConfiguration config, String prefix, List<String> missingPaths) {
        for (String key : defaults.getKeys(false)) {
            String path = prefix.isEmpty() ? key : prefix + "." + key;
            Object value = defaults.get(key);
            if (value instanceof ConfigurationSection section) {
                collectMissingConfigPaths(section, config, path, missingPaths);
                continue;
            }
            if (!config.contains(path)) {
                missingPaths.add(path);
            }
        }
    }

    private void logSharedModeWarnings() {
        if (databaseManager == null || !databaseManager.isMySql() || pluginConfig == null) {
            return;
        }
        if (pluginConfig.serverId() == null
            || pluginConfig.serverId().isBlank()
            || "local".equalsIgnoreCase(pluginConfig.serverId().trim())) {
            getLogger().warning("database.type=mysql is enabled, but server-id is still 'local'. Set a unique server-id on every backend.");
        }
        if (!pluginConfig.claimSync().enabled()) {
            getLogger().warning("database.type=mysql is enabled, but claim-sync.enabled=false. Shared claim cache updates require Redis or manual /claim reload.");
        } else if (!pluginConfig.claimSync().usesRedis()) {
            getLogger().warning("claim-sync.enabled=true, but claim-sync.transport is not redis. Shared claim cache sync will not start.");
        }
        if (!pluginConfig.crossServerTeleportEnabled()) {
            getLogger().warning("cross-server-teleport.enabled=false. Remote claim menus can show, but cross-server teleport is disabled.");
        }
    }

    private void logLegacyRuleConfigWarnings() {
        File legacyFlagsFile = new File(getDataFolder(), "flags.yml");
        if (legacyFlagsFile.exists()) {
            getLogger().warning("Legacy flags.yml was found. CoreClaim now uses rules.yml; flags.yml is only kept as compatibility fallback.");
        }
        if (getConfig().getConfigurationSection("flags") != null) {
            getLogger().warning("Legacy flags config was found in config.yml. CoreClaim now uses rules.yml; config.yml.flags is only kept as compatibility fallback.");
        }
        if (getConfig().getConfigurationSection("permissions.new-claim-defaults") != null
            || getConfig().getConfigurationSection("permissions.system-claim-defaults") != null) {
            getLogger().warning("Legacy permission defaults were found in config.yml. CoreClaim now uses rules.yml; config.yml.permissions is only kept as compatibility fallback.");
        }
    }

    private void ensureRulesDefaults() {
        try (InputStream inputStream = getResource("rules.yml")) {
            if (inputStream == null) {
                return;
            }
            if (!getDataFolder().exists() && !getDataFolder().mkdirs()) {
                throw new IllegalStateException("无法创建插件数据目录。");
            }
            File file = new File(getDataFolder(), "rules.yml");
            boolean existed = file.exists();
            FileConfiguration defaults = YamlConfiguration.loadConfiguration(new InputStreamReader(inputStream, StandardCharsets.UTF_8));
            FileConfiguration rulesConfig = existed ? YamlConfiguration.loadConfiguration(file) : new YamlConfiguration();
            boolean changed = false;
            File legacyFlagsFile = new File(getDataFolder(), "flags.yml");
            if (legacyFlagsFile.exists()) {
                FileConfiguration legacyFlags = YamlConfiguration.loadConfiguration(legacyFlagsFile);
                changed |= migrateLegacySection(legacyFlags.getConfigurationSection("new-claim-defaults"), rulesConfig, "new-claim-defaults.flags");
                changed |= migrateLegacySection(legacyFlags.getConfigurationSection("system-claim-defaults"), rulesConfig, "system-claim-defaults.flags");
                if (changed) {
                    getLogger().info("Migrated legacy flag defaults from flags.yml to rules.yml");
                }
            }
            changed |= migrateLegacySection(getConfig().getConfigurationSection("flags.new-claim-defaults"), rulesConfig, "new-claim-defaults.flags");
            changed |= migrateLegacySection(getConfig().getConfigurationSection("flags.system-claim-defaults"), rulesConfig, "system-claim-defaults.flags");
            changed |= migrateLegacySection(getConfig().getConfigurationSection("permissions.new-claim-defaults"), rulesConfig, "new-claim-defaults.permissions");
            changed |= migrateLegacySection(getConfig().getConfigurationSection("permissions.system-claim-defaults"), rulesConfig, "system-claim-defaults.permissions");
            List<String> missingPaths = new ArrayList<>();
            collectMissingConfigPaths(defaults, rulesConfig, "", missingPaths);
            if (!missingPaths.isEmpty()) {
                rulesConfig.setDefaults(defaults);
                rulesConfig.options().copyDefaults(true);
                changed = true;
            }
            if (!existed || changed) {
                rulesConfig.save(file);
                if (!missingPaths.isEmpty()) {
                    getLogger().info("Added missing rules defaults: " + String.join(", ", missingPaths));
                }
            }
        } catch (Exception exception) {
            getLogger().warning("Failed to prepare rules defaults: " + exception.getMessage());
        }
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
        menuResources.put("claim-list", new ResourceConfig(this, "gui/claim-list.yml"));
        menuResources.put("claim-view", new ResourceConfig(this, "gui/claim-view.yml"));
        menuResources.put("claim-manage", new ResourceConfig(this, "gui/claim-manage.yml"));
        menuResources.put("trust", new ResourceConfig(this, "gui/trust.yml"));
        menuResources.put("trust-online-add", new ResourceConfig(this, "gui/trust-online-add.yml"));
        menuResources.put("claim-permissions", new ResourceConfig(this, "gui/claim-permissions.yml"));
        menuResources.put("selection-create", new ResourceConfig(this, "gui/selection-create.yml"));
        menuResources.put("core", new ResourceConfig(this, "gui/core.yml"));
    }

    private void ensureHealthyGuiResources() {
        for (String resource : List.of(
            "gui/claim-list.yml",
            "gui/claim-view.yml",
            "gui/claim-manage.yml",
            "gui/trust.yml",
            "gui/trust-online-add.yml",
            "gui/claim-permissions.yml",
            "gui/selection-create.yml",
            "gui/core.yml"
        )) {
            repairCorruptedGuiResource(resource);
        }
    }

    private void repairCorruptedGuiResource(String fileName) {
        try {
            File file = new File(getDataFolder(), fileName);
            if (!file.exists()) {
                return;
            }
            String content = Files.readString(file.toPath(), StandardCharsets.UTF_8);
            if (!looksLikeGuiMojibake(content) && !looksLikeOutdatedGuiLayout(fileName, content)) {
                return;
            }
            saveResource(fileName, true);
            getLogger().warning("Detected outdated or corrupted GUI content in " + fileName + ". Replaced it with the bundled resource.");
        } catch (Exception exception) {
            getLogger().warning("Failed to verify GUI resource " + fileName + ": " + exception.getMessage());
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
        return switch (fileName) {
            case "gui/claim-list.yml" -> !content.contains("layout-version: 2");
            case "gui/claim-view.yml" -> !content.contains("layout-version: 1");
            case "gui/trust-online-add.yml" -> !content.contains("layout-version: 1");
            case "gui/core.yml",
                 "gui/claim-permissions.yml" -> !content.contains("layout-version: 5");
            case "gui/trust.yml" -> !content.contains("layout-version: 4");
            case "gui/selection-create.yml" -> !content.contains("layout-version: 3");
            default -> false;
        };
    }
}
