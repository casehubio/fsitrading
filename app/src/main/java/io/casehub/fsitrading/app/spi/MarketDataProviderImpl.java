package io.casehub.fsitrading.app.spi;

import io.casehub.fsitrading.app.service.SyntheticMarketDataProvider;
import io.casehub.fsitrading.model.PriceTick;
import io.casehub.fsitrading.spi.MarketDataProvider;
import io.casehub.fsitrading.spi.MarketDataSnapshot;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.math.BigDecimal;
import java.math.RoundingMode;

@ApplicationScoped
public class MarketDataProviderImpl implements MarketDataProvider {

    private final SyntheticMarketDataProvider syntheticProvider;

    @Inject
    public MarketDataProviderImpl(SyntheticMarketDataProvider syntheticProvider) {
        this.syntheticProvider = syntheticProvider;
    }

    @Override
    public MarketDataSnapshot snapshot(String instrument, int depth) {
        PriceTick tick = syntheticProvider.generateTick();
        var price = tick.price();
        var spread = price.multiply(new BigDecimal("0.001")).setScale(2, RoundingMode.HALF_UP);
        var bid = price.subtract(spread.divide(BigDecimal.TWO, 2, RoundingMode.HALF_UP));
        var ask = price.add(spread.divide(BigDecimal.TWO, 2, RoundingMode.HALF_UP));
        return new MarketDataSnapshot(bid, ask, spread, tick.volume());
    }
}
