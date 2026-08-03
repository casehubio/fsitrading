# casehub-fsitrading — Consumer Guide

> Multi-agent trading automation, overnight bot management, and regulatory compliance for algorithmic trading.

**GitHub:** [casehubio/fsitrading](https://github.com/casehubio/fsitrading)
**Tier:** Application

---

## Purpose

Financial Services Trading application. Multi-agent trading automation, overnight bot management, market situation detection and response, and regulatory compliance for algorithmic trading (MiFID II, Dodd-Frank, MAR).

---

## Module Structure

| Module | Type | Purpose |
|---|---|---|
| `casehub-fsitrading-api` | Pure-Java SPI (no Quarkus) | Domain model, SPI interfaces, capability tags |
| `casehub-fsitrading-app` | Quarkus application | REST resources, JPA entities, foundation wiring, case plan models |

---

## Current State

Scaffold only — Maven structure, documentation, workspace ready. No implementation yet.

Domain research and design phase is the immediate next step.
