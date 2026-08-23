# C4b: Overnight Ops Panels Design

**Date:** 2026-08-22
**Issue:** #29
**Scope:** Wire 8 blocks-ui components to C4a backend REST endpoints and WebSocket push topics. Add typed incident/work-item push payloads. Place panels in the existing trading desk dock-workbench. No custom Lit components.

---

## Summary

The C4a backend provides incident management (case definition, SLA, risk classifier, agents, REST endpoints) and push broadcasting (4 CDI event types across 3 topics). The C5a trading desk provides the dock-workbench infrastructure, Quinoa build chain, and the `topicSource()` WebSocket data source.

C4b bridges these: register blocks-ui components, wire data sources to C4a endpoints and push topics, fix the push payload inconsistency, and add the missing work-item push delivery mechanism.

---

## §1 blocks-ui Dependency

Add `@casehubio/blocks-ui-work`, `@casehubio/blocks-ui-case`, and `@casehubio/blocks-ui-core` npm packages using the same Maven unpack + `file:` protocol link pattern as pages packages.

### Maven (app/pom.xml)

Add the blocks-ui npm Maven artifact:

```xml
<dependency>
  <groupId>io.casehub</groupId>
  <artifactId>casehub-blocks-ui-npm</artifactId>
  <version>${casehub-blocks.version}</version>
  <type>tar.gz</type>
  <classifier>npm</classifier>
  <scope>provided</scope>
</dependency>
```

The existing `maven-dependency-plugin` unpack execution extracts npm packages into `.casehub-packages/`. Add the blocks-ui tarballs to the same execution.

### npm (app/src/main/webui/package.json)

```json
{
  "dependencies": {
    "@casehubio/blocks-ui-core": "file:../../.casehub-packages/blocks-ui-core",
    "@casehubio/blocks-ui-work": "file:../../.casehub-packages/blocks-ui-work",
    "@casehubio/blocks-ui-case": "file:../../.casehub-packages/blocks-ui-case"
  }
}
```

### Component Registration (index.ts)

Import the blocks-ui component modules to trigger `customElements.define()`:

```typescript
import "@casehubio/blocks-ui-work/work-item-inbox";
import "@casehubio/blocks-ui-work/work-item-detail";
import "@casehubio/blocks-ui-work/approval-gate";
import "@casehubio/blocks-ui-work/sla-indicator";
import "@casehubio/blocks-ui-work/sla-breach-policy";
import "@casehubio/blocks-ui-case/case-explorer";
import "@casehubio/blocks-ui-work/notification-inbox";
import "@casehubio/blocks-ui-core/blocks-timeline";
```

The exact import paths depend on how blocks-ui packages export their components. Verify against the actual package entry points during implementation.

---

## §2 IncidentPushPayload — Typed Push Payloads

Replace raw CDI event broadcasting in `FsiIncidentNotifier` with typed payloads matching the `TradingPushPayload` pattern.

### IncidentPushPayload.java (app module)

```java
package io.casehub.fsitrading.app.push;

public sealed interface IncidentPushPayload {

    String type();

    record IncidentCreated(
            String type,
            UUID caseId,
            String severity,
            String eventType,
            List<String> instruments,
            String description,
            Instant claimDeadline,
            Instant completionDeadline,
            Instant createdAt) implements IncidentPushPayload {
        public IncidentCreated(UUID caseId, String severity, String eventType,
                               List<String> instruments, String description,
                               Instant claimDeadline, Instant completionDeadline,
                               Instant createdAt) {
            this("INCIDENT_CREATED", caseId, severity, eventType, instruments,
                 description, claimDeadline, completionDeadline, createdAt);
        }
    }

    record SlaBreached(
            String type,
            UUID caseId,
            UUID taskId,
            String breachType,
            int tier,
            String severity) implements IncidentPushPayload {
        public SlaBreached(UUID caseId, UUID taskId, String breachType,
                           int tier, String severity) {
            this("SLA_BREACHED", caseId, taskId, breachType, tier, severity);
        }
    }

    record IncidentResolved(
            String type,
            UUID caseId,
            Instant resolvedAt) implements IncidentPushPayload {
        public IncidentResolved(UUID caseId, Instant resolvedAt) {
            this("INCIDENT_RESOLVED", caseId, resolvedAt);
        }
    }

}
```

