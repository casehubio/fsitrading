# C2 Market Pulse — Design Spec

**Date:** 2026-08-12
**Issue:** #19 (epic: C2 — Market Pulse)
**Depends on:** #18 (C1 — Strategy Arena, closed)
**Decisions:** `decisions.md` in this directory (D1–D8)

---

## Summary

Market Pulse is a 5-level temporal summarisation pipeline. Synthetic market ticks flow through computational and LLM-powered summarisation stages, producing increasingly abstract market insights. Strategy agents observe at the granularity matching their time horizon. The pipeline publishes to WebSocket push topics, qhorus channels, and CDI domain events that bridge to the Strategy Arena.

The pipeline is a **pure composition of blocks' `EventStreamBus`** (D7). Each level has its own bus. `SummarisationRunner` instances subscribe to upstream buses and publish to downstream buses. External consumers (observation cache, channel bridge, push service) subscribe alongside downstream runners. Levels 0-2 are synchronous microsecond computation; Levels 3-4 dispatch asynchronously to an LLM via structured output.

Full vertical slice (D1): pipeline + REST endpoints + pages-push WebSocket + a minimal `fsi-market-panel` proving end-to-end data flow from synthetic ticks to browser.

---

## 1. Pipeline Architecture

### 1.1 Bus Composition (D7)

`EventStreamBus` is the single composition mechanism. Each `SummarisationRunner` takes an output `EventStreamBus<OUT>` in its constructor and publishes results to it. Downstream runners subscribe to upstream buses — the "chain" is bus subscriptions, not a separate wiring mechanism.

```
                    ┌──────────────┐
 Scheduler/REST ──► │ Level 0 Bus  │──► Compactor ──► KeyedSummarisationRunner
                    │ (PriceTick)  │                        │
                    └──────┬───────┘                        ▼
                           │                    ┌──────────────────┐
                    [push, obs]                │   Level 1 Bus    │──► SummarisationRunner(L2)
                                                │   (OHLCV)        │
                                                └──────┬───────────┘
                                                       │
                                                [push, obs]
                                                       ▼
                                                ┌──────────────────┐
                                                │   Level 2 Bus    │──► SummarisationRunner(L3) [ASYNC]
                                                │ (TrendSummary)   │
                                                └──────┬───────────┘
                                                       │
                                         [push, obs, channel bridge]
                                                       ▼
                                                ┌──────────────────┐
                                                │   Level 3 Bus    │──► SummarisationRunner(L4) [ASYNC]
                                                │(RegimeAssessment)│
                                                └──────┬───────────┘
                                                       │
                                         [push, obs, detector → CDI events]
                                                       ▼
                                                ┌──────────────────┐
                                                │   Level 4 Bus    │
                                                │(SessionNarrative)│
                                                └──────┬───────────┘
                                                       │
                                                [push, obs]
```

### 1.2 Level Definitions

Level 0 is the ingestion point — no runner, no summarisation. Ticks are published directly to the Level 0 bus by the scheduler/REST endpoint. The Compactor operates inside the L0→L1 `KeyedSummarisationRunner`.

| Level | EventLevel | Window | Summariser | Output | Dispatch |
|---|---|---|---|---|---|
| 0 | tick (0) | — (ingestion) | — (no runner) | PriceTick | — |
| 1 | bar-1m (1) | completionTest: age ≥ 60s, staleTimeout: 90s | FsiOhlcvSummariser | OHLCV | sync |
| 2 | trend-5m (2) | WindowPolicy.ofAge(300_000) | FsiTrendSummariser | TrendSummary | sync |
| 3 | regime-1h (3) | WindowPolicy.ofAge(3_600_000) | FsiRegimeSummariser (LLM) | RegimeAssessment | async |
| 4 | narrative (4) | WindowPolicy.ofAge(28_800_000) | FsiNarrativeSummariser (LLM) | SessionNarrative | async |

**Level 1** uses `KeyedSummarisationRunner<String, PriceTick, OHLCV>` which takes a `completionTest` predicate and `staleTimeout` — not a `WindowPolicy`. The completion test checks accumulated event age ≥ 60 seconds; `staleTimeout` of 90 seconds handles sparse overnight ticks. **Levels 2-4** use `SummarisationRunner` which takes `WindowPolicy`.

