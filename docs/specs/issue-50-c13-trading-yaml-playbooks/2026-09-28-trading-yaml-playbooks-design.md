# C13: Trading YAML Playbooks — Design Spec

**Epic:** casehubio/fsitrading#50
**Date:** 2026-09-28
**Branch:** issue-50-c13-trading-yaml-playbooks

---

## Overview

Author trading-specific YAML step definitions, write coordination playbooks for key trading desk scenarios, and connect to the CBR feedback loop so playbooks learn from execution outcomes.

**Architecture:** Three-tier orchestration. (1) Case definitions (engine choreography) detect situations and dispatch responses via capability bindings. (2) Playbooks (yaml-core step files) provide the imperative worker implementation — step sequences with control flow, scatter-gather, deadlines, escalation. (3) Prose resolution documents surface guidance for novel situations where no automated playbook applies — ingested into CBR for similarity-based retrieval.

**Case↔Playbook dispatch:** Case definition workers invoke playbooks via the engine's Serverless Workflow `do:` dispatch type with a `casehub:step-file` callable. The callable is a `CallableDispatcher` registered in the engine's `CallableDispatchRegistry` — the same extensible mechanism used by all SWF `call:` steps. Worker YAML:

```yaml
workers:
  - name: classifier
    capabilities: [incident-classify]
    do:
      - call: casehub:step-file
        with:
          file: playbooks/incident-classify-steps.yaml
```

When the case binding fires, `FlowWorkerFunctionProvider` handles the `do:` block, `CasehubCallableTaskBuilder` routes the `call:` to the `CallableDispatchRegistry`, and the `StepFileCallableDispatcher` loads and executes the referenced yaml-core step file. The step file receives the case context via `${context.*}` variables. On completion, step outputs merge back into the case context via the worker's `outputProjection`.

**Implementation:** `StepFileCallableDispatcher` is new engine infrastructure (to be filed as casehubio/engine issue). It is architecturally lightweight — a single `CallableDispatcher` implementation (~50 lines) that resolves a step file path, constructs a `StepFileExecutor` from platform `yaml-step-runtime`, executes it with the case's `WorkerContext`, and returns the step outputs as a `Map<String, Object>`. All dispatch infrastructure (`FlowWorkerFunctionProvider`, `CasehubCallableTaskBuilder`, `CallableDispatchRegistry`) already exists.

**CBR integration:** Playbooks inherit the case's CBR context automatically — `WorkerContext.experiences()` provides retrieved past outcomes to playbook steps. Engine#1190 delivers the generic yaml-core → CBR outcome recording bridge, enabling step-level outcome tracking alongside the existing case-level retain. Until #1190 lands, case-level CBR (via `CbrCaseRetainObserver`) works now; step-level outcome granularity is deferred.

**Scope boundary:** fsitrading delivers validated YAML — step definitions that resolve against the step catalog, playbooks that parse with correct structure and decorator composition, simulation corpus for testing. Runtime execution of structural step types (block, if/else, match) and the playbook state machine executor are engine work tracked separately — issues to be filed against casehubio/engine for BlockStep sequential executor, IfElseStep condition evaluation, MatchStep pattern matching, and PlaybookStateMachineExecutor (see §State Machine Runtime Model). Note: engine#1190-#1192 are CBR integration work (outcome recording, plan mapping, goal modulation), not structural step runtime.

**State machine notation:** Playbook state machines (§2.1–§2.5) use a YAML notation with string-named states, step lists per state, `on-step-failure` transitions, and deadline fallbacks. This notation is a **design specification** — it defines the intended behaviour that the engine's `PlaybookStateMachineExecutor` must implement. It is NOT directly executable by yaml-core's existing `OrcStateMachine<S extends Enum<S>>` (which requires Java enum states and programmatic builder API). The mapping from YAML notation to `OrcStateMachine` is defined in §State Machine Runtime Model. Robustness properties (semaphore lifecycle, deadline semantics, financial operation completion) are **design constraints** for the executor implementation, not properties of the current YAML — they specify what the executor MUST guarantee.

**Chapter identity:** C13 follows the vertical-slice replan (C0-C6 in ARC42STORIES). C7-C12 are not defined — C13 is the first post-replan epic, deliberately numbered to avoid collision with the superseded old-plan chapters (1-12). ARC42STORIES §9.1 will be updated to add C13 when work begins.

**C13 delivery phasing:** Phase 1 and Phase 2 deliver validated YAML that parses, resolves, and structurally validates. Standalone playbook execution (for testing and simulation) works via yaml-core's `StepFileExecutor` without the engine dispatch bridge. Full case↔playbook runtime dispatch requires `StepFileCallableDispatcher` (engine cross-repo dependency — see below). This is a deployment dependency, not an architectural gap — the dispatch mechanism is fully designed and uses existing infrastructure patterns.

---

## Phase 1 — Foundation

### 1.1 SPI Extraction (#51)

Design SPI interfaces in `api/` that define the contracts step definitions need. Existing concrete classes become `@ApplicationScoped` implementations adapted to the SPI contract. Annotate with `@SimulationEligible` — the platform's APT generator (`SimulationDecoratorProcessor` in `casehub-platform-simulation-generator`) auto-creates CDI simulation decorators at compile time.

**SPI contracts (designed for step definition consumers):**

| Interface | Package | Impl adapts from | Purpose |
|-----------|---------|-------------------|---------|
| `MarketDataProvider` | `io.casehub.fsitrading.spi` | `SyntheticMarketDataProvider` | Price ticks, OHLCV bars, order book snapshots |
| `OrderExecutionService` | `io.casehub.fsitrading.spi` | `OrderService` | Submit order (sync response), cancel order, order status |
| `RiskAssessmentEngine` | `io.casehub.fsitrading.spi` | `FsiRiskAssessor` | Per-instrument risk evaluation, VaR calculation |
| `StrategyEvaluator` | `io.casehub.fsitrading.spi` | `AbstractStrategyAgent` subclasses | Strategy evaluation for instrument/regime |

**SPI method signatures (step-driven contract):**

```java
public interface RiskAssessmentEngine {
    RiskResult assess(String instrument, String scenario);
    // Impl: FsiRiskAssessor.assess() takes ConsensusResult + List<PositionEntity>.
    // Adapter builds a single-instrument ConsensusResult from the instrument param
    // and fetches current positions via PositionService.
}

public interface OrderExecutionService {
    OrderResult submit(String instrument, OrderSide side, BigDecimal quantity, OrderType type);
    void cancel(UUID orderId);
    OrderStatus status(UUID orderId);
    // Impl: OrderService.createFromDecision() takes TradeDecision.
    // Adapter constructs TradeDecision from step params.
    // submit() returns immediately with {orderId, PENDING, null} — fill-price
    // populates asynchronously via OrderService.fill(). The step output schema
    // declares fill-price as optional (required: false) for this reason.
}

public interface StrategyEvaluator {
    EvaluationResult evaluate(String instrument, String strategy, String regime);
    // Impl: AbstractStrategyAgent.evaluate(MarketSignal) returns sealed StrategyResponse.
    // Adapter constructs MarketSignal from instrument + regime, selects the
    // strategy agent by name, and maps StrategyResponse.Trade → {TRADE, side, confidence, rationale}
    // and StrategyResponse.Hold → {HOLD, null, confidence, rationale}.
}

public interface MarketDataProvider {
    MarketSnapshot snapshot(String instrument, int depth);
    // Impl: SyntheticMarketDataProvider already provides this.
    // SPI formalises the existing contract.
}
```

