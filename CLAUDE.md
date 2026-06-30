# fsitrading Workspace

**Name:** casehub-fsitrading

**Project repo:** /Users/mdproctor/claude/casehub/fsitrading
**Workspace:** /Users/mdproctor/claude/public/casehub/fsitrading
**Workspace type:** public

## Session Start

Run `add-dir /Users/mdproctor/claude/casehub/fsitrading` before any other work.

## Artifact Locations

| Skill | Writes to |
|-------|-----------|
| brainstorming (specs) | `specs/` |
| writing-plans (plans) | `plans/` |
| handover | `HANDOFF.md` |
| idea-log | `IDEAS.md` |
| design-snapshot | `snapshots/` |
| java-update-design / update-primary-doc | `design/JOURNAL.md` (created by `epic`) |
| adr | `adr/` |
| write-blog | `blog/` |

## Structure

- `HANDOFF.md` — session handover (single file, overwritten each session)
- `IDEAS.md` — idea log (single file)
- `specs/` — brainstorming / design specs (superpowers output)
- `plans/` — implementation plans (superpowers output)
- `snapshots/` — design snapshots with INDEX.md (auto-pruned, max 10)
- `adr/` — architecture decision records with INDEX.md
- `blog/` — project diary entries with INDEX.md
- `design/` — epic journal (created by `epic` at branch start)

## Git Discipline

Two git repositories are active in every session: a **workspace** (methodology artifacts: handover, blog, specs, plans, ADRs) and the **project repo** (source code).

Before any git operation, run `git rev-parse --show-toplevel` to confirm which repo is currently active. Do not assume — the session may have opened in either. cd to the correct repo before staging:
- Source code commits → project repo
- Methodology artifacts → workspace

**Pre-push hook (`core.hooksPath = .githooks`):** The project repo has a hook that blocks all pushes containing commits — including empty ones. All `chore: branch closed` stamps require `--no-verify` on push, regardless of whether the branch has an existing remote upstream. See garden entry GE-20260531-2f51fd for root cause detail.

## Rules

- All methodology artifacts go here, not in the project repo
- Promotion to project repo is always explicit — never automatic
- Workspace branches mirror project branches — switch both together

## Routing

| Artifact   | Destination | Notes |
|------------|-------------|-------|
| adr        | project     | lands in `docs/adr/` — promoted at epic close |
| specs      | project     | lands in `docs/specs/` — promoted at epic close |
| blog       | workspace   | staged here; published to mdproctor.github.io via publish-blog |
| plans      | workspace   | stay in workspace permanently |
| design     | workspace   | epic journal stays in workspace |
| snapshots  | workspace   | stay in workspace permanently |
| handover   | workspace   | |

---

# casehub-fsitrading — Claude Code Project Guide

## Platform Context

This repo is one component of the casehubio multi-repo platform. **Before implementing anything — any feature, SPI, data model, or abstraction — run the Platform Coherence Protocol.**

> **Platform docs:** Local paths use `../parent/docs/` as root. If a path doesn't exist, the parent repo isn't cloned locally — fetch from `https://raw.githubusercontent.com/casehubio/parent/main/docs/<path>` instead.

The protocol asks: Does this already exist elsewhere? Is this the right repo for it? Does this create a consolidation opportunity? Is this consistent with how the platform handles the same concern in other repos?

**Platform architecture (read BEFORE any implementation decision):**
```
../parent/docs/PLATFORM.md
../parent/docs/APPLICATIONS.md
../parent/docs/AGENTIC-HARNESS-GUIDE.md
../parent/docs/LIFECYCLE.md
../parent/docs/CHANNELS.md
../parent/docs/CBR-CAPABILITY.md
../parent/docs/ARCHITECTURE.md
```

**Foundation repo deep-dives (read ALL of these during initial research — understand what the platform provides):**
- casehub-engine: `../parent/docs/repos/casehub-engine.md`
- casehub-ledger: `../parent/docs/repos/casehub-ledger.md`
- casehub-work: `../parent/docs/repos/casehub-work.md`
- casehub-qhorus: `../parent/docs/repos/casehub-qhorus.md`
- casehub-platform: `../parent/docs/repos/casehub-platform.md`
- casehub-eidos: `../parent/docs/repos/casehub-eidos.md`
- casehub-connectors: `../parent/docs/repos/casehub-connectors.md`
- casehub-iot: `../parent/docs/repos/casehub-iot.md`
- casehub-ops: `../parent/docs/repos/casehub-ops.md`
- casehub-neural-text: `../parent/docs/repos/casehub-neural-text.md`
- casehub-worker: `../parent/docs/repos/casehub-worker.md`
- casehub-openclaw: `../parent/docs/repos/casehub-openclaw.md`
- claudony: `../parent/docs/repos/claudony.md`
- casehub-drafthouse: `../parent/docs/repos/casehub-drafthouse.md`

