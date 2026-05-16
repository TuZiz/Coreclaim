package com.coreclaim.storage.sql;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class UpsertSqlFactoryTest {

    @Test
    void claimUpsertSqlIncludesSpawnColumnsAndExpectedPlaceholders() {
        String sql = new UpsertSqlFactory(true).claimUpsertSql();
        assertTrue(sql.contains("name_key"));
        assertTrue(sql.contains("creation_type"));
        assertTrue(sql.contains("allow_animal_spawn"));
        assertTrue(sql.contains("allow_monster_spawn"));
        assertEquals(43L, sql.chars().filter(ch -> ch == '?').count());
    }

    @Test
    void memberSettingsUpsertSqlIncludesSpawnColumnsAndExpectedPlaceholders() {
        String sql = new UpsertSqlFactory(false).memberSettingsUpsertSql();
        assertTrue(sql.contains("allow_animal_spawn"));
        assertTrue(sql.contains("allow_monster_spawn"));
        assertEquals(14L, sql.chars().filter(ch -> ch == '?').count());
    }
}
