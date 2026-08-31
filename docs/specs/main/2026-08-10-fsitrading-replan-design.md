# fsitrading Replan — Platform-Forward Vertical Slices

**Date:** 2026-08-10
**Scope:** Full replan of fsitrading development roadmap based on current platform capabilities
**Supersedes:** Original chapter-per-layer plan (Chapters 1-3 implemented, 4+ obsolete)

---

## Summary

Rewrite of fsitrading's development plan from scratch. The CaseHub platform has matured significantly — blocks provides a full agentic orchestration framework with 8 patterns, blocks-ui has 31+ domain components, pages has IntelliJ-style dock workbenches with drag-and-drop tool windows and floating panels. The old layer-per-chapter model is replaced by **vertical slices** — each chapter delivers a working end-to-end trading scenario with backend orchestration + UI panels.

**Priority order:** Showcase platform → Domain fidelity → Layer progression parity

**Structure:** 7 chapters (C0 prerequisite fixes + 6 vertical slices), targeting ~160 reusable types across blocks/blocks-ui/pages and 25+ blocks-ui components across two dock-workbench pages.

---

## What Exists (Chapters 1-3)

Implemented in June 2026. Working vertical slice: domain model, order lifecycle, position tracking, ledger integration, trust scoring.

- Domain model: `TradeDecision`, `Instrument`, 7 strategy types, 7 market event types, order lifecycle enums
- `StrategyEvaluator` SPI for pluggable strategy implementations
- Order lifecycle: create from decision, fill with price, status tracking
- Position management: quantity tracking, average cost, realized P&L
- Tamper-evident audit: `StrategyEvaluationLedgerEntry`, `OrderExecutionLedgerEntry` with `causedByEntryId` chains
- Trust scoring: `PnlAttestationService` — SOUND/FLAGGED attestations from P&L outcomes, Bayesian Beta via foundation
- Case engine: `StrategyEvaluationCaseDefinition` with capabilities, goals, milestones, human approval gate
- Synthetic market data, 6 REST endpoints, dual-datasource (H2 dev / PostgreSQL prod)

**Open issues folded into slices:** #6 (CDI wiring) → C0, #14 (transactional) → C0, #12 (eidos registration) → C1, #13 (quality dimensions) → C1. Infrastructure issues (#1-#5, #9-#11) triaged in C0.

**Note:** This spec is a roadmap. Each chapter (C0-C6) requires its own focused design spec before implementation — comparable to the existing `docs/specs/2026-06-30-chapter3-trust-scoring-design.md`.

---

## Chapter Overview

| Chapter | Name | Domain scenario | Primary platform showcase | Orchestration patterns |
|---|---|---|---|---|
| **C0** | Foundation Fixes | — | — | — |
| **C1** | Strategy Arena | Multiple strategies evaluate a market signal, get trust-routed, results aggregated | blocks orchestration, all 6 routing strategies, accountability listeners, eidos | Supervisor, Parallel, Voting |
| **C2** | Market Pulse | Synthetic ticks flow through 5-level summarisation → agent observations | blocks summarisation (full pipeline), pages-push WebSocket, channel bridges | Sequence, Loop |
| **C3** | Trade Deliberation | Market event → specialist agents deliberate → convergence → execute | blocks conversation protocol, epistemic common ground, convergence detection | Debate |
| **C4** | Overnight Ops | Anomaly → case → HTN decomposition → oversight gates → SLA escalation | engine case model, work items, approval gates, SLA, notifications | HTN, Conditional |
| **C5** | The Trading Desk | Two dock-workbench pages composing all panels from C1-C4 | dock-workbench, floating/popout, layout persistence, full push | (all accessible from UI) |
| **C6** | Knowledge & Compliance | CBR for market events, automated post-mortem, compliance grid, GDPR | neocortex CBR (DTW, edit distance), PlanAdapter, ConversationRenderer | — |

---

## Chapter 0 — Foundation Fixes

**Goal:** Clean prerequisite debt and add new dependencies so C1 starts clean. No new domain features.

**Scope:**
- **#6** — Remove `-deployment` deps, add Jandex arc config, fix `application.properties` for CDI bean discovery
- **#14** — Add `@Transactional` to `SimulatedOrderExecutor.executeDecision()` for dual-datasource atomicity
- **Maven dependencies** — Add `casehub-blocks`, `casehub-neocortex-memory-api`, `casehub-neocortex-memory`, `casehub-neocortex-memory-inmem` (test scope) to `pom.xml`. Required by C1 (orchestration, routing, memory) and C6 (CBR).
- **ARC42STORIES.MD** — Rewrite to reflect the new vertical-slice plan. Preserve C1-C3 "Done" history. Replace §4.3+ (journey/chapter sequencing, layer taxonomy, Layer×Chapter matrix) with the new C0-C6 structure.
- **Triage open infrastructure issues** — #1/#9 (CI workflow, duplicates), #2 (parent BOM), #3/#11 (build infra, duplicates), #4/#10 (issue-workflow, duplicates), #5 (ARC42STORIES stub — superseded by rewrite). Close obsolete, fold relevant into C0.
- Verify build: `JAVA_HOME=$(/usr/libexec/java_home -v 26) mvn --batch-mode install`

