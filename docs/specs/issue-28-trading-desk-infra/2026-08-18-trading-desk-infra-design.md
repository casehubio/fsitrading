# Trading Desk Infrastructure — Design Spec

Issue: casehubio/fsitrading#28
Branch: issue-28-trading-desk-infra

## Overview

Build the Trading Desk as a dock-workbench page using the pages TypeScript DSL. 8 panels from C1-C3 data contracts composed via DSL builders (`dataTable`, `metricGrid`, `heatmapChart`, `eventTimeline`, `masterDetail`, `hostPanel`). Fully real-time: composite data binding (REST initial + WebSocket live) for panels with push topics. New CDI events and push listener for position updates, P&L changes, trust score changes, and routing decisions. Layout persistence via SQLite-backed REST store. Single-file TypeScript composition rendered by `loadSite()`.

Deliberately supersedes the replan spec §5.4 which assumed 4 custom web components — casehub-pages#317 filled the DSL gaps, making pure DSL composition viable.

---

## §1 Dependency Bump

Bump `casehub-pages` npm packages to pick up the new DSL builders from pages#317.

Update `app/src/main/webui/package.json`:

```json
{
  "dependencies": {
    "@casehubio/pages-runtime": "file:../../.casehub-packages/pages-runtime",
    "@casehubio/pages-ui": "file:../../.casehub-packages/pages-ui",
    "@casehubio/pages-data": "file:../../.casehub-packages/pages-data"
  }
}
```

Maven unpacks the npm tarballs from `casehub-pages-npm` SNAPSHOT artifacts into `.casehub-packages/` (gitignored). The Quinoa build finds them via `file:` protocol links. This follows the pattern from the pages Quinoa template (`templates/quinoa-host/package.json`).

Add the Maven dependency to `app/pom.xml`:

```xml
<dependency>
  <groupId>io.casehub</groupId>
  <artifactId>casehub-pages-npm</artifactId>
  <version>${casehub-pages.version}</version>
  <type>tar.gz</type>
  <classifier>npm</classifier>
  <scope>provided</scope>
</dependency>
```

Add a Maven unpack execution in the `maven-dependency-plugin` to extract the npm packages before the Quinoa build runs.

Also add the `casehub-pages-layout-sqlite` dependency for the backend layout store:

```xml
<dependency>
  <groupId>io.casehub</groupId>
  <artifactId>casehub-pages-layout-sqlite</artifactId>
</dependency>
```

---

## §2 CDI Domain Events

Three new CDI event records fired from existing service methods. Follow the #26 pattern (CDI events → observer → broadcaster).

### PositionUpdatedEvent

```java
package io.casehub.fsitrading.app.service;

public record PositionUpdatedEvent(
        UUID positionId,
        String instrument,
        String assetClass,
        UUID strategyId,
        BigDecimal quantity,
        BigDecimal avgCost,
        BigDecimal realizedPnl,
        BigDecimal fillPrice,
        BigDecimal closedQuantity,
        Instant updatedAt) {}
```

**Fired from:** `PositionService.applyFill()`, after position state is updated and before the method returns. Constructed from the `FillResult` record. Carries both position state and fill details — the push listener uses `realizedPnl` presence to decide whether to also broadcast to `pnl:{strategyId}`.

### TrustScoreChangedEvent

```java
package io.casehub.fsitrading.app.arena;

public record TrustScoreChangedEvent(
        String strategyType,
        String actorId,
        double trustScore,
        int decisionCount,
        String phase) {}
```

**Fired from:** The arena completion path, after P&L attestation recording updates the trust score. The exact firing point depends on where attestations are recorded — likely `ArenaConfiguration`'s result handler after order execution and fill.

### RoutingDecisionEvent

```java
package io.casehub.fsitrading.app.arena;

public record RoutingDecisionEvent(
        UUID evaluationId,
        String instrument,
        List<String> selectedAgents,
        String routingStrategy,
        Instant decidedAt) {}
```

**Fired from:** `FsiArenaRouting.route()` or the arena evaluation result handler, after a routing decision is persisted as a `RoutingDecisionRecord`.

---

## §3 Trading Push Payloads

Type-discriminated payloads following the #26 pattern. Sealed interface with `type` field for client dispatch.

