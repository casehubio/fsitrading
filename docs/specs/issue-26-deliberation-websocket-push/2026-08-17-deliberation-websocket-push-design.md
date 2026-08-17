# Deliberation WebSocket Push — Design Spec

Issue: casehubio/fsitrading#26
Branch: issue-26-deliberation-websocket-push

## Overview

Wire deliberation lifecycle events (start, complete, fail) and per-round convergence updates to WebSocket push topics via `PushBroadcaster`. Two topics: `deliberation:active` (global feed) and `deliberation:{channelId}` (per-deliberation feed). Follows the C2 Market Pulse push pattern but uses CDI events for transport (consistent with C2 D4 decoupling precedent) and a dedicated listener bean (not mixed into `FsiMarketPushService`).

## §1 CDI Domain Events

Three domain event records fired by `FsiDeliberationOrchestrator` at lifecycle transition points. These are CDI events (`@Observes`), not push payloads — the push listener translates them into broadcast payloads.

### DeliberationStartedEvent

```java
package io.casehub.fsitrading.app.deliberation;

public record DeliberationStartedEvent(
        UUID deliberationId,
        UUID channelId,
        String instrument,
        String triggerType,
        String participants,
        Instant startedAt) {}
```

**Fired from:** `FsiDeliberationOrchestrator.startDeliberation()`, after the `DeliberationRecord` is persisted.

### DeliberationCompletedEvent

```java
public record DeliberationCompletedEvent(
        UUID deliberationId,
        UUID channelId,
        String instrument,
        String convergenceState,
        double confidence,
        int establishedCount,
        int disputedCount,
        int pendingCount,
        int rounds,
        UUID commitmentId,
        UUID tradeDecisionId,
        String outcomeType,
        Instant endedAt) {}
```

**Fired from:** `FsiDeliberationOrchestrator.completeDeliberation()`, after the `DeliberationRecord` is updated and the outcome (Execute or Escalate) is resolved. `outcomeType` is `"EXECUTE"` or `"ESCALATE"`.

### DeliberationFailedEvent

```java
public record DeliberationFailedEvent(
        UUID deliberationId,
        UUID channelId,
        String instrument,
        String reason,
        Instant endedAt) {}
```

**Fired from:** `FsiDeliberationOrchestrator.failDeliberation()`, after the `DeliberationRecord` is updated.

### CDI event firing

Inject `jakarta.enterprise.event.Event<T>` for each event type:

```java
@Inject Event<DeliberationStartedEvent> startedEvent;
@Inject Event<DeliberationCompletedEvent> completedEvent;
@Inject Event<DeliberationFailedEvent> failedEvent;
```

Fire after the transactional state update in each method. CDI synchronous observers (`@Observes` without `@ObservesAsync`) execute in the same thread and transaction as the caller. The push broadcast (sending bytes to WebSocket connections) is a side effect that cannot be rolled back if the transaction fails. This is consistent with market data push behaviour — fire-and-forget with no delivery guarantee. If transactional consistency matters in future, switch to `@ObservesAsync` or `TransactionPhase.AFTER_SUCCESS`.

## §2 Push Payloads

Type-discriminated envelope records for WebSocket broadcast. Each payload has a `type` field for client-side dispatch.

```java
package io.casehub.fsitrading.app.deliberation;

public sealed interface DeliberationPushPayload {

    String type();

    record Started(
            String type,
            UUID deliberationId,
            UUID channelId,
            String instrument,
            String triggerType,
            Instant startedAt) implements DeliberationPushPayload {
        public Started(UUID deliberationId, UUID channelId, String instrument,
                       String triggerType, Instant startedAt) {
            this("DELIBERATION_STARTED", deliberationId, channelId,
                 instrument, triggerType, startedAt);
        }
    }

    record Completed(
            String type,
            UUID deliberationId,
            UUID channelId,
            String instrument,
            String convergenceState,
            double confidence,
            int establishedCount,
            int disputedCount,
            int pendingCount,
            int rounds,
            UUID commitmentId,
            UUID tradeDecisionId,
            String outcomeType,
            Instant endedAt) implements DeliberationPushPayload {
        public Completed(UUID deliberationId, UUID channelId, String instrument,
                         String convergenceState, double confidence,
                         int establishedCount, int disputedCount, int pendingCount,
                         int rounds, UUID commitmentId, UUID tradeDecisionId,
                         String outcomeType, Instant endedAt) {
            this("DELIBERATION_COMPLETED", deliberationId, channelId, instrument,
                 convergenceState, confidence, establishedCount, disputedCount,
                 pendingCount, rounds, commitmentId, tradeDecisionId,
                 outcomeType, endedAt);
        }
    }

    record Failed(
            String type,
            UUID deliberationId,
            UUID channelId,
            String instrument,
            String reason,
            Instant endedAt) implements DeliberationPushPayload {
        public Failed(UUID deliberationId, UUID channelId, String instrument,
                      String reason, Instant endedAt) {
            this("DELIBERATION_FAILED", deliberationId, channelId,
                 instrument, reason, endedAt);
        }
    }

    record ConvergenceUpdate(
            String type,
            UUID channelId,
            String convergenceState,
            double confidence,
            int establishedCount,
            int disputedCount,
            int pendingCount,
            int dispatchCount) implements DeliberationPushPayload {
        public ConvergenceUpdate(UUID channelId, String convergenceState,
                                 double confidence, int establishedCount,
                                 int disputedCount, int pendingCount,
                                 int dispatchCount) {
            this("CONVERGENCE_UPDATE", channelId, convergenceState, confidence,
                 establishedCount, disputedCount, pendingCount, dispatchCount);
        }
    }
}
```