**Not in scope:** #12 (eidos registration) folds into C1. #13 (quality dimensions) folds into C1.

---

## Chapter 1 — Strategy Arena

**Scenario:** A market signal arrives. The system triages which strategy agents are relevant, dispatches them concurrently, aggregates their trade decisions via voting, and audits every routing and execution decision to the ledger.

### 1.1 Agent Identity — eidos Registration (closes #12)

Each `StrategyType` gets an `AgentDescriptor` registered in the eidos `AgentRegistry`:

| StrategyType | Capabilities | Model family | Persona |
|---|---|---|---|
| MOMENTUM | momentum, trend-analysis | rule | momentum@v1 |
| MEAN_REVERSION | mean-reversion, statistical | rule | mean-reversion@v1 |
| STATISTICAL_ARBITRAGE | statistical-arbitrage, pairs | rule | statistical-arbitrage@v1 |
| MARKET_MAKING | market-making, liquidity | rule | market-making@v1 |
| EVENT_DRIVEN | event-driven, news | rule | event-driven@v1 |
| PORTFOLIO_REBALANCE | portfolio-rebalance, allocation | rule | portfolio-rebalance@v1 |
| OVERNIGHT_RISK_MANAGEMENT | overnight-risk, defensive | rule | overnight-risk-management@v1 |

Each descriptor also carries a `DispositionProfile` for `DispositionAwareRouting`:

| Axis | Momentum | Mean Reversion | Market Making |
|---|---|---|---|
| Risk appetite | aggressive | moderate | conservative |
| Time horizon | medium | medium | short |
| Market preference | trending | range-bound | liquid |
| Reaction speed | moderate | slow | fast |

### 1.2 Quality Dimension Scores (closes #13)

Three initial trust dimensions on P&L attestations:

| Dimension | What it measures | Scoring |
|---|---|---|
| `return-magnitude` | Size of win/loss relative to position | `min(1.0, |realizedPnl| / closedNotional)` |
| `hold-period-efficiency` | P&L per unit time held | Normalized `realizedPnl / holdPeriodMinutes` |
| `risk-adjusted-return` | Return relative to volatility exposure | `realizedPnl / (position × recentVolatility)` |

### 1.3 Orchestration — Three Patterns

**Supervisor (triage):** `Patterns.supervisor()` with the full agent roster. LLM evaluates signal characteristics (instrument, event type, magnitude, time of day) and selects which strategy types should respond.

**Parallel (evaluation):** `Patterns.parallel()` dispatches all selected strategies concurrently. Each receives context via `AffordanceRenderer` — structured descriptions of instruments, positions, and allowed actions.

**Voting (consensus):** `Patterns.voting()` with `MajorityVote` aggregation on same-instrument decisions. Ties → `Deadlocked` → escalate to human trader.

The three patterns compose via `AgentRef.composed()`.

### 1.4 Routing Strategies — All Six

| Strategy | Role in trading |
|---|---|
| `LlmAgentRoutingStrategy` | LLM reasoning: "given this signal, which strategies should respond?" |
| `CbrAgentRoutingStrategy` | "Last time we saw a similar signal, which strategies performed well?" |
| `DispositionAwareRouting` | Match strategy personality to market conditions |
| `PlanCompositionAnalyser` | Multi-step plan outcome scoring |
| `PredecessorAnalyser` | What worked after the previous step |
| `CoordinationSignalProvider` | Which strategy combinations work well together |

Plus `CbrRoutingPromptSection` injecting historical outcomes into the LLM routing prompt.

`FsiCbrOutcomeWeights` implements `CbrOutcomeWeights` with P&L-weighted outcomes. `FsiTrustRoutingPolicyProvider` implements trust routing policy with per-strategy thresholds (minimum trust: 0.4, bootstrap: 10 observations, quality floor: 0.3).

### 1.5 Accountability — Three Listeners

| Listener | Purpose |
|---|---|
| `LedgerExecutionListener` | EU AI Act Art.12 + MiFID II Art.17 compliance-grade audit |
| `EventLogListener` | Operational event stream for debugging |
| `MetricsListener` | OTel histograms — strategy duration, routing counters, failure rates |

### 1.6 AffordanceRenderer

Strategy agents receive structured context via `AffordanceRenderer`:

```
== AAPL (Apple Inc, EQUITY, NASDAQ) ==
  Position: +100 @ $182.30 avg (unrealized P&L: +$320)
  Last price: $185.50 (↑ 1.2% today)
  Actions: BUY (limit/market, max 400), SELL (limit/market, max 100), HOLD
  Constraints: max position 500 shares, daily loss limit $5,000
```

