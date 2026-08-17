package io.casehub.fsitrading.app.deliberation;

import io.casehub.blocks.agentic.model.DriverEvent;
import io.casehub.blocks.agentic.model.EventSource;
import io.casehub.blocks.conversation.CommonGroundAnalyser;
import io.casehub.blocks.conversation.ConvergenceAnalyser;
import io.casehub.blocks.conversation.ConvergencePolicy;
import io.casehub.blocks.conversation.ConversationState;
import io.casehub.blocks.conversation.EpistemicRule;
import io.casehub.fsitrading.app.pipeline.FsiMarketPushService;
import io.casehub.qhorus.api.gateway.MessageObserver;
import io.casehub.qhorus.api.gateway.MessageReceivedEvent;
import io.casehub.qhorus.api.message.MessageView;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

public class FsiDeliberationStateObserver implements MessageObserver, EventSource {

    private final FsiConversationProjection projection;
    private final String channelName;
    private final UUID channelId;
    private final AtomicReference<ConversationState> state;
    private volatile Consumer<DriverEvent> sink;
    private final EpistemicRule epistemicRule;
    private final ConvergencePolicy convergencePolicy;
    private final int recentWindow;
    private final FsiMarketPushService.PushBroadcaster broadcaster;
    private int dispatchCount;

    public FsiDeliberationStateObserver(FsiConversationProjection projection,
                                        String channelName, UUID channelId,
                                        EpistemicRule epistemicRule,
                                        ConvergencePolicy convergencePolicy,
                                        int recentWindow,
                                        FsiMarketPushService.PushBroadcaster broadcaster) {
        this.projection = projection;
        this.channelName = channelName;
        this.channelId = channelId;
        this.state = new AtomicReference<>(projection.identity());
        this.epistemicRule = epistemicRule;
        this.convergencePolicy = convergencePolicy;
        this.recentWindow = recentWindow;
        this.broadcaster = broadcaster;
    }

    @Override
    public void onMessage(MessageReceivedEvent event) {
        var messageView = toMessageView(event);
        var newState = state.updateAndGet(current -> projection.apply(current, messageView));
        dispatchCount++;

        if (broadcaster != null) {
            var commonGround = CommonGroundAnalyser.analyse(newState, epistemicRule);
            var signal = ConvergenceAnalyser.analyse(newState, commonGround,
                    convergencePolicy, recentWindow);
            broadcaster.broadcast("deliberation:" + channelId,
                    new DeliberationPushPayload.ConvergenceUpdate(
                            channelId,
                            signal.state().name(),
                            signal.confidence(),
                            commonGround.establishedFacts().size(),
                            commonGround.disputedPoints().size(),
                            commonGround.pendingClaims().size(),
                            dispatchCount));
        }

        var currentSink = sink;
        if (currentSink != null) {
            currentSink.accept(DriverEvent.signal("message"));
        }
    }

    @Override
    public Cancellation subscribe(Consumer<DriverEvent> newSink) {
        this.sink = newSink;
        return () -> this.sink = null;
    }

    public ConversationState currentState() {
        return state.get();
    }

    @Override
    public Set<String> channels() {
        return Set.of(channelName);
    }

    @Override
    public Scope scope() {
        return Scope.LOCAL;
    }

    private static MessageView toMessageView(MessageReceivedEvent event) {
        return new MessageView(
                event.messageId(),
                event.channelId(),
                event.senderId(),
                event.messageType(),
                event.content(),
                event.correlationId(),
                null,
                event.target(),
                event.topic(),
                List.of(),
                event.actorType(),
                event.occurredAt(),
                null,
                0);
    }
}
