# C6b: Post-mortem + Compliance Grid + GDPR Erasure

**Date:** 2026-09-03
**Issue:** casehubio/fsitrading#37
**Parent epic:** casehubio/fsitrading#23 (C6: Knowledge & Compliance)
**Branch:** issue-23-knowledge-compliance

---

## Summary

Three independent backend capabilities for the Knowledge & Compliance chapter:

1. **Automated post-mortem** — generates markdown from an overnight incident's conversation state via the platform's `ConversationRenderer`. A qhorus channel captures agent interactions during the incident lifecycle; the post-mortem endpoint replays the channel through `ConversationProjection` on demand.

2. **Compliance grid** — live evidence query mapping 5 regulatory requirements (MiFID II Art.17, RTS 6 monitoring, RTS 6 kill switches, Dodd-Frank audit trail, MAR surveillance) to existing platform mechanisms. Returns `TrustRoutingRequirement` records with `RequirementStatus` and `RoutingDecisionRecord` evidence.

3. **GDPR erasure** — single-call orchestrator erasing trader data from episodic memory (`CaseMemoryStore`), CBR cases (`CbrCaseMemoryStore`), and ledger identity mappings (`LedgerErasureService`). Domain-scoped CBR erasure via `EraseRequest`.

---

## 1. Automated Post-Mortem

### Architecture (D11, D14, D15)

The post-mortem pipeline has three stages: capture → accumulate → render.

```
Incident Lifecycle                          Post-mortem Endpoint
─────────────────                          ──────────────────────

CDI Events                                 GET /api/postmortem/{caseId}
  │                                            │
  ├─ WorkItemLifecycleEvent                    ▼
  │   (work item state transitions)     Replay channel messages
  │                                            │
  ├─ CaseOutcomeEvent                          ▼
  │   (case terminal state)             IncidentConversationProjection
  │                                            │
  ▼                                            ▼
IncidentPostMortemBridge                ConversationState
  │                                            │
  │  @ObservesAsync                            ▼
  │  WorkItemLifecycleEvent             ConversationRenderer
  │  CaseOutcomeEvent                          │
  ▼                                            ▼
Qhorus Channel                          Markdown response
  "postmortem"
  (persisted messages)
```

### Channel Configuration

The `postmortem` channel is created programmatically in `OvernightIncidentCaseHub.augment()` rather than via YAML (the YAML DSL does not currently support `channels` declarations). The channel uses qhorus JPA persistence.

The channel receives messages from the bridge observer. Agent workers can optionally post to it during execution for richer narrative.

**Data constraint (GDPR):** Bridge messages must not include trader-identifying data. Messages describe agent actions on instruments (agent IDs, instrument symbols, strategy names) — not trader personal data. This design constraint means the postmortem channel is NOT a GDPR erasure target.

### IncidentPostMortemBridge (D11)

`@ApplicationScoped` CDI observer that bridges engine events to qhorus messages on the `postmortem` channel.

**Event sources — real engine types:**

| CDI Event | Source | Data available |
|---|---|---|
| `WorkItemLifecycleEvent` | `io.casehub.work.api` | status, actor, detail, rationale, planRef, outcome, workItem |
| `CaseOutcomeEvent` | `io.casehub.api.spi` | caseType, caseId, outcomeLabel, caseFileSnapshot, closedAt |

**Event mapping — milestone-level granularity (D14):**

| Event | ConversationPoint mapping | Message entry type |
|---|---|---|
| `WorkItemLifecycleEvent` (status=CREATED) | Creates new ConversationPoint with `topic` derived from `planRef` milestone | PROPOSE — "{actor} assigned: {detail}" |
| `WorkItemLifecycleEvent` (status=COMPLETED) | Appends ThreadEntry to milestone's point | COMMIT — "{actor} completed: {outcome}" |
| `WorkItemLifecycleEvent` (status=FAILED) | Appends ThreadEntry to milestone's point | ASSERT — "{actor} failed: {detail}" |
| `CaseOutcomeEvent` | Creates final ConversationPoint `topic = "OUTCOME"` | DONE — "Case {outcomeLabel}: {summary}" |

