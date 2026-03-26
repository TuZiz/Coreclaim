package com.coreclaim.service;

import com.coreclaim.model.PlayerProfile;
import com.coreclaim.storage.DatabaseManager;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class ProfileService {

    private final DatabaseManager databaseManager;
    private final Map<UUID, PlayerProfile> profiles = new ConcurrentHashMap<>();

    public ProfileService(DatabaseManager databaseManager) {
        this.databaseManager = databaseManager;
        load();
    }

    public PlayerProfile getOrCreate(UUID uuid, String name) {
        PlayerProfile profile = profiles.computeIfAbsent(uuid, key -> new PlayerProfile(uuid, name, 0, 0, false));
        profile.setLastKnownName(name);
        return profile;
    }

    public void saveProfile(PlayerProfile profile) {
        databaseManager.update(
            """
            INSERT INTO profiles (uuid, name, activity_points, online_minutes, starter_core_granted)
            VALUES (?, ?, ?, ?, ?)
            ON CONFLICT(uuid) DO UPDATE SET
                name = excluded.name,
                activity_points = excluded.activity_points,
                online_minutes = excluded.online_minutes,
                starter_core_granted = excluded.starter_core_granted
            """,
            statement -> {
                statement.setString(1, profile.uuid().toString());
                statement.setString(2, profile.lastKnownName());
                statement.setInt(3, profile.activityPoints());
                statement.setInt(4, profile.onlineMinutes());
                statement.setInt(5, profile.starterCoreGranted() ? 1 : 0);
            }
        );

        databaseManager.update(
            "DELETE FROM profile_global_members WHERE owner_uuid = ?",
            statement -> statement.setString(1, profile.uuid().toString())
        );
        for (UUID memberId : profile.globalTrustedMembers()) {
            databaseManager.update(
                "INSERT INTO profile_global_members (owner_uuid, member_uuid) VALUES (?, ?)",
                statement -> {
                    statement.setString(1, profile.uuid().toString());
                    statement.setString(2, memberId.toString());
                }
            );
        }
    }

    public boolean addGlobalTrustedMember(UUID ownerId, UUID memberId) {
        PlayerProfile profile = profiles.get(ownerId);
        if (profile == null) {
            return false;
        }
        return profile.addGlobalTrustedMember(memberId);
    }

    public boolean removeGlobalTrustedMember(UUID ownerId, UUID memberId) {
        PlayerProfile profile = profiles.get(ownerId);
        if (profile == null) {
            return false;
        }
        return profile.removeGlobalTrustedMember(memberId);
    }

    public boolean isGloballyTrusted(UUID ownerId, UUID memberId) {
        PlayerProfile profile = profiles.get(ownerId);
        return profile != null && profile.isGloballyTrusted(memberId);
    }

    public void save() {
        for (PlayerProfile profile : profiles.values()) {
            saveProfile(profile);
        }
    }

    private void load() {
        databaseManager.query(
            "SELECT uuid, name, activity_points, online_minutes, starter_core_granted FROM profiles",
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
                        resultSet.getInt("starter_core_granted") == 1
                    );
                    profiles.put(uuid, profile);
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
}