**Application repo deep-dives (learn from how other apps use the platform):**
- casehub-aml: `../parent/docs/repos/casehub-aml.md`
- casehub-clinical: `../parent/docs/repos/casehub-clinical.md`
- casehub-life: `../parent/docs/repos/casehub-life.md`
- casehub-devtown: `../parent/docs/repos/casehub-devtown.md`

**Use IntelliJ MCP to browse peer repo source code directly.** When designing SPIs, domain model, or integration patterns, use `mcp__intellij-index__ide_find_class`, `ide_find_references`, `ide_type_hierarchy` etc. to see exactly how other repos (especially casehub-aml, casehub-clinical, casehub-life) implement the same foundation patterns. Copy proven patterns — don't invent parallel approaches.

---

## Project Type

type: java

**Stack:** Java 21 (on Java 26 JVM), Quarkus 3.32.2, GraalVM 25 (native image target)

---

## Session Start

Read `AGENTIC-HARNESS-GUIDE.md` at session start alongside this CLAUDE.md.
Path: `../parent/docs/AGENTIC-HARNESS-GUIDE.md`

---

## What This Project Is

`casehub-fsitrading` is the **Financial Services Trading** application built on the CaseHub platform foundation.

**Full domain background — read first:** [`docs/DOMAIN.md`](docs/DOMAIN.md) — what automated trading is, the overnight bot management problem, market microstructure, strategy types, key entities, compliance frameworks (MiFID II, Dodd-Frank, MAR), competitive landscape, market data integration patterns, and research directions. That document gives a new session everything it needs to start researching and designing without external reading.

This is an **application layer**, not a framework. The foundation provides coordination, accountability, audit, and compliance primitives. casehub-fsitrading provides the trading domain logic on top.

### Design Philosophy — Be Bold, Be Adventurous

**Do not limit design to what CaseHub provides today.** Design for what the platform is becoming:

1. **CBR (Case-Based Reasoning)** — design market event response as a CBR system from day one. Past market incidents (flash crashes, liquidity events, overnight gaps) feed future detection and response. Use `CaseRetriever` SPI even though the RAG pipeline is still maturing. Plan for Retrieve/Reuse/Revise/Retain. Read `../parent/docs/CBR-CAPABILITY.md`.

2. **casehub-blocks** — the reusable building blocks repo is being created. Design trading patterns (risk approval gate, strategy debate, escalation to human trader, multi-agent consensus on position sizing) as blocks candidates. Implement locally first, then propose extraction.

3. **AI fusion** — think beyond rule-based agents. Where can LLM agents add value? Market narrative synthesis? Anomaly explanation? Natural language trade rationale? Sentiment analysis from news feeds? Design SPIs that allow LLM-powered and rule-based agents to coexist with the same trust model.

4. **Propose new platform capabilities.** If trading needs something the platform doesn't have, file it as a parent issue. Examples: real-time market data streaming (casehub-ras for situation detection), temporal pattern detection (Drools CEP for market patterns), time-series analysis SPIs. Push the platform forward.

5. **Pages UI** — design the trading application UI using casehub-pages. Position overview, P&L timeline, agent trust scores, strategy performance, overnight incident log, market heat maps. Use `hostPanel()` for custom trading-specific components; use the DSL for data-bound visualizations. Pages is embedded via iframe — design the data contracts (datasets) and panel layout that the application will consume. Drive requirements upstream if trading needs primitives casehub-pages doesn't have yet.

6. **casehub-iot for market data** — the IoT layer's `StateChangeEvent` / `CloudEvent` pattern maps to market data feeds. Price ticks, order book changes, and market status events are structurally identical to IoT state changes. Explore using the stream modules (kafka, webhook, poll) for market data ingestion.

### Platform Coherence is Critical

**Consistency with the platform is not optional.** Every pattern, convention, and naming decision must align with the foundation. Before implementing anything:
1. Read PLATFORM.md — understand capability ownership and boundary rules
2. Use IntelliJ MCP to browse peer repos — see how they solve the same problem
3. Follow the module tier structure — pure-Java SPI in api/, JPA + Quarkus in app/
4. Follow the CDI displacement pattern — @DefaultBean mocks, @Alternative overrides
5. Follow naming conventions — check garden protocols