The bridge derives the current milestone from `WorkItemLifecycleEvent.planRef()` — the plan reference encodes the HTN decomposition path. Work items within the same milestone share a common plan prefix. The bridge groups by this prefix to produce one ConversationPoint per milestone phase.

**Classification mapping:**

| Field | Source |
|---|---|
| `priority` | Incident severity from CaseContext (CRITICAL → HIGH, HIGH → MEDIUM, MEDIUM → LOW) |
| `scope` | `"incident-response"` |
| `location` | Instrument symbol from CaseContext |

### IncidentConversationProjection (R1-02)

Subclass of `ConversationProjection` that defines the post-mortem channel's message parsing rules:

```java
public class IncidentConversationProjection extends ConversationProjection {
    @Override protected String sentinel() { return "PMETA:"; }
    @Override protected boolean isPointInitiator(String entryType) {
        return "PROPOSE".equals(entryType);  // new work item = new point
    }
    @Override protected String statusAfter(String entryType) {
        return switch (entryType) {
            case "DONE" -> "RESOLVED";
            case "ASSERT" -> "ESCALATED";  // failures escalate
            default -> "OPEN";
        };
    }
}
```

The bridge formats messages with the `PMETA:` sentinel prefix in `ChannelMessageMeta` format so the projection can parse them.

### ConversationRendererConfig

```java
ConversationRendererConfig.builder()
    .groupByTopic(true)
    .showEpistemicStatus(true)
    .showConvergenceSignal(true)
    .showObligationChain(true)
    .showProgress(false)
    .statusEmoji(Map.of(
        "OPEN", "🟡",
        "RESOLVED", "✅",
        "ESCALATED", "🔴"))
    .resolvedStatuses(Set.of("RESOLVED"))
    .escalatedStatuses(Set.of("ESCALATED"))
    .build();
```

### RenderContext Population

At render time, the endpoint populates `RenderContext` with:

- **commonGround** — `CommonGroundAnalyser.analyse(conversationState, epistemicRule)` produces `CommonGroundState`. The `EpistemicRule` classifies points: ESTABLISHED if all thread entries reached COMMIT/DONE, DISPUTED if any ASSERT (failure), PENDING otherwise
- **convergence** — derived from case outcome: goals met → `ConvergenceSignal(ConvergenceState.CONSENSUS, 1.0, "incident resolved")`, goals partially met → `ConvergenceSignal(ConvergenceState.CONVERGING, goalSuccessRate, detail)`
- **reactions** — empty (no agent reactions in audit channel)
- **progress** — empty (not used for post-mortem)

### Post-Mortem Output Structure

The rendered markdown includes:

1. **Convergence summary** — overall incident resolution status
2. **Per-milestone sections** (grouped by topic):
   - Epistemic status counts (established/pending/disputed)
   - Obligation chain (PROPOSE → COMMIT → DONE flow)
   - Agent actions as threaded entries
3. **Human flags** — any items flagged for human review during the incident
4. **Agent memos** — optional agent observations posted to the channel
5. **CBR record** — appended after rendering: similar incidents retrieved, adaptation strategies applied, outcome

### PostMortemResource

```
GET /api/postmortem/{caseId}
```

Returns: rendered markdown string (Content-Type: text/markdown)

Flow:
1. Load case by ID, verify case type is `overnight-incident`
2. Query qhorus messages for the `postmortem` channel scoped to this case
3. Replay through `ConversationProjection` → `ConversationState`
4. Build `RenderContext` with `CommonGroundAnalyser` output and convergence signal
5. Render via `ConversationRenderer` with FSI config
6. Append CBR context section (retrieved experiences, adaptations from `CaseContext["cbrExperiences"]`)

---

## 2. Compliance Grid

### Architecture (D12)

