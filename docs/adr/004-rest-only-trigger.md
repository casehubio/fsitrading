# ADR-004: REST-Only Arena Trigger for C1

**Status:** Accepted
**Date:** 2026-08-11
**Decision:** D4

## Context

Production trading systems react to market events, not REST calls. But C1's job is to prove the orchestration pipeline works correctly before adding event-driven complexity.

## Decision

Arena triggered exclusively via `POST /api/evaluations/trigger`. No auto-triggering on market events. C2 (Market Pulse) adds event-driven triggering when the summarisation pipeline lands.

## Consequences

- Testable and controllable — explicit trigger makes debugging straightforward
- REST endpoint becomes the integration point for C2's event layer
- Not production-realistic until C2