### 1.3 Per-Instrument Grouping and tick() Driver

`KeyedSummarisationRunner<String, PriceTick, OHLCV>` groups by instrument symbol. Each group has independent `KeyedAccumulator` and completion test evaluation.

**tick() driver:** `SummarisationRunner.tick(Instant now)` drains the accumulator when the window policy is satisfied, runs the summariser, and publishes to the output bus. Bus subscriptions handle `collect()` but `tick()` must be called periodically. `MarketPulseScheduler` drives `tick()` on all runners at a fixed interval (100ms) via a `@Scheduled` method separate from tick generation. Order: L1 `tick()` first, then L2, then L3, then L4 — each level drains before the next checks. For `KeyedSummarisationRunner`, `tick()` iterates all keyed groups.

At Level 4, `TieredContentSummariser` switches behavior: ≤5 instruments → verbatim bullet points, 6-20 → heuristic grouping by sector, 20+ → LLM-synthesised cross-instrument narrative.

### 1.4 Async Dispatch for LLM Levels

Levels 0-2 subscribers run synchronously on the publishing thread (microsecond computation). Level 3-4 subscriber callbacks wrap the `runner.collect()` and `runner.tick()` calls in an `Executor.submit()` — the LLM work runs off the bus thread so it doesn't block upstream publishing. `EventStreamBus` itself has no async dispatch capability; the async behavior is achieved by the subscriber callback delegating to an executor rather than running inline.

The bus still calls all subscribers in registration order synchronously — but L3-4 callbacks return immediately after submitting to the executor. This means subscriber ordering (§1.5) is preserved for dispatch, but L3-4 completion is asynchronous. Results may arrive out of tick order — acceptable for temporal summaries.

### 1.5 Subscriber Ordering

Within a single bus, subscribers are dispatched in registration order (`CopyOnWriteArrayList`). Registration order is explicit in the pipeline builder (`MarketPulseConfiguration`):
1. Observation service (first — always has latest data)
2. Market event detector (second — fires CDI events after observation is current)
3. Push service (third — push reflects current state)
4. Channel bridge (Level 2+ only)
5. Downstream runner (last — processing comes after observation)

This ordering is documented and enforced by the builder, not accidental.

### 1.6 Compactor

`FsiTickCompactor` implements `Compactor<PriceTick>`:
- Deduplicates same-instrument/same-second ticks (keeps the latest)
- Tags >3σ price deviation as anomalous (`PriceTick.anomaly = true`)
- σ computed from a sliding window of the last 100 ticks per instrument

### 1.7 LLM Cost Control

Levels 3-4 use LLM calls that incur cost. Cost is controlled by the window policy intervals themselves — L3 runs at most once per hour, L4 at most once per session. No additional gating is needed. The arena event bridge, channel bridge, and observation service all consume L3-4 output and must function without browser connections (e.g., overnight/headless scenarios). Push-only gating would silently disable the pipeline's most important downstream consumers.

---

## 2. Domain Types (D8)

All in `io.casehub.fsitrading.model` (api module). Pure Java records, no framework dependencies.

### 2.1 Pipeline Output Types

```java
public record PriceTick(
    String instrument, BigDecimal price, BigDecimal volume,
    Instant timestamp, boolean anomaly) {}

public record OHLCV(
    String instrument, BigDecimal open, BigDecimal high, BigDecimal low,
    BigDecimal close, BigDecimal volume, int tickCount,
    Instant windowStart, Instant windowEnd) {}

public record TrendSummary(
    String instrument, TrendDirection direction, double momentum,
    double volatility, String volumeProfile,
    Instant windowStart, Instant windowEnd) {}

public record RegimeAssessment(
    String instrument, MarketRegime regime, double confidence,
    String rationale, Instant timestamp) {}

public record SessionNarrative(
    List<String> instruments, String narrative, Instant timestamp) {}
```

### 2.2 Enums

```java
public enum MarketRegime { TRENDING, MEAN_REVERTING, VOLATILE, QUIET }
public enum TrendDirection { UP, DOWN, SIDEWAYS }
public enum ScenarioType { NORMAL_DAY, FLASH_CRASH, LIQUIDITY_DROP, GAP_OPEN, MULTI_INSTRUMENT }
```

### 2.3 CDI Domain Events

