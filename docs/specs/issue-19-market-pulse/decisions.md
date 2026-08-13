# C2 Market Pulse — Decisions

## D1: Scope — Full Vertical Slice

**Choice:** Full vertical slice — pipeline + REST endpoints + pages-push WebSocket topic hierarchy + a minimal `fsi-market-panel` to prove the end-to-end push path works.
**Alternatives:**
- Backend only — pipeline + REST, defer UI/push to C5. Faster but doesn't prove the push path.
- Backend + push plumbing — pipeline + REST + WebSocket topics, but no UI panel. Push is wired but unobservable.
**Rationale:** Vertical slice philosophy — every chapter delivers a working end-to-end scenario. A minimal panel proving ticks flow from synthetic data through 5 summarisation levels to a browser WebSocket is the most convincing demonstration of the platform's push capability.
**Trade-offs:** Requires adding `casehub-pages` dependencies and Quinoa webui setup, which C5 would have done anyway. The minimal panel may need rework when the full dock-workbench composition lands in C5.
**Exploration:** quick
**Status:** captured

## D2: LLM for Levels 3-4 — Real LLM, Fast/Cheap Model

**Choice:** Real LLM synthesis via `LlmContentSummariser` for Level 3 (regime assessment) and Level 4 (session narrative). Use a fast/cheap model (ideally local) for dev iteration rather than rule-based stand-ins. Tests use CDI test doubles with canned responses. LLM output uses structured output (JSON schema) for `RegimeAssessment` to produce typed results (regime enum + confidence + rationale) rather than free text. Level 4 `SessionNarrative` remains free text (APPEND mode). On LLM failure, the pipeline gracefully skips the affected level — downstream consumers get stale data until the next successful summarisation.
**Alternatives:**
- Rule-based stand-in — heuristic logic pretending to be synthesis. Fast and deterministic but doesn't produce anything useful.
- Dual-mode with `@DefaultBean` fallback — rule-based default, LLM override. Rules still don't produce useful output.
**Rationale:** Rules that mimic LLM output (e.g., "if momentum > threshold → TRENDING") are threshold checks, not regime assessments. The value of L3/L4 is genuine synthesis. A lighter model produces real insight at lower cost; CDI test doubles handle determinism in tests. Structured output for L3 gives deterministic typing while preserving LLM reasoning.
**Trade-offs:** Dev requires an LLM provider running (even a small local one). Acceptable — the platform already has LLM provider CDI beans. Tests are isolated via mocks. Graceful degradation means agents may observe stale regime data — acceptable since stale is better than absent.
**Exploration:** quick
**Status:** revised (R1-05: added structured output, graceful degradation)

## D3: Tick Generation Lifecycle — Hybrid

**Choice:** Hybrid — background scheduler generates steady-state ticks at a configurable interval. `POST /api/market-data/scenario` injects specific anomaly events (flash crash, liquidity drop, gap open) on top. Scheduler can be paused/resumed.
**Alternatives:**
- Scheduler-driven only — always running, no scenario control. Ticks accumulate when nobody's watching.
- REST-triggered session only — explicit start/stop, controllable, but no continuous background feed.
**Rationale:** Hybrid gives always-on realism for the push demo plus injectable drama for testing specific pipeline paths. Scenarios exercise all three `TieredContentSummariser` modes and anomaly-tagged events.
**Trade-offs:** Two tick sources means the pipeline must handle both smoothly. The scheduler rate needs to be configurable (fast for demos, slow for background). LLM-level summarisation (L3-4) runs regardless of observer presence — the arena event bridge and channel bridge are non-push consumers that must function without browsers. LLM cost is controlled by window policy intervals (~1/hour for L3, ~1/session for L4).
**Exploration:** quick
**Status:** revised (R1-06: originally added observer gating; spec review R1-01 removed it — window policies are sufficient cost control)

## D4: Arena Coupling — Event Bus Decoupling (Cache-First)

