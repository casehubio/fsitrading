package io.casehub.fsitrading.app.engine;

import io.casehub.api.model.Binding;
import io.casehub.api.model.CaseDefinition;
import io.casehub.api.model.ContextChangeTrigger;
import io.casehub.api.model.Goal;
import io.casehub.api.model.GoalExpression;
import io.casehub.api.model.GoalKind;
import io.casehub.api.model.HumanTaskTarget;
import io.casehub.api.model.OutcomeAction;
import io.casehub.api.model.Milestone;
import io.casehub.api.model.OutcomePolicy;
import io.casehub.api.model.Trigger;
import io.casehub.worker.api.Capability;

import java.util.Map;
import java.util.Set;

import static io.casehub.fsitrading.app.engine.StrategyEvaluationCaseDescriptor.*;

public final class StrategyEvaluationCaseDefinition {

    private StrategyEvaluationCaseDefinition() {}

    public static CaseDefinition build() {
        var strategyEval = cap("strategy-evaluation",
                "{ strategyId: .strategyId, instrument: .instrument, marketEvent: .marketEvent }",
                "{ evaluation: . }");

        var riskAssessment = cap("risk-assessment",
                "{ decision: .evaluation.decision, currentPositions: .currentPositions }",
                "{ riskAssessment: . }");

        var orderExecution = cap("order-execution",
                "{ decision: .evaluation.decision, marketEvent: .marketEvent }",
                "{ execution: . }");

        var tradeExecuted = Goal.builder()
                .name("trade-executed")
                .kind(GoalKind.SUCCESS)
                .condition(".execution.status == \"FILLED\"")
                .build();

        var noTradeNeeded = Goal.builder()
                .name("no-trade-needed")
                .kind(GoalKind.SUCCESS)
                .condition(".evaluation.action == \"HOLD\"")
                .build();

        var tradeRejected = Goal.builder()
                .name("trade-rejected")
                .kind(GoalKind.FAILURE)
                .condition(".riskAssessment.level == \"HIGH\" and .humanApproval.outcome == \"REJECTED\"")
                .build();

        var executionFailed = Goal.builder()
                .name("execution-failed")
                .kind(GoalKind.FAILURE)
                .condition(".execution.status == \"REJECTED\" or .execution.status == \"CANCELLED\"")
                .build();

        Trigger contextChange = new ContextChangeTrigger("true");

        var def = CaseDefinition.builder()
                .namespace(NAMESPACE)
                .name(NAME)
                .version(VERSION)
                .title("Strategy Evaluation — automated trade decision cycle")
                .capabilities(strategyEval, riskAssessment, orderExecution)
                .goals(tradeExecuted, noTradeNeeded, tradeRejected, executionFailed)
                .completion(
                        GoalExpression.anyOf(tradeExecuted, noTradeNeeded),
                        GoalExpression.anyOf(tradeRejected, executionFailed))
                .milestones(
                        Milestone.builder()
                                .name("strategy-evaluated")
                                .completionCriteria(".evaluation != null")
                                .build(),
                        Milestone.builder()
                                .name("risk-assessed")
                                .completionCriteria(".riskAssessment != null")
                                .build())
                .semanticData(Map.of(
                        "riskThresholds", Map.of(
                                "highRiskQuantity", HIGH_RISK_QUANTITY,
                                "highRiskNotional", HIGH_RISK_NOTIONAL)))
                .build();

        def.getBindings().add(Binding.builder()
                .name("evaluate-strategy")
                .on(contextChange)
                .when(".marketEvent != null and .evaluation == null")
                .capability(strategyEval)
                .outcomePolicy(new OutcomePolicy(
                        OutcomeAction.REROUTE, OutcomeAction.REROUTE,
                        OutcomeAction.REROUTE, 2))
                .build());

        def.getBindings().add(Binding.builder()
                .name("assess-risk")
                .on(contextChange)
                .when(".evaluation.action == \"TRADE\" and .riskAssessment == null")
                .capability(riskAssessment)
                .build());

        def.getBindings().add(Binding.builder()
                .name("human-approval-gate")
                .on(contextChange)
                .when(".riskAssessment.level == \"HIGH\" and .humanApproval == null")
                .humanTask(HumanTaskTarget.inline()
                        .title("High-risk trade approval required")
                        .expiresIn(HUMAN_APPROVAL_SLA)
                        .candidateGroups(Set.of("senior-traders"))
                        .inputMapping("{ decision: .evaluation.decision, risk: .riskAssessment, instrument: .instrument }")
                        .outputMapping("{ humanApproval: . }")
                        .outcomes(Set.of("APPROVED", "REJECTED"))
                        .build())
                .build());

        def.getBindings().add(Binding.builder()
                .name("execute-trade")
                .on(contextChange)
                .when(".evaluation.action == \"TRADE\" and .execution == null and (.riskAssessment.level != \"HIGH\" or .humanApproval.outcome == \"APPROVED\")")
                .capability(orderExecution)
                .outcomePolicy(new OutcomePolicy(
                        OutcomeAction.FAULT, OutcomeAction.FAULT,
                        OutcomeAction.FAULT, 1))
                .build());

        return def;
    }

    private static Capability cap(String name, String input, String output) {
        return Capability.of(name, input, output);
    }
}
