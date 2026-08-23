package io.casehub.fsitrading.app.push;

import io.casehub.fsitrading.model.GateOpenedEvent;
import io.casehub.work.api.WorkItem;
import io.casehub.work.api.WorkItemLifecycleEvent;
import io.casehub.work.api.WorkItemStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class FsiWorkItemPushListenerTest {

    private final List<BroadcastCapture> broadcasts = new ArrayList<>();
    private FsiWorkItemPushListener listener;

    @BeforeEach
    void setUp() {
        listener = new FsiWorkItemPushListener(
                (topic, event) -> broadcasts.add(new BroadcastCapture(topic, event)));
    }

    @Test
    void gateOpened_broadcastsToSummaryAndCaseTopics() {
        var caseId = UUID.randomUUID();
        var event = new GateOpenedEvent(
                caseId, "Close > 25% portfolio", "HIGH", "fsi-oncall");

        listener.onGateOpened(event);

        assertEquals(2, broadcasts.size());
        assertEquals("work-item:" + caseId, broadcasts.get(0).topic);
        assertEquals("work-item:summary", broadcasts.get(1).topic);
        var payload = (WorkItemPushPayload.GateOpened) broadcasts.get(0).event;
        assertEquals("GATE_OPENED", payload.type());
        assertEquals(caseId, payload.caseId());
    }

    @Test
    void created_broadcastsToItemAndSummaryTopics() {
        var itemId = UUID.randomUUID();
        var now = Instant.now();
        var workItem = fsiWorkItem(itemId, WorkItemStatus.PENDING,
                "Approve trade", "incident-review", "fsi-oncall", now);
        var event = WorkItemLifecycleEvent.of("created", workItem, "system", "Work item created");

        listener.onWorkItemLifecycle(event);

        assertEquals(2, broadcasts.size());
        assertEquals("work-item:" + itemId, broadcasts.get(0).topic);
        assertEquals("work-item:summary", broadcasts.get(1).topic);
        var payload = (WorkItemPushPayload.WorkItemCreated) broadcasts.get(0).event;
        assertEquals("WORK_ITEM_CREATED", payload.type());
        assertEquals(itemId, payload.itemId());
    }

    @Test
    void assigned_broadcastsWorkItemAssigned() {
        var itemId = UUID.randomUUID();
        var now = Instant.now();
        var workItem = WorkItem.builder()
                .id(itemId).scope("fsitrading").status(WorkItemStatus.ASSIGNED)
                .title("Review").candidateGroups("fsi-oncall")
                .assigneeId("trader-1").assignedAt(now)
                .createdAt(now).build();
        var event = WorkItemLifecycleEvent.of("assigned", workItem, "system", "Assigned");

        listener.onWorkItemLifecycle(event);

        assertEquals(2, broadcasts.size());
        var payload = (WorkItemPushPayload.WorkItemAssigned) broadcasts.get(0).event;
        assertEquals("WORK_ITEM_ASSIGNED", payload.type());
        assertEquals("trader-1", payload.assignedTo());
    }

    @Test
    void completed_broadcastsWorkItemCompleted() {
        var itemId = UUID.randomUUID();
        var now = Instant.now();
        var workItem = WorkItem.builder()
                .id(itemId).scope("fsitrading").status(WorkItemStatus.COMPLETED)
                .title("Review").candidateGroups("fsi-oncall")
                .outcome("APPROVED").resolution("Risk accepted")
                .completedAt(now).createdAt(now).build();
        var event = WorkItemLifecycleEvent.of("completed", workItem, "system", "Done");

        listener.onWorkItemLifecycle(event);

        assertEquals(2, broadcasts.size());
        var payload = (WorkItemPushPayload.WorkItemCompleted) broadcasts.get(0).event;
        assertEquals("WORK_ITEM_COMPLETED", payload.type());
        assertEquals("APPROVED", payload.outcome());
    }

    @Test
    void escalated_broadcastsWithUpdatedDeadlines() {
        var itemId = UUID.randomUUID();
        var now = Instant.now();
        var newClaimDeadline = now.plusSeconds(60);
        var newExpiresAt = now.plusSeconds(180);
        var workItem = WorkItem.builder()
                .id(itemId).scope("fsitrading").status(WorkItemStatus.ASSIGNED)
                .title("Review").candidateGroups("oncall-escalation")
                .claimDeadline(newClaimDeadline).expiresAt(newExpiresAt)
                .createdAt(now).build();
        var event = WorkItemLifecycleEvent.of("escalated", workItem, "system", "SLA escalated");

        listener.onWorkItemLifecycle(event);

        assertEquals(2, broadcasts.size());
        var payload = (WorkItemPushPayload.WorkItemEscalated) broadcasts.get(0).event;
        assertEquals("WORK_ITEM_ESCALATED", payload.type());
        assertEquals("oncall-escalation", payload.candidateGroups());
        assertEquals(newClaimDeadline, payload.claimDeadline());
        assertEquals(newExpiresAt, payload.expiresAt());
    }

    @Test
    void nonFsiScope_ignored() {
        var workItem = WorkItem.builder()
                .id(UUID.randomUUID()).scope("other-app").status(WorkItemStatus.PENDING)
                .title("Other").candidateGroups("other-group")
                .createdAt(Instant.now()).build();
        var event = WorkItemLifecycleEvent.of("created", workItem, "system", "Created");

        listener.onWorkItemLifecycle(event);

        assertTrue(broadcasts.isEmpty());
    }

    @Test
    void wireEvent_nullWorkItem_ignored() {
        var event = WorkItemLifecycleEvent.fromWire(
                "io.casehub.work.workitem.created", "/workitems/1", "1",
                UUID.randomUUID(), WorkItemStatus.PENDING, Instant.now(),
                "system", "Created", null, null, null, null, null,
                null, null, "fsi-oncall", List.of());

        listener.onWorkItemLifecycle(event);

        assertTrue(broadcasts.isEmpty());
    }

    private static WorkItem fsiWorkItem(UUID id, WorkItemStatus status,
                                         String title, String itemType,
                                         String candidateGroups, Instant now) {
        return WorkItem.builder()
                .id(id).scope("fsitrading").status(status)
                .title(title).types(Set.of(itemType))
                .candidateGroups(candidateGroups)
                .claimDeadline(now.plusSeconds(120))
                .expiresAt(now.plusSeconds(300))
                .createdAt(now).build();
    }

    record BroadcastCapture(String topic, Object event) {}
}
