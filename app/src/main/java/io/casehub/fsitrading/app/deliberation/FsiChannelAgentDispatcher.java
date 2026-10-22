package io.casehub.fsitrading.app.deliberation;

import io.casehub.agency.communication.dispatch.ChannelAgentHandler;
import io.casehub.agency.communication.dispatch.ChannelAgentRequest;
import io.casehub.agency.communication.dispatch.ChannelAgentDispatcher;
import io.casehub.agency.communication.dispatch.ChannelMessageMeta;
import io.casehub.agency.communication.dispatch.AgentTask;
import io.casehub.platform.api.identity.ActorType;
import io.casehub.qhorus.api.message.MessageDispatch;
import io.casehub.qhorus.api.message.MessageType;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

public class FsiChannelAgentDispatcher extends ChannelAgentDispatcher {

    public FsiChannelAgentDispatcher(Function<AgentTask, String> agentProvider,
                                     Consumer<MessageDispatch> messageSink,
                                     Iterable<ChannelAgentHandler> handlers,
                                     String senderId) {
        super(agentProvider, messageSink, handlers, senderId);
    }

    @Override
    protected void onError(ChannelAgentRequest request, String reason) {
        super.onError(request, reason);
        var meta = new LinkedHashMap<String, String>();
        meta.put("entryType", "SUB_TASK_ERROR");
        meta.put("subTaskId", request.correlationId());
        meta.put("role", "ORCHESTRATOR");
        var content = ChannelMessageMeta.encode("##FSI##", meta, reason);
        var notification = MessageDispatch.builder()
                .channelId(request.channelId())
                .sender(senderId())
                .correlationId(request.correlationId())
                .type(MessageType.COMMAND)
                .content(content)
                .actorType(ActorType.SYSTEM)
                .build();
        messageSink().accept(notification);
    }
}
