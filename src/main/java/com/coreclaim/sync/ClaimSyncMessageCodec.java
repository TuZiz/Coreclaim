package com.coreclaim.sync;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

final class ClaimSyncMessageCodec {

    private static final String PROTOCOL_VERSION = "v2";
    private static final long MAX_CLOCK_SKEW_MILLIS = 120_000L;
    private static final String HMAC_ALGORITHM = "HmacSHA256";

    private final String secret;
    private final String originInstanceId;
    private final Clock clock;
    private final Map<String, Long> seenNonces = new ConcurrentHashMap<>();

    ClaimSyncMessageCodec(String secret, String originInstanceId) {
        this(secret, originInstanceId, Clock.systemUTC());
    }

    ClaimSyncMessageCodec(String secret, String originInstanceId, Clock clock) {
        this.secret = secret == null ? "" : secret;
        this.originInstanceId = originInstanceId;
        this.clock = clock;
    }

    String encode(ClaimSyncEventType eventType, int claimId) {
        long timestamp = clock.millis();
        String nonce = UUID.randomUUID().toString();
        String unsignedPayload = unsignedPayload(eventType.wireName(), claimId, originInstanceId, timestamp, nonce);
        return unsignedPayload + "|" + sign(unsignedPayload);
    }

    ClaimSyncMessage decode(String payload) {
        if (payload == null || payload.isBlank()) {
            return null;
        }
        String[] parts = payload.split("\\|", -1);
        if (parts.length != 7 || !PROTOCOL_VERSION.equals(parts[0])) {
            return null;
        }
        ClaimSyncEventType eventType = ClaimSyncEventType.fromWireName(parts[1]);
        if (eventType == null) {
            return null;
        }
        long timestamp;
        int claimId;
        try {
            claimId = Integer.parseInt(parts[2]);
            timestamp = Long.parseLong(parts[4]);
        } catch (NumberFormatException exception) {
            return null;
        }
        if (Math.abs(clock.millis() - timestamp) > MAX_CLOCK_SKEW_MILLIS) {
            return null;
        }
        String nonce = parts[5];
        if (nonce.isBlank()) {
            return null;
        }
        pruneSeenNonces();
        if (seenNonces.putIfAbsent(nonce, timestamp) != null) {
            return null;
        }
        String unsignedPayload = unsignedPayload(parts[1], claimId, parts[3], timestamp, nonce);
        if (!constantTimeEquals(sign(unsignedPayload), parts[6])) {
            seenNonces.remove(nonce);
            return null;
        }
        return new ClaimSyncMessage(eventType, claimId, parts[3]);
    }

    private String unsignedPayload(String eventName, int claimId, String origin, long timestamp, String nonce) {
        return PROTOCOL_VERSION
            + "|" + eventName
            + "|" + claimId
            + "|" + origin
            + "|" + timestamp
            + "|" + nonce;
    }

    private String sign(String payload) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to sign claim sync message.", exception);
        }
    }

    private boolean constantTimeEquals(String left, String right) {
        if (left == null || right == null) {
            return false;
        }
        byte[] leftBytes = left.getBytes(StandardCharsets.UTF_8);
        byte[] rightBytes = right.getBytes(StandardCharsets.UTF_8);
        int diff = leftBytes.length ^ rightBytes.length;
        for (int index = 0; index < Math.min(leftBytes.length, rightBytes.length); index++) {
            diff |= leftBytes[index] ^ rightBytes[index];
        }
        return diff == 0;
    }

    private void pruneSeenNonces() {
        long cutoff = clock.millis() - MAX_CLOCK_SKEW_MILLIS;
        seenNonces.entrySet().removeIf(entry -> entry.getValue() < cutoff);
    }

    record ClaimSyncMessage(ClaimSyncEventType eventType, int claimId, String originInstanceId) {
    }
}
