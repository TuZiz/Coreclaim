package com.coreclaim.sync;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class ClaimSyncMessageCodecTest {

    private static final Clock FIXED_CLOCK = Clock.fixed(Instant.ofEpochMilli(1_700_000_000_000L), ZoneOffset.UTC);

    @Test
    void signedMessageRoundTrips() {
        ClaimSyncMessageCodec codec = new ClaimSyncMessageCodec("secret", "origin-a", FIXED_CLOCK);

        ClaimSyncMessageCodec.ClaimSyncMessage message = codec.decode(codec.encode(ClaimSyncEventType.CLAIM_UPDATED, 42));

        assertNotNull(message);
        assertEquals(ClaimSyncEventType.CLAIM_UPDATED, message.eventType());
        assertEquals(42, message.claimId());
        assertEquals("origin-a", message.originInstanceId());
    }

    @Test
    void tamperedMessageIsRejected() {
        ClaimSyncMessageCodec codec = new ClaimSyncMessageCodec("secret", "origin-a", FIXED_CLOCK);
        String payload = codec.encode(ClaimSyncEventType.CLAIM_UPDATED, 42).replace("|42|", "|43|");

        assertNull(codec.decode(payload));
    }

    @Test
    void replayedNonceIsRejected() {
        ClaimSyncMessageCodec codec = new ClaimSyncMessageCodec("secret", "origin-a", FIXED_CLOCK);
        String payload = codec.encode(ClaimSyncEventType.CLAIM_UPDATED, 42);

        assertNotNull(codec.decode(payload));
        assertNull(codec.decode(payload));
    }

    @Test
    void expiredMessageIsRejected() {
        ClaimSyncMessageCodec encoder = new ClaimSyncMessageCodec("secret", "origin-a", FIXED_CLOCK);
        ClaimSyncMessageCodec decoder = new ClaimSyncMessageCodec(
            "secret",
            "origin-b",
            Clock.fixed(FIXED_CLOCK.instant().plusMillis(121_000L), ZoneOffset.UTC)
        );

        assertNull(decoder.decode(encoder.encode(ClaimSyncEventType.CLAIM_UPDATED, 42)));
    }
}
