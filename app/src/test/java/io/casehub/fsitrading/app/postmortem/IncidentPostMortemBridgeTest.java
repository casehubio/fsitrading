package io.casehub.fsitrading.app.postmortem;

import io.casehub.api.spi.CaseOutcomeEvent;
import io.casehub.blocks.channel.ChannelMessageMeta;
import io.casehub.qhorus.api.message.MessageDispatch;
import io.casehub.qhorus.api.message.MessageDispatcher;
import io.casehub.work.api.WorkItemLifecycleEvent;
import io.casehub.work.api.WorkItemStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class IncidentPostMortemBridgeTest {

    private MessageDispatcher messageDispatcher;
    private IncidentPostMortemBridge bridge;

    @BeforeEach
    void setUp() {
        messageDispatcher = mock(MessageDispatcher.class);
        bridge = new IncidentPostMortemBridge();
        bridge.messageDispatcher = messageDispatcher;
        bridge.setChannelId(UUID.randomUUID());
    }

    @Test
    void completedWorkItemProducesCommitMessage() {
        when(messageDispatcher.dispatch(any())).thenReturn(null);
        WorkItemLifecycleEvent event = mockWorkItemEvent(
            WorkItemStatus.COMPLETED, "agent-risk", "Positions reduced", "overnight-incident");

        bridge.onWorkItemEvent(event);

        ArgumentCaptor<MessageDispatch> captor = ArgumentCaptor.forClass(MessageDispatch.class);
        verify(messageDispatcher).dispatch(captor.capture());
        Map<String, String> meta = ChannelMessageMeta.parseMeta("PMETA:", captor.getValue().content());
        assertThat(meta.get("entryType")).isEqualTo("COMMIT");
        assertThat(meta.get("role")).isEqualTo("agent-risk");
    }

    @Test
    void pendingWorkItemProducesProposeMessage() {
        when(messageDispatcher.dispatch(any())).thenReturn(null);
        WorkItemLifecycleEvent event = mockWorkItemEvent(
            WorkItemStatus.PENDING, "bridge", "Evaluate risk exposure", "overnight-incident");

        bridge.onWorkItemEvent(event);

        ArgumentCaptor<MessageDispatch> captor = ArgumentCaptor.forClass(MessageDispatch.class);
        verify(messageDispatcher).dispatch(captor.capture());
        Map<String, String> meta = ChannelMessageMeta.parseMeta("PMETA:", captor.getValue().content());
        assertThat(meta.get("entryType")).isEqualTo("PROPOSE");
    }

    @Test
    void faultedWorkItemProducesAssertMessage() {
        when(messageDispatcher.dispatch(any())).thenReturn(null);
        WorkItemLifecycleEvent event = mockWorkItemEvent(
            WorkItemStatus.FAULTED, "agent-hedge", "Insufficient liquidity", "overnight-incident");

        bridge.onWorkItemEvent(event);

        ArgumentCaptor<MessageDispatch> captor = ArgumentCaptor.forClass(MessageDispatch.class);
        verify(messageDispatcher).dispatch(captor.capture());
        Map<String, String> meta = ChannelMessageMeta.parseMeta("PMETA:", captor.getValue().content());
        assertThat(meta.get("entryType")).isEqualTo("ASSERT");
    }

    @Test
    void ignoresNonOvernightIncidentEvents() {
        WorkItemLifecycleEvent event = mockWorkItemEvent(
            WorkItemStatus.COMPLETED, "agent", "Done", "other-case-type");

        bridge.onWorkItemEvent(event);

        verify(messageDispatcher, never()).dispatch(any());
    }

    @Test
    void caseOutcomeProducesDoneMessage() {
        when(messageDispatcher.dispatch(any())).thenReturn(null);
        CaseOutcomeEvent event = new CaseOutcomeEvent(
            "overnight-incident", "tenant-1", UUID.randomUUID(),
            Map.of(), "COMPLETED", Instant.now(), Map.of());

        bridge.onCaseOutcome(event);

        ArgumentCaptor<MessageDispatch> captor = ArgumentCaptor.forClass(MessageDispatch.class);
        verify(messageDispatcher).dispatch(captor.capture());
        Map<String, String> meta = ChannelMessageMeta.parseMeta("PMETA:", captor.getValue().content());
        assertThat(meta.get("entryType")).isEqualTo("DONE");
        assertThat(captor.getValue().topic()).isEqualTo("OUTCOME");
    }

    @Test
    void caseOutcomeIgnoresNonOvernightIncident() {
        CaseOutcomeEvent event = new CaseOutcomeEvent(
            "other-type", "tenant-1", UUID.randomUUID(),
            Map.of(), "COMPLETED", Instant.now(), Map.of());

        bridge.onCaseOutcome(event);

        verify(messageDispatcher, never()).dispatch(any());
    }

    @Test
    void mapStatusToEntryType() {
        assertThat(IncidentPostMortemBridge.mapStatusToEntryType(WorkItemStatus.PENDING)).isEqualTo("PROPOSE");
        assertThat(IncidentPostMortemBridge.mapStatusToEntryType(WorkItemStatus.ASSIGNED)).isEqualTo("PROPOSE");
        assertThat(IncidentPostMortemBridge.mapStatusToEntryType(WorkItemStatus.COMPLETED)).isEqualTo("COMMIT");
        assertThat(IncidentPostMortemBridge.mapStatusToEntryType(WorkItemStatus.FAULTED)).isEqualTo("ASSERT");
        assertThat(IncidentPostMortemBridge.mapStatusToEntryType(WorkItemStatus.REJECTED)).isEqualTo("ASSERT");
        assertThat(IncidentPostMortemBridge.mapStatusToEntryType(WorkItemStatus.IN_PROGRESS)).isEqualTo("PROPOSE");
    }

    @Test
    void deriveMilestoneTopic() {
        assertThat(IncidentPostMortemBridge.deriveMilestoneTopic("incident.detection.evaluate"))
            .isEqualTo("incident.detection");
        assertThat(IncidentPostMortemBridge.deriveMilestoneTopic("respond"))
            .isEqualTo("respond");
        assertThat(IncidentPostMortemBridge.deriveMilestoneTopic(null))
            .isEqualTo("general");
        assertThat(IncidentPostMortemBridge.deriveMilestoneTopic(""))
            .isEqualTo("general");
    }

    private WorkItemLifecycleEvent mockWorkItemEvent(WorkItemStatus status, String actor,
                                                      String detail, String subject) {
        WorkItemLifecycleEvent event = mock(WorkItemLifecycleEvent.class);
        when(event.status()).thenReturn(status);
        when(event.actor()).thenReturn(actor);
        when(event.detail()).thenReturn(detail);
        when(event.subject()).thenReturn(subject);
        when(event.workItemId()).thenReturn(UUID.randomUUID());
        when(event.planRef()).thenReturn("incident.response.step1");
        when(event.tenancyId()).thenReturn("tenant-1");
        when(event.outcome()).thenReturn(null);
        return event;
    }
}
