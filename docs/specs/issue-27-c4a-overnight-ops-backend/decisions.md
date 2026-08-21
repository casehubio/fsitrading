## D1: Notification mechanism

**Choice:** Two-layer approach: (1) UI push via existing `EventBroadcaster`/`FsiPushWebSocket` for real-time dashboard updates (topics: `incidents/*`, `work-items/*`). (2) Human notification via platform `NotificationDispatcher` — add `casehub-platform-notifications` dependency, fire domain CDI events (`IncidentCreatedEvent`, `GateOpenedEvent`, `SlaBreachEvent`, `IncidentResolvedEvent`), let `SubscriptionEngine` match and route to delivery channels.
**Alternatives:**
- App-level `FsiIncidentNotifier` with custom push — duplicates platform notification infrastructure (violates boundary rules)
- CDI events only — too thin, scatters notification logic across consumers
**Rationale:** The platform provides a full notification pipeline (SubscriptionEngine → NotificationDispatcher → TargetResolver → ChannelRouter → delivery). Building custom notification logic in the app layer violates the boundary rule: "Do not duplicate notification infrastructure." UI push and human notification are distinct concerns — push updates the dashboard, notifications alert the on-call trader.
**Trade-offs:** Adds dependency on notification platform modules. Requires registering FSI event types with `EventTypeRegistry`.
**Sources:** Platform notifications doc (parent/docs/platform/notifications.md), boundary rules (parent/docs/platform/boundary-rules.md), casehub-platform-notifications artifact (published), FsiPushWebSocket (existing C2/C3 push for UI)
**Exploration:** quick
**Status:** revised (R1-02: premise corrected — NotificationDispatcher exists as published artifact)

## D2: Incident trigger origin

**Choice:** New `FsiIncidentTrigger` bean with two ingest paths: (1) observes C2's CDI events (`TrendReversalDetected`, `RegimeChanged`) for market-detected anomalies. (2) REST endpoint `POST /api/incidents/external` for operational events (`COUNTERPARTY_FAILURE`, `MARGIN_CALL`) that arrive via broker webhook or manual trigger. Both paths apply incident-classification logic (severity, time-of-day, position exposure) before creating the case via the engine.
**Alternatives:**
- Extend `FsiMarketEventDetector` — couples C2 (detection) with C4 (response), violates single responsibility
- C2 events only — no coverage for operational events that have no market data signature
**Rationale:** Detection and response are different concerns. C2 answers "what happened in the market"; C4 decides whether that warrants an incident case. Operational events (counterparty failure, margin call) arrive externally — they need a separate ingest path that bypasses the market data pipeline entirely.
**Trade-offs:** Two ingest paths to maintain. Worth it — operational events are fundamentally different from market-detected anomalies.
**Sources:** FsiMarketEventDetector.java (existing C2 detector), replan spec §4.1, §4.3 (conditional routing includes operational events)
**Exploration:** quick
**Status:** revised (R1-06: added external ingest path for operational events)

## D3: HTN agent implementation strategy

**Choice:** Graduated approach — deterministic actions are rule-based, judgment calls use LLM.
- **Decomposition:** Static methods for the three known severity patterns (CRITICAL/HIGH/MEDIUM). LLM fallback via `HybridDecomposition` for novel scenarios that don't match any static method.
- **Execution:** Rule-based agents for mechanical tasks (emergency-halt, close-positions, halt-and-wait). LLM agents (fast/cheap model) for judgment tasks (assess novel pattern, decide exposure reduction, sentiment analysis).
**Alternatives:**
- Rule-based only — misses novel scenarios, can't reason about unprecedented market conditions
- LLM throughout with structured output + rule-based validation — consistent with feedback_llm-over-rules but adds latency for truly mechanical actions
**Rationale:** User explicitly chose this approach: "if its simple enough we can be purely rule based. but there may be times when it needs 2 or even 3." Overnight ops demands speed for known scenarios (rules) and adaptability for novel ones (LLM). Note: the reviewer raised a valid alternative (LLM throughout with guardrails) — filed for future consideration but user preference overrides for this iteration.
**Trade-offs:** Two agent implementation patterns to maintain. "Mechanical" boundary is judgment-dependent — review noted that even emergency-halt may need market-state reasoning during flash crash recovery.
**Sources:** Replan spec §4.2 (HybridDecomposition), user direction (conversation)
**Exploration:** quick
**Depends on:** D2 (incident trigger provides severity classification to decomposition)
**Status:** captured (R1-04: noted alternative, user preference confirmed)

