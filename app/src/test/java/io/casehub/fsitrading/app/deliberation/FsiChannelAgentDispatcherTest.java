package io.casehub.fsitrading.app.deliberation;

import io.casehub.blocks.channel.AgentTask;
import io.casehub.blocks.channel.ChannelAgentRequest;
import io.casehub.blocks.channel.ChannelMessageMeta;
import io.casehub.qhorus.api.gateway.OutboundMessage;
import io.casehub.qhorus.api.message.MessageDispatch;
import io.casehub.qhorus.api.message.MessageType;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class FsiChannelAgentDispatcherTest {

    private static final UUID CHANNEL_ID = UUID.randomUUID();

    @Test
    void onErrorPostsSentinelWrappedMessage() {
        List<MessageDispatch> sent = new ArrayList<>();
        var dispatcher = new FsiChannelAgentDispatcher(
                task -> "response", sent::add, List.of(), "orchestrator-id");

        var request = makeSubTaskRequest("CORRELATION_CHECK", "AAPL");
        dispatcher.dispatch(request);

        assertFalse(sent.isEmpty());
        var msg = sent.get(0);
        assertTrue(msg.content().startsWith("##FSI##"));
        var meta = ChannelMessageMeta.parseMeta("##FSI##", msg.content());
        assertEquals("SUB_TASK_ERROR", meta.get("entryType"));
    }

    private ChannelAgentRequest makeSubTaskRequest(String taskType, String instrument) {
        var content = ChannelMessageMeta.encode("##FSI##",
                Map.of("entryType", "SUB_TASK_REQUEST", "taskType", taskType,
                        "instrument", instrument, "role", "ORCHESTRATOR"),
                "Please analyse " + instrument);
        var message = new OutboundMessage(UUID.randomUUID(), 1L, "orchestrator",
                MessageType.COMMAND, content, null, null, null, List.of(), null, "debate");
        return new ChannelAgentRequest(CHANNEL_ID, UUID.randomUUID().toString(), message, "debate");
    }
}
