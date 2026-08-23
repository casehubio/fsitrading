# Topic Unification + C5b Ops Centre Composition — Design Spec

**Date:** 2026-08-23
**Issues:** #32 (topic separator unification), #30 (C5b Ops Centre composition)
**Branch:** issue-32-unify-topic-separators

---

## Summary

Two coordinated changes. First, unify all push topic separators from mixed colon/slash to colon-only — required by `TopicRegistry`'s trie infrastructure, not a style preference. Second, compose the Ops Centre page and complete the Trading Desk with audit + preferences panels, delivering the C5b milestone.

---

## §1 Topic Separator Unification (#32)

### §1.1 Problem

C1-C3 topics use colon separators (`position:AAPL`, `trust:MOMENTUM`, `market:ticks:AAPL`). C4 topics use slash separators (`incidents/{caseId}`, `work-items/summary`). `TopicRegistry` (casehub-pages-push) hardcodes `:` as its segment delimiter in `split(":", -1)` across all methods — trie insertion, wildcard matching (`*`, `**`), validation, and connection lookup. A slash-delimited topic is an opaque single-segment string with no wildcard support. This is already broken — not a future risk.

### §1.2 Changes

Migrate all slash-separated topics to colon-separated:

| Current topic | Unified topic | File |
|---|---|---|
| `incidents/{caseId}` | `incident:{caseId}` | FsiIncidentNotifier.java |
| `incidents/summary` | `incident:summary` | FsiIncidentNotifier.java |
| `work-items/{itemId}` | `work-item:{itemId}` | FsiWorkItemPushListener.java |
| `work-items/summary` | `work-item:summary` | FsiWorkItemPushListener.java |
| `work-items/{caseId}` | `work-item:{caseId}` | FsiWorkItemPushListener.java |

9 broadcast calls across 2 production files. 9 test assertions across 2 test files.

**Naming convention:** C1-C3 use singular entity names (`position`, `trust`, `routing`). C4 originally used plural (`incidents`, `work-items`). Since this migration already touches every broadcast call and test assertion, align C4 to the singular convention established by C1-C3. Zero marginal cost — the exact lines are already being edited.

### §1.3 Regression Protection

The colon separator is a platform invariant of `TopicRegistry`'s trie infrastructure, not an fsitrading convention. Validation belongs in `EventBroadcaster.broadcast()` alongside the existing wildcard check:

```java
// EventBroadcaster.broadcast() — existing wildcard check:
if (topic.contains("*")) {
    throw new IllegalArgumentException("broadcast topic must not contain wildcards: " + topic);
}
// Add slash rejection (one line):
if (topic.contains("/")) {
    throw new IllegalArgumentException("broadcast topic must use ':' separator, not '/': " + topic);
}
```

**Action:** File `casehubio/pages` issue for adding slash validation to `EventBroadcaster.broadcast()`. No app-level wrapper — the platform fix catches every app, zero chance of drift. The existing test assertions (9 topic strings verified per test run) provide regression protection in fsitrading until the platform fix ships.

### §1.4 Follow-up: Replan Spec §5.7

The replan spec §5.7 "Full Push Wiring" table uses slash notation for all topics — including 6 entries that were never slash-separated (`positions/{instrument}`, `pnl/{strategy}`, `trust/{strategy}`, `routing/latest`, `deliberation/{channelId}`, `market/*`). Out of scope for this implementation — the replan spec is a roadmap document, not a runtime artifact.

**Action:** File `casehubio/fsitrading` issue to update replan spec §5.7 topic table to colon notation.

---

## §2 Multi-Page Site Composition (#30)

### §2.1 Architecture

Refactor the single-page `trading-desk.ts` into a multi-page site with Trading Desk and Ops Centre pages.

```typescript
const site = page("FSI Trading",
  tabs(
    ["Trading Desk", page("Trading Desk", dockWorkbench({...}), { datasets: tradingDatasets, save: tradingSave })],
    ["Ops Centre", page("Ops Centre", dockWorkbench({...}), { datasets: opsDatasets, save: opsSave })],
  ),
);

loadSite(container, site);
```

