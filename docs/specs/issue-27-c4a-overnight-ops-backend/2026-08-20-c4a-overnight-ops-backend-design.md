# C4a: Overnight Ops Backend Design

**Date:** 2026-08-20 (revised 2026-08-21)
**Issue:** #27
**Scope:** Backend half of C4 — case definition, SLA, risk classifier, WorkItems, notifications, REST endpoints. No UI panels (those are C4b #29).

---

## Summary

When an anomaly is detected outside market hours — a flash crash, liquidity drop, counterparty failure, or margin call — the system creates an incident case, decomposes the response by severity via HTN, gates high-risk actions through WorkItem approvals, enforces SLA escalation, and notifies the on-call trader. Every decision in the chain is auditable.

This spec covers the backend plumbing. It implements 3 platform SPIs (`ActionRiskClassifier`, `SlaBreachPolicy`, `CaseHub`), follows 4 platform protocols (case-definition-layers, descriptor-handler-pattern, oversight-action-gate-dedicated-hub, module-tier-structure), adds 2 orchestration patterns (HTN, Conditional), and exposes 7 REST endpoints.

---

## Architecture Overview

```
Market event (C2 pipeline)  ──┐
                               ├──→ FsiIncidentTrigger
External operational event ───┘        │
                                       ├─ classify severity (via IncidentSeverityDescriptor)
                                       ├─ create case via engine
                                       └─ fire IncidentCreatedEvent (CDI)
                                              │
                    ┌─────────────────────────┘
                    ▼
         OvernightIncidentCaseHub (YamlCaseHub)
           overnight-incident.yaml: milestones, bindings, goals
           OvernightIncidentCaseDescriptor: worker logic, SLA, capabilities
                    │
                    ▼
              HTN decomposition (by severity — via IncidentSeverityDescriptor)
              ┌─────────┬───────────┬──────────┐
              │CRITICAL  │HIGH       │MEDIUM    │
              │halt      │reduce     │adjust    │
              │close     │hedge      │monitor   │
              │alert     │alert      │verify    │
              │verify    │verify     │          │
              └────┬─────┴─────┬─────┴────┬─────┘
                   │           │          │
                   ▼           ▼          ▼
            Conditional routing (by event type — via MarketEventTypeDescriptor)
                           │
              FsiActionRiskClassifier gates actions
              (dedicated FsiOversightCaseHub per protocol)
                           │
              FsiSlaBreachPolicy enforces deadlines
                           │
              CDI events + UI push deliver alerts
```

---

## 1. Incident Trigger — `FsiIncidentTrigger`

`@ApplicationScoped` bean with two ingest paths (D2):

### 1.1 Market-detected events

C2's `FsiMarketEventDetector` uses constructor-injected `Consumer<T>` callbacks, not CDI events. The bridge approach: the wiring layer that creates `FsiMarketEventDetector` (in `MarketPulseConfiguration`) adds a callback that fires CDI events via `Event<T>.fire()`. `FsiIncidentTrigger` then observes these CDI events via `@Observes`.

```java
// In MarketPulseConfiguration or equivalent wiring bean:
detector = new FsiMarketEventDetector(
    reversal -> trendReversalEvent.fire(reversal),
    regime -> regimeChangedEvent.fire(regime)
);
```

`FsiIncidentTrigger` observes:
- `TrendReversalDetected` — from bridge when Level 2 trend reverses
- `RegimeChanged` — from bridge when Level 3 regime shifts

Classification logic (delegated to `IncidentSeverityDescriptor`) determines severity based on event magnitude, time of day, and position exposure.

### 1.2 External operational events

`POST /api/incidents/external` accepts operational events that have no market data signature:
- `COUNTERPARTY_FAILURE` — broker/clearing house notification
- `MARGIN_CALL` — broker margin requirement notification

Payload: `{ eventType, instrument, severity, description, source }`.

**Security:** Endpoint requires authentication. Authorized roles: `fsi-ops`, `fsi-admin`. Rate-limited to prevent denial-of-service via flood of fake incidents. Input validation: `eventType` must be a valid `MarketEventType`, `severity` must be a valid `IncidentSeverity`, `instrument` must be non-blank.

### 1.3 Severity classification — via `IncidentSeverityDescriptor`

Per descriptor-handler-pattern protocol (PP-20260609), severity-specific behaviour lives in a descriptor POJO, not in switch statements across services.

```java
public record IncidentSeverityDescriptor(
    IncidentSeverity severity,
    List<String> decompositionSteps,
    Duration claimDeadline,
    Duration completionDeadline,
    Set<String> candidateGroups
) {
    public static IncidentSeverityDescriptor forSeverity(IncidentSeverity severity) {
        return REGISTRY.get(severity);
    }
}
```

| Severity | Decomposition steps | Claim deadline | Completion deadline | Candidates |
|----------|-------------------|----------------|-------------------|------------|
| CRITICAL | halt, close, alert, verify | 2 min | 5 min | fsi-oncall |
| HIGH | reduce, hedge, alert, verify | 7 min | 15 min | fsi-oncall |
| MEDIUM | adjust, monitor, verify | 30 min | 60 min | fsi-oncall |

Trigger classification thresholds:

| Severity | Trigger examples |
|----------|-----------------|
| CRITICAL | Flash crash (>5% drop in <1 min), full counterparty failure, margin call on >50% of portfolio |
| HIGH | Liquidity drop (>3σ spread widening), partial counterparty failure, large gap open |
| MEDIUM | Regime change to VOLATILE, circuit breaker on single instrument, news event with sentiment shift |

Off-hours (before 07:00, after 20:00) amplifies MEDIUM to HIGH.

---

## 2. Case Definition — YAML + YamlCaseHub + CaseDescriptor

Per case-definition-layers protocol (PP-20260518): application case definitions use YAML for structure, `YamlCaseHub` for runtime entry, `*CaseDescriptor` for business logic.

### 2.1 `overnight-incident.yaml`

```yaml
name: overnight-incident
namespace: fsitrading
version: "1.0.0"
spec:
  milestones:
    - DETECTED
    - CLASSIFIED
    - RESPONDED
    - VERIFIED
    - CLOSED
  goals:
    - name: capital-preserved
      condition: ".portfolioValueRatio >= 0.95"
    - name: positions-safe
      condition: ".unhedgedExposure == 0"
    - name: sla-met
      condition: ".slaBreachCount == 0"
  bindings:
    - name: classify
      on: { contextChange: {} }
      when: ".severity != null and .classified == null"
      capability: "incident-classify"
    - name: respond
      on: { contextChange: {} }
      when: ".classified == true and .responded == null"
      capability: "incident-respond"
    - name: verify
      on: { contextChange: {} }
      when: ".responded == true and .verified == null"
      capability: "incident-verify"
    - name: review
      on: { contextChange: {} }
      when: ".verified == true and .closed == null"
      humanTask:
        title: "Incident Review"
        types: ["incident-review"]
        candidateGroups: "fsi-oncall"
  cbrConfig:
    topK: 5
    minSimilarity: 0.6
    temporalDecayHalfLifeDays: 90
```

### 2.2 `OvernightIncidentCaseHub`

```java
@ApplicationScoped
public class OvernightIncidentCaseHub extends YamlCaseHub {
    public OvernightIncidentCaseHub() {
        super("fsitrading/overnight-incident.yaml");
    }

    @Override
    protected void augment(CaseDefinition definition) {
        descriptor.augmentWorkers(definition);
    }
}
```

### 2.3 `OvernightIncidentCaseDescriptor`

Carries all business logic — worker lambdas, SLA policies, decomposition sequences. Tested independently of Quarkus (pure Java, no CDI).

```java
public class OvernightIncidentCaseDescriptor {
    private final Map<IncidentSeverity, IncidentSeverityDescriptor> severities;
    private final Map<MarketEventType, MarketEventTypeDescriptor> eventTypes;

    // Worker functions, capability routing, SLA policy assignment
    public void augmentWorkers(CaseDefinition definition) { ... }
}
```

### 2.4 Goals

| Goal | Success criteria |
|------|-----------------|
| `capital-preserved` | Post-incident portfolio value ≥ 95% of pre-incident value |
| `positions-safe` | No unhedged exposure in affected instruments |
| `sla-met` | All response actions completed within SLA window |

---

## 3. HTN Decomposition

`Patterns.htn()` with compound root task `handle-overnight-incident`. Uses `HybridDecomposition` — static methods first, LLM fallback for novel scenarios (D3).

### 3.1 Static decomposition methods

Decomposition sequences live in `IncidentSeverityDescriptor` (not static methods):

| Severity | Steps |
|----------|-------|
| CRITICAL | emergency-halt → close-positions → alert-oncall → verify |
| HIGH | reduce-exposure → hedge → alert-oncall → verify |
| MEDIUM | adjust-limits → monitor → verify |

### 3.2 HTN + Conditional composition

The HTN "respond" leaf task delegates to a Conditional sub-pattern via `AgentRef.composed()`. The Conditional routes by event type to the appropriate response agent. This follows the C1 pattern where Supervisor, Parallel, and Voting compose via `AgentRef.composed()`.

```
HTN root: handle-overnight-incident
  └─ method (CRITICAL): [halt, close, respond, alert, verify]
                                  │
                                  └─ respond: AgentRef.composed(Patterns.conditional()
                                       .when(FLASH_CRASH, emergencyHaltAgent)
                                       .when(LIQUIDITY_DROP, positionReducerAgent)
                                       ...)
```

### 3.3 LLM fallback + recovery

When no static method matches, `HybridDecomposition` delegates to `LlmDecomposition`. LLM output is validated against the allowed action vocabulary.

**Recovery policy:** LLM agent failures fall back to a conservative rule-based action. If an LLM agent times out or returns invalid output, the system executes a default safe action for the event type (halt for CRITICAL, reduce for HIGH, monitor for MEDIUM). The `CaseDefinition` includes a `RecoveryPolicy` that maps to these defaults.

### 3.4 Agent implementation (D3)

| Agent | Type | Rationale |
|-------|------|-----------|
| `emergencyHaltAgent` | Rule-based | Halts all trading — deterministic |
| `closePositionsAgent` | Rule-based | Closes positions at market — deterministic |
| `haltAndWaitAgent` | Rule-based | Suspends trading — deterministic |
| `alertOncallAgent` | Rule-based | Creates WorkItem + fires notification — deterministic |
| `positionReducerAgent` | LLM | Decides which positions to reduce, considering liquidity |
| `hedgeAgent` | LLM | Selects hedging instruments and sizing |
| `reEvaluatorAgent` | LLM | Assesses gap open impact |
| `exposureCloserAgent` | LLM | Prioritises counterparty exposure closure |
| `sentimentAnalyserAgent` | LLM | Interprets news event |
| `liquidationAgent` | LLM | Plans orderly liquidation sequence |
| `adjustLimitsAgent` | Rule-based | Tightens position limits — deterministic |
| `monitorAgent` | Rule-based | Sets up monitoring thresholds — deterministic |
| `verifyAgent` | Rule-based | Validates position state — deterministic |

---

## 4. Conditional Routing — via `MarketEventTypeDescriptor`

Per descriptor-handler-pattern protocol (PP-20260609), event-type routing lives in a descriptor POJO:

```java
public record MarketEventTypeDescriptor(
    MarketEventType eventType,
    String agentName,
    String eventSource,
    String fallbackAction
) {
    public static MarketEventTypeDescriptor forType(MarketEventType type) {
        return REGISTRY.get(type);
    }
}
```

| Event type | Agent | Source | Fallback |
|-----------|-------|--------|----------|
| `FLASH_CRASH` | `emergencyHaltAgent` | Market-detected | halt |
| `LIQUIDITY_DROP` | `positionReducerAgent` | Market-detected | reduce |
| `GAP_OPEN` | `reEvaluatorAgent` | Market-detected | monitor |
| `COUNTERPARTY_FAILURE` | `exposureCloserAgent` | External | halt |
| `CIRCUIT_BREAKER` | `haltAndWaitAgent` | Market-detected | halt |
| `NEWS_EVENT` | `sentimentAnalyserAgent` | Market-detected | monitor |
| `MARGIN_CALL` | `liquidationAgent` | External | reduce |

### 4.1 MarketEventType extension

Add `COUNTERPARTY_FAILURE` and `MARGIN_CALL` to the existing enum. Known limitation: conflates market, detected, and operational events. **Filed as GitHub issue** for sealed hierarchy migration (not just noted as tech debt).

---

## 5. Action Risk Classifier — `FsiActionRiskClassifier`

Implements `ActionRiskClassifier` SPI from `casehub-engine-api`. Annotated with `@RiskClassifier` CDI qualifier for auto-composition (GE-20260607-3b6711).

### 5.1 Dedicated oversight case hub

Per oversight-action-gate-dedicated-hub protocol (PP-20260612-181367), action risk gates use a dedicated `YamlCaseHub`:

```java
@ApplicationScoped
public class FsiOversightCaseHub extends YamlCaseHub {
    public FsiOversightCaseHub() {
        super("fsitrading/fsi-oversight.yaml");
    }
}
```

The oversight YAML defines the gate workflow (approval, delegation, timeout) as a separate case definition from the incident response case. The `FsiActionRiskClassifier` returns `GateRequired` decisions that the platform routes to this oversight case.

### 5.2 Portfolio ratio data flow

`classify(PlannedAction, ClassificationContext)` receives portfolio data via `PlannedAction.parameters()`. Response agents MUST populate `portfolioRatio` (double, 0.0–1.0) in their `PlannedAction` parameters before submitting the action. The classifier reads it:

```java
double ratio = ((Number) action.parameters().getOrDefault("portfolioRatio", 0.0)).doubleValue();
```

### 5.3 Classification table

| Action | Risk | RiskDecision |
|--------|------|-------------|
| Close < 10% portfolio | LOW | `new Autonomous()` |
| Close 10-25% portfolio | MEDIUM | `new Autonomous()` (log only) |
| Close > 25% portfolio | HIGH | `GateRequired` (see §5.4) |
| Full liquidation | CRITICAL | `GateRequired` (see §5.4) |
| New position during incident | MEDIUM | `new Autonomous()` (log only) |
| Counterparty exposure close | HIGH | `GateRequired` (see §5.4) |

Three classification dimensions: magnitude (portfolio ratio), action type, situational context. Independent from C1's `FsiRiskAssessor` (D5).

### 5.4 GateRequired construction

`GateRequired` is a Java record — no builder, all 7 parameters required:

```java
new RiskDecision.GateRequired(
    "Close > 25% portfolio: " + String.format("%.1f%%", ratio * 100),
    false,  // reversible — position closes are not reversible
    StaticSetStrategy.of("fsi-oncall"),
    descriptor.completionDeadline(),  // from IncidentSeverityDescriptor
    "fsitrading",
    null,   // resolutionType
    null    // quorum
)
```

### 5.5 Dual gate interaction (D8)

C1's `FsiRiskGateRouting` and C4's `FsiActionRiskClassifier` operate at different lifecycle stages. Arena evaluation (C1) gates proactive trades; incident response (C4) gates reactive actions. No dedup needed.

---

## 6. SLA Breach Policy — `FsiSlaBreachPolicy`

Implements `SlaBreachPolicy` SPI from `casehub-work-api`. Named strategy: `fsi-overnight-sla`.

### 6.1 SLA windows

Set on the WorkItem at creation time via `IncidentSeverityDescriptor`. `GateRequired.expiresIn` maps to the completion deadline (`expiresAt`). The claim deadline is set separately — the platform derives it from the `IncidentSeverityDescriptor.claimDeadline()` when creating the WorkItem from the `GateRequired` decision.

| Severity | Claim deadline | Completion deadline |
|----------|---------------|-------------------|
| CRITICAL | 2 min | 5 min |
| HIGH | 7 min | 15 min |
| MEDIUM | 30 min | 60 min |

### 6.2 Breach handling

**Tier 1 — `CLAIM_EXPIRED`:**
```java
if (!context.task().candidateGroups().contains("oncall-escalation")) {
    return BreachDecision.EscalateTo.to("oncall-escalation");
}
```

**Tier 2 — already-escalated `CLAIM_EXPIRED` or `COMPLETION_EXPIRED`:**
```java
return new BreachDecision.Exhausted("SLA exhausted — auto-executing");
```

`Exhausted` (not `Fail`) — semantically correct for "all tiers used up, proceeding with the gated action." `Fail` means "abort the action"; `Exhausted` means "we tried all escalation tiers."

### 6.3 Stateless tier detection

Per garden pattern (GE-20260522-f7db12): the policy reads `candidateGroups` from `SlaBreachContext.task()` to determine the current tier. No serialised state.

**Note:** This 2-tier claim/completion model supersedes the replan spec §4.5's aspirational "3 tiers at 50%/75%/100%" — which doesn't map to the actual `BreachType` enum (only `CLAIM_EXPIRED` and `COMPLETION_EXPIRED`).

---

## 7. Notifications (D1)

### 7.1 UI push (real-time dashboard updates)

Via existing `EventBroadcaster`/`FsiPushWebSocket`:

| Topic | Events |
|-------|--------|
| `incidents/{caseId}` | Incident created, milestone reached, severity changed |
| `incidents/summary` | Aggregate incident counts |
| `work-items/{itemId}` | WorkItem created, claimed, completed, escalated |

### 7.2 Human notification

`FsiIncidentNotifier` fires typed CDI events and pushes via `EventBroadcaster`. Platform `NotificationDispatcher` not yet published — when it ships, it observes these CDI events automatically.

| Event | When | Severity signal |
|-------|------|----------------|
| `IncidentCreatedEvent` | Case created | Incident severity |
| `GateOpenedEvent` | Risk classifier returns GateRequired | Action risk level |
| `SlaBreachEvent` | SLA breach at any tier | Tier + severity |
| `IncidentResolvedEvent` | Case reaches CLOSED milestone | — |

**EventTypeRegistry registration:** `FsiEventTypeRegistrar` (`@Startup`) registers all 4 event types with `EventTypeRegistry` (available in `casehub-platform-api`).

---

## 8. REST Endpoints

| Method | Path | Purpose |
|--------|------|---------|
| `GET` | `/api/incidents` | Recent incidents with status (paginated) |
| `GET` | `/api/incidents/{caseId}` | Incident detail |
| `GET` | `/api/incidents/{caseId}/timeline` | Timeline events for incident |
| `POST` | `/api/incidents/simulate` | Inject simulated incident for demo/testing |
| `POST` | `/api/incidents/external` | Ingest external operational event (D2) — requires `fsi-ops` role |
| `GET` | `/api/work-items` | Work items filtered by type/candidateGroups/priority |
| `POST` | `/api/work-items/{id}/resolve` | Approve/Reject/Delegate gated action |

### 8.1 Incident simulation

`POST /api/incidents/simulate` bypasses the C2 pipeline — creates an incident directly via `FsiIncidentTrigger` with specified parameters. Demo and integration testing only.

---

## 9. Module Placement

Following module-tier-structure protocol (PP-20260512) and Store SPI pattern:

### `api/` module (Tier 1 — pure Java, no Quarkus, no JPA)

**Model types:**
- `MarketEventType` — add `COUNTERPARTY_FAILURE`, `MARGIN_CALL`
- `IncidentSeverity` — enum: `CRITICAL`, `HIGH`, `MEDIUM`
- CDI event records: `IncidentCreatedEvent`, `GateOpenedEvent`, `SlaBreachEvent`, `IncidentResolvedEvent`
- `ExternalIncidentRequest` — REST DTO

**Store SPI:**
- `IncidentRecord` — domain POJO (not a JPA entity): `UUID caseId`, `IncidentSeverity severity`, `MarketEventType eventType`, `List<String> instruments`, `String status`, `Instant createdAt`, `Instant resolvedAt`
- `IncidentTimelineRecord` — domain POJO: `String milestone`, `Instant timestamp`, `String description`
- `IncidentStore` — SPI interface: `save(IncidentRecord)`, `findByCaseId(UUID)`, `findRecent(int)`, `addTimelineEntry(UUID, IncidentTimelineRecord)`

**Descriptors:**
- `IncidentSeverityDescriptor` — decomposition steps, SLA windows, candidate groups per severity
- `MarketEventTypeDescriptor` — agent routing, event source, fallback action per event type

### `app/` module (Tier 3 — Quarkus + JPA)

**Package: `io.casehub.fsitrading.app.incident`**
- `OvernightIncidentCaseHub extends YamlCaseHub` — loads `overnight-incident.yaml`
- `OvernightIncidentCaseDescriptor` — worker logic, capability routing, SLA
- `FsiOversightCaseHub extends YamlCaseHub` — oversight action gate case
- `FsiActionRiskClassifier` — `ActionRiskClassifier` SPI with `@RiskClassifier`
- `FsiSlaBreachPolicy` — `SlaBreachPolicy` SPI
- `FsiIncidentTrigger` — incident classification and case creation
- `FsiIncidentNotifier` — CDI events + UI push
- `FsiEventTypeRegistrar` — registers event types at startup
- `IncidentResource` — REST endpoints
- `WorkItemResource` — REST endpoints

**Package: `io.casehub.fsitrading.app.incident.store`**
- `IncidentEntity` — JPA entity mapping `IncidentRecord`
- `IncidentTimelineEntity` — JPA entity mapping `IncidentTimelineRecord`
- `JpaIncidentStore implements IncidentStore` — Panache implementation

**Package: `io.casehub.fsitrading.app.incident.agent`**
- 7 rule-based agents + 6 LLM agents (per §3.4)

**Flyway migrations:** V104 (`fsi_incident` table), V105 (`fsi_incident_timeline` table). Range V104–V109 reserved for C4a.

**Resources:**
- `src/main/resources/fsitrading/overnight-incident.yaml`
- `src/main/resources/fsitrading/fsi-oversight.yaml`

---

## 10. Dependencies

No new Maven dependencies required. All SPIs are available via existing dependencies.

---

## 11. Known Limitations

1. **MarketEventType enum conflation** — mixes market, detected, and operational events. **GitHub issue filed** for sealed hierarchy migration.
2. **No email/SMS delivery** — platform `NotificationDispatcher` not yet published. Push + CDI events for the showcase.
3. **CBR seeded from simulation** — real value comes from production usage.
4. **Mechanical/judgment boundary** — D3's line between rule-based and LLM agents may need adjustment.
5. **LLM agent prompts** — model selection and prompt design are implementation concerns.
6. **Replan supersession** — this spec's 2-tier SLA model supersedes the replan §4.5's aspirational 3-tier percentage model.

---

## References

- Replan spec §4.1-§4.9
- PP-20260518 case-definition-layers — YAML + YamlCaseHub + CaseDescriptor
- PP-20260609 descriptor-handler-pattern — enum behaviour in descriptor POJOs
- PP-20260612-181367 oversight-action-gate-dedicated-hub — dedicated YamlCaseHub for action gates
- PP-20260512 module-tier-structure — Store SPI pattern, three-tier modules
- GE-20260511-3e5a75 — SLA enforcement: candidateGroups + claimDeadline
- GE-20260522-f7db12 — Stateless multi-tier SLA escalation via candidateGroups
- GE-20260607-3b6711 — ActionRiskClassifier auto-composed via @RiskClassifier
- GE-20260607-326c7e — GateRequired restrictiveness
- GE-20260622-71f4b9 — WorkItemLifecycleEvent.detail() null for ESCALATED
- GE-20260810-502dec — YamlCaseHub augment() uses removeIf + add for records
- `ActionRiskClassifier` — `io.casehub.api.spi.ActionRiskClassifier`
- `RiskDecision` — sealed: `Autonomous()`, `GateRequired(reason, reversible, candidateGroups, expiresIn, scope, resolutionType, quorum)`
- `PlannedAction` — `(description, actionType, Map<String, Object> parameters)`
- `SlaBreachPolicy` — `io.casehub.work.api.spi.SlaBreachPolicy`
- `BreachDecision` — sealed: `Fail(reason)`, `EscalateTo(Set<String>, Duration)`, `Exhausted(reason)`, `Extend(Duration)`, `Chained(primary, fallback)`
- `BreachedTask` — `(UUID taskId, String callerRef, String title, Set<String> candidateGroups)`
- `StaticSetStrategy.of("fsi-oncall")` — factory for CandidateSetStrategy
