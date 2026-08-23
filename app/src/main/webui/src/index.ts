import { loadSite } from "@casehubio/pages-runtime";
import { tradingDesk } from "./trading-desk";
import "./panels/fsi-market-panel";
import "@casehubio/blocks-ui-work-item-inbox";
import "@casehubio/blocks-ui-work-item-detail";
import "@casehubio/blocks-ui-approval-gate";
import "@casehubio/blocks-ui-sla-indicator";
import "@casehubio/blocks-ui-sla-breach-policy";
import "@casehubio/blocks-ui-case-explorer";
import "@casehubio/blocks-ui-notification-inbox";
import "@casehubio/blocks-ui-blocks-timeline";

const container = document.getElementById("app");
if (container) {
  loadSite(container, tradingDesk).catch(console.error);
}