**Choice:** Pipeline publishes domain events (e.g., `TrendReversalDetected`, `RegimeChanged`) via CDI events. A separate listener bridges to the arena's `POST /api/evaluations/trigger`. The pipeline does not know about the arena. **Ordering guarantee:** The observation cache (D5) updates first, then fires the CDI domain event from the cache update callback. This eliminates the subscription-ordering race — when the arena triggers, the cache already contains the data that caused the trigger.
**Alternatives:**
- Direct invocation — pipeline calls `EvaluationResource.trigger()` internally. Tight coupling.
- REST self-call — pipeline POSTs to its own endpoint. Maximum decoupling but awkward HTTP overhead.
- Independent subscribers — cache and CDI listener both subscribe to the bus independently, with implicit ordering. Race between cache update and arena trigger.
**Rationale:** The pipeline is a self-contained data processing system. Cache-first ordering makes the data flow explicit: bus event → cache update → CDI domain event → arena trigger. No implicit subscriber ordering dependency.
**Trade-offs:** The CDI event fires from within the cache update path, creating a coupling between the observation service and the event publishing. Acceptable — the observation service IS the authoritative source of "what changed," so it's the natural place to fire domain events.
**Depends on:** D5 (observation cache is the event source), D7 (pipeline feeds the cache via bus subscriptions)
**Exploration:** quick
**Status:** revised (R1-03: cache-first ordering eliminates subscription race)

## D5: Agent Observation — Pull via PartitionedObservationService

**Choice:** The platform's `PartitionedObservationService<PriceTick, String>` maintains per-observer observations with `FsiStrategyVisibilityPolicy` controlling which levels each strategy type can see. Strategy agents pull from the service at arena trigger time. Agents remain stateless `Function<ArenaContext, CompletionStage<AgentResult>>` closures. Domain event detection (`TrendReversalDetected`, `RegimeChanged`) is handled by a separate `FsiMarketEventDetector` bean, not by the observation service — single responsibility.
**Alternatives:**
- Custom `FsiObservationCache` with `Map<String, Map<EventLevel, Object>>` — reimplements the platform's observation infrastructure with untyped maps, embeds domain coupling (strategy-level mapping) in the cache, and conflates caching with domain event detection.
- Push (continuous feed) — agents become stateful observers. Large rearchitecture of C1's agent model.
**Rationale:** `PartitionedObservationService` is an explicit platform showcase item. Using the platform type with `VisibilityPolicy` demonstrates the framework's observation infrastructure. The strategy-level mapping is externalized via `FsiStrategyVisibilityPolicy` — adding a new strategy or changing its observation levels modifies the policy, not the service.
**Trade-offs:** Observations can be stale if pipeline pauses. The `drain()` method uses `.join()` (blocking) — a constraint for concurrent agent evaluation. Acceptable — latest-value queries are the common path.
**Depends on:** D7 (pipeline feeds the service via bus subscriptions)
**Exploration:** quick
**Status:** revised (spec review R1-05: use platform PartitionedObservationService; R1-07: separate detection from caching; R1-09: externalize strategy-level mapping)

## D6: Channel Bridge — Wire Now

**Choice:** Implement `FsiChannelEventAdapter` in C2 that creates/writes to qhorus channels when Level 2+ events fire. Even without C3's deliberation agents, the channels exist and can be observed via REST.
**Alternatives:**
- Stub the hook — emit CDI domain events only, let C3 wire the channel bridge. Clean but doesn't prove the path.
- Wire with noop consumer — channels exist but nobody reads them. Full path exercised but no visible behavior.
**Rationale:** Full vertical slice philosophy. Wiring the channel bridge now means C3 can consume real, populated channels instead of bootstrapping from scratch. The bridge is also observable via qhorus REST APIs — useful for debugging and demos.
**Trade-offs:** Creates channels that have no deliberation agents until C3. The channels accumulate messages with no consumers. Acceptable — the data proves the bridge works, and C3 will add consumers.
**Depends on:** D7 (pipeline produces Level 2+ events that the bridge subscribes to)
**Exploration:** quick
**Status:** captured

## D7: Pipeline Architecture — Bus Composition

