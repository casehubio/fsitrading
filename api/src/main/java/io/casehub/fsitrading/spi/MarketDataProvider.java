package io.casehub.fsitrading.spi;

import io.casehub.platform.simulation.SimulationEligible;

@SimulationEligible(name = "market-data-provider")
public interface MarketDataProvider {

    MarketDataSnapshot snapshot(String instrument, int depth);
}