**Adaptation pattern:** The SPI signatures do NOT match the existing implementation signatures. This is intentional — the SPI is designed for the step definition consumer contract (simple typed inputs/outputs). Each implementation class wraps the existing logic with input construction and output mapping. For example, `RiskAssessmentEngineImpl.assess("AAPL", "stressed")` internally constructs a `ConsensusResult` for AAPL, fetches positions from `PositionService`, calls `FsiRiskAssessor.assess(consensus, positions)`, then extracts `{level, var-amount, exposure-pct}` from the `RiskAssessment` result.

**Each interface:**
- Lives in `api/src/main/java/io/casehub/fsitrading/spi/`
- Annotated `@SimulationEligible(name = "<kebab-name>", capabilities = {...})`
- Implementation class in `app/` adapts existing logic to SPI contract
- APT generates simulation decorator on `mvn compile`

**New API facade operations (MCP tool bindings):**

Step definitions reference MCP tools by registered name. The following new `@PlatformMutation`/`@PlatformQuery` operations are added to existing or new API facades, exposing the SPI contracts as MCP tools:

| Tool name | Facade | Annotation | SPI method |
|-----------|--------|------------|------------|
| `fsi/risk/assess` | `FsiRiskApi` (new) | `@PlatformMutation` | `RiskAssessmentEngine.assess()` |
| `fsi/risk/gate` | `FsiRiskApi` (new) | `@PlatformMutation` | `RiskGateService.evaluate()` |
| `fsi/risk/adjustThresholds` | `FsiRiskApi` (new) | `@PlatformMutation` | `RiskThresholdService.adjust()` |
| `fsi/positions/getByInstrument` | `FsiPositionApi` (existing) | `@PlatformQuery` | `PositionService.getByInstrument()` |
| `fsi/positions/close` | `FsiPositionApi` (existing) | `@PlatformMutation` | `PositionService.close()` |
| `fsi/orders/submit` | `FsiOrderApi` (existing) | `@PlatformMutation` | `OrderExecutionService.submit()` |
| `fsi/orders/halt` | `FsiOrderApi` (existing) | `@PlatformMutation` | `OrderSemaphoreService.halt()` |
| `fsi/orders/release` | `FsiOrderApi` (existing) | `@PlatformMutation` | `OrderSemaphoreService.release()` |
| `fsi/strategies/evaluate` | `FsiStrategyApi` (existing) | `@PlatformMutation` | `StrategyEvaluator.evaluate()` |
| `fsi/market-data/snapshot` | `FsiMarketDataApi` (existing) | `@PlatformQuery` | `MarketDataProvider.snapshot()` |

New `FsiRiskApi` facade:
```java
@McpDomain(value = "fsi/risk", basePath = "/api/fsi/risk")
@ApplicationScoped
public class FsiRiskApi {
    @Inject RiskAssessmentEngine riskEngine;
    @Inject RiskGateService riskGate;
    @Inject RiskThresholdService riskThresholds;

    @PlatformMutation("Assess risk for instrument")
    @RestPath("/assess")
    public Object assess(AssessRequest request) { ... }

    @PlatformMutation("Evaluate risk gate for trade approval")
    @RestPath("/gate")
    public Object gate(GateRequest request) { ... }

    @PlatformMutation("Adjust runtime risk thresholds")
    @RestPath("/adjustThresholds")
    public Object adjustThresholds(AdjustThresholdsRequest request) { ... }
}
```

**New domain services (not SPI extraction):**

The SPI extraction pattern above adapts EXISTING implementation classes to SPI contracts. The following three services are NEW domain implementations introduced by C13 — they have no existing code to adapt:

| Service | Purpose | Implementation scope |
|---------|---------|---------------------|
| `OrderSemaphoreService` | Reference-counted order-halt semaphore with ticket lifecycle | New `@ApplicationScoped` service with `ConcurrentHashMap<String, SemaphoreTicket>` and `AtomicInteger` counter. Tickets are UUID-keyed. `halt()` increments counter + returns ticket. `release(ticketId)` decrements counter + marks ticket released. Counter reaching 0 unblocks order submission. |
| `RiskGateService` | Pre-trade risk gate with approval/rejection workflow | New `@ApplicationScoped` service. `evaluate()` checks current risk assessment against configurable thresholds, returns `{approved, gate-id, rejection-reason}`. No external integration — purely algorithmic gate based on `RiskAssessmentEngine` output + position limits from configuration. |
| `RiskThresholdService` | Runtime risk threshold adjustment | New `@ApplicationScoped` service. `adjust()` modifies runtime threshold values (position limits, stop-loss percentages, exposure caps) stored in an in-memory `ConcurrentHashMap<ThresholdKey, BigDecimal>`. Adjustments are volatile (reset on restart) — persistent threshold configuration remains in `application.properties`. |

These services live in `app/src/main/java/io/casehub/fsitrading/app/service/` alongside existing services. They are scoped to Phase 1 delivery (required by step definitions that reference their MCP tools).

**Dependencies:**
- Add `casehub-platform-simulation-api` to `api/pom.xml` (compile)
- Add `casehub-platform-simulation-generator` to `app/pom.xml` (provided, APT)

**Configuration:**
- `simulation.yaml` in `app/src/main/resources/` — corpus strategy per SPI (key-lookup, sequential, random)

### 1.2 Trading Step Definitions (#52)

Author `trading-steps.yaml` declaring trading-specific step actions with typed input/output schemas and invoke bindings. Deployed alongside playbooks — no recompilation to add new actions.

**File:** `app/src/main/resources/steps/trading-steps.yaml`