`FsiComplianceService` maps 5 regulatory requirements to platform mechanisms, querying live evidence at request time.

```
GET /api/compliance/status
        │
        ▼
FsiComplianceService
        │
        ├─ evaluateMifidArt17()      → LedgerEntryRepository
        ├─ evaluateRts6Monitoring()  → OTel MeterRegistry
        ├─ evaluateRts6KillSwitch()  → ActionRiskClassifier records
        ├─ evaluateDoddFrankAudit()  → LedgerEntryRepository (causedByEntryId)
        └─ evaluateMarSurveillance() → CbrCaseMemoryStore
                │
                ▼
        List<TrustRoutingRequirement>
```

### Requirement → Evidence Mapping

| Requirement | requirementId | Mechanism | Evidence query | Status logic |
|---|---|---|---|---|
| MiFID II Art.17 | `MIFID2_ART17` | Tamper-evident decision chain | `StrategyEvaluationLedgerEntry` + `OrderExecutionLedgerEntry` with `causedByEntryId` links via `TradingLedgerService` | CLOSED if chain count > 0; GAP if no entries |
| MiFID II RTS 6 monitoring | `MIFID2_RTS6_MON` | Real-time metrics | OTel histogram meters for strategy evaluation latency, order execution latency | CLOSED if meters registered; GAP if missing |
| MiFID II RTS 6 kill switches | `MIFID2_RTS6_KILL` | `ActionRiskClassifier` | Work item approval records where `ActionRiskClassifier.classify()` returned HIGH/CRITICAL | CLOSED if classifier active and approvals exist; PARTIAL if classifier active but no approvals triggered |
| Dodd-Frank audit trail | `DODD_FRANK_AUDIT` | `causedByEntryId` chains | Ledger entries with non-null `causedByEntryId`, chain depth ≥ 2 | CLOSED if chains exist; GAP if no causal links |
| MAR surveillance | `MAR_SURVEILLANCE` | CBR event sequence | `CbrCaseMemoryStore` scan for `event_sequence` feature similarity matching | CLOSED if CBR cases stored with event_sequence features; PARTIAL if schema registered but no cases |

### TrustRoutingRequirement Construction

Each evaluator returns a `TrustRoutingRequirement`:

```java
new TrustRoutingRequirement(
    "MIFID2_ART17",
    "MiFID II Article 17 — algorithmic trading decision audit",
    "Tamper-evident ledger entries with causedByEntryId chains",
    status,  // CLOSED, PARTIAL, BREACHED, or GAP
    routingDecisionRecords  // evidence list
);
```

`RoutingDecisionRecord` entries are constructed from ledger entries:

```java
new RoutingDecisionRecord(
    capabilityTag,        // e.g., "strategy-evaluation"
    workerId,             // agent that executed
    trustScoreAtRouting,  // from PnlAttestationService at routing time
    thresholdApplied,     // routing threshold
    ledgerEntry.id        // evidence UUID
);
```

### ComplianceResource

```
GET /api/compliance/status
```

Returns: `List<TrustRoutingRequirement>` as JSON

---

## 3. GDPR Erasure

### Architecture (D13, D16)

`FsiGdprErasureService` orchestrates erasure across three stores in sequence.

```
POST /api/gdpr/erase
  { "traderId": "...", "reason": "GDPR_ART_17_REQUEST" }
        │
        ▼
FsiGdprErasureService.erase(traderId, reason)
        │
        ├─ 1. CaseMemoryStore.eraseEntity(traderId, tenantId)
        │     → episodic agent memories across all domains (R1-06)
        │
        ├─ 2. CbrCaseMemoryStore.erase(EraseRequest)
        │     → CBR cases in "fsitrading" domain only (D16)
        │     domain-scoped to avoid cross-domain gotcha
        │
        └─ 3. LedgerErasureService.erase(traderId, reason)
              → severs token→identity mapping
              → writes ErasureReceiptLedgerEntry
                │
                ▼
        FsiErasureResult
          { memoriesErased, cbrCasesErased, ledgerResult }
```

