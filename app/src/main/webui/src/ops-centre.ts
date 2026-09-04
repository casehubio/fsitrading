import {
  page, dockWorkbench, hostPanel, split, dataTable,
  metricGrid, metric, lookup,
} from "@casehubio/pages-ui";
import { eventTimeline, badge } from "@casehubio/pages-ui/dist/dsl/builders.js";
import { fetchSource, dataSetId } from "@casehubio/pages-data";
import { createRestLayoutStore } from "@casehubio/pages-runtime";
import type { DataSourceBinding } from "@casehubio/pages-data";
import type { DockPanelConfig } from "@casehubio/pages-ui";

const incidentsSource = fetchSource("/api/incidents");
const incidentSeveritySource = fetchSource("/api/incidents/summary/severity");
const incidentStatusSource = fetchSource("/api/incidents/summary/status");
const strategiesSource = fetchSource("/api/strategies");

const incidentDashboard: DockPanelConfig = {
  key: "incident-dashboard",
  label: "Incidents",
  icon: "alert",
  defaultOpen: true,
  zone: "top",
  content: split("vertical", [
    metricGrid({ direction: "row" },
      metric({ lookup: lookup("incident-severity"), subtype: "card" }),
    ),
    eventTimeline({
      lookup: lookup("incidents"),
      strategyKey: "chronological",
    }),
  ]),
};

const cases: DockPanelConfig = {
  key: "cases",
  label: "Cases",
  icon: "folder",
  defaultOpen: true,
  fixed: true,
  content: hostPanel("case-explorer"),
};

const strategies: DockPanelConfig = {
  key: "strategies",
  label: "Strategies",
  icon: "list",
  defaultOpen: true,
  content: dataTable({
    lookup: lookup("strategies"),
  }),
};

const approvals: DockPanelConfig = {
  key: "approvals",
  label: "Approvals",
  icon: "inbox",
  defaultOpen: true,
  content: hostPanel("work-item-inbox"),
};

const workItemDetail: DockPanelConfig = {
  key: "work-item-detail",
  label: "Work Item Detail",
  icon: "document",
  defaultOpen: true,
  content: hostPanel("work-item-detail"),
};

const slaCountdown: DockPanelConfig = {
  key: "sla",
  label: "SLA Status",
  icon: "clock",
  defaultOpen: true,
  content: hostPanel("sla-indicator"),
};

const responseChannel: DockPanelConfig = {
  key: "response-channel",
  label: "Response Channel",
  icon: "conversation",
  defaultOpen: false,
  content: hostPanel("channel-activity"),
};

const incidentTimeline: DockPanelConfig = {
  key: "incident-timeline",
  label: "Timeline",
  icon: "timeline",
  defaultOpen: true,
  content: hostPanel("blocks-timeline"),
};

const slaPolicy: DockPanelConfig = {
  key: "sla-policy",
  label: "SLA Policy",
  icon: "settings",
  defaultOpen: false,
  content: hostPanel("sla-breach-policy"),
};

const approvalGate: DockPanelConfig = {
  key: "approval-gate",
  label: "Approval Gate",
  icon: "shield",
  defaultOpen: false,
  content: hostPanel("approval-gate"),
};

const notifications: DockPanelConfig = {
  key: "notifications",
  label: "Notifications",
  icon: "bell",
  defaultOpen: false,
  content: hostPanel("notification-inbox"),
};

const similarIncidents: DockPanelConfig = {
  key: "similar-incidents",
  label: "Similar Incidents",
  icon: "search",
  defaultOpen: false,
  content: hostPanel("similarity-panel", { endpoint: "/api/incidents/similar" }),
};

const compliance: DockPanelConfig = {
  key: "compliance",
  label: "Compliance",
  icon: "shield",
  defaultOpen: true,
  content: hostPanel("compliance-summary", { endpoint: "/api/compliance/status" }),
};

const gdprErasure: DockPanelConfig = {
  key: "gdpr-erasure",
  label: "GDPR Erasure",
  icon: "lock",
  defaultOpen: false,
  content: hostPanel("gdpr-erasure-action", { endpoint: "/api/gdpr/erase" }),
};

const audit: DockPanelConfig = {
  key: "audit",
  label: "Audit Trail",
  icon: "document",
  defaultOpen: false,
  content: hostPanel("audit-trail-viewer"),
};

const incidentCountBadge = badge({
  lookup: lookup("incident-status"),
  field: "totalActive",
  colorMap: { "0": "green", "default": "red" },
});

const slaStatusBadge = badge({
  lookup: lookup("incident-status"),
  field: "slaStatus",
  colorMap: { OK: "green", WARNING: "yellow", BREACHED: "red" },
});

const layoutStore = createRestLayoutStore("/api/layout");

const datasets: DataSourceBinding[] = [
  { id: dataSetId("incidents"), source: incidentsSource, refreshTime: "10s" },
  { id: dataSetId("incident-severity"), source: incidentSeveritySource, refreshTime: "10s" },
  { id: dataSetId("incident-status"), source: incidentStatusSource, refreshTime: "10s" },
  { id: dataSetId("strategies"), source: strategiesSource },
];

export const opsCentrePage = page("Ops Centre",
  dockWorkbench({
    storageKey: "ops-centre",
    centre: [incidentDashboard.content],
    left: {
      zones: 2,
      panels: [cases, strategies],
    },
    right: {
      zones: 2,
      panels: [approvals, workItemDetail, slaCountdown, responseChannel, similarIncidents],
    },
    bottom: {
      zones: 2,
      panels: [incidentTimeline, slaPolicy, approvalGate, notifications, compliance, audit, gdprErasure],
    },
    statusBar: split("horizontal", [incidentCountBadge, slaStatusBadge]),
  }),
  {
    datasets,
    save: { layoutStore, storageKey: "ops-centre" },
  },
);
