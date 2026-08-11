package io.casehub.fsitrading.app.resource;

import io.casehub.fsitrading.app.model.PositionEntity;
import io.casehub.fsitrading.app.service.PositionService;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

@Path("/api/kpis")
@Produces(MediaType.APPLICATION_JSON)
public class KpiResource {

    @Inject
    PositionService positionService;

    @GET
    public KpiSummary getKpis() {
        var positions = positionService.findAll();
        long tradeCount = positions.stream()
                .filter(p -> p.getRealizedPnl().signum() != 0)
                .count();
        BigDecimal totalPnl = positions.stream()
                .map(PositionEntity::getRealizedPnl)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        long winCount = positions.stream()
                .filter(p -> p.getRealizedPnl().signum() > 0)
                .count();
        double winRate = tradeCount > 0 ? (double) winCount / tradeCount : 0.0;
        BigDecimal avgReturn = tradeCount > 0
                ? totalPnl.divide(BigDecimal.valueOf(tradeCount), 2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

        return new KpiSummary(totalPnl, winRate, tradeCount, avgReturn);
    }

    public record KpiSummary(BigDecimal totalPnl, double winRate, long tradeCount, BigDecimal avgReturn) {}
}
