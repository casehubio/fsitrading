package io.casehub.fsitrading.app.deliberation;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.casehub.blocks.conversation.CommonGroundAnalyser;
import io.casehub.blocks.conversation.ConvergenceAnalyser;
import io.casehub.blocks.conversation.ConvergenceSignal;
import io.casehub.blocks.conversation.ConvergenceState;
import io.casehub.blocks.conversation.EpistemicRules;
import io.casehub.fsitrading.FsiActorIdentity;
import io.casehub.fsitrading.app.ledger.TradingLedgerService;
import io.casehub.fsitrading.model.Instrument;
import io.casehub.fsitrading.model.AssetClass;
import io.casehub.fsitrading.model.OrderType;
import io.casehub.fsitrading.model.TradeDecision;
import io.casehub.fsitrading.model.TradeProvenance;
import io.casehub.fsitrading.app.service.OrderService;
import io.casehub.fsitrading.app.service.StrategyService;
import io.casehub.fsitrading.model.StrategyType;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.spi.CDI;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeoutException;

@ApplicationScoped
public class FsiDeliberationOrchestrator {

    private static final Logger log = Logger.getLogger(FsiDeliberationOrchestrator.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Inject
    FsiConversationProjection projection;

    @Inject
    DeliberationRecordRepository recordRepository;

    @Inject
    FsiDeliberationOutcomeHandler outcomeHandler;

    @Inject
    TradingLedgerService ledgerService;

    @Inject
    OrderService orderService;

    @Inject
    StrategyService strategyService;

    @ConfigProperty(name = "fsi.deliberation.max-rounds", defaultValue = "10")
    int maxRounds;

    @ConfigProperty(name = "fsi.deliberation.wall-clock-timeout-seconds", defaultValue = "900")
    int wallClockTimeoutSeconds;

    @Transactional
    public UUID startDeliberation(String instrument, String triggerType,
                                   List<String> agentIds) {
        var channelId = UUID.randomUUID();
        var recordId = UUID.randomUUID();
        var participants = String.join(",", agentIds);
        var channelName = "fsi-deliberation-" + instrument + "-" + System.currentTimeMillis();

        var record = new DeliberationRecord(recordId, channelId, instrument,
                "IN_PROGRESS", triggerType, participants, Instant.now());
        recordRepository.persist(record);

        log.infof("Started deliberation %s for %s (channel=%s, agents=%s)",
                recordId, instrument, channelName, participants);

        return recordId;
    }

    @Transactional
    public void completeDeliberation(UUID recordId, ConvergenceSignal signal,
                                      io.casehub.blocks.conversation.CommonGroundState commonGround,
                                      io.casehub.blocks.conversation.ConversationState conversationState) {
        var record = recordRepository.findById(recordId).orElseThrow(
                () -> new IllegalStateException("DeliberationRecord not found: " + recordId));

        var outcome = outcomeHandler.resolve(signal, commonGround);

        record.setConvergenceState(signal.state().name());
        record.setConfidence(signal.confidence());
        record.setEstablishedCount(commonGround.establishedFacts().size());
        record.setDisputedCount(commonGround.disputedPoints().size());
        record.setPendingCount(commonGround.pendingClaims().size());
        record.setSummary(signal.reason());
        record.setConversationStateSnapshot(serializeJson(conversationState));
        record.setCommonGroundSnapshot(serializeJson(commonGround));
        record.setEndedAt(Instant.now());

        switch (outcome) {
            case FsiDeliberationOutcomeHandler.OutcomeAction.Execute exec -> {
                record.setStatus("COMPLETED");

                ledgerService.recordDeliberationDecision(
                        record.getId(), record.getChannelId(), record.getInstrument(),
                        exec.convergenceState(), exec.confidence(),
                        commonGround.establishedFacts().size(),
                        commonGround.disputedPoints().size(),
                        record.getParticipants(), null);

                var commitmentId = UUID.randomUUID();
                record.setCommitmentId(commitmentId);
                var provenance = new TradeProvenance(
                        record.getChannelId(), commitmentId,
                        exec.convergenceState(), exec.confidence());
                var tradeInstrument = new Instrument(exec.instrument(), AssetClass.EQUITY, "UNKNOWN");
                var strategy = strategyService.create(
                        "deliberation-" + record.getId(), StrategyType.MOMENTUM);
                var decision = new TradeDecision(
                        strategy.getId().toString(),
                        tradeInstrument, exec.side(), exec.quantity(),
                        exec.orderType(),
                        exec.orderType() == OrderType.LIMIT ? exec.limitPrice() : null,
                        "Deliberation " + exec.convergenceState() + " (confidence=" + exec.confidence() + ")",
                        provenance);

                var order = orderService.createFromDecision(decision);
                record.setTradeDecisionId(order.getId());
                log.infof("Deliberation %s → trade %s: %s %s %s",
                        recordId, order.getId(), exec.side(), exec.quantity(), exec.instrument());
            }
            case FsiDeliberationOutcomeHandler.OutcomeAction.Escalate esc -> {
                record.setStatus("COMPLETED");
                log.infof("Deliberation %s → escalation: %s", recordId, esc.reason());
            }
        }

        recordRepository.merge(record);
    }

    @Transactional
    public void failDeliberation(UUID recordId, String reason) {
        var record = recordRepository.findById(recordId).orElse(null);
        if (record != null) {
            record.setStatus("FAILED");
            record.setEndedAt(Instant.now());
            record.setSummary(reason);
            recordRepository.merge(record);
        }
        log.warnf("Deliberation %s failed: %s", recordId, reason);
    }

    public <T> void executeWithTimeout(UUID recordId, CompletableFuture<T> debateFuture,
                                        Duration timeout) {
        try {
            debateFuture.orTimeout(timeout.toMillis(), java.util.concurrent.TimeUnit.MILLISECONDS).join();
        } catch (java.util.concurrent.CompletionException e) {
            var self = CDI.current().select(FsiDeliberationOrchestrator.class).get();
            if (e.getCause() instanceof TimeoutException) {
                log.warnf("Deliberation %s timed out after %s", recordId, timeout);
                self.failDeliberation(recordId, "Wall-clock timeout after " + timeout.toSeconds() + "s");
            } else {
                log.errorf(e.getCause(), "Deliberation %s failed with unexpected error", recordId);
                self.failDeliberation(recordId, "Unexpected error: " + e.getCause().getMessage());
            }
        } catch (Exception e) {
            var self = CDI.current().select(FsiDeliberationOrchestrator.class).get();
            log.errorf(e, "Deliberation %s failed", recordId);
            self.failDeliberation(recordId, "Execution error: " + e.getMessage());
        }
    }

    private static String serializeJson(Object obj) {
        try {
            return MAPPER.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            log.warnf("Failed to serialize snapshot: %s", e.getMessage());
            return null;
        }
    }
}