### EraseRequest Construction (D16)

CBR erasure uses domain-scoped `EraseRequest` to avoid the cross-domain gotcha (GE-20260720-b7a8b9):

```java
new EraseRequest(traderId, new MemoryDomain("fsitrading"), tenantId, null);
```

Episodic memory uses `eraseEntity()` for full cross-domain GDPR wipe — the opposite strategy from CBR, because `CaseMemoryStore.eraseEntity()` is the documented GDPR path (no cross-domain gotcha for episodic memory):

```java
caseMemoryStore.eraseEntity(traderId, tenantId);
```

### FsiErasureResult

```java
public record FsiErasureResult(
    String traderId,
    int memoriesErased,
    int cbrCasesErased,
    LedgerErasureService.ErasureResult ledgerResult) {}
```

### Idempotency and Retry (D13)

Each store erasure is idempotent:
- `CaseMemoryStore.erase()` returns 0 if already erased
- `CbrCaseMemoryStore.erase()` returns 0 if already erased
- `LedgerErasureService.erase()` returns `mappingFound=false` if already severed

If one store fails mid-sequence, the endpoint returns an error. On retry, completed stores return count=0, failed stores execute. `LedgerErasureService` always writes a receipt (even if `mappingFound=false`), creating a retry-safe audit trail.

### Configuration Requirements

```properties
# Required for GDPR-meaningful ledger erasure — without tokenisation, raw actor IDs
# persist directly in ledger entries and cannot be erased via token mapping severance.
# LedgerErasureService runs without this flag but returns mappingFound=false with no
# data actually erased. (GE-20260531-46f8ab)
casehub.ledger.identity.tokenisation.enabled=true

# Required for erasure receipt audit trail
casehub.ledger.erasure-receipt.enabled=true
```

### GdprErasureResource

```
POST /api/gdpr/erase
```

Request body: `{ "traderId": "string", "reason": "GDPR_ART_17_REQUEST" | "RETENTION_EXPIRED" | "ACCOUNT_DELETION" }`

Returns: `FsiErasureResult` as JSON

---

## New Types

| Type | Package | Role |
|---|---|---|
| `IncidentPostMortemBridge` | `app.postmortem` | Observes WorkItemLifecycleEvent + CaseOutcomeEvent → qhorus channel messages |
| `IncidentConversationProjection` | `app.postmortem` | ConversationProjection subclass with PMETA sentinel and milestone status transitions |
| `FsiConversationRendererConfig` | `app.postmortem` | FSI-specific ConversationRendererConfig factory |
| `FsiEpistemicRule` | `app.postmortem` | EpistemicRule for post-mortem classification (ESTABLISHED/DISPUTED/PENDING) |
| `PostMortemResource` | `app.postmortem` | GET /api/postmortem/{caseId} |
| `FsiComplianceService` | `app.compliance` | Evaluates 5 regulatory requirements against live evidence |
| `ComplianceResource` | `app.compliance` | GET /api/compliance/status |
| `FsiGdprErasureService` | `app.gdpr` | Orchestrates erasure across CaseMemoryStore, CbrCaseMemoryStore, LedgerErasureService |
| `FsiErasureResult` | `app.gdpr` | Result record for multi-store erasure |
| `GdprErasureResource` | `app.gdpr` | POST /api/gdpr/erase |

### Modified Types

| Type | Change |
|---|---|
| `OvernightIncidentCaseHub` | Programmatic `postmortem` channel creation in `augment()` |
| `application.properties` | Add tokenisation and erasure-receipt config |

---

## Testing Strategy

### Post-mortem

- **IncidentPostMortemBridge tests:** verify each event type maps to correct ConversationPoint structure — milestone events create new points, binding/workitem/goal events append ThreadEntries to the current milestone's point
- **PostMortemResource test:** full lifecycle — create incident, advance through milestones, close, call GET /api/postmortem/{caseId}, verify rendered markdown contains milestone sections, agent actions, convergence signal, CBR context
- **ConversationRenderer integration:** verify FSI config renders correctly with all flags enabled (groupByTopic, epistemicStatus, convergenceSignal, obligationChain)

