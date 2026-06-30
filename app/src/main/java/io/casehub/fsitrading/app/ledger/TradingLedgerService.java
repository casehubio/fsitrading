package io.casehub.fsitrading.app.ledger;

import io.casehub.fsitrading.FsiActorIdentity;
import io.casehub.fsitrading.app.model.OrderEntity;
import io.casehub.fsitrading.model.StrategyType;
import io.casehub.ledger.api.model.LedgerEntryType;
import io.casehub.ledger.runtime.model.LedgerEntry;
import io.casehub.ledger.runtime.repository.LedgerEntryRepository;
import io.casehub.platform.api.identity.ActorType;
import io.casehub.platform.api.identity.TenancyConstants;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@ApplicationScoped
public class TradingLedgerService {

    private static final String EXECUTOR_ACTOR_ID = "fsi-trading-executor";
    private static final String EXECUTOR_ACTOR_ROLE = "trading-executor";

    @Inject
    LedgerEntryRepository repository;

    public UUID recordStrategyEvaluation(UUID orderId, UUID strategyId, String strategyName,
                                         StrategyType strategyType,
                                         String instrument, String signal, String rationale) {
        final int seq = nextSequenceNumber(orderId);
        final var entry = new StrategyEvaluationLedgerEntry();
        entry.id = UUID.randomUUID();
        entry.subjectId = orderId;
        entry.sequenceNumber = seq;
        entry.entryType = LedgerEntryType.EVENT;
        entry.actorId = FsiActorIdentity.forStrategy(strategyType);
        entry.actorType = ActorType.AGENT;
        entry.actorRole = FsiActorIdentity.actorRole(strategyType);
        entry.occurredAt = Instant.now();
        entry.strategyId = strategyId;
        entry.strategyName = strategyName;
        entry.instrument = instrument;
        entry.signal = signal;
        entry.rationale = rationale;
        repository.save(entry, TenancyConstants.DEFAULT_TENANT_ID);
        return entry.id;
    }

    public UUID recordOrderExecution(OrderEntity order, UUID causedByEntryId) {
        final int seq = nextSequenceNumber(order.getId());
        final var entry = new OrderExecutionLedgerEntry();
        entry.id = UUID.randomUUID();
        entry.subjectId = order.getId();
        entry.sequenceNumber = seq;
        entry.entryType = LedgerEntryType.EVENT;
        entry.actorId = EXECUTOR_ACTOR_ID;
        entry.actorType = ActorType.AGENT;
        entry.actorRole = EXECUTOR_ACTOR_ROLE;
        entry.occurredAt = Instant.now();
        entry.causedByEntryId = causedByEntryId;
        entry.orderId = order.getId();
        entry.instrument = order.getInstrument();
        entry.side = order.getSide().name();
        entry.quantity = order.getQuantity();
        entry.fillPrice = order.getFillPrice();
        entry.strategyId = order.getStrategyId();
        repository.save(entry, TenancyConstants.DEFAULT_TENANT_ID);
        return entry.id;
    }

    public List<LedgerEntry> findByOrderId(UUID orderId) {
        return repository.findBySubjectId(orderId, TenancyConstants.DEFAULT_TENANT_ID);
    }

    private int nextSequenceNumber(final UUID subjectId) {
        return repository.findLatestBySubjectId(subjectId, TenancyConstants.DEFAULT_TENANT_ID)
                .map(e -> e.sequenceNumber + 1)
                .orElse(1);
    }
}