### FsiIncidentNotifier update

Replace raw CDI event broadcasts with typed payloads:

```java
void onIncidentCreated(@Observes IncidentCreatedEvent event) {
    var payload = new IncidentPushPayload.IncidentCreated(
            event.caseId(), event.severity().name(), event.eventType().name(),
            event.instruments(), event.description(),
            event.claimDeadline(), event.completionDeadline(),
            event.createdAt());
    broadcaster.broadcast("incidents/" + event.caseId(), payload);
    broadcaster.broadcast("incidents/summary", payload);
}
```

Timestamps come from the CDI event records, not computed independently in the notifier. Extend `IncidentCreatedEvent` to carry `createdAt`, `claimDeadline`, `completionDeadline` (computed by `FsiIncidentTrigger` at creation time). Extend `IncidentResolvedEvent` to carry `resolvedAt`. This eliminates clock skew between the database record and the push payload.

### Push Topic Summary (incidents)

| Topic | Payload type | Accumulate | Source |
|-------|-------------|------------|--------|
| `incidents/{caseId}` | `INCIDENT_CREATED`, `SLA_BREACHED`, `INCIDENT_RESOLVED` | false | FsiIncidentNotifier |
| `incidents/summary` | `INCIDENT_CREATED`, `INCIDENT_RESOLVED` | false | FsiIncidentNotifier |

All incident topics use `accumulate: false` — they are event streams, not accumulated state.

---

## §3 WorkItemPushPayload and FsiWorkItemPushListener

The platform's `casehub-work` fires `WorkItemLifecycleEvent` as CDI events. C4b observes these and broadcasts to push topics so the sla-indicator panel receives updated deadlines after escalation.

### WorkItemPushPayload.java (app module)

```java
package io.casehub.fsitrading.app.push;

public sealed interface WorkItemPushPayload {

    String type();

    record WorkItemCreated(
            String type,
            UUID itemId,
            String title,
            String itemType,
            String candidateGroups,
            String status,
            Instant claimDeadline,
            Instant expiresAt,
            Instant createdAt) implements WorkItemPushPayload {
        public WorkItemCreated(UUID itemId, String title, String itemType,
                               String candidateGroups, String status,
                               Instant claimDeadline, Instant expiresAt,
                               Instant createdAt) {
            this("WORK_ITEM_CREATED", itemId, title, itemType, candidateGroups,
                 status, claimDeadline, expiresAt, createdAt);
        }
    }

    record WorkItemAssigned(
            String type,
            UUID itemId,
            String assignedTo,
            Instant assignedAt) implements WorkItemPushPayload {
        public WorkItemAssigned(UUID itemId, String assignedTo, Instant assignedAt) {
            this("WORK_ITEM_ASSIGNED", itemId, assignedTo, assignedAt);
        }
    }

    record GateOpened(
            String type,
            UUID itemId,
            UUID caseId,
            String actionDescription,
            String riskLevel,
            String candidateGroups) implements WorkItemPushPayload {
        public GateOpened(UUID itemId, UUID caseId, String actionDescription,
                          String riskLevel, String candidateGroups) {
            this("GATE_OPENED", itemId, caseId, actionDescription, riskLevel, candidateGroups);
        }
    }

    record WorkItemEscalated(
            String type,
            UUID itemId,
            String fromGroup,
            String toGroup,
            Instant claimDeadline,
            Instant expiresAt) implements WorkItemPushPayload {
        public WorkItemEscalated(UUID itemId, String fromGroup, String toGroup,
                                 Instant claimDeadline, Instant expiresAt) {
            this("WORK_ITEM_ESCALATED", itemId, fromGroup, toGroup,
                 claimDeadline, expiresAt);
        }
    }

    record WorkItemCompleted(
            String type,
            UUID itemId,
            String outcome,
            String resolution,
            Instant completedAt) implements WorkItemPushPayload {
        public WorkItemCompleted(UUID itemId, String outcome,
                                 String resolution, Instant completedAt) {
            this("WORK_ITEM_COMPLETED", itemId, outcome, resolution, completedAt);
        }
    }
}
```