Child pages must be wrapped in an interactive container (`tabs`, `sidebar`, etc.) — `page()` places all children in `slots: { content: children }`, so without a container, all child pages share the path segment `"content"` and the second overwrites the first in `buildPageIndex`. The `walkNavigate` function searches only `INTERACTIVE_TYPES` containers for navigation slot matching.

`tabs()` is the right choice for two reasons: (1) only two workspace pages, so a lightweight horizontal tab bar is sufficient; (2) dock-workbench uses left/right dock bars for panel organisation — a site-level `sidebar()` would compete visually with the dock bars.

### §2.2 File Structure

```
app/src/main/webui/src/
  index.ts              — loadSite entry point (modify)
  site.ts               — root site definition (create)
  trading-desk.ts       — Trading Desk page (modify — remove ops panels, add audit + prefs)
  ops-centre.ts         — Ops Centre page (create)
  topic-source.ts       — WebSocket data source (unchanged)
  panels/
    fsi-market-panel.ts — existing custom panel (unchanged)
```

### §2.3 Dataset Model

Per-page datasets. Each `page()` declares its own `DataSourceBinding[]` in `PageOptions.datasets`. No cross-page dataset sharing — blocks-ui `hostPanel` components self-manage their data via SSE.

**Trading Desk datasets** (existing — unchanged):
- `positions`, `kpis`, `heatmap`, `trust`, `routing`, `deliberations`, `strategies`, `orders`, `regime`

**Ops Centre datasets** (new):
- `incidents` — `fetchSource("/api/incidents"), refreshTime: "10s"` — incident list polled. Does NOT use `composite()` — see §8.5 for rationale.
- `incident-severity` — `fetchSource("/api/incidents/summary/severity"), refreshTime: "10s"` — severity counts polled (aggregated view, no matching push payload shape)
- `incident-status` — `fetchSource("/api/incidents/summary/status"), refreshTime: "10s"` — badge data polled (same rationale as severity)
- `strategies` — `fetchSource("/api/strategies")` — strategy list for Ops Centre strategies panel (dataTable)

### §2.4 Layout Persistence

Each page uses a distinct `storageKey`:
- Trading Desk: `storageKey: "trading-desk"` (existing)
- Ops Centre: `storageKey: "ops-centre"` (new)

Both share the same `createRestLayoutStore("/api/layout")` backend — the key differentiates saved layouts.

---

## §3 Trading Desk Changes

### §3.1 Remove Ops Panels

Move 8 C4b ops panels from the Trading Desk right zone to the Ops Centre (§4). Remove from `trading-desk.ts`:
- `caseExplorer`, `workItemInbox`, `workItemDetail`, `approvalGate`
- `slaIndicator`, `incidentTimeline`, `notificationInbox`, `slaBreachPolicy`

### §3.2 Add Audit Panel

```typescript
const audit: DockPanelConfig = {
  key: "audit",
  label: "Audit Trail",
  icon: "document",
  defaultOpen: false,
  content: hostPanel("audit-trail-viewer"),
};
```

Placed in the bottom zone alongside preferences. The `audit-trail-viewer` blocks-ui component self-manages data via its REST API contract. Verify the expected endpoint path (`/api/audit/*` or `/api/ledger/*`) against the component's API during implementation.

### §3.3 Add Preferences Panel

```typescript
const preferences: DockPanelConfig = {
  key: "preferences",
  label: "Preferences",
  icon: "settings",
  defaultOpen: false,
  content: hostPanel("preferences-editor"),
};
```

Placed in the bottom zone. Wired to `GET/PUT /api/preferences/trust-routing`. Verify the component accepts endpoint configuration via `panelProps` or HTML attributes during implementation.

### §3.4 Updated Trading Desk Layout

| Zone | Panels |
|------|--------|
| Centre | positions (dataTable), P&L heatmap (heatmapChart) |
| Left (2 zones) | strategies (fixed, split), market (hostPanel), KPIs (metricGrid) |
| Right (2 zones) | trust (dataTable), routing (dataTable), deliberation (eventTimeline), commitments (eventTimeline) |
| Bottom | audit (hostPanel), preferences (hostPanel) |
| Status bar | regime badge |

11 panels: 1 custom (market) + 8 DSL (positions, heatmap, strategies, KPIs, trust, routing, deliberation, commitments) + 2 hostPanel (audit, preferences).

---

## §4 Ops Centre Page

### §4.1 Layout