```yaml
namespace: fsitrading

actions:
  assess-risk:
    description: Evaluate risk exposure for instrument
    inputs:
      instrument: { type: string, required: true }
      scenario: { type: string, enum: [normal, stressed, extreme] }
    outputs:
      level: { type: string, enum: [LOW, MEDIUM, HIGH, CRITICAL] }
      var-amount: { type: number }
      exposure-pct: { type: number }
    on-error: { retries: 1, fallback: fail }
    invoke:
      mcp: { tool: fsi/risk/assess }

  fetch-position:
    description: Get current position for instrument
    inputs:
      instrument: { type: string, required: true }
    outputs:
      quantity: { type: number }
      entry-price: { type: number }
      unrealised-pnl: { type: number }
    invoke:
      mcp: { tool: fsi/positions/getByInstrument }

  submit-order:
    description: Place an order
    inputs:
      instrument: { type: string, required: true }
      side: { type: string, enum: [BUY, SELL] }
      quantity: { type: number, required: true }
      order-type: { type: string, enum: [MARKET, LIMIT], default: MARKET }
    outputs:
      order-id: { type: string }
      status: { type: string }
      fill-price: { type: number, required: false }
    on-error: { retries: 0, fallback: fail }
    invoke:
      mcp: { tool: fsi/orders/submit }

  evaluate-strategy:
    description: Run strategy evaluation for instrument/regime
    inputs:
      instrument: { type: string, required: true }
      strategy: { type: string, required: true }
      regime: { type: string }
    outputs:
      action: { type: string, enum: [TRADE, HOLD] }
      side: { type: string, enum: [BUY, SELL] }
      confidence: { type: number }
      rationale: { type: string }
    invoke:
      mcp: { tool: fsi/strategies/evaluate }

  check-market-data:
    description: Get latest market data snapshot
    inputs:
      instrument: { type: string, required: true }
      depth: { type: integer, default: 5 }
    outputs:
      bid: { type: number }
      ask: { type: number }
      spread: { type: number }
      volume: { type: number }
    invoke:
      mcp: { tool: fsi/market-data/snapshot }

  trigger-risk-gate:
    description: Invoke risk gate for trade approval
    inputs:
      instrument: { type: string, required: true }
      proposed-action: { type: string, required: true }
      proposed-side: { type: string, enum: [BUY, SELL] }
      risk-level: { type: string, required: true }
    outputs:
      approved: { type: boolean }
      gate-id: { type: string }
      rejection-reason: { type: string, required: false }
    on-error: { retries: 0, fallback: reject }
    invoke:
      mcp: { tool: fsi/risk/gate }

  halt-new-orders:
    description: Acquire order-submission semaphore — blocks new order submission
    inputs:
      reason: { type: string, required: true }
      scope: { type: string, enum: [ALL, INSTRUMENT], default: ALL }
      instrument: { type: string }
    outputs:
      semaphore-id: { type: string }
      halted-at: { type: string }
    invoke:
      mcp: { tool: fsi/orders/halt }

  release-orders:
    description: Release order-submission semaphore — resumes order submission
    inputs:
      semaphore-id: { type: string, required: true }
    outputs:
      released: { type: boolean }
    invoke:
      mcp: { tool: fsi/orders/release }

  close-position:
    description: Close an existing position — creates counter-order for full or partial close
    inputs:
      instrument: { type: string, required: true }
      close-pct: { type: number, default: 100 }
    outputs:
      order-id: { type: string }
      side: { type: string }
      quantity: { type: number }
      status: { type: string }
    on-error: { retries: 1, fallback: notify }
    invoke:
      mcp: { tool: fsi/positions/close }

  adjust-risk-thresholds:
    description: Adjust runtime risk thresholds for instrument or portfolio-wide
    inputs:
      instrument: { type: string }
      threshold-type: { type: string, enum: [POSITION_LIMIT, STOP_LOSS, EXPOSURE_PCT] }
      new-value: { type: number, required: true }
    outputs:
      previous-value: { type: number }
      applied: { type: boolean }
    invoke:
      mcp: { tool: fsi/risk/adjustThresholds }

  reconcile-orders:
    description: Reconcile local order state with broker — catches orphaned orders from interrupted steps
    inputs:
      playbook-id: { type: string }
    outputs:
      reconciled-count: { type: integer }
      mismatches: { type: array }
    invoke:
      mcp: { tool: fsi/orders/reconcile }

  notify-risk-desk:
    description: Send alert to risk desk
    inputs:
      severity: { type: string, enum: [INFO, WARNING, CRITICAL], required: true }
      message: { type: string, required: true }
      instrument: { type: string }
    on-error: { retries: 1, fallback: skip }
    invoke:
      rest:
        method: POST
        url: ${config.fsitrading.risk-desk-url}/alerts

  analyse-sentiment:
    description: LLM-based market sentiment analysis
    inputs:
      instrument: { type: string, required: true }
      context: { type: string }
    outputs:
      sentiment: { type: string, enum: [BULLISH, BEARISH, NEUTRAL, MIXED] }
      confidence: { type: number }
      rationale: { type: string }
    invoke:
      agent:
        descriptor: sentiment-analyser

  explain-decision:
    description: LLM-based trade decision rationale for audit trail
    inputs:
      decision: { type: object, required: true }
      market-context: { type: object }
    outputs:
      explanation: { type: string }
      risk-factors: { type: string }
    invoke:
      agent:
        descriptor: decision-explainer

  calculate-var:
    description: Value at Risk calculation
    inputs:
      portfolio: { type: object, required: true }
      confidence-level: { type: number, default: 0.99 }
      horizon-days: { type: integer, default: 1 }
    outputs:
      var-amount: { type: number }
      expected-shortfall: { type: number }
    invoke:
      process:
        command: python3
        args: [scripts/var-calculator.py]

  check-fix-session:
    description: FIX gateway session status
    inputs:
      session-id: { type: string, required: true }
    outputs:
      connected: { type: boolean }
      latency-ms: { type: integer }
    invoke:
      process:
        command: fix-session-check
        args: [--session, ${inputs.session-id}]
```

**Invoke binding mapping:**

| Invoke type | Count | Actions |
|-------------|-------|---------|
| `mcp` | 11 | assess-risk, fetch-position, submit-order, evaluate-strategy, check-market-data, trigger-risk-gate, halt-new-orders, release-orders, close-position, adjust-risk-thresholds, reconcile-orders |
| `rest` | 1 | notify-risk-desk |
| `agent` | 2 | analyse-sentiment, explain-decision |
| `process` | 2 | calculate-var, check-fix-session |

