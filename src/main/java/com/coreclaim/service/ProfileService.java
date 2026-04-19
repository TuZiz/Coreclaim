package com.coreclaim.service;

import com.coreclaim.model.PlayerProfile;
import com.coreclaim.storage.DatabaseManager;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class ProfileService {

    private final DatabaseManager databaseManager;
    private final Map<UUID, PlayerProfile> profiles = new ConcurrentHashMap<>();
    private final Object knownNamesLock = new Object();
    private final Map<String, UUID> playerIdsByName = new HashMap<>();
    private final Map<String, String> displayNamesByName = new HashMap<>();
    private final Map<UUID, String> indexedNameByPlayer = new HashMap<>();
    private final Set<String> conflictedNames = new HashSet<>();

    public ProfileService(DatabaseManager databaseManager) {
        this.databaseManager = databaseManager;
        load();
    }

    public PlayerProfile getOrCreate(UUID uuid, String name) {
        PlayerProfile profile = profiles.computeIfAbsent(uuid, key -> new PlayerProfile(uuid, name, 0, 0, false, false, false, false));
        if (name != null && !name.isBlank()) {
            profile.setLastKnownName(name);
            refreshKnownName(profile);
        }
        return profile;
    }

    public void saveProfile(PlayerProfile profile) {
        refreshKnownName(profile);
        databaseManager.update(
            databaseManager.profileUpsertSql(),
            statement -> {
                statement.setString(1, profile.uuid().toString());
                statement.setString(2, profile.lastKnownName());
                statement.setInt(3, profile.activityPoints());
                statement.setInt(4, profile.onlineMinutes());
                statement.setInt(5, profile.starterCoreGranted() ? 1 : 0);
                statement.setInt(6, profile.starterCoreReclaimed() ? 1 : 0);
                statement.setInt(7, profile.starterCoreUsed() ? 1 : 0);
                statement.setInt(8, profile.autoShowBorders() ? 1 : 0);
            }
        );
    }

    public boolean addGlobalTrustedMember(UUID ownerId, UUID memberId) {
        PlayerProfile profile = profiles.get(ownerId);
        if (profile == null) {
            return false;
        }
        if (!profile.addGlobalTrustedMember(memberId)) {
            return false;
        }
        saveProfile(profile);
        databaseManager.update(
            databaseManager.insertIgnoreSql("profile_global_members", "owner_uuid, member_uuid", "?, ?"),
            statement -> {
                statement.setString(1, ownerId.toString());
                statement.setString(2, memberId.toString());
            }
        );
        return true;
    }

    public boolean removeGlobalTrustedMember(UUID ownerId, UUID memberId) {
        PlayerProfile profile = profiles.get(ownerId);
        if (profile == null) {
            return false;
        }
        if (!profile.removeGlobalTrustedMember(memberId)) {
            return false;
        }
        databaseManager.update(
            "DELETE FROM profile_global_members WHERE owner_uuid = ? AND member_uuid = ?",
            statement -> {
                statement.setString(1, ownerId.toString());
                statement.setString(2, memberId.toString());
            }
        );
        return true;
    }

    public boolean isGloballyTrusted(UUID ownerId, UUID memberId) {
        PlayerProfile profile = profiles.get(ownerId);
        return profile != null && profile.isGloballyTrusted(memberId);
    }

    public UUID findPlayerIdByName(String rawName) {
        String normalizedName = normalizeName(rawName);
        if (normalizedName == null) {
            return null;
        }
        synchronized (knownNamesLock) {
            if (conflictedNames.contains(normalizedName)) {
                return null;
            }
            return playerIdsByName.get(normalizedName);
        }
    }

    public List<String> knownPlayerNames() {
        synchronized (knownNamesLock) {
            List<String> names = new ArrayList<>(displayNamesByName.values());
            names.sort(String.CASE_INSENSITIVE_ORDER);
            return names;
        }
    }

    public boolean usesSharedDatabase() {
        return databaseManager.isMySql();
    }

    public void save() {
        if (databaseManager.isMySql()) {
            return;
        }
        for (PlayerProfile profile : profiles.values()) {
            saveProfile(profile);
        }
    }

    private void load() {
        databaseManager.query(
            "SELECT uuid, name, activity_points, online_minutes, starter_core_granted, starter_core_reclaimed, starter_core_used, auto_show_borders FROM profiles",
            statement -> {
            },
            resultSet -> {
                while (resultSet.next()) {
                    UUID uuid = UUID.fromString(resultSet.getString("uuid"));
                    PlayerProfile profile = new PlayerProfile(
                        uuid,
                        resultSet.getString("name"),
                        resultSet.getInt("activity_points"),
                        resultSet.getInt("online_minutes"),
                        resultSet.getInt("starter_core_granted") == 1,
                        resultSet.getInt("starter_core_reclaimed") == 1,
                        resultSet.getInt("starter_core_used") == 1,
                        resultSet.getInt("auto_show_borders") == 1
                    );
                    profiles.put(uuid, profile);
                    refreshKnownName(profile);
                }
                return null;
            }
        );

        databaseManager.query(
            "SELECT owner_uuid, member_uuid FROM profile_global_members",
            statement -> {
            },
            resultSet -> {
                while (resultSet.next()) {
                    UUID ownerId = UUID.fromString(resultSet.getString("owner_uuid"));
                    UUID memberId = UUID.fromString(resultSet.getString("member_uuid"));
                    PlayerProfile profile = profiles.get(ownerId);
                    if (profile != null) {
                        profile.addGlobalTrustedMember(memberId);
                    }
                }
                return null;
            }
        );
    }

    private void refreshKnownName(PlayerProfile profile) {
        String normalizedName = normalizeName(profile.lastKnownName());
        synchronized (knownNamesLock) {
            String oldName = indexedNameByPlayer.get(profile.uuid());
            if (oldName != null && !oldName.equals(normalizedName)) {
                indexedNameByPlayer.remove(profile.uuid());
                rebuildNameEntry(oldName);
            }
            if (normalizedName == null) {
                return;
            }
            indexedNameByPlayer.put(profile.uuid(), normalizedName);
            rebuildNameEntry(normalizedName);
        }
    }

    private void rebuildNameEntry(String normalizedName) {
        UUID uniqueMatch = null;
        String displayName = null;
        boolean conflict = false;
        for (Map.Entry<UUID, String> entry : indexedNameByPlayer.entrySet()) {
            if (!normalizedName.equals(entry.getValue())) {
                continue;
            }
            if (uniqueMatch == null) {
                uniqueMatch = entry.getKey();
                PlayerProfile profile = profiles.get(entry.getKey());
                displayName = profile == null ? null : profile.lastKnownName();
                continue;
            }
            conflict = true;
            break;
        }

        if (uniqueMatch == null) {
            conflictedNames.remove(normalizedName);
            playerIdsByName.remove(normalizedName);
            displayNamesByName.remove(normalizedName);
            return;
        }

        if (conflict) {
            conflictedNames.add(normalizedName);
            playerIdsByName.remove(normalizedName);
            displayNamesByName.remove(normalizedName);
            return;
        }

        conflictedNames.remove(normalizedName);
        playerIdsByName.put(normalizedName, uniqueMatch);
        displayNamesByName.put(normalizedName, displayName == null || displayName.isBlank() ? uniqueMatch.toString() : displayName);
    }

    private String normalizeName(String rawName) {
        if (rawName == null) {
            return null;
        }
        String trimmed = rawName.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        return trimmed.toLowerCase(Locale.ROOT);
    }
}
