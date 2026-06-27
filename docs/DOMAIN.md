# FSI Trading Domain Background

This document gives a new session enough context to start designing and researching without external reading. It covers what automated trading is, the overnight bot management problem, where multi-agent coordination fits, and what makes CaseHub's approach different.

---

## What This Domain Covers

Three related but distinct concerns, unified by the need for multi-agent coordination with formal accountability:

1. **Automated trading** — strategy agents executing trades with trust-weighted selection, risk gates, and tamper-evident decision records
2. **Situation detection and response** — detecting market anomalies (flash crashes, liquidity events, gap opens) and coordinating response across specialist agents
3. **Overnight incident management** — autonomous agent operations outside market hours when human traders are unavailable, with formal escalation when anomalies exceed agent authority

The origin story: OpenClaw's most common use case was trading bots dealing with overnight scenarios. Rather than building this into OpenClaw (a framework-agnostic integration layer), it belongs as a CaseHub application where the full accountability stack — trust scoring, audit ledger, oversight gates, SLA enforcement — wraps the trading domain.

---

## How Automated Trading Works

### Market Microstructure Basics

Financial markets operate through order books — bid/ask queues where buy and sell orders are matched. Key concepts:

| Concept | What it means | Why it matters for agents |
|---------|---------------|--------------------------|
| **Bid/Ask spread** | Gap between best buy and best sell price | Narrow spread = liquid market; wide = illiquid or stressed |
| **Order book depth** | Volume available at each price level | Shallow depth = price impact risk for large orders |
| **Market impact** | Price movement caused by a trade | Large orders move the market — agents must size carefully |
| **Latency** | Time between decision and execution | Milliseconds matter for some strategies; seconds for others |
| **Slippage** | Difference between expected and actual execution price | Real-world execution cost — agents must account for it |
| **Liquidity** | Ability to enter/exit positions without significant price impact | Disappears in stress events — the overnight problem |

### Strategy Types (Agent Specialisations)

Each strategy type maps to a CaseHub `Worker` with a `Capability`:

| Strategy | What it does | Agent characteristics |
|----------|-------------|---------------------|
| **Market making** | Provides liquidity by quoting bid/ask | Continuous operation, high frequency, risk = inventory accumulation |
| **Statistical arbitrage** | Exploits price relationships between correlated instruments | Pairs/baskets, mean-reversion, medium frequency |
| **Momentum/trend following** | Follows price trends | Longer holding periods, lower frequency, risk = reversal |
| **Event-driven** | Trades around news/events | Requires NLP/sentiment, spiky activity around events |
| **Portfolio rebalancing** | Maintains target allocations | Periodic, rule-based, risk = tracking error |
| **Overnight risk management** | Monitors and adjusts positions during off-hours | Defensive, event-driven, escalation-heavy |

**Trust dimension:** each strategy agent builds a trust score based on P&L outcomes. An arb agent with consistent positive returns earns trust; one that generates losses on its claimed specialisation loses it. This feeds strategy selection via `TrustWeightedAgentStrategy`.

---

## The Overnight Problem

This is the core scenario that drives the application's design. When human traders go home:

### What Can Go Wrong

| Event | Severity | Required response |
|-------|----------|-------------------|
| **Flash crash** | Critical | Halt trading, protect capital, alert on-call |
| **Liquidity disappearance** | High | Reduce position sizes, widen limits, alert on-call |
| **Gap open** (market opens at a significantly different price) | High | Re-evaluate all positions against new prices |
| **Counterparty failure** | Critical | Close exposure to failing counterparty immediately |
| **Exchange outage** | Medium | Switch to backup venue or halt, depending on exposure |
| **News event** (geopolitical, earnings, central bank) | Variable | Re-evaluate affected positions, possibly hedge |
| **Margin call** | Critical | Liquidate positions or post collateral — time-bounded |
| **Bot malfunction** (runaway orders, stuck in a loop) | Critical | Kill the bot, reverse positions if possible |

### Why This Needs CaseHub

