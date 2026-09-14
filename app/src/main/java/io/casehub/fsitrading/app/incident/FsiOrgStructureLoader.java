package io.casehub.fsitrading.app.incident;

import io.casehub.eidos.org.api.OrgRegistry;
import io.casehub.eidos.org.api.OrgStructure;

public final class FsiOrgStructureLoader {

    static final String TENANT = "fsitrading";

    private FsiOrgStructureLoader() {}

    public static void load(OrgRegistry registry) {
        OrgStructure.define(TENANT)
            .unit("trading-desk").name("Trading Desk").kind("tiered-escalation").add()

            .unit("emergency-response").name("Emergency Response Team").kind("team")
                .parentUnit("trading-desk")
                .member("emergencyHaltAgent", "lead")
                .member("closePositionsAgent", "responder")
                .member("haltAndWaitAgent", "responder")
                .member("liquidationAgent", "responder")
                .member("exposureCloserAgent", "responder")
                .capability("emergency-halt")
                .capability("position-close")
                .capability("forced-liquidation")
                .add()

            .unit("risk-management").name("Risk Management Team").kind("team")
                .parentUnit("trading-desk")
                .member("positionReducerAgent", "lead")
                .member("hedgeAgent", "specialist")
                .member("adjustLimitsAgent", "specialist")
                .capability("exposure-reduction")
                .capability("hedging")
                .capability("limit-adjustment")
                .add()

            .unit("analysis").name("Analysis Team").kind("team")
                .parentUnit("trading-desk")
                .member("sentimentAnalyserAgent", "lead")
                .member("reEvaluatorAgent", "analyst")
                .member("monitorAgent", "analyst")
                .capability("sentiment-analysis")
                .capability("strategy-evaluation")
                .capability("market-monitoring")
                .add()

            .unit("operations").name("Operations Team").kind("team")
                .parentUnit("trading-desk")
                .member("verifyAgent", "lead")
                .member("alertOncallAgent", "coordinator")
                .capability("outcome-verification")
                .capability("human-escalation")
                .add()

            // Cross-team escalation: analysis lead → risk lead → emergency lead
            .escalatesTo("sentimentAnalyserAgent", "positionReducerAgent")
                .scope("incident-respond").add()
            .escalatesTo("positionReducerAgent", "emergencyHaltAgent")
                .scope("incident-respond").add()

            // Operations supervises all team leads
            .supervises("verifyAgent", "sentimentAnalyserAgent").add()
            .supervises("verifyAgent", "positionReducerAgent").add()
            .supervises("verifyAgent", "emergencyHaltAgent").add()

            // Within-team reporting
            .reportsTo("closePositionsAgent", "emergencyHaltAgent").add()
            .reportsTo("haltAndWaitAgent", "emergencyHaltAgent").add()
            .reportsTo("liquidationAgent", "emergencyHaltAgent").add()
            .reportsTo("exposureCloserAgent", "emergencyHaltAgent").add()
            .reportsTo("hedgeAgent", "positionReducerAgent").add()
            .reportsTo("adjustLimitsAgent", "positionReducerAgent").add()
            .reportsTo("reEvaluatorAgent", "sentimentAnalyserAgent").add()
            .reportsTo("monitorAgent", "sentimentAnalyserAgent").add()
            .reportsTo("alertOncallAgent", "verifyAgent").add()

            .build()
            .registerAll(registry);
    }
}
