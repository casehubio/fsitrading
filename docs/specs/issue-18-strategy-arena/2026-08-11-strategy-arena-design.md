# C1 Strategy Arena — Design Spec

**Date:** 2026-08-11
**Issue:** #18 (epic: C1 — Strategy Arena)
**Closes:** #12 (eidos registration), #13 (quality dimension scores — §4 documents C1 scope for third dimension)
**Decisions:** `decisions.md` in this directory (D1–D6)

---

## Summary

The Strategy Arena is a multi-agent trading evaluation system. A market signal arrives, the system triages which strategy agents should respond, dispatches them concurrently, aggregates their trade decisions via voting, risk-gates the consensus, and executes. Every step is auditable.

The arena is a **pure composition of blocks orchestration patterns** (D1). No case engine, no procedural orchestrator service. The arena is an `ExecutionModel<ArenaContext>` built at startup and invoked per REST trigger. Strategy agents implement blocks' agent interface directly (D2). Routing uses the platform's `RoutingSignalAssembler` via a multi-select adapter (D3). Aggregation is pluggable (D5). Risk gating and execution are pattern steps, not post-processing (D6).

---

## 1. Arena Architecture

### 1.1 Pattern Composition

```
Sequence [
  Supervisor    → triage: which strategies respond to this signal?
  Parallel      → evaluate: selected strategies run concurrently
  Voting        → aggregate: FsiMajorityVoteByInstrument
  ExternalAgent → assess risk: FsiRiskAssessor
  Conditional   → risk gate: HIGH or DEADLOCKED → HumanAgent, else pass-through
  ExternalAgent → execute: orders, positions, P&L attestations
]
```

Each step is a blocks pattern. The full arena is a `Sequence` of `ComposedAgent`s. Accountability listeners attach to the top-level Sequence — every step is captured in the ledger.

**FailurePolicy (per-pattern):** Each pattern builder carries its own `FailurePolicy` instance (`AbstractPatternBuilder.failurePolicy`). Non-default settings per step:
- **Supervisor:** `.onRoutingFailure(FAIL)` — no agents match → arena halts
- **Parallel:** `.maxAgentRetries(3)` with `onExhausted=SKIP` — partial failures tolerated; agents that fail after retries are excluded from voting
- **Voting:** `.onDeadlock(ESCALATE)` — safety net; should not fire since `FsiMajorityVoteByInstrument` always returns `Resolved`
- **All other steps (Risk Assessment, Conditional, Execution):** `FailurePolicy.defaults()` — `onRoutingFailure=FAIL`, `onDeadlock=FAIL`, `agentRetry=(3, 1s, FIXED, FAIL)`

### 1.2 ArenaContext

Mutable state flowing through the composition:

| Field | Set by | Type |
|---|---|---|
| `marketSignal` | Trigger (input) | `MarketSignal` |
| `selectedAgents` | Supervisor | `List<RoutingCandidate>` |
| `routingDecisions` | Supervisor | `List<io.casehub.blocks.routing.RoutingDecisionRecord>` — one per selected candidate |
| `evaluations` | Parallel | `Map<StrategyType, StrategyResponse>` |
| `consensus` | Voting | `ConsensusResult` |
| `riskAssessment` | Risk assessment | `RiskAssessment` |
| `approvalOutcome` | Conditional/Human | `ApprovalOutcome` |
| `executions` | ExternalAgent | `List<ExecutionResult>` |

**`ApprovalOutcome` values:**

| Value | Set when |
|---|---|
| `NOT_REQUIRED` | Risk is LOW — pass-through agent auto-approves |
| `APPROVED` | Human approves the consensus |
| `REJECTED` | Human rejects the consensus |
| `TIMEOUT` | Human approval expires (default: 4h — see §7.1) |

When `approvalOutcome` is `REJECTED` or `TIMEOUT`, the Execution step is a no-op — no trade is executed. `ArenaRunEntity` status is updated to `COMPLETED` (with outcome recorded) for `REJECTED`, or `FAILED` for `TIMEOUT`.

**Evaluation collection semantics:** The Parallel step collects `AgentResult.output()` from each agent after all agents complete (or are skipped via `onExhausted=SKIP`). Each successful agent's output is cast to `StrategyResponse` and recorded in `evaluations`. Agents that fail after exhausting retries produce no `SUCCESS` result and are excluded from both the evaluations map and subsequent voting. `ArenaContext` is NOT progressively mutated during Parallel execution — the evaluations map is populated post-completion from the collected `AgentResult` set.

### 1.3 Accountability

All 3 listeners attach to the top-level Sequence:

| Listener | Purpose |
|---|---|
| `LedgerExecutionListener` | EU AI Act Art.12 + MiFID II Art.17 compliance-grade audit. Every routing decision, evaluation, vote, risk gate, and execution gets a ledger entry with `causedByEntryId` chains. |
| `EventLogListener` | Operational event stream for debugging and monitoring. |
| `MetricsListener` | OTel histograms — strategy duration, routing counters, failure rates. |

### 1.4 ArenaConfiguration

CDI producer bean. Builds the composed `ExecutionModel<ArenaContext>` at startup:

1. Resolves all 7 `RoutingCandidate`s (AgentRef + AgentDescriptor pairs)
2. Builds the Supervisor with `FsiArenaRouting`
3. Builds the Parallel with all strategy agents
4. Builds the Voting with `FsiMajorityVoteByInstrument`
5. Builds the risk assessment `AgentRef.external()` (`FsiRiskAssessor`) — computes `RiskAssessment` from `ConsensusResult` and records it on `ArenaContext`
6. Builds the Conditional via `Patterns.<ArenaContext>conditional().when(c -> true, passThroughAgent).route(new FsiRiskGateRouting())`. The `.when()` call registers the pass-through agent as a candidate (required — `ExecutionModel` validates `candidateSupplier` non-null) and `.route()` overrides the default `FirstMatchRouting` with the custom strategy. `FsiRiskGateRouting` is a `RoutingStrategy<ArenaContext>` that reads `RoutingContext.state().riskAssessment()`: when `level == HIGH` or any instrument is deadlocked, it creates `AgentRef.human(approvalTemplate)` dynamically and returns `RoutingDecision.Selected(List.of(humanAgent))`; otherwise it selects the pass-through from `RoutingContext.candidates()`. The dynamic creation allows the `WorkItemCreateRequest` to include consensus-specific details from `ArenaContext`. The `approvalTemplate` is built in `FsiRiskGateRouting.route()`:
   - `title`: "Arena: High-Risk Consensus Approval — {instrument}"
   - `description`: consensus summary (instrument, side, quantity, risk level)
   - `types`: `["trade-approval"]`
   - `priority`: `HIGH`
   - `expiresAtBusinessHours`: 4 (configurable — the 4h human approval timeout from §7.1)
   - `permittedOutcomes`: `[APPROVE, REJECT]`
   - `scope`: `"fsitrading"`
   **Blocks API note:** `ConditionalBuilder` only exposes `.when()` as a public method to set the candidate roster (calling `protected AbstractPatternBuilder.agents()`). The `.when().route()` chain is a workaround — `.when()` sets both candidates AND routing, then `.route()` overrides just the routing. A cleaner API would be a public `candidates(AgentRef...)` method on `ConditionalBuilder`. Filed as blocks issue
7. Builds the execution `AgentRef.external()`
8. Composes via Sequence, attaches 3 accountability listeners, sets FailurePolicy
9. Produces the `ExecutionModel` as a CDI bean

The REST endpoint injects this bean and calls `.execute(arenaContext)`.

### 1.5 Voting Edge Cases

`FsiMajorityVoteByInstrument` handles:
- **Tie (equal BUY/SELL votes):** `FsiMajorityVoteByInstrument` always returns `AggregationResult.Resolved(ConsensusResult)` — never `AggregationResult.Deadlocked`. The `ConsensusResult` contains per-instrument `InstrumentConsensus` entries, each with status `CONSENSUS` or `DEADLOCKED`. Deadlocked instruments are flagged within the result, not via the aggregation failure path. The downstream risk assessment step (§1.4 step 5) marks any deadlocked instrument as `HIGH` risk. The Conditional then routes `HIGH`-risk results to `AgentRef.human()` for resolution.
- **All HOLD:** No trade. `ConsensusResult` records consensus HOLD with status `CONSENSUS`. ExternalAgent step becomes a no-op.
- **Mixed instruments:** Groups by instrument, votes independently per group. One instrument may reach consensus while another deadlocks — both are recorded in the same `ConsensusResult`.
- **All abstain for an instrument:** If all routed agents return `AgentResult.declined()` for an instrument, `FsiMajorityVoteByInstrument` records `InstrumentConsensus(status=NO_VOTERS)` for that instrument. `FsiRiskAssessor` treats `NO_VOTERS` the same as `DEADLOCKED` — routes to human. Semantically distinct from "all HOLD" (no strategy had an opinion vs. all strategies deliberately chose HOLD).
- **No-opinion agents:** `AgentResult.declined()` from blocks' native mechanism = abstention. Majority is of participating voters (agents with `StrategyResponse.Trade` or `StrategyResponse.Hold`), not roster size.
- **Quantity aggregation:** When a side wins the vote, the consensus quantity is the routing-score-weighted average of that side's proposed quantities: `Σ(quantity_i × score_i) / Σ(score_i)`, rounded to the nearest whole unit. Scores come from `ArenaContext.routingDecisions` — a `List<RoutingDecisionRecord>`, one per selected candidate (§3.1 step 9). Each record's `trustScoreAtRouting()` provides the blended routing score for that candidate. Lookup: `routingDecisions.stream().filter(r -> r.workerId().equals(agentName)).findFirst().map(RoutingDecisionRecord::trustScoreAtRouting).orElse(0.5)`. This uses the same scores that determined agent selection, ensuring consistency between routing and voting.

### 1.6 Supersedes

| Retired | Reason |
|---|---|
| `StrategyEvaluationCaseDefinition` | Replaced by blocks pattern composition (D1) |
| `StrategyEvaluationCaseDescriptor` | Constants migrate to arena config |
| `StrategyEvaluator` SPI | Replaced by direct blocks agents (D2) |
| `SimulatedOrderExecutor` | Execution logic moves into the arena's ExternalAgent step (see §1.7) |