Jackson serialises these via the Quarkus-configured `ObjectMapper`. The `type` field is always first in the canonical constructor, ensuring it appears in JSON output. Clients `switch` on `type` to deserialise the appropriate shape.

## §3 FsiDeliberationPushListener

A dedicated `@ApplicationScoped` bean that observes deliberation CDI events and broadcasts push payloads. Separate from `FsiMarketPushService` (which stays market-data-only).

```java
package io.casehub.fsitrading.app.deliberation;

@ApplicationScoped
public class FsiDeliberationPushListener {

    private final FsiMarketPushService.PushBroadcaster broadcaster;

    @Inject
    public FsiDeliberationPushListener(io.casehub.pages.push.EventBroadcaster eventBroadcaster) {
        this.broadcaster = eventBroadcaster::broadcast;
    }

    void onStarted(@Observes DeliberationStartedEvent event) {
        var payload = new DeliberationPushPayload.Started(
                event.deliberationId(), event.channelId(), event.instrument(),
                event.triggerType(), event.startedAt());
        broadcaster.broadcast("deliberation:active", payload);
        broadcaster.broadcast("deliberation:" + event.channelId(), payload);
    }

    void onCompleted(@Observes DeliberationCompletedEvent event) {
        var payload = new DeliberationPushPayload.Completed(
                event.deliberationId(), event.channelId(), event.instrument(),
                event.convergenceState(), event.confidence(),
                event.establishedCount(), event.disputedCount(), event.pendingCount(),
                event.rounds(), event.commitmentId(), event.tradeDecisionId(),
                event.outcomeType(), event.endedAt());
        broadcaster.broadcast("deliberation:active", payload);
        broadcaster.broadcast("deliberation:" + event.channelId(), payload);
    }

    void onFailed(@Observes DeliberationFailedEvent event) {
        var payload = new DeliberationPushPayload.Failed(
                event.deliberationId(), event.channelId(), event.instrument(),
                event.reason(), event.endedAt());
        broadcaster.broadcast("deliberation:active", payload);
        broadcaster.broadcast("deliberation:" + event.channelId(), payload);
    }
}
```

### Why not `FsiMarketPushService`?

`FsiMarketPushService` is a plain Java class (not CDI-managed) that subscribes to `EventStreamBus` instances — a reactive, bus-driven pattern. Deliberation push is event-driven (CDI `@Observes`). Mixing both patterns in one class creates a hybrid that's harder to reason about (D5). The listener is CDI-managed because it needs `@Observes`; the market push service is not because it's wired manually via `MarketPulseConfiguration`.

## §4 Per-Round Convergence Updates

`FsiDeliberationStateObserver` already implements `MessageObserver` and is called on every channel message. It holds `ConversationState` via an `AtomicReference`. Adding convergence analysis and push broadcast to the observer's message-processing path gives real-time convergence updates with no cross-repo dependency.

### Changes to FsiDeliberationStateObserver

Add `ConvergenceAnalyser`, `CommonGroundAnalyser`, `EpistemicRules`, and `PushBroadcaster` as constructor dependencies:

```java
public class FsiDeliberationStateObserver implements MessageObserver, EventSource {

    private final FsiConversationProjection projection;
    private final String channelName;
    private final UUID channelId;
    private final AtomicReference<ConversationState> state;
    private volatile Consumer<DriverEvent> sink;
    private final ConvergenceAnalyser convergenceAnalyser;
    private final CommonGroundAnalyser commonGroundAnalyser;
    private final FsiMarketPushService.PushBroadcaster broadcaster;
    private int dispatchCount;

    @Override
    public void onMessage(MessageReceivedEvent event) {
        var messageView = toMessageView(event);
        var newState = state.updateAndGet(current ->
                projection.apply(current, messageView));
        dispatchCount++;

        // Compute convergence and broadcast
        var signal = convergenceAnalyser.analyse(newState, dispatchCount);
        var commonGround = commonGroundAnalyser.analyse(newState);
        broadcaster.broadcast("deliberation:" + channelId,
                new DeliberationPushPayload.ConvergenceUpdate(
                        channelId,
                        signal.state().name(),
                        signal.confidence(),
                        commonGround.establishedFacts().size(),
                        commonGround.disputedPoints().size(),
                        commonGround.pendingClaims().size(),
                        dispatchCount));

        var currentSink = sink;
        if (currentSink != null) {
            currentSink.accept(DriverEvent.signal("message"));
        }
    }
}
```

### Convergence analysis is cheap

Both `ConvergenceAnalyser.analyse()` and `CommonGroundAnalyser.analyse()` are pure computation over `ConversationState` — no LLM calls, no I/O, no database queries. They iterate points, threads, and memos to compute counts and ratios. This is safe to call on every message without performance concern.

### Broadcast frequency

The observer fires on every channel message. In a typical deliberation with 3 agents and 10 rounds, that's ~30 messages — ~30 convergence updates. Acceptable volume for WebSocket push. If UI panels need less granularity, they can debounce client-side.

### Observer construction

The observer is constructed in the deliberation setup path (not CDI-managed). The `PushBroadcaster` and analysers must be passed to the constructor by whoever creates the observer. This is the same place that currently creates the observer with just `projection` and `channelName` — it gains three new dependencies.

## §5 Orchestrator Changes

### FsiDeliberationOrchestrator modifications

Three changes:

1. **Inject CDI Event types:**
```java
@Inject Event<DeliberationStartedEvent> startedEvent;
@Inject Event<DeliberationCompletedEvent> completedEvent;
@Inject Event<DeliberationFailedEvent> failedEvent;
```

2. **Fire `startedEvent` at end of `startDeliberation()`:**
```java
startedEvent.fire(new DeliberationStartedEvent(
        recordId, channelId, instrument, triggerType,
        participants, record.getStartedAt()));
```

3. **Fire `completedEvent` at end of `completeDeliberation()`** (after outcome resolution):
```java
completedEvent.fire(new DeliberationCompletedEvent(
        record.getId(), record.getChannelId(), record.getInstrument(),
        signal.state().name(), signal.confidence(),
        commonGround.establishedFacts().size(),
        commonGround.disputedPoints().size(),
        commonGround.pendingClaims().size(),
        record.getRounds(), record.getCommitmentId(),
        record.getTradeDecisionId(),
        outcome instanceof OutcomeAction.Execute ? "EXECUTE" : "ESCALATE",
        record.getEndedAt()));
```

4. **Fire `failedEvent` at end of `failDeliberation()`:**
```java
failedEvent.fire(new DeliberationFailedEvent(
        recordId, record.getChannelId(), record.getInstrument(),
        reason, record.getEndedAt()));
```

### Note on `channelId` in `failDeliberation()`

The current `failDeliberation()` method takes only `recordId` and `reason`. It loads the record to get the channelId. The record is guaranteed to exist (created in `startDeliberation()`), so `channelId` is always available for the failure event.

## §6 WebSocket Topics

| Topic | Events | Trigger |
|-------|--------|---------|
| `deliberation:active` | `DELIBERATION_STARTED`, `DELIBERATION_COMPLETED`, `DELIBERATION_FAILED` | Lifecycle transitions in orchestrator |
| `deliberation:{channelId}` | All of the above + `CONVERGENCE_UPDATE` | Lifecycle transitions + every channel message |

### Topic naming convention

Colon-separated hierarchies, consistent with existing market data topics:
- `market:ticks:AAPL` → `deliberation:active`
- `market:bars:MSFT` → `deliberation:{channelId}`

### Subscription flow

