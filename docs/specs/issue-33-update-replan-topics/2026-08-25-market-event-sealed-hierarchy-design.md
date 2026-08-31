# MarketEventType Sealed Hierarchy — Design Spec

**Date:** 2026-08-25
**Issue:** #31
**Branch:** issue-33-update-replan-topics

---

## Summary

Replace the flat `MarketEventType` enum's implicit domain grouping (runtime `eventSource` string in `MarketEventTypeDescriptor`) with a compile-time sealed interface hierarchy. The enum stays for JPA/JSON persistence. The sealed interface adds type-safe domain routing and prepares the data model for C6 CBR feature extraction.

---

## §1 Problem

`MarketEventType` has 9 values from three distinct event source domains:

| Domain | Values | Ingest path |
|--------|--------|------------|
| Raw market data | PRICE_TICK, VOLUME_SPIKE | Price feed / summarisation pipeline |
| Detected/derived | FLASH_CRASH, LIQUIDITY_DROP, GAP_OPEN, CIRCUIT_BREAKER, NEWS_EVENT | Market analysis, CDI domain events |
| External operational | COUNTERPARTY_FAILURE, MARGIN_CALL | Broker webhook / clearing house API |

`MarketEventTypeDescriptor` knows this (`eventSource: "Market-detected"` vs `"External"`) but only at runtime. The compiler can't enforce domain-specific logic — a method that should only handle detected events accepts any `MarketEventType`.

`PRICE_TICK` and `VOLUME_SPIKE` are not in the descriptor registry at all — they never trigger incidents. They exist in the enum because the C2 market data pipeline uses the same type, but they have fundamentally different lifecycle semantics.

---

## §2 Design

### §2.1 MarketEventType Enum — Unchanged

The enum stays. JPA `@Enumerated(STRING)` in `IncidentEntity` and `MarketEventEntity`, JSON deserialization in `SimulateRequest` and `ExternalIncidentRequest`, and 118 existing references all continue to work.

Add a `domain()` method that returns the sealed sub-interface class:

```java
public enum MarketEventType {
    PRICE_TICK, VOLUME_SPIKE,
    FLASH_CRASH, LIQUIDITY_DROP, GAP_OPEN, CIRCUIT_BREAKER, NEWS_EVENT,
    COUNTERPARTY_FAILURE, MARGIN_CALL;

    public Class<? extends MarketEvent> domain() {
        return switch (this) {
            case PRICE_TICK, VOLUME_SPIKE -> MarketEvent.RawMarketData.class;
            case FLASH_CRASH, LIQUIDITY_DROP, GAP_OPEN, CIRCUIT_BREAKER, NEWS_EVENT -> MarketEvent.DetectedEvent.class;
            case COUNTERPARTY_FAILURE, MARGIN_CALL -> MarketEvent.OperationalEvent.class;
        };
    }
}
```

### §2.2 MarketEvent Sealed Interface — New

```java
package io.casehub.fsitrading.model;

import java.time.Instant;

public sealed interface MarketEvent {
    MarketEventType type();
    String description();
    Instant occurredAt();

    sealed interface RawMarketData extends MarketEvent
            permits PriceTick, VolumeSpike {}

    sealed interface DetectedEvent extends MarketEvent
            permits FlashCrash, LiquidityDrop, GapOpen, CircuitBreaker, NewsEvent {}

    sealed interface OperationalEvent extends MarketEvent
            permits CounterpartyFailure, MarginCall {}

    record PriceTick(String description, Instant occurredAt) implements RawMarketData {
        @Override public MarketEventType type() { return MarketEventType.PRICE_TICK; }
    }

    record VolumeSpike(String description, Instant occurredAt) implements RawMarketData {
        @Override public MarketEventType type() { return MarketEventType.VOLUME_SPIKE; }
    }

    record FlashCrash(String description, Instant occurredAt) implements DetectedEvent {
        @Override public MarketEventType type() { return MarketEventType.FLASH_CRASH; }
    }

    record LiquidityDrop(String description, Instant occurredAt) implements DetectedEvent {
        @Override public MarketEventType type() { return MarketEventType.LIQUIDITY_DROP; }
    }

    record GapOpen(String description, Instant occurredAt) implements DetectedEvent {
        @Override public MarketEventType type() { return MarketEventType.GAP_OPEN; }
    }

    record CircuitBreaker(String description, Instant occurredAt) implements DetectedEvent {
        @Override public MarketEventType type() { return MarketEventType.CIRCUIT_BREAKER; }
    }

    record NewsEvent(String description, Instant occurredAt) implements DetectedEvent {
        @Override public MarketEventType type() { return MarketEventType.NEWS_EVENT; }
    }

    record CounterpartyFailure(String description, Instant occurredAt) implements OperationalEvent {
        @Override public MarketEventType type() { return MarketEventType.COUNTERPARTY_FAILURE; }
    }

    record MarginCall(String description, Instant occurredAt) implements OperationalEvent {
        @Override public MarketEventType type() { return MarketEventType.MARGIN_CALL; }
    }
}
```

### §2.3 Factory Method

Bridge from enum to sealed hierarchy. Used when constructing events from persistence or API input:

```java
// On MarketEventType enum:
public MarketEvent toEvent(String description, Instant occurredAt) {
    return switch (this) {
        case PRICE_TICK -> new MarketEvent.PriceTick(description, occurredAt);
        case VOLUME_SPIKE -> new MarketEvent.VolumeSpike(description, occurredAt);
        case FLASH_CRASH -> new MarketEvent.FlashCrash(description, occurredAt);
        case LIQUIDITY_DROP -> new MarketEvent.LiquidityDrop(description, occurredAt);
        case GAP_OPEN -> new MarketEvent.GapOpen(description, occurredAt);
        case CIRCUIT_BREAKER -> new MarketEvent.CircuitBreaker(description, occurredAt);
        case NEWS_EVENT -> new MarketEvent.NewsEvent(description, occurredAt);
        case COUNTERPARTY_FAILURE -> new MarketEvent.CounterpartyFailure(description, occurredAt);
        case MARGIN_CALL -> new MarketEvent.MarginCall(description, occurredAt);
    };
}
```

### §2.4 MarketEventTypeDescriptor — Derive eventSource

The `eventSource` field (`"Market-detected"`, `"External"`) becomes derivable from `type.domain()`:

```java
public String eventSource() {
    return switch (eventType.domain().getSimpleName()) {
        case "RawMarketData" -> "Raw";
        case "DetectedEvent" -> "Market-detected";
        case "OperationalEvent" -> "External";
        default -> "Unknown";
    };
}
```

The `eventSource` parameter is removed from the constructor. The REGISTRY entries drop that field.

---

## §3 Consumer Impact

### §3.1 No-change consumers

All existing `switch` on `MarketEventType` enum values still compile. No consumer is forced to adopt the sealed hierarchy. This is additive.

### §3.2 Consumers that benefit from the hierarchy

| Consumer | Current | With hierarchy |
|----------|---------|---------------|
| `OvernightIncidentCaseDescriptor.agentNameFor()` | Switch on enum | Can use `instanceof DetectedEvent` vs `OperationalEvent` for domain-level routing |
| `FsiIncidentTrigger.classifySeverity()` | Switch on enum with some values treated differently | Can branch on domain first, then specific type |
| `FsiIncidentTrigger.inferEventType()` | Returns enum | Can return specific sealed record |
| C6 CBR feature extraction (future) | N/A | Pattern match on specific records for domain-specific features |

### §3.3 Consumers that must change

| Consumer | Change needed | Reason |
|----------|--------------|--------|
| `MarketEventTypeDescriptor` | Remove `eventSource` constructor param, derive from `domain()` | Source category is now in the type system |
| `MarketEventTypeDescriptorTest` | Update assertions for derived `eventSource()` | Constructor signature change |

---

## §4 Testing

**MarketEventTest** (new, api module) — verify:
- All 9 enum values have a corresponding sealed record via `toEvent()`
- `domain()` returns correct sub-interface class per value
- `type()` on each record returns the correct enum value
- `instanceof` checks: `FlashCrash instanceof DetectedEvent`, `CounterpartyFailure instanceof OperationalEvent`, etc.

**MarketEventTypeDescriptorTest** — update for derived `eventSource()`.

**No test changes needed** for consumers that still switch on the enum (all existing tests pass unchanged).

---

## §5 File Inventory

### Java

| File | Action | Description |
|------|--------|-------------|
| `api/.../model/MarketEvent.java` | Create | Sealed interface + 9 records |
| `api/.../model/MarketEventType.java` | Modify | Add `domain()` and `toEvent()` methods |
| `api/.../model/MarketEventTypeDescriptor.java` | Modify | Derive `eventSource` from `domain()`, remove from constructor |
| `api/test/.../model/MarketEventTest.java` | Create | Tests for sealed hierarchy, factory, domain mapping |
| `api/test/.../model/MarketEventTypeDescriptorTest.java` | Modify | Update for derived `eventSource()` |
| `docs/guides/consumer-guide.md` | Modify | Document new sealed hierarchy in domain model section |

---

## §6 Known Limitations

1. **Records carry only `description` + `occurredAt`** — per-event-type domain fields (magnitude, counterpartyId, gapSize) deferred to C6 CBR feature extraction. The hierarchy is structurally ready for enrichment.

2. **No consumer is forced to use the hierarchy** — existing enum switches still work. This is intentional (additive refactor), but it means the type safety benefit is opt-in until consumers are migrated.

3. **`MarketEvent.MarginCall` clashes with `MarketEventType.MARGIN_CALL`** — the record is nested inside `MarketEvent`, so the fully-qualified name is `MarketEvent.MarginCall`. No conflict with the enum value.

---

## References

- `MarketEventType.java:3-13` — current flat enum
- `MarketEventTypeDescriptor.java:5-31` — runtime source domain categorization
- `IncidentEntity.java:32` — JPA @Enumerated(STRING) persistence
- `MarketEventEntity.java:27` — JPA @Enumerated(STRING) persistence
- `FsiIncidentTrigger.java:100-121` — severity classification and event inference
- `OvernightIncidentCaseDescriptor.java:51` — agent routing by event type
- Issue #31 — original problem statement
- Replan spec §4.3 — conditional routing table (7 event types → agents)