| Zone | Panels | Default open |
|------|--------|-------------|
| Centre | incident dashboard (DSL: metricGrid + eventTimeline) | yes |
| Left (2 zones) | cases (case-explorer, fixed), strategies (DSL: dataTable) | yes |
| Right (2 zones) | approvals (work-item-inbox), detail (work-item-detail), SLA countdown (sla-indicator), response channel (channel-activity) | approvals + detail + SLA: yes; channel: no |
| Bottom (2 zones) | timeline + SLA policy (blocks-timeline, sla-breach-policy), gate + alerts (approval-gate, notification-inbox) | timeline: yes; gate: no |
| Status bar | incident count badge + SLA status badge |

11 panels: 2 DSL compositions (incident dashboard, strategies) + 9 hostPanel (blocks-ui).

**Strategies panel:** Uses `dataTable({ lookup: lookup("strategies") })` bound to the page-level `strategies` dataset, matching the Trading Desk pattern. The `list-pane` blocks-ui component extends `DataSourceMixin` and requires a dataset connection — a bare `hostPanel("list-pane")` with no data binding renders empty.

**Channel-activity wiring:** The `channel-activity` component manages its own data via native qhorus WebSocket, but requires a `channelId` property to know which channel to display. The intended data flow: user selects an incident in `case-explorer` → the incident's response channel ID propagates to `channel-activity`. This cross-panel selection uses the pages event system (`emitPagesEvent`/`onPagesEvent`). Verify the exact wiring mechanism (panelProps, selectionSource attribute, or explicit event listener) during implementation.

**Deferred:** `context-gauge` (LLM context window usage display) was in the replan spec but the component does not exist in blocks-ui. Deferred until the component is built — see filed issue.

### §4.2 Incident Dashboard (Centre)

DSL composition replacing the replan spec's custom `fsi-incident-dashboard` component:

```typescript
const incidentDashboard: DockPanelConfig = {
  key: "incident-dashboard",
  label: "Incidents",
  icon: "alert",
  defaultOpen: true,
  zone: "top",
  content: split("vertical", [
    metricGrid({ direction: "row" },
      metric({ lookup: lookup("incident-severity"), subtype: "card" }),
    ),
    eventTimeline({
      lookup: lookup("incidents"),
      strategyKey: "chronological",
    }),
  ]),
};
```

The `metric` component expects tabular data (rows/columns via `DataSink`). The `incident-severity` dataset binds to `GET /api/incidents/summary/severity`, which returns flat `[{severity, count}]` rows — one metric card per severity level.

### §4.3 Status Bar

```typescript
const incidentCountBadge = badge({
  lookup: lookup("incident-status"),
  field: "totalActive",
  colorMap: { "0": "green", "default": "red" },
});

const slaStatusBadge = badge({
  lookup: lookup("incident-status"),
  field: "slaStatus",
  colorMap: { OK: "green", WARNING: "yellow", BREACHED: "red" },
});
```

The `incident-status` dataset binds to `GET /api/incidents/summary/status`, which returns a single-row table `[{totalActive, slaStatus}]`. Each badge extracts its field from the first row.

### §4.4 New REST Endpoints

| Method | Path | Purpose | Response shape |
|--------|------|---------|---------------|
| `GET` | `/api/incidents/summary/severity` | Severity counts for metric grid | Flat rows: `[{severity, count}]` |
| `GET` | `/api/incidents/summary/status` | Active total + SLA status for badges | Single-row: `[{totalActive, slaStatus}]` |

**Severity response:**
```json
[
  { "severity": "CRITICAL", "count": 0 },
  { "severity": "HIGH", "count": 1 },
  { "severity": "MEDIUM", "count": 1 },
  { "severity": "LOW", "count": 0 }
]
```

**Status response:**
```json
[{ "totalActive": 2, "slaStatus": "WARNING" }]
```

Both endpoints are added to `IncidentResource.java`. Both consume a new `IncidentStore.getSummary()` SPI method:

```java
// IncidentStore SPI addition:
IncidentSummary getSummary();

// New model record:
record IncidentSummary(
    long totalActive,
    String slaStatus,
    List<SeverityCount> bySeverity
) {
    record SeverityCount(String severity, long count) {}
}
```

