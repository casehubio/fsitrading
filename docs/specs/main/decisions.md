# fsitrading Replan Decisions

## D1: Goal Priority

**Choice:** Showcase platform > Domain fidelity > Layer progression parity
**Alternatives:**
- Domain fidelity first — realistic trading before platform showcase, slower to impress
- Layer parity first — catch up with AML/Clinical/DevTown, methodical but doesn't leverage platform maturity
**Rationale:** The platform has matured enormously (blocks orchestration, blocks-ui 31 components, dock-workbench, push protocol). fsitrading should demonstrate what CaseHub can do at its best, with domain fidelity as the second lens and layer coverage as emergent.
**Trade-offs:** Some platform features may be showcased before they're deeply integrated into the domain model. Accept this — the showcase value outweighs premature polish.
**Exploration:** quick
**Status:** captured

## D2: Plan Structure — Vertical Slices

**Choice:** Vertical slices (each chapter delivers a working end-to-end feature: backend + orchestration + UI)
**Alternatives:**
- Capability layers — one foundation module per chapter (proven from other apps but slow to show results)
- Hybrid — group by domain scenario but layer-aware (compromise, unclear boundaries)
**Rationale:** The platform is mature enough that pulling in multiple capabilities per slice is natural. Each slice should be demonstrable as a standalone trading scenario, not an infrastructure step.
**Trade-offs:** Some foundation setup (eidos registration, CDI wiring) may need to be front-loaded into S1 even though it's not a "feature." Accept this — prerequisites fold into the first slice.
**Exploration:** quick
**Status:** captured

## D3: Slice Organisation — Domain-Forward (Approach A)

**Choice:** 6 slices organised by trading scenario: Strategy Arena, Market Pulse, Trade Deliberation, Overnight Ops, The Trading Desk, Knowledge & Compliance
**Alternatives:**
- Platform-first (Approach B) — organised by platform capability, horizontal slices
- Two-phase (Approach C) — core pipeline first, sophisticated features second
**Rationale:** Domain-forward means every slice is demonstrable ("here's what happens when a flash crash is detected overnight"). Platform capabilities are pulled in to serve each scenario. Satisfies all three goal priorities in order.
**Trade-offs:** Approach B would be more systematic for platform coverage tracking. Approach C would get a running app faster. A trades both for richer per-slice payoff.
**Exploration:** quick
**Status:** captured

## D4: Workbench Layout — Two Dock-Workbench Pages

**Choice:** Two full dock-workbench pages: Trading Desk (day-to-day strategy monitoring) and Ops Centre (overnight incident management)
**Alternatives:**
- Trading Desk only — simpler, one layout for everything
- Ops Centre only — optimised for the overnight scenario which is the strongest domain story
**Rationale:** Two pages showcase the dock-workbench capability twice with different panel arrangements. Trading Desk is position/P&L/strategy focused; Ops Centre is incident/timeline/escalation focused. Both use the 6-zone model with drag-and-drop rearrangement.
**Trade-offs:** Two layouts means more panel registration, more YAML, more testing surface. Worth it for the showcase value.
**Exploration:** quick
**Status:** captured

## D5: Orchestration — All 8 Patterns

**Choice:** Showcase all 8 blocks orchestration patterns (Supervisor, Sequence, Loop, Parallel, Voting, Debate, Conditional, HTN), each with a real trading scenario
**Alternatives:**
- Supervisor + Voting only — two highest-impact patterns, faster
- Supervisor + Voting + Debate + HTN — four patterns, deliberation + decomposition
- Workflow-shaped only (Supervisor + Sequence + Parallel + Conditional) — no deliberation
**Rationale:** Every pattern maps to a genuine trading scenario. fsitrading becomes the reference app for blocks orchestration — no other app uses all 8. Maximum platform showcase value.
**Trade-offs:** Implementation surface is large. Mitigated by distributing across slices: S1 gets Supervisor/Parallel/Voting, S2 gets Sequence/Loop, S3 gets Debate, S4 gets HTN/Conditional.
**Depends on:** D3 (slice organisation determines pattern distribution)
**Exploration:** quick
**Status:** captured

## D6: Market Data — Synthetic Through Multi-Level Summarisation

