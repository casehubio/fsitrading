# Chapter 3 — Trust Scoring from P&L Attestations

**Issue:** #8  
**Branch:** `8-chapter3-trust-scoring`  
**Stacked on:** `7-chapter1-domain-baseline` (Chapters 1–2)  
**Date:** 2026-06-30

---

## Summary

After each position close, write a `LedgerAttestation` recording whether the strategy's trade decision was profitable (SOUND) or unprofitable (FLAGGED), weighted by P&L magnitude. The foundation's trust scoring machinery (Bayesian Beta) computes per-strategy trust scores automatically. A REST endpoint exposes scores to traders.

**Key design decision:** No new `LedgerEntry` subclass. The ARC42STORIES.MD spec proposed `OutcomeAttestationEntry`, but the foundation's existing `LedgerAttestation` already provides `verdict`, `capabilityTag`, `trustDimension`, `dimensionScore`, and `confidence`. Using the existing primitive avoids redundant schema, follows the consumer pattern, and integrates directly with the trust scoring pipeline.

**ARC42STORIES.MD correction required:** Chapter 3's entry must be updated to reflect: (1) `LedgerAttestation` replaces `OutcomeAttestationEntry`, (2) incremental trust updates supplement the nightly job (not replace it — the nightly `TrustScoreJob` remains as a consistency backstop).

**Design limitation:** Chapter 3's binary SOUND/FLAGGED model is a bootstrapping approximation, not a complete trading performance measure. It captures win/loss ratio weighted by magnitude but does NOT capture risk-adjusted returns, drawdown patterns, time-weighted performance, or correlation with market conditions. Quality dimensions (#13) address some of these via `trustDimension` in a future chapter.

---

## 1. Actor Identity Model

### Problem

`TradingLedgerService` writes `actorId = "fsi-trading-executor"` on all ledger entries. Trust scoring groups by `actorId` — a shared ID means all strategies get one aggregated score.

### Solution

Each strategy type gets a unique actor identity following the platform convention (ledger ADR 0004):

```
Format: "rule:{strategy-type-kebab-case}@v1"
```

| StrategyType | actorId |
|---|---|
| `MOMENTUM` | `"rule:momentum@v1"` |
| `MEAN_REVERSION` | `"rule:mean-reversion@v1"` |
| `STATISTICAL_ARBITRAGE` | `"rule:statistical-arbitrage@v1"` |
| `MARKET_MAKING` | `"rule:market-making@v1"` |
| `EVENT_DRIVEN` | `"rule:event-driven@v1"` |
| `PORTFOLIO_REBALANCE` | `"rule:portfolio-rebalance@v1"` |
| `OVERNIGHT_RISK_MANAGEMENT` | `"rule:overnight-risk-management@v1"` |

- **Model family:** `"rule"` — these are rule-based strategies, not LLM agents
- **Persona:** derived from `StrategyType` enum, kebab-cased
- **Major version:** `@v1` — version bump resets trust to Beta(1,1) = 0.5 prior
- **ActorTypeResolver:** the format matches the `[\w-]+:[\w-]+@[\w.]+` regex and resolves to `ActorType.AGENT`

### Granularity

Trust is scored per strategy TYPE, not per strategy INSTANCE. Multiple strategies of the same type contribute to a single trust score. This answers "does momentum work?" — not "does this parameter set work?"

### Components

**`FsiActorIdentity`** (api/ module, pure Java):
- `forStrategy(StrategyType) → String` — derives actorId from strategy type
- `actorRole(StrategyType) → String` — returns display role (e.g., `"momentum-strategy"`)

### Breaking changes

- `TradingLedgerService.recordStrategyEvaluation()` — takes `StrategyType` parameter, derives `actorId` via `FsiActorIdentity` instead of hardcoding
- `TradingLedgerService.recordOrderExecution()` — keeps `actorId = "fsi-trading-executor"` (executor is a separate actor)
- `SimulatedOrderExecutor` — passes strategy info to `recordStrategyEvaluation()`

### Load-bearing invariant

The actorId split between eval and execution entries is critical for trust scoring correctness. The trust scorer groups attestations by the `actorId` of the attested entry. If the `OrderExecutionLedgerEntry` carried the strategy's actorId, attestations against it would score the strategy — but the execution entry represents the executor's action, not the strategy's decision. Attestations must target `StrategyEvaluationLedgerEntry` (the decision) to score the decision-maker.

### Version bump criteria

Bump `@v1` → `@v2` (resetting trust to Beta(1,1) = 0.5) when: strategy evaluation logic changes fundamentally (different signals, different instruments, different risk model). Do NOT bump for: parameter tuning, threshold adjustments, bug fixes that don't change the decision model.

---