### 1.7 ExternalAgent — Execution Step Detail

The ExternalAgent step (`AgentRef.external()`) replaces `SimulatedOrderExecutor`. Its `Function<ArenaContext, CompletionStage<AgentResult>>` performs all 6 operations that `SimulatedOrderExecutor` currently handles, within a `@Transactional` boundary:

| # | Operation | Current owner | Arena owner |
|---|---|---|---|
| 1 | Create `OrderEntity` from `TradeDecision` | `OrderService.createFromDecision()` | ExternalAgent (same service) |
| 2 | Simulate fill price and apply | `OrderService.fill()` | ExternalAgent (same service) |
| 3 | Apply fill to positions (P&L) | `PositionService.applyFill()` | ExternalAgent (same service) |
| 4 | Write `StrategyEvaluationLedgerEntry` | `TradingLedgerService` | ExternalAgent (same service) |
| 5 | Write `OrderExecutionLedgerEntry` | `TradingLedgerService` | ExternalAgent (same service) |
| 6 | Write `LedgerAttestation` (P&L + quality dims) | `PnlAttestationService` | ExternalAgent (same service) |

**Ledger entry responsibilities:**

- `LedgerExecutionListener` (accountability listener) writes **orchestration-level** ledger entries: one per step (routing decision, each agent dispatch, voting outcome, risk gate, execution dispatch). These form the generic audit trail with `causedByEntryId` chains. They capture what the arena did, not what the domain did.
- The ExternalAgent writes **domain-specific** ledger entries: `StrategyEvaluationLedgerEntry` (strategy name, instrument, signal, rationale) and `OrderExecutionLedgerEntry` (orderId, instrument, side, quantity, fillPrice). These capture domain semantics that the generic listener cannot. `causedByEntryId` on the domain entries points to the corresponding generic listener entry, linking the two audit trails.
- `PnlAttestationService` writes attestations (base + 3 quality dimensions per §4.2) linked to the evaluation entry.

---

## 2. Agent Identity — eidos Registration

Closes #12.

### 2.1 FsiStrategyAgentRegistrar

Implements `AgentDescriptorRegistrar` SPI. Registered as CDI bean, called at startup. Registers 7 `AgentDescriptor`s in the eidos `AgentRegistry`.

| StrategyType | actorId | Capabilities | Provider | Model | Slot | Persona |
|---|---|---|---|---|---|---|
| MOMENTUM | `rule:momentum@v1` | `momentum`, `trend-analysis` | `casehub-fsitrading` | `rule` | `executor` | `momentum@v1` |
| MEAN_REVERSION | `rule:mean-reversion@v1` | `mean-reversion`, `statistical` | `casehub-fsitrading` | `rule` | `executor` | `mean-reversion@v1` |
| STATISTICAL_ARBITRAGE | `rule:statistical-arbitrage@v1` | `statistical-arbitrage`, `pairs` | `casehub-fsitrading` | `rule` | `executor` | `statistical-arbitrage@v1` |
| MARKET_MAKING | `rule:market-making@v1` | `market-making`, `liquidity` | `casehub-fsitrading` | `rule` | `executor` | `market-making@v1` |
| EVENT_DRIVEN | `rule:event-driven@v1` | `event-driven`, `news` | `casehub-fsitrading` | `rule` | `executor` | `event-driven@v1` |
| PORTFOLIO_REBALANCE | `rule:portfolio-rebalance@v1` | `portfolio-rebalance`, `allocation` | `casehub-fsitrading` | `rule` | `executor` | `portfolio-rebalance@v1` |
| OVERNIGHT_RISK_MANAGEMENT | `rule:overnight-risk-management@v1` | `overnight-risk`, `defensive` | `casehub-fsitrading` | `rule` | `executor` | `overnight-risk-management@v1` |

Actor identity uses the existing `FsiActorIdentity.forStrategy()` convention.

**Runtime sync:** When a strategy is activated at runtime, `FsiStrategyAgentRegistrar` calls `AgentRegistry.register()` directly to add the new descriptor. Deactivation is handled at routing time: `FsiArenaRouting` (§3.1 step 1a) filters the candidate roster against `StrategyService.isActive(strategyType)` before scoring. This is a pre-routing filter, not a trust policy concern — a deactivated strategy is excluded regardless of its trust score. `AgentRegistry` has no `unregister()`, so the descriptor remains in the registry but is never routed to.

### 2.2 DispositionProfile

Each descriptor carries a `DispositionProfile` for `DispositionAwareRouting`:

| Axis | Momentum | Mean Rev. | Stat Arb | Mkt Making | Event Driven | Port. Rebal. | Overnight Risk |
|---|---|---|---|---|---|---|---|
| Risk appetite | aggressive | moderate | moderate | conservative | aggressive | conservative | conservative |
| Time horizon | medium | medium | short | short | short | long | medium |
| Market pref. | trending | range-bound | any | liquid | volatile | any | any |
| Reaction speed | moderate | slow | fast | fast | fast | slow | fast |

### 2.3 Strategy Agents

