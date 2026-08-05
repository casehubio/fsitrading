# io.casehub.fsitrading.spi.StrategyEvaluator

**Package:** `io.casehub.fsitrading.spi`

**Kind:** `interface`

## Methods

### `public abstract java.util.Optional<io.casehub.fsitrading.model.TradeDecision> evaluate(java.lang.String strategyId, io.casehub.fsitrading.model.Instrument instrument, java.math.BigDecimal currentPrice, java.util.Map<java.lang.String,java.lang.Object> marketContext)`

#### Parameters

- `strategyId` (`java.lang.String`)
- `instrument` (`io.casehub.fsitrading.model.Instrument`)
- `currentPrice` (`java.math.BigDecimal`)
- `marketContext` (`java.util.Map<java.lang.String,java.lang.Object>`)
