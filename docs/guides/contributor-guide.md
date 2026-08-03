# casehub-fsitrading -- Contributor Guide

> Financial Services Trading application -- multi-agent trading automation, trust-weighted strategy selection, and tamper-evident audit trail for algorithmic trading.

**GitHub:** [casehubio/fsitrading](https://github.com/casehubio/fsitrading)

---

## Module Structure

| Module | Artifact | Type | Purpose |
|---|---|---|---|
| `api` | `casehub-fsitrading-api` | Pure-Java (no Quarkus) | Domain model records/enums, SPI interfaces, capability tags, actor identity |
| `app` | `casehub-fsitrading-app` | Quarkus application | REST resources, JPA entities, services, ledger entries, case definitions, Flyway migrations |

---

## Platform Dependencies

| Dependency | Platform Layer | Usage in fsitrading |
|---|---|---|
| `casehub-platform-api` | L1: Identity | `ActorType`, `TenancyConstants` -- actor identity for ledger entries |
| `casehub-platform-expression` | L1: Expression | JQ evaluator for case definition bindings |
| `casehub-platform-config` | L1: Config | YAML-backed `PreferenceProvider` displacing `MockPreferenceProvider` |
| `casehub-engine` | L5: Case engine | `CaseDefinition`, `Binding`, `Goal`, `Milestone` -- strategy evaluation case lifecycle |
| `casehub-engine-planning` | L5: Planning | Plan item store for case execution |
| `casehub-engine-ledger` | L6: Trust routing | Trust-weighted routing with `WorkerDecisionEntry` per worker execution |
| `casehub-ledger` | L4: Ledger | `LedgerEntryRepository`, `LedgerAttestation`, `AttestationVerdict` -- tamper-evident audit with Merkle chain |
| `casehub-work` | L2: Human task | `HumanTaskTarget` -- human approval gate for high-risk trades |
| `casehub-qhorus` | L3: Agent comms | Typed agent communication (COMMAND/RESPONSE/DONE/DECLINE/FAILURE) |
| `casehub-worker` | Worker framework | `Capability` definitions for case bindings |
| `casehub-neocortex-memory` | Memory | Platform memory store (JPA-backed) |

---

## Architecture

### Execution Flow

1. `SyntheticMarketDataProvider` generates price ticks (dev/test) or real market data arrives (future)
2. Strategy evaluators produce `TradeDecision` via the `StrategyEvaluator` SPI
3. `SimulatedOrderExecutor.executeDecision()` orchestrates the full cycle:
   - Creates order via `OrderService`
   - Determines fill price and fills the order
   - Updates position via `PositionService.applyFill()` -- tracks quantity, average cost, realized P&L
   - Records `StrategyEvaluationLedgerEntry` to tamper-evident ledger with actor identity derived from strategy type
   - Records `OrderExecutionLedgerEntry` chained to the evaluation via `causedByEntryId`
   - If the fill closes a position (realized P&L), generates a trust attestation via `PnlAttestationService`
4. Trust attestations feed back into Bayesian Beta scoring per strategy agent, visible via `TrustScoreResource`

### Case Definition

`StrategyEvaluationCaseDefinition` defines a full case lifecycle:

**Capabilities:** strategy-evaluation, risk-assessment, order-execution

**Goals:**
- Success: trade-executed (FILLED) or no-trade-needed (HOLD)
- Failure: trade-rejected (HIGH risk + human REJECTED) or execution-failed (REJECTED/CANCELLED)

**Human approval gate:** Trades where quantity >= 10,000 or notional >= $500,000 are gated by a `HumanTaskTarget` requiring `senior-traders` group approval within a 1-hour SLA.

**Milestones:** strategy-evaluated, risk-assessed

### Ledger Integration

Two domain-specific ledger entry types extend `JpaLedgerEntry`:

- `StrategyEvaluationLedgerEntry` -- captures strategyId, strategyName, instrument, signal, rationale. Actor identity is `rule:<strategy-type>@v1` (e.g., `rule:momentum@v1`).
- `OrderExecutionLedgerEntry` -- captures orderId, instrument, side, quantity, fillPrice, strategyId. Chained to evaluation entry via `causedByEntryId`.

Both implement `domainContentBytes()` for Merkle chain integrity.

### Trust Scoring

`PnlAttestationService` generates `LedgerAttestation` entries:
- Verdict: `SOUND` for positive P&L, `FLAGGED` for negative
- Confidence: scaled by `abs(realizedPnl / closedNotional) * 10.0`, clamped to [0.1, 1.0]
- Capability tag: derived from `StrategyType` (e.g., `momentum`, `mean-reversion`)
- Attestor: `fsi-pnl-system` with `ActorType.SYSTEM`

Trust scores are exported via `TrustExportService` and exposed at `/api/trust/strategies`.

### Position Tracking

`PositionService.applyFill()` implements:
- Same-direction fills: weighted average cost calculation
- Opposite-direction fills: realizes P&L = (fillPrice - avgCost) * closedQty, adjusts for short positions
- Returns `FillResult` record with position state, realized P&L, closed notional, closed quantity

### Datasource Layout

Dual H2 databases in dev/test (PostgreSQL in prod):
- **Default datasource** -- trade orders, positions, strategies, market events, platform memory, engine state
- **qhorus datasource** -- qhorus runtime, ledger entries (strategy evaluation + order execution), trust scores

Flyway migrations: `db/fsitrading/migration`, `db/work/migration`, `db/memory/migration` (default); `db/qhorus/migration`, `db/ledger/migration`, `db/fsitrading-ledger/migration` (qhorus).

---

## SPI Extension Points

| SPI | Package | Purpose |
|---|---|---|
| `StrategyEvaluator` | `io.casehub.fsitrading.spi` | Pluggable strategy implementations -- receives instrument + price + market context, returns `Optional<TradeDecision>` |

No concrete `StrategyEvaluator` implementations exist yet. The current flow uses `SimulatedOrderExecutor` with decisions constructed externally.

---

## JPA Entities

| Entity | Table | Key Fields |
|---|---|---|
| `OrderEntity` | `trade_order` | instrument, strategyId, side, orderType, quantity, limitPrice, fillPrice, status, rationale, caseInstanceId |
| `PositionEntity` | `position` | instrument, assetClass, strategyId, quantity, avgCost, unrealizedPnl, realizedPnl |
| `StrategyEntity` | `trading_strategy` | name, strategyType, instruments (text), parameters (text), active |
| `MarketEventEntity` | `market_event` | instrument, eventType, price, volume, data (text) |

---

## Known Issues

| # | Title | Status |
|---|---|---|
| [#14](https://github.com/casehubio/fsitrading/issues/14) | `SimulatedOrderExecutor.executeDecision()` lacks `@Transactional` -- dual-datasource atomicity | Open |
| [#13](https://github.com/casehubio/fsitrading/issues/13) | Add quality dimension scores to P&L attestations | Open |
| [#12](https://github.com/casehubio/fsitrading/issues/12) | Register strategy agents as `AgentDescriptor`s in eidos | Open |

---

## What's Next

The implemented vertical slice covers Chapters 1--3 of the roadmap. Remaining work includes:

- Concrete `StrategyEvaluator` implementations (momentum, mean-reversion, etc.)
- CBR integration for market event knowledge retention
- Real market data ingestion via stream modules (casehub-iot CloudEvent pattern)
- SLA enforcement with `SlaBreachPolicy` and escalation to on-call trader
- Multi-agent strategy debate and consensus
- Pages UI for position overview, P&L timeline, agent trust scores
- eidos agent registration

---

## Design Documents

- `docs/DOMAIN.md` -- full domain background (automated trading, market microstructure, compliance frameworks)
- `docs/specs/2026-06-30-chapter3-trust-scoring-design.md` -- trust scoring design spec
