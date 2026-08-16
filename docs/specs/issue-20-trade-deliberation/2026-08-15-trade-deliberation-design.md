# C3 — Trade Deliberation Design Spec

**Date:** 2026-08-15
**Issue:** casehubio/fsitrading#20
**Depends on:** C1 (Strategy Arena, #18 — closed), C2 (Market Pulse, #19 — closed)
**Branch:** issue-20-trade-deliberation

---

## Summary

Significant market events trigger multi-agent deliberation on a qhorus channel. Strategy agents (from C1) debate trade responses using the blocks conversation protocol. Epistemic common ground tracks agreement. Convergence detection determines when to act. Agreed decisions become qhorus commitments (accountability) and TradeDecisions (execution).

**Platform showcase:** blocks conversation protocol (ConversationProjection, ConversationFold, CommonGroundAnalyser, EpistemicRules), blocks convergence detection (ConvergenceAnalyser, ConvergencePolicies, ConvergenceTermination), ChoreographedDriver, Debate orchestration pattern, ChannelAgentDispatcher with LLM-mediated handlers, qhorus Commitment lifecycle.

---

## §1 Architecture Overview

C3 composes four platform capabilities — conversation protocol, convergence detection, choreographed execution, and channel-based agent dispatch — into a deliberation pipeline:

```
Market Event (from C2 FsiMarketEventDetector)
  → FsiDeliberationTrigger (significance filter + concurrency guard)
    → Create qhorus channel: fsi-deliberation-{instrument}-{timestamp}
    → FsiArenaRouting selects relevant strategy agents (reuses C1 routing logic)
    → FsiDeliberationStateObserver(FsiConversationProjection, channel)
      → implements MessageObserver (projection state updates)
      → implements EventSource (wakes ChoreographedDriver)
      → holds ConversationState for ConvergenceTermination
    → Patterns.debate()
        .debaters(selected agents as ChannelAgent refs)
        .convergence(compositeTermination)  // ConvergenceTermination + MaxIterationsTermination
        .backend(choreographed(invoker, serialize(), stateObserver))
    → On CONSENSUS: Commitment + TradeDecision → execute via C1 pipeline
    → On DEADLOCK: FLAG_HUMAN → escalate to human trader
    → On DIMINISHING_RETURNS: Commitment (reduced confidence) + TradeDecision
    → Persist DeliberationRecord snapshot
    → Close channel
```

### New Types

| Type | Package | Role |
|------|---------|------|
| `FsiConversationProjection` | `app.deliberation` | Extends `ConversationProjection`, sentinel `##FSI##` |
| `FsiDeliberationStateObserver` | `app.deliberation` | Implements `MessageObserver` + `EventSource`; applies `FsiConversationProjection`, holds `ConversationState`, wakes `ChoreographedDriver` on new messages |
| `FsiDeliberationTrigger` | `app.deliberation` | Significance filter on market events, concurrency guard |
| `FsiDeliberationOrchestrator` | `app.deliberation` | Wires debate pattern, observer, termination, dispatches execution |
| `FsiDeliberationOutcomeHandler` | `app.deliberation` | Convergence → Commitment + TradeDecision |
| `DeliberationRecord` | `app.deliberation` | JPA entity — snapshot + concurrency guard (status column) |
| `FsiDebateHandler` | `app.deliberation` | `ChannelAgentHandler` for debate: prompt context, entry type parsing, metadata |
| `FsiChannelAgentDispatcher` | `app.deliberation` | Extends `ChannelAgentDispatcher`; overrides `onError()` to post failure notifications to channel |
| `TradeProvenance` | `app.deliberation` | Record: deliberation provenance fields for TradeDecision |
| `CorrelationCheckHandler` | `app.deliberation.handler` | LLM-interpreted correlation sub-task |
| `VolumeAnalysisHandler` | `app.deliberation.handler` | LLM-interpreted volume sub-task |
| `NewsCheckHandler` | `app.deliberation.handler` | LLM-powered news sub-task |
| `DeliberationResource` | `app.deliberation` | REST endpoints |

### Platform Types Used (not reimplemented)

| Type | Source | How used |
|------|--------|----------|
| `ConversationProjection` | blocks.conversation | Extended by FsiConversationProjection |
| `ConversationFold` | blocks.conversation | Pure fold operations (createPoint, respondToPoint, etc.) |
| `ConversationState` | blocks.conversation | Projected state record |
| `CommonGroundAnalyser` | blocks.conversation | Classify facts as established/pending/disputed |
| `EpistemicRules` | blocks.conversation | Composable epistemic rules factory |
| `ConvergenceAnalyser` | blocks.conversation | Detect consensus/deadlock/diminishing returns |
| `ConvergencePolicies` | blocks.conversation | Composable convergence policy factory |
| `ConvergenceTermination` | blocks.agentic.termination | Bridges CommonGroundAnalyser + ConvergenceAnalyser → TerminationDecision |
| `MaxIterationsTermination` | blocks.agentic.termination | Hard round cap guard |
| `Patterns.debate()` | blocks.agentic.pattern | Debate pattern builder |
| `ChoreographedDriver` | blocks.agentic.model | Event-driven execution backend |
| `ChannelAgentDispatcher` | blocks.channel | Routes sub-task requests to handlers |
| `ChannelAgentHandler` | blocks.channel | Interface for sub-task handler implementations |
| `ContextTracker` | blocks.channel | LLM context usage monitoring |
| `ChannelSummariser` | blocks.channel.summary | Channel compression via SummaryUpdateHook |
| `AgentRef.channel()` | blocks.agentic | ChannelAgent ref wrapping channelId + handler |
| `TerminationCondition` | blocks.agentic.termination | Termination evaluation interface |
| `Commitment` | qhorus.api.message | 7-state commitment lifecycle |
| `CommitmentStore` | qhorus.api.store | Commitment persistence |

---

## §2 FsiConversationProjection

Extends `ConversationProjection`. Reuses the `##FSI##` sentinel already established by C2's `FsiChannelEventAdapter`.

### Configuration

| Method | Value | Rationale |
|--------|-------|-----------|
| `sentinel()` | `"##FSI##"` | Matches C2's existing sentinel for consistent channel metadata |
| `isPointInitiator(entryType)` | `RAISE`, `PROPOSE` | RAISE = trading thesis, PROPOSE = specific trade action |
| `statusAfter(entryType)` | See table below | Trading-specific status transitions |

### Entry Types and Status Transitions

**Point initiators** (create new ConversationPoints):

| Entry type | Meaning |
|-----------|---------|
| `RAISE` | Agent raises a trading thesis ("AAPL showing momentum exhaustion") |
| `PROPOSE` | Agent proposes a specific trade action ("BUY 200 MSFT at market") |

**Point responses** (respond to existing points):

| Entry type | Status after | Meaning |
|-----------|-------------|---------|
| `AGREE` | `AGREED` | Agent endorses the point |
| `COUNTER` | `ACTIVE` | Agent offers an alternative — keeps debate going |
| `DISPUTE` | `DISPUTED` | Agent disagrees with evidence |
| `QUALIFY` | `ACTIVE` | Agent agrees with caveats |
| `RESOLVE` | `RESOLVED` | Agent synthesises competing views |

### Epistemic Rules

```
var base = EpistemicRules.explicitAcknowledgement(2)
    .or(EpistemicRules.tacitAcceptance(3));

EpistemicRule failureSafe = (point, context) ->
    context.failedBy().isEmpty()
        ? EpistemicStatus.ESTABLISHED   // pass-through: don't block base rule
        : EpistemicStatus.DISPUTED;     // veto: failures exist

var rule = base.and(failureSafe);
```

A point is ESTABLISHED when either:
- 2 agents explicitly acknowledge it (AGREE, QUALIFY, or RESOLVE responses — `MessageType.RESPONSE`/`DONE` → `acknowledgedBy`), OR
- At least 1 agent responded and no one disputes or fails for 3 consecutive rounds

AND no agent has sent a `MessageType.FAILURE` response on that point.

The `failureSafe` guard ensures any `MessageType.FAILURE` response vetoes establishment regardless of how many agents acknowledged — `EpistemicRules.explicitAcknowledgement()` checks `disputedBy()` but not `failedBy()`, so without the guard, `.or()` would return ESTABLISHED even with failures present.

**Currently defensive:** No code path in this spec produces `MessageType.FAILURE` messages — `FsiDebateHandler.buildResponse()` only emits COMMAND/PROPOSE/RESPONSE/DONE/DECLINE, and `ChannelAgentDispatcher.onError()` only logs. With `failedBy()` always empty, the guard reduces to identity. However, `FsiChannelAgentDispatcher` (§6) overrides `onError()` to post failure notifications to the channel. If a future extension maps these notifications to `MessageType.FAILURE` on debate points, the guard activates without spec changes. The guard is retained as a forward-compatible safety net — removing it would require re-adding it when FAILURE production paths are introduced.

The `failureSafe` rule returns ESTABLISHED (not PENDING) for the no-failure case because `.and()` requires BOTH sub-rules to return non-PENDING for ESTABLISHED to propagate. A PENDING return would block convergence entirely — the `.and()` semantics are: DISPUTED if either is DISPUTED, ESTABLISHED only if both are non-PENDING, PENDING otherwise.

### MessageType Mapping

Each entry type maps to a qhorus `MessageType` that drives `CommonGroundAnalyser`'s participation tracking. This mapping is the contract between the conversation protocol and the convergence machinery:

| Entry type | MessageType | CommonGroundAnalyser effect | Rationale |
|-----------|-------------|---------------------------|-----------|
| `RAISE` | `COMMAND` | Not in `respondedBy` | Point initiator — raising a topic is not responding |
| `PROPOSE` | `PROPOSE` | In `respondedBy` (not excluded) | Point initiator — purpose-built type for proposals |
| `AGREE` | `RESPONSE` | `acknowledgedBy` ✓ | Endorses the point |
| `COUNTER` | `COMMAND` | Not in `respondedBy` | Alternative offered — doesn't endorse or dispute the original |
| `DISPUTE` | `DECLINE` | `disputedBy` ✓ | Explicit disagreement |
| `QUALIFY` | `RESPONSE` | `acknowledgedBy` ✓ | Agrees with caveats — still an endorsement |
| `RESOLVE` | `DONE` | `acknowledgedBy` ✓, `completedBy` ✓ | Synthesises competing views — resolution |

**COUNTER → COMMAND rationale:** A counter-proposal is neither agreement nor dispute — it's a redirection. With COMMAND, the counter-proposer doesn't inflate `acknowledgedBy` (which would misrepresent endorsement) and doesn't trigger `disputedBy` (which would be too strong). The counter-proposer creates a competing PROPOSE point for their alternative; the original point's convergence depends on actual agreements.

### Convergence Policy

```
ConvergencePolicies.composite(
    ConvergencePolicies.structural(0.8, 3),
    ConvergencePolicies.commonGroundRatio(0.7, 0.4)
)
```

| Policy | Threshold | Detects |
|--------|-----------|---------|
| `structural` | similarity > 0.8 | DEADLOCK — agents repeating themselves |
| `structural` | stale > 3 rounds | DIMINISHING_RETURNS — no new points |
| `commonGroundRatio` | established ≥ 70% | CONSENSUS — sufficient agreement |
| `commonGroundRatio` | disputed ≥ 40% | DEADLOCK — irreconcilable disagreement |

The `composite()` policy picks the highest-confidence signal, with severity as tiebreaker when confidences are equal (severity order: DEADLOCK > DIMINISHING_RETURNS > CONVERGING > PROGRESSING > CONSENSUS). This means a CONSENSUS at confidence 0.85 prevails over a DEADLOCK at confidence 0.82. For the deliberation use case, `ConvergenceTermination`'s `confidenceThreshold` (default 0.6) gates low-confidence signals before they reach the composite tiebreaker — signals that pass the threshold are more reliably differentiated by confidence than by severity.

**CONVERGING suppression window:** When `establishedRatio ∈ [0.7, 0.9)` and `roundsSinceStatusChange ≤ 2`, the `structural` policy returns CONVERGING at confidence = establishedRatio, while `commonGroundRatio` returns CONSENSUS at the same confidence. The composite tiebreaker picks CONVERGING (severity 3) over CONSENSUS (severity 1). Since CONVERGING is not in `ConvergenceTermination.terminateOn`, the debate continues — this is beneficial during active debate, as it pushes for higher consensus (≥90% established ratio). If `MaxIterationsTermination` fires during this window, the outcome handler receives CONVERGING instead of CONSENSUS — see §5 "On CONVERGING" for handling.

---

## §3 Deliberation Trigger & Concurrency

### FsiDeliberationTrigger

Subscribes to C2's `EventStreamBus<TrendSummary>` (L2) and `EventStreamBus<RegimeAssessment>` (L3) via `FsiMarketEventDetector`'s output consumers. Filters for significance before creating a deliberation.

**Significance thresholds:**

| Event type | Condition | Configurable via |
|-----------|-----------|-----------------|
| `RegimeChanged` | Always significant (any regime transition) | — |
| `TrendReversalDetected` | `momentum magnitude > 0.5` | `fsi.deliberation.trend-reversal-threshold` |

Both thresholds are `@ConfigProperty` — tunable in `application.properties` without code changes. The trend reversal threshold defaults to 0.5 but can be lowered for testing or raised to reduce noise.

### Concurrency Guard

One active deliberation per instrument. Enforced via database-level guard on `DeliberationRecord` — the same pattern established by C1's `ArenaRunEntity`.

**Database guard:** A partial unique index on `deliberation_record`:

```sql
CREATE UNIQUE INDEX idx_deliberation_record_inflight
ON deliberation_record(instrument) WHERE status = 'IN_PROGRESS'
```

This allows multiple `COMPLETED`/`FAILED`/`CANCELLED` rows per instrument (run history preserved) while preventing concurrent `IN_PROGRESS` deliberations.

- **On trigger:** INSERT `DeliberationRecord` with `status = IN_PROGRESS`, `ended_at = NULL`. If the INSERT violates the partial unique index, another deliberation is already in-flight → return without creating a deliberation (HTTP 409 for manual trigger).
- **On completion/failure/cancellation:** UPDATE `status` to `COMPLETED`/`FAILED`/`CANCELLED`, set `ended_at`.
- **Crash recovery:** `FsiDeliberationOrchestrator` (`@Startup` CDI observer) runs a recovery sweep on boot: `UPDATE deliberation_record SET status = 'FAILED', ended_at = NOW(), summary = 'server_restart_recovery' WHERE status = 'IN_PROGRESS'`. This prevents orphaned IN_PROGRESS rows from permanently blocking an instrument after a crash.

### Manual Trigger

`POST /api/deliberations/trigger` accepts:

```json
{
  "instrument": "AAPL",
  "eventType": "MANUAL",
  "context": "Testing deliberation flow"
}
```

Bypasses the significance filter but respects the concurrency guard. Creates a deliberation with `trigger_type = MANUAL` in the DeliberationRecord.

### Timeout Policy

Hard cap of 10 rounds via `MaxIterationsTermination` composed with `ConvergenceTermination` using a lambda composite (convergence evaluated first, max-iterations as guard):

```java
TerminationCondition<DeliberationContext> termination = ctx -> {
    var decision = convergenceTermination.evaluate(ctx);
    return decision instanceof TerminationDecision.Continue
        ? maxIterationsTermination.evaluate(ctx) : decision;
};
```

If 10 rounds pass without convergence, the debate terminates as COMPLETE (max iterations reached) with whatever common ground was established. Convergence is evaluated first, so if it detects DEADLOCK at round 8, that Escalate decision takes priority over the round cap's Complete.

The max rounds value is `@ConfigProperty("fsi.deliberation.max-rounds")` defaulting to 10.

The `recentWindow` parameter for `ConvergenceAnalyser.analyse()` controls how many recent thread entries are used for similarity computation (Jaccard similarity on tokenized content) and message length trend. Default: `@ConfigProperty("fsi.deliberation.recent-window")` = 5. With 3–5 agents, 5 entries is approximately 1–2 rounds of debate — enough to detect repetition without false positives from early divergent discussion. The `recentWindow` interacts with the `structural(0.8, 3)` stale-rounds threshold: similarity is computed over the recent window, while stale-rounds counts rounds since the last new point. A small window can amplify similarity scores in early rounds — the default of 5 balances sensitivity with stability.

`CompositeTermination` in `blocks.conversation.orchestration` provides ordered composition for `TerminationCondition<ConversationState>`, but since the debate pattern uses `DeliberationContext` (not `ConversationState`) as its generic type, the lambda composition above provides the same first-match semantics with the correct type parameter.

---

## §4 Deliberation Orchestrator & Debate Wiring

### FsiDeliberationOrchestrator

CDI `@ApplicationScoped` bean. Entry point: `startDeliberation(MarketEvent event)` called by `FsiDeliberationTrigger`.

**Orchestration sequence:**

**Step 1 — Create channel:**

Create a qhorus channel via `MessageService.createChannel()` with name `fsi-deliberation-{instrument}-{timestamp}` and metadata:
- `instrument`: event instrument symbol
- `eventType`: REGIME_CHANGED / TREND_REVERSAL / MANUAL
- `triggerSource`: event description

**Step 2 — Select agents:**

Reuse C1's `FsiArenaRouting` routing strategy to select which strategy agents are relevant to this event. The orchestrator creates an `ArenaContext` from the deliberation trigger's `MarketEvent` (same market signal data), then calls `FsiArenaRouting.route()` with a `RoutingContext<ArenaContext>` wrapping it. The routing strategy evaluates signal characteristics (instrument, event type, magnitude, time of day) via signal assembler, LLM, and CBR signals, and returns the subset of 7 registered strategy agents scoring above threshold.

If `FsiArenaRouting.route()` returns `RoutingDecision.Unresolvable` (all candidates scored below the 0.3 threshold — zero agents selected), the orchestrator updates the `DeliberationRecord` with `status = FAILED`, `summary = "no eligible agents for {instrument}: {unresolvable.reason()}"`, sets `ended_at`, and returns without creating a channel or wiring a debate. For the manual trigger endpoint, this produces a 200 response with the FAILED record (the 202 async path is not entered).

Each selected agent is wrapped as `AgentRef.channel(channelId, debateHandler)` where `debateHandler` is the `FsiDebateHandler` (§4.3).

**Step 3 — Wire state observer:**

```java
var stateObserver = new FsiDeliberationStateObserver(
    new FsiConversationProjection(), channelName);
```

`FsiDeliberationStateObserver` implements both `MessageObserver` (qhorus) and `EventSource` (blocks). It is registered as a `MessageObserver` on the channel — on each message, it applies `FsiConversationProjection` to update its held `ConversationState`, then emits a `DriverEvent.signal("message")` to wake the `ChoreographedDriver`. It exposes `currentState()` for the termination's state extractor.

**Step 4 — Build termination:**

```java
// Epistemic rule — must match §2 composition including failureSafe guard
var base = EpistemicRules.explicitAcknowledgement(2)
    .or(EpistemicRules.tacitAcceptance(3));

EpistemicRule failureSafe = (point, context) ->
    context.failedBy().isEmpty()
        ? EpistemicStatus.ESTABLISHED   // pass-through: don't block base rule
        : EpistemicStatus.DISPUTED;     // veto: failures exist

var epistemicRule = base.and(failureSafe);

var policy = ConvergencePolicies.composite(
    ConvergencePolicies.structural(0.8, 3),
    ConvergencePolicies.commonGroundRatio(0.7, 0.4));

var convergenceTermination = new ConvergenceTermination<DeliberationContext>(
    ctx -> stateObserver.currentState(),   // state extractor — closes over observer
    epistemicRule, policy,
    recentWindow,                           // @ConfigProperty default 5
    confidenceThreshold,                    // @ConfigProperty default 0.6
    Set.of(CONSENSUS, DEADLOCK, DIMINISHING_RETURNS));

var maxIterations = new MaxIterationsTermination<DeliberationContext>(maxRounds);

// Lambda composition: convergence first (so DEADLOCK → Escalate takes priority),
// max-iterations as guard
TerminationCondition<DeliberationContext> termination = ctx -> {
    var decision = convergenceTermination.evaluate(ctx);
    return decision instanceof TerminationDecision.Continue
        ? maxIterations.evaluate(ctx) : decision;
};
```

**Step 5 — Execute debate:**

```java
var result = Patterns.<DeliberationContext>debate()
    .debaters(channelAgents)
    .convergence(termination)
    .backend(ExecutionBackend.choreographed(invoker, EventConcurrencyPolicy.serialize(), stateObserver))
    .execute(deliberationContext)
    .await().atMost(Duration.ofMinutes(wallClockTimeout));
```

`wallClockTimeout` is `@ConfigProperty("fsi.deliberation.wall-clock-timeout-minutes")` defaulting to 15. This is a safety net independent of the round cap — if `EventSource` fails to deliver events (MessageObserver registration failure, network partition), `MaxIterationsTermination` never fires because the iteration count doesn't increment. The wall-clock timeout prevents permanent thread leaks.

The `DeliberationContext` carries the market event, instrument, selected agents, and a reference to the `ContextTracker`.

**Step 6 — Handle result:**

Re-analyse convergence state from the final `ConversationState`, then dispatch to `FsiDeliberationOutcomeHandler`:

```java
var finalState = stateObserver.currentState();
var finalCg = CommonGroundAnalyser.analyse(finalState, epistemicRule);
var finalSignal = ConvergenceAnalyser.analyse(finalState, finalCg, policy, recentWindow);
```

This re-analysis is necessary because `ExecutionResult.Completed(Object result)` carries an opaque result — `ConvergenceTermination` passes `signal.reason()` (a String), and `MaxIterationsTermination` passes `"Max iterations reached"`. Neither carries the structured `ConvergenceSignal`. Re-analysis is idempotent (same state + rules = same result) and cheap (no LLM calls).

Dispatch based on `ExecutionResult` variant:
- `Completed` → use `finalSignal` to determine outcome path (CONSENSUS/CONVERGING/DIMINISHING_RETURNS/PROGRESSING)
- `Escalated` → DEADLOCK path (FLAG_HUMAN) — `ConvergenceTermination` maps DEADLOCK to `Escalate`
- `Failed` → log error, UPDATE record with `status = FAILED`, `summary = cause`
- `Cancelled` → log, UPDATE record with `status = CANCELLED`

When `MaxIterationsTermination` fires (result = `Completed("Max iterations reached")`), the re-analysed `finalSignal` reveals the actual convergence state. This may be PROGRESSING (debate was active but hit the cap) — NOT DIMINISHING_RETURNS. A productively progressing debate that hit the round cap routes to human escalation (same as DEADLOCK), not to trade execution.

**Step 7 — Cleanup:**

1. UPDATE `DeliberationRecord` — set `status`, `ended_at`, convergence metrics (§7)
2. Close channel via `MessageService`

### §4.3 FsiDebateHandler

`FsiDebateHandler` implements `ChannelAgentHandler`. One shared instance for all debate participants — agent identity comes from the `ChannelAgentRequest.senderId()`.

**`handles(ChannelAgentRequest)`:** Returns true for requests where `request.taskType()` is `DEBATE_TURN` (the debate pattern dispatches each turn as a `DEBATE_TURN` request).

**`prepareTask(ChannelAgentRequest)`:** Builds the `AgentTask` prompt with:
- **System context:** Agent's strategy type and disposition (from `AgentDescriptor.dispositionProfile()`)
- **Market event:** Instrument, event type, magnitude, trigger source
- **Conversation state:** Summary of current points (established, pending, disputed) from `observer.currentState()` — not the full thread, but the common ground snapshot
- **Instructions:** Structured output format: the agent must respond with `entryType` (one of RAISE, PROPOSE, AGREE, COUNTER, DISPUTE, QUALIFY, RESOLVE) and `body` (its argument). First-round agents default to RAISE or PROPOSE; subsequent rounds default to response types.
- **Point context:** If responding to an existing point, the point's thread summary is included

**`buildResponse(channelId, senderId, llmOutput, trigger)`:** Parses the LLM's structured response and builds a `MessageDispatch` with:
- `content`: `##FSI##` sentinel + metadata block (`entryType`, `role` from `FsiActorIdentity.actorRole()`, `round` from `trigger.round()`, `pointId` from `trigger.correlationId()`, `priority` defaulting to `MEDIUM`, `scope` from instrument) + body text
- `correlationId`: For point initiators (RAISE/PROPOSE), a new UUID. For responses, the `pointId` of the target point.
- `messageType`: per §2 MessageType Mapping — `RESPONSE` for endorsements (AGREE, QUALIFY), `DONE` for synthesis (RESOLVE), `DECLINE` for disagreement (DISPUTE), `COMMAND` for topic-raising and alternatives (RAISE, COUNTER), `PROPOSE` for trade proposals (PROPOSE)

Entry type determination follows a keyword+structure approach: the LLM is instructed to emit the entry type explicitly in its structured output. If the LLM's stated entry type doesn't match its content (e.g., says AGREE but argues against), the handler trusts the explicit label — the conversation protocol handles inconsistency through subsequent rounds.

### ContextTracker Integration

The orchestrator creates a `ContextTracker` per deliberation. Each agent response calls `tracker.addContribution(responseChars)`. The tracker is configured with:
- `windowSizeChars`: 100_000 (approximate LLM context window)
- `thresholdPercent`: 80.0

When `snapshot().thresholdBreached()` returns true, the `ChannelSummariser` (blocks' existing `SummaryUpdateHook` SPI) compresses the channel. This is automatic via qhorus's summary lifecycle — no custom compression code needed.

The `context-gauge` UI panel (C5) reads from a lightweight SSE endpoint backed by `tracker.snapshot()`.

---

## §5 Outcome Handler & Commitment Flow

### FsiDeliberationOutcomeHandler

Processes the debate result after the orchestrator completes.

### On CONSENSUS

1. **Extract agreed action:** From `finalCg` (the re-analysed `CommonGroundState` from Step 6), find established PROPOSE-type facts: iterate `finalCg.establishedFacts()`, cross-reference each `GroundedFact.pointId()` against `ConversationState.points()` to find points whose first `ThreadEntry.entryType()` is `"PROPOSE"`. If multiple established PROPOSE points exist, take the one with the highest `PointClassification.priority()`; ties broken by most recent `GroundedFact.round()`. This is the agreed trade action.

   Point status (`ConversationPoint.status()`) is NOT used — it tracks the last response type (e.g., ACTIVE after a QUALIFY), not epistemic agreement. Epistemic status (ESTABLISHED/PENDING/DISPUTED from `CommonGroundAnalyser`) is authoritative.

2. **Create Commitment:**
   ```
   Commitment.builder()
       .channelId(channelId)
       .correlationId(agreedPointId)
       .messageType(MessageType.COMMAND)
       .requester(orchestratorSenderId)
       .obligor(proposingAgentSenderId)
       .state(CommitmentState.OPEN)
       .expiresAt(Instant.now().plus(commitmentExpiryDuration))
       .build()
   ```
   `commitmentExpiryDuration` is `@ConfigProperty("fsi.deliberation.commitment-expiry-minutes")` defaulting to 10. Persisted via `CommitmentStore.save()`.

3. **Transition to ACKNOWLEDGED:** Before starting execution, transition the commitment to `CommitmentState.ACKNOWLEDGED` via `CommitmentStore`. This signals "execution in progress" and prevents the commitment from expiring during order processing. The `acknowledgedAt` timestamp is set.

4. **Derive TradeDecision:** Extract instrument, action (BUY/SELL/HOLD), quantity, and strategy type from the agreed point's content. Create a `TradeDecision` with a `TradeProvenance` companion:

   ```java
   // TradeProvenance — new record in api module
   public record TradeProvenance(
       UUID deliberationChannelId,
       UUID commitmentId,
       ConvergenceState convergenceState,
       double confidence) {}
   ```

   The existing `TradeDecision` record gains a nullable `TradeProvenance provenance` field (breaking change — C1 callers pass `null`). This keeps provenance opt-in: direct strategy evaluations (C1 path) produce `TradeDecision` with `provenance = null`; deliberated trades carry full provenance.

   For CONSENSUS: `convergenceState = CONSENSUS`, `confidence = finalSignal.confidence()`. For CONVERGING that passes the execution gate (§5 "On CONVERGING"): `convergenceState = CONVERGING`, `confidence = finalSignal.confidence()`.

5. **Execute:** Route the `TradeDecision` through `OrderService.createFromDecision()` → `OrderService.fill()` → `PositionService.applyFill()`. This is the same order creation and fill pipeline used by `FsiExecutionAgent`, but called directly with the pre-built `TradeDecision` rather than through `FsiExecutionAgent.execute(ArenaContext)` (which requires a full `ArenaContext` with arena-specific voting results). Ledger entries (`StrategyEvaluationLedgerEntry` → `OrderExecutionLedgerEntry`) and `PnlAttestationService` are called directly.

6. **Close the loop:** On fill → transition commitment to FULFILLED. On execution failure → FAILED. The commitment's terminal state feeds into `PnlAttestationService` for trust scoring — the Bayesian Beta update for the proposing agent reflects whether the deliberated trade succeeded.

### On DEADLOCK

1. Post `FLAG_HUMAN` entry to the channel with a summary of the disputed points.
2. Create `Commitment` with `obligor = FsiActorIdentity.HUMAN_TRADER` (`"human:trader@v1"` — follows the `type:name@version` convention), `state = OPEN`.
3. No TradeDecision — the human decides. The commitment remains OPEN until a human trader acts (future integration with C4's work item approval gates).

### On PROGRESSING

When `MaxIterationsTermination` fires during an actively progressing debate, the re-analysed `finalSignal` (§4 Step 6) shows `PROGRESSING`. This means agents were still productively contributing when the round cap was reached — NOT that they reached agreement or stalled.

Route to the same handler as DEADLOCK: post `FLAG_HUMAN` with a summary noting "debate was active at round cap — human review recommended", create a commitment with `obligor = FsiActorIdentity.HUMAN_TRADER`, no `TradeDecision`. A productive debate that merely ran out of rounds should not trigger trade execution.

### On CONVERGING

CONVERGING appears when `MaxIterationsTermination` fires during the composite tiebreaker's CONVERGING suppression window (§2): the `structural` policy returned CONVERGING at the same confidence as `commonGroundRatio`'s CONSENSUS, and severity tiebreaker picked CONVERGING. The debate was making progress (≥50% established ratio, recent status changes) but CONVERGING is not in `terminateOn`, so `ConvergenceTermination` returned Continue and `MaxIterationsTermination` fired instead.

The outcome depends on whether the debate had substantive consensus suppressed by the tiebreaker:

```java
double establishedRatio = (double) finalCg.establishedFacts().size()
    / (finalCg.establishedFacts().size() + finalCg.pendingClaims().size() + finalCg.disputedPoints().size());

if (establishedRatio >= commonGroundConsensusThreshold) {
    // CONSENSUS was suppressed by composite tiebreaker — treat as DIMINISHING_RETURNS
    // (execution gate applies: sufficient established ratio → execute with reduced confidence)
} else {
    // Genuinely converging but below consensus threshold — escalate to human
}
```

`commonGroundConsensusThreshold` is the same 0.7 threshold used in `ConvergencePolicies.commonGroundRatio()`. When the established ratio meets this threshold, the `commonGroundRatio` policy would have returned CONSENSUS — the composite tiebreaker suppressed it. The DIMINISHING_RETURNS path is appropriate because the execution gate (§5 "On DIMINISHING_RETURNS") already checks established ratio before executing. The `confidence` on the resulting TradeDecision is set to the convergence signal's confidence value.

When the established ratio is below 0.7, the debate was genuinely still converging toward consensus when it ran out of rounds. Route to human escalation (same as PROGRESSING).

### On DIMINISHING_RETURNS

**Execution gate:** DIMINISHING_RETURNS means "no new points for N rounds" — agents stopped contributing. This is NOT agreement. The common ground at that point could have low established ratio. Before executing a trade, check the established ratio from `finalCg`:

```java
double establishedRatio = (double) finalCg.establishedFacts().size()
    / (finalCg.establishedFacts().size() + finalCg.pendingClaims().size() + finalCg.disputedPoints().size());

if (establishedRatio < diminishingReturnsExecutionThreshold) {
    // Route to human escalation (same as DEADLOCK)
}
```

`diminishingReturnsExecutionThreshold` is `@ConfigProperty("fsi.deliberation.diminishing-returns-min-established")` defaulting to 0.5. If established ratio is below 50%, the debate stalled without sufficient agreement — route to human escalation rather than executing a trade with weak consensus.

If the gate passes, proceed as CONSENSUS but:
- The `confidence` field on the TradeDecision is set to the convergence signal's confidence value (< 1.0, typically 0.5–0.8).
- The commitment is created identically to CONSENSUS.
- Reduced confidence propagates to trust scoring — partial-confidence executions contribute proportionally less to Bayesian Beta updates via the existing `FsiQualityDimensionScorer` weighting.

SETTLED: DIMINISHING_RETURNS with sufficient established ratio executes a trade with reduced confidence. DIMINISHING_RETURNS with insufficient established ratio escalates to human. This is an explicit design decision — not an implicit consequence of treating DIMINISHING_RETURNS as CONSENSUS-minus-confidence.

### Commitment Lifecycle Events

Commitment state changes are published to WebSocket topic `deliberation/{channelId}` via `FsiMarketPushService`'s `PushBroadcaster`. C3 adds a `subscribeDeliberation()` method to `FsiMarketPushService` that wires deliberation events to the broadcaster (analogous to the existing `subscribe()` method for L0-L4 market data buses). The `commitment-viz` and `blocks-timeline` UI panels (wired in C5) consume these events.

---

## §6 Sub-Task Handlers

During deliberation, agents can request sub-tasks via `SUB_TASK_REQUEST` entries on the channel. `ChannelAgentDispatcher` routes each request to the first matching `ChannelAgentHandler`.

### CorrelationCheckHandler (LLM-interpreted computation)

| Aspect | Detail |
|--------|--------|
| **Matches** | `taskType = "CORRELATION_CHECK"` |
| **Input** | Two instrument symbols from the request metadata |
| **Implementation** | `prepareTask()` computes Pearson correlation on recent 1-min bars from C2's `FsiObservationCache`, then embeds the raw results (coefficient, sample size, p-value) into the `AgentTask.assembledInput` prompt for LLM interpretation |
| **Output** | `SUB_TASK_FINDING` with correlation coefficient, sample size, significance (p-value), and natural language trading interpretation |
| **LLM** | Yes — interprets pre-computed statistics in debate context |

### VolumeAnalysisHandler (LLM-interpreted computation)

| Aspect | Detail |
|--------|--------|
| **Matches** | `taskType = "VOLUME_ANALYSIS"` |
| **Input** | Instrument symbol from the request metadata |
| **Implementation** | `prepareTask()` computes volume vs 20-bar moving average and z-score from cached OHLCV data, then embeds the raw results into the `AgentTask.assembledInput` prompt for LLM interpretation |
| **Output** | `SUB_TASK_FINDING` with current vs average volume, z-score, profile classification (NORMAL / HIGH / SPIKE / DRY), and natural language trading interpretation |
| **LLM** | Yes — interprets pre-computed volume analysis in debate context |

### NewsCheckHandler (LLM-powered)

| Aspect | Detail |
|--------|--------|
| **Matches** | `taskType = "NEWS_CHECK"` |
| **Input** | Instrument symbol + optional keywords from the request metadata |
| **Implementation** | `prepareTask()` assembles instrument context + recent price action from `FsiObservationCache` into the prompt. In the synthetic data environment, the LLM generates a plausible news assessment based on the price pattern. |
| **Output** | `SUB_TASK_FINDING` with sentiment assessment (BULLISH / BEARISH / NEUTRAL), key themes, relevance score (0.0–1.0) |
| **LLM** | Yes — via `AgentTask` → agent provider → parse response |

All three handlers implement the same `ChannelAgentHandler` interface. `ChannelAgentDispatcher.dispatch()` unconditionally calls the agent provider for every handler — there is no way to skip the LLM call. Computational handlers compute their raw data in `prepareTask()` and embed it in the prompt; the LLM adds natural language interpretation that integrates coherently with the debate. This is a design choice: LLM-interpreted findings produce richer debate contributions than raw numbers.

### FsiChannelAgentDispatcher

Extends `ChannelAgentDispatcher`. Overrides `onError()` to post sentinel-wrapped `SUB_TASK_ERROR` messages to the channel — the platform's `onError()` only logs a warning, making sub-task failures invisible.

```java
@Override
protected void onError(ChannelAgentRequest request, String reason) {
    super.onError(request, reason);
    var content = ChannelMessageMeta.encode("##FSI##",
        Map.of("entryType", "SUB_TASK_ERROR",
               "subTaskId", request.correlationId(),
               "taskType", request.taskType(),
               "role", "ORCHESTRATOR"),
        reason);
    var notification = MessageDispatch.builder()
        .channelId(request.channelId())
        .senderId(senderId())
        .correlationId(request.correlationId())
        .messageType(MessageType.EVENT)
        .content(content)
        .build();
    messageSink().accept(notification);
}
```

The sentinel-wrapped message with `entryType=SUB_TASK_ERROR` feeds the platform's built-in error pathway: `ConversationProjection.doApply()` routes to `handleSubTaskError()`, which calls `ConversationFold.errorSubTask()`, creating a `SubTaskFinding` with `TaskStatus.FAULTED` in `ConversationState.subTaskFindings()`. This provides:

- **State tracking:** Errors are recorded in `ConversationState.subTaskFindings()` — queryable and included in `conversation_state_snapshot` for post-hoc audit.
- **Epistemic isolation:** `subTaskFindings` is a separate map in `ConversationState`, not part of `points()` — `CommonGroundAnalyser.buildContext()` does not iterate it, so errors do not affect `acknowledgedBy`, `disputedBy`, or `failedBy`. Epistemic classification of debate points is unaffected.
- **Channel visibility:** The raw sentinel-wrapped message appears in the qhorus channel history, visible in the `channel-activity` UI panel.

The orchestrator wires `FsiChannelAgentDispatcher` in place of the base `ChannelAgentDispatcher` when creating the debate's sub-task dispatch pipeline.

---

## §7 Persistence & REST Endpoints

### DeliberationRecord — JPA Entity

**Table:** `deliberation_record`
**Flyway migration:** Next available V-number

| Column | Type | Constraint | Description |
|--------|------|-----------|-------------|
| `id` | `UUID` | PK | |
| `channel_id` | `UUID` | NOT NULL | qhorus channel reference |
| `instrument` | `VARCHAR(20)` | NOT NULL | e.g. "AAPL" |
| `status` | `VARCHAR(16)` | NOT NULL | IN_PROGRESS / COMPLETED / FAILED / CANCELLED |
| `trigger_type` | `VARCHAR(30)` | NOT NULL | REGIME_CHANGED / TREND_REVERSAL / MANUAL |
| `convergence_state` | `VARCHAR(30)` | NULLABLE | CONSENSUS / CONVERGING / DEADLOCK / DIMINISHING_RETURNS / PROGRESSING (null if status ≠ COMPLETED) |
| `confidence` | `DOUBLE` | NULLABLE | From ConvergenceSignal, 0.0–1.0 (null if status ≠ COMPLETED) |
| `established_count` | `INT` | NULLABLE | Established common ground facts (null if status ≠ COMPLETED) |
| `disputed_count` | `INT` | NULLABLE | Disputed points (null if status ≠ COMPLETED) |
| `pending_count` | `INT` | NULLABLE | Pending claims (null if status ≠ COMPLETED) |
| `rounds` | `INT` | NOT NULL | Total debate rounds completed (0 if failed before first round) |
| `participants` | `VARCHAR(500)` | NOT NULL | Comma-separated agent sender IDs |
| `commitment_id` | `UUID` | NULLABLE | FK to qhorus commitment (null if debate failed) |
| `trade_decision_id` | `UUID` | NULLABLE | FK to trade decision (null if DEADLOCK or failed) |
| `started_at` | `TIMESTAMP` | NOT NULL | Deliberation start |
| `ended_at` | `TIMESTAMP` | NULLABLE | Deliberation end (null while IN_PROGRESS) |
| `summary` | `TEXT` | NULLABLE | Convergence reason from ConvergenceSignal (null while IN_PROGRESS) |
| `conversation_state_snapshot` | `TEXT` | NULLABLE | JSON-serialised `ConversationState` at debate completion — enables post-hoc queries without replaying channel messages (which may have been compressed by `ChannelSummariser`) |
| `common_ground_snapshot` | `TEXT` | NULLABLE | JSON-serialised `CommonGroundState` at debate completion — derived from conversation_state_snapshot + epistemic rules, stored separately for direct access without re-analysis |

**Indexes:**

```sql
CREATE UNIQUE INDEX idx_deliberation_record_inflight
ON deliberation_record(instrument) WHERE status = 'IN_PROGRESS';

CREATE INDEX idx_deliberation_record_instrument ON deliberation_record(instrument, status);
```

### REST Endpoints

| Method | Path | Purpose | Response |
|--------|------|---------|----------|
| `GET` | `/api/deliberations` | List deliberations | Paginated `DeliberationRecord` list, filterable by `instrument`, `convergenceState`, `triggerType` |
| `GET` | `/api/deliberations/{id}` | Single record | `DeliberationRecord` |
| `GET` | `/api/deliberations/{id}/state` | ConversationState | For IN_PROGRESS: returns `observer.currentState()` (live projected state with full point/thread/memo structure). For COMPLETED: returns the `conversation_state_snapshot` from `DeliberationRecord` (compression-safe). Returns 404 if IN_PROGRESS and observer is no longer available. |
| `GET` | `/api/deliberations/{id}/common-ground` | CommonGroundState | For IN_PROGRESS: replays `FsiConversationProjection` from live channel and applies `CommonGroundAnalyser.analyse()`. For COMPLETED: returns the `common_ground_snapshot` from `DeliberationRecord` (compression-safe). |
| `POST` | `/api/deliberations/trigger` | Manual trigger | `DeliberationRecord` (async — returns 202 Accepted with the record ID, deliberation runs in background). Returns 409 if deliberation already in progress for the instrument. |

### Deliberation Ledger Entry

`DeliberationDecisionLedgerEntry` — JOINED inheritance subclass of `LedgerEntry` (`@DiscriminatorValue("DELIBERATION_DECISION")`). Completes the causality chain required by ARC42STORIES §1 Quality Goal #1:

MarketEvent → `StrategyEvaluationLedgerEntry` (if arena-originated) → `DeliberationDecisionLedgerEntry` → `OrderExecutionLedgerEntry`

| Field | Type | Description |
|-------|------|-------------|
| `deliberationId` | `UUID` | FK to `DeliberationRecord.id` |
| `channelId` | `UUID` | qhorus channel reference |
| `instrument` | `VARCHAR(20)` | Instrument symbol |
| `convergenceState` | `VARCHAR(30)` | CONSENSUS / CONVERGING / DIMINISHING_RETURNS |
| `confidence` | `DOUBLE` | From ConvergenceSignal |
| `establishedCount` | `INT` | Established facts count |
| `disputedCount` | `INT` | Disputed points count |
| `participants` | `VARCHAR(500)` | Comma-separated agent sender IDs |

Written by `FsiDeliberationOutcomeHandler` before order creation. The `OrderExecutionLedgerEntry` created by the execution pipeline carries `causedByEntryId` pointing to this ledger entry, completing the tamper-evident decision chain.

Flyway migration: next available V-number in the V2100+ range (qhorus datasource).

### WebSocket Failure Reporting

When a deliberation fails (agent errors, channel creation failure, convergence errors, wall-clock timeout), the `deliberation/active` WebSocket topic sends a failure notification:

```json
{
  "instrument": "AAPL",
  "channelId": "uuid",
  "state": "FAILED",
  "reason": "Wall-clock timeout after 15 minutes",
  "deliberationId": "uuid"
}
```

The `DeliberationRecord` is updated with `status = FAILED` and `summary` containing the failure reason. Callers polling `GET /api/deliberations/{id}` can inspect the record for failure details.

### DeliberationResource

JAX-RS `@Path("/api/deliberations")` resource. Injects `DeliberationRecordRepository` (Panache) for queries and `FsiDeliberationOrchestrator` for the manual trigger.

---

## §8 UI Panels & WebSocket Topics

### Panels

Four panels, all using existing blocks-ui components — no custom panels needed for C3:

| Panel | blocks-ui Component | Data source | Wired in |
|-------|-------------------|-------------|----------|
| Deliberation feed | `channel-activity` | qhorus channel WebSocket (native) | C5 Trading Desk |
| Commitment lifecycle | `commitment-viz` | `deliberation/{channelId}` push topic | C5 Trading Desk |
| Deliberation timeline | `blocks-timeline` (commitment strategy) | Commitment lifecycle events | C5 Trading Desk |
| Context usage | `context-gauge` | `ContextTracker.snapshot()` via SSE | C5 Ops Centre |

### WebSocket Topics

Published via `FsiMarketPushService`'s `PushBroadcaster` (C3 adds `subscribeDeliberation()` — see §5):

| Topic | Payload | Trigger |
|-------|---------|---------|
| `deliberation/{channelId}` | Commitment state changes, convergence progress | Outcome handler + per-round updates |
| `deliberation/active` | Started/completed notifications (instrument, channelId, state) | Trigger + completion |

### No Custom Web Components

`channel-activity` renders qhorus channel messages natively — the `##FSI##` sentinel metadata is parsed by the component's existing projection support. `commitment-viz` and `blocks-timeline` consume commitment lifecycle events from the push topic. `context-gauge` polls the ContextTracker snapshot SSE endpoint.

All four panels are composed into dock-workbench zones in C5. C3's responsibility is ensuring the data contracts (push topics, SSE endpoints, REST resources) exist.

---

## Design Limitations

1. **Synthetic data limits debate realism.** Strategy agents deliberate based on synthetic market data. Their reasoning is bounded by the quality of the synthetic patterns. Real market data (future upgrade) would produce more meaningful deliberation.

2. **No real news feed.** The `NewsCheckHandler` generates plausible assessments from price patterns, not actual news. Real news integration (RSS, API) is a future enhancement that swaps the handler implementation without changing the interface.

3. **Human escalation is fire-and-forget for C3.** DEADLOCK creates a commitment with `obligor = FsiActorIdentity.HUMAN_TRADER` (`"human:trader@v1"`) but there's no UI for the human to respond. C4's work item approval gates will close this loop. Until then, DEADLOCK commitments expire. The `human:` prefix distinguishes human actors from `rule:` (strategy agents) while following the same `type:name@version` format — but `"human:trader@v1"` is not resolvable to an `AgentDescriptor` and will have no trust scoring feedback.

4. **Single-instrument deliberation.** Each deliberation concerns one instrument. Cross-instrument correlation is available as a sub-task (CorrelationCheckHandler) but the deliberation itself doesn't span instruments. Multi-instrument deliberation (e.g., pairs trading decisions) is a future enhancement.

5. **ChannelAgent invoker dependency.** The debate pattern's `AgentInvoker` must handle `AgentRef.ChannelAgent`. This is provided by `PlatformAgentInvoker` (blocks#19/engine integration). If the invoker isn't available in the current SNAPSHOT, a local implementation wrapping the existing `FsiArenaRouting` agent provider will bridge the gap.
