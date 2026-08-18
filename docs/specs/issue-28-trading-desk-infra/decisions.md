## D1: Panel strategy — pure DSL composition

**Choice:** Build the entire Trading Desk using the pages TypeScript DSL. All DSL gaps (heatmap, metric row, event timeline, master-detail, conditional row styling, grouped rows) have been filled by casehub-pages#317. No new custom web components needed — the existing `fsi-market-panel` (hostPanel) is the only custom element.
**Alternatives:**
- Custom web components for each panel (fsi-position-overview, fsi-pnl-heatmap, fsi-incident-dashboard as per replan §5.4) — more code, each manages its own data and rendering, duplicates DSL capabilities now available via pages#317
- Hybrid DSL + hostPanel — unnecessary since the DSL now covers all panel types
**Rationale:** The DSL provides `heatmapChart()`, `metricGrid()`, `eventTimeline()`, `masterDetail()`, `dataTable({ rowStyle, groupBy })` — all builders needed for the Trading Desk panels. Pure DSL composition is less code, more consistent, and automatically benefits from pages runtime improvements (layout persistence, cross-filter state, theme support). This deliberately supersedes the replan spec §5.4 which assumed 4 custom panels — that design was written before pages#317 filled the DSL gaps.
**Trade-offs:** Coupled to pages DSL API surface. Acceptable — pages is a platform dependency we already consume. If Bloomberg-level UX (real-time cell flashing, order book depth) is needed later, specific panels can be promoted to custom web components without changing the dock-workbench composition.
**Sources:** casehub-pages#317, pages/packages/pages-ui/src/dsl/builders.ts, fsitrading replan spec §C5, decision review R1-01
**Exploration:** quick
**Status:** revised (clarified spec supersession)

## D2: Scope — real-time for C1-C3 data with new push topics