**Choice:** Synthetic data (fake/historical presented as live) flowing through blocks' multi-level summarisation pipeline. EventStreamBus → SummarisationRunner chains at 5 temporal levels (ticks → 1-min bars → 5-min trends → hourly regime → session narrative). Each strategy agent observes at its appropriate level via ObservationAccumulator.
**Alternatives:**
- Real market data via WebSocket (external dependency, more impressive but fragile)
- Synthetic only without summarisation (simple but misses the most novel platform showcase)
**Rationale:** The multi-level summarisation approach is genuinely novel — blocks' temporal hierarchy becomes a market microstructure analysis framework. Each level feeds the next. Strategy agents get context at the right granularity for their time horizon. This is the most distinctive platform showcase in the whole app.
**Trade-offs:** Synthetic data limits realism. Accept this — the pipeline architecture is the showcase, not the data quality. Real feeds can be swapped in later via the same pipeline.
**Exploration:** quick
**Status:** captured

## D7: Push Model — Full WebSocket Push

**Choice:** Full WebSocket push via pages-push EventBroadcaster for all live data (positions, P&L, market ticks, agent activity). Uses the new triggerUrl pattern for push-triggered re-fetch of paginated datasets.
**Alternatives:**
- SSE + polling hybrid — simpler backend, still responsive
- Push with replay via durable EventStore — maximum showcase but more infrastructure
**Rationale:** Full push makes the trading dashboard feel like a real trading terminal. WebSocket is bidirectional (supports triggerUrl pattern). The TopicRegistry with wildcard-aware fan-out handles per-instrument, per-strategy, and per-agent topic hierarchies naturally.
**Trade-offs:** WebSocket is more complex than SSE for the backend. The pages-push SDK handles most of the complexity.
**Exploration:** quick
**Status:** captured

## D8: Open Issues — Fold Into Slices

**Choice:** Fold existing open issues into vertical slices where they naturally fit. #14 (transactional) and #6 (CDI wiring) are prerequisites — fix at S1 start. #12 (eidos) folds into S1. #13 (quality dimensions) folds into trust routing work in S1.
**Alternatives:**
- Clean slate first — close all before starting new chapters
- Ignore — leave as-is, replan supersedes them
**Rationale:** Folding avoids a "cleanup phase" that produces no visible features. The issues land where they're naturally needed.
**Trade-offs:** S1 carries some prerequisite debt. Accept this — it's a small amount and the slice still delivers visible features.
**Exploration:** quick
**Status:** captured

## D9: Conversation Protocol — Trade Deliberation + Incident Response + Post-Mortem

**Choice:** Three uses of blocks' conversation protocol: (1) trade deliberation channels for live market events, (2) incident response channels for overnight scenarios, (3) automated post-mortem generation from ConversationRenderer after incidents close.
**Alternatives:**
- Trade deliberation only — simplest, high showcase value
- Incident response only — reserves the protocol for the strongest scenario
**Rationale:** Three uses demonstrate the protocol's versatility. The post-mortem generation (ConversationRenderer producing markdown from ConversationState) is a unique showcase — no other app does this. CommonGroundAnalyser tracking what agents agreed on + ConvergenceAnalyser detecting consensus or deadlock are directly relevant to trading decision quality.
**Trade-offs:** Three integration points means more wiring. The conversation protocol types are well-designed for reuse though — the same ConversationProjection subclass works for both deliberation and incident channels.
**Depends on:** D5 (Debate pattern uses conversation protocol)
**Exploration:** quick
**Status:** captured

## D10: Scope — 5-6 Vertical Slices

**Choice:** 6 vertical slices: S1 Strategy Arena, S2 Market Pulse, S3 Trade Deliberation, S4 Overnight Ops, S5 The Trading Desk, S6 Knowledge & Compliance
**Alternatives:**
- 3-4 slices — larger chapters, faster to plan, heavier per-slice
- 8+ slices — one per orchestration pattern, maximum granularity
**Rationale:** 6 slices balances granularity with coherence. Each slice is a meaningful trading scenario. The 8 orchestration patterns distribute naturally across 6 slices without forcing artificial boundaries.
**Trade-offs:** More slices means more issues, more branches, more overhead. 6 is the sweet spot — each slice is substantial enough to warrant its own branch but small enough to complete in 1-2 sessions.
**Depends on:** D3 (slice organisation), D5 (pattern distribution)
**Exploration:** quick
**Status:** captured

## D11: CBR for Market Events — Full 4-Step Pipeline

