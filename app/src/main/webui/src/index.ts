import { loadSite } from "@casehubio/pages-runtime";
import { tradingDesk } from "./trading-desk";
import "./panels/fsi-market-panel";

const container = document.getElementById("app");
if (container) {
  loadSite(container, tradingDesk).catch(console.error);
}
