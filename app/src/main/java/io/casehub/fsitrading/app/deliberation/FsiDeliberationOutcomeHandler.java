package io.casehub.fsitrading.app.deliberation;

import io.casehub.blocks.conversation.CommonGroundState;
import io.casehub.blocks.conversation.ConvergenceSignal;
import io.casehub.blocks.conversation.ConvergenceState;
import io.casehub.blocks.conversation.GroundedFact;
import io.casehub.fsitrading.model.OrderSide;
import io.casehub.fsitrading.model.OrderType;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@ApplicationScoped
public class FsiDeliberationOutcomeHandler {

    private static final Pattern TRADE_PATTERN = Pattern.compile(
            "(?i)(BUY|SELL)\\s+(\\d+)\\s+(\\S+)(?:\\s+(?:at\\s+)?(market|limit))?(?:\\s+(\\d+\\.?\\d*))?");

    @ConfigProperty(name = "fsi.deliberation.diminishing-returns-min-established", defaultValue = "0.5")
    double diminishingReturnsMinEstablished;

    @ConfigProperty(name = "fsi.deliberation.converging-consensus-threshold", defaultValue = "0.7")
    double convergingConsensusThreshold;

    public sealed interface OutcomeAction {
        record Execute(OrderSide side, BigDecimal quantity, String instrument,
                       OrderType orderType, BigDecimal limitPrice,
                       double confidence, String convergenceState) implements OutcomeAction {}
        record Escalate(String reason, String convergenceState) implements OutcomeAction {}
    }

    public OutcomeAction resolve(ConvergenceSignal signal, CommonGroundState commonGround) {
        return switch (signal.state()) {
            case CONSENSUS -> resolveConsensus(signal, commonGround);
            case DEADLOCK -> new OutcomeAction.Escalate(
                    "Deadlock: " + signal.reason(), signal.state().name());
            case PROGRESSING -> new OutcomeAction.Escalate(
                    "Debate active at round cap: " + signal.reason(), signal.state().name());
            case DIMINISHING_RETURNS -> resolveDiminishingReturns(signal, commonGround);
            case CONVERGING -> resolveConverging(signal, commonGround);
        };
    }

    private OutcomeAction resolveConsensus(ConvergenceSignal signal, CommonGroundState commonGround) {
        int totalPoints = commonGround.establishedFacts().size()
                + commonGround.pendingClaims().size()
                + commonGround.disputedPoints().size();
        if (totalPoints == 0) {
            return new OutcomeAction.Escalate(
                    "Empty common ground — no points to execute", signal.state().name());
        }

        var proposeFact = findHighestPriorityPropose(commonGround);
        if (proposeFact.isEmpty()) {
            return new OutcomeAction.Escalate(
                    "No PROPOSE facts established — cannot derive trade", signal.state().name());
        }

        var parsed = parseTrade(proposeFact.get().content());
        if (parsed.isEmpty()) {
            return new OutcomeAction.Escalate(
                    "Cannot parse trade from: " + proposeFact.get().content(), signal.state().name());
        }

        var trade = parsed.get();
        return new OutcomeAction.Execute(trade.side, trade.quantity, trade.instrument,
                trade.orderType, trade.limitPrice, signal.confidence(), signal.state().name());
    }

    private OutcomeAction resolveDiminishingReturns(ConvergenceSignal signal, CommonGroundState commonGround) {
        double ratio = establishedRatio(commonGround);
        if (ratio < diminishingReturnsMinEstablished) {
            return new OutcomeAction.Escalate(
                    "Established ratio " + String.format("%.2f", ratio) + " below threshold",
                    signal.state().name());
        }
        var consensusResult = resolveConsensus(signal, commonGround);
        if (consensusResult instanceof OutcomeAction.Execute exec) {
            return new OutcomeAction.Execute(exec.side(), exec.quantity(), exec.instrument(),
                    exec.orderType(), exec.limitPrice(), signal.confidence(), signal.state().name());
        }
        return consensusResult;
    }

    private OutcomeAction resolveConverging(ConvergenceSignal signal, CommonGroundState commonGround) {
        double ratio = establishedRatio(commonGround);
        if (ratio >= convergingConsensusThreshold) {
            return resolveDiminishingReturns(signal, commonGround);
        }
        return new OutcomeAction.Escalate(
                "Converging but established ratio " + String.format("%.2f", ratio) + " below consensus threshold",
                signal.state().name());
    }

    private double establishedRatio(CommonGroundState commonGround) {
        int total = commonGround.establishedFacts().size()
                + commonGround.pendingClaims().size()
                + commonGround.disputedPoints().size();
        if (total == 0) return 0.0;
        return (double) commonGround.establishedFacts().size() / total;
    }

    private Optional<GroundedFact> findHighestPriorityPropose(CommonGroundState commonGround) {
        return commonGround.establishedFacts().values().stream()
                .filter(f -> f.content() != null && TRADE_PATTERN.matcher(f.content()).find())
                .findFirst();
    }

    record ParsedTrade(OrderSide side, BigDecimal quantity, String instrument,
                       OrderType orderType, BigDecimal limitPrice) {}

    Optional<ParsedTrade> parseTrade(String content) {
        if (content == null) return Optional.empty();
        Matcher m = TRADE_PATTERN.matcher(content);
        if (!m.find()) return Optional.empty();

        var side = OrderSide.valueOf(m.group(1).toUpperCase());
        var quantity = new BigDecimal(m.group(2));
        var instrument = m.group(3);
        var orderTypeStr = m.group(4);
        var orderType = orderTypeStr != null && orderTypeStr.equalsIgnoreCase("limit")
                ? OrderType.LIMIT : OrderType.MARKET;
        BigDecimal limitPrice = null;
        if (orderType == OrderType.LIMIT && m.group(5) != null) {
            limitPrice = new BigDecimal(m.group(5));
        }

        return Optional.of(new ParsedTrade(side, quantity, instrument, orderType, limitPrice));
    }
}
