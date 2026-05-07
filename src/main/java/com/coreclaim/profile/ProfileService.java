package com.coreclaim.profile;

import com.coreclaim.profile.PlayerProfile;
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
        PlayerProfile profile = profiles.computeIfAbsent(uuid, key -> new PlayerProfile(
            uuid,
            name,
            0,
            0,
            0L,
            false,
            false,
            false,
            false,
            0L,
            "",
            false
        ));
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
                statement.setLong(5, profile.onlineSeconds());
                statement.setInt(6, profile.starterCoreGranted() ? 1 : 0);
                statement.setInt(7, profile.starterCoreReclaimed() ? 1 : 0);
                statement.setInt(8, profile.starterCoreUsed() ? 1 : 0);
                statement.setInt(9, profile.autoShowBorders() ? 1 : 0);
                statement.setLong(10, profile.lastSeenAt());
                statement.setString(11, profile.lastGroupKey());
                statement.setInt(12, profile.cleanupPermissionExempt() ? 1 : 0);
            }
        );
    }

    public PlayerProfile findProfile(UUID uuid) {
        return uuid == null ? null : profiles.get(uuid);
    }

    public void updatePresence(UUID uuid, String name, long lastSeenAt, String groupKey, boolean cleanupPermissionExempt) {
        PlayerProfile profile = getOrCreate(uuid, name);
        profile.setLastSeenAt(lastSeenAt);
        if (groupKey != null) {
            profile.setLastGroupKey(groupKey);
        }
        profile.setCleanupPermissionExempt(cleanupPermissionExempt);
        saveProfile(profile);
    }

    public void touchLastSeen(UUID uuid, String name, long lastSeenAt) {
        PlayerProfile profile = getOrCreate(uuid, name);
        profile.setLastSeenAt(lastSeenAt);
        saveProfile(profile);
    }

    public boolean addGlobalTrustedMember(UUID ownerId, UUID memberId) {
        return false;
    }

    public boolean removeGlobalTrustedMember(UUID ownerId, UUID memberId) {
        PlayerProfile profile = profiles.get(ownerId);
        boolean removedProfileEntry = profile != null && profile.removeGlobalTrustedMember(memberId);
        int removedRows = databaseManager.update(
            "DELETE FROM profile_global_members WHERE owner_uuid = ? AND member_uuid = ?",
            statement -> {
                statement.setString(1, ownerId.toString());
                statement.setString(2, memberId.toString());
            }
        );
        return removedProfileEntry || removedRows > 0;
    }

    public boolean isGloballyTrusted(UUID ownerId, UUID memberId) {
        return false;
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
            """
            SELECT uuid, name, activity_points, online_minutes, starter_core_granted, starter_core_reclaimed,
                   online_seconds, starter_core_used, auto_show_borders, last_seen_at, last_group_key, cleanup_permission_exempt
            FROM profiles
            """,
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
                        resultSet.getLong("online_seconds"),
                        resultSet.getInt("starter_core_granted") == 1,
                        resultSet.getInt("starter_core_reclaimed") == 1,
                        resultSet.getInt("starter_core_used") == 1,
                        resultSet.getInt("auto_show_borders") == 1,
                        resultSet.getLong("last_seen_at"),
                        resultSet.getString("last_group_key"),
                        resultSet.getInt("cleanup_permission_exempt") == 1
                    );
                    profiles.put(uuid, profile);
                    refreshKnownName(profile);
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
