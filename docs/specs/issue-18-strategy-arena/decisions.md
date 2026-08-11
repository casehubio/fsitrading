# C1 Strategy Arena — Decisions

## D1: Arena Orchestration Model — Blocks Patterns Replace Case Engine

**Choice:** Blocks orchestration patterns (`Patterns.supervisor()` → `Patterns.parallel()` → `Patterns.voting()`) are the primary orchestrator for the Strategy Arena. The existing `StrategyEvaluationCaseDefinition` is superseded for the multi-strategy flow.
**Alternatives:**
- Embed — blocks orchestration inside a case binding's capability. Awkward nesting; case model assumes one capability → one result, fights concurrency.
- Layer — arena wraps individual per-strategy case instances. Adds indirection without value; individual cases would be trivial (one binding, no goals), and `LedgerExecutionListener` already provides the same audit guarantees.
**Rationale:** The arena is a concurrent multi-agent evaluation with consensus — triage, parallel dispatch, voting. Blocks patterns are purpose-built for this. The case engine's reactive binding model is designed for adaptive lifecycle management (correct for C4 Overnight Ops), not deterministic multi-agent orchestration. Human approval gates for high-risk consensus decisions call the engine's WorkItem service directly — no case lifecycle needed.
**Trade-offs:** Loses case engine's declarative milestone/goal tracking for the arena flow. Acceptable — the arena's completion condition is a single check (consensus or deadlock), not a complex goal expression. The existing `StrategyEvaluationCaseDefinition` becomes dead code for the arena; may be retained for a simplified single-strategy path or removed entirely.
**Exploration:** deep-analysis
**Status:** captured

## D2: Strategy Agent Implementation — Direct Blocks Agents

**Choice:** Strategy agents implement blocks' agent interface directly. The `StrategyEvaluator` SPI is retired. Each `StrategyType` gets a blocks agent class that receives `AffordanceRenderer` context natively and returns a `TradeDecision`.
**Alternatives:**
- Adapt — wrap `StrategyEvaluator` implementations behind an adapter that translates blocks context → SPI parameters → domain logic. Preserves the SPI as a domain contract.
**Rationale:** The current `StrategyEvaluator` signature is too thin for arena context (positions, price history, affordances). Keeping it means either cramming into `Map<String, Object>` or evolving the SPI to match blocks' context — either way the adapter is translating without a consumer on the other side. No external repo imports `fsitrading-api` for this SPI. fsitrading depends on blocks APIs the same way it depends on JPA or CDI — application composing platform library. Domain logic stays in fsitrading; blocks provides orchestration primitives.
**Trade-offs:** fsitrading's strategy logic depends on blocks API types. Acceptable — this is the normal app → platform dependency direction, not problematic coupling. If blocks APIs are well-typed and composable, depending on them is a strength.
**Depends on:** D1
**Exploration:** deep-analysis
**Status:** captured

## D3: Routing Strategy Composition — Platform Bridge

**Choice:** `FsiArenaRouting` implements blocks' `RoutingStrategy<ArenaContext>` as a multi-select adapter. It composes all 6 routing strategies: 4 `RoutingSignalProvider` beans via `RoutingSignalAssembler` (public SPI in `io.casehub.api.spi.routing`) plus direct invocation of `LlmAgentRoutingStrategy` and `CbrAgentRoutingStrategy` (`AgentRoutingStrategy` implementations). LLM/CBR single-select results are converted to per-candidate binary scores (1.0/0.0) and blended with the 4 continuous signal-provider scores. All candidates scoring above a configurable threshold are selected. The adapter translates between blocks types (`RoutingCandidate`, `RoutingContext<ArenaContext>`) and engine routing types (`AgentCandidate`, `AgentRoutingContext`). fsitrading provides domain configuration (`DispositionProfile` per strategy type, `FsiCbrOutcomeWeights`, `FsiTrustRoutingPolicyProvider`) but no custom scoring logic.
**Alternatives:**
- `ComposableAgentRoutingStrategy` bridge — single-select (picks one winner), engine-internal package (`io.casehub.engine.internal.routing`). Would require repeated calls or internal API access for multi-select. **Rejected: impedance mismatch.**
- Scored ensemble — implement custom composition: run all signal providers, weight scores, merge. Reimplements what `RoutingSignalAssembler` already does.
- Tiered fallback — LLM primary, others supplementary. Underutilises the signal providers and doesn't showcase all strategies equally.
**Rationale:** `RoutingSignalAssembler` already does the hard work — CDI discovery of signal providers, per-candidate scoring, score clamping. The arena only adds threshold-based multi-select on top. This avoids depending on the engine's internal `ComposableAgentRoutingStrategy` (which is single-select and in an internal package). Type translation (`RoutingCandidate` → `AgentCandidate`, `ArenaContext` → `AgentRoutingContext`) is non-trivial but well-defined — documented explicitly in §3.1.
**Trade-offs:** The adapter fabricates engine-specific fields (`caseId`, `tenancyId`, `runningJobs`) that don't have natural blocks equivalents. These are synthetic but safe — no engine runtime interprets them. `LlmAgentRoutingStrategy` and `CbrAgentRoutingStrategy` are `AgentRoutingStrategy` implementations (single-select), not `RoutingSignalProvider` (per-candidate scoring). `FsiArenaRouting` bridges this by converting single-select results to binary per-candidate scores for blending — a lossy conversion (no confidence gradation), but the LLM/CBR selection still influences which candidates clear the multi-select threshold.
**Depends on:** D1, D2
**Exploration:** quick
**Status:** captured