**Choice:** `EventStreamBus` is the single composition mechanism. Each `SummarisationRunner` takes an output `EventStreamBus<OUT>` in its constructor and publishes results to it. Downstream runners subscribe to upstream buses — the "chain" is bus subscriptions, not a separate wiring mechanism. External consumers (observation cache, channel bridge, push topics) subscribe to the same per-level buses alongside downstream runners. `Compactor` deduplicates at ingestion on the Level 0 bus. `KeyedSummarisationRunner` groups by instrument symbol.

**Async dispatch for LLM levels:** Levels 0-2 subscribers run synchronously on the publishing thread (microsecond computation). Level 3-4 subscribers dispatch to an async executor — the LLM call runs off the bus thread so it doesn't block upstream publishing. The `EventStreamBus.publish()` call returns immediately for async subscribers.

**Subscriber ordering:** Within a single bus, subscribers are dispatched in registration order (`CopyOnWriteArrayList`). Registration order is explicit in the pipeline builder — observation cache first, then external consumers, then downstream runners. This ordering is documented, not accidental.
**Alternatives:**
- Two explicit mechanisms (bus + separate chain) — overstates complexity; the chain IS bus subscriptions.
- Pure bus with implicit wiring — same mechanism but without explicit ordering documentation. Fragile.
**Rationale:** `SummarisationRunner` is designed to compose via `EventStreamBus`. Recognising this as a single mechanism (not two) simplifies the mental model. Explicit subscriber ordering and async dispatch for LLM levels address the backpressure gap between microsecond and seconds-latency levels.
**Trade-offs:** Async dispatch for L3-4 means downstream consumers of L3 output may see results arrive out of tick order. Acceptable — regime assessment and session narrative are temporal summaries, not per-tick responses. The `WindowPolicy.ofAge()` naturally absorbs ordering variance.
**Exploration:** quick
**Status:** revised (R1-01: single mechanism, R1-02: async dispatch for LLM levels)

## D8: Domain Types — All in api Module

**Choice:** `PriceTick`, `OHLCV`, `TrendSummary`, `RegimeAssessment`, `SessionNarrative` as records in `io.casehub.fsitrading.model` (api module). Pure Java, no framework dependencies.
**Alternatives:**
- Split by visibility — tick/bar types in api, higher-level types in app. Inconsistent boundary.
- All in app — pipeline internals only, REST exposes DTOs. Over-indirection for domain vocabulary.
**Rationale:** These types represent the trading domain's market data vocabulary. `MarketSignal` is already in api. Arena agents need `TrendSummary` and `RegimeAssessment` for observation context (D5). C3 will consume `RegimeAssessment` for deliberation triggers (D6). Pure records with no framework deps belong in the API module.
**Trade-offs:** Exposes pipeline output types as public API. Any change to these types is a breaking change for consumers. Acceptable — these are stable domain concepts, not implementation details. Types may evolve during C2 implementation; breaking changes are acceptable pre-release (no external consumers exist yet).
**Exploration:** quick
**Status:** captured

---

## Acknowledged Limitations (from decision review)

**Single-process topology (R1-08):** The entire pipeline runs in a single JVM process. `EventStreamBus` uses synchronous in-process dispatch. Observation caches are `ConcurrentHashMap` in-memory state. This is correct for the showcase purpose. Multi-node operation (if needed) is a C6/deployment concern — the pipeline's in-memory assumptions are documented, not hidden.

**Minimal panel rework (R1-09):** The `fsi-market-panel` may need rework when C5's dock-workbench composition lands. Accepted — the panel proves the push path end-to-end and the Quinoa/pages setup work carries forward.

**Channel accumulation (R1-10):** Unconsumed qhorus channel messages accumulate until C3 adds deliberation agents. Use `BoundedProjectionDecorator` to cap high-volume channels (Level 2) to the last 100 messages. Bounded accumulation is acceptable.

**PartitionedObservationService.drain() blocking (R1-07):** The `drain()` method uses `.join()` (blocking). When multiple arena agents call `drain()` concurrently, threads block on the join. This is a constraint of the existing API — the spec acknowledges it. Mitigation: the observation cache (D5) provides pre-computed latest values; `drain()` is only needed for historical window access, not the common path.
