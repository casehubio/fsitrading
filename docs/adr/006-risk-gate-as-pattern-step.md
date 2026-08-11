# ADR-006: Risk Gate as Pattern Step, Not Post-Processing

**Status:** Accepted
**Date:** 2026-08-11
**Decision:** D6
**Depends on:** ADR-001, ADR-005

## Context

Risk assessment and human approval are part of the trading decision chain. Implementing them as procedural post-processing after voting would make them invisible to accountability listeners.

## Decision

Risk assessment and human approval gates are blocks pattern steps within the arena Sequence. `FsiRiskAssessor` is an ExternalAgent step; `FsiRiskGateRouting` is a `RoutingStrategy<ArenaContext>` on a Conditional step that routes HIGH/CRITICAL risk to `AgentRef.human()` and LOW/MEDIUM to pass-through.

## Consequences

- All 3 accountability listeners capture every step including the risk gate
- Full composition: Sequence[Routing → Evaluation → Voting → Risk → Gate → Execute]
- `ArenaContext` carries accumulated state through all steps (triage, evaluations, consensus, risk, approval, execution)
- No step is outside the accountability boundary — MiFID II Art.17 compliance
