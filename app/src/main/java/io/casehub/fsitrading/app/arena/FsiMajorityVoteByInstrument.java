package io.casehub.fsitrading.app.arena;

import io.casehub.fsitrading.model.ConsensusResult;
import io.casehub.fsitrading.model.InstrumentConsensus;
import io.casehub.fsitrading.model.OrderSide;
import io.casehub.fsitrading.model.StrategyResponse;
import io.casehub.fsitrading.model.StrategyType;
import io.casehub.fsitrading.model.TradeDecision;
import jakarta.enterprise.context.ApplicationScoped;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@ApplicationScoped
public class FsiMajorityVoteByInstrument {

    public ConsensusResult aggregate(Map<StrategyType, StrategyResponse> evaluations,
                                      Map<StrategyType, Double> routingScores) {
        Map<String, List<VotedDecision>> byInstrument = new HashMap<>();

        for (var entry : evaluations.entrySet()) {
            var strategyType = entry.getKey();
            var response = entry.getValue();
            if (response instanceof StrategyResponse.Trade trade) {
                double score = routingScores.getOrDefault(strategyType, 0.5);
                for (var decision : trade.decisions()) {
                    byInstrument.computeIfAbsent(decision.instrument().symbol(), k -> new ArrayList<>())
                            .add(new VotedDecision(decision, score));
                }
            }
        }

        Map<String, InstrumentConsensus> instruments = new HashMap<>();
        for (var entry : byInstrument.entrySet()) {
            instruments.put(entry.getKey(), voteForInstrument(entry.getValue()));
        }

        return new ConsensusResult(instruments);
    }

    private InstrumentConsensus voteForInstrument(List<VotedDecision> votes) {
        Map<OrderSide, Integer> sideCounts = new HashMap<>();
        Map<OrderSide, List<VotedDecision>> sideVotes = new HashMap<>();

        for (var vote : votes) {
            var side = vote.decision.side();
            sideCounts.merge(side, 1, Integer::sum);
            sideVotes.computeIfAbsent(side, k -> new ArrayList<>()).add(vote);
        }

        if (sideCounts.isEmpty()) {
            return new InstrumentConsensus(InstrumentConsensus.Status.NO_VOTERS, null, null, Map.of());
        }

        OrderSide winner = null;
        int maxVotes = 0;
        boolean tied = false;

        for (var entry : sideCounts.entrySet()) {
            if (entry.getValue() > maxVotes) {
                winner = entry.getKey();
                maxVotes = entry.getValue();
                tied = false;
            } else if (entry.getValue() == maxVotes) {
                tied = true;
            }
        }

        if (tied) {
            return new InstrumentConsensus(InstrumentConsensus.Status.DEADLOCKED, null, null, sideCounts);
        }

        BigDecimal quantity = computeWeightedQuantity(sideVotes.get(winner));
        return new InstrumentConsensus(InstrumentConsensus.Status.CONSENSUS, winner, quantity, sideCounts);
    }

    private BigDecimal computeWeightedQuantity(List<VotedDecision> winningVotes) {
        double weightedSum = 0;
        double scoreSum = 0;
        for (var vote : winningVotes) {
            weightedSum += vote.decision.quantity().doubleValue() * vote.score;
            scoreSum += vote.score;
        }
        if (scoreSum == 0) {
            return winningVotes.getFirst().decision().quantity();
        }
        return BigDecimal.valueOf(weightedSum / scoreSum).setScale(0, RoundingMode.HALF_UP);
    }

    private record VotedDecision(TradeDecision decision, double score) {}
}
