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

public class VolumeAnalysisHandler implements ChannelAgentHandler {

    private final FsiObservationCache cache;

    public VolumeAnalysisHandler(FsiObservationCache cache) {
        this.cache = cache;
    }

    @Override
    public boolean handles(ChannelAgentRequest request) {
        var meta = parseMeta(request);
        return "SUB_TASK_REQUEST".equals(meta.get("entryType"))
                && "VOLUME_ANALYSIS".equals(meta.get("taskType"));
    }

    @Override
    public AgentTask prepareTask(ChannelAgentRequest request) {
        var meta = parseMeta(request);
        var instrument = meta.getOrDefault("instrument", "unknown");
        var bar = cache.latestBar(instrument);

        var statsContext = new StringBuilder();
        statsContext.append("Volume analysis for ").append(instrument).append(".\n");
        bar.ifPresent(b -> statsContext.append("Latest bar: volume=").append(b.volume())
                .append(" close=").append(b.close())
                .append(" high=").append(b.high())
                .append(" low=").append(b.low()).append("\n"));

        return new AgentTask(
                "You are a volume analyst. Classify volume as NORMAL, HIGH, SPIKE, or DRY relative to typical activity. Provide a trading-relevant interpretation.",
                statsContext.toString());
    }

    @Override
    public MessageDispatch buildResponse(UUID channelId, String senderId,
                                         String llmOutput, ChannelAgentRequest trigger) {
        var meta = new LinkedHashMap<String, String>();
        meta.put("entryType", "SUB_TASK_FINDING");
        meta.put("taskType", "VOLUME_ANALYSIS");
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
