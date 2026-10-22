import { registerPanel, loadSite } from "@casehubio/pages-runtime";
import { fsiSite } from "./site";

import "./panels/fsi-market-panel";
import "@casehubio/agency-ui-work-item-inbox";
import "@casehubio/agency-ui-work-item-detail";
import "@casehubio/agency-ui-approval-gate";
import "@casehubio/agency-ui-sla-indicator";
import "@casehubio/agency-ui-sla-breach-policy";
import "@casehubio/agency-ui-case-explorer";
import "@casehubio/agency-ui-notification-inbox";
import "@casehubio/agency-ui-blocks-timeline";
import "@casehubio/agency-ui-similarity-panel";
import "@casehubio/agency-ui-compliance-summary";
import "@casehubio/agency-ui-gdpr-erasure-action";
import "@casehubio/agency-ui-audit-trail-viewer";
import "@casehubio/agency-ui-narrative-timeline";
import "@casehubio/agency-ui-orchestration-workbench";
import "@casehubio/agency-ui-conversation-viewer";
import "@casehubio/agency-ui-routing-rationale";
import "@casehubio/agency-ui-trust-workbench";
import "@casehubio/agency-ui-htn-diagram";

registerPanel("fsi-market-panel", "fsi-market-panel");
registerPanel("case-explorer", "agency-case-explorer");
registerPanel("work-item-inbox", "agency-work-item-inbox");
registerPanel("work-item-detail", "agency-work-item-detail");
registerPanel("approval-gate", "agency-approval-gate");
registerPanel("sla-indicator", "agency-sla-indicator");
registerPanel("sla-breach-policy", "agency-sla-breach-policy");
registerPanel("notification-inbox", "agency-notification-inbox");
registerPanel("agency-timeline", "agency-blocks-timeline");
registerPanel("audit-trail-viewer", "agency-audit-trail-viewer");
registerPanel("similarity-panel", "agency-similarity-panel");
registerPanel("compliance-summary", "agency-compliance-summary");
registerPanel("gdpr-erasure-action", "agency-gdpr-erasure-action");
registerPanel("narrative-timeline", "agency-narrative-timeline");
registerPanel("orchestration-workbench", "agency-orchestration-workbench");
registerPanel("conversation-viewer", "agency-conversation-viewer");
registerPanel("routing-rationale", "agency-routing-rationale");
registerPanel("trust-workbench", "blocks-trust-workbench");
registerPanel("htn-diagram", "agency-htn-diagram");

const container = document.getElementById("app");
if (container) {
  loadSite(container, fsiSite).catch(console.error);
}