```java
public record TrendReversalDetected(
    String instrument, TrendDirection oldDirection, TrendDirection newDirection,
    TrendSummary trendSummary) {}

public record RegimeChanged(
    String instrument, MarketRegime oldRegime, MarketRegime newRegime,
    RegimeAssessment assessment) {}
```

### 2.4 Relationship to Existing Types

`PriceTick` is distinct from `MarketEventEntity` — it's a lightweight value object for the pipeline, not a JPA entity. `SyntheticMarketDataProvider.generateTick()` returns a `PriceTick` that also gets persisted as a `MarketEventEntity` for audit continuity.

`PriceTick.toMarketSignal()` provides conversion to `MarketSignal` for arena trigger integration.

---

## 3. Synthetic Data Enhancement (D3)

### 3.1 Hybrid Lifecycle

**Background scheduler:** `@Scheduled` Quarkus method, configurable interval (`fsi.market.tick-interval=500ms` default). Generates ticks across all instruments with realistic patterns. Paused/resumed via REST.

**Scenario injection:** `POST /api/market-data/scenario` accepts a `ScenarioType` and generates a burst of ticks that exercise specific pipeline paths.

### 3.2 Scenarios

| Scenario | Ticks | What it generates | Pipeline path exercised |
|---|---|---|---|
| `NORMAL_DAY` | 200 | U-shaped volume, small random walks | Full pipeline, verbatim TieredContentSummariser |
| `FLASH_CRASH` | 50 | Single instrument drops 8% in 30s | Compactor anomaly tagging, regime VOLATILE, channel bridge |
| `LIQUIDITY_DROP` | 100 | Volume decreases 90% across instruments | Sparse tick handling, ≤5 instrument mode |
| `GAP_OPEN` | 30 | 3% gap from previous close, high volume | Regime transition, trend reversal CDI event |
| `MULTI_INSTRUMENT` | 500 | 25 instruments | TieredContentSummariser 20+ mode (LLM narrative) |

### 3.3 Realistic Patterns

- U-shaped intraday volume: `volume × (1 + cos(π × fractionOfDay))`
- Price walks with configurable drift and mean-reversion tendency
- Sparse overnight ticks (1 per minute vs 10 per second during session)
- Anomaly injection: flash crash as sudden -8% move with 10× volume spike

### 3.4 Code Changes

`SyntheticMarketDataProvider` refactored:
- `generateTick()` returns `PriceTick` (was `MarketEventEntity`)
- New `generateScenario(ScenarioType)` returns `List<PriceTick>` fed into pipeline
- `MarketEventEntity` persistence moves to pipeline ingestion (Level 0 bus subscriber)
- New `ScenarioRunner` CDI bean handles scenario tick sequence generation

---

## 4. Observation & Arena Integration (D4, D5)

### 4.1 PartitionedObservationService — Platform Showcase

The observation layer uses the platform's `PartitionedObservationService<PriceTick, String>` — not a custom cache. This is an explicit platform showcase item from the replan spec.

`PartitionedObservationService` provides per-observer partitioned accumulation with typed `VisibilityPolicy<E, K>`. fsitrading configures it with `FsiStrategyVisibilityPolicy` — a `VisibilityPolicy` implementation that maps strategy types to their observable levels.

**Configuration (in `MarketPulseConfiguration`):**
```java
PartitionedObservationService.<PriceTick, String>builder()
    .visibilityPolicy(new FsiStrategyVisibilityPolicy())
    .build()
```

The service subscribes to all level buses and maintains per-observer latest state. Strategy agents query it at arena trigger time via the typed API.

### 4.2 FsiStrategyVisibilityPolicy

Implements `VisibilityPolicy<PriceTick, String>`. Externalizes the strategy-level mapping — the observation service has no knowledge of `StrategyType`:

| Strategy type | Observation levels | What they get |
|---|---|---|
| MARKET_MAKING | 0-1 | Raw ticks + bars |
| STATISTICAL_ARBITRAGE | 1-2 | Bars + trends |
| MOMENTUM | 2-3 | Trends + regime |
| EVENT_DRIVEN | 2-3 + filtered L0 anomalies | Trends + regime + anomaly ticks |
| PORTFOLIO_REBALANCE | 3-4 | Regime + narrative |
| OVERNIGHT_RISK_MANAGEMENT | 2-4 | Trends + regime + narrative |