1. Client connects to `/ws/push` WebSocket
2. Client sends: `{"op": "listen", "topics": ["deliberation:active"]}`
3. Server pushes `DELIBERATION_STARTED` when a deliberation begins
4. Client extracts `channelId`, sends: `{"op": "listen", "topics": ["deliberation:<channelId>"]}`
5. Server pushes `CONVERGENCE_UPDATE` events during debate
6. Server pushes `DELIBERATION_COMPLETED` or `DELIBERATION_FAILED` when deliberation ends

### Known limitation: subscription race

Events published between steps 3 and 4 may be missed. `EventBroadcaster` supports `EventStore` with sequence numbers for catch-up replay, but designing the replay protocol is C5 scope (when UI panels are wired). See decisions.md "Known Limitations" section.

## §7 Testing Strategy

### FsiDeliberationPushListenerTest (unit)

Inject a mock `EventBroadcaster`, fire CDI events, assert broadcast calls with correct topics and payload types. Pattern matches `FsiMarketPushServiceTest` — use a `BroadcastCapture` list.

```java
class FsiDeliberationPushListenerTest {
    List<BroadcastCapture> broadcasts = new ArrayList<>();
    FsiDeliberationPushListener listener;

    @BeforeEach
    void setUp() {
        listener = new FsiDeliberationPushListener(
                (topic, event) -> broadcasts.add(new BroadcastCapture(topic, event)));
    }

    @Test
    void started_broadcastsToBothTopics() {
        var event = new DeliberationStartedEvent(...);
        listener.onStarted(event);
        assertEquals(2, broadcasts.size());
        assertEquals("deliberation:active", broadcasts.get(0).topic);
        assertStartsWith("deliberation:", broadcasts.get(1).topic);
        assertInstanceOf(DeliberationPushPayload.Started.class, broadcasts.get(0).event);
    }
}
```

### FsiDeliberationStateObserver convergence test (unit)

Extend `FsiDeliberationStateObserverTest` to verify that `onMessage()` broadcasts a `CONVERGENCE_UPDATE` payload to `deliberation:{channelId}`. Inject a mock broadcaster, publish a message, assert the broadcast.

### Integration test

A `@QuarkusTest` that:
1. Starts a deliberation via `FsiDeliberationOrchestrator.startDeliberation()`
2. Verifies `DELIBERATION_STARTED` was broadcast to both topics
3. Completes the deliberation via `completeDeliberation()`
4. Verifies `DELIBERATION_COMPLETED` was broadcast to both topics
5. Verifies payload type discriminators are correct

## §8 File Inventory

| File | Action | Description |
|------|--------|-------------|
| `app/.../deliberation/DeliberationStartedEvent.java` | Create | CDI domain event record |
| `app/.../deliberation/DeliberationCompletedEvent.java` | Create | CDI domain event record |
| `app/.../deliberation/DeliberationFailedEvent.java` | Create | CDI domain event record |
| `app/.../deliberation/DeliberationPushPayload.java` | Create | Sealed interface with 4 payload records |
| `app/.../deliberation/FsiDeliberationPushListener.java` | Create | CDI observer → push broadcaster |
| `app/.../deliberation/FsiDeliberationOrchestrator.java` | Modify | Inject + fire CDI events |
| `app/.../deliberation/FsiDeliberationStateObserver.java` | Modify | Add convergence analysis + broadcast |
| `app/test/.../deliberation/FsiDeliberationPushListenerTest.java` | Create | Unit test for listener |
| `app/test/.../deliberation/FsiDeliberationStateObserverTest.java` | Modify | Add convergence broadcast test |

## References

- `FsiMarketPushService.java:28-61` — existing push subscription pattern (market data)
- `FsiMarketPushServiceTest.java` — BroadcastCapture test pattern
- `MarketPulseConfiguration.java:94-96` — push service producer wiring
- `FsiDeliberationOrchestrator.java:64-140` — lifecycle methods (start/complete/fail)
- `FsiDeliberationStateObserver.java:29-35` — message observer with ConversationState
- `FsiDeliberationOutcomeHandler.java:36-46` — outcome resolution (Execute/Escalate)
- `FsiPushWebSocket.java` — WebSocket endpoint, listen/unlisten protocol
- C2 Market Pulse decisions D4 — CDI event decoupling precedent
- Spec §5 (Commitment Lifecycle Events) — push contract from C3 deliberation design
- Spec §8 (WebSocket Topics) — topic definitions
- Decision review R1-02 — blocks#125 wrong execution path finding
- Decision review R1-05 — CDI events as C2 precedent
- Decision review R1-08 — colon naming convention
- Decision review R1-12 — type-discriminated payload requirement
