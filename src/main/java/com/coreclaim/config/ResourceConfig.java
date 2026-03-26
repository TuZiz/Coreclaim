package com.coreclaim.config;

import com.coreclaim.CoreClaimPlugin;
import java.io.File;
import java.io.IOException;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

public final class ResourceConfig {

    private final CoreClaimPlugin plugin;
    private final String fileName;
    private File file;
    private FileConfiguration configuration;

    public ResourceConfig(CoreClaimPlugin plugin, String fileName) {
        this.plugin = plugin;
        this.fileName = fileName;
        reload();
    }

    public void reload() {
        if (!plugin.getDataFolder().exists() && !plugin.getDataFolder().mkdirs()) {
            throw new IllegalStateException("无法创建插件数据目录");
        }
        this.file = new File(plugin.getDataFolder(), fileName);
        File parent = file.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            throw new IllegalStateException("无法创建配置目录: " + parent.getAbsolutePath());
        }
        if (!file.exists()) {
            plugin.saveResource(fileName, false);
        }
        this.configuration = YamlConfiguration.loadConfiguration(file);
    }

    public FileConfiguration config() {
        return configuration;
    }

    public void save() {
        try {
            configuration.save(file);
        } catch (IOException exception) {
            plugin.getLogger().warning("保存配置文件失败 " + fileName + ": " + exception.getMessage());
        }
    }
}
