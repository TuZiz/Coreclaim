package com.coreclaim;

import com.coreclaim.command.CoreClaimCommand;
import com.coreclaim.config.GroupConfig;
import com.coreclaim.config.PluginConfig;
import com.coreclaim.config.ResourceConfig;
import com.coreclaim.economy.EconomyHook;
import com.coreclaim.gui.MenuService;
import com.coreclaim.item.ClaimCoreFactory;
import com.coreclaim.listener.ClaimEnterLeaveListener;
import com.coreclaim.listener.ClaimCoreListener;
import com.coreclaim.listener.ClaimCoreInteractionListener;
import com.coreclaim.listener.ClaimNamingListener;
import com.coreclaim.listener.ClaimProtectionListener;
import com.coreclaim.listener.MenuListener;
import com.coreclaim.listener.RemovalConfirmListener;
import com.coreclaim.papi.CoreClaimPlaceholderExpansion;
import com.coreclaim.platform.PlatformScheduler;
import com.coreclaim.service.ClaimActionService;
import com.coreclaim.service.ClaimService;
import com.coreclaim.service.ClaimVisualService;
import com.coreclaim.service.HologramService;
import com.coreclaim.service.OnlineRewardService;
import com.coreclaim.service.PendingClaimService;
import com.coreclaim.service.ProfileService;
import com.coreclaim.service.RemovalConfirmationService;
import com.coreclaim.storage.DatabaseManager;
import java.util.HashMap;
import java.util.Map;
import org.bukkit.command.PluginCommand;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

public final class CoreClaimPlugin extends JavaPlugin {

    private PluginConfig pluginConfig;
    private GroupConfig groupConfig;
    private ResourceConfig messageResource;
    private ResourceConfig groupsResource;
    private final Map<String, ResourceConfig> menuResources = new HashMap<>();
    private PlatformScheduler platformScheduler;
    private DatabaseManager databaseManager;
    private ClaimCoreFactory claimCoreFactory;
    private ProfileService profileService;
    private ClaimService claimService;
    private EconomyHook economyHook;
    private HologramService hologramService;
    private PendingClaimService pendingClaimService;
    private ClaimActionService claimActionService;
    private ClaimVisualService claimVisualService;
    private MenuService menuService;
    private OnlineRewardService onlineRewardService;
    private RemovalConfirmationService removalConfirmationService;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        this.messageResource = new ResourceConfig(this, "messages.yml");
        this.groupsResource = new ResourceConfig(this, "groups.yml");
        loadMenuResources();

        this.pluginConfig = new PluginConfig(getConfig());
        this.groupConfig = new GroupConfig(groupsResource.config());
        this.platformScheduler = new PlatformScheduler(this);
        this.databaseManager = new DatabaseManager(this);
        this.claimCoreFactory = new ClaimCoreFactory(this);
        this.profileService = new ProfileService(databaseManager);
        this.claimService = new ClaimService(this, databaseManager, profileService);
        this.economyHook = new EconomyHook(this);
        this.hologramService = new HologramService(this);
        this.claimVisualService = new ClaimVisualService(this);
        this.pendingClaimService = new PendingClaimService(this, claimService, profileService, claimCoreFactory, hologramService, claimVisualService);
        this.claimActionService = new ClaimActionService(this, claimService, hologramService, claimVisualService, economyHook);
        this.removalConfirmationService = new RemovalConfirmationService(this, claimActionService, claimService);
        this.menuService = new MenuService(this, claimService, profileService, claimActionService, removalConfirmationService);
        this.onlineRewardService = new OnlineRewardService(this, platformScheduler, profileService, claimCoreFactory);

        getServer().getPluginManager().registerEvents(
            new ClaimCoreListener(this, claimCoreFactory, pendingClaimService),
            this
        );
        getServer().getPluginManager().registerEvents(
            new ClaimProtectionListener(this, claimService, claimCoreFactory),
            this
        );
        getServer().getPluginManager().registerEvents(
            new ClaimCoreInteractionListener(this, claimService, pendingClaimService, claimActionService, menuService),
            this
        );
        getServer().getPluginManager().registerEvents(new ClaimNamingListener(this, pendingClaimService), this);
        getServer().getPluginManager().registerEvents(new MenuListener(menuService), this);
        getServer().getPluginManager().registerEvents(new ClaimEnterLeaveListener(this, claimService), this);
        getServer().getPluginManager().registerEvents(new RemovalConfirmListener(this, removalConfirmationService), this);

        PluginCommand command = getCommand("claim");
        if (command != null) {
            CoreClaimCommand executor = new CoreClaimCommand(
                this,
                claimService,
                profileService,
                claimActionService,
                menuService,
                removalConfirmationService
            );
            command.setExecutor(executor);
            command.setTabCompleter(executor);
        }

        if (getServer().getPluginManager().getPlugin("PlaceholderAPI") != null) {
            new CoreClaimPlaceholderExpansion(profileService, claimService, pluginConfig, groupConfig).register();
        }

        hologramService.refreshAll(claimService);
        onlineRewardService.start();
        getLogger().info(message("database-ready", "{file}", databaseManager.databaseFile().getName()));
        getLogger().info("CoreClaim enabled in " + (platformScheduler.isFolia() ? "Folia" : "Spigot/Bukkit") + " mode.");
    }

    @Override
    public void onDisable() {
        if (onlineRewardService != null) {
            onlineRewardService.stop();
        }
        if (profileService != null) {
            profileService.save();
        }
        if (claimService != null) {
            claimService.save();
        }
        if (hologramService != null) {
            hologramService.clearAllLoadedHolograms();
        }
        if (databaseManager != null) {
            databaseManager.close();
        }
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

    public EconomyHook economy() {
        return economyHook;
    }

    public PlatformScheduler platformScheduler() {
        return platformScheduler;
    }

    public String color(String text) {
        return text == null ? "" : text.replace('&', '\u00A7');
    }

    public String message(String path) {
        String prefix = messagesConfig().getString("prefix", "&6[CoreClaim] &f");
        String body = messagesConfig().getString(path, path);
        return color(prefix + body);
    }

    public String message(String path, String... replacements) {
        String message = message(path);
        for (int index = 0; index + 1 < replacements.length; index += 2) {
            message = message.replace(replacements[index], replacements[index + 1]);
        }
        return message;
    }

    private void loadMenuResources() {
        menuResources.put("main", new ResourceConfig(this, "gui/main.yml"));
        menuResources.put("claim-list", new ResourceConfig(this, "gui/claim-list.yml"));
        menuResources.put("claim-manage", new ResourceConfig(this, "gui/claim-manage.yml"));
        menuResources.put("trust", new ResourceConfig(this, "gui/trust.yml"));
        menuResources.put("core", new ResourceConfig(this, "gui/core.yml"));
    }
}
