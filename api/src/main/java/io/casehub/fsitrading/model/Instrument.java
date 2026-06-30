package io.casehub.fsitrading.model;

import java.util.Objects;

public record Instrument(String symbol, AssetClass assetClass, String exchange) {

    public Instrument {
        Objects.requireNonNull(symbol, "symbol");
        Objects.requireNonNull(assetClass, "assetClass");
        Objects.requireNonNull(exchange, "exchange");
        if (symbol.isBlank()) {
            throw new IllegalArgumentException("symbol must not be blank");
        }
    }
}
