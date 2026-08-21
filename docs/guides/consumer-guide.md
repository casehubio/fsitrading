# casehub-fsitrading -- Consumer Guide

> Financial Services Trading application -- multi-agent trading automation, trust-weighted strategy selection, and tamper-evident audit trail for algorithmic trading.

**GitHub:** [casehubio/fsitrading](https://github.com/casehubio/fsitrading)
**Tier:** Application (domain logic on CaseHub foundation)

---

## Purpose

Algorithmic trading application built on the CaseHub platform. Strategies generate trade decisions from market events. Orders execute via a simulated exchange. Positions track quantity and P&L. Every decision is recorded in a tamper-evident ledger, and P&L outcomes feed back as trust attestations so strategy selection improves over time.

Not a framework -- this is a domain application. Trading-specific logic lives here; coordination, audit, and trust primitives come from the platform.

---

## Module Structure

| Module | Artifact | Type | Purpose |
|---|---|---|---|
| `api` | `casehub-fsitrading-api` | Pure-Java (no Quarkus) | Domain model records/enums, SPI interfaces, capability tags, actor identity |
| `app` | `casehub-fsitrading-app` | Quarkus application | REST resources, JPA entities, services, ledger entries, case definitions, Flyway migrations |

---

## Current State

Chapters 1--2 implemented (August 2026). Working vertical slices: domain model, order lifecycle, position tracking, ledger integration, trust scoring, multi-agent arena, and 5-level market data pipeline with WebSocket push.

**Implemented (C1 Strategy Arena):**
- Strategy Arena -- multi-agent evaluation pipeline: Sequence[Routing → Evaluation → Voting → Risk → Gate → Execute]
- 7 strategy agents registered via eidos `AgentDescriptorRegistrar`
- Multi-select routing, per-instrument majority voting, risk classification with human approval gate
- Order lifecycle, position management, tamper-evident ledger audit trail
- Trust scoring -- Bayesian Beta from P&L attestations with quality floor filtering

**Implemented (C2 Market Pulse):**
- 5-level temporal summarisation pipeline: PriceTick → OHLCV → TrendSummary → RegimeAssessment → SessionNarrative
- Synthetic market data with U-shaped volume profiles and 5 injectable scenarios (flash crash, liquidity drop, gap open, volume spike, mean reversion)
- Computational summarisers (L0-L2) and LLM-powered summarisers (L3-L4) with structured output and graceful degradation
- Observation cache with strategy-level visibility policy for arena agent context
- Market event detection (trend reversal, regime change) via CDI domain events
- Channel bridge to qhorus for L2+ events
- Arena integration -- observation context injected into strategy agent evaluations
- WebSocket push via pages-push EventBroadcaster -- live ticks, bars, trends, regime to browser
- Minimal fsi-market-panel web component (Quinoa + esbuild) proving end-to-end push path
- Sequence + Loop orchestration model via casehub-blocks patterns
- 31 REST endpoints (see API section below)
- Dual-datasource configuration (H2 dev, PostgreSQL prod)

**Implemented (C4a Overnight Ops Backend):**
- Incident lifecycle -- severity classification (CRITICAL/HIGH/MEDIUM), off-hours amplification, 2-tier SLA escalation
- YAML case definitions (overnight-incident, fsi-oversight) with CBR config, milestones, goals, capability bindings
- 13 response agents -- 7 rule-based (halt, close, alert, adjust, monitor, verify, halt-and-wait), 6 LLM stubs (reduce, hedge, re-evaluate, close-exposure, sentiment, liquidate)
- Action risk classifier gates high-risk actions (>25% portfolio close, full liquidation, counterparty close) via dedicated oversight CaseHub
- SLA breach policy with claim/completion tiers, escalation to oncall-escalation group
- JPA incident store with timeline tracking (Flyway V105-V106)
- C2 pipeline bridge -- FsiMarketEventDetector fires CDI events for TrendReversalDetected/RegimeChanged
- EventTypeRegistry registration (4 incident event types) + EventBroadcaster push
- 7 incident REST endpoints + WorkItem query/resolve

**Not yet implemented:**
- C3: Multi-agent strategy debate
- C4b: Overnight Ops UI panels (Pages DSL)
- C5a: Pages UI -- trading desk dock-workbench with 9 panels via pages DSL, layout persistence, composite REST+WS data binding
- C6: Full CBR pipeline, advanced quality dimensions (max drawdown, market timing, Kelly criterion)

---

## REST API

All endpoints produce `application/json`.

| Method | Path | Description |
|---|---|---|
| `GET` | `/api/orders` | List all orders (most recent first) |
| `GET` | `/api/orders/strategy/{strategyId}` | Orders for a specific strategy |
| `GET` | `/api/positions` | All positions |
| `GET` | `/api/positions/strategy/{strategyId}` | Positions for a specific strategy |
| `GET` | `/api/strategies` | All registered strategies |
| `GET` | `/api/strategies/active` | Active strategies only |
| `POST` | `/api/strategies` | Create a strategy (`{name, strategyType}`) |
| `POST` | `/api/market-data/tick` | Generate a synthetic market tick |
| `GET` | `/api/market-data/recent?limit=20` | Recent market events |
| `GET` | `/api/audit/orders/{orderId}` | Audit trail for an order -- returns typed ledger entries (STRATEGY_EVALUATION, ORDER_EXECUTION) with causality chain |
| `GET` | `/api/trust/strategies` | Trust scores for all strategy types -- Bayesian Beta from P&L attestations |
| `GET` | `/api/trust/strategies/{strategyType}` | Trust score for a specific strategy type |
| `POST` | `/api/evaluations/trigger` | Trigger an arena run (idempotent with `Idempotency-Key` header) |
| `GET` | `/api/routing/decisions` | Recent routing decisions (paginated, `?limit=N`) |
| `GET` | `/api/routing/decisions/latest` | Most recent completed routing decision |
| `GET` | `/api/kpis` | Aggregated KPIs (totalPnl, winRate, tradeCount, avgReturn) |
| `GET` | `/api/kpis/heatmap` | P&L cross-tabulated by instrument × strategy name |
| `GET` | `/api/layout/{key}` | Load saved dock-workbench layout |
| `PUT` | `/api/layout/{key}` | Save dock-workbench layout (JSON body) |
| `GET` | `/api/preferences/trust-routing` | Trust routing threshold configuration |
| `PUT` | `/api/preferences/trust-routing` | Update trust routing thresholds |
| `GET` | `/api/market-data/bars/{instrument}` | Historical 1-min OHLCV bars |
| `GET` | `/api/market-data/trends/{instrument}` | Recent 5-min trend summaries |
| `GET` | `/api/market-data/regime/{instrument}` | Latest regime assessment |
| `GET` | `/api/market-data/narrative` | Latest session narrative |
| `POST` | `/api/market-data/scenario` | Inject scenario event (flash crash, gap open, etc.) |
| `POST` | `/api/market-data/scheduler/pause` | Pause tick generation |
| `POST` | `/api/market-data/scheduler/resume` | Resume tick generation |
| `GET` | `/api/incidents?limit=20` | Recent incidents with status (paginated) |
| `GET` | `/api/incidents/{caseId}` | Incident detail (404 if not found) |
| `GET` | `/api/incidents/{caseId}/timeline` | Timeline events for an incident |
| `POST` | `/api/incidents/simulate` | Inject simulated incident for demo/testing |
| `POST` | `/api/incidents/external` | Ingest external operational event (requires `fsi-ops` role) |
| `GET` | `/api/work-items?type=&candidateGroup=&status=` | Work items filtered by type/group/status |
| `POST` | `/api/work-items/{id}/resolve` | Approve/Reject/Delegate a gated action |

### WebSocket Push

Connect to `ws://{host}/ws/push`. Send `listen` to subscribe to topic patterns:

```json
{"op": "listen", "id": "1", "topics": ["market:ticks:*", "market:regime:*"]}
```

| Topic pattern | Level | Payload | Rate |
|---|---|---|---|
| `market:ticks:{instrument}` | 0 | PriceTick | Every tick (~500ms) |
| `market:bars:{instrument}` | 1 | OHLCV | ~1/min per instrument |
| `market:trends:{instrument}` | 2 | TrendSummary | ~1/5min per instrument |
| `market:regime:{instrument}` | 3 | RegimeAssessment | ~1/hour per instrument |
| `market:narrative` | 4 | SessionNarrative | ~1/session |
| `deliberation:active` | — | DeliberationPushPayload (Started/Completed/Failed) | Per deliberation lifecycle |
| `deliberation:{channelId}` | — | DeliberationPushPayload (all types + ConvergenceUpdate) | Per message during debate |
| `position:{instrument}` | — | TradingPushPayload.PositionUpdate | Per fill |
| `pnl:{strategyId}` | — | TradingPushPayload.PnlUpdate | Per position close |
| `trust:{strategyType}` | — | TradingPushPayload.TrustUpdate | Per arena attestation |
| `routing:latest` | — | TradingPushPayload.RoutingUpdate | Per routing decision |
| `incidents/{caseId}` | — | IncidentCreatedEvent / SlaBreachEvent / IncidentResolvedEvent | Per incident lifecycle event |
| `incidents/summary` | — | IncidentCreatedEvent / IncidentResolvedEvent | Per incident create/resolve |
| `work-items/{caseId}` | — | GateOpenedEvent | Per high-risk action gate |

Deliberation payloads include a `type` field for client dispatch: `DELIBERATION_STARTED`, `DELIBERATION_COMPLETED`, `DELIBERATION_FAILED`, `CONVERGENCE_UPDATE`.

Trading payloads include a `type` field: `POSITION_UPDATE`, `PNL_UPDATE`, `TRUST_UPDATE`, `ROUTING_UPDATE`.

### Trust Score Response

```json
{
  "strategyType": "MOMENTUM",
  "actorId": "rule:momentum@v1",
  "trustScore": 0.72,
  "decisionCount": 15,
  "phase": "ACTIVE",
  "attestationSummary": { "positive": 11, "negative": 4 }
}
```

Phase is `BOOTSTRAP` until 10 decisions, then `ACTIVE`.

---

## Domain Model (API Module)

**Records:**
- `TradeDecision` -- strategy output: strategyId, instrument, side, quantity, orderType, limitPrice, rationale, provenance (nullable TradeProvenance)
- `Instrument` -- symbol + asset class + exchange

**Enums:**
- `StrategyType` -- MOMENTUM, MEAN_REVERSION, STATISTICAL_ARBITRAGE, MARKET_MAKING, EVENT_DRIVEN, PORTFOLIO_REBALANCE, OVERNIGHT_RISK_MANAGEMENT
- `AssetClass` -- EQUITY, FIXED_INCOME, FX, COMMODITY, CRYPTO, INDEX
- `OrderSide` -- BUY, SELL
- `OrderType` -- MARKET, LIMIT, STOP, STOP_LIMIT
- `OrderStatus` -- PENDING, SUBMITTED, PARTIALLY_FILLED, FILLED, CANCELLED, REJECTED
- `MarketEventType` -- PRICE_TICK, VOLUME_SPIKE, FLASH_CRASH, LIQUIDITY_DROP, GAP_OPEN, CIRCUIT_BREAKER, NEWS_EVENT, COUNTERPARTY_FAILURE, MARGIN_CALL
- `IncidentSeverity` -- CRITICAL, HIGH, MEDIUM

**Market data types (C2):**
- `PriceTick` -- instrument, price, volume, timestamp, anomaly flag
- `OHLCV` -- 1-minute bar: open, high, low, close, volume, windowStart, windowEnd
- `TrendSummary` -- 5-min trend: direction (UP/DOWN/FLAT), momentum, volatility, priorRegime
- `RegimeAssessment` -- LLM-synthesised: instrument, regime (TRENDING/VOLATILE/RANGE_BOUND/MEAN_REVERTING), confidence, rationale
- `SessionNarrative` -- LLM-synthesised: instruments covered, narrative text, timestamp

**Arena types:**
- `MarketSignal` -- instrument, eventType, price, volume, timestamp
- `StrategyResponse` -- sealed: `Trade(List<TradeDecision>, String)` | `Hold(String)`
- `ConsensusResult` -- per-instrument voting results with deadlock detection
- `InstrumentConsensus` -- status (CONSENSUS/DEADLOCKED/NO_VOTERS), winningSide, quantity, votes
- `RiskAssessment` -- overall and per-instrument risk levels (LOW/MEDIUM/HIGH/CRITICAL)
- `ApprovalOutcome` -- NOT_REQUIRED, APPROVED, REJECTED, TIMEOUT

**Deliberation types (C3):**
- `TradeProvenance` -- deliberation provenance: deliberationChannelId, commitmentId, convergenceState, confidence
- `DeliberationRecord` -- JPA entity: channel_id, instrument, status, trigger_type, convergence_state, confidence, established/disputed/pending counts, rounds, participants, commitment_id, trade_decision_id, timestamps, conversation/common-ground snapshots
- `DeliberationDecisionLedgerEntry` -- JOINED ledger entry: deliberationId, channelId, instrument, convergenceState, confidence, established/disputed counts, participants

**Identity:**
- `FsiActorIdentity` -- derives actor IDs, roles, and capability tags from `StrategyType` for trust scoring integration
- `FsiActorIdentity.HUMAN_TRADER` -- constant `"human:trader@v1"` for escalation commitments
- `FsiCapabilities` -- string constants for capability-based routing (momentum, mean-reversion, etc.)

---

## C3 — Trade Deliberation

Multi-agent strategy debate over qhorus channels with epistemic convergence detection, producing commitments and trade decisions.

### REST Endpoints

| Method | Path | Purpose | Response |
|--------|------|---------|----------|
| `GET` | `/api/deliberations` | List deliberations | Paginated list, filterable by instrument, convergenceState, triggerType |
| `GET` | `/api/deliberations/{id}` | Single record | `DeliberationRecord`, 404 for unknown |
| `POST` | `/api/deliberations/trigger?instrument=AAPL` | Manual trigger | 202 Accepted with record ID, 409 if in-flight |

### Convergence Paths

| State | Action |
|-------|--------|
| CONSENSUS | Execute trade via C1 pipeline with full confidence |
| DEADLOCK | Escalate to human trader |
| PROGRESSING | Escalate (debate active at round cap) |
| DIMINISHING_RETURNS | Execute if established ratio >= 0.5, escalate otherwise |
| CONVERGING | Execute if established ratio >= 0.7, escalate otherwise |

### Configuration Properties

| Property | Default | Description |
|----------|---------|-------------|
| `fsi.deliberation.max-rounds` | 10 | Maximum debate rounds |
| `fsi.deliberation.wall-clock-timeout-seconds` | 900 | Wall-clock timeout for debate |
| `fsi.deliberation.trend-reversal-threshold` | 0.5 | Minimum momentum for trend reversal trigger |
| `fsi.deliberation.diminishing-returns-min-established` | 0.5 | Minimum established ratio for DR execution |
| `fsi.deliberation.converging-consensus-threshold` | 0.7 | Minimum established ratio for converging execution |

---

## Build

```bash
JAVA_HOME=$(/usr/libexec/java_home -v 26) mvn --batch-mode install
```

Uses H2 in-memory for dev/test. PostgreSQL for production (`%prod` profile).
