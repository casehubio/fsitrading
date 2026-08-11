package io.casehub.fsitrading.app.resource;

import io.casehub.fsitrading.app.model.PositionEntity;
import io.casehub.fsitrading.app.service.PositionService;
import io.casehub.fsitrading.model.AssetClass;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class KpiResourceTest {

    private KpiResource resource;
    private PositionService positionService;

    @BeforeEach
    void setUp() {
        resource = new KpiResource();
        positionService = mock(PositionService.class);
        resource.positionService = positionService;
    }

    @Test
    void returnsAggregatedKpis() {
        var p1 = position("AAPL", "100");
        var p2 = position("GOOG", "-50");
        var p3 = position("MSFT", "200");
        when(positionService.findAll()).thenReturn(List.of(p1, p2, p3));

        var kpis = resource.getKpis();

        assertEquals(0, new BigDecimal("250").compareTo(kpis.totalPnl()));
        assertEquals(3, kpis.tradeCount());
        assertEquals(2.0 / 3.0, kpis.winRate(), 0.001);
    }

    @Test
    void emptyPositions_returnsZeros() {
        when(positionService.findAll()).thenReturn(List.of());

        var kpis = resource.getKpis();

        assertEquals(BigDecimal.ZERO, kpis.totalPnl());
        assertEquals(0, kpis.tradeCount());
        assertEquals(0.0, kpis.winRate());
    }

    private PositionEntity position(String instrument, String realizedPnl) {
        var p = new PositionEntity(UUID.randomUUID(), instrument, AssetClass.EQUITY, UUID.randomUUID());
        p.setRealizedPnl(new BigDecimal(realizedPnl));
        return p;
    }
}