```java
package io.casehub.fsitrading.app.push;

public sealed interface TradingPushPayload {

    String type();

    record PositionUpdate(
            String type,
            UUID positionId,
            String instrument,
            String assetClass,
            UUID strategyId,
            BigDecimal quantity,
            BigDecimal avgCost,
            BigDecimal realizedPnl,
            Instant updatedAt) implements TradingPushPayload {
        public PositionUpdate(UUID positionId, String instrument, String assetClass,
                              UUID strategyId, BigDecimal quantity, BigDecimal avgCost,
                              BigDecimal realizedPnl, Instant updatedAt) {
            this("POSITION_UPDATE", positionId, instrument, assetClass,
                 strategyId, quantity, avgCost, realizedPnl, updatedAt);
        }
    }

    record PnlUpdate(
            String type,
            UUID strategyId,
            String instrument,
            BigDecimal realizedPnl,
            BigDecimal fillPrice,
            BigDecimal closedQuantity,
            Instant updatedAt) implements TradingPushPayload {
        public PnlUpdate(UUID strategyId, String instrument, BigDecimal realizedPnl,
                         BigDecimal fillPrice, BigDecimal closedQuantity, Instant updatedAt) {
            this("PNL_UPDATE", strategyId, instrument, realizedPnl,
                 fillPrice, closedQuantity, updatedAt);
        }
    }

    record TrustUpdate(
            String type,
            String strategyType,
            String actorId,
            double trustScore,
            int decisionCount,
            String phase) implements TradingPushPayload {
        public TrustUpdate(String strategyType, String actorId, double trustScore,
                           int decisionCount, String phase) {
            this("TRUST_UPDATE", strategyType, actorId, trustScore,
                 decisionCount, phase);
        }
    }

    record RoutingUpdate(
            String type,
            UUID evaluationId,
            String instrument,
            List<String> selectedAgents,
            String routingStrategy,
            Instant decidedAt) implements TradingPushPayload {
        public RoutingUpdate(UUID evaluationId, String instrument,
                             List<String> selectedAgents, String routingStrategy,
                             Instant decidedAt) {
            this("ROUTING_UPDATE", evaluationId, instrument, selectedAgents,
                 routingStrategy, decidedAt);
        }
    }
}
```

---

## §4 FsiTradingPushListener

Single `@ApplicationScoped` bean observing all trading CDI events and broadcasting to push topics. Follows the `FsiDeliberationPushListener` pattern.

```java
package io.casehub.fsitrading.app.push;

@ApplicationScoped
public class FsiTradingPushListener {

    private final FsiMarketPushService.PushBroadcaster broadcaster;

    @Inject
    public FsiTradingPushListener(io.casehub.pages.push.EventBroadcaster eventBroadcaster) {
        this.broadcaster = eventBroadcaster::broadcast;
    }

    FsiTradingPushListener(FsiMarketPushService.PushBroadcaster broadcaster) {
        this.broadcaster = broadcaster;
    }

    void onPositionUpdated(@Observes PositionUpdatedEvent event) {
        var posPayload = new TradingPushPayload.PositionUpdate(
                event.positionId(), event.instrument(), event.assetClass(),
                event.strategyId(), event.quantity(), event.avgCost(),
                event.realizedPnl(), event.updatedAt());
        broadcaster.broadcast("position:" + event.instrument(), posPayload);

        if (event.realizedPnl() != null && event.closedQuantity() != null) {
            var pnlPayload = new TradingPushPayload.PnlUpdate(
                    event.strategyId(), event.instrument(), event.realizedPnl(),
                    event.fillPrice(), event.closedQuantity(), event.updatedAt());
            broadcaster.broadcast("pnl:" + event.strategyId(), pnlPayload);
        }
    }

    void onTrustChanged(@Observes TrustScoreChangedEvent event) {
        var payload = new TradingPushPayload.TrustUpdate(
                event.strategyType(), event.actorId(), event.trustScore(),
                event.decisionCount(), event.phase());
        broadcaster.broadcast("trust:" + event.strategyType(), payload);
    }

    void onRoutingDecision(@Observes RoutingDecisionEvent event) {
        var payload = new TradingPushPayload.RoutingUpdate(
                event.evaluationId(), event.instrument(), event.selectedAgents(),
                event.routingStrategy(), event.decidedAt());
        broadcaster.broadcast("routing:latest", payload);
    }
}
```

### Push Topic Summary

