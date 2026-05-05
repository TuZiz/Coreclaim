package com.coreclaim.storage.sql;

public final class UpsertSqlFactory {

    private final boolean mysql;

    public UpsertSqlFactory(boolean mysql) {
        this.mysql = mysql;
    }

    public String insertIgnoreSql(String table, String columns, String values) {
        if (mysql) {
            return "INSERT IGNORE INTO " + table + " (" + columns + ") VALUES (" + values + ")";
        }
        return "INSERT OR IGNORE INTO " + table + " (" + columns + ") VALUES (" + values + ")";
    }

    public String profileUpsertSql() {
        if (mysql) {
            return """
                INSERT INTO profiles (
                    uuid,
                    name,
                    activity_points,
                    online_minutes,
                    online_seconds,
                    starter_core_granted,
                    starter_core_reclaimed,
                    starter_core_used,
                    auto_show_borders,
                    last_seen_at,
                    last_group_key,
                    cleanup_permission_exempt
                )
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON DUPLICATE KEY UPDATE
                    name = VALUES(name),
                    activity_points = VALUES(activity_points),
                    online_minutes = VALUES(online_minutes),
                    online_seconds = VALUES(online_seconds),
                    starter_core_granted = VALUES(starter_core_granted),
                    starter_core_reclaimed = VALUES(starter_core_reclaimed),
                    starter_core_used = VALUES(starter_core_used),
                    auto_show_borders = VALUES(auto_show_borders),
                    last_seen_at = VALUES(last_seen_at),
                    last_group_key = VALUES(last_group_key),
                    cleanup_permission_exempt = VALUES(cleanup_permission_exempt)
                """;
        }
        return """
            INSERT INTO profiles (
                uuid,
                name,
                activity_points,
                online_minutes,
                online_seconds,
                starter_core_granted,
                starter_core_reclaimed,
                starter_core_used,
                auto_show_borders,
                last_seen_at,
                last_group_key,
                cleanup_permission_exempt
            )
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT(uuid) DO UPDATE SET
                name = excluded.name,
                activity_points = excluded.activity_points,
                online_minutes = excluded.online_minutes,
                online_seconds = excluded.online_seconds,
                starter_core_granted = excluded.starter_core_granted,
                starter_core_reclaimed = excluded.starter_core_reclaimed,
                starter_core_used = excluded.starter_core_used,
                auto_show_borders = excluded.auto_show_borders,
                last_seen_at = excluded.last_seen_at,
                last_group_key = excluded.last_group_key,
                cleanup_permission_exempt = excluded.cleanup_permission_exempt
            """;
    }