Each strategy type gets an `AgentRef.external()` wrapping its evaluation logic. Signature: `Function<ArenaContext, CompletionStage<AgentResult>>`. Receives the full `ArenaContext` including `AffordanceRenderer` output. Returns `AgentResult` containing `StrategyResponse`:

```java
public sealed interface StrategyResponse {
    record Trade(List<TradeDecision> decisions, String rationale) implements StrategyResponse {}
    record Hold(String rationale) implements StrategyResponse {}
}
```

- **Trade:** Agent proposes one or more `TradeDecision`s — deliberate trading signal with information content.
- **Hold:** Agent has evaluated the signal and deliberately chooses not to trade — a positive decision that participates in voting (counts toward majority).
- **Abstention:** Agent cannot evaluate this signal (timeout, out-of-domain). Expressed via blocks' native `AgentResult.declined(ref, reason)` — does not participate in voting.

At startup, each `(AgentRef, AgentDescriptor)` pair is assembled into a `RoutingCandidate`. The Supervisor's agent roster is the full set of 7 candidates.

---

## 3. Routing — Platform Bridge

### 3.1 FsiArenaRouting

Implements blocks' `RoutingStrategy<ArenaContext>`. Multi-select adapter that composes all 6 routing strategies — 4 `RoutingSignalProvider` beans via `RoutingSignalAssembler` plus 2 `AgentRoutingStrategy` invocations (LLM, CBR) — and selects all candidates above threshold:

1. Receives the `RoutingContext<ArenaContext>` (roster of `RoutingCandidate`s + `ArenaContext`)
1a. **Activation filter:** Filters candidates against `StrategyService.isActive(strategyType)`. Inactive strategies are excluded before scoring regardless of trust score.
2. **Type translation — `RoutingCandidate` → `AgentCandidate`:** `workerId` from `AgentRef.name()` (each strategy agent is constructed with `AgentRef.external(label, fn)` where label is the strategy type slug — e.g. `"momentum"`, `"mean-reversion"` — ensuring unique, stable identifiers), `capabilities` from `AgentDescriptor.capabilities()`, `runningJobs = 0` (not meaningful in blocks), `health = HEALTHY`, `agentDescriptor` from `RoutingCandidate.descriptor()`, `matchDegree = EXACT` (pre-registered), `violations = null`
3. **Type translation — `ArenaContext` → `AgentRoutingContext`:** `caseId = UUID.nameUUIDFromBytes(runId)` (deterministic synthetic ID), `capabilityName = "strategy-evaluation"`, `caseContext = arenaContextAsJsonNode`, `tenancyId = "fsitrading"`, `experiences` from `CaseMemoryStore`, `routingSignalWeights` from arena config
4. **Signal providers (4):** Calls `RoutingSignalAssembler.assemble(context, candidates)` to get per-candidate `RoutingSignal`s from all registered `RoutingSignalProvider` beans (Disposition, PlanComposition, Predecessor, Coordination)
5. **LLM routing:** Calls `LlmAgentRoutingStrategy.select(context, candidates)`. Converts the single-select `RoutingResult.assigned(workerId)` to a per-candidate score: selected candidate gets 1.0, others 0.0 for the `"llm"` signal. If LLM returns `unresolvable`, the signal is omitted (no score contribution).
6. **CBR routing:** Calls `CbrAgentRoutingStrategy.select(context, candidates)`. Same single-to-multi conversion as LLM. If CBR returns `unresolvable` (insufficient history), the signal is omitted.
7. **Blending:** Computes blended score per candidate across all 6 signals using configurable weights (default: equal weight for present signals). Signals that were omitted (unresolvable) are excluded from the weight normalization.
8. **Multi-select:** Selects all candidates with blended score ≥ configurable minimum (default 0.3). Returns `RoutingDecision.Selected(List<AgentRef>)`
9. Records one `io.casehub.blocks.routing.RoutingDecisionRecord` per selected candidate on `ArenaContext.routingDecisions`. Each record: `capabilityTag = "strategy-evaluation"`, `workerId = agentRef.name()`, `trustScoreAtRouting = blendedScore`, `thresholdApplied = minimumThreshold`, `evidenceEntryId = null` (no ledger entry at routing time; populated later by `LedgerExecutionListener`)

The `AgentRoutingStrategy` interface (`select()`) is single-select by design — it returns one winner. For the arena's multi-select needs, `FsiArenaRouting` treats LLM and CBR results as binary scoring signals (1.0/0.0) that participate in blending alongside the 4 continuous signal-provider scores. All 6 routing strategies are invoked and influence candidate selection: 4 (`RoutingSignalProvider` beans) provide continuous per-candidate scores (0.0–1.0), 2 (`AgentRoutingStrategy` implementations) provide binary endorsements (1.0 for the selected candidate, 0.0 for others). With equal weights, a binary endorsement contributes ≈1/6 of the blended score — sufficient to push a marginal candidate above or below the selection threshold, but without the score gradation of continuous signals. **C1 limitation:** LLM/CBR signals are binary, not graduated. Genuine multi-select scoring (confidence-ranked candidate lists) is a C6 improvement when the CBR pipeline supports graduated scoring.

