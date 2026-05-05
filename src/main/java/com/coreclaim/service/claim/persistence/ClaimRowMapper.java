package com.coreclaim.service.claim.persistence;

import com.coreclaim.model.Claim;
import com.coreclaim.model.ClaimFlag;
import com.coreclaim.model.ClaimFlagState;
import com.coreclaim.model.ClaimMemberSettings;
import com.coreclaim.model.ClaimPermission;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Map;
import java.util.UUID;

final class ClaimRowMapper {

    private ClaimRowMapper() {
    }

    static Claim claimFromResultSet(ResultSet resultSet) throws SQLException {
        int fallbackDistance = resultSet.getInt("radius");
        int east = resultSet.getInt("east");
        int south = resultSet.getInt("south");
        int west = resultSet.getInt("west");
        int north = resultSet.getInt("north");
        return new Claim(
            resultSet.getInt("id"),
            UUID.fromString(resultSet.getString("owner_uuid")),
            resultSet.getString("owner_name"),
            resultSet.getString("name"),
            resultSet.getString("server_id"),
            resultSet.getString("world"),
            resultSet.getInt("center_x"),
            resultSet.getInt("center_y"),
            resultSet.getInt("center_z"),
            resultSet.getInt("min_y"),
            resultSet.getInt("max_y"),
            resultSet.getInt("full_height") != 0,
            east <= 0 ? fallbackDistance : east,
            south <= 0 ? fallbackDistance : south,
            west <= 0 ? fallbackDistance : west,
            north <= 0 ? fallbackDistance : north,
            resultSet.getLong("created_at"),
            resultSet.getInt("core_visible") == 1,
            resultSet.getString("enter_message"),
            resultSet.getString("leave_message"),
            resultSet.getInt("allow_place") != 0,
            resultSet.getInt("allow_break") != 0,
            resultSet.getInt("allow_interact") != 0,
            resultSet.getInt("allow_container") != 0,
            resultSet.getInt("allow_mob_interact") != 0,
            resultSet.getInt("allow_animal_spawn") != 0,
            resultSet.getInt("allow_monster_spawn") != 0,
            resultSet.getInt("allow_redstone") != 0,
            resultSet.getInt("allow_explosion") != 0,
            resultSet.getInt("allow_bucket") != 0,
            resultSet.getInt("allow_teleport") != 0,
            resultSet.getInt("allow_flight") != 0,
            resultSet.getInt("system_managed") != 0,
            resultSet.getInt("deny_all") != 0,
            nullableDouble(resultSet, "tp_x"),
            nullableDouble(resultSet, "tp_y"),
            nullableDouble(resultSet, "tp_z"),
            nullableFloat(resultSet, "tp_yaw"),
            nullableFloat(resultSet, "tp_pitch"),
            resultSet.getLong("last_expanded_at")
        );
    }

    static Claim snapshotClaim(Claim claim) {
        if (claim == null) {
            return null;
        }
        Claim snapshot = new Claim(
            claim.id(),
            claim.owner(),
            claim.ownerName(),
            claim.name(),
            claim.serverId(),
            claim.world(),
            claim.centerX(),
            claim.centerY(),
            claim.centerZ(),
            claim.minY(),
            claim.maxY(),
            claim.fullHeight(),
            claim.east(),
            claim.south(),
            claim.west(),
            claim.north(),
            claim.createdAt(),
            claim.coreVisible(),
            claim.enterMessage(),
            claim.leaveMessage(),
            claim.permission(ClaimPermission.PLACE),
            claim.permission(ClaimPermission.BREAK),
            claim.permission(ClaimPermission.INTERACT),
            claim.permission(ClaimPermission.INTERACT),
            claim.permission(ClaimPermission.MOB_INTERACT),
            claim.permission(ClaimPermission.ANIMAL_SPAWN),
            claim.permission(ClaimPermission.MONSTER_SPAWN),
            claim.permission(ClaimPermission.REDSTONE),
            claim.permission(ClaimPermission.EXPLOSION),
            claim.permission(ClaimPermission.BUCKET),
            claim.permission(ClaimPermission.TELEPORT),
            claim.permission(ClaimPermission.FLIGHT),
            claim.systemManaged(),
            claim.denyAll(),
            claim.teleportX(),
            claim.teleportY(),
            claim.teleportZ(),
            claim.teleportYaw(),
            claim.teleportPitch(),
            claim.lastExpandedAt()
        );
        for (UUID memberId : claim.trustedMembers()) {
            snapshot.addTrustedMember(memberId);
        }
        for (UUID memberId : claim.deniedMembers()) {
            snapshot.addDeniedMember(memberId);
        }
        for (Map.Entry<ClaimFlag, ClaimFlagState> entry : claim.flagStates().entrySet()) {
            snapshot.setFlagState(entry.getKey(), entry.getValue());
        }
        for (Map.Entry<UUID, ClaimMemberSettings> entry : claim.memberSettings().entrySet()) {
            ClaimMemberSettings settings = entry.getValue();
            snapshot.setMemberSettings(entry.getKey(), new ClaimMemberSettings(
                settings.permission(ClaimPermission.PLACE),
                settings.permission(ClaimPermission.BREAK),
                settings.permission(ClaimPermission.INTERACT),
                settings.permission(ClaimPermission.INTERACT),
                settings.permission(ClaimPermission.MOB_INTERACT),
                settings.permission(ClaimPermission.ANIMAL_SPAWN),
                settings.permission(ClaimPermission.MONSTER_SPAWN),
                settings.permission(ClaimPermission.REDSTONE),
                settings.permission(ClaimPermission.EXPLOSION),
                settings.permission(ClaimPermission.BUCKET),
                settings.permission(ClaimPermission.TELEPORT),
                settings.permission(ClaimPermission.FLIGHT)
            ));
        }
        return snapshot;
    }

    static ClaimMemberSettings memberSettingsFromResultSet(ResultSet resultSet) throws SQLException {
        return new ClaimMemberSettings(
            resultSet.getInt("allow_place") != 0,
            resultSet.getInt("allow_break") != 0,
            resultSet.getInt("allow_interact") != 0,
            resultSet.getInt("allow_container") != 0,
            resultSet.getInt("allow_mob_interact") != 0,
            resultSet.getInt("allow_animal_spawn") != 0,
            resultSet.getInt("allow_monster_spawn") != 0,
            resultSet.getInt("allow_redstone") != 0,
            resultSet.getInt("allow_explosion") != 0,
            resultSet.getInt("allow_bucket") != 0,
            resultSet.getInt("allow_teleport") != 0,
            resultSet.getInt("allow_flight") != 0
        );
    }

    private static Double nullableDouble(ResultSet resultSet, String column) throws SQLException {
        double value = resultSet.getDouble(column);
        return resultSet.wasNull() ? null : value;
    }

    private static Float nullableFloat(ResultSet resultSet, String column) throws SQLException {
        double value = resultSet.getDouble(column);
        return resultSet.wasNull() ? null : (float) value;
    }
}