MCP invoke bindings reference `@PlatformQuery`/`@PlatformMutation` operations on fsitrading API facades (existing from issue #48, new from §1.1). Tool names follow the platform's `@McpDomain` + method registration convention via `DynamicToolRegistrar`. Agent invoke bindings reference eidos agent descriptors by name — dispatched through the same trust-scored infrastructure as the arena and incident pipelines.

**Error model:**

Step definitions declare `on-error` behaviour governing what happens when the invoke handler returns `StepResult.Failure`:

| Property | Values | Default | Meaning |
|----------|--------|---------|---------|
| `retries` | integer >= 0 | `0` | Number of retry attempts before fallback |
| `fallback` | `fail`, `skip`, `reject`, `notify` | `fail` | What happens after exhausting retries |

Fallback behaviours:
- `fail` — step returns `StepResult.Failure`, propagated to playbook as step failure. State machine transitions to error state if defined, otherwise playbook halts.
- `skip` — step is treated as a no-op. Subsequent steps that reference `${result.action-name.*}` see null values. Useful for non-critical enrichment steps.
- `reject` — for gate steps. Treated as if the gate returned `approved: false`. The playbook takes the rejection branch.
- `notify` — step failure is logged and `notify-risk-desk` is called with the error details, then continues as `skip`. The notification is **fire-and-forget**: if `notify-risk-desk` itself fails (e.g., risk desk endpoint unreachable during a market crisis), the step still continues as `skip`. The notification failure is logged but does not propagate, escalate, or re-trigger the `notify` fallback. This prevents cascading failures when infrastructure is degraded.

At the playbook level, state machines define optional `on-step-failure` transitions:

```yaml
states:
  RESPONDING:
    steps: [...]
    on-step-failure: ESCALATED
```

If no `on-step-failure` is defined, a step failure in that state halts the playbook and fires a CDI `PlaybookFailureEvent` for observability.

**Variable resolution for REST bindings:**

The `${config.*}` variable prefix resolves Quarkus configuration properties. `config.fsitrading.risk-desk-url` maps to the Quarkus property `fsitrading.risk-desk-url` defined in `application.properties` or environment variables.

**Platform prerequisite:** yaml-core's `VariableSource` interface does not have a built-in `config()` factory. The following are built-in: `env()`, `systemProperty()`, `chain()`, `nested()`, `forEachContext()`, `matchContext()`. A `ConfigVariableSource` that bridges `SmallRyeConfig` to `VariableSource` is needed — tracked as a platform issue against `yaml-step-runtime` (see Cross-Repo Dependencies). Implementation is ~10 lines:

```java
VariableSource configSource = name ->
    ConfigProvider.getConfig()
        .getOptionalValue(name.replace('-', '.'), String.class)
        .orElse(null);
```

Registered under the `config` prefix in `VariableResolver` by `yaml-step-runtime`'s `StepFileExecutor` setup. This is a platform concern (all applications need config resolution in step files), not fsitrading-specific.

**Security — process invoke bindings:**

Two step definitions use process invoke bindings. Their input-passing mechanisms differ, producing different risk profiles:

| Step | Input mechanism | Risk profile |
|------|----------------|-------------|
| `check-fix-session` | Command-line arg interpolation: `args: [--session, ${inputs.session-id}]` | `session-id` is a deployment-managed string from FIX gateway configuration, not user input. Injection vector exists but input source is trusted. |
| `calculate-var` | Stdin JSON serialisation: `args: [scripts/var-calculator.py]` (static — no `${inputs.*}` in args) | `portfolio` object is serialised as JSON to the process's stdin by the `ProcessInvokeHandler` (same pattern as `PythonInvokeHandler`). No command-line injection vector — the args list contains only the script path. |

The process invoke handler's allow-list (engine#440: "Security model hardening for invoke handlers") provides defence-in-depth when it lands. The `fix-session-check` command pattern will be whitelisted with EXACT matching. The `python3` command is whitelisted with PREFIX matching scoped to `scripts/` directory.

**Verification:**
- `StepCatalog` resolves all 16 action names under `fsitrading` namespace
- `StepSchemaComposer` generates JSON Schema with correct parameter types
- No validation errors on load
- MCP tool names resolve against registered `@McpDomain` operations

### 1.3 Simulation Corpus (#53)

Two-layer simulation architecture:

**Layer 1 — `@SimulationEligible` corpus data files:** Static per-SPI response data in YAML. Decorators read these to return canned responses when the application runs in simulation mode.

| Corpus file | SPI | Key strategy |
|-------------|-----|--------------|
| `market-data-corpus.yaml` | `MarketDataProvider` | key-lookup by instrument symbol |
| `risk-assessment-corpus.yaml` | `RiskAssessmentEngine` | key-lookup by instrument + scenario |
| `order-execution-corpus.yaml` | `OrderExecutionService` | sequential (fill, partial, reject) |
| `strategy-evaluation-corpus.yaml` | `StrategyEvaluator` | key-lookup by instrument + regime |
| `agent-response-corpus.yaml` | Agent descriptors | key-lookup by prompt category |

**Layer 2 — Scenario temporal profiles:** Scenario files (ScenarioExecutor format) that sequence market events over time. Each profile scripts a trading session:

| Profile | What it scripts | Duration |
|---------|----------------|----------|
| `normal-market.yaml` | Steady session, mild volatility, U-shaped volume | ~6h simulated |
| `flash-crash.yaml` | Sudden price drop -> liquidity withdrawal -> recovery | ~30min simulated |
| `regime-shift.yaml` | Gradual transition: trending -> mean-reverting | ~2h simulated |
| `overnight-gap.yaml` | Market close -> gap open -> morning recovery | ~12h simulated |

Profiles use `delivery: simulated` with a new `FsiSimulationConnector` that accepts injected market events and fires them as CDI events via `SyntheticMarketDataProvider`. The connector implements the scenario format's `DemoSpi` interface, accepting scenario steps with `target: fsi-market-data` and injecting them as `PriceTick`, `MarketEvent`, or `RegimeChanged` CDI events depending on the step's `action` field. Timed triggers sequence events with configurable speed multiplier.

Note: `FsiChannelEventAdapter` is a publisher (subscribes to `EventStreamBus<TrendSummary>` and writes to qhorus channels) — it is NOT an injection target. The simulation connector feeds the pipeline at the source (SyntheticMarketDataProvider), and the adapter publishes downstream summarisation outputs to channels as normal.

---

## Phase 2 — Playbooks

### Error Handling Model

All playbooks follow the step catalog's `StepResult.Success`/`StepResult.Failure` model. Error propagation:

1. **Step level:** Each step's `on-error` declaration (section 1.2) governs retry and fallback behaviour.
2. **State level:** State machine states declare optional `on-step-failure` transitions. When a step fails within a state and its `on-error` fallback is `fail`, the state machine transitions to the error state.
3. **Playbook level:** If no error state handles the failure, the playbook halts. The executor's try-finally block ensures semaphore cleanup (see §State Machine Runtime Model, constraint 2). The case's CBR observer records the failure outcome via the standard `CaseOutcomeEvent` mechanism.

Financial system defaults: all order-submission steps (`submit-order`, `close-position`) use `retries: 0` and `fallback: fail` — no retrying financial operations. Risk assessment steps use `retries: 1` with backoff. Gate steps use `fallback: reject` — a failed gate is treated as a rejection, never as an approval.

### State Machine Runtime Model

Playbook state machines use a YAML design notation (string-named states, step lists, `on-step-failure` transitions). The engine's `PlaybookStateMachineExecutor` maps this notation to yaml-core's `OrcStateMachine<S extends Enum<S>>` at runtime:

| YAML notation | OrcStateMachine mapping |
|---|---|
| String-named states (`DETECTED`, `RESPONDING`, etc.) | Generated Java enum at playbook load time. Each playbook's state names become enum constants via dynamic enum generation (`EnumSet` factory or bytecode generation). |
| Step lists per state | `onEnter(S, StateHandler)` — the StateHandler executes the step list sequentially via `StepWalker` with the playbook's `StepResultStore` and `VariableResolver`. |
| `on-step-failure: ESCALATED` | Step execution wraps in try-catch. On `StepResult.Failure` (after `on-error` fallback exhausted), the handler calls `stateMachine.transition(currentState, errorState)`. |
| `deadline: 120s → ESCALATED` | `ScheduledExecutorService` schedules a deadline callback at playbook start. On expiry, the callback calls `stateMachine.transition(currentState, deadlineState)` — but only after any in-flight financial operation step completes (see §Timer and Deadline Semantics). |
| `transition: ESCALATED` (explicit) | Direct `stateMachine.transition(currentState, targetState)` call within the step handler. |

**Design constraints for the executor:**

1. **Step results are playbook-scoped.** A single `StepResultStore` instance persists across ALL state transitions within a playbook execution. Cross-state variable references (e.g., `${result.halt-new-orders.semaphore-id}` accessed in MONITORING after being set in DETECTED) are valid because `DefaultStepResultStore` uses a flat `ConcurrentHashMap<String, Map<String, Object>>` keyed by step name with no state partitioning. The executor MUST NOT create per-state `StepResultStore` instances.

2. **Terminal state cleanup is guaranteed.** The executor wraps playbook execution in a try-finally block. The finally block releases all semaphore tickets owned by the playbook (via `OrderSemaphoreService.releaseAllForPlaybook(playbookInstanceId)`). This is the safety net for abnormal termination — explicit release steps in RESOLVED/ESCALATED states are the primary mechanism, and the try-finally is defence-in-depth. This replaces the previously-specified `on-playbook-complete` hook with a simpler, non-novel mechanism that requires no new platform infrastructure.

3. **Financial operation completion on deadline.** When a deadline expires during an in-flight financial step (`submit-order`, `close-position`), the deadline callback waits for the step's MCP handler to complete its request-response cycle before transitioning. See §Timer and Deadline Semantics.

**Cross-repo dependency:** The `PlaybookStateMachineExecutor` is engine work — see §Cross-Repo Dependencies.

### Timer and Deadline Semantics

Playbooks use two timing mechanisms:

**Deadlines** are absolute — measured from playbook start (wall-clock). A 120s deadline means 120s from the moment the playbook begins executing, regardless of how time is spent across states. Time in prior states counts against the overall deadline. When the deadline expires, the state machine transitions to the deadline fallback state immediately, interrupting any in-progress step.

**Timeouts** are per-step or per-module — measured from the start of that step/module invocation. A 30s timeout on `scatter-response-team` means 30s from when that module begins, not from playbook start. Timeouts are independent budgets within the overall deadline.

**Interaction:** If a playbook has a 120s deadline and a module uses 28s of its 30s timeout, the remaining deadline budget is 120 - (time elapsed since playbook start), not 120 - 28. All timing mechanisms share the same wall-clock.

**Financial operation completion:** When a deadline expires while a financial operation step (`submit-order`, `close-position`) is in-flight, the deadline does NOT interrupt the step's MCP handler call. The handler completes its current request-response cycle (bounded by the handler's own timeout, typically 30s). The step's `StepResult` reflects whatever happened (order submitted but fill pending, order rejected, etc.). After the in-flight step completes, the state machine transitions to the deadline fallback state. This prevents orphaned orders where the broker receives a SELL but the system never processes the response.