### 3.2 Six Routing Strategies

All platform-provided. fsitrading supplies configuration only.

| Strategy | Interface | fsitrading configuration |
|---|---|---|
| `LlmAgentRoutingStrategy` | `AgentRoutingStrategy` | Prompt context: instrument, event type, magnitude, time of day |
| `CbrAgentRoutingStrategy` | `AgentRoutingStrategy` | `FsiCbrOutcomeWeights` — P&L-weighted outcomes |
| `DispositionAwareRouting` | `RoutingSignalProvider` | `DispositionProfile` per strategy type (§2.2) |
| `PlanCompositionAnalyser` | `RoutingSignalProvider` | Multi-step plan outcome scoring |
| `PredecessorAnalyser` | `RoutingSignalProvider` | What worked after previous steps |
| `CoordinationSignalProvider` | `RoutingSignalProvider` | Which strategy combinations work well together |

### 3.3 Trust Routing Policy

`FsiTrustRoutingPolicyProvider`:
- Minimum trust score: 0.4 (below → excluded)
- Bootstrap threshold: 10 observations (benefit of the doubt)
- Quality floor: 0.3 (minimum quality dimension score)

### 3.4 CBR Routing Prompt

`CbrRoutingPromptSection` injects historical arena outcomes into the LLM routing prompt.

### 3.5 Dependency Note

The routing bridge depends only on public SPI types: `RoutingSignalAssembler`, `AgentRoutingContext`, `AgentCandidate`, `RoutingSignalProvider`, `AgentRoutingStrategy` — all in `io.casehub.api.spi.routing` (engine-api). No dependency on engine-internal packages. The `RoutingSignalProvider` implementations (`DispositionAwareRouting`, etc.) are in `io.casehub.blocks.routing.agent` (blocks). `LlmAgentRoutingStrategy` and `CbrAgentRoutingStrategy` are also in `io.casehub.blocks.routing.agent` — they implement `AgentRoutingStrategy` (single-select) rather than `RoutingSignalProvider` (per-candidate scoring), so `FsiArenaRouting` invokes them directly (§3.1 steps 5–6) and converts their results to scoring signals for blending.

---

## 4. Quality Dimension Scores

Closes #13.

**Dimension substitution note:** Issue #13 specifies `risk-reward-ratio` (P&L vs max drawdown), `market-timing` (entry/exit vs optimal), and `position-sizing` (actual vs Kelly criterion). These require intra-position drawdown tracking, hindsight-optimal price analysis, and probability estimation infrastructure — none of which exist in C1. The three dimensions below are C1-practical proxies that are computable from available data (`FillResult`, `PositionEntity.openedAt`, `SyntheticMarketDataProvider` price history). The #13 dimensions are targets for C6 when additional data tracking is in place.

### 4.1 Three Dimensions

| Dimension | Measures | Formula | Range |
|---|---|---|---|
| `return-magnitude` | Profitability relative to position size | `max(0.0, min(1.0, realizedPnl / closedNotional))` | 0.0–1.0 |
| `hold-period-efficiency` | P&L per unit time | `sigmoid(realizedPnl / holdPeriodMinutes, scale=0.1)` | 0.0–1.0 |
| `risk-adjusted-return` | Return relative to volatility exposure | `sigmoid(realizedPnl / (positionSize × recentVolatility), scale=1.0)` | 0.0–1.0 |

**Formula notes:**
- `return-magnitude` uses signed `realizedPnl` (not absolute value). Losses clamp to 0.0, break-even to 0.0, profitable trades scale up to 1.0. This makes it a profitability metric — the quality floor (0.3) correctly excludes strategies that lose money on this dimension.
- `hold-period-efficiency` uses sigmoid to map `ℝ → (0, 1)`. Values below 0.5 indicate unprofitable-per-unit-time (negative input to sigmoid). The quality floor of 0.3 tolerates moderately negative efficiency but excludes severely unprofitable hold periods. **Edge case:** if `holdPeriodMinutes < 1` (sub-minute round-trip), clamp to 1 minute. This prevents division-by-zero and avoids sigmoid saturation on near-instantaneous trades.
- `risk-adjusted-return` uses sigmoid normalization to produce a genuine 0–1 range. Raw `realizedPnl / (positionSize × recentVolatility)` is unbounded; sigmoid maps it to (0, 1) with 0.5 as the break-even point. **Edge cases:** if `recentVolatility < 0.0001` (insufficient data or flat market), default score to 0.5 (break-even). Volatility requires a minimum of 10 ticks from `SyntheticMarketDataProvider`; fewer → default to 0.5.

### 4.2 Integration

`PnlAttestationService.recordOutcome()` calls `FsiQualityDimensionScorer` after computing the base attestation. The scorer writes **three additional attestations** — one per quality dimension — using the foundation's dedicated fields:

- Each attestation sets `trustDimension` to the dimension name (e.g. `"return-magnitude"`)
- Each attestation sets `dimensionScore` to the computed score (0.0–1.0)
- `capabilityTag`, `attestorId`, `ledgerEntryId`, `subjectId` match the base attestation
- `verdict` and `confidence` on the base attestation remain unchanged (existing P&L scoring)

