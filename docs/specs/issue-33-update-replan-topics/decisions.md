# Design Decisions — #31 MarketEventType Sealed Hierarchy

## D1: Persistence approach

**Choice:** Keep MarketEventType enum for JPA/JSON + add parallel sealed interface MarketEvent
**Alternatives:**
- Replace enum with String + sealed interface — simpler domain model but loses enum-level validation in persistence
- Enum implementing marker interfaces — minimal change but no per-domain data shapes
**Rationale:** Two JPA entities (@Enumerated STRING) and REST API request bodies depend on the enum. The enum is the persistence discriminator; the sealed interface is the domain model. Clean separation — zero migration needed.
**Trade-offs:** Two parallel type systems (enum + sealed) must stay in sync. A factory method on MarketEventType bridges them.
**Sources:** `IncidentEntity.java:32`, `MarketEventEntity.java:27`, `ExternalIncidentRequest.java`, `SimulateRequest`
**Exploration:** quick
**Status:** captured

## D2: Data shape approach

**Choice:** Records per event type — each MarketEventType value gets its own record implementing the sealed interface
**Alternatives:**
- Records per domain — three records with union fields, some null depending on event type
- Interface-only — accessor methods only, no per-event records; doesn't solve the data shape problem
**Rationale:** Each record holds exactly the data relevant to that event source. FLASH_CRASH carries different data than COUNTERPARTY_FAILURE. Pattern matching becomes type-safe per event.
**Trade-offs:** 9 record classes. More files but each is small and focused.
**Sources:** Issue #31 description, `MarketEventTypeDescriptor.java` (existing source domain categorization)
**Exploration:** quick
**Status:** captured

## D3: Refactor scope

**Choice:** Structural first — sealed hierarchy with common type() + description() accessors. Per-event data enrichment deferred to C6.
**Alternatives:**
- Full data shapes now — complete per-event fields (magnitude, counterpartyId, gapSize). More upfront work.
- Enum-only grouping — nested sub-enums, no sealed interface. Minimal change but doesn't solve the root problem.
**Rationale:** Current code doesn't carry per-event-type data beyond the enum value. The structural hierarchy enables type-safe routing without inventing fields no consumer uses yet. C6 (CBR feature extraction) will need the per-event fields — add them then.
**Trade-offs:** Records initially carry only type() and description() — richer than the enum but not fully domain-specific yet.
**Sources:** Replan spec §C6 (CBR feature schema), `FsiIncidentTrigger.java` (inference logic)
**Exploration:** quick
**Status:** captured
