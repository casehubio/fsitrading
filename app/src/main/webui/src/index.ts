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
registerPanel("case-explorer", "blocks-case-explorer");
registerPanel("work-item-inbox", "blocks-work-item-inbox");
registerPanel("work-item-detail", "blocks-work-item-detail");
registerPanel("approval-gate", "blocks-approval-gate");
registerPanel("sla-indicator", "blocks-sla-indicator");
registerPanel("sla-breach-policy", "blocks-sla-breach-policy");
registerPanel("notification-inbox", "blocks-notification-inbox");
registerPanel("blocks-timeline", "blocks-blocks-timeline");
registerPanel("audit-trail-viewer", "blocks-audit-trail-viewer");
registerPanel("similarity-panel", "blocks-similarity-panel");
registerPanel("compliance-summary", "blocks-compliance-summary");
registerPanel("gdpr-erasure-action", "blocks-gdpr-erasure-action");
registerPanel("narrative-timeline", "blocks-narrative-timeline");
registerPanel("orchestration-workbench", "blocks-orchestration-workbench");
registerPanel("conversation-viewer", "blocks-conversation-viewer");
registerPanel("routing-rationale", "blocks-routing-rationale");
registerPanel("trust-workbench", "blocks-trust-workbench");
registerPanel("htn-diagram", "blocks-htn-diagram");

const container = document.getElementById("app");
if (container) {
  loadSite(container, fsiSite).catch(console.error);
}