---

## Accountability Properties Delivered

| Regulatory requirement | Without casehub-fsitrading | With casehub-fsitrading |
|---|---|---|
| Auditable trade decision chain (MiFID II Art.17) | Scattered logs; no decision attribution | Commitment per agent task; `causedByEntryId` chains the full decision path |
| Human authorization for high-risk trades | Manual approval via chat; no formal gate | WorkItem with oversight gate; `ActionRiskClassifier` gates large/unusual trades |
| Algorithmic trading compliance (Dodd-Frank) | Post-hoc reports; no real-time audit | Merkle inclusion proofs; tamper-evident decision record |
| Trust-weighted strategy selection | Equal weighting or manual override | Bayesian Beta from P&L outcome attestations per strategy agent |
| Market event knowledge retention | Tribal knowledge; playbooks stale within months | CBR: past market events automatically inform future response |
| Overnight incident SLA | Nobody watching; morning discovery | `SlaBreachPolicy` with escalation to on-call trader; agents have response deadlines |

---

## Layering Rule

This is an application, not a framework. If the capability requires knowledge of financial markets, trading strategies, market microstructure, or financial regulation (MiFID II, Dodd-Frank, MAR), it belongs here. If it is purely about cases, commitments, trust, or audit records, it belongs in the foundation. Never re-implement foundation primitives here.

---

## Build Commands

```bash
JAVA_HOME=$(/usr/libexec/java_home -v 26) mvn --batch-mode install
JAVA_HOME=$(/usr/libexec/java_home -v 26) mvn --batch-mode install -DskipTests
```

Use `mvn` not `./mvnw` — maven wrapper not configured on this machine.

---

## Ecosystem Conventions

**Quarkus version:** 3.32.2. When bumping, bump all projects together.

**GitHub Packages:**
```xml
<repository>
  <id>github</id>
  <url>https://maven.pkg.github.com/casehubio/*</url>
  <snapshots><enabled>true</enabled></snapshots>
</repository>
```

**Java:** `JAVA_HOME=$(/usr/libexec/java_home -v 26)`

**Multi-module test scoping:** Always scope Maven with `-pl <module> -am`. Add `-Dsurefire.failIfNoSpecifiedTests=false` when combining `-am` with `-Dtest=ClassName`.

---

## Development Workflow

Before designing: `superpowers:brainstorming`
Before implementing: `superpowers:test-driven-development`
Before committing: `superpowers:requesting-code-review`

## Peer Repos — Hard Boundary

**Never commit or push to peer repo directories.** Each repo has its own Claude session. For cross-repo fixes, create a GitHub issue on the target repo instead.

Peer repos: platform, eidos, ledger, connectors, iot, work, worker, qhorus, pages, engine, claudony, openclaw, neural-text, devtown, aml, clinical, drafthouse, life, quarkmind, flow, desiredstate, soc

## Project Artifacts

Paths that are project content (not workspace noise). Skills use this to avoid
filtering or dropping commits that touch these paths.

| Path | What it is |
|------|------------|
| `CLAUDE.md` | Project conventions |
| `docs/` | Domain background, specs, ADRs |

## Work Tracking

**Issue tracking:** enabled
**GitHub repo:** casehubio/fsitrading

**Automatic behaviours:**
- Before implementation begins — check for an active issue. If none, run issue-workflow Phase 1 before writing any code.
- Every issue must be linked to its parent epic — no orphan issues.
- Before any commit — confirm issue linkage.
- All commits reference an issue — `Refs #N` or `Closes #N`. No commit may be made without an issue reference.

## IntelliJ MCP Routing

One IntelliJ MCP server is available:

- **`mcp__intellij-index__*`** — use this for ALL code intelligence and navigation. Supports auto-opening projects via `project_path`. Pass `project_path` to auto-open any closed project — never ask the user to open a project manually.

`mcp__intellij__*` (built-in JetBrains MCP) is **disabled** due to a memory leak. Do not attempt to use it. All operations (find class, find references, type hierarchy, diagnostics, rename, move) go through `mcp__intellij-index__*`.

## Writing Style Guide

**The writing style guide at `~/claude-workspace/writing-styles/blog-technical.md` is mandatory for all blog and diary entries.** Load it in full before drafting.