**Choice:** Implement the complete Retrieve → Reuse → Revise → Retain CBR cycle for market events, using neocortex's typed feature-vector similarity with trading-specific features (volatility, volume profile, time-of-day, sector, event sequence). Use `FeatureField.TimeSeries` with `DtwSpec` for price action pattern matching and `DiscreteSequence` with `EditDistanceSpec` for event sequence comparison. Wire `PlanAdapter` for incident response plan adaptation. Record outcomes via `MemoryEmitter` + `RoutingOutcomeRecorder`.
**Alternatives:**
- CBR for routing only — use CBR just for strategy selection, not for market event knowledge
- No CBR — rely on trust scoring alone for strategy selection
**Rationale:** CBR is the platform's most distinctive capability. Market events are a perfect domain for it — a flash crash at 3 AM today should retrieve the response from last month's 2 AM flash crash, adapt the response plan for current conditions (different positions, different agents available), execute, and record the outcome. TimeSeries DTW for price pattern matching and DiscreteSequence edit distance for event sequence comparison are novel applications no other app uses.
**Trade-offs:** Requires designing the feature schema, similarity specs, and plan adaptation rules. These are domain-specific and cannot be borrowed from peer apps.
**Depends on:** D3 (S6 is the CBR slice)
**Exploration:** quick
**Status:** captured

## D12: Agent Memory — Episodic + Reflective (Speculative)

**Choice:** Plan speculatively for agent memory capabilities the platform is actively building. Immediately: use `CaseMemoryStore` for strategy agent episodic memory (last N trading decisions, market context at decision time) via `EpisodicMemoryConfig`. Use `MemoryEmitter` for fire-and-forget outcome recording. Design hooks for future capabilities: goal discovery from memory (engine#808), personality dynamics (engine#857), re-planning on failure (engine#882).
**Alternatives:**
- Wait until capabilities ship — no speculative design, implement when available
- Minimal memory — only CBR, no agent episodic memory
**Rationale:** The user is actively building these capabilities. Designing the trading domain's memory schema now (what should a strategy agent remember? what constitutes a "market regime" memory? when should trust trajectory trigger personality adaptation?) means fsitrading is ready to light up as these features land. The SPI boundaries are stable — implementation will slot in.
**Trade-offs:** Speculative design may need adjustment when capabilities ship. The risk is low because we're designing to SPIs, not implementations.
**Exploration:** quick
**Status:** captured

## D13: Summarisation as Market Analysis Framework

**Choice:** Use blocks' multi-level summarisation framework not just for data aggregation but as the core market analysis architecture. Five temporal levels (tick → 1-min bar → 5-min trend → hourly regime → session narrative). `KeyedSummarisationRunner` for per-instrument grouping. `PartitionedObservationService` for per-strategy visibility. `AffordanceRenderer` for structured agent action descriptions. `ContextTracker` for LLM context window management during deliberation. `Compactor` for tick deduplication. `ChannelEventAdapter`/`Publisher` for bidirectional qhorus bridge.
**Alternatives:**
- Summarisation for data pipeline only — just aggregate ticks, don't use for market analysis
- Custom market analysis — build trading-specific analysis outside blocks' framework
**Rationale:** The level hierarchy IS the analysis. Each level produces a different kind of market insight. Agents operating at different time horizons naturally subscribe to different levels. This is the most novel use of blocks summarisation — no other app treats summarisation as an analysis framework.
**Trade-offs:** Tight coupling to blocks' summarisation model. If blocks changes the APIs, the market analysis pipeline needs updating. The APIs are stable.
**Depends on:** D6 (market data decision)
**Exploration:** quick
**Status:** captured

## D14: Platform Pattern Coverage — Comprehensive Reuse Audit

**Choice:** Systematically map ALL reusable patterns across blocks (orchestration, summarisation, conversation, channel, routing, accountability) and blocks-ui (31+ components) to trading scenarios. No pattern goes unused without a documented reason. Target: ~80% of blocks-ui components used, all 8 orchestration patterns, full summarisation pipeline, full conversation protocol, all routing strategies, all accountability listeners.
**Alternatives:**
- Cherry-pick — use only the patterns that are obviously relevant
- Organic discovery — let patterns emerge as implementation proceeds
**Rationale:** fsitrading should be the reference app for the platform. A comprehensive audit ensures no capability is overlooked and creates the definitive pattern catalogue for future apps to reference.
**Trade-offs:** Some patterns may feel forced if the trading scenario doesn't naturally need them. Mitigated by the audit: if a pattern doesn't fit, document why — that's also valuable.
**Depends on:** D5 (all patterns), D13 (summarisation)
**Exploration:** quick
**Status:** captured
