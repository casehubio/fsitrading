package io.casehub.fsitrading.app.deliberation.handler;

import io.casehub.blocks.channel.ChannelAgentRequest;
import io.casehub.blocks.channel.ChannelMessageMeta;
import io.casehub.fsitrading.app.pipeline.FsiObservationCache;
import io.casehub.qhorus.api.gateway.OutboundMessage;
import io.casehub.qhorus.api.message.MessageType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class SubTaskHandlerTest {

    private static final UUID CHANNEL_ID = UUID.randomUUID();
    private static final String SENDER_ID = "sub-task-agent";

    private FsiObservationCache cache;
    private CorrelationCheckHandler correlationHandler;
    private VolumeAnalysisHandler volumeHandler;
    private NewsCheckHandler newsHandler;

    @BeforeEach
    void setUp() {
        cache = new FsiObservationCache();
        correlationHandler = new CorrelationCheckHandler(cache);
        volumeHandler = new VolumeAnalysisHandler(cache);
        newsHandler = new NewsCheckHandler(cache);
    }

    @Test
    void correlationHandlerMatchesCorrelationCheck() {
        assertTrue(correlationHandler.handles(makeRequest("CORRELATION_CHECK")));
    }

    @Test
    void correlationHandlerRejectsOtherTaskTypes() {
        assertFalse(correlationHandler.handles(makeRequest("VOLUME_ANALYSIS")));
        assertFalse(correlationHandler.handles(makeRequest("NEWS_CHECK")));
    }

    @Test
    void correlationHandlerPreparesTask() {
        var request = makeRequestWithInstruments("CORRELATION_CHECK", "AAPL", "MSFT");
        var task = correlationHandler.prepareTask(request);

        assertNotNull(task.systemPrompt());
        assertTrue(task.assembledInput().contains("AAPL"));
        assertTrue(task.assembledInput().contains("MSFT"));
    }

    @Test
    void correlationHandlerBuildsSubTaskFinding() {
        var request = makeRequest("CORRELATION_CHECK");
        var response = correlationHandler.buildResponse(CHANNEL_ID, SENDER_ID,
                "Correlation: 0.85, strong positive", request);

        assertTrue(response.content().startsWith("##FSI##"));
        var meta = ChannelMessageMeta.parseMeta("##FSI##", response.content());
        assertEquals("SUB_TASK_FINDING", meta.get("entryType"));
        assertEquals("CORRELATION_CHECK", meta.get("taskType"));
    }

    @Test
    void volumeHandlerMatchesVolumeAnalysis() {
        assertTrue(volumeHandler.handles(makeRequest("VOLUME_ANALYSIS")));
    }

    @Test
    void volumeHandlerRejectsOtherTaskTypes() {
        assertFalse(volumeHandler.handles(makeRequest("CORRELATION_CHECK")));
    }

    @Test
    void volumeHandlerPreparesTask() {
        var request = makeRequest("VOLUME_ANALYSIS");
        var task = volumeHandler.prepareTask(request);

        assertNotNull(task.systemPrompt());
        assertTrue(task.assembledInput().contains("Volume analysis"));
    }

    @Test
    void volumeHandlerBuildsSubTaskFinding() {
        var request = makeRequest("VOLUME_ANALYSIS");
        var response = volumeHandler.buildResponse(CHANNEL_ID, SENDER_ID,
                "Volume: HIGH, 2.5x average", request);

        var meta = ChannelMessageMeta.parseMeta("##FSI##", response.content());
        assertEquals("SUB_TASK_FINDING", meta.get("entryType"));
        assertEquals("VOLUME_ANALYSIS", meta.get("taskType"));
    }

    @Test
    void newsHandlerMatchesNewsCheck() {
        assertTrue(newsHandler.handles(makeRequest("NEWS_CHECK")));
    }

    @Test
    void newsHandlerRejectsOtherTaskTypes() {
        assertFalse(newsHandler.handles(makeRequest("CORRELATION_CHECK")));
    }

    @Test
    void newsHandlerPreparesTask() {
        var request = makeRequest("NEWS_CHECK");
        var task = newsHandler.prepareTask(request);

        assertNotNull(task.systemPrompt());
        assertTrue(task.assembledInput().contains("News check"));
    }

    @Test
    void newsHandlerBuildsSubTaskFinding() {
        var request = makeRequest("NEWS_CHECK");
        var response = newsHandler.buildResponse(CHANNEL_ID, SENDER_ID,
                "Sentiment: BULLISH, key theme: earnings beat", request);

        var meta = ChannelMessageMeta.parseMeta("##FSI##", response.content());
        assertEquals("SUB_TASK_FINDING", meta.get("entryType"));
        assertEquals("NEWS_CHECK", meta.get("taskType"));
    }

    private ChannelAgentRequest makeRequest(String taskType) {
        var content = ChannelMessageMeta.encode("##FSI##",
                Map.of("entryType", "SUB_TASK_REQUEST", "taskType", taskType,
                        "instrument", "AAPL", "role", "ORCHESTRATOR"),
                "Analyse AAPL");
        var message = new OutboundMessage(UUID.randomUUID(), 1L, "orchestrator",
                MessageType.COMMAND, content, null, null, null, List.of(), null, "debate");
        return new ChannelAgentRequest(CHANNEL_ID, UUID.randomUUID().toString(), message, "debate");
    }

    private ChannelAgentRequest makeRequestWithInstruments(String taskType,
                                                           String instrument1, String instrument2) {
        var content = ChannelMessageMeta.encode("##FSI##",
                Map.of("entryType", "SUB_TASK_REQUEST", "taskType", taskType,
                        "instrument1", instrument1, "instrument2", instrument2, "role", "ORCHESTRATOR"),
                "Check correlation");
        var message = new OutboundMessage(UUID.randomUUID(), 1L, "orchestrator",
                MessageType.COMMAND, content, null, null, null, List.of(), null, "debate");
        return new ChannelAgentRequest(CHANNEL_ID, UUID.randomUUID().toString(), message, "debate");
    }
}