| Topic | Payload type | Source | Rate |
|-------|-------------|--------|------|
| `position:{instrument}` | `POSITION_UPDATE` | PositionService.applyFill | Per fill |
| `pnl:{strategyId}` | `PNL_UPDATE` | PositionService.applyFill (when closing) | Per close |
| `trust:{strategyType}` | `TRUST_UPDATE` | Arena completion (attestation) | Per evaluation |
| `routing:latest` | `ROUTING_UPDATE` | Arena routing decision | Per evaluation |
| `market:ticks:{instrument}` | PriceTick | C2 pipeline (existing) | ~500ms |
| `market:bars:{instrument}` | OHLCV | C2 pipeline (existing) | ~1/min |
| `market:regime:{instrument}` | RegimeAssessment | C2 pipeline (existing) | ~1/hr |
| `deliberation:active` | DeliberationPushPayload | C3/#26 (existing) | Per deliberation |
| `deliberation:{channelId}` | ConvergenceUpdate | C3/#26 (existing) | Per message |

---

## §5 Layout Persistence

### Backend

Add REST endpoint for layout persistence backed by `casehub-pages-layout-sqlite`:

```java
package io.casehub.fsitrading.app.resource;

@Path("/api/layout")
@ApplicationScoped
public class LayoutResource {

    @Inject
    io.casehub.pages.layout.LayoutPersistenceStore layoutStore;

    @GET
    @Path("/{key}")
    public Response get(@PathParam("key") String key) {
        return layoutStore.load(key, "default", "default")
                .map(data -> Response.ok(data).build())
                .orElse(Response.status(404).build());
    }

    @PUT
    @Path("/{key}")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response put(@PathParam("key") String key, String body) {
        layoutStore.save(key, "default", "default", body);
        return Response.noContent().build();
    }
}
```

Note: `LayoutPersistenceStore.load/save/delete` take `(key, tenantId, userId)`. For C5a (single-tenant, no auth), hardcode `"default"` for both. When multi-tenancy or user auth is added, resolve from `SecurityIdentity`.

### Frontend

```typescript
import { createRestLayoutStore } from "@casehubio/pages-runtime";

const layoutStore = createRestLayoutStore("/api/layout");
```

Passed to the `page()` options via `save: { layoutStore, storageKey: "trading-desk" }`.

### Configuration

```properties
# application.properties — SQLite layout datasource (file-based, zero-config)
casehub.pages.layout.datasource=layout
quarkus.datasource.layout.db-kind=other
quarkus.datasource.layout.jdbc.driver=org.sqlite.JDBC
quarkus.datasource.layout.jdbc.url=jdbc:sqlite:data/layout.db
```

SQLite is used for both dev and prod — it's a file database with no server to start. The `casehub-pages-layout-sqlite` module handles schema migration internally.

---

## §6 Trading Desk Composition

Single TypeScript file using the pages DSL. All panels composed inline. Renders via `loadSite()`.

### Entry Point

`app/src/main/webui/src/index.ts`:

```typescript
import { loadSite } from "@casehubio/pages-runtime";
import { tradingDesk } from "./trading-desk";

const container = document.getElementById("app");
if (container) {
  loadSite(container, tradingDesk).catch(console.error);
}
```

### Host HTML

Replace `market-pulse.html` with `index.html`:

```html
<!DOCTYPE html>
<html lang="en">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1.0">
  <title>Trading Desk</title>
  <link rel="stylesheet" href="theme.css">
</head>
<body>
  <div id="app" style="height: 100vh;"></div>
  <script type="module" src="bundle.js"></script>
</body>
</html>
```

### Site Definition

`app/src/main/webui/src/trading-desk.ts`:

```typescript
import {
  page, dockWorkbench, dataTable, metric, metricGrid,
  heatmapChart, eventTimeline, masterDetail, hostPanel,
  badge, panel
} from "@casehubio/pages-ui";
import { fetchSource, wsSource, composite } from "@casehubio/pages-data";
import { createRestLayoutStore } from "@casehubio/pages-runtime";
import type { DockPanelConfig } from "@casehubio/pages-ui";

// --- Data sources ---

const positionsSource = composite(
  fetchSource("/api/positions"),
  wsSource("position:*")
);

const kpisSource = fetchSource("/api/kpis", { refreshTime: "30s" });

const trustSource = composite(
  fetchSource("/api/trust/strategies"),
  wsSource("trust:*")
);

const routingSource = composite(
  fetchSource("/api/routing/decisions?limit=20"),
  wsSource("routing:latest")
);

const deliberationsSource = composite(
  fetchSource("/api/deliberations"),
  wsSource("deliberation:active")
);

const strategiesSource = fetchSource("/api/strategies");

// --- Panels ---

const positionOverview: DockPanelConfig = {
  key: "positions",
  label: "Positions",
  icon: "table",
  defaultOpen: true,
  zone: "top",
  content: dataTable({
    dataSource: positionsSource,
    groupBy: "assetClass",
    rowStyle: (row) => (row.realizedPnl as number) >= 0 ? "pnl-positive" : "pnl-negative",
    columns: [
      { field: "instrument", header: "Symbol" },
      { field: "quantity", header: "Qty", align: "right" },
      { field: "avgCost", header: "Avg Cost", align: "right", format: "currency" },
      { field: "realizedPnl", header: "P&L", align: "right", format: "currency" },
    ]
  })
};

const pnlHeatmap: DockPanelConfig = {
  key: "pnl-heatmap",
  label: "P&L Heatmap",
  icon: "grid",
  defaultOpen: true,
  zone: "bottom",
  content: heatmapChart({
    dataSource: fetchSource("/api/kpis/heatmap"),
    xField: "instrument",
    yField: "strategy",
    valueField: "pnl",
    colorScale: "diverging"
  })
};

const strategies: DockPanelConfig = {
  key: "strategies",
  label: "Strategies",
  icon: "list",
  defaultOpen: true,
  fixed: true,
  content: masterDetail({
    master: dataTable({
      dataSource: strategiesSource,
      columns: [
        { field: "name", header: "Name" },
        { field: "strategyType", header: "Type" },
        { field: "active", header: "Active" },
      ]
    }),
    detail: dataTable({
      dataSource: fetchSource("/api/orders"),
      filterBy: "strategyId",
      columns: [
        { field: "instrument", header: "Symbol" },
        { field: "side", header: "Side" },
        { field: "quantity", header: "Qty", align: "right" },
        { field: "status", header: "Status" },
        { field: "fillPrice", header: "Fill", align: "right", format: "currency" },
      ]
    })
  })
};

const market: DockPanelConfig = {
  key: "market",
  label: "Market Pulse",
  icon: "chart",
  defaultOpen: true,
  content: hostPanel("fsi-market-panel")
};

const kpis: DockPanelConfig = {
  key: "kpis",
  label: "KPIs",
  icon: "meter",
  defaultOpen: true,
  content: metricGrid({ direction: "row" },
    metric({ label: "Total P&L", dataSource: kpisSource, field: "totalPnl", format: "currency", trend: "auto", sparklineData: "pnlHistory" }),
    metric({ label: "Win Rate", dataSource: kpisSource, field: "winRate", format: "percent" }),
    metric({ label: "Trades", dataSource: kpisSource, field: "tradeCount" }),
    metric({ label: "Avg Return", dataSource: kpisSource, field: "avgReturn", format: "currency" }),
  )
};

const trust: DockPanelConfig = {
  key: "trust",
  label: "Trust Scores",
  icon: "shield",
  defaultOpen: true,
  content: dataTable({
    dataSource: trustSource,
    columns: [
      { field: "strategyType", header: "Strategy" },
      { field: "trustScore", header: "Score", align: "right", format: "decimal:2" },
      { field: "phase", header: "Phase" },
      { field: "decisionCount", header: "Decisions", align: "right" },
    ]
  })
};

const routing: DockPanelConfig = {
  key: "routing",
  label: "Routing",
  icon: "route",
  defaultOpen: false,
  content: dataTable({
    dataSource: routingSource,
    columns: [
      { field: "instrument", header: "Instrument" },
      { field: "selectedAgents", header: "Agents" },
      { field: "routingStrategy", header: "Strategy" },
      { field: "decidedAt", header: "Time", format: "time" },
    ]
  })
};

const deliberation: DockPanelConfig = {
  key: "deliberation",
  label: "Deliberations",
  icon: "conversation",
  defaultOpen: false,
  content: eventTimeline({
    dataSource: deliberationsSource,
    timestampField: "startedAt",
    labelField: "instrument",
    stateField: "status",
    strategyKey: "chronological"
  })
};

const commitments: DockPanelConfig = {
  key: "commitments",
  label: "Commitments",
  icon: "check",
  defaultOpen: false,
  content: eventTimeline({
    dataSource: wsSource("deliberation:*"),
    timestampField: "endedAt",
    labelField: "instrument",
    stateField: "convergenceState",
    strategyKey: "chronological"
  })
};

const regimeBadge = badge({
  dataSource: wsSource("market:regime:*"),
  field: "regime",
  colorMap: {
    TRENDING: "green",
    VOLATILE: "red",
    RANGE_BOUND: "yellow",
    MEAN_REVERTING: "blue"
  }
});

// --- Desk composition ---

const layoutStore = createRestLayoutStore("/api/layout");

export const tradingDesk = page("Trading Desk",
  dockWorkbench({
    storageKey: "trading-desk",
    centre: [positionOverview.content, pnlHeatmap.content],
    left: {
      zones: 2,
      panels: [strategies, market, kpis]
    },
    right: {
      zones: 2,
      panels: [trust, routing, deliberation, commitments]
    },
    statusBar: regimeBadge
  }),
  {
    datasets: [
      { id: "positions", source: positionsSource },
      { id: "kpis", source: kpisSource },
      { id: "trust", source: trustSource },
      { id: "routing", source: routingSource },
      { id: "deliberations", source: deliberationsSource },
      { id: "strategies", source: strategiesSource },
    ],
    save: { layoutStore, storageKey: "trading-desk" }
  }
);
```

