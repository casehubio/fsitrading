# ADR-001: Blocks Patterns Replace Case Engine for Arena Orchestration

**Status:** Accepted
**Date:** 2026-08-11
**Decision:** D1

## Context

The Strategy Arena evaluates market signals through multiple strategy agents concurrently, aggregates their decisions via voting, and executes the consensus. The existing `StrategyEvaluationCaseDefinition` used the case engine's reactive binding model — designed for adaptive lifecycle management, not deterministic multi-agent orchestration.

## Decision

Use blocks orchestration patterns (`Sequence`, `Parallel`, `Voting`, `Conditional`) as the arena's orchestrator. The arena is an `ExecutionModel<ArenaContext>` built at startup and invoked per REST trigger.

## Alternatives Rejected

- **Embed** — blocks orchestration inside a case binding's capability. Case model assumes one capability → one result, fights concurrency.
- **Layer** — arena wraps individual per-strategy case instances. Adds indirection without value; individual cases would be trivial.

## Consequences

- `StrategyEvaluationCaseDefinition` and `StrategyEvaluator` SPI are retired
- Human approval gates call the engine's WorkItem service directly — no case lifecycle needed
- Loses case engine's declarative milestone/goal tracking (acceptable — arena completion is a single consensus check)