Without formal accountability:
- Bot decides to liquidate a position at 3 AM — who approved it? No record.
- Bot detects an anomaly but doesn't escalate — discovered at 8 AM. Losses compound for 5 hours.
- Bot escalates too aggressively — on-call trader gets paged for noise. Alert fatigue.
- Post-mortem: "What did the bot do between 2 AM and 6 AM?" — scattered logs, no decision chain.

With CaseHub:
- Every trade decision is a `Commitment` on the `work` channel with a `causedByEntryId` chain
- Anomaly detection fires on the `observe` channel via `casehub-ras` situation awareness
- High-severity events create a `WorkItem` for the on-call trader with an SLA breach policy
- The `ActionRiskClassifier` gates high-risk actions (large liquidations) through the `oversight` channel
- The full decision chain is in the tamper-evident ledger — regulators can reconstruct every step
- CBR: last month's flash crash response informs tonight's triage

---

## Key Domain Entities (Starting Point — Refine During Research)

| Entity | What it represents | CaseHub mapping |
|--------|-------------------|-----------------|
| `TradingStrategy` | A defined approach with parameters, risk limits, instrument scope | `Worker` with `Capability` tags |
| `Position` | Current holding: instrument, quantity, entry price, P&L | Domain record in `api/` |
| `Order` | Intent to trade: buy/sell, quantity, price, type (limit/market) | Domain record, audit via `LedgerEntry` |
| `MarketEvent` | Detected anomaly: flash crash, liquidity drop, gap, news | `CloudEvent` via stream adapter → `casehub-ras` |
| `TradeDecision` | Agent's decision with rationale | `WorkerResult` + `PlannedAction` if high-risk |
| `RiskLimit` | Threshold that gates agent authority | `ActionRiskClassifier` input |
| `OvernightIncident` | Detected situation requiring response outside hours | `CaseInstance` via `casehub-engine` |
| `EscalationWorkItem` | Human trader review/approval of agent action | `WorkItem` with SLA |
| `StrategyPerformance` | Track record per strategy agent | Feeds `ActorTrustScore` (Bayesian Beta) |

---

## Compliance Frameworks

### MiFID II (EU Markets in Financial Instruments Directive)
- **Article 17**: Firms using algorithmic trading must have effective systems and risk controls, maintain records of algorithmic trading orders, and make records available to regulators on request
- **RTS 6**: Specific requirements for algo trading — real-time monitoring, kill switches, pre-trade risk limits
- **Requirement:** Every algorithmic trading decision must be reconstructable. The ledger's `causedByEntryId` chain and Merkle proofs directly address this.

### Dodd-Frank (US)
- **Title VII**: Regulation of swaps and derivatives — trade reporting, clearing, margin requirements
- **Volcker Rule**: Restrictions on proprietary trading by banks
- **Requirement:** Complete audit trail of trading decisions, risk assessment, and compliance checks

### MAR (Market Abuse Regulation — EU)
- Prohibits insider dealing, market manipulation, unlawful disclosure
- **Surveillance obligation**: Firms must detect and report suspicious trading patterns
- **Relevance:** Agent trading patterns must be monitored for MAR compliance — an agent that inadvertently creates a pattern resembling market manipulation needs detection

### Basel III/IV (Banking)
- Capital adequacy, stress testing, market risk (FRTB — Fundamental Review of the Trading Book)
- **Relevance:** Position risk calculations feed capital requirements — agents must respect capital limits

---

## Market Data Integration

Market data is the heartbeat of a trading system. Multiple data types flow in real-time:

| Data type | Source | Frequency | CaseHub mapping |
|-----------|--------|-----------|-----------------|
| **Price ticks** | Exchange/broker feeds | Milliseconds–seconds | `CloudEvent` via `streams-kafka` or `streams-webhook` |
| **Order book snapshots** | Exchange L2/L3 data | Sub-second | `CloudEvent` with structured data payload |
| **Trade prints** | Exchange trade feed | Per-trade | `CloudEvent` |
| **Reference data** | Bloomberg, Reuters | Daily/periodic | `streams-poll` |
| **News/sentiment** | News APIs, social media | Event-driven | `streams-webhook` + LLM sentiment analysis |
| **Economic calendar** | Central bank announcements, earnings | Scheduled | `streams-poll` or manual `CloudEvent` |

