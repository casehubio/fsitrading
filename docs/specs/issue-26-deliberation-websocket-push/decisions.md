## D1: Scope — full push contract, no external gate

**Choice:** Design and implement both lifecycle events (start/complete/fail) and per-round convergence updates in #26. Per-round convergence uses `FsiDeliberationStateObserver.onMessage()` — the observer already holds `ConversationState` and is called on every channel message. No cross-repo dependency.
**Alternatives:**
- Gate per-round on blocks#125 — rejected after review. blocks#125 adds `RoundListener` to `ConversationOrchestrator`, but fsitrading's deliberation uses `Patterns.debate()` / `ChoreographedDriver`, not `ConversationOrchestrator.converse()`. Wrong execution path.
- Lifecycle only, separate issue — fragments the push contract design
**Rationale:** The observer path is available today, fires at message granularity (finer than round-level), and requires no upstream changes. The full push contract can ship in one issue.
**Trade-offs:** Message-level convergence broadcasts are noisier than round-level. Acceptable — UI panels can throttle or debounce client-side.
**Sources:** FsiDeliberationStateObserver.java:29-35 (onMessage), ConvergenceAnalyser (blocks), ConversationOrchestrator.java (blocks — verified not used by debate path), Decision review R1-02, R1-03
**Exploration:** quick
**Status:** revised (was: gate on blocks#125)

## D2: Transport — CDI events for lifecycle, direct broadcast for per-round

**Choice:** `FsiDeliberationOrchestrator` fires CDI domain events (`DeliberationStartedEvent`, `DeliberationCompletedEvent`, `DeliberationFailedEvent`). A separate `FsiDeliberationPushListener` observes these and broadcasts to WebSocket topics. For per-round convergence, `FsiDeliberationStateObserver` broadcasts directly via `PushBroadcaster` (it's already in the message-processing hot path).
**Alternatives:**
- Direct call — orchestrator injects push service and calls broadcast methods. Rejected: inconsistent with C2 D4 precedent (CDI events for pipeline→arena decoupling), creates coupling, and the spec already describes multiple consumers.
- EventStreamBus\<DeliberationEvent\> — adds indirection and a bus for imperative lifecycle events. Wrong abstraction for the event shape.
**Rationale:** CDI events are the established decoupling pattern in this codebase (C2 D4). Multiple consumers are already specified (commitment-viz, blocks-timeline, C4 escalation). Testable in Quarkus @QuarkusTest. Per-round convergence broadcasts directly from the observer because it's high-frequency and only the push topic consumes it.
**Trade-offs:** CDI events add a level of indirection for lifecycle events. Acceptable — the decoupling benefit outweighs the indirection cost, and the pattern is already proven in C2.
**Depends on:** D5 (separate listener bean)
**Sources:** C2 Market Pulse decisions D4, FsiMarketPushService.java:28-61, FsiDeliberationOrchestrator.java:64-80, Decision review R1-05
**Exploration:** quick
**Status:** revised (was: direct call)

## D3: Topic routing — lifecycle to both topics, convergence to per-channel only; colon convention

**Choice:** All lifecycle events (started, completed, failed) broadcast to both `deliberation:active` (global) and `deliberation:{channelId}` (per-deliberation). Per-round convergence updates broadcast to `deliberation:{channelId}` only. Topic names use colon separators for consistency with existing market data topics (`market:ticks:AAPL`, `market:bars:AAPL`).
**Alternatives:**
- Split by type — forces subscribers to join both topics for a full view of a single deliberation
- Slash-separated paths (spec's original notation) — inconsistent with the established colon convention
**Rationale:** A subscriber to a specific deliberation should see its full lifecycle without a second subscription. Colon convention matches all existing market data topics in `FsiMarketPushService.subscribe()`.
**Trade-offs:** Lifecycle events are duplicated across two topics. Minimal cost — low-frequency events (one start, one end per deliberation).
**Sources:** FsiMarketPushService.java:37-57 (colon topics), Spec §8 (WebSocket Topics table), Decision review R1-08
**Exploration:** quick
**Status:** revised (was: slash-separated topics)

## D4: Payload envelope — type-discriminated events

**Choice:** All push payloads use a type-discriminated envelope: `{"type": "<EVENT_TYPE>", ...fields}`. Event types: `DELIBERATION_STARTED`, `DELIBERATION_COMPLETED`, `DELIBERATION_FAILED`, `CONVERGENCE_UPDATE`. Clients switch on the `type` field to distinguish events on the same topic.
**Alternatives:**
- Bare objects (infer type from field presence) — fragile, error-prone for clients
- Separate wrapper record with type + payload fields — over-engineered for flat event records
**Rationale:** Multiple event types share topics (`deliberation:active` carries started, completed, and failed). Without a discriminator, clients must infer type from field presence. A top-level `type` field is the simplest reliable approach.
**Trade-offs:** None meaningful — one extra field per payload.
**Sources:** Spec §7 (failure reporting JSON example), Decision review R1-12
**Exploration:** quick
**Status:** captured

## D5: Separate FsiDeliberationPushListener bean

**Choice:** Create `FsiDeliberationPushListener` as a dedicated `@ApplicationScoped` bean that `@Observes` deliberation CDI events and broadcasts to WebSocket topics. `FsiMarketPushService` stays market-data-only.
**Alternatives:**
- Add broadcast methods to FsiMarketPushService — mixes subscription-based (market data) and imperative (deliberation) patterns in one class
- Orchestrator injects EventBroadcaster directly — bypasses any push abstraction, scatters broadcast calls across the codebase
**Rationale:** Clean separation of concerns. Market data push is bus-driven; deliberation push is event-driven. Different patterns, different beans. Follows single-responsibility principle.
**Trade-offs:** One more bean. Acceptable — clarity outweighs the minimal overhead.
**Sources:** FsiMarketPushService.java (subscription pattern), Decision review R1-06
**Exploration:** quick
**Status:** captured

## Known Limitations

### Dynamic subscription race (R1-09)
For `deliberation:{channelId}`, the channelId is unknown until the deliberation starts. A client must: (1) subscribe to `deliberation:active`, (2) receive the started event, (3) extract the channelId, (4) subscribe to `deliberation:{channelId}`. Events between steps 2 and 4 may be missed. `EventBroadcaster` supports `EventStore` with sequence numbers for catch-up replay, but designing the replay protocol is C5 scope (when UI panels are wired). Documented here to ensure C5 addresses it.

### Dual WebSocket paths (R1-10)
The `channel-activity` panel uses the native qhorus channel WebSocket for raw message content. The `commitment-viz` and `blocks-timeline` panels use `deliberation:{channelId}` via pages-push for structured events. Two WebSocket paths for the same deliberation is intentional — different data shapes for different consumers. The qhorus WebSocket carries raw agent messages; pages-push carries interpreted lifecycle events.
