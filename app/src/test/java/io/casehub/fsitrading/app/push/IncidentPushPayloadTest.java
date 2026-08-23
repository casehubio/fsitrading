package io.casehub.fsitrading.app.push;

import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class IncidentPushPayloadTest {

    @Test
    void incidentCreated_typeDiscriminator() {
        var now = Instant.now();
        var p = new IncidentPushPayload.IncidentCreated(
                UUID.randomUUID(), "CRITICAL", "FLASH_CRASH",
                List.of("AAPL"), "Flash crash detected",
                now.plusSeconds(120), now.plusSeconds(300), now);
        assertEquals("INCIDENT_CREATED", p.type());
        assertInstanceOf(IncidentPushPayload.class, p);
    }

    @Test
    void incidentCreated_carriesDeadlines() {
        var now = Instant.now();
        var claim = now.plusSeconds(120);
        var completion = now.plusSeconds(300);
        var p = new IncidentPushPayload.IncidentCreated(
                UUID.randomUUID(), "CRITICAL", "FLASH_CRASH",
                List.of("AAPL"), "desc", claim, completion, now);
        assertEquals(claim, p.claimDeadline());
        assertEquals(completion, p.completionDeadline());
        assertEquals(now, p.createdAt());
    }

    @Test
    void slaBreached_typeDiscriminator() {
        var p = new IncidentPushPayload.SlaBreached(
                UUID.randomUUID(), UUID.randomUUID(),
                "CLAIM_EXPIRED", 1, "CRITICAL");
        assertEquals("SLA_BREACHED", p.type());
    }

    @Test
    void incidentResolved_typeDiscriminator() {
        var p = new IncidentPushPayload.IncidentResolved(
                UUID.randomUUID(), Instant.now());
        assertEquals("INCIDENT_RESOLVED", p.type());
    }
}