Each instrument is an `ObservableEntity` with `Affordance` entries. `renderEntities()` and `renderActionVocabulary()` produce the text for agent system prompts.

### 1.7 Agent Memory

**Write:** `MemoryEmitter.emit()` records each trade decision to `CaseMemoryStore` — features, solution, outcome. Fire-and-forget.

**Read:** `EpisodicMemoryConfig` on the case definition injects recent N trading memories into agent evaluation context.

### 1.8 UI Panels

| Panel | Component | Data source |
|---|---|---|
| Strategy list | `list-pane` | `GET /api/strategies` |
| Strategy detail | `detail-pane` | Selection event |
| Trust scores | `trust-score-panel` | `GET /api/trust/strategies` + TrendSourceMixin |
| Trust composite | `trust-workbench` | Composes score + routing history + feedback |
| Routing explanation | `routing-rationale` | `GET /api/routing/decisions/latest` |
| Post-trade trust delta | `trust-feedback-display` | pages-event on trade completion |
| KPI metrics | `kpi-metric-row` | `GET /api/kpis` |
| Trust routing config | `preferences-editor` | `GET/PUT /api/preferences/trust-routing` |

### 1.9 New REST Endpoints

| Method | Path | Purpose |
|---|---|---|
| `GET` | `/api/routing/decisions` | Recent routing decisions with rationale |
| `GET` | `/api/routing/decisions/latest` | Most recent routing decision |
| `GET` | `/api/kpis` | Aggregated KPIs (P&L, win rate, volume, positions) |
| `GET/PUT` | `/api/preferences/trust-routing` | Trust routing thresholds per strategy |
| `POST` | `/api/evaluations/trigger` | Trigger Strategy Arena evaluation |

---

## Chapter 2 — Market Pulse

**Scenario:** Synthetic market ticks flow through a 5-level summarisation pipeline. Each level produces a different kind of market insight. Strategy agents observe at the granularity matching their time horizon.

### 2.1 Summarisation Architecture

Blocks' temporal hierarchy IS the market analysis. `SummarisationRunner` chains connect five levels:

```
Level 0: Raw Ticks → Level 1: 1-Min Bars → Level 2: 5-Min Trends
  → Level 3: Hourly Regime → Level 4: Session Narrative
```

### 2.2 Level Definitions

| Level | EventLevel | WindowPolicy | Summariser | Output |
|---|---|---|---|---|
| 0 | tick (0) | N/A | Pass-through | PriceTick |
| 1 | bar-1m (1) | ofAge(60_000) | SyncSummariser | OHLCV |
| 2 | trend-5m (2) | ofAge(300_000) | SyncSummariser | TrendSummary(direction, momentum, volatility, volumeProfile) |
| 3 | regime-1h (3) | ofAge(3_600_000) | LlmContentSummariser | RegimeAssessment(TRENDING/MEAN_REVERTING/VOLATILE/QUIET) |
| 4 | narrative (4) | ofAge(28_800_000) | LlmContentSummariser (APPEND) | Free-text session narrative |

Levels 1-2: pure computation, microsecond latency. Levels 3-4: LLM-synthesised, seconds latency.

### 2.3 Per-Instrument Grouping

`KeyedSummarisationRunner<String, PriceTick, OHLCV>` groups by instrument symbol. Each group has independent `EventAccumulator` and window policy evaluation.

At Level 4, `TieredContentSummariser` switches behavior: ≤5 instruments → verbatim bullet points, 6-20 → heuristic grouping by sector, 20+ → LLM-synthesised cross-instrument narrative.

### 2.4 Agent Observation

`PartitionedObservationService<PriceTick, String>` — per-strategy visibility. `TieredObservationRenderer` renders at appropriate granularity:

| Strategy type | Observation level | Renderer tier |
|---|---|---|
| Market making | 0-1 | Verbatim |
| Statistical arbitrage | 1-2 | Verbatim/Grouped |
| Momentum | 2-3 | Grouped |
| Event-driven | 2-3 + filtered Level 0 | Grouped |
| Portfolio rebalance | 3-4 | Summarised |
| Overnight risk management | 2-4 | Summarised |

### 2.5 Pipeline Components

- **Compactor:** Dedup same-instrument/same-second ticks. Tag >3σ deviation as anomalous.
- **ChannelEventAdapter:** Bridge Level 2+ events to qhorus channels for C3 deliberation.
- **ChannelEventPublisher:** Bridge agent conclusions back to event bus.
- **ChannelMessageMeta:** Sentinel `##FSI##`, keys: LEVEL, INSTRUMENT, EVENT_TYPE.
- **BoundedProjectionDecorator:** Cap high-volume channels (Level 0-1) to last 100 messages.

### 2.6 Orchestration — Sequence and Loop

**Sequence:** `Patterns.sequence()` chains the pipeline steps via `AgentRef.external()`.

