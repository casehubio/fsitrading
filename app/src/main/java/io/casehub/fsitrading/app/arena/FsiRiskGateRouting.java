package io.casehub.fsitrading.app.arena;

import io.casehub.blocks.agentic.AgentRef;
import io.casehub.blocks.agentic.routing.RoutingContext;
import io.casehub.blocks.agentic.routing.RoutingDecision;
import io.casehub.blocks.agentic.routing.RoutingStrategy;
import io.casehub.fsitrading.model.ApprovalOutcome;
import io.casehub.fsitrading.model.InstrumentConsensus;
import io.casehub.fsitrading.model.RiskAssessment;
import io.casehub.work.api.Outcome;
import io.casehub.work.api.WorkItemCreateRequest;
import io.casehub.work.api.WorkItemPriority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@ApplicationScoped
public class FsiRiskGateRouting implements RoutingStrategy<ArenaContext> {

    private final int approvalTimeoutHours;

    @Inject
    public FsiRiskGateRouting(
            @ConfigProperty(name = "casehub.fsitrading.arena.approval.timeout-hours",
                    defaultValue = "4")
            int approvalTimeoutHours) {
        this.approvalTimeoutHours = approvalTimeoutHours;
    }

    @Override
    public RoutingDecision route(RoutingContext<ArenaContext> context) {
        return doRoute(context);
    }

    private RoutingDecision doRoute(RoutingContext<ArenaContext> context) {
        var arenaCtx = context.state();
        var risk = arenaCtx.riskAssessment();

        if (risk == null || !needsHumanApproval(risk, arenaCtx)) {
            arenaCtx.setApprovalOutcome(ApprovalOutcome.NOT_REQUIRED);
            if (context.candidates().isEmpty()) {
                return new RoutingDecision.Unresolvable("no pass-through agent available");
            }
            return new RoutingDecision.Selected(List.of(context.candidates().get(0).ref()));
        }

        var humanAgent = AgentRef.human(buildApprovalTemplate(arenaCtx, risk));
        return new RoutingDecision.Selected(List.of(humanAgent));
    }

    private boolean needsHumanApproval(RiskAssessment risk, ArenaContext ctx) {
        return risk.requiresApproval()
                || (ctx.consensus() != null && ctx.consensus().requiresHumanReview());
    }

    private WorkItemCreateRequest buildApprovalTemplate(ArenaContext ctx, RiskAssessment risk) {
        var consensus = ctx.consensus();
        String instruments = consensus.instruments().keySet().stream()
                .sorted()
                .collect(Collectors.joining(", "));

        StringBuilder description = new StringBuilder();
        description.append("Risk level: ").append(risk.level()).append("\n\n");
        for (var entry : consensus.instruments().entrySet()) {
            String inst = entry.getKey();
            InstrumentConsensus ic = entry.getValue();
            description.append("**").append(inst).append("**: ");
            if (ic.isDeadlocked()) {
                description.append("DEADLOCKED");
            } else if (ic.hasNoVoters()) {
                description.append("NO VOTERS");
            } else {
                description.append(ic.winningSide())
                        .append(" ").append(formatQuantity(ic.quantity()));
            }
            RiskAssessment.Level instRisk = risk.perInstrument().get(inst);
            if (instRisk != null) {
                description.append(" (risk: ").append(instRisk).append(")");
            }
            description.append("\n");
        }

        return WorkItemCreateRequest.builder()
                .title("Arena: High-Risk Consensus Approval — " + instruments)
                .description(description.toString())
                .types(List.of("trade-approval"))
                .priority(WorkItemPriority.HIGH)
                .expiresAtBusinessHours(approvalTimeoutHours)
                .permittedOutcomes(List.of(
                        new Outcome("APPROVE", "Approve", null),
                        new Outcome("REJECT", "Reject", null)))
                .scope("fsitrading")
                .build();
    }

    private String formatQuantity(BigDecimal qty) {
        return qty != null ? qty.toPlainString() : "0";
    }
}
