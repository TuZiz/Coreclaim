package com.coreclaim.model;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

final class ClaimMembers {

    private final Set<UUID> trustedMembers = new LinkedHashSet<>();
    private final Set<UUID> deniedMembers = new LinkedHashSet<>();
    private final Map<UUID, ClaimMemberSettings> memberSettings = new LinkedHashMap<>();

    boolean isTrusted(UUID playerId) {
        return trustedMembers.contains(playerId);
    }

    boolean canAccess(UUID owner, UUID playerId) {
        return owner.equals(playerId) || (trustedMembers.contains(playerId) && !deniedMembers.contains(playerId));
    }

    boolean addTrustedMember(UUID owner, UUID playerId) {
        if (owner.equals(playerId)) {
            return false;
        }
        return trustedMembers.add(playerId);
    }

    boolean removeTrustedMember(UUID playerId) {
        return trustedMembers.remove(playerId);
    }

    Set<UUID> trustedMembers() {
        return Collections.unmodifiableSet(new LinkedHashSet<>(trustedMembers));
    }

    void clearTrustedMembers() {
        trustedMembers.clear();
    }

    int trustedCount() {
        return trustedMembers.size();
    }

    boolean addDeniedMember(UUID owner, UUID playerId) {
        if (owner.equals(playerId)) {
            return false;
        }
        return deniedMembers.add(playerId);
    }

    boolean removeDeniedMember(UUID playerId) {
        return deniedMembers.remove(playerId);
    }

    boolean isDenied(UUID playerId) {
        return deniedMembers.contains(playerId);
    }

    Set<UUID> deniedMembers() {
        return Collections.unmodifiableSet(new LinkedHashSet<>(deniedMembers));
    }

    void clearDeniedMembers() {
        deniedMembers.clear();
    }

    ClaimMemberSettings memberSettings(UUID playerId) {
        return memberSettings.get(playerId);
    }

    void setMemberSettings(UUID playerId, ClaimMemberSettings settings) {
        if (playerId == null || settings == null) {
            return;
        }
        memberSettings.put(playerId, settings);
    }

    void removeMemberSettings(UUID playerId) {
        memberSettings.remove(playerId);
    }

    Map<UUID, ClaimMemberSettings> memberSettings() {
        return Collections.unmodifiableMap(new LinkedHashMap<>(memberSettings));
    }

    void clearMemberSettings() {
        memberSettings.clear();
    }

    boolean memberPermission(UUID playerId, ClaimPermission permission, boolean fallback) {
        ClaimMemberSettings settings = memberSettings.get(playerId);
        return settings == null ? fallback : settings.permission(permission);
    }
}
