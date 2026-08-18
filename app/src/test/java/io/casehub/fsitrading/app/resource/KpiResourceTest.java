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
    private io.casehub.fsitrading.app.service.StrategyService strategyService;


    @BeforeEach
    void setUp() {
        resource                 = new KpiResource();
        positionService          = mock(PositionService.class);
        strategyService          = mock(io.casehub.fsitrading.app.service.StrategyService.class);
        resource.positionService = positionService;
        resource.strategyService = strategyService;
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

    @Test
    void heatmap_returnsInstrumentByStrategyPnl() {
        var strategyId = UUID.randomUUID();
        var p1         = new PositionEntity(UUID.randomUUID(), "AAPL", AssetClass.EQUITY, strategyId);
        p1.setRealizedPnl(new BigDecimal("100"));
        var p2 = new PositionEntity(UUID.randomUUID(), "MSFT", AssetClass.EQUITY, strategyId);
        p2.setRealizedPnl(BigDecimal.ZERO);
        when(positionService.findAll()).thenReturn(List.of(p1, p2));

        var strategy = mock(io.casehub.fsitrading.app.model.StrategyEntity.class);
        when(strategy.getName()).thenReturn("momentum");
        when(strategyService.findById(strategyId)).thenReturn(strategy);

        var cells = resource.getHeatmap();

        assertEquals(1, cells.size());
        assertEquals("AAPL", cells.get(0).instrument());
        assertEquals("momentum", cells.get(0).strategy());
        assertEquals(0, new BigDecimal("100").compareTo(cells.get(0).pnl()));
    }

    @Test
    void heatmap_emptyPositions_returnsEmpty() {
        when(positionService.findAll()).thenReturn(List.of());

        var cells = resource.getHeatmap();

        assertTrue(cells.isEmpty());
    }


    private PositionEntity position(String instrument, String realizedPnl) {
        var p = new PositionEntity(UUID.randomUUID(), instrument, AssetClass.EQUITY, UUID.randomUUID());
        p.setRealizedPnl(new BigDecimal(realizedPnl));
        return p;
    }
}
