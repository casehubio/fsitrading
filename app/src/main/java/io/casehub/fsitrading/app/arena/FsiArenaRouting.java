package io.casehub.fsitrading.app.arena;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.casehub.api.spi.routing.AgentCandidate;
import io.casehub.api.spi.routing.AgentHealth;
import io.casehub.api.spi.routing.AgentRoutingContext;
import io.casehub.api.spi.routing.RoutingResult;
import io.casehub.api.spi.routing.RoutingSignal;
import io.casehub.api.spi.routing.RoutingSignalAssembler;
import io.casehub.blocks.agentic.AgentRef;
import io.casehub.blocks.agentic.RoutingCandidate;
import io.casehub.blocks.agentic.routing.RoutingContext;
import io.casehub.blocks.agentic.routing.RoutingDecision;
import io.casehub.blocks.agentic.routing.RoutingStrategy;
import io.casehub.blocks.routing.RoutingDecisionRecord;
import io.casehub.blocks.routing.agent.CbrAgentRoutingStrategy;
import io.casehub.blocks.routing.agent.LlmAgentRoutingStrategy;
import io.casehub.eidos.api.MatchDegree;
import io.casehub.fsitrading.FsiActorIdentity;
import io.casehub.fsitrading.app.service.StrategyService;
import io.casehub.fsitrading.model.StrategyType;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@ApplicationScoped
public class FsiArenaRouting implements RoutingStrategy<ArenaContext> {

    private static final String CAPABILITY_TAG = "strategy-evaluation";
    private static final String TENANCY_ID = "fsitrading";
    private static final String LLM_SIGNAL = "llm";
    private static final String CBR_SIGNAL = "cbr";

    private static final Map<String, StrategyType> NAME_TO_TYPE;

    static {
        NAME_TO_TYPE = Arrays.stream(StrategyType.values())
                .collect(Collectors.toMap(FsiActorIdentity::capabilityTag, t -> t));
    }

    private final RoutingSignalAssembler signalAssembler;
    private final @Nullable LlmAgentRoutingStrategy llmStrategy;
    private final @Nullable CbrAgentRoutingStrategy cbrStrategy;
    private final StrategyService strategyService;
    private final double minimumThreshold;

    @Inject
    public FsiArenaRouting(RoutingSignalAssembler signalAssembler,
                           Instance<LlmAgentRoutingStrategy> llmStrategy,
                           Instance<CbrAgentRoutingStrategy> cbrStrategy,
                           StrategyService strategyService,
                           @ConfigProperty(name = "casehub.fsitrading.arena.routing.threshold",
                                   defaultValue = "0.3")
                           double minimumThreshold) {
        this.signalAssembler = signalAssembler;
        this.llmStrategy = llmStrategy.isUnsatisfied() ? null : llmStrategy.get();
        this.cbrStrategy = cbrStrategy.isUnsatisfied() ? null : cbrStrategy.get();
        this.strategyService = strategyService;
        this.minimumThreshold = minimumThreshold;
    }

    FsiArenaRouting(RoutingSignalAssembler signalAssembler,
                    @Nullable LlmAgentRoutingStrategy llmStrategy,
                    @Nullable CbrAgentRoutingStrategy cbrStrategy,
                    StrategyService strategyService,
                    double minimumThreshold) {
        this.signalAssembler = signalAssembler;
        this.llmStrategy = llmStrategy;
        this.cbrStrategy = cbrStrategy;
        this.strategyService = strategyService;
        this.minimumThreshold = minimumThreshold;
    }

    @Override
    public Uni<RoutingDecision> route(RoutingContext<ArenaContext> context) {
        return Uni.createFrom().item(() -> doRoute(context));
    }

    private RoutingDecision doRoute(RoutingContext<ArenaContext> context) {
        var arenaCtx = context.state();
        var allCandidates = context.candidates();

        List<RoutingCandidate> eligible = filterActive(allCandidates);
        if (eligible.isEmpty()) {
            return new RoutingDecision.Unresolvable("no active strategy agents available");
        }

        List<AgentCandidate> agentCandidates = translateCandidates(eligible);
        AgentRoutingContext routingCtx = translateContext(arenaCtx);

        Map<String, Map<String, Double>> allSignals = new LinkedHashMap<>();

        collectAssemblerSignals(routingCtx, agentCandidates, allSignals);
        collectLlmSignal(routingCtx, agentCandidates, eligible, allSignals);
        collectCbrSignal(routingCtx, agentCandidates, eligible, allSignals);

        Map<String, Double> blendedScores = blend(eligible, allSignals);

        List<AgentRef> selected = new ArrayList<>();
        List<RoutingDecisionRecord> records = new ArrayList<>();

        for (var candidate : eligible) {
            String name = candidate.ref().name();
            double score = blendedScores.getOrDefault(name, 0.0);
            records.add(new RoutingDecisionRecord(
                    CAPABILITY_TAG, name, score, minimumThreshold, null));
            if (score >= minimumThreshold) {
                selected.add(candidate.ref());
            }
        }

        arenaCtx.setRoutingDecisions(records);
        arenaCtx.setSelectedAgents(eligible.stream()
                .filter(c -> selected.stream().anyMatch(s -> s.name().equals(c.ref().name())))
                .toList());

        if (selected.isEmpty()) {
            return new RoutingDecision.Unresolvable(
                    "all candidates scored below threshold " + minimumThreshold);
        }

        return new RoutingDecision.Selected(selected);
    }

