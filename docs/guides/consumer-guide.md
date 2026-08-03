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

**Implemented:**
- Domain model -- `TradeDecision`, `Instrument`, 7 strategy types, 7 market event types, order lifecycle enums
- `StrategyEvaluator` SPI for pluggable strategy implementations
- Order lifecycle -- create from decision, fill with price, status tracking
- Position management -- quantity tracking, average cost, realized P&L calculation
- Tamper-evident audit trail -- `StrategyEvaluationLedgerEntry` and `OrderExecutionLedgerEntry` with Merkle chain integrity via `JpaLedgerEntry`
- Trust scoring -- `PnlAttestationService` generates SOUND/FLAGGED attestations from P&L outcomes with confidence scaling
- Case engine integration -- `StrategyEvaluationCaseDefinition` with capabilities (strategy-evaluation, risk-assessment, order-execution), goals, milestones, and human approval gate for high-risk trades
- Synthetic market data provider for development/testing
- 6 REST endpoints (see API section below)
- Dual-datasource configuration (H2 dev, PostgreSQL prod)

**Not yet implemented:**
- CBR for market event knowledge retention
- Real market data integration (stream modules)
- Multi-agent strategy debate
- SLA enforcement with escalation
- Pages UI integration
- Agent registration in eidos (open issue #12)
- Quality dimension scores in P&L attestations (open issue #13)

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

**SPI:**
- `StrategyEvaluator` -- `evaluate(strategyId, instrument, currentPrice, marketContext) -> Optional<TradeDecision>`

**Identity:**
- `FsiActorIdentity` -- derives actor IDs, roles, and capability tags from `StrategyType` for trust scoring integration
- `FsiCapabilities` -- string constants for capability-based routing (momentum, mean-reversion, etc.)

---

## Build

```bash
JAVA_HOME=$(/usr/libexec/java_home -v 26) mvn --batch-mode install
```

Uses H2 in-memory for dev/test. PostgreSQL for production (`%prod` profile).
