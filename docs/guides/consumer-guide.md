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

Chapters 1--3 implemented (June 2026). Working vertical slice: domain model, order lifecycle, position tracking, ledger integration, and trust scoring.

**Implemented (C1 Strategy Arena):**
- Domain model -- `TradeDecision`, `Instrument`, `MarketSignal`, `StrategyResponse`, `ConsensusResult`, `RiskAssessment`, 7 strategy types
- Strategy Arena -- multi-agent evaluation pipeline: Sequence[Routing → Evaluation → Voting → Risk → Gate → Execute] built on casehub-blocks orchestration patterns
- 7 strategy agents (Momentum, MeanReversion, StatArb, MarketMaking, EventDriven, PortfolioRebalance, OvernightRisk) registered via eidos `AgentDescriptorRegistrar`
- Multi-select routing via `RoutingSignalAssembler` with 6 platform routing strategies (LLM, CBR, Disposition, PlanComposition, Predecessor, Coordination)
- Per-instrument majority voting with routing-score-weighted quantities
- Risk classification (LOW/MEDIUM/HIGH/CRITICAL) with human approval gate for HIGH/CRITICAL via `AgentRef.human()`
- Quality dimension scoring -- 3 trust dimensions (return-magnitude, hold-period-efficiency, risk-adjusted-return) on P&L attestations
- Order lifecycle, position management, tamper-evident ledger audit trail
- Trust scoring -- Bayesian Beta from P&L attestations with quality floor filtering
- Synthetic market data provider for development/testing
- 17 REST endpoints (see API section below)
- Dual-datasource configuration (H2 dev, PostgreSQL prod)

**Not yet implemented:**
- C2: Event-driven arena triggering, multi-instrument expansion
- C3: Multi-agent strategy debate
- C4: SLA enforcement with escalation tiers
- C5: Pages UI -- trading desk dock-workbench
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
