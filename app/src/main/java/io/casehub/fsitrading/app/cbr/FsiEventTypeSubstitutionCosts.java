package io.casehub.fsitrading.app.cbr;

import io.casehub.fsitrading.model.MarketEventType;
import io.casehub.neocortex.memory.cbr.SimilaritySpec;

public final class FsiEventTypeSubstitutionCosts {

    private FsiEventTypeSubstitutionCosts() {}

    public static SimilaritySpec.EditDistanceSpec build() {
        var builder = SimilaritySpec.categoricalTableBuilder();

        builder.add("FLASH_CRASH", "LIQUIDITY_DROP", 0.7);
        builder.add("FLASH_CRASH", "GAP_OPEN", 0.4);
        builder.add("FLASH_CRASH", "CIRCUIT_BREAKER", 0.6);
        builder.add("LIQUIDITY_DROP", "MARGIN_CALL", 0.5);
        builder.add("NEWS_EVENT", "COUNTERPARTY_FAILURE", 0.3);

        var types = MarketEventType.values();
        for (int i = 0; i < types.length; i++) {
            for (int j = i + 1; j < types.length; j++) {
                if (types[i].domain().equals(types[j].domain())) {
                    String a = types[i].name();
                    String b = types[j].name();
                    try {
                        builder.add(a, b, 0.2);
                    } catch (IllegalArgumentException e) {
                        // Pair already registered with explicit value
                    }
                }
            }
        }

        return new SimilaritySpec.EditDistanceSpec(
                builder.build().similarities());
    }
}
