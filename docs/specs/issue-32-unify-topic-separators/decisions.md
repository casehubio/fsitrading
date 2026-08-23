# Design Decisions — #32 Topic Unification + #30 C5b Ops Centre

## D1: Topic separator convention

**Choice:** Colon (`:`) for all push topics
**Alternatives:**
- Slash (`/`) — would require rewriting TopicRegistry's trie, wildcard matching, and validation; no benefit
- Mixed (keep both) — broken: `topicSource` wildcard subscriptions can't match slash-delimited topics
**Rationale:** Not a style choice. TopicRegistry hardcodes `:` as the segment delimiter in `split(":", -1)` across all methods — trie insertion, wildcard matching (`*`, `**`), validation, and connection lookup. Slash-delimited topics are opaque single-segment strings with no wildcard support.
**Trade-offs:** 9 broadcast calls across 2 production files + 9 test assertions across 2 test files must change. No frontend impact — C4 hostPanel components use SSE, not topicSource. Replan spec §5.7 topic table uses slash notation and should be updated in a follow-up to prevent recurrence.
**Regression protection:** Add a topic format assertion in the app-level push listener base to catch future slash usage at dev time.
**Naming convention:** C1-C3 use singular entity names (`position`, `trust`, `market`). C4 slash topics use plural (`incidents`, `work-items`). On migration, keep the existing names (`incidents:`, `work-items:`) — renaming to singular would change every test and every consumer for no functional benefit. Document the convention: new topics use singular.
**Sources:** `io.casehub.pages.push.TopicRegistry` (decompiled from casehub-pages-push-0.2-SNAPSHOT.jar), `FsiIncidentNotifier.java` (5 calls), `FsiWorkItemPushListener.java` (4 calls), `FsiIncidentNotifierTest.java`, `FsiWorkItemPushListenerTest.java`
**Exploration:** quick
**Status:** revised (R1-02, R1-04, R1-05, R1-23)

## D2: Multi-page navigation

**Choice:** Nested `page()` calls — a root `page()` wrapping two child `page()` components, loaded by a single `loadSite()` call
**Alternatives:**
- URL-routed pages — separate HTML entry points, independent loadSite() calls; simple but no shared WebSocket connection or datasets
- Single page with mode toggle — one dock-workbench swapping panel sets; all 25 panels loaded at once, layout persistence conflated
- `tabs()` wrapper — provides explicit tab UI, but the pages `page()` builder already supports nested child pages natively and `LiveSite.navigate(path)` handles switching
**Rationale:** The `page()` builder validates no duplicate child page names and nests them as `slots.content`. `loadSite()` accepts any `Component`, builds a page path map via `buildPagePathMap(root)`, and `LiveSite.navigate(path)` switches between pages. Each child `page()` carries its own `PageOptions` with `datasets` and `save` — layout persistence is per-page via distinct `storageKey` values.
**Implementation risk:** How nested pages render navigation UI (tabs, sidebar, or nothing) is unverified. If the runtime doesn't auto-render navigation chrome, add an explicit `tabs()` or `sidebar()` wrapper around the child pages. Verify during implementation before committing to one approach.
**Trade-offs:** Both page trees exist in memory. Data source activation/deactivation on page switch needs verification.
**Sources:** `trading-desk.ts`, `@casehubio/pages-ui/dist/dsl/builders.d.ts:16` (`page()` signature), `@casehubio/pages-runtime/dist/site.d.ts` (`LiveSite.navigate`), `@casehubio/pages-runtime/dist/navigation.d.ts` (`buildPageIndex`, `computeCurrentPage`)
**Exploration:** quick
**Status:** revised (R1-07, R1-08)

## D3: Audit panel implementation

**Choice:** `hostPanel("audit-trail-viewer")` — blocks-ui component composition
**Alternatives:**
- DSL dataTable — flat ledger entry list without causality navigation; less capable
- Custom Lit component — full causality chain drill-down; significant frontend work beyond composition scope
**Rationale:** `audit-trail-viewer` exists in blocks-ui. hostPanel is the proven composition pattern (used for all 8 C4b panels). The component self-manages data fetching and rendering.
**Trade-offs:** Causality chain navigation depends on what the blocks-ui component supports out of the box. If insufficient, can upgrade to custom component in C6.
**Sources:** Replan spec §5.2 (Trading Desk layout), C4b spec §5 (hostPanel pattern)
**Exploration:** quick
**Status:** captured

## D4: Preferences panel implementation

**Choice:** `hostPanel("preferences-editor")` — blocks-ui component composition
**Alternatives:**
- DSL schema form — tighter pages data-binding but form DSL API unverified in this project
- Defer to C6 — trust routing config already accessible via REST, no UI urgency
**Rationale:** Same composition pattern as D3. Consistent with all other blocks-ui panel integrations. Wire to existing `GET/PUT /api/preferences/trust-routing`.
**Trade-offs:** Endpoint configuration (which REST path the component targets) may need HTML attributes — verify against blocks-ui API during implementation.
**Sources:** Replan spec §1.8, §5.2
**Exploration:** quick
**Status:** captured

## D5: Ops Centre incident dashboard

**Choice:** DSL composition using `metricGrid` + `eventTimeline` builders
**Alternatives:**
- Custom Lit component (`fsi-incident-dashboard`) — more control but adds a web component to build and maintain
- hostPanel with blocks-ui — would need a suitable blocks-ui component, which doesn't exist for this specific view
**Rationale:** The dashboard shows severity counts (metric tiles) + recent incident events (timeline). Both are expressible with existing DSL builders. Keeps C5b as pure composition — no custom frontend components. This deliberately overrides the replan spec §5.4 which assumed a custom `fsi-incident-dashboard` web component — the DSL builders are sufficient and avoid adding a maintenance surface.
**Trade-offs:** Less visual control than a custom component. Severity-count-by-category requires a pre-aggregated REST endpoint (`GET /api/incidents/summary`) returning `{severity, count}` rows — the DSL's `metricGrid` doesn't do client-side GROUP BY. If the endpoint can't provide this shape, fall back to `hostPanel("kpi-metric-row")` per the replan spec's original approach.
**Sources:** Replan spec §5.4 (fsi-incident-dashboard description), `trading-desk.ts` (existing metricGrid + eventTimeline usage)
**Exploration:** quick
**Status:** captured

## D6: Dataset sharing model

**Choice:** Per-page datasets — each `page()` declares its own datasets in `PageOptions`
**Alternatives:**
- Site-level shared datasets — single definition, cross-page `lookup()` via `dataset(id, fromPage?)`; theoretically cleaner but the pages-ui API is page-centric — each `page()` owns its `PageOptions.datasets`
**Rationale:** The pages DSL is page-centric — each `page()` carries its own `PageOptions` with `datasets` and `save`. Cross-page data access (`dataset(id, fromPage)`) exists but adds coupling. The blocks-ui `hostPanel` components in the Trading Desk that show ops data (notification-inbox) use SSE internally, not the page's dataset system — so no duplication concern for ops data in the Trading Desk.
**Trade-offs:** Incident and work-item `topicSource` subscriptions are only active on the Ops Centre page. The Trading Desk's notification-inbox handles its own SSE connection.
**Sources:** `trading-desk.ts` (current dataset binding pattern), `@casehubio/pages-ui/dist/dsl/builders.d.ts` (PageOptions type), `@casehubio/pages-runtime/dist/site.d.ts` (Site.dataset API)
**Exploration:** quick
**Status:** captured
