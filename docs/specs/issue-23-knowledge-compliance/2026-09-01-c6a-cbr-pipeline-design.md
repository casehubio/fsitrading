# C6a: CBR Pipeline + Plan Adaptation + Outcome Recording

**Date:** 2026-09-01
**Issue:** casehubio/fsitrading#36
**Parent epic:** casehubio/fsitrading#23 (C6: Knowledge & Compliance)
**Branch:** issue-23-knowledge-compliance

---

## Summary

Implements the CBR (Case-Based Reasoning) pipeline for overnight incident response. Past market incidents feed future detection and response through the neocortex CBR subsystem. The pipeline follows the classic Retrieve-Reuse-Revise-Retain cycle:

1. **Retrieve** — At case start, extract 7 market event features and query for similar past incidents
2. **Reuse** — Adapt retrieved response plans to current conditions via 4 adaptation strategies
3. **Revise** — Agents reference adapted plans as advisory context (no automatic HTN modification)
4. **Retain** — At case close, record the incident as a PlanCbrCase with features, plan trace, and outcome

---

## Architecture

### Integration Model — Inform HTN (D1)

CBR retrieval injects past plans into `CaseContext` as prior art. The static severity-based HTN decomposition remains the primary response structure. Agents and the LLM fallback can reference "what worked before" but are not bound by it.

The CBR pipeline wraps around the existing `OvernightIncidentCaseHub` lifecycle without modifying its YAML case definition structure. The engine's built-in `CaseStartedEventHandler` drives retrieval; a custom `FsiCaseOutcomeObserver` handles retain.

```
                          ┌─────────────────────────────┐
                          │    Overnight Incident Case   │
                          └─────────────┬───────────────┘
                                        │
    ┌───────────────────────────────────┐│┌──────────────────────────────────┐
    │          CBR Pipeline             │││        HTN Decomposition          │
    │                                   │││                                  │
    │  Case Start                       │││  Static severity-based methods   │
    │    ├─ LambdaFeatureExtractor      │││    ├─ CRITICAL: halt→close→alert │
    │    ├─ CbrCaseMemoryStore.retrieve │││    ├─ HIGH: reduce→hedge→alert   │
    │    ├─ FsiPlanAdapter.adapt (topK) │││    └─ MEDIUM: adjust→monitor     │
    │    └─ → CaseContext["cbrExp..."]  │││                                  │
    │                                   │││  LLM fallback reads CBR context  │
    │  Case Close                       │││                                  │
    │    ├─ FsiCaseOutcomeObserver      │││                                  │
    │    ├─ Build PlanTrace             │││                                  │
    │    ├─ Evaluate CbrOutcome         │││                                  │
    │    └─ CbrCaseMemoryStore.store    │││                                  │
    └───────────────────────────────────┘│└──────────────────────────────────┘
                                        │
                          ┌─────────────┴───────────────┐
                          │   DETECTED → CLASSIFIED →   │
                          │   RESPONDED → VERIFIED →    │
                          │   CLOSED                    │
                          └─────────────────────────────┘
```

### CBR Case Type — PlanCbrCase (D2)

`PlanCbrCase` is the only `CbrCase` subtype with both `features` (for similarity retrieval) and `planTrace` (for recording HTN response steps). Each stored case captures:

- **problem** — incident description (event type, severity, affected instruments)
- **solution** — response plan summary (decomposition method, key actions taken)
- **outcome** — result text (goals met/failed)
- **confidence** — derived from goal evaluation
- **features** — 7 market event features (see Feature Schema below)
- **planTrace** — list of `PlanTrace` entries from completed HTN bindings
- **trustScore** — aggregate trust of participating agents
- **producerAgentId** — `"fsi-incident-cbr"`

Plan trace population filters: only terminal capability plan items with executors produce trace entries. Human tasks (review binding) are excluded. Skipped conditional steps are omitted.

---