This means `PnlAttestationService.recordOutcome()` writes **four attestations total** per outcome: one base (existing SOUND/FLAGGED with confidence) plus three dimension attestations. The foundation's trust scoring machinery reads `trustDimension`/`dimensionScore` for quality floor filtering — these fields are purpose-built for this.

### 4.3 Data Requirements

- `return-magnitude`: already available from `FillResult`
- `hold-period-efficiency`: add `openedAt` to `PositionEntity` (set on first same-direction fill)
- `risk-adjusted-return`: computed from `SyntheticMarketDataProvider` price history (stddev of returns over last 100 ticks)

**Flyway migration:** `V101__add_position_opened_at.sql` adds the `opened_at` column to the `position` table (nullable `TIMESTAMP`). Existing positions will have `NULL` `openedAt` — quality dimension scoring skips `hold-period-efficiency` for positions with no `openedAt` (score defaults to 0.5, the sigmoid midpoint).

### 4.4 Routing Integration

Trust routing policy's quality floor (0.3) uses the minimum across all three dimensions. A strategy that profits but with poor hold-period efficiency or poor risk-adjusted return gets downranked.

---

## 5. AffordanceRenderer — Structured Agent Context

### 5.1 Rendered Output

Strategy agents receive structured context via `AffordanceRenderer`:

```
== AAPL (Apple Inc, EQUITY, NASDAQ) ==
  Position: +100 @ $182.30 avg (unrealized P&L: +$320)
  Last price: $185.50 (↑ 1.2% today)
  Actions: BUY (limit/market, max 400), SELL (limit/market, max 100), HOLD
  Constraints: max position 500 shares, daily loss limit $5,000
```

### 5.2 Data Sources

| Element | Source |
|---|---|
| Instrument metadata | `Instrument` record |
| Position | `PositionEntity` (quantity, avgCost, unrealizedPnl from last price) |
| Last price | `MarketSignal` from trigger payload |
| Allowed actions | Derived: BUY if below max position, SELL if holding, HOLD always |
| Constraints | `ArenaConfiguration` defaults, overridable per `StrategyEntity.parameters` JSON |

### 5.3 FsiAffordanceProvider

CDI bean. Assembles `ObservableEntity` instances from current state. Called during the Parallel step. Each agent receives `renderEntities()` (structured text) and `renderActionVocabulary()` (valid actions).

Structured text serves both rule-based agents (parsed) and future LLM agents (prompt context). One rendering for both.

### 5.4 Scope

C1: instruments from the trigger's `MarketSignal`. C2 expands to all instruments under observation.

---

## 6. Agent Memory

### 6.1 Write Path

After each arena run, `MemoryEmitter.emit()` records the full run to `CaseMemoryStore`. Fire-and-forget — memory writes must not block the arena response path.

**Monitoring:** `MemoryEmitter` increments OTel counter `fsi.memory.write.total` on every attempt, and `fsi.memory.write.failures` on exceptions. `MetricsListener` (§1.3) exposes these via the existing metrics endpoint. A persistent write failure rate above 10% degrades CBR routing quality (§6.3) — the `CbrAgentRoutingStrategy` operates on survivorship-biased data. No automatic fallback is specified for C1; C6's full CBR pipeline adds a staleness check.

| Field | Content |
|---|---|
| Features | Market signal characteristics (instrument, event type, price, volatility, time of day) |
| Solution | Routing decision + consensus result |
| Outcome | Execution results + attestation verdicts |

### 6.2 Read Path

Each strategy agent's closure captures a reference to the CDI-managed `CaseMemoryStore` bean. At execution time (when the Parallel step dispatches the agent), the agent queries its own episodic memories directly:

`caseMemoryStore.query(MemoryQuery.forEntity(agentEntityId, MemoryDomain.AGENT, "fsitrading").withLimit(10))`

where `agentEntityId` is the agent's `actorId` (e.g. `"rule:momentum@v1"`). This is a per-agent runtime query, not a shared-context injection. Each agent uses its memories internally during evaluation — they do not appear on `ArenaContext`.

**Note:** The replan spec referenced `EpisodicMemoryConfig` (an engine `CaseDefinition` concept). The arena is a blocks orchestration with no case lifecycle, so episodic memory is queried directly via `CaseMemoryStore` at agent execution time rather than engine auto-injection. The CDI producer (`ArenaConfiguration`) builds only the static `ExecutionModel` structure at startup — all runtime state (including memory queries) happens within agent closures during execution.

### 6.3 Routing Integration

`CbrAgentRoutingStrategy` reads from the same memory store. `FsiCbrOutcomeWeights` weights past outcomes by P&L. Memory serves both the agent (episodic recall) and the routing (CBR signal).

### 6.4 Scope

C1: record and recall. C6 adds the full 4-step CBR pipeline (Retrieve → Reuse → Revise → Retain with DTW/edit-distance similarity).

---

## 7. REST Endpoints

### 7.1 New Endpoints