**Design implication:** the `casehub-platform` stream modules (kafka, webhook, poll, camel) are the natural integration point. Price ticks map to `StateChangeEvent` / `CloudEvent` — structurally identical to IoT state changes. The `casehub-ras` Reticular Activating System can detect market situations (flash crash, liquidity disappearance) from the event stream using Drools CEP or Bayesian `Ganglion` strategies.

---

## Competitive Landscape

| Platform | Approach | What CaseHub adds |
|----------|----------|-------------------|
| **QuantConnect/Lean** | Open-source algo trading engine, backtesting + live | Single-agent, no coordination, no accountability |
| **Interactive Brokers TWS API** | Broker API for automated order execution | Execution only — no coordination, no risk gates |
| **MetaTrader 5** | Retail trading platform with Expert Advisors (EAs) | Single-agent bots, no trust scoring, no formal escalation |
| **KDB+/q** | High-performance time-series database for market data | Data layer only — no agent coordination |
| **Man Group / AHL** | Institutional systematic trading | Proprietary, not a platform others can use |
| **Traditional OMS/EMS** | Order/execution management systems | Rule-based, no LLM agents, no adaptive routing |

**The gap:** No existing platform provides multi-agent coordination with trust-weighted strategy selection, CBR from past market events, normative accountability for trading decisions, and oversight gates for high-risk actions — all with a tamper-evident audit trail that satisfies MiFID II Article 17.

---

## Research Directions — What to Explore

These are starting points for the first design session. Research the internet and Google Scholar for each.

### Core Architecture
- How do institutional trading firms coordinate multiple strategy agents today?
- What risk management architectures exist for multi-strategy funds?
- How do existing systems handle the overnight handoff from human to bot?
- What order management system (OMS) patterns should the domain model follow?

### The Overnight Scenario
- Academic work on automated anomaly detection in financial markets during off-hours
- Real flash crash case studies (2010, 2015 bond flash crash, 2019 Japanese yen) — model as CBR cases
- How do crypto markets (24/7) handle bot management and incident response?
- Circuit breaker patterns — exchange-level and firm-level

### CBR for Market Events
- How to represent market events as cases — which features drive similarity?
- Historical market event databases — can past incidents be ingested as seed cases?
- How to handle regime changes — a strategy that worked in low-vol markets fails in high-vol
- Seasonal/temporal patterns — overnight in Asia vs overnight in US vs weekend

### AI Fusion Opportunities
- LLM agents for market event narrative synthesis ("what happened overnight and why")
- LLM agents for natural language risk assessment from news feeds
- Sentiment analysis from financial news, earnings calls, central bank statements
- Anomaly explanation — why did the anomaly detector fire? Translate model features to trader-readable rationale
- Automated post-mortem generation from the ledger audit trail
- LLM-powered strategy parameter suggestion based on current market regime

### Real-Time Event Processing
- Drools CEP for temporal market pattern detection (three consecutive gap-downs within 2 hours)
- How `casehub-ras` Ganglion strategies map to market situation types
- Multi-signal fusion — combining price action, volume, order flow, and news signals

### Dashboard & Visualisation (casehub-pages)
- What do overnight monitoring dashboards look like at trading firms?
- Real-time P&L heatmaps across strategies and instruments
- Agent trust score timeline — which agents are gaining/losing credibility?
- Incident timeline with kill chain progression (anomaly → detection → analysis → response → resolution)
- Position risk visualisation — exposure by sector, geography, instrument type

### Platform Capabilities to Propose
Think about what trading needs that the platform doesn't have yet. File as `casehubio/parent` issues:
- Time-series analysis SPI — rolling windows, VWAP, volatility calculations
- Market calendar awareness — agents need to know trading hours, holidays, settlement cycles
- Backtesting infrastructure — replay historical market data through the agent coordination system
- Position sizing SPI — risk-adjusted position sizing that respects portfolio-level limits
- Multi-venue execution — route orders across exchanges based on liquidity and cost
- Agent capability for "market regime detection" — trending, mean-reverting, volatile, quiet
