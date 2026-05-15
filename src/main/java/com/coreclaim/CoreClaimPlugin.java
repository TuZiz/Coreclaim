package com.coreclaim;

import com.coreclaim.bootstrap.CommandRegistrar;
import com.coreclaim.bootstrap.ListenerRegistrar;
import com.coreclaim.bootstrap.PluginBootstrap;
import com.coreclaim.config.GroupConfig;
import com.coreclaim.config.PluginConfig;
import com.coreclaim.claim.mutation.ClaimCoreRegionService;
import com.coreclaim.economy.EconomyHook;
import com.coreclaim.gui.MenuService;
import com.coreclaim.item.ClaimCoreFactory;
import com.coreclaim.listener.ClaimEnterLeaveListener;
import com.coreclaim.papi.CoreClaimPlaceholderExpansion;
import com.coreclaim.platform.PlatformScheduler;
import com.coreclaim.service.ClaimActionService;
import com.coreclaim.cleanup.ClaimCleanupService;
import com.coreclaim.input.ClaimInputService;
import com.coreclaim.selection.ClaimSelectionService;
import com.coreclaim.service.ClaimService;
import com.coreclaim.sync.ClaimSyncService;
import com.coreclaim.transfer.ClaimTransferService;
import com.coreclaim.service.ClaimVisualService;
import com.coreclaim.teleport.CrossServerTeleportService;
import com.coreclaim.service.ExplosionAuthorizationService;
import com.coreclaim.service.HologramService;
import com.coreclaim.service.OnlineRewardService;
import com.coreclaim.service.PendingClaimService;
import com.coreclaim.profile.ProfileService;
import com.coreclaim.service.RemovalConfirmationService;
import com.coreclaim.storage.DatabaseManager;
import com.coreclaim.storage.DatabaseAsyncExecutor;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.md_5.bungee.api.ChatColor;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

public final class CoreClaimPlugin extends JavaPlugin {

    public static final String MESSAGE_RESOURCE_PATH = "lang/zh_cn.yml";
    public static final String ENGLISH_MESSAGE_RESOURCE_PATH = "lang/en_us.yml";
    private static final Pattern AMPERSAND_HEX_PATTERN = Pattern.compile("(?i)&#([0-9A-F]{6})");
    private static final Pattern MINI_HEX_PATTERN = Pattern.compile("(?i)<#([0-9A-F]{6})>");

    private PluginConfig pluginConfig;
    private GroupConfig groupConfig;
    private PluginResourceManager resourceManager;
    private PlatformScheduler platformScheduler;
    private DatabaseManager databaseManager;
    private DatabaseAsyncExecutor databaseAsyncExecutor;
    private ClaimCoreRegionService claimCoreRegionService;
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
        resourceManager = new PluginResourceManager(this);
        PluginResourceManager.PreparedResources preparedResources = resourceManager.prepare();
        this.pluginConfig = new PluginConfig(getConfig(), preparedResources.rulesResource().config());
        this.groupConfig = new GroupConfig(preparedResources.groupsResource().config());

        PluginBootstrap.BootstrapResult bootstrap = new PluginBootstrap().initialize(this, pluginConfig, groupConfig);
        this.pluginConfig = bootstrap.pluginConfig();
        this.groupConfig = bootstrap.groupConfig();
        this.platformScheduler = bootstrap.platformScheduler();
        this.databaseManager = bootstrap.databaseManager();
        this.databaseAsyncExecutor = bootstrap.databaseAsyncExecutor();
        this.claimCoreRegionService = bootstrap.claimCoreRegionService();
        this.claimCoreFactory = bootstrap.claimCoreFactory();
        this.profileService = bootstrap.profileService();
        this.claimService = bootstrap.claimService();
        this.economyHook = bootstrap.economyHook();
        this.hologramService = bootstrap.hologramService();
        this.claimCleanupService = bootstrap.claimCleanupService();
        this.pendingClaimService = bootstrap.pendingClaimService();
        this.claimActionService = bootstrap.claimActionService();
        this.claimVisualService = bootstrap.claimVisualService();
        this.crossServerTeleportService = bootstrap.crossServerTeleportService();
        this.claimSyncService = bootstrap.claimSyncService();
        this.claimSelectionService = bootstrap.claimSelectionService();
        this.claimInputService = bootstrap.claimInputService();
        this.claimTransferService = bootstrap.claimTransferService();
        this.menuService = bootstrap.menuService();
        this.onlineRewardService = bootstrap.onlineRewardService();
        this.removalConfirmationService = bootstrap.removalConfirmationService();
        this.explosionAuthorizationService = bootstrap.explosionAuthorizationService();
        this.claimEnterLeaveListener = new ListenerRegistrar().registerAll(this, bootstrap);