| Method | Path | Purpose |
|---|---|---|
| `POST` | `/api/evaluations/trigger` | Trigger an arena run (idempotent — see below) |
| `GET` | `/api/routing/decisions` | Recent routing decisions (paginated) |
| `GET` | `/api/routing/decisions/latest` | Most recent routing decision |
| `GET` | `/api/kpis` | Aggregated KPIs (see below) |
| `GET/PUT` | `/api/preferences/trust-routing` | Trust routing thresholds per strategy |

**Idempotency and concurrency control:**

- **Idempotency:** `POST /api/evaluations/trigger` accepts an optional `idempotencyKey` header (UUID). If provided, `EvaluationResource` checks `ArenaRunEntity` for a matching key. If a run with that key exists and completed, the existing `ArenaResult` is returned (HTTP 200). If in-flight, HTTP 409 Conflict. If absent, the run proceeds normally. If the header is omitted, no idempotency check is performed (fire-and-forget mode for development).
- **Per-instrument concurrency:** `EvaluationResource` creates an `ArenaRunEntity(instrument, status=IN_FLIGHT)` before starting the arena run. Concurrency is enforced by a **partial unique index** (PostgreSQL): `CREATE UNIQUE INDEX idx_arena_run_inflight ON arena_run(instrument) WHERE status = 'IN_FLIGHT'`. This allows multiple `COMPLETED`/`FAILED` rows per instrument (run history is preserved for idempotency and audit) while preventing concurrent `IN_FLIGHT` runs. If another run for the same instrument is already in-flight, the `INSERT` violates the partial index and the trigger returns HTTP 409 Conflict with the existing run's `runId`. On completion (success or failure), the status is updated to `COMPLETED` or `FAILED`. **Crash recovery:** `ArenaConfiguration` (`@Startup` CDI bean) runs a recovery sweep on boot: `UPDATE arena_run SET status = 'FAILED', reason = 'server_restart_recovery' WHERE status = 'IN_FLIGHT'`. This prevents orphaned IN_FLIGHT rows from permanently blocking an instrument after a server crash. Flyway migration: `V102__create_arena_run.sql`.
- **Timeout:** No overall Sequence timeout — the arena run may block on human approval. Per-step timeouts: strategy agents in the Parallel step have 30s timeout (configured via `FailurePolicy.agentRetry`). The automated pipeline (Supervisor → Parallel → Voting → Risk Assessment) completes within seconds; the overall duration is dominated by human approval when triggered. Human approval in the Conditional step has a configurable timeout (default: 4h for C1), wired through `WorkItemCreateRequest.expiresAtBusinessHours(4)` on the approval template built by `FsiRiskGateRouting` (§1.4 step 6). If the human approval timeout expires, the arena run records `ApprovalOutcome.TIMEOUT` and aborts — no trade is executed. The `ArenaRunEntity` status is updated to `FAILED`. Stale approval requests are a known C1 limitation; C4's `FsiSlaBreachPolicy` adds escalation tiers.

**Abort handling:** When the arena run fails mid-sequence (e.g., Parallel step fails after retries, timeout, or system error), `LedgerExecutionListener.onExecutionComplete()` records an `ArenaAbortedRecord` ledger entry with: `failedStep` (which pattern step failed), `reason` (exception message or timeout), `lastSuccessfulStep` (the last step that completed). This distinguishes "system error aborted the run" from "strategies evaluated but chose not to trade" in the audit trail. Regulators querying the ledger will see an explicit abort entry with explanation, not a silent gap in the causality chain.

### 7.2 KPI Content

`GET /api/kpis` returns all-time aggregated metrics with per-strategy breakdown:

| KPI | Definition | Data source |
|---|---|---|
| `totalPnl` | Sum of realized P&L from closed positions | `PositionEntity` (realized P&L on position close) |
| `winRate` | % of profitable position closes (SOUND attestations / total attestations) | `LedgerAttestation` verdict counts |
| `tradeCount` | Total number of closed positions | `PositionEntity` |
| `avgReturn` | Mean realized P&L per closed position | `PositionEntity` |
| `perStrategy` | Array of `{strategyType, totalPnl, winRate, tradeCount, trustScore}` | Above sources + `TrustExportService` |

Response schema: `{ totalPnl, winRate, tradeCount, avgReturn, perStrategy: [...] }`. C5 adds time-windowed views (daily, weekly).

### 7.3 ArenaResult Response

```json
{
  "runId": "uuid",
  "marketSignal": { "instrument": "AAPL", "eventType": "PRICE_MOVEMENT", "price": 185.50 },
  "routing": {
    "selectedStrategies": ["MOMENTUM", "EVENT_DRIVEN", "MARKET_MAKING"],
    "signals": { "llm": "...", "cbr": "...", "disposition": "..." }
  },
  "evaluations": [
    { "strategyType": "MOMENTUM", "decisions": [{ "instrument": "AAPL", "side": "BUY", "quantity": 50 }] },
    { "strategyType": "EVENT_DRIVEN", "decisions": [{ "instrument": "AAPL", "side": "BUY", "quantity": 30 }] },
    { "strategyType": "MARKET_MAKING", "decisions": [{ "instrument": "AAPL", "side": "HOLD" }] }
  ],
  "consensus": [
    { "instrument": "AAPL", "side": "BUY", "quantity": 40, "votes": { "BUY": 2, "HOLD": 1 } }
  ],
  "riskAssessment": { "level": "LOW" },
  "executions": [
    { "orderId": "uuid", "instrument": "AAPL", "side": "BUY", "quantity": 40, "fillPrice": 185.52, "status": "FILLED" }
  ]
}
```

