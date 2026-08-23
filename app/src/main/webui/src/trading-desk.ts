import {
  page, dockWorkbench, dataTable, metric, metricGrid,
  hostPanel, split, lookup,
} from "@casehubio/pages-ui";
import { heatmapChart, eventTimeline, badge } from "@casehubio/pages-ui/dist/dsl/builders.js";
import { fetchSource, composite, dataSetId } from "@casehubio/pages-data";
import { createRestLayoutStore } from "@casehubio/pages-runtime";
import type { DataSourceBinding } from "@casehubio/pages-data";
import type { DockPanelConfig } from "@casehubio/pages-ui";
import { topicSource } from "./topic-source";

const positionsSource = composite(
  fetchSource("/api/positions"),
  topicSource(["position:*"], { keyField: "instrument" }),
);
const kpisSource = fetchSource("/api/kpis");
const heatmapSource = fetchSource("/api/kpis/heatmap");
const trustSource = composite(
  fetchSource("/api/trust/strategies"),
  topicSource(["trust:*"], { keyField: "strategyType" }),
);
const routingSource = composite(
  fetchSource("/api/routing/decisions?limit=20"),
  topicSource(["routing:latest"], { accumulate: false }),
);
const deliberationsSource = composite(
  fetchSource("/api/deliberations"),
  topicSource(["deliberation:active"], { accumulate: false }),
);
const strategiesSource = fetchSource("/api/strategies");
const ordersSource = fetchSource("/api/orders");

const positionOverview: DockPanelConfig = {
  key: "positions",
  label: "Positions",
  icon: "table",
  defaultOpen: true,
  zone: "top",
  content: dataTable({
    lookup: lookup("positions"),
  }),
};

const pnlHeatmap: DockPanelConfig = {
  key: "pnl-heatmap",
  label: "P&L Heatmap",
  icon: "grid",
  defaultOpen: true,
  zone: "bottom",
  content: heatmapChart({
    lookup: lookup("heatmap"),
  }),
};

const strategies: DockPanelConfig = {
  key: "strategies",
  label: "Strategies",
  icon: "list",
  defaultOpen: true,
  fixed: true,
  content: split("vertical", [
    dataTable({ lookup: lookup("strategies") }),
    dataTable({ lookup: lookup("orders") }),
  ]),
};

const market: DockPanelConfig = {
  key: "market",
  label: "Market Pulse",
  icon: "chart",
  defaultOpen: true,
  content: hostPanel("fsi-market-panel"),
};

const kpis: DockPanelConfig = {
  key: "kpis",
  label: "KPIs",
  icon: "meter",
  defaultOpen: true,
  content: metricGrid({ direction: "row" },
    metric({ lookup: lookup("kpis"), subtype: "card" }),
  ),
};

const trust: DockPanelConfig = {
  key: "trust",
  label: "Trust Scores",
  icon: "shield",
  defaultOpen: true,
  content: dataTable({
    lookup: lookup("trust"),
  }),
};

const routing: DockPanelConfig = {
  key: "routing",
  label: "Routing",
  icon: "route",
  defaultOpen: false,
  content: dataTable({
    lookup: lookup("routing"),
  }),
};

const deliberation: DockPanelConfig = {
  key: "deliberation",
  label: "Deliberations",
  icon: "conversation",
  defaultOpen: false,
  content: eventTimeline({
    lookup: lookup("deliberations"),
    strategyKey: "chronological",
  }),
};

const commitments: DockPanelConfig = {
  key: "commitments",
  label: "Commitments",
  icon: "check",
  defaultOpen: false,
  content: eventTimeline({
    lookup: lookup("deliberations"),
    strategyKey: "chronological",
  }),
};

const audit: DockPanelConfig = {
  key: "audit",
  label: "Audit Trail",
  icon: "document",
  defaultOpen: false,
  content: hostPanel("audit-trail-viewer"),
};

const preferences: DockPanelConfig = {
  key: "preferences",
  label: "Preferences",
  icon: "settings",
  defaultOpen: false,
  content: hostPanel("preferences-editor"),
};

const regimeBadge = badge({
  lookup: lookup("regime"),
  colorMap: {
    TRENDING: "green",
    VOLATILE: "red",
    RANGE_BOUND: "yellow",
    MEAN_REVERTING: "blue",
  },
});

const layoutStore = createRestLayoutStore("/api/layout");

const datasets: DataSourceBinding[] = [
  { id: dataSetId("positions"), source: positionsSource },
  { id: dataSetId("kpis"), source: kpisSource, refreshTime: "30s" },
  { id: dataSetId("heatmap"), source: heatmapSource, refreshTime: "30s" },
  { id: dataSetId("trust"), source: trustSource },
  { id: dataSetId("routing"), source: routingSource },
  { id: dataSetId("deliberations"), source: deliberationsSource },
  { id: dataSetId("strategies"), source: strategiesSource },
  { id: dataSetId("orders"), source: ordersSource, refreshTime: "10s" },
  { id: dataSetId("regime"), source: topicSource(["market:regime:*"], { keyField: "instrument" }) },
];

export const tradingDeskPage = page("Trading Desk",
  dockWorkbench({
    storageKey: "trading-desk",
    centre: [positionOverview.content, pnlHeatmap.content],
    left: {
      zones: 2,
      panels: [strategies, market, kpis],
    },
    right: {
      zones: 2,
      panels: [trust, routing, deliberation, commitments],
    },
    bottom: {
      zones: 1,
      panels: [audit, preferences],
    },
    statusBar: regimeBadge,
  }),
  {
    datasets,
    save: { layoutStore, storageKey: "trading-desk" },
  },
);
