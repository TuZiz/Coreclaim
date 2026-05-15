package com.coreclaim.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

class DatabaseManagerTest {

    @Test
    void legacyUseSslFalseMapsToDisabledWhenSslModeIsMissing() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("database.mysql.use-ssl", false);

        assertEquals("DISABLED", DatabaseManager.mysqlSslMode(config));
    }

    @Test
    void legacyUseSslTrueMapsToRequiredWhenSslModeIsMissing() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("database.mysql.use-ssl", true);

        assertEquals("REQUIRED", DatabaseManager.mysqlSslMode(config));
    }

    @Test
    void explicitSslModeWinsOverLegacyUseSsl() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("database.mysql.use-ssl", false);
        config.set("database.mysql.ssl-mode", "VERIFY_IDENTITY");

        assertEquals("VERIFY_IDENTITY", DatabaseManager.mysqlSslMode(config));
    }
}
