# ADR-005: Pluggable Voting Aggregation by Instrument

**Status:** Accepted
**Date:** 2026-08-11
**Decision:** D5
**Depends on:** ADR-001

## Context

Strategies return `List<TradeDecision>` spanning any instruments. The arena needs to aggregate votes per instrument without baking instrument semantics into the pipeline.

## Decision

Aggregation is a pluggable `AggregationStrategy<ArenaContext>`. Default implementation (`FsiMajorityVoteByInstrument`) groups decisions by instrument and applies majority vote per group with routing-score-weighted quantities. Alternative strategies (weighted-by-trust, confidence-scored) can be swapped via `.strategy()` on `VotingBuilder`.

## Consequences

- Instrument grouping is a property of the aggregation, not the arena composition
- Deadlocks recorded inside `ConsensusResult` (per-instrument status), not via `AggregationResult.Deadlocked` — keeps the pipeline flowing
- Same arena composition works regardless of aggregation strategy