## Feature Schema (D3, D8)

Seven features extracted from the incident context and market data pipeline:

| Feature | FeatureField | SimilaritySpec | Weight | Source |
|---|---|---|---|---|
| `event_type` | Categorical | exact match (default) | 0.15 | `MarketEventType` on incident |
| `instrument_sector` | Categorical | exact match (default) | 0.10 | `Instrument.assetClass()` |
| `time_of_day` | Numeric(0, 24) | GaussianDecay(sigma=2.0) | 0.10 | Detection timestamp hour |
| `volatility_at_detection` | Numeric(0, 100) | GaussianDecay(sigma=10.0) | 0.10 | Stddev of recent PRICE_TICK returns for incident instrument |
| `volume_profile` | NumericList(0, 1e9) | list distance (default) | 0.10 | Last 10 `MarketEventEntity` volumes for incident instrument (normalized) |
| `price_action_pattern` | TimeSeries(DtwSpec) | DTW with Sakoe-Chiba band | 0.25 | Last 30 `MarketEventEntity` PRICE_TICK records for incident instrument (timestamp + price + computed momentum) |
| `event_sequence` | DiscreteSequence(EditDistanceSpec) | Edit distance with event-type substitution costs | 0.20 | Ordered list of recent `MarketEventType` values for incident instrument |

### Feature Extraction — LambdaFeatureExtractor (D3)

`FsiFeatureExtractor` provides an `extract(CaseContext): Map<String, Object>` method for use as a method reference in `LambdaFeatureExtractor`. It injects:

- `SyntheticMarketDataProvider` — for `MarketEventEntity` records filtered by instrument

All queries are scoped to the incident's primary instrument. Volatility and price action use only `PRICE_TICK` event type records. The instrument symbol is read from `CaseContext` (set when the incident is created).