    private List<RoutingCandidate> filterActive(List<RoutingCandidate> candidates) {
        return candidates.stream()
                .filter(c -> {
                    StrategyType type = NAME_TO_TYPE.get(c.ref().name());
                    return type != null && strategyService.isActive(type);
                })
                .toList();
    }

    private List<AgentCandidate> translateCandidates(List<RoutingCandidate> eligible) {
        return eligible.stream()
                .map(c -> new AgentCandidate(
                        c.ref().name(),
                        c.descriptor() != null && c.descriptor().capabilities() != null
                                ? c.descriptor().capabilities().stream()
                                .map(cap -> cap.name())
                                .collect(Collectors.toSet())
                                : Set.of(),
                        0,
                        AgentHealth.READY,
                        c.descriptor(),
                        new MatchDegree.Exact(),
                        null))
                .toList();
    }

    private AgentRoutingContext translateContext(ArenaContext arenaCtx) {
        JsonNode caseContext = buildCaseContext(arenaCtx);
        UUID caseId = UUID.nameUUIDFromBytes(arenaCtx.runId().toString().getBytes());
        return new AgentRoutingContext(
                caseId, CAPABILITY_TAG, caseContext, TENANCY_ID,
                List.of(), null, null);
    }

    private JsonNode buildCaseContext(ArenaContext arenaCtx) {
        ObjectMapper mapper = new ObjectMapper();
        ObjectNode node = mapper.createObjectNode();
        var signal = arenaCtx.marketSignal();
        node.put("instrument", signal.instrument());
        node.put("eventType", signal.eventType());
        if (signal.price() != null) {
            node.put("price", signal.price().toString());
        }
        if (signal.volume() != null) {
            node.put("volume", signal.volume().toString());
        }
        return node;
    }

    private void collectAssemblerSignals(AgentRoutingContext routingCtx,
                                         List<AgentCandidate> agentCandidates,
                                         Map<String, Map<String, Double>> allSignals) {
        Map<String, RoutingSignal> signals = signalAssembler.assemble(routingCtx, agentCandidates);
        for (var entry : signals.entrySet()) {
            String providerId = entry.getKey();
            Map<String, Double> perCandidate = new HashMap<>();
            for (var candidateEntry : entry.getValue().candidates().entrySet()) {
                if (candidateEntry.getValue() instanceof RoutingSignal.CandidateSignal.Score score) {
                    perCandidate.put(candidateEntry.getKey(), score.value());
                }
            }
            if (!perCandidate.isEmpty()) {
                allSignals.put(providerId, perCandidate);
            }
        }
    }

    private void collectLlmSignal(AgentRoutingContext routingCtx,
                                  List<AgentCandidate> agentCandidates,
                                  List<RoutingCandidate> eligible,
                                  Map<String, Map<String, Double>> allSignals) {
        if (llmStrategy == null) return;
        try {
            RoutingResult result = llmStrategy.select(routingCtx, agentCandidates);
            addBinarySignal(LLM_SIGNAL, result, eligible, allSignals);
        } catch (Exception ignored) {
            // LLM unavailable — omit signal
        }
    }

    private void collectCbrSignal(AgentRoutingContext routingCtx,
                                  List<AgentCandidate> agentCandidates,
                                  List<RoutingCandidate> eligible,
                                  Map<String, Map<String, Double>> allSignals) {
        if (cbrStrategy == null) return;
        try {
            RoutingResult result = cbrStrategy.select(routingCtx, agentCandidates);
            addBinarySignal(CBR_SIGNAL, result, eligible, allSignals);
        } catch (Exception ignored) {
            // CBR unavailable — omit signal
        }
    }

    private void addBinarySignal(String signalName, RoutingResult result,
                                 List<RoutingCandidate> eligible,
                                 Map<String, Map<String, Double>> allSignals) {
        if (result instanceof RoutingResult.Selected selected) {
            String selectedId = selected.single().executorId();
            Map<String, Double> perCandidate = new HashMap<>();
            for (var candidate : eligible) {
                perCandidate.put(candidate.ref().name(),
                        candidate.ref().name().equals(selectedId) ? 1.0 : 0.0);
            }
            allSignals.put(signalName, perCandidate);
        }
    }

    private Map<String, Double> blend(List<RoutingCandidate> eligible,
                                      Map<String, Map<String, Double>> allSignals) {
        Map<String, Double> blended = new HashMap<>();
        for (var candidate : eligible) {
            String name = candidate.ref().name();
            double scoreSum = 0;
            int signalCount = 0;
            for (var signal : allSignals.values()) {
                Double candidateScore = signal.get(name);
                if (candidateScore != null) {
                    scoreSum += candidateScore;
                    signalCount++;
                }
            }
            blended.put(name, signalCount > 0 ? scoreSum / signalCount : 0.0);
        }
        return blended;
    }
}