**Choice:** C5a adds backend push for positions, P&L, trust, and routing alongside the UI composition. The desk is real-time for all C1-C3 data contracts. C4 push topics (incidents, work-items, notifications) are explicitly out of scope — they belong to C4b (#29).
**Alternatives:**
- REST-only with polling — simpler but adds latency and polling load
- Defer push to a follow-up — fragments the real-time UX across multiple branches
**Rationale:** The push infrastructure is proven — market data push (C2, delivered) and deliberation push (#26, delivered). Adding 3 new CDI event types and one push listener follows the established pattern. The CDI events (`PositionUpdatedEvent`, `TrustScoreChangedEvent`, `RoutingDecisionEvent`) don't exist yet and need to be designed, but the pattern is mechanical: record → fire CDI event → observer broadcasts.
**Trade-offs:** Larger scope (push backend + UI composition in one branch). Acceptable — the push work follows a proven pattern.
**Sources:** FsiDeliberationPushListener.java (pattern), OrderService.java (fill event source), PositionService.java (position update source), replan spec §5.7 (push topic table), decision review R1-02/R1-03
**Exploration:** quick
**Status:** revised (corrected scope boundary)

## D3: Site definition structure — single TypeScript file composition

**Choice:** Single `trading-desk.ts` file that calls `dockWorkbench()` with all panels composed inline. Data sources declared in `page()` options. The existing `src/index.ts` becomes the entry point that imports and renders the desk. TypeScript DSL — the replan spec §5.1 mentioned YAML dashboard files (`dashboards/trading-desk.yaml`), but the pages DSL is TypeScript and the Quinoa template uses TypeScript. YAML was a replan-era assumption; the DSL IS TypeScript.
**Alternatives:**
- Modular panel-per-file — each panel in its own file (panels/positions.ts, panels/trust.ts). More isolation but adds import boilerplate and fragmentation for a 12-panel desk that changes as a unit.
- YAML dashboard definitions — superseded by the TypeScript DSL; YAML would require a separate parser layer that doesn't exist.
**Rationale:** 12 panels with data bindings will likely reach 300-400 lines. If the file exceeds 400 lines during implementation, extract panel definitions into a `panels/` directory and import them. The dock-workbench zone assignments, panel ordering, and data source declarations are all cross-cutting — splitting them prematurely obscures the composition.
**Trade-offs:** May need extraction during implementation if complexity exceeds expectations. Mitigated by the clear extraction path (panels/ directory).
**Sources:** pages/templates/quinoa-host/src/index.ts (TypeScript pattern), replan spec §5.1-5.2, decision review R1-04/R1-05
**Exploration:** quick
**Status:** revised (clarified YAML supersession, realistic size estimate)

## D4: Push listener — single consolidated FsiTradingPushListener

**Choice:** One `FsiTradingPushListener` bean that `@Observes` all trading CDI events and broadcasts to their respective push topics. Two push listeners total: `FsiDeliberationPushListener` (deliberation lifecycle, #26) and `FsiTradingPushListener` (positions, P&L, trust, routing). P&L is derived from the same `PositionUpdatedEvent` — when `realizedPnl` is present, the listener broadcasts to both `position:{instrument}` and `pnl:{strategyId}` from the same event.
**Alternatives:**
- Separate listener per domain — FsiPositionPushListener, FsiTrustPushListener, FsiRoutingPushListener. More isolation but 3 thin observer beans add class proliferation for trivial methods.
**Rationale:** These are all trading-domain lifecycle events flowing to the same WebSocket infrastructure. One listener bean keeps the push wiring discoverable. The deliberation listener stays separate because it was built in a different branch and has a distinct event structure (sealed interface payloads).
**Trade-offs:** Single listener couples position/trust/routing push logic. Acceptable — each observer method is 3-5 lines calling `broadcaster.broadcast()`. If domains diverge significantly, split then.
**Depends on:** D2 (real-time scope)
**Sources:** FsiDeliberationPushListener.java (pattern), C2 D4 (CDI event precedent), decision review R1-06/R1-07
**Exploration:** quick
**Status:** revised (clarified P&L derivation from position event)

## D5: Data binding — composite (REST initial + WebSocket live)

**Choice:** Panels with push topics use `composite(fetchSource(url), wsSource(topic))` — REST for initial load, WebSocket for live updates. Panels without push (KPIs, strategies list) use `fetchSource(url)` with `triggerUrl` for refresh-on-event notification.
**Alternatives:**
- Push-only — empty panels on page load until first event. Poor UX.
- REST-only with polling — adds latency and server load for data that has push infrastructure.
**Rationale:** Composite binding gives instant initial render (REST fetch) plus real-time updates (WebSocket push). This is the pages-data architecture's intended use case — `composite()` source was designed for exactly this pattern.
**Trade-offs:** More complex wiring than pure REST. Acceptable — the composite source handles the coordination internally.
**Sources:** pages/packages/pages-data/src/datasource/sources/composite-source.ts, pages/packages/pages-data/src/datasource/sources/ws-source.ts, replan spec §5.7, decision review R1-08/R1-09
**Exploration:** quick
**Status:** revised (acknowledged known limitations)

## Known Limitations

### Composite source failure mode (R1-08)
If the WebSocket disconnects after the initial REST snapshot, the composite source has no fallback-to-REST path — panels show stale data with no recovery. Whether `wsSource` handles reconnection internally is a pages infrastructure question. For C5a (pre-release), this is acceptable. Production use requires either: pages-level WS reconnection, or a stale-data visual indicator in the dock-workbench.

### REST-to-WS event gap (R1-09)
Events occurring between the REST snapshot and WebSocket connection are potentially missed. The pages push infrastructure has `EventStore` with sequence numbers for catch-up replay. Whether `composite` source uses these for gap recovery is undocumented. For C5a, the gap is acceptable (events are low-frequency for positions/trust/routing). For high-frequency market data, the existing `fsi-market-panel` handles its own WebSocket connection with reconnection — no composite gap.

### Real-time cell updates (R1-01 assumption)
The pages DSL rendering pipeline's ability to provide Bloomberg-level UX (cell flashing, colour transitions on value changes) is untested. C5a delivers the data binding and composition infrastructure. Visual polish (flashing, transitions) can be added via CSS or promoted to custom web components if the DSL rendering doesn't support it.
