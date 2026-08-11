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
| `casehub-blocks` | Orchestration | `Patterns`, `ExecutionModel`, `AgentRef`, `RoutingStrategy` -- arena pipeline composition |
| `casehub-engine-planning` | L5: Planning | Plan item store for case execution |
| `casehub-engine-ledger` | L6: Trust routing | Trust-weighted routing with `WorkerDecisionEntry` per worker execution |
| `casehub-ledger` | L4: Ledger | `LedgerEntryRepository`, `LedgerAttestation`, `AttestationVerdict` -- tamper-evident audit with Merkle chain |
| `casehub-work` | L2: Human task | `HumanTaskTarget` -- human approval gate for high-risk trades |
| `casehub-qhorus` | L3: Agent comms | Typed agent communication (COMMAND/RESPONSE/DONE/DECLINE/FAILURE) |
| `casehub-worker` | Worker framework | `Capability` definitions for case bindings |
| `casehub-neocortex-memory` | Memory | Platform memory store (JPA-backed) |

---

## Architecture

### Strategy Arena Pipeline

The arena is an `ExecutionModel<ArenaContext>` built at startup by `ArenaConfiguration` (CDI producer). Triggered via `POST /api/evaluations/trigger`.

Pipeline: Sequence[Routing → Evaluation → Voting → Risk → Gate → Execute]

1. **Routing** -- `FsiArenaRouting` selects strategy agents above a blended score threshold (default 0.3) using all 6 platform routing strategies via `RoutingSignalAssembler`
2. **Evaluation** -- selected strategy agents evaluate the market signal concurrently, each returning `StrategyResponse` (Trade or Hold)
3. **Voting** -- `FsiMajorityVoteByInstrument` aggregates per instrument with routing-score-weighted quantities. Deadlocks recorded as data inside `ConsensusResult`, not as pipeline failures
4. **Risk Assessment** -- `FsiRiskAssessor` classifies per-instrument risk (deadlock→HIGH, full liquidation→CRITICAL, portfolio percentage thresholds)
5. **Risk Gate** -- `FsiRiskGateRouting` routes HIGH/CRITICAL to `AgentRef.human(WorkItemCreateRequest)` for trader approval; LOW/MEDIUM passes through
6. **Execution** -- `FsiExecutionAgent` creates orders, fills, positions, ledger entries, and P&L attestations with quality dimension scoring

Each step is an `AgentRef.external()` in the Sequence -- reads from and writes to the mutable `ArenaContext`. See `docs/adr/` for architectural decisions (ADR-001 through ADR-006).

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

## Strategy Agents

7 strategy agents registered via `FsiStrategyAgentRegistrar` (eidos `AgentDescriptorRegistrar` SPI):

| Agent | StrategyType | Actor ID |
|---|---|---|
| `MomentumAgent` | MOMENTUM | `rule:momentum@v1` |
| `MeanReversionAgent` | MEAN_REVERSION | `rule:mean-reversion@v1` |
| `StatisticalArbitrageAgent` | STATISTICAL_ARBITRAGE | `rule:statistical-arbitrage@v1` |
| `MarketMakingAgent` | MARKET_MAKING | `rule:market-making@v1` |
| `EventDrivenAgent` | EVENT_DRIVEN | `rule:event-driven@v1` |
| `PortfolioRebalanceAgent` | PORTFOLIO_REBALANCE | `rule:portfolio-rebalance@v1` |
| `OvernightRiskAgent` | OVERNIGHT_RISK_MANAGEMENT | `rule:overnight-risk-management@v1` |

Each agent extends `AbstractStrategyAgent` and implements `evaluate(MarketSignal) → StrategyResponse`.

---

## JPA Entities

| Entity | Table | Key Fields |
|---|---|---|
| `OrderEntity` | `trade_order` | instrument, strategyId, side, orderType, quantity, limitPrice, fillPrice, status, rationale, caseInstanceId |
| `PositionEntity` | `position` | instrument, assetClass, strategyId, quantity, avgCost, unrealizedPnl, realizedPnl |
| `StrategyEntity` | `trading_strategy` | name, strategyType, instruments (text), parameters (text), active |
| `MarketEventEntity` | `market_event` | instrument, eventType, price, volume, data (text) |
| `ArenaRunEntity` | `arena_run` | instrument, status (IN_FLIGHT/COMPLETED/FAILED), idempotencyKey, resultJson |

---

## Known Issues

| # | Title | Status |
|---|---|---|
| [#24](https://github.com/casehubio/fsitrading/issues/24) | Sync consumer/contributor guides for Strategy Arena | Closed |

---

## What's Next

C1 (Strategy Arena) is complete. Remaining roadmap:

- C2: Event-driven arena triggering, multi-instrument expansion
- C3: Multi-agent strategy debate
- C4: SLA enforcement with escalation tiers (`FsiSlaBreachPolicy`)
- C5: Pages UI -- trading desk dock-workbench
- C6: Full CBR pipeline, advanced quality dimensions

---

## Design Documents

- `docs/DOMAIN.md` -- full domain background (automated trading, market microstructure, compliance frameworks)
- `docs/specs/2026-06-30-chapter3-trust-scoring-design.md` -- trust scoring design spec
- `docs/adr/INDEX.md` -- 6 architecture decision records (ADR-001 through ADR-006)