## D4: Arena Trigger — REST-Only for C1

**Choice:** The arena is triggered exclusively via `POST /api/evaluations/trigger` with a market signal payload. No auto-triggering on market events. C2 (Market Pulse) adds event-driven triggering when the summarisation pipeline lands.
**Alternatives:**
- Event-driven — auto-trigger on MarketEventEntity persistence. More realistic but adds complexity before the orchestration is proven.
- Both — event-driven with REST override. Right eventually, but premature for C1.
**Rationale:** C1's job is to get the orchestration right: supervisor → parallel → voting → execute. Explicit REST triggering makes it testable and controllable. The REST endpoint becomes the integration point for C2's event-driven pipeline later.
**Trade-offs:** Not realistic for a production trading system — real systems react to events, not REST calls. Acceptable for C1's scope; C2 adds the event layer.
**Exploration:** quick
**Status:** captured

## D5: Voting — Compositional Aggregation

**Choice:** The arena has no opinion about single vs. multi-instrument. Strategies return `List<TradeDecision>` (any instruments). Aggregation is a pluggable `AggregationStrategy<ArenaContext>` on the Voting pattern. The default implementation (`FsiMajorityVoteByInstrument`) groups decisions by instrument and applies `MajorityVote` per group. Alternative strategies (weighted-by-trust, confidence-scored) can be swapped without changing the arena composition.
**Alternatives:**
- Single-instrument arena — trigger specifies one instrument, voting is flat. Simple but bakes instrument semantics into the flow.
- Per-instrument voting as a hardcoded step — groups by instrument but as fixed logic, not a pluggable strategy.
**Rationale:** Blocks' `AggregationStrategy<T>` is already pluggable via `.strategy()` on `VotingBuilder`. Instrument grouping is a property of the aggregation, not the arena. This showcases blocks' composability — the same arena composition works regardless of how decisions are aggregated.
**Trade-offs:** Slightly more abstract than a hardcoded single-instrument model. The `FsiMajorityVoteByInstrument` implementation needs to handle edge cases (ties per instrument, mixed sides, no-opinion agents).
**Depends on:** D1
**Exploration:** quick
**Status:** captured

## D6: Post-Voting Execution — Conditional Pattern Composition

**Choice:** Risk assessment and human approval gates are expressed as blocks patterns within the arena composition, not as procedural post-processing. `Patterns.conditional()` routes HIGH-risk decisions to `AgentRef.human(approvalTemplate)` and LOW/MEDIUM-risk to direct execution via `AgentRef.external()`. The entire arena is a pure composition of blocks patterns — no "ArenaOrchestrator" service class. The arena is a composed `ExecutionModel` built once at startup, invoked per REST trigger.
**Alternatives:**
- Inline post-processing — procedural code after voting completes. Simpler but the risk gate step is invisible to accountability listeners.
**Rationale:** Keeping the entire flow in blocks pattern composition means all 3 accountability listeners (`LedgerExecutionListener`, `EventLogListener`, `MetricsListener`) capture every step including the risk gate. The full composition is: Sequence [ Supervisor (triage) → Parallel (evaluate) → Voting (aggregate) → ExternalAgent (risk assess) → Conditional (risk gate) → ExternalAgent (execute) ]. No step is outside the accountability boundary.
**Trade-offs:** The Sequence composition requires the context type `T` to flow through all steps, accumulating state. The `ArenaContext` type must carry triage results, evaluations, consensus, risk assessment, and execution results.
**Depends on:** D1, D5
**Exploration:** quick
**Status:** captured