### FsiWorkItemPushListener.java

Follows the `FsiTradingPushListener` testability pattern: wrap `EventBroadcaster` behind a functional interface with a package-private test constructor.

```java
@ApplicationScoped
public class FsiWorkItemPushListener {

    private final FsiMarketPushService.PushBroadcaster broadcaster;

    @Inject
    public FsiWorkItemPushListener(EventBroadcaster eventBroadcaster) {
        this.broadcaster = eventBroadcaster::broadcast;
    }

    FsiWorkItemPushListener(FsiMarketPushService.PushBroadcaster broadcaster) {
        this.broadcaster = broadcaster;
    }

    void onWorkItemLifecycle(@Observes WorkItemLifecycleEvent event) {
        if (!"fsitrading".equals(event.scope())) return;

        WorkItemPushPayload payload = switch (event.eventType()) {
            case CREATED -> new WorkItemPushPayload.WorkItemCreated(...);
            case ASSIGNED -> new WorkItemPushPayload.WorkItemAssigned(...);
            case ESCALATED -> new WorkItemPushPayload.WorkItemEscalated(...);
            case COMPLETED, REJECTED -> new WorkItemPushPayload.WorkItemCompleted(...);
            default -> null;
        };
        if (payload != null) {
            broadcaster.broadcast("work-items/" + event.workItemId(), payload);
            broadcaster.broadcast("work-items/summary", payload);
        }
    }

    void onGateOpened(@Observes GateOpenedEvent event) {
        var payload = new WorkItemPushPayload.GateOpened(
                null, event.caseId(), event.actionDescription(),
                event.riskLevel(), event.candidateGroups());
        broadcaster.broadcast("work-items/summary", payload);
    }
}
```

Key corrections from spec review:
- **Scope filter:** `event.scope()` check ensures only fsitrading work items are broadcast. Platform fires `WorkItemLifecycleEvent` for all case types system-wide.
- **`event.eventType()`** not `event.type()` — the latter returns a CloudEvent type string, not the `WorkEventType` enum.
- **`ASSIGNED`** not `CLAIMED` — `CLAIMED` does not exist in `WorkEventType`.
- **`event.workItemId()`** not `event.workItem().id()` — the latter is null-unsafe for wire-deserialized events.
- **GateOpened moved here** from `IncidentPushPayload` — it creates a work item, so it belongs in the work-item domain. Broadcast to `work-items/summary` so aggregate views stay current.
- **PushBroadcaster functional interface** — enables unit testing without mocking `EventBroadcaster` (which has complex constructor dependencies).

The `ESCALATED` variant carries updated `claimDeadline` and `expiresAt` timestamps so the sla-indicator panel can refresh its countdown.

### Push Topic Summary (work-items)

| Topic | Payload types | Accumulate | Source |
|-------|-------------|------------|--------|
| `work-items/{itemId}` | All 4 lifecycle types | false | FsiWorkItemPushListener |
| `work-items/summary` | All 4 lifecycle types | false | FsiWorkItemPushListener |

---

## §4 Server-Side SLA Deadlines

Extend `IncidentRecord` (api module) with deadline timestamps:

```java
public record IncidentRecord(
        UUID caseId,
        IncidentSeverity severity,
        MarketEventType eventType,
        List<String> instruments,
        String status,
        Instant createdAt,
        Instant resolvedAt,
        Instant claimDeadline,      // NEW
        Instant completionDeadline  // NEW
) {}
```

