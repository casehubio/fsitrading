# Decisions — C6a CBR Pipeline

## D1: CBR + HTN integration model

**Choice:** Inform HTN — CBR retrieval injects past plans into CaseContext as prior art
**Alternatives:**
- Override HTN — CBR ensemble replaces static decomposition; too aggressive, removes proven severity-based structure
- Augment HTN — PlanAdapter modifies static decomposition in-place; tighter coupling, harder to debug when adaptations go wrong
**Rationale:** Preserves the static severity-based HTN decomposition as the primary response structure. CBR adds value through context — agents and LLM fallback can reference "what worked before" without being bound by it. Simpler to implement, test, and debug. The engine's `CaseStartedEventHandler.injectCbrExperiences()` sets four keys in CaseContext's WORKING layer: `cbrExperiences` (serialised `List<RetrievedExperience>` — the adapted plan traces), `cbrBestSimilarity` (max similarity score), `cbrMatchCount` (number of matches), `cbrOutcomeConsistency` (outcome agreement ratio). An agent opts in by reading `context.get("cbrExperiences")` — the same access pattern as any CaseContext data.
**Trade-offs:** Agents must opt-in to using CBR context — there's no automatic plan modification. If an agent ignores CBR, it gets no benefit. This is acceptable for a first implementation; tighter integration (Augment) can be added later if the inform-only model proves insufficient.
**Sources:** Replan spec §C6.2, PlanAdapter API, OvernightIncidentCaseHub.java, CaseStartedEventHandler.java, GE-20260720-6ea915
**Exploration:** quick
**Revised from:** R1-07 surfaced that the agent access mechanism was unspecified. Added CaseContext key names and opt-in pattern.
**Status:** revised

## D2: CBR case type (constrained)

**Choice:** PlanCbrCase — the only CbrCase subtype with both `features` and `planTrace`
**Alternatives:**
- FeatureVectorCbrCase — has features but no plan trace; can't record HTN response steps
- Custom CbrCase subtype — unnecessary overhead, PlanCbrCase already fits
**Rationale:** Incident response produces a plan (HTN decomposition steps) and an outcome. PlanCbrCase captures both the market event features (for similarity retrieval) and the response plan trace (for reuse). This is the type the PlanAdapter and PlanEnsembleAnalyzer APIs operate on.
**Trade-offs:** PlanTrace population is non-trivial for cases with human task steps (no executor), conditional routing (skipped steps), and HTN decomposition branches. `FsiCaseOutcomeObserver` must filter these — human tasks excluded, skipped steps omitted, only terminal capability plan items with executors produce trace entries. Incomplete traces are acceptable because FsiPlanAdapter operates on the steps that were actually executed, not on what was planned.
**Sources:** PlanCbrCase.java, PlanAdapter.java, PlanEnsembleAnalyzer.java, CbrCaseRetainObserver.java (trace construction reference)
**Exploration:** quick
**Status:** captured

## D3: Feature extraction approach (constrained)

**Choice:** LambdaFeatureExtractor — programmatic Java extraction
**Alternatives:**
- JqFeatureExtractor — cannot compute TimeSeries (DtwSpec) or DiscreteSequence (EditDistanceSpec) features from JQ expressions; these require runtime aggregation from the market data pipeline
**Rationale:** The feature schema includes price_action_pattern (TimeSeries) and event_sequence (DiscreteSequence). These require collecting data from the market pipeline (OHLCV bars, trend summaries) and structuring them into FeatureValue types that JQ cannot produce. LambdaFeatureExtractor receives CaseContext and can pull from injected services.
**Trade-offs:** Feature extraction logic lives in Java rather than YAML — harder to modify without recompilation. Acceptable for complex features that need runtime data.
**Sources:** FeatureExtractor.java, FeatureField.java (TimeSeries, DiscreteSequence), Replan spec §C6.1
**Exploration:** quick
**Status:** captured

## D4: CbrConfig wiring

