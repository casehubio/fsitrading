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
- 24 REST endpoints (see API section below)
- Dual-datasource configuration (H2 dev, PostgreSQL prod)

**Not yet implemented:**
- C3: Multi-agent strategy debate
- C4: SLA enforcement with escalation tiers
- C5: Pages UI -- trading desk dock-workbench (replaces minimal panel with full composition)
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
| `GET` | `/api/preferences/trust-routing` | Trust routing threshold configuration |
| `PUT` | `/api/preferences/trust-routing` | Update trust routing thresholds |
| `GET` | `/api/market-data/bars/{instrument}` | Historical 1-min OHLCV bars |
| `GET` | `/api/market-data/trends/{instrument}` | Recent 5-min trend summaries |
| `GET` | `/api/market-data/regime/{instrument}` | Latest regime assessment |
| `GET` | `/api/market-data/narrative` | Latest session narrative |
| `POST` | `/api/market-data/scenario` | Inject scenario event (flash crash, gap open, etc.) |
| `POST` | `/api/market-data/scheduler/pause` | Pause tick generation |
| `POST` | `/api/market-data/scheduler/resume` | Resume tick generation |

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
- `TradeDecision` -- strategy output: strategyId, instrument, side, quantity, orderType, limitPrice, rationale
- `Instrument` -- symbol + asset class + exchange

**Enums:**
- `StrategyType` -- MOMENTUM, MEAN_REVERSION, STATISTICAL_ARBITRAGE, MARKET_MAKING, EVENT_DRIVEN, PORTFOLIO_REBALANCE, OVERNIGHT_RISK_MANAGEMENT
- `AssetClass` -- EQUITY, FIXED_INCOME, FX, COMMODITY, CRYPTO, INDEX
- `OrderSide` -- BUY, SELL
- `OrderType` -- MARKET, LIMIT, STOP, STOP_LIMIT
- `OrderStatus` -- PENDING, SUBMITTED, PARTIALLY_FILLED, FILLED, CANCELLED, REJECTED
- `MarketEventType` -- PRICE_TICK, VOLUME_SPIKE, FLASH_CRASH, LIQUIDITY_DROP, GAP_OPEN, CIRCUIT_BREAKER, NEWS_EVENT

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

**Identity:**
- `FsiActorIdentity` -- derives actor IDs, roles, and capability tags from `StrategyType` for trust scoring integration
- `FsiCapabilities` -- string constants for capability-based routing (momentum, mean-reversion, etc.)

---

## Build

```bash
JAVA_HOME=$(/usr/libexec/java_home -v 26) mvn --batch-mode install
```

Uses H2 in-memory for dev/test. PostgreSQL for production (`%prod` profile).