Computed at incident creation time: `Instant.now().plus(descriptor.claimDeadline())` and `Instant.now().plus(descriptor.completionDeadline())`. Stored in `IncidentEntity` and returned by the REST API. The push payload (§2) carries the same timestamps.

The `GET /api/work-items` response already includes WorkItem fields. If the platform's `WorkItem` record doesn't carry deadline timestamps, the `WorkItemResource` response DTO may need extending — verify against the actual `WorkItem` API during implementation.

---

## §5 Trading Desk Panel Composition

Add 8 DockPanelConfig entries to `trading-desk.ts`. All use `hostPanel()` — blocks-ui components are self-contained and manage their own data fetching (REST + SSE). No pages-data dataset bindings needed for these panels.

**Temporary placement:** These panels are placed in the Trading Desk's right zone as `defaultOpen: false` secondary panels. C5b (#30) will move them to the dedicated Ops Centre page with proper zone assignments and `defaultOpen: true` where appropriate.

### Panel Definitions

```typescript
const caseExplorer: DockPanelConfig = {
  key: "cases",
  label: "Cases",
  icon: "folder",
  defaultOpen: false,
  content: hostPanel("case-explorer"),
};

const workItemInbox: DockPanelConfig = {
  key: "work-items",
  label: "Work Items",
  icon: "inbox",
  defaultOpen: false,
  content: hostPanel("work-item-inbox"),
};

const workItemDetail: DockPanelConfig = {
  key: "work-item-detail",
  label: "Work Item Detail",
  icon: "document",
  defaultOpen: false,
  content: hostPanel("work-item-detail"),
};

const approvalGate: DockPanelConfig = {
  key: "approval-gate",
  label: "Approval Gate",
  icon: "shield",
  defaultOpen: false,
  content: hostPanel("approval-gate"),
};

const slaIndicator: DockPanelConfig = {
  key: "sla",
  label: "SLA Status",
  icon: "clock",
  defaultOpen: false,
  content: hostPanel("sla-indicator"),
};

const incidentTimeline: DockPanelConfig = {
  key: "incident-timeline",
  label: "Incident Timeline",
  icon: "timeline",
  defaultOpen: false,
  content: hostPanel("blocks-timeline"),
};

const notificationInbox: DockPanelConfig = {
  key: "notifications",
  label: "Notifications",
  icon: "bell",
  defaultOpen: false,
  content: hostPanel("notification-inbox"),
};

const slaBreachPolicy: DockPanelConfig = {
  key: "sla-policy",
  label: "SLA Policy",
  icon: "settings",
  defaultOpen: false,
  content: hostPanel("sla-breach-policy"),
};
```

### Dock-Workbench Placement

Add to the right zone of the existing trading desk (secondary panels, defaultOpen: false). The user can open them on demand via the dock panel list.

```typescript
right: {
  zones: 2,
  panels: [trust, routing, deliberation, commitments,
           caseExplorer, workItemInbox, workItemDetail,
           approvalGate, slaIndicator, incidentTimeline,
           notificationInbox, slaBreachPolicy],
},
```

### blocks-ui Component Configuration

blocks-ui components accept configuration via HTML attributes and properties. The exact attribute API depends on each component's public interface — verify during implementation. Key configuration points:

| Component | Key config | Data contract |
|-----------|-----------|---------------|
| `work-item-inbox` | `candidate-groups="fsi-oncall"`, work item type filter | `GET /api/work-items` |
| `work-item-detail` | Selected item from inbox via pages-event | `GET /api/work-items` (selected item) |
| `approval-gate` | `resolve-endpoint="/api/work-items/{id}/resolve"` | `POST /api/work-items/{id}/resolve` |
| `sla-indicator` | `topic="work-items/*"` for deadline updates | Push: `work-items/{itemId}` |
| `sla-breach-policy` | Static SLA tier display | Hardcoded or `GET /api/sla-config` |
| `case-explorer` | Incident list with status filter | `GET /api/incidents` |
| `notification-inbox` | `topics="incidents/*,work-items/*"` | Push: all incident/work-item topics |
| `blocks-timeline` | `source-endpoint="/api/incidents/{caseId}/timeline"` | `GET /api/incidents/{caseId}/timeline` |

