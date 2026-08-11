# ADR-003: Multi-Select Routing via Platform Bridge

**Status:** Accepted
**Date:** 2026-08-11
**Decision:** D3
**Depends on:** ADR-001, ADR-002

## Context

The arena needs multi-select routing (choose several strategies above a threshold) but the platform's routing strategies are single-select (`AgentRoutingStrategy`) or per-candidate scoring (`RoutingSignalProvider`).

## Decision

`FsiArenaRouting` implements blocks' `RoutingStrategy<ArenaContext>` as a multi-select adapter. Composes all 6 routing strategies via `RoutingSignalAssembler` (public SPI) plus direct LLM/CBR invocation. LLM/CBR single-select results are converted to binary scores (1.0/0.0) and blended with continuous signal-provider scores. All candidates above a configurable threshold (default 0.3) are selected.

## Alternatives Rejected

- **ComposableAgentRoutingStrategy** — engine-internal package, single-select only
- **Scored ensemble** — reimplements what `RoutingSignalAssembler` already does
- **Tiered fallback** — LLM primary, others supplementary. Underutilises signal providers

## Consequences

- Type translation between blocks types and engine routing types is non-trivial but well-defined
- LLM/CBR binary signals provide ~1/6 weight in blending — sufficient to influence selection but without graduated confidence (C6 improvement)
- Adapter fabricates synthetic fields (`caseId`, `tenancyId`) for engine types — safe, no runtime interprets them