### 7.4 Existing Endpoints

Unchanged: `/api/strategies`, `/api/trust/strategies`, `/api/orders`, `/api/positions`, `/api/market-data`, `/api/audit`.

---

## 8. UI Panels

C1 implements backend and data contracts. C5 wires panels into the Trading Desk dock-workbench.

| Panel | blocks-ui Component | Data Source |
|---|---|---|
| Strategy list | `list-pane` | `GET /api/strategies` |
| Strategy detail | `detail-pane` | Selection event |
| Trust scores | `trust-score-panel` | `GET /api/trust/strategies` + TrendSourceMixin |
| Trust composite | `trust-workbench` | Score + routing history + feedback |
| Routing explanation | `routing-rationale` | `GET /api/routing/decisions/latest` |
| Post-trade trust delta | `trust-feedback-display` | pages-event on trade completion |
| KPI metrics | `kpi-metric-row` | `GET /api/kpis` |
| Trust routing config | `preferences-editor` | `GET/PUT /api/preferences/trust-routing` |

---

## 9. Code Changes

### 9.1 Retired

| File | Reason |
|---|---|
| `StrategyEvaluator` (api SPI) | Replaced by direct blocks agents (D2) |
| `StrategyEvaluationCaseDefinition` | Replaced by blocks pattern composition (D1) |
| `StrategyEvaluationCaseDescriptor` | Constants migrate to arena config |
| `SimulatedOrderExecutor` | Execution logic moves into arena's ExternalAgent step (§1.7) |

### 9.2 Modified

| File | Change |
|---|---|
| `PnlAttestationService` | Extended with `FsiQualityDimensionScorer` call (writes 3 dimension attestations per §4.2) |
| `PositionEntity` | Add `openedAt` field (`V101__add_position_opened_at.sql`) |
| `FsiCapabilities` | Add arena capability constants |

### 9.3 New

| Component | Module | Purpose |
|---|---|---|
| `FsiStrategyAgentRegistrar` | app | Registers 7 AgentDescriptors at startup |
| `FsiArenaRouting` | app | Multi-select RoutingStrategy using RoutingSignalAssembler |
| `FsiRiskAssessor` | app | ExternalAgent: computes RiskAssessment from ConsensusResult |
| `FsiRiskGateRouting` | app | RoutingStrategy\<ArenaContext\> for Conditional — reads risk assessment, routes HIGH/deadlocked to human |
| `StrategyResponse` | api | Sealed interface: Trade / Hold |
| `FsiMajorityVoteByInstrument` | app | AggregationStrategy — groups by instrument, majority vote |
| `FsiQualityDimensionScorer` | app | Computes 3 quality dimension scores |
| `FsiAffordanceProvider` | app | Assembles ObservableEntity instances |
| `FsiCbrOutcomeWeights` | app | CbrOutcomeWeights — P&L-weighted outcomes |
| `FsiTrustRoutingPolicyProvider` | app | Trust routing thresholds per strategy |
| `ArenaContext` | app | Mutable state through pattern composition |
| `ArenaConfiguration` | app | CDI producer: builds ExecutionModel at startup |
| `MarketSignal` | api | Arena trigger input (see §9.5) |
| `ConsensusResult` | api | Voting output (contains `List<InstrumentConsensus>`) |
| `InstrumentConsensus` | api | Per-instrument voting result: status (CONSENSUS / DEADLOCKED / NO_VOTERS), winning side, quantity |
| `ArenaRunEntity` | app | JPA entity for arena run deduplication (idempotency key, status, timestamps) |
| `ArenaRunEntity` | app | JPA entity: tracks in-flight arena runs with unique constraint on (instrument, IN_FLIGHT) |
| `ArenaResult` | app | REST response |
| 7× strategy agent classes | app | One per StrategyType |
| `EvaluationResource` | app | POST /api/evaluations/trigger |
| `RoutingDecisionResource` | app | GET /api/routing/decisions |
| `KpiResource` | app | GET /api/kpis |
| `PreferencesResource` | app | GET/PUT /api/preferences/trust-routing |

### 9.4 Module Placement

`MarketSignal`, `ConsensusResult`, `InstrumentConsensus`, and `StrategyResponse` in `api/` (pure Java, no framework deps). Everything else in `app/`.

### 9.5 MarketSignal Definition

```java
public record MarketSignal(
    String instrument,
    String eventType,
    BigDecimal price,
    BigDecimal volume,
    Instant timestamp
) {}
```

**Relationship to `MarketEventEntity`:** `MarketSignal` is a lightweight API record for arena triggering. On REST trigger, `EvaluationResource` persists a `MarketEventEntity` from the signal payload (for audit continuity with existing ledger entries), then passes the `MarketSignal` to the arena. `SyntheticMarketDataProvider` gains a `toMarketSignal()` conversion method for test/dev integration.