**Loop:** `Patterns.loop()` with `exitCondition(state -> state.marketClosed || state.breachDetected)` for continuous monitoring.

### 2.7 Synthetic Data Enhancement

Enhanced `SyntheticMarketDataProvider`: U-shaped intraday volume profile, configurable anomaly injection (flash crash, liquidity drop, gap open), sparse overnight ticks. Exercises all three `TieredContentSummariser` modes.

### 2.8 Pages Push

`EventBroadcaster` topic hierarchy: `market/ticks/{instrument}`, `market/bars/{instrument}`, `market/trends/{instrument}`, `market/regime/{instrument}`, `market/narrative`. Wildcard support via `TopicRegistry`. `triggerUrl` pattern for push-triggered REST re-fetch of paginated datasets.

### 2.9 New REST Endpoints

| Method | Path | Purpose |
|---|---|---|
| `GET` | `/api/market-data/bars/{instrument}` | Historical 1-min OHLCV bars (paginated) |
| `GET` | `/api/market-data/trends/{instrument}` | Recent 5-min trend summaries |
| `GET` | `/api/market-data/regime/{instrument}` | Current regime assessment |
| `GET` | `/api/market-data/narrative` | Latest session narrative |
| `POST` | `/api/market-data/scenario` | Inject market scenario for demo/testing |

### 2.10 UI Panels

Custom `fsi-market-panel` via `registerPanel()` — `PagesTimeseries` for price charts, `PagesMetric` for spot values, `PagesBadge` for regime badges. Subscribes to `market/*` WebSocket topics.

---

## Chapter 3 — Trade Deliberation

**Scenario:** Significant market event detected. Specialist agents deliberate on a qhorus channel. Epistemic common ground tracks agreement. Convergence detection determines when to act.

### 3.1 Deliberation Flow

```
Market event (from C2 ChannelEventAdapter)
  → Create qhorus channel → ChannelAgentDispatcher routes to handlers
  → Agents raise/respond (RAISE/AGREE/COUNTER/DISPUTE/QUALIFY)
  → CommonGroundAnalyser: established / pending / disputed
  → ConvergenceAnalyser: CONSENSUS → execute | DEADLOCK → escalate | DIMINISHING_RETURNS → execute with reduced confidence
```

### 3.2 Conversation Protocol

**FsiConversationProjection** — extends `ConversationProjection`. Sentinel: `##FSI##`. Dispatches on metadata entry types. Full `ConversationFold` operations: createPoint, respondToPoint, reprioritisePoint, flagHuman, addMemo, requestSubTask, completeSubTask.

### 3.3 Epistemic Common Ground

`CommonGroundAnalyser` with composed epistemic rules: `explicitAcknowledgement(2).or(tacitAcceptance(3))` — either 2 explicit acks OR 3 rounds of silence establishes a fact.

| Status | Trading meaning |
|---|---|
| ESTABLISHED | Actionable — all agents agree |
| PENDING | Raised, awaiting confirmation |
| DISPUTED | Agents disagree — needs resolution |

### 3.4 Convergence Detection

`ConvergenceAnalyser` with composite policy: `structural(0.8, 3)` + `commonGroundRatio(0.7, 0.4)`.

| State | Trading action |
|---|---|
| CONSENSUS | Execute agreed trade decision |
| DEADLOCK | Escalate to human trader (FLAG_HUMAN) |
| DIMINISHING_RETURNS | Execute best-available, reduced confidence |

### 3.5 Orchestration — Debate Pattern

`Patterns.debate()` with `debaters(strategyAgents...)`, `maxRounds(10)`, `convergence(convergenceTermination)`. `ConvergenceTermination` bridges `CommonGroundAnalyser` + `ConvergenceAnalyser`.

### 3.6 Channel Sub-Tasks

`ChannelAgentDispatcher` routes sub-task requests to `ChannelAgentHandler` implementations: `CorrelationCheckHandler`, `VolumeAnalysisHandler`, `NewsCheckHandler`. First-match routing. Results as `SUB_TASK_FINDING` entries.

### 3.7 Context Management

`ContextTracker` monitors LLM context usage. At 80%, triggers `ChannelSummariser` compression. `context-gauge` displays live.

### 3.8 Trade Commitments

Agreed decisions expressed as `Commitment` on the qhorus channel. 7-state model: OPEN → ACKNOWLEDGED → FULFILLED/FAILED/DECLINED/DELEGATED/EXPIRED.

### 3.9 UI Panels

| Panel | Component | Data source |
|---|---|---|
| Deliberation feed | `channel-activity` | qhorus channel WebSocket |
| Commitment lifecycle | `commitment-viz` | Commitment state events |
| Deliberation timeline | `blocks-timeline` (commitment strategy) | Commitment lifecycle events |
| Context usage | `context-gauge` | `ContextTracker.snapshot()` via SSE |

### 3.10 New REST Endpoints