### Custom Component Registration

The existing `fsi-market-panel` is already registered via `customElements.define()`. The entry point imports it:

```typescript
import "./panels/fsi-market-panel";
```

If `masterDetail` is used with a strategy detail panel, a minimal `fsi-strategy-detail` hostPanel may need to be created — or the detail side can use a DSL composition (`dataTable` of orders filtered by selected strategy).

---

## §7 Quinoa Configuration

### application.properties

```properties
quarkus.quinoa.build-dir=dist
quarkus.quinoa.enable-spa-routing=false
quarkus.quinoa.package-manager-install=true
quarkus.quinoa.package-manager-install.node-version=20.11.1
```

### esbuild.config.mjs

Extend the existing esbuild config to bundle the pages runtime and DSL:

```javascript
import esbuild from "esbuild";

const watch = process.argv.includes("--watch");

import { copyFileSync } from "fs";

const ctx = await esbuild.context({
  entryPoints: ["src/index.ts"],
  bundle: true,
  outfile: "dist/bundle.js",
  format: "esm",
  target: "es2020",
  sourcemap: true,
  minify: !watch,
});

if (watch) {
  await ctx.watch();
} else {
  await ctx.rebuild();
  ctx.dispose();
}

// Copy static assets to dist/
copyFileSync("src/index.html", "dist/index.html");
```

### Build chain

Maven `generate-resources` → unpack npm tarballs → `npm install` → Quinoa runs esbuild → `dist/bundle.js` served as static resource.

---

## §8 Testing Strategy

### Java Backend

**TradingPushPayloadTest** (unit) — type discriminator assertions for all 4 payload records. Pattern: `DeliberationPushPayloadTest`.

**FsiTradingPushListenerTest** (unit) — `BroadcastCapture` list, fire CDI events, assert correct topics and payload types. Test that `onPositionUpdated` broadcasts to both `position:` and `pnl:` when realizedPnl is present, and only to `position:` when null.

**PositionService integration** — verify `PositionUpdatedEvent` fires after `applyFill()`. Use `DeliberationEventCaptor` pattern (standalone `@ApplicationScoped` bean with `@Observes` and getter methods).

**LayoutResource** (unit) — GET returns 404 for missing keys, 200 for existing. PUT saves and subsequent GET returns the saved value.

### TypeScript Frontend

**Build verification** — `npm run build` succeeds with no TypeScript errors. The esbuild bundle includes pages-runtime and pages-ui.

**Runtime verification** — start the Quarkus dev server, open the Trading Desk in a browser. Verify:
1. Dock-workbench renders with 8 panels in correct zones
2. Position table loads from REST API
3. Market panel connects to WebSocket and shows live ticks
4. KPI metrics display values from `/api/kpis`
5. Layout drag-and-drop works — rearrange a panel, refresh, verify persistence

---

## §9 File Inventory

### Java (backend)

