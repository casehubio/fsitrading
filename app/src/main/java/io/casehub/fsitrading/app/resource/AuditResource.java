package io.casehub.fsitrading.app.resource;

import io.casehub.fsitrading.app.ledger.OrderExecutionLedgerEntry;
import io.casehub.fsitrading.app.ledger.StrategyEvaluationLedgerEntry;
import io.casehub.fsitrading.app.ledger.TradingLedgerService;
import io.casehub.ledger.runtime.model.LedgerEntry;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Path("/api/audit")
@Produces(MediaType.APPLICATION_JSON)
public class AuditResource {

    @Inject
    TradingLedgerService tradingLedgerService;

    @GET
    @Path("/orders/{orderId}")
    public List<AuditEntryDto> getOrderAuditTrail(@PathParam("orderId") UUID orderId) {
        return tradingLedgerService.findByOrderId(orderId).stream()
                .map(AuditResource::toDto)
                .toList();
    }

    private static AuditEntryDto toDto(LedgerEntry entry) {
        var dto = new AuditEntryDto();
        dto.id = entry.id;
        dto.subjectId = entry.subjectId;
        dto.sequenceNumber = entry.sequenceNumber;
        dto.entryType = entry.entryType != null ? entry.entryType.name() : null;
        dto.actorId = entry.actorId;
        dto.actorRole = entry.actorRole;
        dto.causedByEntryId = entry.causedByEntryId;
        dto.digest = entry.digest;
        dto.occurredAt = entry.occurredAt;

        if (entry instanceof OrderExecutionLedgerEntry exec) {
            dto.discriminator = "ORDER_EXECUTION";
            dto.orderId = exec.orderId;
            dto.instrument = exec.instrument;
            dto.side = exec.side;
            dto.quantity = exec.quantity;
            dto.fillPrice = exec.fillPrice;
            dto.strategyId = exec.strategyId;
        } else if (entry instanceof StrategyEvaluationLedgerEntry eval) {
            dto.discriminator = "STRATEGY_EVALUATION";
            dto.strategyId = eval.strategyId;
            dto.strategyName = eval.strategyName;
            dto.instrument = eval.instrument;
            dto.signal = eval.signal;
            dto.rationale = eval.rationale;
        }
        return dto;
    }

    public static class AuditEntryDto {
        public UUID id;
        public UUID subjectId;
        public int sequenceNumber;
        public String entryType;
        public String discriminator;
        public String actorId;
        public String actorRole;
        public UUID causedByEntryId;
        public String digest;
        public Instant occurredAt;
        public UUID orderId;
        public String instrument;
        public String side;
        public BigDecimal quantity;
        public BigDecimal fillPrice;
        public UUID strategyId;
        public String strategyName;
        public String signal;
        public String rationale;
    }
}