The resource transforms the single SPI result into the two tabular response shapes. `JpaIncidentStore` implements `getSummary()` with a JPA aggregation query rather than fetching all records.

### §4.5 blocks-ui Component Registration

The Ops Centre reuses the same blocks-ui components already imported in `index.ts` (from C4b). The 8 existing imports cover most Ops Centre hostPanels.

New import for the Ops Centre response channel panel:

```typescript
import "@casehubio/blocks-ui-channel-activity";
```

This follows the existing flat import convention (`@casehubio/blocks-ui-{component}`) used throughout `index.ts`. The `channel-activity` component exists in blocks-ui.

---

## §5 WebSocket Push Topic Summary

Complete topic table after unification (all colon-separated):

| Topic | Payload type | Source | Consumer page |
|-------|-------------|--------|--------------|
| `position:{instrument}` | POSITION_UPDATE | FsiTradingPushListener | Trading Desk |
| `pnl:{strategyId}` | PNL_UPDATE | FsiTradingPushListener | Trading Desk |
| `trust:{strategyType}` | TRUST_UPDATE | FsiTradingPushListener | Trading Desk |
| `routing:latest` | ROUTING_UPDATE | FsiTradingPushListener | Trading Desk |
| `deliberation:active` | DeliberationPushPayload | FsiDeliberationPushListener | Trading Desk |
| `deliberation:{channelId}` | ConvergenceUpdate | FsiDeliberationPushListener/StateObserver | Trading Desk |
| `market:ticks:{instrument}` | PriceTick | FsiMarketPushService | Trading Desk |
| `market:bars:{instrument}` | OHLCV | FsiMarketPushService | Trading Desk |
| `market:trends:{instrument}` | TrendSummary | FsiMarketPushService | Trading Desk |
| `market:regime:{instrument}` | RegimeAssessment | FsiMarketPushService | Trading Desk |
| `market:narrative` | String | FsiMarketPushService | Trading Desk |
| `incident:{caseId}` | IncidentPushPayload | FsiIncidentNotifier | Ops Centre |
| `incident:summary` | IncidentPushPayload | FsiIncidentNotifier | Ops Centre |
| `work-item:{itemId}` | WorkItemPushPayload | FsiWorkItemPushListener | Ops Centre |
| `work-item:{caseId}` | WorkItemPushPayload (GateOpened) | FsiWorkItemPushListener | Ops Centre |
| `work-item:summary` | WorkItemPushPayload | FsiWorkItemPushListener | Ops Centre |

---

## §6 Testing Strategy

### §6.1 Topic Unification (#32)

**FsiIncidentNotifierTest** — update 5 topic assertions from `/` to `:` and from plural to singular. Verify `incident:{caseId}` and `incident:summary` patterns.

**FsiWorkItemPushListenerTest** — update 4 topic assertions from `/` to `:` and from plural to singular. Verify `work-item:{itemId}`, `work-item:{caseId}`, and `work-item:summary` patterns.

**Topic validation** — new test verifying the format assertion rejects slash-containing topics.

### §6.2 Ops Centre (#30)

**IncidentSummaryEndpoints** (unit) — verify `/api/incidents/summary/severity` returns correct severity counts and `/api/incidents/summary/status` returns totalActive + slaStatus.

**TypeScript build verification** — `npm run build` succeeds with multi-page site composition.

**Runtime verification** — start Quarkus dev server, verify:
1. Both pages render and tab navigation between them works
2. Ops Centre incident dashboard shows severity count cards from `/api/incidents/summary/severity`
3. Work-item inbox loads and filters correctly
4. Simulate an incident via `/api/incidents/simulate`, verify push events arrive on Ops Centre
5. Trading Desk still functions with audit + preferences panels available
6. Layout persistence works independently for each page

---

## §7 File Inventory

### Java (backend)

