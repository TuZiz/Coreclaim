package com.coreclaim.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

class GroupConfigTest {

    @Test
    void zeroMaxDistanceMeansUnlimited() {
        YamlConfiguration configuration = new YamlConfiguration();
        configuration.set("default.max-distance", 0);
        configuration.set("default.max-claims", 5);

        ClaimGroup group = new GroupConfig(configuration).resolve(null);

        assertEquals(0, group.maxDistance());
        assertFalse(group.hasDistanceLimit());
        assertEquals(Integer.MAX_VALUE, group.remainingDistance(24));
        assertEquals(30, group.clampExpandAmount(24, 30));
        assertFalse(group.exceedsDistanceLimit(300, 0, 0, 0));
    }

    @Test
    void missingMaxClaimsFallsBackToFive() {
        YamlConfiguration configuration = new YamlConfiguration();

        ClaimGroup group = new GroupConfig(configuration).resolve(null);

        assertEquals(0, group.maxDistance());
        assertEquals(5, group.maxClaims());
        assertFalse(group.hasDistanceLimit());
    }
}