| Method | Path | Purpose |
|---|---|---|
| `GET` | `/api/deliberations` | Recent deliberations with convergence status |
| `GET` | `/api/deliberations/{channelId}/state` | Current ConversationState |
| `GET` | `/api/deliberations/{channelId}/common-ground` | CommonGroundState |
| `POST` | `/api/deliberations/trigger` | Manually trigger deliberation |

---

## Chapter 4 — Overnight Ops

**Scenario:** Anomaly detected off-hours. Case created. HTN decomposes response. Oversight gates high-risk actions. SLA escalation to on-call trader.

### 4.1 Case Model

`OvernightIncidentCaseDefinition` extending `CaseHub` (Java DSL). Milestones: DETECTED → CLASSIFIED → RESPONDED → VERIFIED → CLOSED. Bindings for detect, classify, triage (HTN root), respond (Conditional), verify, review (WorkItem). Goals: capital-preserved, positions-safe, sla-met. `CbrConfig`: topK=5, minSimilarity=0.6, temporalDecayHalfLifeDays=90.

### 4.2 Orchestration — HTN

`Patterns.htn()` with compound root task "handle-overnight-incident". Three decomposition methods gated by severity:

- CRITICAL: emergency-halt → close-positions → alert-oncall → verify
- HIGH: reduce-exposure → hedge → alert-oncall → verify
- MEDIUM: adjust-limits → monitor → verify

`HybridDecomposition` — static methods first, LLM fallback for novel scenarios.

### 4.3 Orchestration — Conditional

`Patterns.conditional()` for event-type routing within the HTN "respond" step:

| Predicate | Agent |
|---|---|
| FLASH_CRASH | emergencyHaltAgent |
| LIQUIDITY_DROP | positionReducerAgent |
| GAP_OPEN | reEvaluatorAgent |
| COUNTERPARTY_FAILURE | exposureCloserAgent |
| CIRCUIT_BREAKER | haltAndWaitAgent |
| NEWS_EVENT | sentimentAnalyserAgent |
| MARGIN_CALL | liquidationAgent |

### 4.4 ActionRiskClassifier

`FsiActionRiskClassifier`:

| Action | Risk | Gate? |
|---|---|---|
| Close < 10% portfolio | LOW | No |
| Close 10-25% | MEDIUM | Log only |
| Close > 25% | HIGH | WorkItem for on-call |
| Full liquidation | CRITICAL | WorkItem with elevated SLA |
| New position during incident | MEDIUM | Log only |
| Counterparty exposure close | HIGH | WorkItem |

### 4.5 SLA Enforcement

`FsiSlaBreachPolicy` escalation tiers: 50% → warning, 75% → escalation, 100% → auto-execute + compliance notification. SLA windows: CRITICAL=5min, HIGH=15min, MEDIUM=60min.

### 4.6 Work Items

Queue: `fsi-overnight-approvals`. WorkItem with `approval-gate` (quorum, evidence slots, SLA). Approve/Reject/Delegate actions.

### 4.7 Notifications

`NotificationDispatcher` sends alerts: incident created, gate opened, SLA tiers 1-3, incident resolved. Channels: push, email, SMS based on severity.

### 4.8 UI Panels

| Panel | Component |
|---|---|
| Incident inbox | `work-item-inbox` (three-tab: My Work / Claimable / All) |
| Incident detail | `work-item-detail` |
| Approval gate | `approval-gate` |
| SLA countdown | `sla-indicator` |
| SLA escalation | `sla-breach-policy` |
| Case browser | `case-explorer` |
| Incident timeline | `blocks-timeline` (state progression) |
| Notifications | `notification-inbox` |

### 4.9 New REST Endpoints

| Method | Path | Purpose |
|---|---|---|
| `GET` | `/api/incidents` | Recent incidents with status |
| `GET` | `/api/incidents/{caseId}` | Incident detail |
| `GET` | `/api/incidents/{caseId}/timeline` | Timeline events |
| `POST` | `/api/incidents/simulate` | Inject simulated incident |
| `GET` | `/api/work-items` | Work items (filtered) |
| `POST` | `/api/work-items/{id}/approve` | Approve gated action |
| `POST` | `/api/work-items/{id}/reject` | Reject gated action |

---

## Chapter 5 — The Trading Desk

**Scenario:** Two dock-workbench pages compose all panels from C1-C4 into professional trading workspaces.

### 5.1 Quinoa Integration

```
app/src/main/webui/
  package.json, .casehub-packages/ (gitignored)
  src/index.ts, panels/, dashboards/trading-desk.yaml, dashboards/ops-centre.yaml
```

Maven unpacks `casehub-pages-npm` + `casehub-blocks-ui-npm` SNAPSHOTs. Quinoa builds via esbuild.

### 5.2 Trading Desk Layout

`type: dock-workbench` with 6-zone model:

- **Centre:** position overview + P&L heatmap (custom panels)
- **Left (2 zones):** strategies (fixed, list-pane), market (custom), KPIs (kpi-metric-row)
- **Right (2 zones):** trust (trust-workbench), routing (routing-rationale), deliberation (channel-activity), commitments (commitment-viz)
- **Bottom (1 zone):** audit trail (audit-trail-viewer), preferences (preferences-editor)
- **Status bar:** regime badge + connection status

12 panels: 4 custom + 8 blocks-ui.

### 5.3 Ops Centre Layout

`type: dock-workbench` with 6-zone model:

- **Centre:** incident dashboard (custom)
- **Left (2 zones):** cases (case-explorer, fixed), strategies (list-pane)
- **Right (2 zones):** approvals (work-item-inbox), detail (work-item-detail), response channel (channel-activity), context (context-gauge)
- **Bottom (2 zones):** timeline + SLA (blocks-timeline, sla-breach-policy), gate + alerts (approval-gate, notification-inbox)
- **Status bar:** incident count + SLA status

13 panels: 2 custom + 11 blocks-ui.

### 5.4 Custom Panels

| Panel | What it renders |
|---|---|
| `fsi-position-overview` | pages-table with row accent (green/red P&L), grouped-data-view by asset class |
| `fsi-pnl-heatmap` | ECharts heatmap — P&L by instrument × strategy |
| `fsi-market-panel` | PagesTimeseries + PagesMetric + PagesBadge for regime |
| `fsi-incident-dashboard` | Active incidents — severity counts (kpi-metric-row) + recent events (blocks-timeline) |

### 5.5 Floating/Popout Panels

`DetachController` enables popping any panel into a separate browser window. Use cases: multi-monitor trading, full-screen deliberation feed, focused approval gate. Style transfer via `copyStyles()`, theme sync via `pages-theme-change` event.

### 5.6 Layout Persistence

`createRestLayoutStore('/api/layout')` backed by `casehub-pages-layout-sqlite`. Dock arrangement, split ratios, open/closed state, zone rearrangements all persist across sessions.

### 5.7 Full Push Wiring

All live data via WebSocket `EventBroadcaster`:

| Topic pattern | Source | Consumer |
|---|---|---|
| `market:ticks:{instrument}` | C2 pipeline | fsi-market-panel |
| `market:bars:{instrument}` | C2 pipeline | fsi-market-panel |
| `market:trends:{instrument}` | C2 pipeline | fsi-market-panel |
| `market:regime:{instrument}` | C2 pipeline | fsi-market-panel |
| `market:narrative` | C2 pipeline | fsi-market-panel |
| `position:{instrument}` | Fill events | fsi-position-overview |
| `pnl:{strategyId}` | Fill events | fsi-pnl-heatmap, kpi-metric-row |
| `trust:{strategyType}` | Attestation events | trust-score-panel |
| `routing:latest` | Routing decisions | routing-rationale |
| `deliberation:active` | Deliberation lifecycle | channel-activity |
| `deliberation:{channelId}` | Deliberation events | channel-activity |
| `incident:{caseId}` | Incident lifecycle | fsi-incident-dashboard |
| `incident:summary` | Incident lifecycle | fsi-incident-dashboard |
| `work-item:{itemId}` | WorkItem changes | work-item-inbox |
| `work-item:{caseId}` | Gate opened | work-item-inbox |
| `work-item:summary` | WorkItem changes | work-item-inbox |

`triggerUrl` pattern: paginated datasets re-fetch on WebSocket push notification.

---

## Chapter 6 — Knowledge & Compliance

**Scenario:** Past market incidents feed future detection and response through CBR. Automated post-mortems from conversation state. Compliance grid for MiFID II / Dodd-Frank. GDPR erasure.

### 6.1 CBR — Full 4-Step Pipeline

Feature schema for market events:

| Feature | FeatureField | SimilaritySpec | Weight |
|---|---|---|---|
| event_type | Categorical | exact match | 0.15 |
| instrument_sector | Categorical | exact match | 0.10 |
| time_of_day | Numeric | GaussianDecay(stddev=2hrs) | 0.10 |
| volatility_at_detection | Numeric | GaussianDecay | 0.10 |
| volume_profile | NumericList | list distance | 0.10 |
| price_action_pattern | TimeSeries | DtwSpec | 0.25 |
| event_sequence | DiscreteSequence | EditDistanceSpec | 0.20 |

`FsiRoutingFeatureExtractor` extracts from case context. `TrustWeightedCbrCaseMemoryStore` modulates by source trust. `TemporalDecay.halfLife(90)`. `CbrQuery.scope(Path.of("fsitrading", sector))`.

### 6.2 Plan Adaptation

`FsiPlanAdapter`: agent substitution (replace EXCLUDED agents), threshold adjustment (scale by volatility), step addition (larger positions → pre-reduce), step suppression (market closed → skip halt). `PlanEnsembleAnalyzer` synthesises from top-5 retrieved plans: UNANIMOUS/CONSENSUS/CONTESTED/MINORITY/UNIQUE step classification.