Feature extraction runs at case start (via the engine's automatic CBR retrieval flow). Features are cached by the engine for the case's duration (D10: `CASE_LIFETIME` timing — this caches the retrieval *results*, not the raw feature map).

**Feature reproduction for outcome recording:** `FsiCaseOutcomeObserver` re-extracts features at case close using the same `FsiFeatureExtractor` but anchored to the incident's detection timestamp. Queries use `WHERE instrument = :instrument AND occurredAt <= :detectionTimestamp ORDER BY occurredAt DESC` to reproduce the exact same dataset that was available at case start. This avoids both (a) the MutableCaseContext contract violation of caching features in the extractor side-effect, and (b) feature drift from new records accumulated during the incident. A casehub-engine issue is filed for `CaseStartedEventHandler` to cache extracted features alongside `cbrExperiences` — that would eliminate the need for re-extraction entirely.

### Schema Registration (D8)

`CbrFeatureSchema` is registered in `OvernightIncidentCaseHub.augment()` via `cbrStore.registerSchema()`. Without schema registration, `CbrSimilarityScorer.scoreDetailed()` returns 1.0 for all cases — effectively random retrieval.

The schema is registered once per definition load and cached in the store by `caseType` key. When `NoOpCbrCaseMemoryStore` is active (tests without neocortex on classpath), `registerSchema()` is a silent no-op.

```java
CbrFeatureSchema schema = CbrFeatureSchema.of("plan",
    FeatureField.categorical("event_type"),
    FeatureField.categorical("instrument_sector"),
    FeatureField.numeric("time_of_day", 0, 24,
        new SimilaritySpec.GaussianDecay(2.0)),
    FeatureField.numeric("volatility_at_detection", 0, 100,
        new SimilaritySpec.GaussianDecay(10.0)),
    FeatureField.numericList("volume_profile", 0, 1e9),
    FeatureField.timeSeries("price_action_pattern", "timestamp",
        new SimilaritySpec.DtwSpec(WarpingConstraint.sakoeChibaBand(5)),
        FeatureField.numeric("timestamp", 0, Double.MAX_VALUE),
        FeatureField.numeric("price", 0, Double.MAX_VALUE),
        FeatureField.numeric("momentum", -1, 1)),
    FeatureField.discreteSequence("event_sequence",
        new SimilaritySpec.EditDistanceSpec(buildEventTypeSubstitutionCosts()))
);
```

### Event Type Substitution Costs

Edit distance for `event_sequence` uses domain-specific substitution similarities between `MarketEventType` values:

| Event A | Event B | Similarity | Rationale |
|---|---|---|---|
| FLASH_CRASH | LIQUIDITY_DROP | 0.7 | Both involve rapid price decline |
| FLASH_CRASH | GAP_OPEN | 0.4 | Both sudden, different mechanism |
| LIQUIDITY_DROP | MARGIN_CALL | 0.5 | Liquidity stress → margin pressure |
| CIRCUIT_BREAKER | FLASH_CRASH | 0.6 | Circuit breakers triggered by crashes |
| NEWS_EVENT | COUNTERPARTY_FAILURE | 0.3 | News may signal counterparty risk |

**Domain-based baseline:** Unlisted pairs within the same `MarketEventType.domain()` group default to 0.2 (same domain = partial similarity). Cross-domain pairs default to 0.0. Explicit entries in the table above override these baselines. Domain groups: `RawMarketData` (PRICE_TICK, VOLUME_SPIKE), `DetectedEvent` (FLASH_CRASH, LIQUIDITY_DROP, GAP_OPEN, CIRCUIT_BREAKER, NEWS_EVENT), `OperationalEvent` (COUNTERPARTY_FAILURE, MARGIN_CALL).

Built via `SimilaritySpec.categoricalTableBuilder()` with domain-based defaults computed from `MarketEventType.domain()`.

---

## CbrConfig Wiring — Hybrid (D4)

The overnight-incident.yaml retains declarative CBR parameters:

```yaml
cbrConfig:
  topK: 5
  minSimilarity: 0.6
  temporalDecayHalfLifeDays: 90
```

`OvernightIncidentCaseHub.augment()` reads the YAML-parsed `CbrConfig` and rebuilds with additions:

```java
@Override
protected void augment(CaseDefinition definition) {
    descriptor.augmentWorkers(definition);

    // CBR feature schema registration
    cbrStore.registerSchema(FsiCbrFeatureSchema.SCHEMA);

    // Rebuild CbrConfig: preserve YAML params, add Java-only config
    CbrConfig yamlConfig = definition.getCbrConfig();
    definition.setCbrConfig(CbrConfig.builder()
        .featureExtractor(fsiFeatureExtractor::extract)
        .topK(yamlConfig.topK())
        .minSimilarity(yamlConfig.minSimilarity())
        .temporalDecayHalfLifeDays(yamlConfig.temporalDecayHalfLifeDays())
        .domain("fsitrading")
        .caseType(PlanCbrCase.CBR_TYPE)
        .cbrType(PlanCbrCase.CBR_TYPE)
        .timing(CbrConfig.CbrRetrievalTiming.CASE_LIFETIME)
        .weight("event_type", 0.15)
        .weight("instrument_sector", 0.10)
        .weight("time_of_day", 0.10)
        .weight("volatility_at_detection", 0.10)
        .weight("volume_profile", 0.10)
        .weight("price_action_pattern", 0.25)
        .weight("event_sequence", 0.20)
        .build());
}
```

---

## Plan Adaptation — FsiPlanAdapter (D6)

`FsiPlanAdapter` implements `PlanAdapter` as a CDI `@ApplicationScoped` bean. The engine's `CbrRetrievalService.adaptAndMapPlanTrace()` calls `adapt()` automatically on each individual retrieved `ScoredCbrCase<PlanCbrCase>`.

### Four Adaptation Strategies

For each step in the retrieved plan trace:

1. **Agent substitution** — If the original worker agent has trust below threshold (0.4), substitute with the highest-trust agent that has the same capability. Action: `SUBSTITUTED`.

2. **Threshold adjustment** — If current volatility is significantly higher than the retrieved case's volatility (>2× ratio), boost the step's priority. Action: `BOOSTED`.

3. **Step addition** — If current position size exceeds the retrieved case's position size by >50%, add a pre-reduce step before the main response. Action: `ADDED`.

4. **Step suppression** — If the market is currently closed and the step requires market access (e.g., close positions, place orders), suppress the step. Action: `SUPPRESSED`.

Steps that pass all checks are retained with `RETAINED` action.

### FsiPlanAdapter Dependencies

- `PnlAttestationService` — for agent trust scores (substitution strategy)
- `SyntheticMarketDataProvider` — for computed current volatility (threshold adjustment)
- `MarketPulseState` — for `isMarketClosed()` (step suppression)
- `PositionService` — for current position sizes (step addition)

### Ensemble Analysis — Deferred (D6)

`FsiPlanEnsembleAnalyzer` is **deferred** to a platform fix. The engine's `CbrRetrievalService` does not invoke `PlanEnsembleAnalyzer` after individual adaptation. A casehub-engine issue will be filed to add `PlanEnsembleAnalyzer` invocation to `CbrRetrievalService`.

In the first implementation, agents see 5 individually-adapted plans in `CaseContext["cbrExperiences"]`. The "Inform" model (D1) means agents reference these as advisory context — ensemble synthesis adds incremental value but is not required for the core CBR pipeline.

---

## Outcome Recording — FsiCaseOutcomeObserver (D5)

### CDI Wiring (D7)

The platform's `CbrCaseRetainObserver` is excluded from CDI to prevent duplicate entries:

```properties
# application.properties (main AND test)
quarkus.arc.exclude-types=io.casehub.engine.internal.memory.CbrCaseRetainObserver
```

**Constraint:** This exclusion is application-global — it prevents `CbrCaseRetainObserver` from running for ALL case types, not just overnight-incident. `FsiCaseOutcomeObserver` becomes the sole CBR retain observer. It handles overnight-incident cases with FSI-specific extraction; if additional case types with `CbrConfig` are added to fsitrading, `FsiCaseOutcomeObserver.onOutcome()` must be extended to handle them (or delegate to platform-style extraction via `SnapshotCaseContext` for non-FSI case types).

`FsiCaseOutcomeObserver` is `@ApplicationScoped` (not `@DefaultBean`), displacing `NoOpCaseOutcomeObserver` (`@DefaultBean`) via the standard CaseHub displacement pattern.

### Observer Flow

```
CaseOutcomeEvent (case reaches CLOSED milestone)
  └─ FsiCaseOutcomeObserver.onOutcome()
       ├─ Guard: skip if caseType has no CbrConfig
       ├─ Re-extract features via FsiFeatureExtractor
       │    (anchored to detection timestamp — queries
       │     MarketEventEntity WHERE occurredAt <= detectionTimestamp
       │     to reproduce the exact case-start feature set)
       ├─ Build PlanTrace from completed bindings
       │    ├─ Filter: only terminal capability plan items with executors
       │    ├─ Exclude: human tasks (review binding)
       │    └─ Exclude: skipped conditional steps
       ├─ Evaluate CbrOutcome
       │    ├─ successRate from goal evaluation (capital-preserved, positions-safe, sla-met)
       │    └─ detail from goal results summary
       ├─ Construct PlanCbrCase
       │    ├─ problem = incident description
       │    ├─ solution = response plan summary
       │    ├─ outcome = goal results
       │    ├─ features = re-extracted features (detection-time anchored)
       │    ├─ planTrace = built plan trace
       │    ├─ trustScore = aggregate trust
       │    └─ producerAgentId = "fsi-incident-cbr"
       └─ cbrStore.store(planCbrCase, PlanCbrCase.CBR_TYPE, entityId,
                         domain, tenantId, caseId.toString(), scope)
```

### Outcome Evaluation

`CbrOutcome.successRate` derived from the three incident goals:

| Goal | Success condition | Weight |
|---|---|---|
| capital-preserved | `.portfolioValueRatio >= 0.95` | 0.4 |
| positions-safe | `.unhedgedExposure == 0` | 0.4 |
| sla-met | `.slaBreachCount == 0` | 0.2 |

Weighted sum of boolean goal evaluations produces `successRate` in [0, 1].

---

## Domain and Scope (D9)

- **Domain:** `"fsitrading"` — application-level CBR namespace
- **Store scope:** `Path.root()` — cases stored globally within domain
- **Query scope:** `Path.root()` — the engine's `CbrRetrievalService` hardcodes `Path.root()` in the query; `CbrConfig` has no scope field

Sector-scoped retrieval (narrowing queries to a specific instrument sector) is not achievable through the engine's automatic retrieval path. A casehub-engine issue is filed for adding a `scope` field to `CbrConfig`. Until then, all cases in the `"fsitrading"` domain are searched regardless of sector — the feature-based similarity scoring (particularly `instrument_sector` as a Categorical feature) provides implicit sector affinity.

---

## Retrieval Timing (D10)

`CbrRetrievalTiming.CASE_LIFETIME` — features extracted once at case start, cached for the case's duration. Overnight incident characteristics (severity, event type, instruments, price action) are fixed at detection and don't change during the response lifecycle.

---

## Trust-Weighted Store

`TrustWeightedCbrCaseMemoryStore` is a CDI `@Decorator` gated by `casehub.cbr.trust-weighting.enabled=true`. When enabled, it modulates retrieval similarity scores by the stored case's `trustScore` — cases produced by higher-trust agents rank higher. Without this property, retrieval returns raw similarity scores without trust weighting.

```properties
# application.properties
casehub.cbr.trust-weighting.enabled=true
```

The decorator is part of `casehub-neocortex-memory` (already on classpath). No new types needed — enabling the property activates the decorator via its `@IfBuildProperty` guard.

---

## New Types

| Type | Package | Role |
|---|---|---|
| `FsiFeatureExtractor` | `app.cbr` | Extracts 7 features from CaseContext + market data |
| `FsiCbrFeatureSchema` | `app.cbr` | Static schema definition with 7 FeatureField entries |
| `FsiPlanAdapter` | `app.cbr` | 4 adaptation strategies for retrieved plans |
| `FsiCaseOutcomeObserver` | `app.cbr` | Records PlanCbrCase at case close |
| `FsiEventTypeSubstitutionCosts` | `app.cbr` | Edit distance substitution costs with domain-based baseline for MarketEventType |

### Modified Types

| Type | Change |
|---|---|
| `OvernightIncidentCaseHub` | Add `@Inject CbrCaseMemoryStore`, register schema + rebuild CbrConfig in `augment()` |

### Configuration Changes

| File | Change |
|---|---|
| `application.properties` | Add `CbrCaseRetainObserver` to `quarkus.arc.exclude-types` |
| `application.properties` | Add `casehub.cbr.trust-weighting.enabled=true` |
| `application.properties` (test) | Same exclusion + trust-weighting |

---

## REST Endpoints

| Method | Path | Purpose |
|---|---|---|
| `GET` | `/api/incidents/similar?caseId={id}` | Retrieve similar past incidents for a given case |

Returns the `List<RetrievedExperience>` from `CaseContext["cbrExperiences"]` for the specified case, with similarity scores and adapted plan traces. `CaseStartedEventHandler` stores serialized `RetrievedExperience` records (mapped from `ScoredCbrCase` via `CbrRetrievalService`), not raw `ScoredCbrCase<PlanCbrCase>`.

---

## Testing Strategy

- **Unit tests:** `FsiFeatureExtractor` (feature extraction from mock CaseContext), `FsiPlanAdapter` (each adaptation strategy in isolation), `FsiCaseOutcomeObserver` (outcome evaluation, plan trace building)
- **Integration test:** Full pipeline — create incident, verify CBR retrieval, close incident, verify case stored, create similar incident, verify retrieval returns the stored case with correct similarity score
- **CBR store:** `casehub-neocortex-memory-cbr-tracking` module in test scope — provides a real `CbrCaseMemoryStore` backed by in-memory tracking with full similarity scoring. Use `quarkus.arc.selected-alternatives` to activate in test profile

---

## Platform Issues to File

| Repo | Issue | Description |
|---|---|---|
| casehub-engine | PlanEnsembleAnalyzer integration | `CbrRetrievalService` should invoke `PlanEnsembleAnalyzer.analyze()` after individual `PlanAdapter.adapt()` calls, injecting the `EnsemblePlan` into `CaseContext` alongside individual adapted plans |
| casehub-engine | CbrConfig scope field | Add `scope` field to `CbrConfig` so applications can specify sector/path-scoped retrieval. Currently `CbrRetrievalService.retrieveInternal()` hardcodes `Path.root()` |
| casehub-engine | CaseStartedEventHandler feature caching | Cache extracted feature map in `CaseContext` alongside `cbrExperiences` so outcome observers can read detection-time features without re-extraction |

## Deferred Issues to File

| Repo | Issue | Description | Source |
|---|---|---|---|
| casehubio/fsitrading | RoutingOutcomeRecorder per-step CBR integration | Implement per-step outcome recording via `RoutingOutcomeRecorder` — records individual routing decisions alongside case-level CBR outcomes | Issue #36 §Outcome recording |
| casehubio/fsitrading | MemoryEmitter CBR case recording | Wire `MemoryEmitter.emit()` to record full CBR case (features + solution + outcome) to `CaseMemoryStore` for agent episodic memory, separate from CBR store retention | Issue #36 §Outcome recording |
| casehubio/fsitrading | CBR supersession for regime changes | Supersede CBR cases when market regime shifts (VOLATILE→QUIET etc). Concept sound but unimplementable against current API: `supersede()` requires a supersedingCaseId (regime changes aren't cases), `scan()` has no scope param, stored cases lack regime metadata. Needs platform support: scope-based scan, regime-triggered supersession API | R2-02 |

---

## References

- Replan spec §C6.1-6.3 — feature schema, plan adaptation strategies, outcome recording
- `FeatureField.java` — sealed FeatureField hierarchy (neocortex-memory-api)
- `CbrCaseMemoryStore.java` — store/retrieve/erase/supersede API
- `CbrQuery.java` — query with features, weights, filters, temporal decay, scope
- `PlanCbrCase.java` — case type with plan trace
- `PlanAdapter.java` / `PlanEnsembleAnalyzer.java` — adaptation and ensemble SPIs
- `CbrConfig.java` — engine CBR configuration with LambdaFeatureExtractor
- `OvernightIncidentCaseHub.java` — existing case definition (augment point)
- `FsiCbrOutcomeWeights.java` — existing routing outcome weights (arena module)
- GE-20260720-6ea915 — CbrCaseRetainObserver duplicate entry gotcha
- GE-20260718-95e11e — store() parameter order: (cbrCase, caseType, entityId, domain, tenantId, caseId, scope)
- GE-20260720-b7a8b9 — eraseEntity() crosses all CBR domains
- GE-20260706-abaddc — @DefaultBean bridge resolution issue
- GE-20260804-eb75e0 — scan() returns summaries without features
- GE-20260820-d4e011 — FeatureVectorCbrCase requires non-blank solution
- GE-20260804-7bd9f4 — ScoredCbrCase constructor parameter order
- GE-20260820-c19b68 — CbrQuery has no producerAgentId filter