## D4: MarketEventType enum extension

**Choice:** Add `COUNTERPARTY_FAILURE` and `MARGIN_CALL` to the existing `MarketEventType` enum. They represent real overnight risk scenarios and complete the conditional routing table from the spec.
**Alternatives:**
- Keep existing types only — limits conditional routing to 5 types, leaves spec gaps unfilled
- Sealed interface hierarchy (`MarketDataEvent`, `DetectedEvent`, `OperationalEvent`) — cleaner type system but requires refactoring C1-C3 code that consumes the enum
**Rationale:** Both event types are genuine trading domain concepts with distinct response patterns. The enum already mixes raw market events (PRICE_TICK) with derived events (FLASH_CRASH) — adding operational events is pragmatic for pre-release. A sealed hierarchy is the right long-term direction but out of scope for C4a.
**Trade-offs:** Enum conflates three event source domains (market data, detected patterns, operational). Acceptable for pre-release; note as tech debt for future cleanup. `SyntheticMarketDataProvider` will need extension to trigger operational events for testing.
**Sources:** MarketEventType.java (existing enum), replan spec §4.3 (conditional routing table)
**Exploration:** quick
**Status:** revised (R1-05: fixed self-reference dep, noted type conflation as known limitation)

## D5: FsiActionRiskClassifier — independent rules

**Choice:** Own classification rules, independent of `FsiRiskAssessor`. Three classification dimensions: magnitude (portfolio ratio), action type (counterparty close → HIGH), and situational context (new position during incident → MEDIUM).
**Alternatives:**
- Delegate to `FsiRiskAssessor` — input impedance mismatch (ConsensusResult vs action context), missing dimensions (no incident context or action-type awareness), and policy divergence is likely (incident response may need higher risk tolerance than normal trading)
- Share thresholds via constants — couples policies that represent independent decisions and should be free to diverge
**Rationale:** The two classifiers operate at different architectural layers (blocks routing vs engine action), receive different input types, and classify on different dimensions. The 10%/25% magnitude thresholds happen to match today but represent different policies: "acceptable risk for a proactive trade" vs "acceptable risk for an incident response action." DRY on two constants is trivial duplication; coupling them is real cost.
**Trade-offs:** Two copies of the same threshold values. Acceptable — they may diverge when CRITICAL incidents need relaxed gates for speed.
**Sources:** FsiRiskAssessor.java, FsiRiskGateRouting.java (C1 arena layer), ActionRiskClassifier SPI (engine-api), GE-20260607-3b6711 (auto-composition via @RiskClassifier), GE-20260607-326c7e (GateRequired restrictiveness)
**Exploration:** deep-analysis
**Status:** captured (R1-08: verified, reasoning holds)

## D6: SLA breach policy — single policy with claim/completion tiers