**Reconciliation:** The ESCALATED state in playbooks that submit orders includes a reconciliation step (`fsi/orders/reconcile`) that queries broker order status for any orders submitted during the playbook's execution. This catches orders that were submitted, filled at the broker, but whose fill responses arrived after the state machine transitioned. The reconciliation step updates local `OrderEntity` and `PositionEntity` state to match broker reality.

**Simulation speed:** In scenario profiles with speed multiplier > 1, simulated-time delays (e.g., `delay: 10s between iterations`) are scaled by the multiplier. Deadlines and timeouts remain wall-clock — a 120s deadline expires in 120 real seconds regardless of speed multiplier. This prevents simulation from masking timing bugs.

### Trigger Disambiguation

When a single event could trigger multiple playbooks, a priority model prevents conflicts:

| Trigger condition | Playbook | Priority |
|-------------------|----------|----------|
| `RegimeChanged` to `VOLATILE` + price drop > 5% in 60s | Flash Crash Response | 1 (highest) |
| Risk assessment returns CRITICAL/HIGH | Risk Escalation | 2 |
| `RegimeChanged` (any transition) | Market Regime Shift | 3 |

**Rules:**
- Higher-priority playbooks suppress lower-priority ones for the same instrument when triggers overlap.
- Flash crash response fires only when `RegimeChanged` indicates extreme volatility AND rapid price decline (not every VOLATILE transition). The case binding's `when` clause checks both conditions.
- If flash crash response activates for instrument X, market regime shift for instrument X is suppressed. Other instruments are unaffected.
- Risk escalation can run concurrently with flash crash (different concern: individual risk vs systemic event) but the flash crash's `halt-new-orders` semaphore prevents risk escalation from submitting new orders.
- **Flash crash singleton semantics:** At most one flash crash playbook instance runs at a time. If a second instrument triggers flash crash while one is already active, the trigger is suppressed — the active instance's RESPONDING state already assesses ALL exposed instruments (not just the triggering instrument), so a second instance would redundantly close the same positions. The case binding enforces this via `maxInstances: 1` on the flash crash case definition. If the second trigger represents a genuinely broader event (more instruments affected), the active instance's MONITORING loop detects the continued high risk and re-enters RESPONDING.

