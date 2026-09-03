package io.casehub.fsitrading.app.postmortem;

import io.casehub.api.spi.CaseOutcomeEvent;
import io.casehub.blocks.channel.ChannelMessageMeta;
import io.casehub.platform.api.identity.ActorType;
import io.casehub.qhorus.api.message.MessageDispatch;
import io.casehub.qhorus.api.message.MessageDispatcher;
import io.casehub.qhorus.api.message.MessageType;
import io.casehub.work.api.WorkItemLifecycleEvent;
import io.casehub.work.api.WorkItemStatus;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.ObservesAsync;
import jakarta.inject.Inject;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@ApplicationScoped
public class IncidentPostMortemBridge {

    static final String SENTINEL = "PMETA:";
    private static final String CASE_TYPE = "overnight-incident";

    @Inject MessageDispatcher messageDispatcher;

    private volatile UUID channelId;

    public void setChannelId(UUID channelId) {
        this.channelId = channelId;
    }

    public void onWorkItemEvent(@ObservesAsync WorkItemLifecycleEvent event) {
        if (channelId == null) return;
        if (!CASE_TYPE.equals(event.subject())) return;

        String entryType = mapStatusToEntryType(event.status());
        String body = event.actor() + " " + entryType.toLowerCase() + ": "
            + (event.outcome() != null ? event.outcome() : event.detail());

        Map<String, String> meta = new LinkedHashMap<>();
        meta.put("entryType", entryType);
        meta.put("role", event.actor());
        meta.put("round", "1");
        meta.put("scope", "incident-response");

        String content = ChannelMessageMeta.encode(SENTINEL, meta, body);
        String topic = deriveMilestoneTopic(event.planRef());
        String correlationId = topic + "-" + event.workItemId();

        messageDispatcher.dispatch(MessageDispatch.builder()
            .channelId(channelId)
            .sender("postmortem-bridge")
            .type(MessageType.STATUS)
            .content(content)
            .correlationId(correlationId)
            .actorType(ActorType.SYSTEM)
            .topic(topic)
            .tenancyId(event.tenancyId())
            .build());
    }

    public void onCaseOutcome(@ObservesAsync CaseOutcomeEvent event) {
        if (channelId == null) return;
        if (!CASE_TYPE.equals(event.caseType())) return;

        Map<String, String> meta = new LinkedHashMap<>();
        meta.put("entryType", "DONE");
        meta.put("role", "bridge");
        meta.put("round", "1");
        meta.put("scope", "incident-response");

        String body = "Case " + event.outcomeLabel() + ": incident closed";
        String content = ChannelMessageMeta.encode(SENTINEL, meta, body);

        messageDispatcher.dispatch(MessageDispatch.builder()
            .channelId(channelId)
            .sender("postmortem-bridge")
            .type(MessageType.STATUS)
            .content(content)
            .correlationId("outcome-" + event.caseId())
            .actorType(ActorType.SYSTEM)
            .topic("OUTCOME")
            .tenancyId(event.tenancyId())
            .build());
    }

    static String mapStatusToEntryType(WorkItemStatus status) {
        return switch (status) {
            case PENDING, ASSIGNED -> "PROPOSE";
            case COMPLETED -> "COMMIT";
            case FAULTED, REJECTED, CANCELLED -> "ASSERT";
            default -> "PROPOSE";
        };
    }

    static String deriveMilestoneTopic(String planRef) {
        if (planRef == null || planRef.isBlank()) return "general";
        int lastDot = planRef.lastIndexOf('.');
        return lastDot > 0 ? planRef.substring(0, lastDot) : planRef;
    }
}
