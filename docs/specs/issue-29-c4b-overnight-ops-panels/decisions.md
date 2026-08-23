## D0: Use blocks-ui components — no custom replacements

**Choice:** All 8 C4 panels use existing blocks-ui components registered via `hostPanel()`. Add `@casehubio/blocks-ui-*` npm dependency to `package.json` using the same `file:` protocol link pattern as pages packages.
**Alternatives:**
- Build custom Lit components for fsitrading — violates platform coherence; the components already exist, are tested, and are used by 3 other CaseHub apps
**Rationale:** Issue #29 explicitly says "wire blocks-ui panels." These are production-grade platform components: `work-item-inbox` has 11 test files, SSE-based live updates, keyboard shortcuts, accessibility mixins. Building custom replacements duplicates effort and diverges from platform patterns.
**Trade-offs:** Dependent on blocks-ui npm package availability via Maven unpack. If a panel needs fsitrading-specific behaviour that blocks-ui can't provide, a custom Lit component layered on top is acceptable — but only for genuinely non-generic behaviour.
**Sources:** Replan spec §4.8 (panel list), blocks-ui APPLICATIONS.md (3 apps use these components), issue #29 body ("Wire these blocks-ui panels")
**Exploration:** quick (surfaced by decision review — should have been the foundational decision)
**Status:** captured

## D1: Blocks-ui components via hostPanel — no combined custom panel

**Choice:** Register `work-item-inbox`, `work-item-detail`, and `approval-gate` as separate blocks-ui components via `hostPanel()`. Each is an independent panel in the dock-workbench. Selection events communicated via `pages-event` bus (`emitPagesEvent`/`onPagesEvent` from `@casehubio/blocks-ui-core`).
**Alternatives:**
- Combined custom `fsi-work-item-panel` — violates D0; builds a monolithic custom replacement for three production platform components
- `work-item-workbench` (blocks-ui pre-built composition) — handles inbox→detail selection internally; may be appropriate if dock-workbench zone layout doesn't need separate panels
**Rationale:** Separate panels give the dock-workbench full layout flexibility. The Ops Centre (C5b) needs these in different zones. blocks-ui components already handle their own data subscriptions via SSE/WorkItemEventTopics.
**Trade-offs:** Three `hostPanel()` registrations instead of one. Trivial cost.
**Sources:** Decision review R1-01 (blocks-ui components exist), WorkItemResource.java:49-62 (resolve endpoint), blocks-ui work-item-inbox source (SSEManager, WorkItemLifecycleEvent)
**Exploration:** quick (revised after decision review)
**Depends on:** D0
**Status:** revised

## D2: Typed IncidentPushPayload — event stream topics

**Choice:** Create `IncidentPushPayload` sealed interface with type-discriminated records, matching the `TradingPushPayload` pattern. `FsiIncidentNotifier` wraps CDI events before broadcasting. Incident topics use `accumulate: false` in topicSource — they are event streams, not accumulated state.
**Alternatives:**
- Leave raw CDI events — less ceremony, but no type discriminator for client-side dispatch
- Merge into TradingPushPayload — semantically conflates trading with incident ops
**Rationale:** Pattern consistency with `TradingPushPayload` and `DeliberationPushPayload`. Compile-time type safety in `FsiIncidentNotifier`. Client-side type dispatch for rendering different event kinds. The `incidents/{caseId}` topic carries three event types (created, SLA breach, resolved) — `accumulate: false` treats these as an event stream, avoiding the accumulator overwrite problem.
**Trade-offs:** Small wrapping ceremony in FsiIncidentNotifier. Topic subscribers must handle events individually rather than reading accumulated state.
**Sources:** TradingPushPayload.java:8-73 (pattern), FsiIncidentNotifier.java:25-49 (current raw broadcast), topic-source.ts:70-75 (accumulator logic), decision review R1-03 (rationale correction)
**Exploration:** quick (rationale revised after decision review)
**Status:** revised

## D3: Inline C4 panels in trading-desk.ts

**Choice:** Add C4 DockPanelConfig definitions directly in `trading-desk.ts` as `defaultOpen: false` panels. No separate ops-panels.ts module.
**Alternatives:**
- Separate ops-panels.ts with named exports — premature extraction; C5b will need different zone assignments, different defaultOpen values, different data source configurations anyway
**Rationale:** With blocks-ui components (D0/D1), the panel definitions are minimal — just `DockPanelConfig` objects with `hostPanel("component-name")`. At ~250 lines total, trading-desk.ts is well within single-module comfort. When C5b builds the Ops Centre, the extraction path is trivial (move configs, add exports) and the implementor has actual knowledge of what needs to diverge.
**Trade-offs:** C5b will need to extract. Trivial refactor when the time comes.
**Sources:** trading-desk.ts (170 lines currently), decision review R1-05 (premature extraction critique)
**Depends on:** D0, D1
**Exploration:** quick (revised after decision review)
**Status:** revised

## D4: Server-side SLA deadlines with push updates

**Choice:** Extend incident REST responses and push payloads to include `claimDeadline` and `completionDeadline` Instants. Deadlines are pushed not only at creation but also on WorkItem lifecycle changes (escalation, claim, completion) via a new `FsiWorkItemPushListener`.
**Alternatives:**
- Client-side derivation — duplicates domain logic in TypeScript, doesn't handle escalation-driven deadline changes
- Creation-only deadlines — stale after SLA tier escalation
**Rationale:** `IncidentSeverityDescriptor` already has deadline durations. Backend computes absolute timestamps at creation. When `FsiSlaBreachPolicy` escalates (CLAIM_EXPIRED → EscalateTo), the new candidate group may have different effective deadlines. The push mechanism must deliver updated deadlines so the sla-indicator panel shows correct countdowns.
**Trade-offs:** Requires new FsiWorkItemPushListener observing WorkItemLifecycleEvent. Small backend addition.
**Sources:** IncidentSeverityDescriptor (C4a spec §1.3), FsiSlaBreachPolicy (C4a spec §6.2), decision review R1-06 (deadline staleness gap)
**Exploration:** quick (extended after decision review)
**Status:** revised

## D5: FsiWorkItemPushListener for work-item lifecycle events

**Choice:** Create `FsiWorkItemPushListener` that observes `WorkItemLifecycleEvent` (CDI event from casehub-work) and broadcasts to push topics. Create `WorkItemPushPayload` sealed interface for type-discriminated work-item lifecycle events.
**Alternatives:**
- Rely on blocks-ui SSE only — blocks-ui's work-item-inbox uses SSE internally, but the sla-indicator and other panels need push delivery for deadline updates
- No push for work items — means the sla-indicator panel can't show live countdowns
**Rationale:** The incident push pattern (CDI event → observer → EventBroadcaster) is established. WorkItem lifecycle events (created, claimed, escalated, completed) need the same treatment. The sla-indicator panel depends on receiving updated deadline timestamps after escalation.
**Trade-offs:** Additional listener bean + sealed interface. Follows established pattern.
**Sources:** FsiTradingPushListener.java (established CDI→push pattern), casehub-work WorkItemLifecycleEvent, decision review R1-07 (push delivery gap)
**Depends on:** D4
**Exploration:** quick (surfaced by decision review)
**Status:** captured