Adding a new strategy type or changing observation levels requires modifying the policy, not the observation service.

### 4.3 FsiMarketEventDetector — Domain Events (D4)

Domain event detection is a separate concern from observation caching. `FsiMarketEventDetector` is a CDI bean that subscribes to Level 2+ buses independently and detects significant market changes:

- **Trend reversal:** `TrendSummary.direction` changed from previous → fires `TrendReversalDetected` CDI event
- **Regime change:** `RegimeAssessment.regime` changed from previous → fires `RegimeChanged` CDI event

The detector maintains minimal state (previous direction/regime per instrument) for comparison. It is registered after the observation service in the subscriber ordering (§1.5) — by the time the CDI event fires, the observation service already has the latest data. This eliminates the subscription-ordering race without conflating detection with caching.

### 4.4 Arena Bridge

`FsiArenaEventBridge` — CDI bean that observes `TrendReversalDetected` and `RegimeChanged`. Converts to `MarketSignal` and calls the arena trigger internally. The pipeline knows nothing about this.

### 4.5 C1 Agent Modification

Each strategy agent's `evaluate()` gains access to `PartitionedObservationService` (injected via the agent's CDI closure). During the Parallel step, agents query observations at their appropriate granularity via the service's typed API. This enriches the `AffordanceRenderer` output with live pipeline data.

---

## 5. Channel Bridge (D6)

### 5.1 FsiChannelEventAdapter

CDI bean subscribing to Level 2+ buses. Bridges events to qhorus channels.

| Bus level | Channel name pattern | Bounded? |
|---|---|---|
| Level 2 (TrendSummary) | `fsi-market-trends-{instrument}` | Yes — `BoundedProjectionDecorator(100)` |
| Level 3 (RegimeAssessment) | `fsi-market-regime-{instrument}` | No — low volume |
| Level 3 (RegimeChanged) | `fsi-market-regime-changes` | No — sparse |

### 5.2 Message Metadata

`ChannelMessageMeta` with sentinel `##FSI##`, keys:
- `LEVEL` — numeric level (2, 3)
- `INSTRUMENT` — symbol (e.g., "AAPL")
- `EVENT_TYPE` — e.g., `TREND_SUMMARY`, `REGIME_ASSESSMENT`, `REGIME_CHANGED`

C3's `FsiConversationProjection` will dispatch on these metadata entries.

### 5.3 Not Wired in C2

`ChannelEventPublisher` (feedback loop — agent conclusions back to event bus) is a C3 concern. C2 writes to channels; C3 reads and responds.

---

## 6. Orchestration — Sequence and Loop

### 6.1 Sequence — Pipeline Setup

`Patterns.sequence()` initialises the pipeline at startup:
1. Register all bus subscribers (cache, bridge, push)
2. Start the background scheduler
3. Run initial data load (if resuming)

### 6.2 Loop — Continuous Monitoring

```java
Patterns.<MarketPulseState>loop()
    .exitCondition(state -> state.marketClosed() || state.shutdownRequested())
    .task("market-pulse-monitor")
    .build()
```

The loop drives the monitoring lifecycle — market session boundaries, end-of-session Level 4 narrative summarisation, clean shutdown. The scheduler drives tick generation; the loop drives lifecycle management.

### 6.3 MarketPulseState

Mutable context for the loop:
- `marketClosed` — whether the simulated market session has ended
- `shutdownRequested` — graceful shutdown flag
- `lastNarrativeTimestamp` — when L4 last ran
- `sessionStart` / `sessionEnd` — simulated market hours

---

## 7. Pages Push & UI Panel (D1)

### 7.1 Push Topics

`FsiMarketPushService` subscribes to all level buses and publishes to `EventBroadcaster`:

| Topic pattern | Level | Payload | Rate |
|---|---|---|---|
| `market/ticks/{instrument}` | 0 | PriceTick JSON | High — every tick |
| `market/bars/{instrument}` | 1 | OHLCV JSON | ~1/min per instrument |
| `market/trends/{instrument}` | 2 | TrendSummary JSON | ~1/5min per instrument |
| `market/regime/{instrument}` | 3 | RegimeAssessment JSON | ~1/hour per instrument |
| `market/narrative` | 4 | SessionNarrative JSON | ~1/session |

