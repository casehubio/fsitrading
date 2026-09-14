# C7a Agent Org Structure — Design Decisions

## D1: 4-team structure

**Choice:** Trading Desk with 4 teams: Emergency Response (5 agents), Risk Management (3 agents), Analysis (3 agents), Operations (2 agents).
**Alternatives:**
- Flatter (2 teams) — loses the domain-meaningful grouping
- Deeper (sub-teams within teams) — over-structures 13 agents
**Rationale:** Maps to real trading desk roles. Emergency handles CRITICAL severity, Risk handles HIGH, Analysis handles detection/evaluation, Operations cross-cuts (alerting + verification).
**Trade-offs:** Fixed structure — if agents are added later, they need team assignment.
**Sources:** OvernightIncidentCaseDescriptor (13 agents), IncidentSeverityDescriptor (HTN decomposition)
**Exploration:** quick
**Status:** captured

## D2: Tiered escalation pattern

**Choice:** Analysis ESCALATES_TO Risk Management ESCALATES_TO Emergency Response. Operations SUPERVISES all three teams. Within-team agents REPORTS_TO a lead agent.
**Alternatives:**
- Flat cross-team escalation — individual agent-to-agent, no structure
- Pipeline (Analysis → Risk → Execution → Verify) — too linear for the domain
**Rationale:** Matches real escalation: analyst detects anomaly → risk assesses exposure → emergency responds. Operations (verify + alert) oversees everything. Eidos OrgRegistry.escalationPath() directly supports this.
**Trade-offs:** Escalation path is fixed — dynamic escalation (based on trust or load) would need runtime overrides.
**Depends on:** D1 (team structure)
**Sources:** RelationshipKind enum (SUPERVISES, ESCALATES_TO, REPORTS_TO), eidos OrgRegistry.escalationPath()
**Exploration:** quick
**Status:** captured

## D3: YAML-based org definition

**Choice:** Separate `org-structure.yaml` file in `fsitrading/app/src/main/resources/fsitrading/`. Loaded at startup via eidos YAML registrar or in `CaseHub.augment()`.
**Alternatives:**
- Java builder (OrgStructure.define()) — programmatic but less visible
- Both YAML + builder — redundant for this use case
**Rationale:** Declarative, editable, visible in project docs. Consistent with overnight-incident.yaml pattern. YAML registrar handles parsing and registration.
**Trade-offs:** Less type-safe than Java builder. Schema changes in eidos require YAML updates.
**Sources:** Eidos YAML archetype examples (8 patterns), overnight-incident.yaml (existing pattern)
**Exploration:** quick
**Status:** captured