### Compliance grid

- **FsiComplianceService tests:** each evaluator method tested independently — mock ledger entries, routing records, CBR store, verify correct RequirementStatus for each scenario (CLOSED, PARTIAL, GAP)
- **Edge cases:** no ledger entries → GAP; entries without causal chains → PARTIAL; full chain → CLOSED

### GDPR erasure

- **FsiGdprErasureService tests:** verify all three stores are called in sequence, verify EraseRequest uses domain "fsitrading" (D16), verify idempotent retry (second call returns zeros)
- **Configuration test:** verify tokenisation enabled check (GE-20260531-46f8ab)
- **Integration test:** full lifecycle — create incident with agent memories, CBR cases, ledger entries → erase → verify all stores empty → retry → verify idempotent

### Test configuration

```properties
# Test profile additions
casehub.ledger.identity.tokenisation.enabled=true
casehub.ledger.erasure-receipt.enabled=true
```

Note: enabling tokenisation changes `actorId` to opaque tokens in ledger entries. Tests asserting raw actorId strings must use a separate `@TestProfile` or assert on tokens (GE-20260531-46f8ab caveat).

---

## REST Endpoints

| Method | Path | Purpose |
|---|---|---|
| `GET` | `/api/postmortem/{caseId}` | Generated post-mortem markdown |
| `GET` | `/api/compliance/status` | Compliance grid |
| `POST` | `/api/gdpr/erase` | GDPR erasure request |

---

## Platform Issues to File

| Repo | Issue | Description |
|---|---|---|
| casehub-engine | PlanEnsembleAnalyzer integration | `CbrRetrievalService` should invoke `PlanEnsembleAnalyzer.analyze()` after individual `PlanAdapter.adapt()` calls (deferred from D6, still no tracking issue) |

---

## References

- Replan spec §C6.4-6.6 — feature requirements for post-mortem, compliance, GDPR
- `ConversationRenderer.java` / `ConversationRendererConfig.java` / `ConversationState.java` / `RenderContext.java` — blocks conversation rendering
- `ConversationProjection.java` — builds ConversationState from message stream
- `CommonGroundAnalyser.java` — epistemic status analysis
- `TrustRoutingRequirement.java` / `RoutingDecisionRecord.java` / `RequirementStatus.java` — compliance evidence types
- `ActionRiskClassifier.java` — kill switch mechanism
- `StrategyEvaluationLedgerEntry.java` / `OrderExecutionLedgerEntry.java` — tamper-evident audit entries
- `LedgerErasureService.java` — ledger identity erasure with receipt
- `CaseMemoryStore.java` — episodic memory erasure
- `CbrCaseMemoryStore.java` / `EraseRequest.java` — domain-scoped CBR erasure
- `ErasureNotificationCaseMemoryStore.java` / `ErasureNotificationCbrCaseMemoryStore.java` — erasure event decorators
- `OutcomeWeightingCbrCaseMemoryStore.java` / `DefaultOutcomeWeightingFunction.java` — confidence→retrieval scoring
- GE-20260628-6599e6 — post-erasure actor-scoped receipt queries return empty
- GE-20260618-3e5f2d — ErasureReceiptLedgerEntry entity name collision (use foundation's, don't define own)
- GE-20260531-46f8ab — tokenisation.enabled required for LedgerErasureService.erase()
- GE-20260720-b7a8b9 — eraseEntity() crosses all CBR domains
- GE-20260818-907d8c — per-thread ambient compression in ChatObservationRenderer
- GE-20260612-17c161 — LedgerProcessor blocks em.persist() on LedgerEntry subclasses
- GE-20260612-de141c — LedgerProcessor domainContentBytes() requirement
- GE-20260511-b6f903 — LedgerEntry subclass required caller-set fields