## 2. P&L Attestation Service

### Trigger

Attestations fire only when positions close with non-zero realized P&L (opposite-direction fills). Same-direction fills (adding to position) produce no attestation. Break-even closes (realizedPnl == 0) produce no attestation — zero P&L is not evidence of decision quality and would inflate trust scores by rewarding volume of neutral activity.

### Transactional boundary

`executeDecision()` must be `@Transactional` to ensure atomicity across all 6 steps. The flow spans two datasources (default for domain tables, qhorus for ledger tables) — Quarkus Narayana JTA coordinates XA transactions automatically. The `AttestationRecordedEvent` with `TransactionPhase.AFTER_SUCCESS` fires after the outer JTA transaction commits, ensuring all data is durable before the incremental trust update runs.

### Flow

```
SimulatedOrderExecutor.executeDecision():
  1. Create order from TradeDecision
  2. Determine fill price, fill order
  3. Apply fill to position → FillResult (includes realizedPnl)
  4. Write StrategyEvaluationLedgerEntry (per-strategy actorId) → evalEntryId
  5. Write OrderExecutionLedgerEntry (causedByEntryId = evalEntryId)
  6. IF FillResult.hasRealizedPnl() → PnlAttestationService.recordOutcome()
```

### PositionService change

`applyFill()` currently returns `void`. Changed to return `FillResult`:

```java
public record FillResult(
    PositionEntity position,
    BigDecimal realizedPnl,      // non-null only on position close
    BigDecimal closedNotional    // |closedQuantity * fillPrice| (NOT avgCost — avoids penny-stock inflation)
) {
    public boolean hasRealizedPnl() {
        return realizedPnl != null && realizedPnl.signum() != 0;
    }
}
```

`closedNotional` uses `fillPrice * closedQuantity` (not `avgCost`) to normalize confidence across price levels. A $0.10 penny stock with avgCost=$0.05 would produce `confidence = |$0.05 / $0.05| = 1.0` — massive overweighting. Using fillPrice: `|$0.05 / $0.10| = 0.5` — proportional.

### PnlAttestationService

Writes `LedgerAttestation` via `LedgerEntryRepository.saveAttestation()` (triggers `AttestationRecordedEvent` for incremental trust updates).

| Attestation field | Value |
|---|---|
| `ledgerEntryId` | `StrategyEvaluationLedgerEntry.id` (the decision being judged) |
| `subjectId` | `orderId` (same subject as the ledger entries) |
| `attestorId` | `"fsi-pnl-system"` |
| `attestorType` | `ActorType.SYSTEM` |
| `attestorRole` | `"pnl-attestor"` |
| `verdict` | `SOUND` if `realizedPnl > 0`, `FLAGGED` if `realizedPnl < 0` (zero excluded — no attestation) |
| `confidence` | `min(1.0, max(0.1, 10.0 * \|realizedPnl\| / closedNotional))` |
| `capabilityTag` | `FsiCapabilities` constant for the strategy type (see mapping table below) |
| `evidence` | JSON: `{"realizedPnl": "250.00", "closedNotional": "5000.00", "fillPrice": "55.00", "closedQuantity": "100", "holdPeriodMinutes": 1440}` |
| `occurredAt` | `Instant.now()` |

### Verdict mapping

- Profit (`realizedPnl > 0`) → `SOUND` (increments α in Bayesian Beta)
- Loss (`realizedPnl < 0`) → `FLAGGED` (increments β)
- Break-even (`realizedPnl == 0`) → no attestation (not evidence of decision quality)

### Confidence

Encodes P&L magnitude relative to position size. `scaleFactor = 10.0`:

| Return | Confidence |
|---|---|
| 1% | 0.10 (clamped floor) |
| 5% | 0.50 |
| 10%+ | 1.00 (clamped ceiling) |

### StrategyType → capabilityTag mapping

| StrategyType | capabilityTag (FsiCapabilities constant) |
|---|---|
| `MOMENTUM` | `"momentum"` |
| `MEAN_REVERSION` | `"mean-reversion"` |
| `STATISTICAL_ARBITRAGE` | `"statistical-arbitrage"` |
| `MARKET_MAKING` | `"market-making"` |
| `EVENT_DRIVEN` | `"event-driven"` |
| `PORTFOLIO_REBALANCE` | `"portfolio-rebalance"` |
| `OVERNIGHT_RISK_MANAGEMENT` | `"overnight-risk-management"` |

---

## 3. Configuration

### application.properties additions

```properties
casehub.ledger.trust-score.enabled=true
casehub.ledger.trust-score.incremental.enabled=true
casehub.ledger.trust-score.materialization.enabled=true
```

### No new Flyway migrations

