# fsitrading C3 — Trade Deliberation Decisions

## D1: Deliberation Trigger

**Choice:** Combination — significant events (B) trigger automatic deliberation, plus manual trigger endpoint (C) always available
**Alternatives:**
- Every detected market event — simple but noisy and expensive (LLM calls per debate round)
- Manual only — developer-controlled but doesn't exercise the pattern in realistic conditions
**Rationale:** Regime changes to VOLATILE or momentum reversals with high magnitude warrant real deliberation. Manual trigger enables demos and testing without waiting for synthetic data to produce significant events.
**Trade-offs:** Threshold tuning — too sensitive means noise, too conservative means the pattern rarely fires. Mitigated by making thresholds configurable.
**Exploration:** quick
**Status:** captured

## D2: Deliberation Participants

**Choice:** Reuse C1's strategy agents — same agents that evaluate in the arena also deliberate on channels, selected by the supervisor based on event relevance
**Alternatives:**
- Separate deliberation-specialist agents (devil's advocate, risk assessor, market historian) — richer debate personas but redundant with existing agents and more to build
**Rationale:** Strategy agents already have distinct dispositions (aggressive/conservative, trend-following/mean-reverting) creating natural debate tension. The supervisor's selection mechanism from C1 picks the relevant subset per event. Avoids a parallel agent taxonomy.
**Trade-offs:** Strategy agents were designed for individual evaluation, not collaborative debate. Their prompts need adaptation for debate mode (raising points, responding to others). This is prompt engineering, not new infrastructure.
**Depends on:** D1 (trigger mechanism determines what event context the supervisor uses for selection)
**Exploration:** quick
**Status:** captured

## D3: Deliberation Output

**Choice:** Both — commitment for accountability, TradeDecision for execution. Commitment lifecycle tracks whether the agreed trade was fulfilled/declined/expired, feeding back into trust scoring.
**Alternatives:**
- Commitment only — accountability trail but no direct execution path
- TradeDecision only — simpler but skips the commitment lifecycle and its 7-state accountability trail
**Rationale:** The commitment gives MiFID II Art.17 decision attribution (who agreed, when, what was the convergence state). The TradeDecision feeds the existing C1 order execution pipeline. The commitment's terminal states (FULFILLED/FAILED/DECLINED) naturally close the loop for trust scoring — did the agreed trade actually work?
**Trade-offs:** Two artifacts per deliberation adds complexity. Mitigated by the commitment being the authoritative record and the TradeDecision being derived from it.
**Depends on:** D2 (participating agents are the commitment's obligors)
**Exploration:** quick
**Status:** captured

## D4: Sub-Task Handler Mode

**Choice:** LLM-interpreted computation — all three handlers go through `ChannelAgentDispatcher.dispatch()` which unconditionally calls the agent provider. Correlation and volume handlers compute raw data in `prepareTask()` and embed it in the LLM prompt for contextual interpretation; news check is fully LLM-powered.
**Alternatives:**
- Rule-based computation for quantitative handlers — faster and deterministic, but `ChannelAgentDispatcher.dispatch()` unconditionally calls the agent provider for every handler (no way to skip the LLM call)
- All fully LLM-powered (no pre-computation) — flexible but wastes LLM capacity on arithmetic
**Rationale:** `ChannelAgentHandler` is LLM-first by design — the dispatcher's agent provider call is mandatory. Computational handlers leverage this by embedding pre-computed statistics in the prompt, getting natural language interpretation that integrates coherently with the debate. LLM-interpreted findings produce richer debate contributions than raw numbers.
**Trade-offs:** Every sub-task incurs LLM latency and non-determinism, even for quantitative checks. Mitigated by the pre-computation step — the LLM interprets rather than calculates, reducing error surface.
**Depends on:** D3 (sub-task findings inform the deliberation that produces the commitment/TradeDecision)
**Exploration:** quick
**Status:** captured

## D5: Execution Backend

**Choice:** Choreographed (ChoreographedDriver + FsiDeliberationStateObserver as EventSource) — debate loop waits for channel events between iterations, enabling mid-debate market data updates, async human escalation, and sub-task finding delivery
**Alternatives:**
- Reactive (OrchestratedDriver) — synchronous turn orchestration, simpler but no external event integration
- Reactive now, choreographed later — defers complexity but adds no value when FsiDeliberationStateObserver already provides the EventSource
**Rationale:** FsiDeliberationStateObserver implements both MessageObserver (qhorus) and EventSource (blocks) — the choreographed backend is almost free. Channel-based debate IS event-driven by nature: each agent's response is a channel event that wakes the next iteration. This is the primary platform showcase for C3.
**Trade-offs:** Choreographed is harder to unit test (event timing, async wake-up). Mitigated by blocks' existing test infrastructure (RecordingChannelBackend, DriverEvent.signal() for synthetic events).
**Depends on:** D2 (agents are ChannelAgent refs), D4 (sub-task findings arrive as channel events)
**Exploration:** quick
**Status:** captured

## D6: Channel Lifecycle

**Choice:** One channel per deliberation session — fresh qhorus channel created on trigger, closed on convergence or timeout. Channel name: `fsi-deliberation-{instrument}-{timestamp}`.
**Alternatives:**
- One persistent channel per instrument — reuse with reset(), but state bleeds without careful management
- One shared channel for all instruments — minimal overhead but requires per-instrument projection scoping for no benefit
**Rationale:** Each deliberation is a discrete event with a clear start (market event) and end (convergence/timeout). Fresh channel gives clean ConversationState, clean commitment scope, clean audit trail. Completed channels are queryable via REST.
**Trade-offs:** Channel creation overhead per deliberation. Negligible — qhorus channels are lightweight in-memory structures backed by JPA when persistence is needed.
**Depends on:** D1 (trigger creates the channel), D5 (FsiDeliberationStateObserver observes the channel)
**Exploration:** quick
**Status:** captured

## D7: Persistence Model

**Choice:** Snapshot persistence — store a DeliberationRecord JPA entity with final ConversationState summary, CommonGroundState, ConvergenceSignal, outcome (commitment ID, trade decision ID), participants, instrument, timestamps. One row per deliberation. Requires Flyway migration.
**Alternatives:**
- Qhorus channel persistence only — replay projection from stored messages on demand, no new tables but slow for listing
- Both (qhorus + snapshot) — correct but over-engineered; qhorus message persistence is the raw audit trail, snapshot is sufficient for the REST API
**Rationale:** REST endpoints need fast queries for listing and filtering deliberations. Replaying projections from raw messages is O(messages) per deliberation. A snapshot row is O(1). The snapshot captures the computed outcome — the raw messages remain in qhorus for deep audit if needed.
**Trade-offs:** Snapshot can drift from raw messages if the projection logic changes. Acceptable for a pre-release app — snapshot is the operational view, raw messages are the audit view.
**Depends on:** D3 (outcome includes commitment ID and TradeDecision ID), D6 (one record per channel lifecycle)
**Exploration:** quick
**Status:** captured