**Choice:** Single `FsiSlaBreachPolicy` with 2-tier escalation using the platform's claim/completion expiry mechanism:
- **Tier 1 (claim deadline):** WorkItem created with claim deadline (CRITICAL: 2 min, HIGH: 7 min, MEDIUM: 30 min). If unclaimed → `onBreach(CLAIM_EXPIRED)` → policy returns `EscalateTo(oncall-escalation)` with tighter completion deadline.
- **Tier 2 (completion deadline):** Escalated WorkItem expires → `onBreach(COMPLETION_EXPIRED)` → policy detects escalation tier via `candidateGroups` containing `oncall-escalation` → returns `Fail` (auto-execute with compliance notification).
Tier detection via `candidateGroups` per the stateless garden pattern.
**Alternatives:**
- Per-severity named policies (CRITICAL/HIGH/MEDIUM) — unnecessary complexity; SLA windows vary by severity at WorkItem creation, not at policy level
- 3-tier with percentage-based thresholds — doesn't map to the actual SPI mechanism (only `CLAIM_EXPIRED` and `COMPLETION_EXPIRED` breach types exist)
- Short-circuit for tight windows — defeats the purpose of human oversight for CRITICAL incidents (reviewer R1-03)
**Rationale:** The platform fires `onBreach` only for claim expiry and completion expiry — not at percentage thresholds. Multi-tier works by setting intermediate claim deadlines and using candidateGroups for tier detection. Severity-specific timing is set on the WorkItem at creation (different claim/completion deadlines per severity), not in the policy.
**Trade-offs:** CRITICAL incidents get only 2 min before first escalation. This is intentional — if no one claims in 2 min at 3am, escalation is the right response, not waiting longer.
**Sources:** SlaBreachPolicy SPI (work-api — BreachType: CLAIM_EXPIRED, COMPLETION_EXPIRED), GE-20260511-3e5a75 (BreachDecision pattern), GE-20260522-f7db12 (stateless multi-tier via candidateGroups), SlaBreachContext (no time-remaining field — tier detection via candidateGroups only)
**Exploration:** quick
**Status:** revised (R1-03: removed short-circuit, corrected SPI mapping to claim/completion mechanism)

## D7: WorkItem types and inbox strategy

**Choice:** Use `types` field to distinguish WorkItem purposes: `risk-gate` for action risk approvals, `incident-review` for case milestone sign-offs. Inbox aggregation via `candidateGroups` (on-call group sees all their items) and `type` filter for focused views. C1's existing `trade-approval` type remains — it's a distinct concern (arena consensus approval vs incident action gating).
**Alternatives:**
- Single type for all WorkItems — loses the ability to filter different concerns in the UI
- Separate named queues — the platform doesn't have explicit queues; WorkItemQuery is query-based
**Rationale:** `WorkItemQuery` supports filtering by `type`, `candidateGroups`, `assigneeId`, `priority`, and `status`. The on-call trader's inbox queries by `candidateGroups` (shows all items assigned to their group). Type-based tabs provide focused views. Note: `WorkItemQuery` has no `scope` filter — `candidateGroups` is the correct aggregation mechanism.
**Trade-offs:** Three WorkItem types across C1+C4 (trade-approval, risk-gate, incident-review). Clear semantic distinction justifies the split.
**Sources:** WorkItemQuery.java (candidateGroups, type filters — no scope filter), WorkItemCreateRequest.java (types, scope, candidateGroups fields), FsiRiskGateRouting.java (existing C1 pattern: types=trade-approval)
**Exploration:** quick
**Status:** revised (R1-07: corrected aggregation to candidateGroups instead of scope, acknowledged existing trade-approval type)

## D8: Dual gate interaction model (C1 routing + C4 engine)

**Choice:** Document as non-overlapping by lifecycle stage. C1's `FsiRiskGateRouting` gates during arena evaluation (proactive trading). C4's `FsiActionRiskClassifier` gates during case execution (incident response). A position close from arena evaluation and a position close from incident response are different actions in different contexts — they don't double-gate. If an incident triggers a deliberation that goes through the arena, the arena gate (C1) fires; the incident's own response actions are gated by C4. No dedup needed.
**Alternatives:**
- Dedup mechanism — unnecessary complexity for non-overlapping lifecycle stages
**Rationale:** The two gates operate at different points: C1 gates what strategy agents propose, C4 gates what incident-response agents execute. Same person (on-call trader) may see both, but for different actions at different times.
**Trade-offs:** If a future flow combines arena evaluation with incident response in a single action, both gates could fire. Acceptable — better to over-gate than under-gate for financial compliance.
**Sources:** FsiRiskGateRouting.java (C1 blocks routing), ActionRiskClassifier SPI (C4 engine action), reviewer R1-10
**Exploration:** quick
**Status:** captured