`FsiMarketPushService` tracks active subscriptions per topic for diagnostics but does not gate pipeline summarisation — L3-4 always run regardless of push subscriber count (§1.7).

### 7.2 Minimal fsi-market-panel

Quinoa webui setup:
```
app/src/main/webui/
  package.json
  src/
    index.ts
    panels/
      fsi-market-panel.ts
```

Maven unpacks `casehub-pages-npm` SNAPSHOT. Quinoa builds via esbuild.

**Panel displays:**
- Live price ticker (latest tick per instrument) — `PagesMetric`
- Current regime badge per instrument — `PagesBadge`
- Sparkline of recent OHLCV close prices — `PagesTimeseries`

Subscribes to `market/ticks/{instrument}`, `market/bars/{instrument}`, `market/regime/{instrument}`. Registered via `registerPanel()`. C5 replaces with full dock-workbench composition.

---

## 8. LLM Integration (D2)

### 8.1 Level 3 — Regime Assessment

`FsiRegimeSummariser` wraps `LlmContentSummariser` with structured JSON output. The LLM receives accumulated `TrendSummary` entries for the window and returns:

```json
{
  "regime": "TRENDING",
  "confidence": 0.85,
  "rationale": "Sustained upward momentum across 12 consecutive 5-min windows with increasing volume..."
}
```

JSON schema constraint ensures the response parses to `RegimeAssessment`. The `regime` field maps to `MarketRegime` enum.

### 8.2 Level 4 — Session Narrative

`FsiNarrativeSummariser` implements `Summariser<RegimeAssessment, SessionNarrative>` directly. It uses `TieredContentSummariser` internally to select the rendering mode, then converts the `SummaryResult` (qhorus text type) to a typed `SessionNarrative` record:

1. Receives accumulated `RegimeAssessment` entries from the L3→L4 window
2. Renders them as text input for `TieredContentSummariser`
3. `TieredContentSummariser` selects mode based on instrument count:
   - ≤5 instruments → `VerbatimContentSummariser` (bullet points)
   - 6-20 → heuristic grouping by sector
   - 20+ → `LlmContentSummariser` (cross-instrument synthesis)
4. Receives `SummaryResult` from the content summariser
5. Converts to `SessionNarrative(instruments, summaryResult.text(), Instant.now())`

This adapter pattern bridges the `ContentSummariser<T>` → `SummaryResult` world with the `Summariser<IN, OUT>` → typed domain record world. The `SummarisationRunner` at Level 4 sees `Summariser<RegimeAssessment, SessionNarrative>` — it doesn't know about the `TieredContentSummariser` inside.

### 8.3 Failure Handling

On LLM failure (provider unavailable, timeout, malformed response), the pipeline gracefully skips the affected level. The `SummarisationRunner.onFailure` callback logs the error and increments an OTel counter (`fsi.pipeline.llm.failures`). Downstream consumers get stale data until the next successful summarisation. No fallback to rules — stale real data is better than fresh fake data.

### 8.4 Model Configuration

`fsi.market.llm.model` config property selects the model. Default to a fast/cheap model for dev. Production can override to a more capable model. The platform's LLM provider CDI beans handle model routing.

---

## 9. REST Endpoints

### 9.1 New Endpoints

| Method | Path | Purpose |
|---|---|---|
| `GET` | `/api/market-data/bars/{instrument}` | Historical 1-min OHLCV bars (paginated) |
| `GET` | `/api/market-data/trends/{instrument}` | Recent 5-min trend summaries |
| `GET` | `/api/market-data/regime/{instrument}` | Current regime assessment |
| `GET` | `/api/market-data/narrative` | Latest session narrative |
| `POST` | `/api/market-data/scenario` | Inject market scenario |
| `POST` | `/api/market-data/scheduler/pause` | Pause background tick generation |
| `POST` | `/api/market-data/scheduler/resume` | Resume background tick generation |

### 9.2 Existing Endpoints (unchanged)

`POST /api/market-data/tick` and `GET /api/market-data/recent` remain.

### 9.3 Persistence for Pagination

