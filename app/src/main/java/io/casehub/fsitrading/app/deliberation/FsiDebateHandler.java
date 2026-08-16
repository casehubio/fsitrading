package io.casehub.fsitrading.app.deliberation;

import io.casehub.blocks.channel.AgentTask;
import io.casehub.blocks.channel.ChannelAgentHandler;
import io.casehub.blocks.channel.ChannelAgentRequest;
import io.casehub.blocks.channel.ChannelMessageMeta;
import io.casehub.fsitrading.FsiActorIdentity;
import io.casehub.fsitrading.model.StrategyType;
import io.casehub.platform.api.identity.ActorType;
import io.casehub.qhorus.api.message.MessageDispatch;
import io.casehub.qhorus.api.message.MessageType;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class FsiDebateHandler implements ChannelAgentHandler {

    private static final Set<String> POINT_INITIATORS = Set.of("RAISE", "PROPOSE");
    private static final Set<String> VALID_ENTRY_TYPES = Set.of(
            "RAISE", "PROPOSE", "AGREE", "COUNTER", "DISPUTE", "QUALIFY", "RESOLVE");

    private final StrategyType strategyType;
    private final String marketEventContext;

    public FsiDebateHandler(StrategyType strategyType, String marketEventContext) {
        this.strategyType = strategyType;
        this.marketEventContext = marketEventContext;
    }

    @Override
    public boolean handles(ChannelAgentRequest request) {
        return true;
    }

    @Override
    public AgentTask prepareTask(ChannelAgentRequest request) {
        var role = FsiActorIdentity.actorRole(strategyType);
        var systemPrompt = "You are a " + role + " agent participating in a trading deliberation. " +
                "Analyse the market event and debate with other strategy agents. " +
                "Respond with your assessment using the structured format.";

        var input = "Market event: " + marketEventContext + "\n\n" +
                "Respond with:\n" +
                "entryType: <one of RAISE, PROPOSE, AGREE, COUNTER, DISPUTE, QUALIFY, RESOLVE>\n" +
                "targetPointId: <UUID of the point you're responding to, only for responses>\n" +
                "body: <your analysis or trade proposal>";

        return new AgentTask(systemPrompt, input);
    }

    @Override
    public MessageDispatch buildResponse(UUID channelId, String senderId,
                                         String llmOutput, ChannelAgentRequest trigger) {
        var parsed = parseLlmOutput(llmOutput);
        var entryType = parsed.getOrDefault("entryType", "RAISE");
        if (!VALID_ENTRY_TYPES.contains(entryType)) {
            entryType = "RAISE";
        }
        var body = parsed.getOrDefault("body", llmOutput);
        var targetPointId = parsed.get("targetPointId");

        var correlationId = POINT_INITIATORS.contains(entryType)
                ? UUID.randomUUID().toString()
                : (targetPointId != null ? targetPointId : trigger.correlationId());

        var messageType = mapToMessageType(entryType);
        var role = FsiActorIdentity.actorRole(strategyType);

        var meta = new LinkedHashMap<String, String>();
        meta.put("entryType", entryType);
        meta.put("role", role);
        var content = ChannelMessageMeta.encode("##FSI##", meta, body);

        var builder = MessageDispatch.builder()
                .channelId(channelId)
                .sender(senderId)
                .type(messageType)
                .content(content)
                .correlationId(correlationId)
                .actorType(ActorType.AGENT);

        if (!POINT_INITIATORS.contains(entryType)) {
            var seq = trigger.message() != null ? trigger.message().sequenceId() : null;
            builder.inReplyTo(seq != null ? seq : 0L);
        }

        return builder.build();
    }

    private static MessageType mapToMessageType(String entryType) {
        return switch (entryType) {
            case "RAISE", "COUNTER" -> MessageType.COMMAND;
            case "PROPOSE" -> MessageType.PROPOSE;
            case "AGREE", "QUALIFY" -> MessageType.RESPONSE;
            case "DISPUTE" -> MessageType.DECLINE;
            case "RESOLVE" -> MessageType.DONE;
            default -> MessageType.COMMAND;
        };
    }

    private static Map<String, String> parseLlmOutput(String output) {
        var result = new LinkedHashMap<String, String>();
        if (output == null) return result;
        for (var line : output.split("\n")) {
            var colonIdx = line.indexOf(':');
            if (colonIdx > 0) {
                var key = line.substring(0, colonIdx).strip();
                var value = line.substring(colonIdx + 1).strip();
                result.put(key, value);
            }
        }
        return result;
    }
}
