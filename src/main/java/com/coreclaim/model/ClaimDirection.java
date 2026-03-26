package com.coreclaim.model;

import java.util.Locale;

public enum ClaimDirection {
    NORTH("north", "北"),
    SOUTH("south", "南"),
    WEST("west", "西"),
    EAST("east", "东");

    private final String key;
    private final String displayName;

    ClaimDirection(String key, String displayName) {
        this.key = key;
        this.displayName = displayName;
    }

    public String key() {
        return key;
    }

    public String displayName() {
        return displayName;
    }

    public static ClaimDirection fromInput(String input) {
        if (input == null || input.isBlank()) {
            return null;
        }
        return switch (input.toLowerCase(Locale.ROOT)) {
            case "north", "n", "北" -> NORTH;
            case "south", "s", "南" -> SOUTH;
            case "west", "w", "西" -> WEST;
            case "east", "e", "东" -> EAST;
            default -> null;
        };
    }
}