### 6.3 Outcome Recording

`MemoryEmitter` records full case (features + solution + outcome) to `CbrCaseMemoryStore`. `RoutingOutcomeRecorder` records per-step. `CaseOutcomeObserver` records case-level. `Supersession` for regime changes.

### 6.4 Automated Post-Mortem

`ConversationRenderer` generates markdown from `ConversationState`. `ConversationRendererConfig`: `groupByTopic: true`, `showEpistemicStatus: true`, `showConvergenceSignal: true`, `showObligationChain: true`. Output includes established facts, disputed points, actions taken, outcome, CBR record.

### 6.5 Compliance Grid

`compliance-summary` renders requirements:

| Requirement | Mechanism | Evidence type |
|---|---|---|
| MiFID II Art.17 | LedgerExecutionListener | RoutingDecisionRecord |
| MiFID II RTS 6 monitoring | MetricsListener | OTel histograms |
| MiFID II RTS 6 kill switches | ActionRiskClassifier | WorkItem approvals |
| Dodd-Frank audit trail | causedByEntryId chains | Ledger entries |
| MAR surveillance | CBR event sequence matching | DiscreteSequence similarity |

`TrustRoutingRequirement` wraps each with `RoutingDecisionRecord` list.

### 6.6 GDPR Erasure

`FsiGdprErasureService` erases from: `CaseMemoryStore`, CBR cases, ledger (via `LedgerErasureService`). `ErasureReceiptLedgerEntry` for audit. `gdpr-erasure-action` component for the form.

### 6.7 UI Panels

| Panel | Component | Data source |
|---|---|---|
| Similar incidents | `similarity-panel` | `GET /api/cbr/similar?caseId={id}` |
| Compliance grid | `compliance-summary` | `GET /api/compliance/status` |
| GDPR erasure | `gdpr-erasure-action` | `POST /api/gdpr/erase` |
| Audit trail | `audit-trail-viewer` | Ledger REST API |

### 6.8 Speculative — Agent Memory Evolution

Designed for future engine capabilities (actively in development):