---

## §6 Testing Strategy

### Java Backend

**IncidentPushPayloadTest** (unit) — type discriminator assertions for all 4 payload records. Pattern: `TradingPushPayloadTest`.

**WorkItemPushPayloadTest** (unit) — type discriminator assertions for all 4 lifecycle records.

**FsiIncidentNotifierTest** (unit, create new) — verify FsiIncidentNotifier wraps CDI events in IncidentPushPayload records before broadcasting. Assert correct type discriminators and deadline fields. Covers all 4 event handlers.

**FsiWorkItemPushListenerTest** (unit) — `BroadcastCapture` list, fire WorkItemLifecycleEvent CDI events, assert correct topics and payload types. Pattern: `FsiTradingPushListenerTest`.

**IncidentRecord deadline fields** — verify claimDeadline and completionDeadline are computed and stored correctly. Update existing IncidentResourceTest.

### TypeScript Frontend

**Build verification** — `npm run build` succeeds with blocks-ui imports and no TypeScript errors.

**Runtime verification** — start Quarkus dev server, verify:
1. Trading desk renders with C4 panels available in the panel list
2. Opening case-explorer shows incident list from `/api/incidents`
3. work-item-inbox loads and filters by candidate group
4. Simulate an incident via `/api/incidents/simulate`, verify push events arrive
5. SLA indicator shows countdown from deadline timestamps

---

## §7 File Inventory

### Java (backend)

| File | Action | Description |
|------|--------|-------------|
| `app/.../push/IncidentPushPayload.java` | Create | Sealed interface with 3 type-discriminated records |
| `app/.../push/WorkItemPushPayload.java` | Create | Sealed interface with 5 records (4 lifecycle + GateOpened) |
| `api/.../model/IncidentCreatedEvent.java` | Modify | Add createdAt, claimDeadline, completionDeadline fields |
| `api/.../model/IncidentResolvedEvent.java` | Modify | Add resolvedAt field |
| `app/.../push/FsiWorkItemPushListener.java` | Create | CDI observer for WorkItemLifecycleEvent → push |
| `app/.../incident/FsiIncidentNotifier.java` | Modify | Wrap CDI events in IncidentPushPayload before broadcasting |
| `api/.../model/IncidentRecord.java` | Modify | Add claimDeadline, completionDeadline fields |
| `app/.../incident/store/IncidentEntity.java` | Modify | Add deadline columns |
| `app/.../incident/store/JpaIncidentStore.java` | Modify | Map deadline fields |
| `app/.../incident/FsiIncidentTrigger.java` | Modify | Compute deadline timestamps at creation |
| `app/test/.../push/IncidentPushPayloadTest.java` | Create | Payload type tests |
| `app/test/.../push/WorkItemPushPayloadTest.java` | Create | Payload type tests |
| `app/test/.../push/FsiWorkItemPushListenerTest.java` | Create | Push listener tests |
| `app/test/.../incident/FsiIncidentNotifierTest.java` | Create | Test all 4 event handlers with typed payloads |
| `app/test/.../incident/IncidentResourceTest.java` | Modify | Assert deadline fields in responses |

### Flyway Migration

| File | Action | Description |
|------|--------|-------------|
| `app/.../db/migration/V107__incident_deadlines.sql` | Create | Add `claim_deadline` and `completion_deadline` columns to `fsi_incident` |

