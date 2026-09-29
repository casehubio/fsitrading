# Design Decisions — C13 Trading YAML Playbooks

## D1: Playbook-to-case relationship

**Choice:** Playbooks complement case definitions
**Alternatives:**
- Playbooks replace case definitions — migrates everything to step-based model, loses reactive choreography strengths
- Playbooks are standalone — independent event subscription, no case integration; loses CBR, audit, SLA infrastructure
**Rationale:** Case definitions (engine choreography) are the reactive detection and dispatch layer — bindings fire on context changes when situations are detected. Playbooks (yaml-core step lists) are the imperative response plans that cases invoke. Two layers, each doing what it's best at: cases detect, playbooks coordinate.
**Trade-offs:** Two orchestration models to understand. Mitigated by clear separation — case YAML in engine format, playbook YAML in yaml-core step format.
**Sources:** docs/repos/casehub-engine/three-pathways.md, docs/guides/yaml-language-guide.md §9, existing case definitions (overnight-incident, fsi-oversight)
**Exploration:** quick
**Status:** captured

## D2: Agent step invoke bindings

**Choice:** Reuse eidos agent descriptors
**Alternatives:**
- Standalone agent definitions — inline model/prompt in step YAML; decoupled but duplicates agent config, bypasses trust scoring
- Hybrid — eidos for existing agents, inline for new ones; inconsistent, two patterns for same concern
**Rationale:** Step invoke bindings reference existing agent descriptors by name (`invoke: { agent: { descriptor: sentiment-analyser } }`). The step catalog dispatches through the same eidos infrastructure that the arena and incident pipelines use. Trust scoring, persona config, and capability tags are shared.
**Trade-offs:** Coupling to eidos registration — new playbook agents require an eidos descriptor, not just YAML config. Acceptable because agent identity should be managed centrally.
**Sources:** docs/guides/yaml-language-guide.md §10 (invoke bindings), app/agent/ (7 strategy agents), app/incident/agent/ (13 incident agents), casehub-eidos AgentDescriptorRegistrar
**Exploration:** quick
**Status:** captured

## D3: Runtime execution scope

**Choice:** Parse-time validation only
**Alternatives:**
- Implement runtime in-epic — delivers end-to-end execution but adds engine work outside fsitrading's ownership
- Minimal runtime stub — fsitrading-specific executor for subset of step types; duplicates engine work, divergence risk
**Rationale:** fsitrading authors valid playbook YAML that parses and validates against the step catalog. StepWalker resolves action keys, StepSchemaComposer validates parameter types. Runtime execution of structural types (block, if/else, match) is engine-repo work tracked by cross-repo issues. fsitrading is unblocked — all Phase 1 and Phase 2 issues deliver validated YAML.
**Trade-offs:** Can't run playbooks end-to-end until engine runtime ships. Simulation testing uses scenario engine for temporal sequencing + @SimulationEligible decorators for SPI responses — not playbook execution.
**Sources:** platform/docs/blog/2026-09-27-mdp01-step-catalog-and-block-control-flow.md ("The types are ready; the runtime isn't"), engine#1190-#1192
**Exploration:** quick
**Status:** captured

## D4: SPI extraction and @SimulationEligible

**Choice:** Use existing platform @SimulationEligible APT infrastructure
**Alternatives:**
- Build new — unnecessary; the annotation, processor, and tests already exist
**Rationale:** `@SimulationEligible` in `casehub-platform-simulation-api` with `SimulationDecoratorProcessor` in `simulation-generator` already auto-generates CDI simulation decorators at compile time. Already used in production by casehub-iot (`DeviceProvider`). fsitrading extracts SPI interfaces, annotates them, adds the simulation-generator dependency, and gets decorators generated on `mvn compile`.
**Trade-offs:** None — this is the designed platform mechanism.
**Sources:** platform/simulation-api/src/main/java/.../SimulationEligible.java, platform/simulation-generator/.../SimulationDecoratorProcessor.java, casehub-iot-api DeviceProvider.java (production usage)
**Exploration:** quick (verified via IntelliJ MCP search)
**Status:** captured

## D5: CBR feedback loop wiring

**Choice:** Use existing CBR config on case definitions
**Alternatives:**
- Wait for engine #1190-#1192 — the generic yaml-core ↔ CBR bridge is the long-term mechanism; defers outcome recording until those issues ship
- Build fsitrading-specific bridge — domain-specific CBR observer; works immediately but duplicates engine work
**Rationale:** The overnight-incident case definition already has a `cbr:` block with features, domain, weights, and problem description. `CbrCaseRetainObserver` stores outcomes automatically when cases reach terminal state. Playbooks invoked by that case inherit its CBR context — no new wiring needed in fsitrading.
**Trade-offs:** Playbook-level outcome granularity depends on engine's step-level CBR recording (FsiStepOutcomeObserver already observes step outcomes). Fine-grained playbook step outcomes wait for engine #1190.
**Sources:** docs/repos/casehub-engine/cbr-playbook-guide.md, app/cbr/ (FsiCaseOutcomeObserver, FsiStepOutcomeObserver, FsiPlanAdapter, FsiCbrFeatureSchema)
**Exploration:** quick
**Status:** captured

## D6: Shared playbook modules

**Choice:** Extract shared patterns as yaml-core modules upfront
**Alternatives:**
- Self-contained playbooks — each playbook is a single file; simpler initially but duplicates risk-gate, scatter-response, deadline-escalation across all 5 playbooks
- Start self-contained, extract later — discover abstractions from real usage; pragmatic but means maintaining 5 copies of the same patterns during Phase 2
**Rationale:** Common coordination patterns appear in 3+ of the 5 playbooks: risk assessment gate (flash crash, strategy eval, risk escalation), scatter-gather to response team (flash crash, overnight incident), deadline with escalation (flash crash, overnight incident, risk escalation), state machine transitions (flash crash, overnight incident, market regime shift). Extracting these as parameterised modules from the start enforces consistency and reduces the per-playbook authoring surface.
**Trade-offs:** Upfront design effort for module interfaces. Mitigated by yaml-core's module system being well-documented with parameter validation, aliasing, and reference rewriting.
**Sources:** docs/guides/yaml-language-guide.md §5 (Modules), issues #54-#58 (playbook structures showing shared patterns)
**Exploration:** quick
**Status:** captured

## D7: Simulation layer architecture

**Choice:** Two-layer simulation — scenario engine for timing, @SimulationEligible decorators for SPI data
**Alternatives:**
- Decorators only — @SimulationEligible decorators handle both timing and data; self-contained but reinvents temporal sequencing that ScenarioExecutor already provides
- Scenario only — temporal profiles are scenario files with direct REST/CDI injection; simpler but doesn't give playbook tests isolated per-SPI simulation
**Rationale:** Temporal profiles (flash-crash, regime-shift, overnight-gap) are scripted sequences of timed events — exactly what the scenario engine does. `@SimulationEligible` decorators provide canned per-SPI responses from corpus data files. Two layers with clean separation: ScenarioExecutor owns when events happen, decorators own what SPIs return. The scenario engine is a first-class platform tool with active pages UI investment (#498-#501).
**Trade-offs:** Two systems to configure for a test run. Mitigated by clear responsibility split — temporal profiles are scenario files, response data is corpus YAML.
**Sources:** docs/platform/scenario-format.md, platform/simulation-api, pages#498-#501 (scenario UI improvements)
**Exploration:** quick
**Status:** captured