    public String claimUpsertSql() {
        if (mysql) {
            return """
                INSERT INTO claims (
                    id, owner_uuid, owner_name, name, core_visible, world, server_id, center_x, center_y, center_z,
                    min_y, max_y, full_height, radius, east, south, west, north, enter_message, leave_message,
                    allow_place, allow_break, allow_interact, allow_container, allow_mob_interact, allow_animal_spawn, allow_monster_spawn, allow_redstone,
                    allow_explosion, allow_bucket, allow_teleport, allow_flight, system_managed, deny_all, tp_x, tp_y, tp_z, tp_yaw, tp_pitch, last_expanded_at, created_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON DUPLICATE KEY UPDATE
                    owner_uuid = VALUES(owner_uuid),
                    owner_name = VALUES(owner_name),
                    name = VALUES(name),
                    core_visible = VALUES(core_visible),
                    world = VALUES(world),
                    server_id = VALUES(server_id),
                    center_x = VALUES(center_x),
                    center_y = VALUES(center_y),
                    center_z = VALUES(center_z),
                    min_y = VALUES(min_y),
                    max_y = VALUES(max_y),
                    full_height = VALUES(full_height),
                    radius = VALUES(radius),
                    east = VALUES(east),
                    south = VALUES(south),
                    west = VALUES(west),
                    north = VALUES(north),
                    enter_message = VALUES(enter_message),
                    leave_message = VALUES(leave_message),
                    allow_place = VALUES(allow_place),
                    allow_break = VALUES(allow_break),
                    allow_interact = VALUES(allow_interact),
                    allow_container = VALUES(allow_container),
                    allow_mob_interact = VALUES(allow_mob_interact),
                    allow_animal_spawn = VALUES(allow_animal_spawn),
                    allow_monster_spawn = VALUES(allow_monster_spawn),
                    allow_redstone = VALUES(allow_redstone),
                    allow_explosion = VALUES(allow_explosion),
                    allow_bucket = VALUES(allow_bucket),
                    allow_teleport = VALUES(allow_teleport),
                    allow_flight = VALUES(allow_flight),
                    system_managed = VALUES(system_managed),
                    deny_all = VALUES(deny_all),
                    tp_x = VALUES(tp_x),
                    tp_y = VALUES(tp_y),
                    tp_z = VALUES(tp_z),
                    tp_yaw = VALUES(tp_yaw),
                    tp_pitch = VALUES(tp_pitch),
                    last_expanded_at = VALUES(last_expanded_at),
                    created_at = VALUES(created_at)
                """;
        }
        return """
            INSERT INTO claims (
                id, owner_uuid, owner_name, name, core_visible, world, server_id, center_x, center_y, center_z,
                min_y, max_y, full_height, radius, east, south, west, north, enter_message, leave_message,
                allow_place, allow_break, allow_interact, allow_container, allow_mob_interact, allow_animal_spawn, allow_monster_spawn, allow_redstone, allow_explosion, allow_bucket, allow_teleport, allow_flight, system_managed, deny_all, tp_x, tp_y, tp_z, tp_yaw, tp_pitch, last_expanded_at, created_at
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT(id) DO UPDATE SET
                owner_uuid = excluded.owner_uuid,
                owner_name = excluded.owner_name,
                name = excluded.name,
                core_visible = excluded.core_visible,
                world = excluded.world,
                server_id = excluded.server_id,
                center_x = excluded.center_x,
                center_y = excluded.center_y,
                center_z = excluded.center_z,
                min_y = excluded.min_y,
                max_y = excluded.max_y,
                full_height = excluded.full_height,
                radius = excluded.radius,
                east = excluded.east,
                south = excluded.south,
                west = excluded.west,
                north = excluded.north,
                enter_message = excluded.enter_message,
                leave_message = excluded.leave_message,
                allow_place = excluded.allow_place,
                allow_break = excluded.allow_break,
                allow_interact = excluded.allow_interact,
                allow_container = excluded.allow_container,
                allow_mob_interact = excluded.allow_mob_interact,
                allow_animal_spawn = excluded.allow_animal_spawn,
                allow_monster_spawn = excluded.allow_monster_spawn,
                allow_redstone = excluded.allow_redstone,
                allow_explosion = excluded.allow_explosion,
                allow_bucket = excluded.allow_bucket,
                allow_teleport = excluded.allow_teleport,
                allow_flight = excluded.allow_flight,
                system_managed = excluded.system_managed,
                deny_all = excluded.deny_all,
                tp_x = excluded.tp_x,
                tp_y = excluded.tp_y,
                tp_z = excluded.tp_z,
                tp_yaw = excluded.tp_yaw,
                tp_pitch = excluded.tp_pitch,
                last_expanded_at = excluded.last_expanded_at,
                created_at = excluded.created_at
            """;
    }

    public String memberSettingsUpsertSql() {
        if (mysql) {
            return """
                INSERT INTO claim_member_permissions (
                    claim_id, player_uuid, allow_place, allow_break, allow_interact, allow_container,
                    allow_mob_interact, allow_animal_spawn, allow_monster_spawn, allow_redstone, allow_explosion, allow_bucket, allow_teleport, allow_flight
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON DUPLICATE KEY UPDATE
                    allow_place = VALUES(allow_place),
                    allow_break = VALUES(allow_break),
                    allow_interact = VALUES(allow_interact),
                    allow_container = VALUES(allow_container),
                    allow_mob_interact = VALUES(allow_mob_interact),
                    allow_animal_spawn = VALUES(allow_animal_spawn),
                    allow_monster_spawn = VALUES(allow_monster_spawn),
                    allow_redstone = VALUES(allow_redstone),
                    allow_explosion = VALUES(allow_explosion),
                    allow_bucket = VALUES(allow_bucket),
                    allow_teleport = VALUES(allow_teleport),
                    allow_flight = VALUES(allow_flight)
                """;
        }
        return """
            INSERT INTO claim_member_permissions (
                claim_id, player_uuid, allow_place, allow_break, allow_interact, allow_container, allow_mob_interact, allow_animal_spawn, allow_monster_spawn, allow_redstone, allow_explosion, allow_bucket, allow_teleport, allow_flight
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT(claim_id, player_uuid) DO UPDATE SET
                allow_place = excluded.allow_place,
                allow_break = excluded.allow_break,
                allow_interact = excluded.allow_interact,
                allow_container = excluded.allow_container,
                allow_mob_interact = excluded.allow_mob_interact,
                allow_animal_spawn = excluded.allow_animal_spawn,
                allow_monster_spawn = excluded.allow_monster_spawn,
                allow_redstone = excluded.allow_redstone,
                allow_explosion = excluded.allow_explosion,
                allow_bucket = excluded.allow_bucket,
                allow_teleport = excluded.allow_teleport,
                allow_flight = excluded.allow_flight
            """;
    }

    public String metaUpsertSql() {
        if (mysql) {
            return """
                INSERT INTO coreclaim_meta (meta_key, meta_value)
                VALUES (?, ?)
                ON DUPLICATE KEY UPDATE meta_value = VALUES(meta_value)
                """;
        }
        return """
            INSERT INTO coreclaim_meta (meta_key, meta_value)
            VALUES (?, ?)
            ON CONFLICT(meta_key) DO UPDATE SET meta_value = excluded.meta_value
            """;
    }
}
