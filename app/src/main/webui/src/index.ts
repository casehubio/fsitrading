import { registerPanel, loadSite } from "@casehubio/pages-runtime";
import { fsiSite } from "./site";

import "./panels/fsi-market-panel";
import "@casehubio/blocks-ui-work-item-inbox";
import "@casehubio/blocks-ui-work-item-detail";
import "@casehubio/blocks-ui-approval-gate";
import "@casehubio/blocks-ui-sla-indicator";
import "@casehubio/blocks-ui-sla-breach-policy";
import "@casehubio/blocks-ui-case-explorer";
import "@casehubio/blocks-ui-notification-inbox";
import "@casehubio/blocks-ui-blocks-timeline";
import "@casehubio/blocks-ui-similarity-panel";
import "@casehubio/blocks-ui-compliance-summary";
import "@casehubio/blocks-ui-gdpr-erasure-action";
import "@casehubio/blocks-ui-audit-trail-viewer";
import "@casehubio/blocks-ui-narrative-timeline";
import "@casehubio/blocks-ui-orchestration-workbench";
import "@casehubio/blocks-ui-conversation-viewer";
import "@casehubio/blocks-ui-routing-rationale";
import "@casehubio/blocks-ui-trust-workbench";
import "@casehubio/blocks-ui-htn-diagram";

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
