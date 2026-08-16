package io.casehub.fsitrading.app.deliberation;

import io.casehub.blocks.channel.ChannelAgentRequest;
import io.casehub.blocks.channel.ChannelMessageMeta;
import io.casehub.fsitrading.FsiActorIdentity;
import io.casehub.fsitrading.model.StrategyType;
import io.casehub.qhorus.api.gateway.OutboundMessage;
import io.casehub.qhorus.api.message.MessageType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class FsiDebateHandlerTest {

    private static final UUID CHANNEL_ID = UUID.randomUUID();
    private static final String SENDER_ID = FsiActorIdentity.forStrategy(StrategyType.MOMENTUM);

    private FsiDebateHandler handler;

    @BeforeEach
    void setUp() {
        handler = new FsiDebateHandler(StrategyType.MOMENTUM, "AAPL regime changed to VOLATILE");
    }

    @Test
    void handlesAllRequests() {
        var request = makeRequest("some content");
        assertTrue(handler.handles(request));
    }

    @Test
    void prepareTaskBuildsPromptWithContext() {
        var request = makeRequest("previous debate message");
        var task = handler.prepareTask(request);

        assertNotNull(task.systemPrompt());
        assertTrue(task.systemPrompt().contains("momentum"), "should mention strategy type");
        assertNotNull(task.assembledInput());
        assertTrue(task.assembledInput().contains("AAPL regime changed to VOLATILE"),
                "should include market event context");
    }

    @ParameterizedTest
    @CsvSource({
            "RAISE, COMMAND",
            "PROPOSE, PROPOSE",
            "AGREE, RESPONSE",
            "COUNTER, COMMAND",
            "DISPUTE, DECLINE",
            "QUALIFY, RESPONSE",
            "RESOLVE, DONE"
    })
    void buildResponseMapsEntryTypeToMessageType(String entryType, String expectedType) {
        var llmOutput = "entryType: " + entryType + "\nbody: Test assertion about the market";
        var request = makeRequest("trigger");

        var response = handler.buildResponse(CHANNEL_ID, SENDER_ID, llmOutput, request);

        assertEquals(MessageType.valueOf(expectedType), response.type());
    }

    @Test
    void buildResponseIncludesSentinel() {
        var llmOutput = "entryType: RAISE\nbody: AAPL showing momentum exhaustion";
        var request = makeRequest("trigger");

        var response = handler.buildResponse(CHANNEL_ID, SENDER_ID, llmOutput, request);

        assertTrue(response.content().startsWith("##FSI##"));
        var meta = ChannelMessageMeta.parseMeta("##FSI##", response.content());
        assertEquals("RAISE", meta.get("entryType"));
        assertEquals("momentum-strategy", meta.get("role"));
    }

    @Test
    void buildResponseSetsCorrelationIdForPointInitiator() {
        var llmOutput = "entryType: RAISE\nbody: thesis";
        var request = makeRequest("trigger");

        var response = handler.buildResponse(CHANNEL_ID, SENDER_ID, llmOutput, request);

        assertNotNull(response.correlationId());
    }

    @Test
    void buildResponseUsesTargetCorrelationIdForResponses() {
        var targetPointId = UUID.randomUUID().toString();
        var llmOutput = "entryType: AGREE\ntargetPointId: " + targetPointId + "\nbody: I concur";
        var request = makeRequest("trigger");

        var response = handler.buildResponse(CHANNEL_ID, SENDER_ID, llmOutput, request);

        assertEquals(targetPointId, response.correlationId());
    }

    @Test
    void buildResponseDefaultsToRaiseForInvalidEntryType() {
        var llmOutput = "entryType: INVALID_TYPE\nbody: some text";
        var request = makeRequest("trigger");

        var response = handler.buildResponse(CHANNEL_ID, SENDER_ID, llmOutput, request);

        assertEquals(MessageType.COMMAND, response.type());
        var meta = ChannelMessageMeta.parseMeta("##FSI##", response.content());
        assertEquals("RAISE", meta.get("entryType"));
    }

    @Test
    void buildResponseSetsCorrectSender() {
        var llmOutput = "entryType: RAISE\nbody: thesis";
        var request = makeRequest("trigger");

        var response = handler.buildResponse(CHANNEL_ID, SENDER_ID, llmOutput, request);

        assertEquals(SENDER_ID, response.sender());
    }

    @Test
    void buildResponseExtractsBodyContent() {
        var llmOutput = "entryType: PROPOSE\nbody: BUY 200 AAPL at market";
        var request = makeRequest("trigger");

        var response = handler.buildResponse(CHANNEL_ID, SENDER_ID, llmOutput, request);

        var body = ChannelMessageMeta.bodyContent("##FSI##", response.content());
        assertEquals("BUY 200 AAPL at market", body);
    }

    private ChannelAgentRequest makeRequest(String content) {
        var message = new OutboundMessage(UUID.randomUUID(), 1L, "other-agent",
                MessageType.COMMAND, content, null, null, null, List.of(), null, "debate");
        return new ChannelAgentRequest(CHANNEL_ID, UUID.randomUUID().toString(), message, "debate");
    }
}
