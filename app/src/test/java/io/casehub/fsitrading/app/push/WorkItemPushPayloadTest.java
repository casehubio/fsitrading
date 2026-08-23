package io.casehub.fsitrading.app.push;

import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class WorkItemPushPayloadTest {

    @Test
    void workItemCreated_typeDiscriminator() {
        var now = Instant.now();
        var p = new WorkItemPushPayload.WorkItemCreated(
                UUID.randomUUID(), "Approve trade", "incident-review",
                "fsi-oncall", "PENDING",
                now.plusSeconds(120), now.plusSeconds(300), now);
        assertEquals("WORK_ITEM_CREATED", p.type());
        assertInstanceOf(WorkItemPushPayload.class, p);
    }

    @Test
    void workItemAssigned_typeDiscriminator() {
        var p = new WorkItemPushPayload.WorkItemAssigned(
                UUID.randomUUID(), "trader-1", Instant.now());
        assertEquals("WORK_ITEM_ASSIGNED", p.type());
    }

    @Test
    void gateOpened_typeDiscriminator() {
        var p = new WorkItemPushPayload.GateOpened(
                UUID.randomUUID(),
                "Close > 25% portfolio", "HIGH", "fsi-oncall");
        assertEquals("GATE_OPENED", p.type());
    }

    @Test
    void workItemEscalated_typeDiscriminator() {
        var now = Instant.now();
        var p = new WorkItemPushPayload.WorkItemEscalated(
                UUID.randomUUID(), "oncall-escalation",
                now.plusSeconds(60), now.plusSeconds(180));
        assertEquals("WORK_ITEM_ESCALATED", p.type());
        assertEquals("oncall-escalation", p.candidateGroups());
    }

    @Test
    void workItemCompleted_typeDiscriminator() {
        var p = new WorkItemPushPayload.WorkItemCompleted(
                UUID.randomUUID(), "APPROVED", "Risk accepted", Instant.now());
        assertEquals("WORK_ITEM_COMPLETED", p.type());
    }
}