OHLCV bars and trend summaries are persisted to support paginated REST queries. `V103__create_market_data_summary.sql` creates backing tables. `RegimeAssessment` and `SessionNarrative` are served from the observation cache (low volume, no pagination needed).

---

## 10. Code Changes

### 10.1 New — api module

| Component | Purpose |
|---|---|
| `PriceTick` | Pipeline Level 0 input/output record |
| `OHLCV` | Level 1 output — 1-min bar aggregate |
| `TrendSummary` | Level 2 output — 5-min trend analysis |
| `RegimeAssessment` | Level 3 output — LLM regime classification |
| `SessionNarrative` | Level 4 output — LLM session narrative |
| `MarketRegime` | Enum: TRENDING, MEAN_REVERTING, VOLATILE, QUIET |
| `TrendDirection` | Enum: UP, DOWN, SIDEWAYS |
| `ScenarioType` | Enum: NORMAL_DAY, FLASH_CRASH, LIQUIDITY_DROP, GAP_OPEN, MULTI_INSTRUMENT |
| `TrendReversalDetected` | CDI event record |
| `RegimeChanged` | CDI event record |

### 10.2 New — app module

| Component | Purpose |
|---|---|
| `MarketPulseConfiguration` | CDI producer: builds pipeline — buses, runners, subscribers, orchestration model |
| `MarketPulseState` | Mutable context for Loop pattern |
| `FsiTickCompactor` | `Compactor` impl — dedup + anomaly tagging |
| `FsiOhlcvSummariser` | `SyncSummariser<PriceTick, OHLCV>` — bar aggregation |
| `FsiTrendSummariser` | `SyncSummariser<OHLCV, TrendSummary>` — trend computation |
| `FsiRegimeSummariser` | Wraps `LlmContentSummariser` — structured JSON → RegimeAssessment |
| `FsiNarrativeSummariser` | Wraps `LlmContentSummariser` — APPEND mode free text |
| `FsiStrategyVisibilityPolicy` | `VisibilityPolicy` impl — maps strategy types to observable levels |
| `FsiMarketEventDetector` | Detects trend reversals and regime changes, fires CDI domain events |
| `FsiChannelEventAdapter` | Bridges Level 2+ bus events to qhorus channels |
| `FsiMarketPushService` | Bus subscriber → EventBroadcaster topics |
| `FsiArenaEventBridge` | CDI observer: domain events → arena trigger |
| `ScenarioRunner` | Generates scenario tick sequences |
| `MarketPulseScheduler` | `@Scheduled` tick generation with pause/resume |
| Quinoa webui + `fsi-market-panel` | Minimal push-proving UI panel |

### 10.3 Modified

| Component | Change |
|---|---|
| `SyntheticMarketDataProvider` | Returns `PriceTick`, adds `generateScenario()`, U-shaped volume |
| `AbstractStrategyAgent` (+ subclasses) | Access `PartitionedObservationService` for market context |
| `FsiAffordanceProvider` | Enriched with pipeline observation data |
| `MarketDataResource` | Extended with bars, trends, regime, narrative, scenario, scheduler endpoints |
| `pom.xml` | Add `casehub-pages` dependency |

### 10.4 Flyway Migrations

| Migration | Purpose |
|---|---|
| `V103__create_market_data_summary.sql` | Tables for OHLCV bars and trend summaries (REST pagination) |

---

## 11. Acknowledged Limitations

**Single-process topology:** The entire pipeline runs in a single JVM. `EventStreamBus` uses synchronous in-process dispatch. Observation caches are in-memory. Correct for showcase; multi-node is a C6/deployment concern.

**Minimal panel rework:** The `fsi-market-panel` may need rework when C5's dock-workbench lands. The Quinoa/pages setup carries forward.

**Channel accumulation:** Qhorus channels accumulate messages until C3 adds deliberation agents. `BoundedProjectionDecorator(100)` caps Level 2 channels.

**PartitionedObservationService.drain() blocking:** The `drain()` method uses `.join()`. The observation service (§4) provides latest values for the common agent query path; `drain()` is only for historical window access. If multiple arena agents call `drain()` concurrently during the Parallel step, threads block on the join — a constraint to design around in test scenarios.

**Synthetic data limits realism:** Price patterns and volume profiles are approximations. The pipeline architecture is the showcase, not the data quality. Real feeds swap the data source without changing the pipeline.