Using existing foundation tables:
- `ledger_attestation` (V1000) — stores attestations
- `actor_trust_score` (V1001) — stores computed trust scores

### No trust-routing.yaml

Trust routing policy configuration belongs to Chapter 4. Chapter 3 computes and exposes scores only.

### Hibernate/datasource

No changes — `io.casehub.ledger.runtime.model` is already in the qhorus persistence unit's packages.

---

## 4. Trust Score REST Endpoint

### Resource

`TrustScoreResource` at `/api/trust/strategies`

| Method | Path | Returns |
|---|---|---|
| `GET` | `/api/trust/strategies` | All active strategies with trust scores |
| `GET` | `/api/trust/strategies/{strategyType}` | Single strategy type trust score |

### Response: `StrategyTrustView`

```json
{
  "strategyType": "MOMENTUM",
  "actorId": "rule:momentum@v1",
  "trustScore": 0.72,
  "decisionCount": 45,
  "phase": "BOOTSTRAP | ACTIVE",
  "attestationSummary": {
    "positive": 28,
    "negative": 17
  }
}
```

- `trustScore` — capability-scoped Bayesian Beta (α/(α+β)), or `null` if no attestations
- `decisionCount` — count of `StrategyEvaluationLedgerEntry` records for this actor
- `phase` — `BOOTSTRAP` if below observation threshold, `ACTIVE` otherwise
- 404 for unknown strategy type

### Implementation

Injects `TrustExportService` (not `TrustGateService` — export service provides `attestationPositive`/`attestationNegative` counts that `TrustGateService` does not expose) and `StrategyService`. For each active strategy, derives actorId via `FsiActorIdentity.forStrategy()` and queries CAPABILITY-scoped trust scores. The score type is CAPABILITY (scoped to the strategy's `capabilityTag`) — consistent with what `TrustWeightedAgentStrategy` will query in Chapter 4.

### Phase classification

- `BOOTSTRAP`: `decisionCount < 10` (matches platform default `minimumObservations`)
- `ACTIVE`: `decisionCount >= 10`

---

## 5. Test Strategy

### Unit tests (api/ module, plain JUnit)

| Test | Verifies |
|---|---|
| `FsiActorIdentityTest` | Actor ID derivation for each StrategyType; format matches ActorTypeResolver regex |
| `FillResultTest` | `hasRealizedPnl()`: null → false, zero → false, non-zero → true |

### Unit tests (app/ module, mocked dependencies)

| Test | Verifies |
|---|---|
| `PnlAttestationServiceTest` | Verdict mapping (profit→SOUND, loss→FLAGGED); confidence calculation (scaling, clamping to [0.1, 1.0]); no attestation when realizedPnl is null or zero; evidence field contains structured JSON |

### Integration tests (app/ module, @QuarkusTest)

| Test | Verifies |
|---|---|
| `PnlAttestationIntegrationTest` | Full pipeline: strategy → trade → position close → LedgerAttestation written with correct fields |
| `TrustScoreComputationTest` | Attestation → trust score: write attestations → verify ActorTrustScore rows with correct Bayesian Beta |
| `TrustScoreResourceTest` | REST endpoints: list all scores, single score, 404 for unknown, null score for bootstrap |
| `SimulatedOrderExecutorTest` (extended) | Per-strategy actorId on eval entries; attestation on position close; no attestation on same-direction fill; no attestation on break-even close; transactional atomicity (all-or-nothing across both datasources) |

---

## 6. What This Does NOT Include

| Concern | Why deferred | Tracked |
|---|---|---|
| Trust-weighted strategy routing | Chapter 4 scope — needs TrustRoutingPolicyProvider + trust-routing.yaml | ARC42STORIES.MD §9.3 C4 |
| AgentDescriptor registration in eidos | Needed for engine discovery, not for trust scoring | #12 |
| Quality dimension scores (trustDimension/dimensionScore) | No consumer until Phase 3 quality floors are configured | #13 |
| Real worker dispatch via engine | Chapter 4 — WorkerDecisionEntry records activate naturally when engine routes | ARC42STORIES.MD §9.3 C4 |

---

## Platform Coherence

| PLATFORM.md rule | Compliance |
|---|---|
| "Do not add trust scoring to casehub-work or casehub-engine" | ✅ Using ledger's LedgerAttestation + ActorTrustScore |
| Ledger consumer pattern | ✅ N/A — no new LedgerEntry subclass |
| Actor identity convention (ADR 0004) | ✅ `"rule:{type}@v1"` matches format |
| CDI event pattern | ✅ saveAttestation() triggers AttestationRecordedEvent |
| Module tier structure | ✅ FsiActorIdentity in api/ (pure Java), services in app/ |
| Flyway numbering | ✅ N/A — no new migrations |
