package io.casehub.fsitrading.app.push;

import io.casehub.fsitrading.app.pipeline.FsiMarketPushService;
import io.casehub.fsitrading.model.GateOpenedEvent;
import io.casehub.pages.push.EventBroadcaster;
import io.casehub.work.api.WorkItem;
import io.casehub.work.api.WorkItemLifecycleEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;

@ApplicationScoped
public class FsiWorkItemPushListener {

    private final FsiMarketPushService.PushBroadcaster broadcaster;

    @Inject
    public FsiWorkItemPushListener(EventBroadcaster eventBroadcaster) {
        this.broadcaster = eventBroadcaster::broadcast;
    }

    FsiWorkItemPushListener(FsiMarketPushService.PushBroadcaster broadcaster) {
        this.broadcaster = broadcaster;
    }

    void onWorkItemLifecycle(@Observes WorkItemLifecycleEvent event) {
        WorkItem wi = event.workItem();
        if (wi == null || !"fsitrading".equals(wi.scope())) return;

        WorkItemPushPayload payload = switch (event.eventType()) {
            case CREATED -> new WorkItemPushPayload.WorkItemCreated(
                    event.workItemId(), wi.title(),
                    wi.types() != null && !wi.types().isEmpty() ? wi.types().iterator().next() : null,
                    wi.candidateGroups(),
                    wi.status() != null ? wi.status().name() : null,
                    wi.claimDeadline(), wi.expiresAt(), wi.createdAt());
            case ASSIGNED -> new WorkItemPushPayload.WorkItemAssigned(
                    event.workItemId(), wi.assigneeId(), wi.assignedAt());
            case ESCALATED -> new WorkItemPushPayload.WorkItemEscalated(
                    event.workItemId(), wi.candidateGroups(),
                    wi.claimDeadline(), wi.expiresAt());
            case COMPLETED, REJECTED -> new WorkItemPushPayload.WorkItemCompleted(
                    event.workItemId(), wi.outcome(), wi.resolution(), wi.completedAt());
            default -> null;
        };
        if (payload != null) {
            broadcaster.broadcast("work-items/" + event.workItemId(), payload);
            broadcaster.broadcast("work-items/summary", payload);
        }
    }

    void onGateOpened(@Observes GateOpenedEvent event) {
        var payload = new WorkItemPushPayload.GateOpened(
                event.caseId(), event.actionDescription(),
                event.riskLevel(), event.candidateGroups());
        broadcaster.broadcast("work-items/" + event.caseId(), payload);
        broadcaster.broadcast("work-items/summary", payload);
    }
}
