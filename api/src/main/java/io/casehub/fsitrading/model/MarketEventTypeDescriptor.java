package io.casehub.fsitrading.model;

public record MarketEventTypeDescriptor(
        MarketEventType eventType,
        String agentName,
        String fallbackAction) {

    public String eventSource() {
        Class<? extends MarketEvent> domain = eventType.domain();
        if (domain == MarketEvent.RawMarketData.class) {return "Raw";}
        if (domain == MarketEvent.DetectedEvent.class) {return "Market-detected";}
        if (domain == MarketEvent.OperationalEvent.class) {return "External";}
        return "Unknown";
    }

    private static final java.util.Map<MarketEventType, MarketEventTypeDescriptor> REGISTRY = java.util.Map.of(
            MarketEventType.FLASH_CRASH, new MarketEventTypeDescriptor(
                    MarketEventType.FLASH_CRASH, "emergencyHaltAgent", "halt"),
            MarketEventType.LIQUIDITY_DROP, new MarketEventTypeDescriptor(
                    MarketEventType.LIQUIDITY_DROP, "positionReducerAgent", "reduce"),
            MarketEventType.GAP_OPEN, new MarketEventTypeDescriptor(
                    MarketEventType.GAP_OPEN, "reEvaluatorAgent", "monitor"),
            MarketEventType.COUNTERPARTY_FAILURE, new MarketEventTypeDescriptor(
                    MarketEventType.COUNTERPARTY_FAILURE, "exposureCloserAgent", "halt"),
            MarketEventType.CIRCUIT_BREAKER, new MarketEventTypeDescriptor(
                    MarketEventType.CIRCUIT_BREAKER, "haltAndWaitAgent", "halt"),
            MarketEventType.NEWS_EVENT, new MarketEventTypeDescriptor(
                    MarketEventType.NEWS_EVENT, "sentimentAnalyserAgent", "monitor"),
            MarketEventType.MARGIN_CALL, new MarketEventTypeDescriptor(
                    MarketEventType.MARGIN_CALL, "liquidationAgent", "reduce")
                                                                                                              );

    public static MarketEventTypeDescriptor forType(MarketEventType type) {
        return REGISTRY.get(type);
    }
}
