package com.coreclaim.profile;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.UUID;
import java.util.Set;

public final class PlayerProfile {

    private final UUID uuid;
    private String lastKnownName;
    private int activityPoints;
    private long onlineSeconds;
    private boolean starterCoreGranted;
    private boolean starterCoreReclaimed;
    private boolean starterCoreUsed;
    private boolean autoShowBorders;
    private long lastSeenAt;
    private String lastGroupKey;
    private boolean cleanupPermissionExempt;
    private final Set<UUID> globalTrustedMembers = new LinkedHashSet<>();

    public PlayerProfile(
        UUID uuid,
        String lastKnownName,
        int activityPoints,
        int onlineMinutes,
        long onlineSeconds,
        boolean starterCoreGranted,
        boolean starterCoreReclaimed,
        boolean starterCoreUsed,
        boolean autoShowBorders,
        long lastSeenAt,
        String lastGroupKey,
        boolean cleanupPermissionExempt
    ) {
        this.uuid = uuid;
        this.lastKnownName = lastKnownName;
        this.activityPoints = Math.max(0, activityPoints);
        this.onlineSeconds = Math.max(0L, onlineSeconds > 0L ? onlineSeconds : onlineMinutes * 60L);
        this.starterCoreGranted = starterCoreGranted;
        this.starterCoreReclaimed = starterCoreReclaimed;
        this.starterCoreUsed = starterCoreUsed;
        this.autoShowBorders = autoShowBorders;
        this.lastSeenAt = Math.max(0L, lastSeenAt);
        this.lastGroupKey = lastGroupKey == null ? "" : lastGroupKey;
        this.cleanupPermissionExempt = cleanupPermissionExempt;
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
        return (int) Math.max(0L, onlineSeconds / 60L);
    }

    public synchronized void addOnlineMinutes(int minutes) {
        addOnlineSeconds(minutes * 60L);
    }

    public synchronized long onlineSeconds() {
        return onlineSeconds;
    }

    public synchronized void addOnlineSeconds(long seconds) {
        if (seconds <= 0L) {
            return;
        }
        this.onlineSeconds = Math.max(0L, this.onlineSeconds + seconds);
    }

    public synchronized boolean starterCoreGranted() {
        return starterCoreGranted;
    }

    public synchronized void setStarterCoreGranted(boolean starterCoreGranted) {
        this.starterCoreGranted = starterCoreGranted;
    }

    public synchronized boolean starterCoreReclaimed() {
        return starterCoreReclaimed;
    }

    public synchronized void setStarterCoreReclaimed(boolean starterCoreReclaimed) {
        this.starterCoreReclaimed = starterCoreReclaimed;
    }

    public synchronized boolean starterCoreUsed() {
        return starterCoreUsed;
    }

    public synchronized void setStarterCoreUsed(boolean starterCoreUsed) {
        this.starterCoreUsed = starterCoreUsed;
    }

    public synchronized boolean autoShowBorders() {
        return autoShowBorders;
    }

    public synchronized void setAutoShowBorders(boolean autoShowBorders) {
        this.autoShowBorders = autoShowBorders;
    }

    public synchronized long lastSeenAt() {
        return lastSeenAt;
    }

    public synchronized void setLastSeenAt(long lastSeenAt) {
        this.lastSeenAt = Math.max(this.lastSeenAt, Math.max(0L, lastSeenAt));
    }

    public synchronized String lastGroupKey() {
        return lastGroupKey;
    }

    public synchronized void setLastGroupKey(String lastGroupKey) {
        this.lastGroupKey = lastGroupKey == null ? "" : lastGroupKey.trim();
    }

    public synchronized boolean cleanupPermissionExempt() {
        return cleanupPermissionExempt;
    }

    public synchronized void setCleanupPermissionExempt(boolean cleanupPermissionExempt) {
        this.cleanupPermissionExempt = cleanupPermissionExempt;
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
