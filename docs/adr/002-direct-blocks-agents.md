# ADR-002: Strategy Agents Implement Blocks Agent Interface Directly

**Status:** Accepted
**Date:** 2026-08-11
**Decision:** D2
**Depends on:** ADR-001

## Context

The `StrategyEvaluator` SPI was too thin for arena context — no access to positions, price history, or affordances. Keeping it means wrapping blocks' context through an adapter with no consumer on the other side.

## Decision

Retire `StrategyEvaluator` SPI. Each strategy type gets an `AgentRef.external()` wrapping its evaluation logic. Agents receive the full `ArenaContext` including `AffordanceRenderer` output and return `StrategyResponse` (Trade or Hold).

## Alternative Rejected

- **Adapt** — wrap `StrategyEvaluator` behind an adapter translating blocks context to SPI parameters. Preserves the SPI but no external repo imports it.

## Consequences

- Strategy logic depends on blocks API types (normal app → platform dependency direction)
- Seven concrete agent classes replace the SPI contract
- Abstention expressed via blocks' native `AgentResult.declined()` rather than returning empty
