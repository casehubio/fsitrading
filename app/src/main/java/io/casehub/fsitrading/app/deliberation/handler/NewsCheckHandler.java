package io.casehub.fsitrading.app.deliberation.handler;

import io.casehub.blocks.channel.AgentTask;
import io.casehub.blocks.channel.ChannelAgentHandler;
import io.casehub.blocks.channel.ChannelAgentRequest;
import io.casehub.blocks.channel.ChannelMessageMeta;
import io.casehub.fsitrading.app.pipeline.FsiObservationCache;
import io.casehub.platform.api.identity.ActorType;
import io.casehub.qhorus.api.message.MessageDispatch;
import io.casehub.qhorus.api.message.MessageType;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

public class NewsCheckHandler implements ChannelAgentHandler {

    private final FsiObservationCache cache;

    public NewsCheckHandler(FsiObservationCache cache) {
        this.cache = cache;
    }

    @Override
    public boolean handles(ChannelAgentRequest request) {
        var meta = parseMeta(request);
        return "SUB_TASK_REQUEST".equals(meta.get("entryType"))
                && "NEWS_CHECK".equals(meta.get("taskType"));
    }

    @Override
    public AgentTask prepareTask(ChannelAgentRequest request) {
        var meta = parseMeta(request);
        var instrument = meta.getOrDefault("instrument", "unknown");

        var context = new StringBuilder();
        context.append("News check for ").append(instrument).append(".\n");
        cache.latestTick(instrument).ifPresent(t ->
                context.append("Current price: ").append(t.price()).append("\n"));
        cache.latestTrend(instrument).ifPresent(t ->
                context.append("Trend: direction=").append(t.direction())
                        .append(" momentum=").append(t.momentum()).append("\n"));
        cache.latestRegime(instrument).ifPresent(r ->
                context.append("Market regime: ").append(r.regime()).append("\n"));

        return new AgentTask(
                "You are a financial news analyst. Based on the price pattern and market context, " +
                "assess sentiment (BULLISH/BEARISH/NEUTRAL), identify key themes, and provide a relevance score (0.0-1.0).",
                context.toString());
    }

    @Override
    public MessageDispatch buildResponse(UUID channelId, String senderId,
                                         String llmOutput, ChannelAgentRequest trigger) {
        var meta = new LinkedHashMap<String, String>();
        meta.put("entryType", "SUB_TASK_FINDING");
        meta.put("taskType", "NEWS_CHECK");
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
