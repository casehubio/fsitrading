package io.casehub.fsitrading.app.incident;

import io.casehub.fsitrading.model.IncidentSeverity;
import io.casehub.fsitrading.model.IncidentSeverityDescriptor;
import io.casehub.fsitrading.model.MarketEventType;
import io.casehub.fsitrading.model.MarketEventTypeDescriptor;

import io.casehub.api.model.CaseDefinition;
import io.casehub.fsitrading.app.incident.agent.AdjustLimitsAgent;
import io.casehub.fsitrading.app.incident.agent.AlertOncallAgent;
import io.casehub.fsitrading.app.incident.agent.ClosePositionsAgent;
import io.casehub.fsitrading.app.incident.agent.EmergencyHaltAgent;
import io.casehub.fsitrading.app.incident.agent.ExposureCloserAgent;
import io.casehub.fsitrading.app.incident.agent.HaltAndWaitAgent;
import io.casehub.fsitrading.app.incident.agent.HedgeAgent;
import io.casehub.fsitrading.app.incident.agent.IncidentResponseAgent;
import io.casehub.fsitrading.app.incident.agent.LiquidationAgent;
import io.casehub.fsitrading.app.incident.agent.MonitorAgent;
import io.casehub.fsitrading.app.incident.agent.PositionReducerAgent;
import io.casehub.fsitrading.app.incident.agent.ReEvaluatorAgent;
import io.casehub.fsitrading.app.incident.agent.SentimentAnalyserAgent;
import io.casehub.fsitrading.app.incident.agent.VerifyAgent;
import io.casehub.worker.api.Worker;
import io.casehub.worker.api.WorkerFunction;
import io.casehub.worker.api.WorkerResult;

import java.util.List;
import java.util.Map;

public class OvernightIncidentCaseDescriptor {

    private static final Map<String, IncidentResponseAgent> AGENTS = Map.ofEntries(
            Map.entry("emergencyHaltAgent", new EmergencyHaltAgent()),
            Map.entry("closePositionsAgent", new ClosePositionsAgent()),
            Map.entry("haltAndWaitAgent", new HaltAndWaitAgent()),
            Map.entry("alertOncallAgent", new AlertOncallAgent()),
            Map.entry("adjustLimitsAgent", new AdjustLimitsAgent()),
            Map.entry("monitorAgent", new MonitorAgent()),
            Map.entry("verifyAgent", new VerifyAgent()),
            Map.entry("positionReducerAgent", new PositionReducerAgent()),
            Map.entry("hedgeAgent", new HedgeAgent()),
            Map.entry("reEvaluatorAgent", new ReEvaluatorAgent()),
            Map.entry("exposureCloserAgent", new ExposureCloserAgent()),
            Map.entry("sentimentAnalyserAgent", new SentimentAnalyserAgent()),
            Map.entry("liquidationAgent", new LiquidationAgent()));

    public List<String> decompositionStepsFor(IncidentSeverity severity) {
        return IncidentSeverityDescriptor.forSeverity(severity).decompositionSteps();
    }

    public String agentNameFor(MarketEventType eventType) {
        return MarketEventTypeDescriptor.forType(eventType).agentName();
    }

    public void augmentWorkers(CaseDefinition definition) {
        var workers = definition.getWorkers();
        workers.removeIf(w -> w.function() == WorkerFunction.NONE);
        AGENTS.forEach((name, agent) ->
                               workers.add(Worker.builder()
                                                 .name(name)
                                                 .capabilityName("incident-respond")
                                                 .function(input -> WorkerResult.of(agent.execute(input)))
                                                 .build()));
    }
}
