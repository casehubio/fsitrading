package io.casehub.fsitrading.app.deliberation.handler;

import io.casehub.blocks.channel.AgentTask;
import io.casehub.blocks.channel.ChannelAgentHandler;
import io.casehub.blocks.channel.ChannelAgentRequest;
import io.casehub.blocks.channel.ChannelMessageMeta;
import io.casehub.fsitrading.app.pipeline.FsiObservationCache;
import io.casehub.fsitrading.model.OHLCV;
import io.casehub.platform.api.identity.ActorType;
import io.casehub.qhorus.api.message.MessageDispatch;
import io.casehub.qhorus.api.message.MessageType;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

public class CorrelationCheckHandler implements ChannelAgentHandler {

    private final FsiObservationCache cache;

    public CorrelationCheckHandler(FsiObservationCache cache) {
        this.cache = cache;
    }

    @Override
    public boolean handles(ChannelAgentRequest request) {
        var meta = parseMeta(request);
        return "SUB_TASK_REQUEST".equals(meta.get("entryType"))
                && "CORRELATION_CHECK".equals(meta.get("taskType"));
    }

    @Override
    public AgentTask prepareTask(ChannelAgentRequest request) {
        var meta = parseMeta(request);
        var instrument1 = meta.getOrDefault("instrument1", "unknown");
        var instrument2 = meta.getOrDefault("instrument2", "unknown");

        var bar1 = cache.latestBar(instrument1);
        var bar2 = cache.latestBar(instrument2);

        var statsContext = new StringBuilder();
        statsContext.append("Correlation check between ").append(instrument1)
                .append(" and ").append(instrument2).append(".\n");
        bar1.ifPresent(b -> statsContext.append(instrument1).append(" latest bar: close=")
                .append(b.close()).append(" volume=").append(b.volume()).append("\n"));
        bar2.ifPresent(b -> statsContext.append(instrument2).append(" latest bar: close=")
                .append(b.close()).append(" volume=").append(b.volume()).append("\n"));

        return new AgentTask(
                "You are a quantitative analyst. Interpret the correlation data and provide a trading-relevant assessment.",
                statsContext.toString());
    }

    @Override
    public MessageDispatch buildResponse(UUID channelId, String senderId,
                                         String llmOutput, ChannelAgentRequest trigger) {
        var meta = new LinkedHashMap<String, String>();
        meta.put("entryType", "SUB_TASK_FINDING");
        meta.put("taskType", "CORRELATION_CHECK");
        meta.put("role", "ANALYST");
        var content = ChannelMessageMeta.encode("##FSI##", meta, llmOutput);

        return MessageDispatch.builder()
                .channelId(channelId)
                .sender(senderId)
                .type(MessageType.COMMAND)
                .content(content)
                .correlationId(trigger.correlationId())
                .actorType(ActorType.AGENT)
                .build();
    }

    private Map<String, String> parseMeta(ChannelAgentRequest request) {
        var content = request.message() != null ? request.message().content() : null;
        return content != null ? ChannelMessageMeta.parseMeta("##FSI##", content) : Map.of();
    }
}
