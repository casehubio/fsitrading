package io.casehub.fsitrading.app.deliberation;

import io.casehub.blocks.agentic.model.DriverEvent;
import io.casehub.blocks.agentic.model.EventSource;
import io.casehub.blocks.conversation.ConversationState;
import io.casehub.qhorus.api.gateway.MessageObserver;
import io.casehub.qhorus.api.gateway.MessageReceivedEvent;
import io.casehub.qhorus.api.message.MessageView;

import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

public class FsiDeliberationStateObserver implements MessageObserver, EventSource {

    private final FsiConversationProjection projection;
    private final String channelName;
    private final AtomicReference<ConversationState> state;
    private volatile Consumer<DriverEvent> sink;

    public FsiDeliberationStateObserver(FsiConversationProjection projection, String channelName) {
        this.projection = projection;
        this.channelName = channelName;
        this.state = new AtomicReference<>(projection.identity());
    }

    @Override
    public void onMessage(MessageReceivedEvent event) {
        var messageView = toMessageView(event);
        state.updateAndGet(current -> projection.apply(current, messageView));
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
