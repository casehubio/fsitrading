# casehub-fsitrading — Contributor Guide

> Multi-agent trading automation, overnight bot management, and regulatory compliance for algorithmic trading.

**GitHub:** [casehubio/fsitrading](https://github.com/casehubio/fsitrading)

---

## Module Structure

| Module | Type | Purpose |
|---|---|---|
| `casehub-fsitrading-api` | Pure-Java SPI (no Quarkus) | Domain model, SPI interfaces, capability tags |
| `casehub-fsitrading-app` | Quarkus application | REST resources, JPA entities, foundation wiring, case plan models |

---

## Platform Primitives Used

| Primitive | Usage |
|---|---|
| Trust-weighted selection | Bayesian Beta scoring from P&L outcome attestations per strategy agent |
| CBR (Case-Based Reasoning) | Past market events (flash crashes, liquidity events, overnight gaps) inform future detection and response |
| Oversight gates | `ActionRiskClassifier` gates high-risk trade authorization requiring human approval |
| Commitment lifecycle | Response SLA enforcement via `SlaBreachPolicy` with escalation to on-call trader |
| Audit ledger | Tamper-evident regulatory compliance — Merkle inclusion proofs for decision records |
| Stream modules | Market data ingestion via CloudEvent adapters (kafka, webhook, poll) |

---

## Current State

Scaffold — Maven structure, documentation, workspace ready. No implementation yet.

Domain research and design phase is the immediate next step.

---

## Design Documents

- `CLAUDE.md` — project conventions and design philosophy
- `docs/DOMAIN.md` — full domain background (automated trading, market microstructure, compliance frameworks)
