import { page, tabs } from "@casehubio/pages-ui";
import { tradingDeskPage } from "./trading-desk";
import { opsCentrePage } from "./ops-centre";

export const fsiSite = page("FSI Trading",
  tabs(
    ["Trading Desk", tradingDeskPage],
    ["Ops Centre", opsCentrePage],
  ),
);