| File | Action | Description |
|------|--------|-------------|
| `app/.../incident/FsiIncidentNotifier.java` | Modify | Change 5 topic strings: `/` → `:` separator + plural → singular entity name |
| `app/.../push/FsiWorkItemPushListener.java` | Modify | Change 4 topic strings: `/` → `:` separator + plural → singular entity name |
| `api/.../spi/IncidentStore.java` | Modify | Add `getSummary()` method |
| `api/.../model/IncidentSummary.java` | Create | Summary record with severity counts + status |
| `app/.../incident/store/JpaIncidentStore.java` | Modify | Implement `getSummary()` with JPA aggregation query |
| `app/.../resource/IncidentResource.java` | Modify | Add `GET /api/incidents/summary/severity` and `GET /api/incidents/summary/status` endpoints |
| `app/test/.../incident/FsiIncidentNotifierTest.java` | Modify | Update 5 topic assertions |
| `app/test/.../push/FsiWorkItemPushListenerTest.java` | Modify | Update 4 topic assertions |
| `app/test/.../resource/IncidentResourceTest.java` | Modify | Add test for summary endpoint |

### TypeScript (frontend)

| File | Action | Description |
|------|--------|-------------|
| `app/src/main/webui/src/site.ts` | Create | Root site definition with nested pages |
| `app/src/main/webui/src/ops-centre.ts` | Create | Ops Centre page composition (11 panels) |
| `app/src/main/webui/src/trading-desk.ts` | Modify | Remove 8 ops panels, add audit + preferences, export as child page |
| `app/src/main/webui/src/index.ts` | Modify | Import site instead of tradingDesk, add new blocks-ui imports |

---

## §8 Known Limitations

1. **blocks-ui component API contracts** — `audit-trail-viewer` and `preferences-editor` endpoint configuration is unverified. If they can't be pointed at the fsitrading REST paths, they may need `panelProps` or fall back to DSL alternatives.

2. **Incident summary aggregation** — the summary endpoints are new. If the JPA aggregation query is expensive, consider caching or materialized views. For pre-release volume, a simple query is sufficient.

3. **topicSource is project-local** — `topic-source.ts` is not a platform primitive. Every pages-push consumer app must write its own WebSocket subscription adapter. This is a platform gap, not a design limitation — file as a pages issue if it becomes a pattern.

4. **context-gauge deferred** — the `context-gauge` component (LLM context window usage display) was in the replan spec's Ops Centre layout but does not exist in blocks-ui. Deferred until the component is built. See filed issue.

5. **`composite()` source data loss** — `composite(fetchSource, topicSource)` performs a clean handoff: after the REST snapshot, it disconnects REST entirely and connects the WebSocket source directly to the consumer. The WebSocket source's internal accumulator starts empty — the first push event sends a 1-row snapshot, replacing all REST-loaded data. This is a platform bug in `pages-data/composite-source.ts`, not a usage error. The Ops Centre avoids it by using polled `fetchSource` for all datasets. The Trading Desk's existing `composite()` datasets (`positions`, `trust`, `routing`, `deliberations`) have the same latent bug but it is masked by infrequent push events in synthetic testing. **Actions:** (1) File `casehubio/pages` issue for fixing `composite()` to seed the live source with the REST snapshot. (2) File `casehubio/fsitrading` issue for auditing Trading Desk `composite()` datasets once the platform fix ships.

---

## References

- `io.casehub.pages.push.TopicRegistry` — colon separator hardcoded in trie infrastructure
- `io.casehub.pages.push.EventBroadcaster` — no topic format validation
- `FsiIncidentNotifier.java` — 5 slash-separator broadcast calls
- `FsiWorkItemPushListener.java` — 4 slash-separator broadcast calls
- `trading-desk.ts` — current single-page dock-workbench composition
- `topic-source.ts` — project-local WebSocket data source
- `@casehubio/pages-ui/dist/dsl/builders.d.ts` — page(), dockWorkbench(), hostPanel() API
- `@casehubio/pages-runtime/dist/site.d.ts` — loadSite(), LiveSite.navigate()
- `@casehubio/pages-runtime/dist/navigation.d.ts` — buildPageIndex, computeCurrentPage
- Replan spec §5.1-5.7 — C5 Trading Desk/Ops Centre design
- Issue-28 spec — C5a dock-workbench infrastructure (predecessor)
- Issue-29 spec — C4b overnight ops panels (predecessor)
- Decision review R1-02 — blast radius correction (9 calls, not 6)
- Decision review R1-04 — replan spec §5.7 follow-up dependency
- Decision review R1-05 — regression protection suggestion
- Decision review R1-07 — nested page() is the correct multi-page mechanism
- Decision review R1-16 — D5 replan spec override acknowledged