**Choice:** Hybrid — YAML retains declarative params (topK, minSimilarity, temporalDecayHalfLifeDays); `augment()` reads the YAML-parsed CbrConfig and rebuilds with the LambdaFeatureExtractor added
**Alternatives:**
- YAML + separate schema registration — keeps YAML as source of truth but adds moving parts; feature extraction happens outside the case engine's automatic flow
- Full Java DSL override — builds CbrConfig entirely in augment(), ignoring YAML values; creates split-brain risk because overnight-incident.yaml already has a `cbrConfig` section that would be silently superseded
**Rationale:** The overnight-incident.yaml already declares `cbrConfig: { topK: 5, minSimilarity: 0.6, temporalDecayHalfLifeDays: 90 }`. This is the right place for declarative params. `augment()` reads `definition.getCbrConfig()` (the YAML-parsed config) and rebuilds via the builder, preserving topK/minSimilarity/temporalDecay and adding only the LambdaFeatureExtractor and domain. This avoids split-brain (one config, two sources) while keeping YAML as source of truth for tunable params.
**Trade-offs:** Feature extraction logic is in Java, declarative params in YAML — two places, but each owns a distinct concern. The YAML `cbrConfig` section stays in overnight-incident.yaml. `augment()` must read and re-set the existing values, not ignore them.
**Sources:** CbrConfig.java (builder API), overnight-incident.yaml, OvernightIncidentCaseHub.java, GE-20260720-6ea915
**Exploration:** quick
**Revised from:** Full Java DSL override — R1-04 correctly identified that the existing YAML cbrConfig section would be silently ignored, creating a maintenance hazard. Hybrid preserves YAML as source of truth for tunable params.
**Status:** revised

## D5: Outcome recording strategy

**Choice:** Single observer at case close — FsiCaseOutcomeObserver listens for CaseOutcomeEvent
**Alternatives:**
- Dual recording (per-step + case-level) — richer data but triggers eraseEntity cross-domain gotcha (GE-20260720-b7a8b9), requires suffixed entity IDs, two CBR domains to manage
- Event-driven milestone pipeline — progressive PlanCbrCase construction across milestones; complex state management with no clear benefit over single-write-at-close
**Rationale:** Features are extracted at case start (by the engine's automatic retrieval flow) and cached in CaseContext. At case close, FsiCaseOutcomeObserver extracts features from the case snapshot, constructs plan trace via `PlanItemStore.findByCaseId()` — filtering terminal capability plan items with executors, sorting by `createdAt`, mapping to `PlanTrace` records (mirroring `CbrCaseRetainObserver`'s construction logic) — records `CbrOutcome`, and stores one `PlanCbrCase`. Single write path, no cross-domain erasure risk, simple to test.
**Trade-offs:** No per-step CBR entries — we can't query "which individual agent performed well in step X." Acceptable because trust scoring (PnlAttestationService) already provides per-agent quality tracking. CBR's value is at the incident-response-plan level, not the individual-step level. Outcome retention scope (which terminal statuses to retain) is governed by D17.
**Sources:** CbrCaseMemoryStore.java, CbrOutcome.java, PlanItemStore.java (trace construction), GE-20260720-6ea915 (exclude CbrCaseRetainObserver), GE-20260720-b7a8b9 (eraseEntity cross-domain risk)
**Exploration:** quick
**Depends on:** D7 (CbrCaseRetainObserver exclusion enables FsiCaseOutcomeObserver), D17 (outcome retention scope). Engine dependencies: `PlanItemStore` (CDI injection — trace construction requires `findByCaseId()`). Note: `CaseDefinitionRegistry` is NOT required — `FsiCaseOutcomeObserver` already filters by `caseType == "overnight-incident"` and can hardcode binding-to-capability mappings for this case type. D1 dependency removed — single-write-at-close is justified by (a) eraseEntity cross-domain avoidance and (b) PnlAttestationService per-agent tracking, independent of the CBR integration model.
**CDI resolution:** `FsiCaseOutcomeObserver` as `@ApplicationScoped` (no `@DefaultBean`) displaces `NoOpCaseOutcomeObserver` (`@DefaultBean`) following the standard CaseHub displacement protocol (GE-20260513-4f26a7). No `@Alternative` or `@Priority` needed.
**Revised from:** R1-02 identified that planTrace was empty (`List.of()`) despite D2's rationale and D6's adaptation logic depending on populated traces. Revised to specify `PlanItemStore`-based trace construction. R1-03/R1-10 surfaced that the COMPLETED-only filter was an implicit decision — outcome scope now governed by D17. R2-02 identified undeclared `PlanItemStore` dependency — added to Sources and Depends-on. Noted that `CaseDefinitionRegistry` is not needed since the observer hardcodes overnight-incident binding names.
**Status:** revised

## D6: Plan adaptation scope

