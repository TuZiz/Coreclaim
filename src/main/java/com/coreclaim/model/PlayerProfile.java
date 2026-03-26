package com.coreclaim.model;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.UUID;
import java.util.Set;

public final class PlayerProfile {

    private final UUID uuid;
    private String lastKnownName;
    private int activityPoints;
    private int onlineMinutes;
    private boolean starterCoreGranted;
    private final Set<UUID> globalTrustedMembers = new LinkedHashSet<>();

    public PlayerProfile(UUID uuid, String lastKnownName, int activityPoints, int onlineMinutes, boolean starterCoreGranted) {
        this.uuid = uuid;
        this.lastKnownName = lastKnownName;
        this.activityPoints = Math.max(0, activityPoints);
        this.onlineMinutes = Math.max(0, onlineMinutes);
        this.starterCoreGranted = starterCoreGranted;
    }

    public UUID uuid() {
        return uuid;
    }

    public synchronized String lastKnownName() {
        return lastKnownName;
    }

    public synchronized void setLastKnownName(String lastKnownName) {
        if (lastKnownName != null && !lastKnownName.isBlank()) {
            this.lastKnownName = lastKnownName;
        }
    }

    public synchronized int activityPoints() {
        return activityPoints;
    }

    public synchronized void setActivityPoints(int activityPoints) {
        this.activityPoints = Math.max(0, activityPoints);
    }

    public synchronized int onlineMinutes() {
        return onlineMinutes;
    }

    public synchronized void addOnlineMinutes(int minutes) {
        this.onlineMinutes = Math.max(0, this.onlineMinutes + minutes);
    }

    public synchronized boolean starterCoreGranted() {
        return starterCoreGranted;
    }

    public synchronized void setStarterCoreGranted(boolean starterCoreGranted) {
        this.starterCoreGranted = starterCoreGranted;
    }

    public synchronized boolean addGlobalTrustedMember(UUID memberId) {
        return globalTrustedMembers.add(memberId);
    }

    public synchronized boolean removeGlobalTrustedMember(UUID memberId) {
        return globalTrustedMembers.remove(memberId);
    }

    public synchronized boolean isGloballyTrusted(UUID memberId) {
        return globalTrustedMembers.contains(memberId);
    }

    public synchronized Set<UUID> globalTrustedMembers() {
        return Collections.unmodifiableSet(new LinkedHashSet<>(globalTrustedMembers));
    }
}