**Close-position idempotency:** `close-position` is idempotent at the domain level. If a position for an instrument is already closed (quantity = 0) or is being closed by another playbook, `PositionService.close()` returns `{order-id: null, side: null, quantity: 0, status: ALREADY_CLOSED}` — no counter-order is created. This prevents double-close across concurrent playbook instances (e.g., flash crash and risk escalation both attempting to close the same instrument's position).

### Order Semaphore Model

The `halt-new-orders` / `release-orders` steps use a **reference-counted** semaphore model:

- **Acquire** (`halt-new-orders`): increments a global order-halt counter and returns a unique `semaphore-id` ticket. If the counter was 0, order submission is now blocked. If already > 0, order submission remains blocked (already halted) and the counter increments. Each acquire is idempotent from the caller's perspective — it always returns a valid ticket.

- **Release** (`release-orders`): decrements the counter and marks the ticket as released. Orders resume only when the counter reaches 0. Releasing an already-released ticket is a no-op (idempotent) — the counter is not decremented again.

**Concurrent playbook semantics:** When flash crash (priority 1) and risk escalation (priority 2) both call `halt-new-orders`, counter = 2. Each playbook releases its own ticket independently. Orders resume only when both playbooks have released. This is correct: if both a systemic flash crash AND an individual risk escalation are active, orders should stay halted until BOTH resolve.

**Lifecycle guarantee:** Every playbook that acquires a semaphore MUST release it in every terminal state — RESOLVED, ESCALATED, or any error path. The executor's try-finally block (see §State Machine Runtime Model, constraint 2) provides automatic semaphore cleanup: on playbook termination (normal or abnormal), `OrderSemaphoreService.releaseAllForPlaybook(playbookInstanceId)` releases any unreleased tickets. The explicit release steps in terminal states are the primary mechanism; the try-finally is defence-in-depth for abnormal termination (exception, deadline expiry during non-financial step, process crash).

### Shared Modules

Before authoring individual playbooks, extract common coordination patterns as parameterised yaml-core modules:

**Module: `risk-gate`**
```yaml
module:
  name: risk-gate
  parameters:
    instrument: { type: string, required: true }
    proposed-action: { type: string, required: true }
    proposed-side: { type: string }
    escalation-severity: { type: string, default: CRITICAL }

steps:
  - fsitrading.assess-risk:
      instrument: ${param.instrument}
  - if: ${result.assess-risk.level} == 'CRITICAL' || ${result.assess-risk.level} == 'HIGH'
    then:
      - fsitrading.trigger-risk-gate:
          instrument: ${param.instrument}
          proposed-action: ${param.proposed-action}
          proposed-side: ${param.proposed-side}
          risk-level: ${result.assess-risk.level}
      - if: ${result.trigger-risk-gate.approved} == false
        then:
          - fsitrading.notify-risk-desk:
              severity: ${param.escalation-severity}
              message: "Risk gate rejected: ${param.proposed-action} on ${param.instrument}. Reason: ${result.trigger-risk-gate.rejection-reason}"
```

**Module: `parallel-assessment`** (renamed from `scatter-response-team`)
```yaml
module:
  name: parallel-assessment
  parameters:
    perspectives: { type: list, required: true }
    instrument: { type: string, required: true }
    deadline: { type: string, default: "30s" }

steps:
  - parallel:
      forEach: ${param.perspectives}
      as: perspective
      steps:
        - fsitrading.analyse-sentiment:
            instrument: ${param.instrument}
            context: ${each.perspective}
    timeout: ${param.deadline}
    on-error: { retries: 0, fallback: notify }
```

This module dispatches the same LLM agent step (`analyse-sentiment`) in parallel with different context strings, producing multi-perspective situational assessment. Each entry in `perspectives` is a context prompt (e.g., "Assess risk exposure impact of current event", "Evaluate regulatory compliance implications"). The module does NOT dispatch different step actions per entry — that would require dynamic invoke binding resolution, which the step catalog doesn't support (invoke bindings are resolved statically at step registration time).

**Removed modules:**

Two modules from round 1 have been removed because they require yaml-core capabilities that don't exist:

- **`deadline-escalation`** — attempted to wrap arbitrary step lists as a module parameter. yaml-core module parameters are typed values (string, number, boolean, list of strings), not structural YAML content. The deadline-escalation pattern is already covered by existing constructs: `timeout` decorator + step-level `on-error: { fallback: notify }` + state-level `on-step-failure` transitions.

- **`state-machine-chapter`** — attempted to generate N `match/cases` entries from a parameter list. yaml-core's `forEach` decorator expands list elements into step copies, but cannot generate match case entries dynamically. Match cases are structural YAML that must be written concretely. Each playbook writes its state machine inline using `match/cases` + `loop` with concrete states and transitions specific to that playbook's domain.

**Phase 2 verification (without engine runtime):**
- All 5 playbooks parse without errors via `StepDefinitionParser` + yaml-core YAML parsing
- All step references resolve against the step catalog (`StepCatalog.resolve()` returns non-empty for every `fsitrading.*` action name used in playbooks)
- Module imports (`risk-gate`, `parallel-assessment`) resolve and expand correctly
- Variable references (`${result.*}`, `${event.*}`, `${config.*}`, `${param.*}`) are structurally valid (correct prefix, valid dot-path syntax)
- Output reference validation: every `${result.action-name.field}` expression references a field declared in the corresponding step definition's `outputs`
- `forEach` declarations reference valid data sources
- Shared module parameter types match playbook-provided argument types

**Phase 2 verification (with engine runtime — after PlaybookStateMachineExecutor lands):**
- State machine transitions execute correctly: DETECTED → RESPONDING → MONITORING → RESOLVED
- `on-step-failure` transitions fire on `StepResult.Failure`
- Deadline fallback transitions fire after wall-clock timeout
- Semaphore acquire/release lifecycle completes in all terminal paths
- Financial operation completion: deadline does NOT interrupt in-flight `submit-order`/`close-position` MCP calls
- Cross-state variable references resolve (`${result.halt-new-orders.semaphore-id}` accessible in MONITORING after being set in DETECTED)
- Simulation scenario profiles drive playbook execution end-to-end via `FsiSimulationConnector`

### 2.1 Flash Crash Response (#54)

**Trigger:** Case binding on `RegimeChanged` event where `newRegime == VOLATILE` AND price decline > 5% within 60s (see Trigger Disambiguation).

**Structure:**
```
State machine: DETECTED -> RESPONDING -> MONITORING -> RESOLVED
Overall deadline: 120s -> escalation to ESCALATED state

DETECTED:
  - parallel:
    - fsitrading.assess-risk: { instrument: ${event.instrument}, scenario: extreme }
    - fsitrading.check-fix-session: { session-id: ${config.fsitrading.fix-session-id} }
  - if: ${result.check-fix-session.connected} == false
    then:
      - fsitrading.notify-risk-desk:
          severity: CRITICAL
          message: "FIX session disconnected during flash crash — orders cannot execute"
      - transition: ESCALATED
  - fsitrading.halt-new-orders: { reason: "flash-crash-detected", scope: ALL }
  on-step-failure: ESCALATED

RESPONDING:
  - import: parallel-assessment
    perspectives:
      - "Assess risk exposure impact: current positions, VaR breach potential, counterparty exposure"
      - "Evaluate regulatory compliance: MiFID II obligations, reporting requirements, position limit breaches"
      - "Assess execution capability: FIX session health, available liquidity, execution venue status"
    instrument: ${event.instrument}
    deadline: 30s
  - block:
    - fsitrading.assess-risk: { instrument: ${event.instrument}, scenario: extreme }
    - forEach instruments where ${result.assess-risk.exposure-pct} > 0:
      - import: risk-gate
        instrument: ${each.instrument}
        proposed-action: close-position
      - if: ${result.trigger-risk-gate.approved} != false
        then:
          - fsitrading.close-position: { instrument: ${each.instrument} }
    on-step-failure: ESCALATED

MONITORING:
  - loop:
    - fsitrading.assess-risk: { instrument: ${event.instrument}, scenario: stressed }
    until: ${result.assess-risk.level} == 'LOW' || ${result.assess-risk.level} == 'MEDIUM'
    delay: 10s
    max-iterations: 12
  - fsitrading.release-orders: { semaphore-id: ${result.halt-new-orders.semaphore-id} }
  on-step-failure: ESCALATED

ESCALATED (deadline fallback OR step failure):
  - fsitrading.notify-risk-desk:
      severity: CRITICAL
      message: "Flash crash response escalated — manual intervention required"
  - fsitrading.reconcile-orders: {}
  - wait-state: human-approval
    timeout: 300s
  - fsitrading.release-orders: { semaphore-id: ${result.halt-new-orders.semaphore-id} }
```

**Semaphore lifecycle:** The semaphore acquired in DETECTED is released in both normal (MONITORING → RESOLVED) and error (ESCALATED) paths. In ESCALATED, release happens after human approval so that orders remain halted during the manual review period. The executor's try-finally block provides a safety net — if the playbook terminates without reaching the release step, `OrderSemaphoreService.releaseAllForPlaybook()` releases the ticket automatically (see §Order Semaphore Model, §State Machine Runtime Model constraint 2).

**Portfolio state consistency:** Risk assessment and position closes are sequential per instrument (no `concurrency` on the forEach). This avoids the race condition where parallel closes mutate portfolio exposure while concurrent risk assessments read stale state. The portfolio is assessed once (single `assess-risk` call) to identify all exposed instruments, then each instrument is closed sequentially through the risk gate. This is slower than parallel close but guarantees consistent risk decisions.

**FIX session handling:** The DETECTED state checks FIX session status in parallel with risk assessment. If the session is disconnected, the playbook transitions directly to ESCALATED — there is no point closing positions if orders cannot execute.

### 2.2 Strategy Evaluation Cycle (#55)

**Trigger:** Scheduled evaluation or manual trigger via `POST /api/evaluations/trigger`.

**Structure:**
```
forEach instruments in watchlist:
  - parallel evaluate-strategy: [momentum, mean-reversion, stat-arb]
    quorum: 2 of 3 agree
  - import: risk-gate
  - if approved: submit-order + explain-decision
  - if rejected: notify-risk-desk with rationale
```

**Quorum semantics:**

"2 of 3 agree" means 2 or more strategies return the same `action` value (TRADE or HOLD):

| Scenario | Quorum | Result |
|----------|--------|--------|
| 2+ TRADE, any direction | Met | Proceed to risk gate |
| 2+ HOLD | Met | Skip — no order submitted |
| 1 TRADE, 1 HOLD, 1 TRADE (different direction) | Met (2 TRADE) | Proceed to risk gate |
| All different (impossible with TRADE/HOLD binary) | N/A | Binary action means 2+ always agree on one |

When quorum is met on TRADE:
- **Direction** comes from the highest-confidence TRADE response's `side` field. If momentum says BUY(0.8) and mean-reversion says SELL(0.7), the order direction is BUY.
- **Confidence** for the risk gate is the average confidence of the agreeing TRADE strategies.
- **Rationale** concatenates all agreeing strategies' rationales for the audit trail.

When quorum is met on HOLD: no order is submitted. The cycle advances to the next instrument.

### 2.3 Overnight Incident Response (#56)

**Trigger:** RAS situation event (volatility-spike, market-anomaly, active-breach).

**Structure:**
```
State machine: DETECTED -> TRIAGING -> RESPONDING -> POST_MORTEM
SLA deadline per severity: CRITICAL=5min, HIGH=15min, MEDIUM=60min
(SLA deadline is the overall playbook deadline for this instance)

TRIAGING:
  - parallel: assess-risk + analyse-sentiment
  on-step-failure: RESPONDING (degrade gracefully — triage with partial data)

RESPONDING:
  - match ${triage.resolution-type}:
    - "automated": execute response plan
    - "human-required": wait-state for trader approval
      timeout: remaining SLA budget -> fallback auto-reject
  on-step-failure: POST_MORTEM

POST_MORTEM:
  - explain-decision for audit trail
```

**SLA deadline interaction:** The SLA deadline is the playbook's overall deadline (absolute from start, per §Timer and Deadline Semantics). The wait-state for trader approval gets the remaining budget: if triage takes 4 minutes of a 5-minute CRITICAL SLA, the wait-state gets ~1 minute before auto-rejection. The wait-state's `timeout` is set to `remaining SLA budget` — calculated as `playbook-start-time + SLA - now()` by the step executor's deadline tracking. When the timeout expires, the `on-error: { fallback: reject }` behaviour treats it as an auto-rejection and transitions to POST_MORTEM.

### 2.4 Risk Escalation (#57)

**Trigger:** Risk assessment returning CRITICAL or HIGH.

**Structure:**
```
match ${risk.level}:
  - CRITICAL:
    - fsitrading.halt-new-orders: { reason: "risk-escalation", scope: ALL }
    - fsitrading.notify-risk-desk: { severity: CRITICAL }
    - wait-state for risk committee approval
      timeout: 60s -> auto-reject pending trades
    - on approval:
      - fsitrading.release-orders: { semaphore-id: ${result.halt-new-orders.semaphore-id} }
      - fsitrading.adjust-risk-thresholds: { threshold-type: EXPOSURE_PCT, new-value: ${approval.new-threshold} }
    - on rejection:
      - forEach exposed positions:
        - fsitrading.close-position: { instrument: ${each.instrument} }
        (sequential — same rationale as 2.1)
      - fsitrading.release-orders: { semaphore-id: ${result.halt-new-orders.semaphore-id} }
  - HIGH:
    - fsitrading.notify-risk-desk: { severity: WARNING }
    - continue with heightened monitoring loop
```

### 2.5 Market Regime Shift (#58)

**Trigger:** `RegimeChanged` event from Market Pulse pipeline (suppressed if flash crash response activates for the same instrument — see Trigger Disambiguation).

**Structure:**
```
- Retrieve CBR cases for similar regime transitions
- match ${regime.new}:
  - TRENDING: adjust lookback windows, signal thresholds
  - MEAN_REVERTING: adjust position limits, reversion parameters
  - VOLATILE: reduce position sizes, widen stop-losses
  - default: conservative default posture
- Monitor convergence: loop until strategy P&L stabilises
  delay: 30s, max iterations: 20
- Record outcome for CBR retention (automatic via case)
```

---

## CBR Integration

### Observer Generalisation

The existing CBR observers (`FsiCaseOutcomeObserver`, `FsiStepOutcomeObserver`) are hardcoded to `CASE_TYPE = "overnight-incident"`. C13 generalises these to support all 5 playbook case types:

**Approach:** Extract an `FsiFeatureExtractor` SPI interface from the existing concrete class. Register per-case-type implementations via CDI qualifier. The observer dispatches to the correct extractor based on case type.

**Step 1 — Extract interface:**

```java
// api/src/main/java/io/casehub/fsitrading/spi/FsiFeatureExtractor.java
public interface FsiFeatureExtractor {
    String caseType();
    Map<String, Object> extractFromSnapshot(Map<String, Object> snapshot, Instant detectedAt);
}
```

**Step 2 — Rename existing class, implement interface:**

The existing `FsiFeatureExtractor` (concrete class) becomes `OvernightIncidentFeatureExtractor implements FsiFeatureExtractor`. Its current `extractFromSnapshot()` method already matches the interface contract. `caseType()` returns `"overnight-incident"`.

**Step 3 — CDI-based registry:**

```java
@ApplicationScoped
public class FsiFeatureExtractorRegistry {
    private final Map<String, FsiFeatureExtractor> extractors;

    @Inject
    public FsiFeatureExtractorRegistry(Instance<FsiFeatureExtractor> all) {
        this.extractors = new HashMap<>();
        all.forEach(e -> extractors.put(e.caseType(), e));
    }

    public Optional<FsiFeatureExtractor> get(String caseType) {
        return Optional.ofNullable(extractors.get(caseType));
    }
}
```

**Step 4 — Per-case-type implementations:**

| Case type | Implementation | Data sources | Key features |
|-----------|---------------|-------------|-------------|
| `overnight-incident` | `OvernightIncidentFeatureExtractor` (existing, renamed) | `SyntheticMarketDataProvider.findRecentByInstrument()` | severity, eventType, instrument, marketVolatility, timeOfDay |
| `flash-crash-response` | `FlashCrashFeatureExtractor` | `SyntheticMarketDataProvider` (price ticks for drop calculation) + snapshot fields | priceDropPct (computed from price tick delta), timeToDetect (snapshot `detectedAt` - first tick), instrumentCount, regimeAtOnset |
| `strategy-evaluation` | `StrategyEvaluationFeatureExtractor` | Snapshot fields only (evaluation results are in the case context) | instrument, regime, strategyConsensus, quorumMargin (computed from strategy response confidences) |
| `risk-escalation` | `RiskEscalationFeatureExtractor` | Snapshot fields only | riskLevel, exposurePct, instrument, resolutionType |
| `market-regime-shift` | `RegimeShiftFeatureExtractor` | Snapshot fields + `SyntheticMarketDataProvider` (for transition speed calculation) | oldRegime, newRegime, transitionSpeed, instrumentCount |

**Step 5 — Updated observer:**

```java
@Override
public void onOutcome(CaseOutcomeEvent event) {
    Optional<FsiFeatureExtractor> extractor = extractorRegistry.get(event.caseType());
    if (extractor.isEmpty()) return;
    // ... rest of observer logic using extractor.get()
}
```

The observer no longer needs a hardcoded case type set — it accepts any case type that has a registered extractor. New case types are supported by adding an `@ApplicationScoped` `FsiFeatureExtractor` implementation.

---

## Phase 3 — Trading Content

### 3.1 Prose Resolution Documents (#62)

Prose guidance for situations requiring human judgment. Ingested via engine's `CorpusSourceAdapter` into the CBR store alongside plan traces.

**Documents:**

| Document | Situation | Content |
|----------|-----------|---------|
| Counterparty default response | Counterparty failure | Negotiation steps, escalation contacts, settlement alternatives |
| Regulatory inquiry response | MiFID II / Dodd-Frank inquiry | Disclosure requirements, communication templates, legal escalation |
| Unprecedented market event | No historical precedent | Assessment framework, conservative posture, info gathering checklist |
| System failure during market hours | Exchange/system outage | Manual trading procedures, exchange notification, failover steps |
| Margin call response | Margin threshold breach | Collateral assessment, priority liquidation sequence, counterparty comms |

**Format:** Engine's `ResolutionGuideInput` — problem, solution, steps, features (for CBR matching), domain.

**Three-tier integration:**
1. **Automated:** YAML playbooks handle known patterns (flash crash, regime shift)
2. **Guided:** Prose documents surface for situations requiring judgment
3. **Novel:** When no document matches, nearest analogs surface with gap analysis

**Phase 3 verification:**
- All 5 prose resolution documents parse as valid `ResolutionGuideInput` YAML
- Feature fields in each document are non-empty and use valid CBR feature types
- `CorpusSourceAdapter` ingests each document into the CBR store without errors
- CBR similarity search retrieves relevant documents for test queries matching each document's situation (e.g., "counterparty failure" retrieves `counterparty-default.yaml`)
- Three-tier routing: given a scenario with a matching playbook, a matching prose document, and no match, the system returns automated/guided/novel responses respectively

---

## Cross-Repo Dependencies

**Blocking:** The following engine issue blocks case↔playbook runtime dispatch:

| Repo | Issue | What it provides | Blocks |
|------|-------|-----------------|--------|
| engine | TBD | `StepFileCallableDispatcher` — `CallableDispatcher` implementation that resolves step file paths and executes them via `StepFileExecutor`. Registers as `casehub:step-file` in `CallableDispatchRegistry`. | Case↔playbook runtime dispatch. Without this, playbooks can be parsed and validated but not invoked from case bindings. |

**Blocking** playbook runtime execution (but not authoring/validation):

| Repo | Issue | What it provides | Blocks |
|------|-------|-----------------|--------|
| engine | TBD | `PlaybookStateMachineExecutor` — maps YAML state machine notation (string-named states, step lists per state, `on-step-failure`, deadline fallback) to `OrcStateMachine<S extends Enum<S>>`. Includes dynamic enum generation for state names, `StepWalker`-based state handlers, try-finally semaphore cleanup, and financial operation deadline completion. See §State Machine Runtime Model. | All playbook runtime execution. Without this, playbooks can be parsed and validated but state machine transitions cannot execute. |
| platform | TBD | `ConfigVariableSource` — `VariableSource` implementation bridging `SmallRyeConfig` to yaml-core variable resolution. Registered under `config` prefix by `StepFileExecutor`. ~10 lines. | REST invoke bindings using `${config.*}` variable references (e.g., `notify-risk-desk`). Without this, `VariableResolver` throws `UnresolvedVariableException` on the `config` prefix. |

**Not blocking** fsitrading work (playbook authoring, validation, and simulation proceed independently):

| Repo | Issue | What it provides | Blocks |
|------|-------|-----------------|--------|
| engine | #1081 | CBR retrieval feedback (`RetrievalFeedbackObserver`, `SelectionFeedbackRecorder`) — currently disabled pending neocortex-memory-api publication | Per-step and per-selection CBR feedback loops. Case-level retain (Layer 2) works now; Layers 1 and 3 are disabled. |
| engine | #1190 | Generic yaml-core -> CBR outcome recording bridge | Playbook-level CBR (case-level works now) |
| engine | #1191 | CBR plan -> yaml-core playbook mapping | CBR-driven playbook selection |
| engine | #1192 | Goal-modulated playbook selection | Adaptive playbook routing |
| pages | #498 | Scenario lifecycle state + versioning | Scenario management UI |
| pages | #499 | Event-triggered scenario activation | Automated scenario execution |
| pages | #500 | Scenario outcome tracking + CBR linkage | Scenario analytics |
| pages | #501 | Step catalog browser | Step definition exploration UI |

---

## File Layout

```
api/src/main/java/io/casehub/fsitrading/spi/
  MarketDataProvider.java          # @SimulationEligible
  OrderExecutionService.java       # @SimulationEligible
  RiskAssessmentEngine.java        # @SimulationEligible
  StrategyEvaluator.java           # @SimulationEligible
  FsiFeatureExtractor.java         # CBR feature extraction SPI

app/src/main/java/io/casehub/fsitrading/app/api/
  FsiRiskApi.java                  # new — @McpDomain("fsi/risk")

app/src/main/java/io/casehub/fsitrading/app/spi/
  RiskAssessmentEngineImpl.java    # adapts FsiRiskAssessor
  OrderExecutionServiceImpl.java   # adapts OrderService
  StrategyEvaluatorImpl.java       # adapts AbstractStrategyAgent subclasses
  MarketDataProviderImpl.java      # adapts SyntheticMarketDataProvider

app/src/main/java/io/casehub/fsitrading/app/service/
  OrderSemaphoreService.java       # new — reference-counted order semaphore
  RiskGateService.java             # new — pre-trade risk gate
  RiskThresholdService.java        # new — runtime threshold management

app/src/main/java/io/casehub/fsitrading/app/cbr/
  FsiFeatureExtractorRegistry.java           # CDI-based extractor dispatch
  OvernightIncidentFeatureExtractor.java     # renamed from FsiFeatureExtractor
  FlashCrashFeatureExtractor.java            # new
  StrategyEvaluationFeatureExtractor.java    # new
  RiskEscalationFeatureExtractor.java        # new
  RegimeShiftFeatureExtractor.java           # new

app/src/main/java/io/casehub/fsitrading/app/simulation/
  FsiSimulationConnector.java      # DemoSpi for scenario injection

app/src/main/resources/
  steps/
    trading-steps.yaml             # 16 step definitions
  modules/
    risk-gate.yaml                 # shared module — risk assessment + gate + escalation
    parallel-assessment.yaml       # shared module — multi-perspective LLM assessment
  playbooks/
    flash-crash-response.yaml      # #54
    strategy-evaluation-cycle.yaml  # #55
    overnight-incident.yaml        # #56
    risk-escalation.yaml           # #57
    market-regime-shift.yaml       # #58
  simulation/
    simulation.yaml                # corpus strategy config
    corpus/
      market-data-corpus.yaml
      risk-assessment-corpus.yaml
      order-execution-corpus.yaml
      strategy-evaluation-corpus.yaml
      agent-response-corpus.yaml
    profiles/
      normal-market.yaml           # scenario format
      flash-crash.yaml
      regime-shift.yaml
      overnight-gap.yaml
  resolution/
    counterparty-default.yaml      # prose resolution docs
    regulatory-inquiry.yaml
    unprecedented-event.yaml
    system-failure.yaml
    margin-call.yaml
```

---

## References

- [yaml-language-guide.md](../../parent/docs/repos/casehub-platform/yaml-language-guide.md) — yaml-core meta-language reference (variables, modules, step types, decorators)
- [cbr-playbook-guide.md](../../parent/docs/repos/casehub-engine/cbr-playbook-guide.md) — CBR configuration and feature design
- [unified-resolution-guide.md](../../parent/docs/repos/casehub-engine/unified-resolution-guide.md) — resolution pipeline (automated, guided, novel)
- [three-pathways.md](../../parent/docs/repos/casehub-engine/three-pathways.md) — YAML, Java, TypeScript case definition pathways
- [scenario-format.md](../../parent/docs/platform/scenario-format.md) — scenario file schema for scripted execution
- [dynamic-step-catalog-design.md](../../platform/docs/specs/issue-429-yaml-type-system/2026-09-25-dynamic-step-catalog-design.md) — step catalog SPI, invoke handlers, StepResult model
- [platform/simulation-api/.../SimulationEligible.java](../../platform/simulation-api/src/main/java/io/casehub/platform/simulation/SimulationEligible.java) — @SimulationEligible annotation
- [platform blog: step-catalog-and-block-control-flow](../../platform/docs/blog/2026-09-27-mdp01-step-catalog-and-block-control-flow.md) — step catalog architecture, block control flow
- [docs/DOMAIN.md](../../fsitrading/docs/DOMAIN.md) — FSI trading domain background
- [docs/guides/consumer-guide.md](../../fsitrading/docs/guides/consumer-guide.md) — fsitrading current state (C1-C7)
- [app/cbr/](../../fsitrading/app/src/main/java/io/casehub/fsitrading/app/cbr/) — existing CBR integration (FsiCaseOutcomeObserver, FsiStepOutcomeObserver, FsiPlanAdapter)
- [casehubio/fsitrading#50](https://github.com/casehubio/fsitrading/issues/50) — epic issue with dependency graph
