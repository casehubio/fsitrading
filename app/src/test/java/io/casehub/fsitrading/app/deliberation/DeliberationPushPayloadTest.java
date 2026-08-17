package io.casehub.fsitrading.app.deliberation;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class DeliberationPushPayloadTest {

    @Test
    void started_hasCorrectTypeDiscriminator() {
        var payload = new DeliberationPushPayload.Started(
                UUID.randomUUID(), UUID.randomUUID(), "AAPL",
                "REGIME_CHANGED", Instant.now());
        assertEquals("DELIBERATION_STARTED", payload.type());
    }

    @Test
    void completed_hasCorrectTypeDiscriminator() {
        var payload = new DeliberationPushPayload.Completed(
                UUID.randomUUID(), UUID.randomUUID(), "AAPL",
                "CONSENSUS", 0.95, 5, 1, 2, 3,
                UUID.randomUUID(), UUID.randomUUID(), "EXECUTE", Instant.now());
        assertEquals("DELIBERATION_COMPLETED", payload.type());
    }

    @Test
    void failed_hasCorrectTypeDiscriminator() {
        var payload = new DeliberationPushPayload.Failed(
                UUID.randomUUID(), UUID.randomUUID(), "AAPL",
                "Wall-clock timeout", Instant.now());
        assertEquals("DELIBERATION_FAILED", payload.type());
    }

    @Test
    void convergenceUpdate_hasCorrectTypeDiscriminator() {
        var payload = new DeliberationPushPayload.ConvergenceUpdate(
                UUID.randomUUID(), "PROGRESSING", 0.3, 2, 1, 4, 5);
        assertEquals("CONVERGENCE_UPDATE", payload.type());
    }

    @Test
    void allPayloadsAreSealed() {
        assertTrue(DeliberationPushPayload.class.isSealed());
        var permitted = DeliberationPushPayload.class.getPermittedSubclasses();
        assertEquals(4, permitted.length);
    }
}