| File | Action | Description |
|------|--------|-------------|
| `app/.../service/PositionUpdatedEvent.java` | Create | CDI event record |
| `app/.../arena/TrustScoreChangedEvent.java` | Create | CDI event record |
| `app/.../arena/RoutingDecisionEvent.java` | Create | CDI event record |
| `app/.../push/TradingPushPayload.java` | Create | Sealed interface with 4 payload records |
| `app/.../push/FsiTradingPushListener.java` | Create | CDI observer → push broadcaster |
| `app/.../service/PositionService.java` | Modify | Fire PositionUpdatedEvent after applyFill |
| `app/.../arena/ArenaConfiguration.java` | Modify | Fire TrustScoreChangedEvent + RoutingDecisionEvent |
| `app/.../resource/LayoutResource.java` | Create | REST endpoint for layout persistence |
| `app/.../resource/KpiResource.java` | Modify | Add `/api/kpis/heatmap` endpoint (P&L by instrument × strategy) |
| `app/pom.xml` | Modify | Add pages-npm + pages-layout-sqlite dependencies |
| `app/test/.../push/TradingPushPayloadTest.java` | Create | Payload type tests |
| `app/test/.../push/FsiTradingPushListenerTest.java` | Create | Push listener tests |

### TypeScript (frontend)

| File | Action | Description |
|------|--------|-------------|
| `app/src/main/webui/package.json` | Modify | Add pages-runtime, pages-ui, pages-data deps |
| `app/src/main/webui/src/index.ts` | Modify | Import trading-desk, call loadSite |
| `app/src/main/webui/src/trading-desk.ts` | Create | Dock-workbench composition |
| `app/src/main/webui/src/index.html` | Create | Host HTML (replaces market-pulse.html) |
| `app/src/main/webui/esbuild.config.mjs` | Create | Extended esbuild config |
| `app/src/main/webui/tsconfig.json` | Modify | Add pages type references |

### Configuration

| File | Action | Description |
|------|--------|-------------|
| `app/src/main/resources/application.properties` | Modify | Quinoa + layout datasource config |

---

## Known Limitations

### Composite source failure mode
If the WebSocket disconnects after the initial REST snapshot, panels show stale data with no automatic recovery to REST. Whether `wsSource` handles reconnection internally is a pages infrastructure question. For C5a (pre-release), acceptable. Production requires either pages-level WS reconnection or a stale-data visual indicator.

### REST-to-WS event gap
Events occurring between the REST snapshot and WebSocket connection are potentially missed. The pages push infrastructure has `EventStore` with sequence numbers for catch-up replay. Whether `composite` source uses these is undocumented. For C5a, acceptable — position/trust/routing events are low-frequency.

### Real-time cell updates
The pages DSL rendering pipeline's ability to provide Bloomberg-level UX (cell flashing, colour transitions on value changes) is untested. C5a delivers data binding and composition. Visual polish can be added via CSS or promoted to custom web components if needed.

### KPI heatmap endpoint
`/api/kpis/heatmap` returns P&L cross-tabulated by instrument × strategy. Added to the file inventory as a modification to `KpiResource`. Implementation: join positions, aggregate `realizedPnl` by `(instrument, strategyId)`, return as `{instrument, strategy, pnl}` rows.

---

## References

- `FsiDeliberationPushListener.java` — CDI event → push broadcaster pattern (proven in #26)
- `DeliberationPushPayload.java` — sealed interface payload pattern (proven in #26)
- `FsiMarketPushService.java:28-61` — existing push subscription pattern
- `PositionService.java:25-67` — applyFill (position update event source)
- `OrderService.java:38-48` — fill (order fill event source)
- `ArenaConfiguration.java` — arena result handler (trust/routing event source)
- `TrustScoreResource.java` — trust score REST API shape
- `pages/packages/pages-ui/src/dsl/builders.ts:466-560` — dock-workbench, hostPanel, split DSL
- `pages/packages/pages-data/src/datasource/sources/composite-source.ts` — composite data binding
- `pages/packages/pages-data/src/datasource/sources/ws-source.ts` — WebSocket data source
- `pages/templates/quinoa-host/` — Quinoa integration template
- `pages/backend/layout-sqlite/` — SQLite layout persistence
- casehub-pages#317 — DSL gap fills (heatmap, metricGrid, eventTimeline, masterDetail)
- Replan spec §5.1-5.7 — C5 Trading Desk design (superseded in §5.4 by D1)
- Decision review R1-01 through R1-12 — adversarial validation findings