V107 follows sequentially after V106 (C4a's `fsi_incident_timeline`).

### TypeScript (frontend)

| File | Action | Description |
|------|--------|-------------|
| `app/src/main/webui/package.json` | Modify | Add blocks-ui-core, blocks-ui-work, blocks-ui-case deps |
| `app/src/main/webui/src/index.ts` | Modify | Import blocks-ui component modules |
| `app/src/main/webui/src/trading-desk.ts` | Modify | Add 8 DockPanelConfig entries + data sources |

### Configuration

| File | Action | Description |
|------|--------|-------------|
| `app/pom.xml` | Modify | Add casehub-blocks-ui-npm dependency + unpack config |

---

## §8 Known Limitations

1. **blocks-ui component attribute API** — the exact configuration attributes for each component need to be verified against the actual package exports. The spec documents expected config points; implementation may reveal different APIs.

2. **blocks-ui SSE vs WebSocket** — blocks-ui's `work-item-inbox` uses SSE internally via `SSEManager`. If it handles its own data subscription, the `topicSource` WebSocket data source may be redundant for that component. Verify during implementation whether the component needs external data wiring or self-manages.

3. **sla-breach-policy data source** — the SLA tier configuration (CRITICAL=5min, HIGH=15min, MEDIUM=60min) is in `IncidentSeverityDescriptor` (Java). There's no REST endpoint exposing this config. If the `sla-breach-policy` component needs dynamic config, add a `GET /api/sla-config` endpoint. Otherwise, it renders static display from the component's built-in defaults.

4. **Topic path separators** — C4a uses `/` in topic paths (`incidents/{caseId}`) while C1-C3 use `:` (`position:{instrument}`, `trust:{strategyType}`). Both work with `EventBroadcaster` — the separator is arbitrary. But `topicSource` wildcard matching must match the actual separator used. Keep C4a's `/` convention since it's already in production code. File a GitHub issue to unify all topic separators before C5b composes both conventions in one file — two matching behaviors in the same subscription system is convention drift waiting to become a bug.

---

## References

- `IncidentResource.java` — C4a REST endpoints (7 endpoints across incidents and work-items)
- `WorkItemResource.java` — C4a work item resolve endpoint
- `FsiIncidentNotifier.java` — current raw CDI event broadcasting (to be updated)
- `TradingPushPayload.java:8-73` — established typed push payload pattern
- `FsiTradingPushListener.java` — established CDI→push observer pattern
- `topic-source.ts` — WebSocket data source with accumulate/keyField
- `trading-desk.ts` — C5a dock-workbench composition (pattern to follow)
- C4a spec §1.3 — IncidentSeverityDescriptor: severity, deadlines, candidate groups
- C4a spec §6.2 — FsiSlaBreachPolicy: 2-tier escalation, Exhausted terminal
- C4a spec §7.1 — Push topic hierarchy: incidents/{caseId}, incidents/summary, work-items/{itemId}
- C5a spec §1, §3, §4, §6 — dependency setup, push payloads, push listener, composition
- Replan spec §4.8 — C4 panel list (8 blocks-ui components)
- Replan spec §5.3 — Ops Centre layout (C5b scope, informs panel placement)
- Decision review R1-01 — blocks-ui components are production-grade (3 apps, 11 test files for inbox)
- Decision review R1-03 — accumulator rationale correction, topic mode decision
- Decision review R1-06 — deadline staleness gap, WorkItem push delivery mechanism
- Spec review R1-03 — `event.eventType()` not `event.type()`, `ASSIGNED` not `CLAIMED`
- Spec review R1-04 — `event.workItemId()` null-safety
- Spec review R1-05 — scope filter for fsitrading work items only
- Spec review R1-06 — GateOpened moved from IncidentPushPayload to WorkItemPushPayload
- Spec review R1-07 — hostPanel components self-manage data, unused data sources removed
- Spec review R1-08 — CDI events extended to carry source timestamps
- Spec review R1-12 — PushBroadcaster functional interface for testability