**Choice:** Implement FsiPlanAdapter as CDI `@ApplicationScoped` bean (called automatically by engine's `CbrRetrievalService`). Defer FsiPlanEnsembleAnalyzer to platform fix — file issue on casehub-engine to add PlanEnsembleAnalyzer invocation to `CbrRetrievalService` after individual adaptation. First implementation has per-case plan adaptation only, no ensemble synthesis.
**Alternatives:**
- Skip adaptation entirely, pass raw retrieved plans — agents get 5 unprocessed plans with no synthesis; wastes the platform's adaptation SPI
- LLM-based adaptation — use an LLM to synthesize plans instead of the structured PlanAdapter; slower, non-deterministic, unnecessary when the structured API handles the use cases
- CaseLifecycleEvent observer for interim ensemble — `@ObservesAsync CaseLifecycleEvent` filtered for "CaseStarted" could perform ensemble analysis after `injectCbrExperiences()`. But `fireAsync` provides no ordering guarantee relative to case execution: `CaseStartedEventHandler.onCaseStarted()` fires the lifecycle event asynchronously, then immediately publishes `casehub.context.changed` which triggers binding evaluation. Ensemble analysis would race with first-step agents.
- Custom first-step binding for interim ensemble — a binding triggered on `.cbrExperiences != null and .cbrEnsemblePlan == null` whose worker performs ensemble analysis. Deterministic ordering but adds latency to incident response and an artificial step to the case plan.
**Rationale:** FsiPlanAdapter implements the 4 strategies from replan §C6.2: agent substitution (replace low-trust agents), threshold adjustment (scale by volatility), step addition (larger positions → pre-reduce), step suppression (market closed → skip halt). The engine's `CbrRetrievalService.adaptAndMapPlanTrace()` calls `PlanAdapter.adapt()` automatically on each individual `ScoredCbrCase<PlanCbrCase>`. Agents see 5 individually-adapted plans in CaseContext — sufficient for the "Inform" model (D1) where agents reference past plans as advisory context. Ensemble synthesis (combining top-K plans into a consensus plan with UNANIMOUS/CONSENSUS/CONTESTED step classification) adds incremental value but requires runtime data unavailable at definition time. `augment()` runs at definition load time via `YamlCaseHub.getDefinition()` (lazy double-checked locking) — no case instance or CaseContext exists at that point. The platform issue is the architecturally correct fix: `CbrRetrievalService` should invoke `PlanEnsembleAnalyzer` after individual adaptation, just as it already invokes `PlanAdapter`.
**Trade-offs:** No ensemble synthesis in first implementation — agents see individual adapted plans but no consensus analysis. Acceptable because: (1) individual plan adaptation already provides the core CBR value, (2) the "Inform" model means agents make their own judgment anyway, (3) both interim workarounds (lifecycle observer, custom binding) add complexity with ordering or latency trade-offs for marginal benefit.
**Depends on:** D2 (PlanCbrCase), D4 (CbrConfig wiring)
**Sources:** PlanAdapter.java, PlanEnsembleAnalyzer.java, CbrRetrievalService.java (verified: no PlanEnsembleAnalyzer injection), CaseStartedEventHandler.java (verified: injectCbrExperiences runs at case start, not at augment time), YamlCaseHub.java (verified: augment() runs at definition load time), Replan spec §C6.2
**Exploration:** quick
**Revised from:** R1: Original D6 stated engine calls PlanEnsembleAnalyzer automatically (false). R2: Interim workaround placing ensemble analysis in augment() was architecturally impossible — augment() runs at definition time, not case start time. Revised to defer ensemble to platform fix.
**Action:** File casehub-engine issue for PlanEnsembleAnalyzer invocation in CbrRetrievalService (R1-08: no tracking issue exists for this deferred capability).
**Status:** revised

## D7: CDI wiring for CBR pipeline

**Choice:** Exclude CbrCaseRetainObserver; configure CbrCaseMemoryStore via existing CDI decorator chain
**Alternatives:** None — this is a required mitigation for GE-20260720-6ea915 (duplicate entry gotcha)
**Rationale:** Add `io.casehub.engine.internal.memory.CbrCaseRetainObserver` to `quarkus.arc.exclude-types` in both main and test `application.properties`. The engine's automatic retrieval (via CaseStartedEventHandler) continues to work — only the automatic retain is disabled. Our FsiCaseOutcomeObserver handles retain with proper domain-specific features and plan trace. CbrCaseMemoryStore resolution follows the existing CDI chain (TrustWeightedCbrCaseMemoryStore if casehub-neocortex-memory is on classpath; InMemoryCbrCaseMemoryStore for tests via selected-alternatives).
**Trade-offs:** If a test-scoped `application.properties` is later added with its own `quarkus.arc.exclude-types`, Quarkus config resolution will REPLACE (not merge) the main exclusion list, silently re-enabling `CbrCaseRetainObserver` in tests. Document in CLAUDE.md.
**Sources:** GE-20260720-6ea915, GE-20260706-abaddc (DefaultBean resolution), application.properties
**Exploration:** quick
**Revised from:** R1-13 identified that the "both properties files" concern was unfounded (no test application.properties exists). Revised to state the actual risk: Quarkus config replacement semantics.
**Status:** revised

## D8: CbrFeatureSchema registration strategy

**Choice:** Register CbrFeatureSchema in `OvernightIncidentCaseHub.augment()` via `cbrStore.registerSchema()` at case definition augmentation time
**Alternatives:**
- Startup observer — register schema from a CDI `@Observes @Startup` bean; schema is available before any case starts but couples registration to application lifecycle
- Engine-automatic — let the engine register schema from YAML config; not supported, schema registration requires programmatic `FeatureField` declarations
- Omit schema registration — all cases score 1.0 similarity (verified: `CbrSimilarityScorer.scoreDetailed()` returns `SimilarityBreakdown(1.0, Map.of())` when schema is null)
**Rationale:** The schema defines 7 `FeatureField` entries with specific `SimilaritySpec` types: `FeatureField.Categorical` (event_type, instrument_sector), `FeatureField.Numeric` with `GaussianDecay` (time_of_day, volatility_at_detection), `FeatureField.NumericList` (volume_profile), `FeatureField.TimeSeries` with `DtwSpec` (price_action_pattern), `FeatureField.DiscreteSequence` with `EditDistanceSpec` (event_sequence). `augment()` is the natural registration point — it already configures the CbrConfig and runs at definition load time (before any case starts). `OvernightIncidentCaseHub` requires a new `@Inject CbrCaseMemoryStore cbrStore` field. CDI field injection happens at bean construction, before `augment()` is called, so the store is available. When `NoOpCbrCaseMemoryStore` is active (tests without casehub-neocortex-memory on classpath), `registerSchema()` is a silent no-op — correct behavior for tests that don't exercise CBR similarity scoring. The schema is registered once per definition load and cached in the store by caseType key.
**Trade-offs:** Schema registration and CbrConfig wiring are in the same method. This is intentional — they are part of the same concern (CBR pipeline configuration for this case type).
**Depends on:** D4 (augment() is the configuration point)
**Sources:** CbrFeatureSchema.java, CbrSimilarityScorer.java (null schema → 1.0), InMemoryCbrCaseMemoryStore.java (registerSchema stores by caseType), Replan spec §C6.1 (feature table)
**Exploration:** surfaced by review (R1-03, R1-11)
**Status:** captured

## D9: CBR domain scoping

**Choice:** Domain string `"fsitrading"`, scope by instrument sector via `Path.of("fsitrading", sector)` at retrieval time; domain set in augment() CbrConfig builder
**Alternatives:**
- Domain per case type (e.g., `"overnight-incident"`) — too narrow, prevents cross-case-type retrieval within the same trading domain
- No scope — flat retrieval across all instruments; sector-specific incidents (e.g., tech flash crash) would match unrelated sectors
- Scope at store time only — prevents dynamic sector-scoped queries; the query sector should come from the current incident's instruments, not from a fixed config
**Rationale:** The replan spec §C6.1 specifies `CbrQuery.scope(Path.of("fsitrading", sector))` for sector-scoped retrieval. Domain `"fsitrading"` is the application-level namespace. Scope by sector at retrieval time (not store time) — cases are stored at `Path.root()` (global within domain) but queries can narrow by sector path. Domain configuration: set `domain: "fsitrading"` in the CbrConfig builder in augment(), avoiding reliance on EpisodicMemoryConfig fallback.
**Trade-offs:** Sector-scoped queries reduce recall — a cross-sector incident pattern won't match. Acceptable for first implementation; `Path.root()` queries provide the escape hatch for broad retrieval.
**Depends on:** D4 (CbrConfig builder sets domain)
**Sources:** CbrQuery.java (scope parameter), CbrRetrievalService.resolveDomain(), Replan spec §C6.1
**Exploration:** surfaced by review (R1-12)
**Status:** captured

## D10: CBR retrieval timing

**Choice:** `CbrRetrievalTiming.CASE_LIFETIME` — features extracted once at case start, cached for the case's duration
**Alternatives:**
- `PER_EVALUATION` (default) — re-extracts features on every retrieval; unnecessary overhead for overnight incidents whose characteristics (severity, event type, instruments) don't change after detection
**Rationale:** Overnight incident characteristics are fixed at detection. The market event, severity, affected instruments, and price action pattern that trigger the case don't change as the case progresses through DETECTED → CLASSIFIED → RESPONDED → VERIFIED → CLOSED. Re-extracting features on each evaluation wastes computation and market data service calls. `CASE_LIFETIME` caching is explicitly supported by `CbrRetrievalService` via `ConcurrentHashMap` cache with 1000-entry bound and per-case eviction.
**Trade-offs:** If the incident evolves significantly (e.g., cascade to additional instruments), cached features won't reflect the change. Acceptable for first implementation — incident response is time-bounded and detection-time features are the relevant similarity signal. Note: `CASE_LIFETIME` caching applies to the `RetrievedExperience` list from `CbrRetrievalService`. D6's ensemble analysis (when implemented via platform fix) would operate on these cached experiences and write the `EnsemblePlan` as a separate CaseContext key — it does not modify the cached retrieval results.
**Depends on:** D4 (CbrConfig timing parameter)
**Cross-ref:** D6 (ensemble analysis operates on cached experiences, writes separate key)
**Sources:** CbrConfig.CbrRetrievalTiming, CbrRetrievalService.retrieveInternal() (CASE_LIFETIME caching)
**Exploration:** surfaced by review (R1-13)
**Status:** captured

---

# Decisions — C6b Post-mortem + Compliance + GDPR

## D11: Post-mortem data source

**Choice:** Hybrid qhorus channel — add a qhorus channel to overnight-incident.yaml. A CaseLifecycleObserver bridges engine events (bindings, milestones, goal evaluations) to qhorus messages automatically. Agent workers can optionally post richer observations. ConversationProjection accumulates state; ConversationRenderer generates the post-mortem markdown.
**Alternatives:**
- Case lifecycle projection — build ConversationState directly from case lifecycle events without a channel; simpler but skips qhorus/blocks conversation infrastructure entirely
- Direct markdown generation — skip ConversationRenderer, build markdown from CaseContext/PlanTrace; doesn't reuse platform conversation rendering
**Rationale:** This is a platform showcase project. Wiring a qhorus channel demonstrates blocks conversation + qhorus + ConversationRenderer working end-to-end. The bridge provides guaranteed structural coverage (every binding, milestone, goal evaluation becomes a ConversationPoint) while agents can layer on reasoning when they have something meaningful to say. ConversationRenderer already handles groupByTopic, epistemicStatus, convergenceSignal, obligationChain — all of which map naturally to incident response phases.
**Trade-offs:** Requires a `CaseLifecycleObserver` bridge that translates engine events (bindings, milestones, goal evaluations) to qhorus `MessageDispatch` calls, a channel definition in overnight-incident.yaml, optional agent worker posts, and replay infrastructure in the post-mortem endpoint. This is ~4 new types and a lifecycle bridge observer — more implementation surface than direct `ConversationState` construction from case lifecycle events. The cost is justified by platform showcase value: demonstrating qhorus + ConversationRenderer end-to-end.
**Sources:** ConversationRenderer.java, ConversationProjection.java, ConversationRendererConfig.java, RenderContext.java, Replan spec §C6.4
**Exploration:** quick
**Revised from:** R1-04 identified that the "low incremental cost" claim was understated. Revised trade-off to honestly state the implementation surface while defending the decision as platform showcase.
**Status:** revised

## D12: Compliance grid approach

**Choice:** Live evidence query — service queries ledger entries, routing decisions, and CBR records at request time. Returns `TrustRoutingRequirement` per regulatory requirement with `RequirementStatus` (CLOSED/PARTIAL/BREACHED/GAP) and actual `RoutingDecisionRecord` evidence.
**Alternatives:**
- Static mapping with probes — checks whether mechanisms are wired (class exists, config set) rather than whether evidence exists; answers "is this capability deployed?" not "is this case compliant?"
- Per-case compliance report — given a caseId, queries all evidence for that incident only; narrower scope, one case at a time
**Rationale:** Live evidence demonstrates TrustRoutingRequirement, RoutingDecisionRecord, and ledger causedByEntryId chains working together. The compliance grid answers "what is the compliance posture of this trading system?" with real evidence counts and entry references. Maps 5 requirements to existing mechanisms: MiFID II Art.17 → ledger entry chains (StrategyEvaluationLedgerEntry, OrderExecutionLedgerEntry), RTS 6 monitoring → OTel histograms, RTS 6 kill switches → ActionRiskClassifier work item approvals, Dodd-Frank → causedByEntryId chains, MAR → CBR event sequence similarity. Note: "LedgerExecutionListener" from replan spec does not exist. Actual evidence sources per requirement: MiFID II Art.17 → `StrategyEvaluationLedgerEntry` (seq=1) + `OrderExecutionLedgerEntry` (seq=2, `causedByEntryId` = eval entry) via `TradingLedgerService`; RTS 6 monitoring → OTel histograms from `ActionRiskClassifier`; RTS 6 kill switches → work item approval records via `ActionRiskClassifier.classify()`; Dodd-Frank → `causedByEntryId` ledger chains; MAR → CBR `event_sequence` feature similarity via `CbrCaseMemoryStore`.
**Trade-offs:** Requires querying multiple stores (ledger, routing, CBR) per request. Acceptable for a compliance dashboard that's queried infrequently. Caching can be added later if needed.
**Sources:** TrustRoutingRequirement.java, RoutingDecisionRecord.java, RequirementStatus.java, ActionRiskClassifier.java, StrategyEvaluationLedgerEntry.java, OrderExecutionLedgerEntry.java, TradingLedgerService.java, Replan spec §C6.5
**Exploration:** quick
**Revised from:** R1-09 identified that the LedgerExecutionListener reference was imprecise. Added per-requirement evidence type mapping with actual classes and query paths.
**Status:** revised

## D13: GDPR erasure scope

**Choice:** Single orchestrator — `FsiGdprErasureService.erase(traderId)` coordinates erasure across all three stores: `CaseMemoryStore.erase()`, `CbrCaseMemoryStore.erase(EraseRequest)`, and `LedgerErasureService.erase()`. One REST endpoint, centralized gotcha handling.
**Alternatives:**
- FSI stores only, ledger separate — cleaner separation but requires two API calls for full erasure; splits the audit trail
- Event-driven cascade — CDI events to separate observers; loosely coupled but hard to guarantee all-or-nothing semantics and produce a single audit receipt
**Rationale:** A trader requesting GDPR erasure expects one action to erase their data everywhere. Splitting across endpoints creates operational risk (partial erasure). The orchestrator centralizes the garden gotchas: tokenisation must be enabled (GE-20260531-46f8ab), eraseEntity crosses all CBR domains (GE-20260720-b7a8b9), post-erasure actor-scoped queries return empty (GE-20260628-6599e6). LedgerErasureService already writes its own ErasureReceiptLedgerEntry — no need for a separate FSI receipt type (avoids GE-20260618-3e5f2d entity name collision).
**Trade-offs:** CaseMemoryStore and CbrCaseMemoryStore erasure are not transactional with ledger erasure (different persistence units). Each individual store erasure is idempotent — retrying a completed erasure returns zero records with no side effects. If one store fails mid-sequence, the endpoint returns an error and the operator retries. On retry, previously completed stores return count=0, failed stores execute, and `LedgerErasureService` creates a receipt reflecting the retry's erasure counts. The receipt from a retry may show count=0 for stores erased in a prior attempt — a logging precision concern, not a compliance gap, since GDPR Art.17 requires erasure "without undue delay," not atomic single-attempt completion.
**Depends on:** D5 (FsiCaseOutcomeObserver stores CBR cases — those need erasing), D7 (CbrCaseRetainObserver excluded — FsiCaseOutcomeObserver is the sole source)
**Sources:** LedgerErasureService.java, CaseMemoryStore.java (erase/eraseEntity), CbrCaseMemoryStore.java (erase(EraseRequest)), ErasureNotificationCaseMemoryStore.java, EraseRequest.java, GE-20260531-46f8ab, GE-20260618-3e5f2d, GE-20260628-6599e6, GE-20260720-b7a8b9, Replan spec §C6.6
**Exploration:** quick
**Revised from:** R1-06 identified that D13 choice description referenced `eraseEntity()` (the cross-domain API) instead of `erase(EraseRequest)` (the domain-scoped API from D16). Fixed to match D16. R1-05 trade-off clarified to make retry semantics and idempotency guarantees explicit.
**Status:** revised

## D14: Post-mortem bridge granularity

**Choice:** Milestone-level — one ConversationPoint per milestone (DETECTED, CLASSIFIED, RESPONDED, VERIFIED, CLOSED). Each point accumulates ThreadEntries from bindings and work items within that milestone. Topics map to milestone names.
**Alternatives:**
- Binding-level — one ConversationPoint per agent task; very granular but noisy post-mortems with dozens of points
- HTN-method-level — one ConversationPoint per decomposition method; middle ground but couples to HTN structure
**Rationale:** Milestone boundaries are the natural narrative structure of an incident: what happened (DETECTED), what was it (CLASSIFIED), what was done (RESPONDED), did it work (VERIFIED), outcome (CLOSED). Each milestone accumulates the binding evaluations, work item results, and goal assessments that occurred within it as ThreadEntries. Compact, readable post-mortems that match how a human trader would describe the incident.
**Trade-offs:** Individual agent decisions are collapsed into milestone-level entries. The thread shows which agents acted and what they did, but at summary level. Acceptable because the per-agent detail is always available in the ledger audit trail (Dodd-Frank compliance).
**Depends on:** D11 (hybrid qhorus channel)
**Sources:** ConversationPoint.java, ConversationState.java, overnight-incident.yaml (milestones), Replan spec §C6.4
**Exploration:** quick
**Status:** captured

## D15: Post-mortem ConversationState retrieval

**Choice:** Generate on demand — when GET /api/postmortem/{caseId} is called, replay the case's qhorus channel messages through ConversationProjection to rebuild ConversationState. No additional storage needed.
**Alternatives:**
- Cache at case close — snapshot ConversationState in FsiCaseOutcomeObserver; faster reads but adds storage, staleness, and serialization concerns
- Live projection — running ConversationProjection per active case; works for open cases but requires memory management
**Rationale:** Qhorus messages are persisted to the qhorus datasource (PostgreSQL in production, H2 in dev — matching fsitrading's dual-datasource setup per ARC42STORIES §9.3). ConversationProjection is a pure function over a message stream — replaying is deterministic and idempotent. Post-mortem is an infrequent read (after incident close) so on-demand replay is acceptable. No new storage, no serialization format, no migration concerns. Dependency: qhorus JPA persistence must be active for the post-mortem channel — messages must survive application restart.
**Trade-offs:** Replay cost scales with message count per case. For overnight incidents with ~10-30 bindings, this is negligible. If post-mortems become frequent or cases grow large, caching can be added later without API changes.
**Depends on:** D11 (hybrid qhorus channel), D14 (milestone granularity)
**Sources:** ConversationProjection.java, qhorus message persistence (JPA-backed)
**Exploration:** quick
**Revised from:** R1-12 identified that the persistence backend dependency was unspecified. Added explicit dependency on qhorus JPA persistence with datasource reference.
**Status:** revised

## D16: CBR erasure scoping

**Choice:** Use `CbrCaseMemoryStore.erase(EraseRequest)` with domain specified, instead of `eraseEntity()`. EraseRequest takes domain and entityId — domain-scoped erasure avoids the cross-domain collision documented in GE-20260720-b7a8b9.
**Alternatives:**
- Accept cross-domain erasure — fsitrading currently has one domain so eraseEntity() is safe today; risky if a second domain is added later
- Suffixed entityIds — store CBR cases with domain-suffixed entityIds; workaround that obscures the real entityId
**Rationale:** `eraseEntity(entityId, tenantId)` has no domain parameter and erases cases across ALL CBR domains. `erase(EraseRequest)` accepts domain, entityId, tenantId, and caseId — precise, domain-scoped erasure. This is the architecturally correct approach and future-proofs against additional CBR domains.
**Trade-offs:** EraseRequest requires knowing the domain string at erasure time. FsiGdprErasureService uses the constant `"fsitrading"` domain — same as what's configured in CbrConfig (D4/augment).
**Depends on:** D9 (domain string "fsitrading"), D13 (orchestrator scope)
**Sources:** CbrCaseMemoryStore.java (erase vs eraseEntity), EraseRequest.java, GE-20260720-b7a8b9
**Exploration:** quick
**Status:** captured

## D17: Outcome retention scope

**Choice:** Retain all terminal outcomes — COMPLETED, FAULTED, and CANCELLED cases are stored as PlanCbrCase entries with neutral confidence (null → 1.0 default). Outcome quality is encoded in `outcomeLabel` and `CbrOutcome.result`, not in the confidence field.
**Alternatives:**
- COMPLETED only — discard FAULTED/CANCELLED; simpler but loses failure learning
- Confidence-differentiated — set confidence=0.0 for FAULTED, 1.0 for COMPLETED; breaks failure-avoidance retrieval when `OutcomeWeightingCbrCaseMemoryStore` is active (see rationale)
**Rationale:** CBR literature (Aamodt & Plaza 1994, Kolodner 1993) consistently identifies negative cases as essential for case-based reasoning — they prevent repeated failures and support failure-avoidance retrieval. An overnight flash crash where the response plan FAULTED is exactly the case that should be retrieved when a similar crash occurs, so agents can avoid repeating the failed approach. The engine's `CbrCaseRetainObserver` retains all terminal outcomes via its `OUTCOME_MAP` (COMPLETED→SUCCESS, FAULTED→FAILURE, CANCELLED→CANCELLED); `FsiCaseOutcomeObserver` should follow the same pattern. Confidence must be neutral (null/1.0) for ALL retained cases because `OutcomeWeightingCbrCaseMemoryStore` applies `similarity * (1.0 - alpha + alpha * confidence)` — reduced confidence pushes cases below `minSimilarity`, making FAULTED cases unretrievable at any substantive alpha. Outcome quality is a separate signal: `outcomeLabel` ("COMPLETED"/"FAULTED"/"CANCELLED") and `CbrOutcome.result` (SUCCESS/FAILURE) carry this information. Agents in the Inform model (D1) read outcomeLabel directly and make their own judgment.
**Trade-offs:** FAULTED cases may dominate retrieval results if many failures precede a successful response pattern. Mitigated by the existing topK limit and by agents' ability to filter by outcomeLabel. CANCELLED cases have no quality signal — they were stopped, not failed. Including them provides "this was attempted" context without implying success or failure.
**Depends on:** D5 (FsiCaseOutcomeObserver is the retain implementation)
**Sources:** CaseOutcomeEvent.java (outcomeLabel: COMPLETED/FAULTED/CANCELLED), CbrCaseRetainObserver.java (retains all outcomes via OUTCOME_MAP), CbrOutcome.java, OutcomeWeightingCbrCaseMemoryStore.java (confidence → retrieval scoring), DefaultOutcomeWeightingFunction.java (alpha formula)
**Exploration:** surfaced by review (R1-03, R1-10)
**Revised from:** R2-01 identified that confidence=0.0 for FAULTED defeats failure-avoidance retrieval when `OutcomeWeightingCbrCaseMemoryStore` is active. Root cause: confidence conflated retrieval relevance with outcome quality. Fixed by decoupling — confidence stays neutral (1.0), outcome quality goes to outcomeLabel and CbrOutcome.result.
**Status:** revised

## D18: Feature extraction data source coupling

**Choice:** Direct injection of `SyntheticMarketDataProvider` — FsiFeatureExtractor depends on the concrete synthetic data source
**Alternatives:**
- MarketDataProvider interface — abstract behind an SPI; cleaner transition to real data but adds indirection for a single implementation in C6
- CaseContext-only extraction — extract features purely from CaseContext without market data queries; loses the price action pattern and volume profile features that require historical data
**Rationale:** C6 uses synthetic market data exclusively. The feature extraction logic (volatility computation, volume profiles, price action patterns, event sequences) is independent of data source — `MarketEventEntity` is the domain model regardless of whether data is synthetic or real. When real market data arrives (C5+), `FsiFeatureExtractor` needs its injection point changed, but the computation logic is preserved. Introducing an SPI now would be speculative design — the real data pipeline's shape (streaming vs. batch, API vs. event store) is unknown and will determine the appropriate abstraction.
**Trade-offs:** Direct coupling means a code change when the data source changes. This is intentional — the coupling is visible, the transition is mechanical (change injection type), and the feature computation logic is reusable.
**Depends on:** D3 (LambdaFeatureExtractor pattern)
**Sources:** FsiFeatureExtractor.java, SyntheticMarketDataProvider.java, MarketEventEntity.java
**Exploration:** surfaced by review (R1-11)
**Status:** captured
