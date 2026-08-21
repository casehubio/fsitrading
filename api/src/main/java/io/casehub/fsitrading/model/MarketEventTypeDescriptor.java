package io.casehub.fsitrading.model;

import java.util.Map;

public record MarketEventTypeDescriptor(
        MarketEventType eventType,
        String agentName,
        String eventSource,
        String fallbackAction) {

    private static final Map<MarketEventType, MarketEventTypeDescriptor> REGISTRY = Map.of(
            MarketEventType.FLASH_CRASH, new MarketEventTypeDescriptor(
                    MarketEventType.FLASH_CRASH, "emergencyHaltAgent", "Market-detected", "halt"),
            MarketEventType.LIQUIDITY_DROP, new MarketEventTypeDescriptor(
                    MarketEventType.LIQUIDITY_DROP, "positionReducerAgent", "Market-detected", "reduce"),
            MarketEventType.GAP_OPEN, new MarketEventTypeDescriptor(
                    MarketEventType.GAP_OPEN, "reEvaluatorAgent", "Market-detected", "monitor"),
            MarketEventType.COUNTERPARTY_FAILURE, new MarketEventTypeDescriptor(
                    MarketEventType.COUNTERPARTY_FAILURE, "exposureCloserAgent", "External", "halt"),
            MarketEventType.CIRCUIT_BREAKER, new MarketEventTypeDescriptor(
                    MarketEventType.CIRCUIT_BREAKER, "haltAndWaitAgent", "Market-detected", "halt"),
            MarketEventType.NEWS_EVENT, new MarketEventTypeDescriptor(
                    MarketEventType.NEWS_EVENT, "sentimentAnalyserAgent", "Market-detected", "monitor"),
            MarketEventType.MARGIN_CALL, new MarketEventTypeDescriptor(
                    MarketEventType.MARGIN_CALL, "liquidationAgent", "External", "reduce")
    );

    public static MarketEventTypeDescriptor forType(MarketEventType type) {
        return REGISTRY.get(type);
    }
}