        getServer().getMessenger().registerOutgoingPluginChannel(this, "BungeeCord");
        new CommandRegistrar().registerClaimCommand(this, bootstrap);

        if (getServer().getPluginManager().getPlugin("PlaceholderAPI") != null) {
            new CoreClaimPlaceholderExpansion(this, profileService, claimService).register();
        }

        hologramService.refreshAll(claimService);
        claimSyncService.start();
        claimCleanupService.start();
        onlineRewardService.start();
        logSharedModeWarnings();
        resourceManager.logLegacyRuleConfigWarnings();
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
            hologramService.shutdown();
        }
        if (databaseAsyncExecutor != null) {
            databaseAsyncExecutor.close();
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
        return resourceManager.menuConfig(menuKey);
    }

    public FileConfiguration messagesConfig() {
        return resourceManager.messagesConfig();
    }

    public String messageResourcePath() {
        return resourceManager == null ? PluginResourceManager.messageResourcePath(this) : resourceManager.messageResourcePath();
    }

    public FileConfiguration rulesConfig() {
        return resourceManager.rulesConfig();
    }

    public GroupConfig groups() {
        return groupConfig;
    }

    public ClaimCoreFactory claimCoreFactory() {
        return claimCoreFactory;
    }

    public DatabaseAsyncExecutor databaseAsyncExecutor() {
        return databaseAsyncExecutor;
    }

    public ClaimCoreRegionService claimCoreRegionService() {
        return claimCoreRegionService;
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
        String prefix = messagesConfig().getString("prefix", "&#64748B[&#A7F3D0Claim&#64748B] &#CBD5E1");
        String body = messagesConfig().getString(path, path);
        String message = prefix + body;
        for (int index = 0; index + 1 < replacements.length; index += 2) {
            message = message.replace(replacements[index], replacements[index + 1]);
        }
        return color(message);
    }

    public String plainMessage(String path, String... replacements) {
        String message = messagesConfig().getString(path, path);
        for (int index = 0; index + 1 < replacements.length; index += 2) {
            message = message.replace(replacements[index], replacements[index + 1]);
        }
        return color(message);
    }

    public List<String> messageList(String path, String... replacements) {
        List<String> lines = messagesConfig().getStringList(path);
        if (lines.isEmpty()) {
            String single = messagesConfig().getString(path);
            if (single == null || single.isBlank()) {
                return List.of();
            }
            lines = List.of(single);
        }
        List<String> result = new ArrayList<>();
        for (String line : lines) {
            String formatted = line;
            for (int index = 0; index + 1 < replacements.length; index += 2) {
                formatted = formatted.replace(replacements[index], replacements[index + 1]);
            }
            result.add(color(formatted));
        }
        return result;
    }

    public int reloadPluginResources() {
        ensureResourceManager();
        reloadConfig();
        resourceManager.reloadResources();
        reloadConfig();
        this.pluginConfig = new PluginConfig(getConfig(), resourceManager.rulesConfig());
        this.groupConfig = new GroupConfig(resourceManager.groupsConfig());
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
        resourceManager.logLegacyRuleConfigWarnings();
        return claimCount;
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

    private void ensureResourceManager() {
        if (resourceManager == null) {
            resourceManager = new PluginResourceManager(this);
            resourceManager.prepare();
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
}