- **Goal discovery (engine#808):** Strategy agents discover new objectives from accumulated memories — e.g., "momentum works better pre-market → propose pre-market momentum goal"
- **Personality dynamics (engine#857):** Strategy dispositions evolve through interaction — aggressive agents that repeatedly fail against conservative approaches adapt
- **Re-planning on failure (engine#882):** When a trade execution step fails, dynamically replan remaining steps
- **Planning under constraints (engine#884):** Time-bounded decisions — "decide within 30 seconds or escalate"

These design to SPIs, not implementations. fsitrading defines the domain's memory schema (what constitutes a market regime memory, when trust trajectory should trigger personality adaptation) so the app is ready when these capabilities land.

### 6.9 New REST Endpoints

| Method | Path | Purpose |
|---|---|---|
| `GET` | `/api/incidents/similar` | Similar past incidents |
| `GET` | `/api/incidents/history` | Paginated CBR case scan |
| `GET` | `/api/compliance/status` | Compliance grid |
| `POST` | `/api/gdpr/erase` | GDPR erasure request |
| `GET` | `/api/postmortem/{caseId}` | Generated post-mortem |

---

## Platform Pattern Coverage

### blocks (Java) — 7 categories

| Category | Patterns used | Coverage |
|---|---|---|
| Orchestration (8 patterns) | Supervisor, Sequence, Loop, Parallel, Voting, Debate, Conditional, HTN | 8/8 (100%) |
| Summarisation (14 core types) | EventStreamBus, SummarisationRunner, KeyedSummarisationRunner, Compactor, TieredContentSummariser, LlmContentSummariser, VerbatimContentSummariser, ObservationAccumulator, PartitionedObservationService, AffordanceRenderer, ObservableEntity, Affordance | 12/14 (86%) |
| Conversation (27 types) | ConversationProjection, ConversationFold, ConversationRenderer, ConversationState, CommonGroundAnalyser, EpistemicRules, ConvergenceAnalyser, ConvergencePolicies, ConvergenceTermination | 9/27 (33% — remaining are state/record types consumed transitively) |
| Channel utilities (11 types) | ChannelAgentDispatcher, ChannelAgentHandler, ChannelEventAdapter, ChannelEventPublisher, ChannelMessageMeta, ContextTracker, BoundedProjectionDecorator | 7/11 (64%) |
| Routing (17 types) | LlmAgentRoutingStrategy, CbrAgentRoutingStrategy, DispositionAwareRouting, PlanCompositionAnalyser, PredecessorAnalyser, CoordinationSignalProvider, CbrRoutingPromptSection, RoutingDecisionRecord, TrustRoutingRequirement, CbrOutcomeWeights | 10/17 (59% — remaining are default beans/framework records) |
| Accountability (3 types) | LedgerExecutionListener, EventLogListener, MetricsListener | 3/3 (100%) |

**Note:** `ActionRiskClassifier` is in casehub-engine-api, not blocks. `FsiActionRiskClassifier` (C4) implements that engine SPI directly.

### blocks-ui (TS) — Components used

| Component | Slice | Trading use |
|---|---|---|
| split-workbench | C5 | Inside dock zones |
| list-pane | C1, C5 | Strategy list, position list |
| detail-pane | C1, C5 | Strategy/position detail |
| grouped-data-view | C5 | Positions by asset class |
| work-item-inbox | C4 | Overnight approvals |
| work-item-detail | C4 | Escalation detail |
| notification-inbox | C4, C5 | Trade/SLA alerts |
| sla-indicator | C4 | Incident SLA countdown |
| kpi-metric-row | C1, C2, C5 | P&L, win rate, volume |
| approval-gate | C4 | High-risk trade approval |
| audit-trail-viewer | C5, C6 | Ledger entries |
| blocks-timeline | C3, C4 | Commitment lifecycle, incident progression |
| trust-score-panel | C1 | Strategy trust scores |
| channel-activity | C3, C4 | Deliberation feed, incident response |
| commitment-viz | C3 | Trade commitment lifecycle |
| similarity-panel | C6 | Similar past market events |
| compliance-summary | C6 | MiFID II / Dodd-Frank grid |
| trust-feedback-display | C1 | Post-trade trust delta |
| sla-breach-policy | C4 | Escalation tiers |
| gdpr-erasure-action | C6 | Trader PII erasure |
| case-explorer | C4, C5 | Browse incidents |
| preferences-editor | C1, C5 | Trust routing thresholds |
| routing-rationale | C1, C5 | Routing decision explanation |
| trust-workbench | C1, C5 | Composite trust visibility |
| context-gauge | C3, C5 | LLM context window usage |

**25 of 32 components used** (78%). Excluded: work-item-workbench (replaced by dock-workbench composition), work-item-row (deprecated), document-workbench (drafthouse-specific — debate-feed/document-diff/review-tracker/brainstorm-options are document-review components, not trading), session-list/detail/workbench (claudony-specific), graph stencils (alpha, tracked for future).

### neocortex — Capabilities used

| Capability | Slice |
|---|---|
| CbrCaseMemoryStore (feature-vector similarity) | C6 |
| FeatureField.TimeSeries + DtwSpec | C6 |
| FeatureField.DiscreteSequence + EditDistanceSpec | C6 |
| TrustWeightedCbrCaseMemoryStore | C6 |
| TemporalDecay | C6 |
| CbrFilter | C6 |
| Supersession | C6 |
| PlanAdapter / AdaptedPlan | C6 |
| PlanEnsembleAnalyzer / StepConsensus | C6 |
| CaseMemoryStore (agent memory) | C1 |
| MemoryEmitter | C1, C6 |
| EpisodicMemoryConfig | C1 |
| CbrOutcomeWeights SPI | C1 |

### pages — Capabilities used

| Capability | Slice |
|---|---|
| dock-workbench (6-zone, drag-and-drop) | C5 |
| Floating/popout panels (DetachController) | C5 |
| Layout persistence (LayoutStore + SQLite) | C5 |
| EventBroadcaster + TopicRegistry (push) | C2, C5 |
| triggerUrl (push-triggered re-fetch) | C5 |
| loadSite + Quinoa | C5 |
| PagesTimeseries, PagesMetric, PagesBadge | C2, C5 |
| PagesMap (heatmap) | C5 |
| pages-table (row accent, grouped) | C5 |
| Schema-driven forms | C1 (preferences) |
| Design tokens + theming | C5 |

---

## Design Limitations

1. **Synthetic data limits realism.** The summarisation pipeline is sophisticated but fed by fake data. Price patterns, volume profiles, and market events are approximations. Real feed integration (Alpaca, Polygon) is a future upgrade that swaps the data source without changing the pipeline.

2. **All 8 orchestration patterns in one app is ambitious.** Each pattern needs real agent implementations, not stubs. C1 carries three patterns; if implementation reveals that one pattern doesn't add value for the trading scenario, it's better to drop it than force it.

3. **CBR effectiveness depends on case volume.** DTW and edit distance similarity are meaningful only with sufficient past cases. The initial CBR case library will be seeded from simulated incidents — real value comes from production usage over time.

4. **Speculative agent memory features (D12) may change.** Engine#808, #857, #882, #884 are open issues, not shipped features. The domain schema design is intentionally SPI-boundary-aligned to absorb changes.

5. **Two dock-workbench pages means double the panel testing surface.** 25 panels across 2 pages with drag-and-drop rearrangement creates a combinatorial testing challenge. Mitigated by the fact that panels are independently tested via blocks-ui showcase — the workbench composition is declarative YAML.
