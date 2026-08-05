# io.casehub.fsitrading.model.TradeDecision

**Package:** `io.casehub.fsitrading.model`

**Kind:** `record`

## Fields

### `instrument` (`io.casehub.fsitrading.model.Instrument`)

### `limitPrice` (`java.math.BigDecimal`)

### `orderType` (`io.casehub.fsitrading.model.OrderType`)

### `quantity` (`java.math.BigDecimal`)

### `rationale` (`java.lang.String`)

### `side` (`io.casehub.fsitrading.model.OrderSide`)

### `strategyId` (`java.lang.String`)

## Record Components

### `instrument` (`io.casehub.fsitrading.model.Instrument`)

### `limitPrice` (`java.math.BigDecimal`)

### `orderType` (`io.casehub.fsitrading.model.OrderType`)

### `quantity` (`java.math.BigDecimal`)

### `rationale` (`java.lang.String`)

### `side` (`io.casehub.fsitrading.model.OrderSide`)

### `strategyId` (`java.lang.String`)

## Constructors

### `public TradeDecision(java.lang.String strategyId, io.casehub.fsitrading.model.Instrument instrument, io.casehub.fsitrading.model.OrderSide side, java.math.BigDecimal quantity, io.casehub.fsitrading.model.OrderType orderType, java.math.BigDecimal limitPrice, java.lang.String rationale)`

#### Parameters

- `strategyId` (`java.lang.String`)
- `instrument` (`io.casehub.fsitrading.model.Instrument`)
- `side` (`io.casehub.fsitrading.model.OrderSide`)
- `quantity` (`java.math.BigDecimal`)
- `orderType` (`io.casehub.fsitrading.model.OrderType`)
- `limitPrice` (`java.math.BigDecimal`)
- `rationale` (`java.lang.String`)

## Methods

### `public final boolean equals(java.lang.Object o)`

#### Parameters

- `o` (`java.lang.Object`)

### `public final int hashCode()`

### `public io.casehub.fsitrading.model.Instrument instrument()`

### `public java.math.BigDecimal limitPrice()`

### `public io.casehub.fsitrading.model.OrderType orderType()`

### `public java.math.BigDecimal quantity()`

### `public java.lang.String rationale()`

### `public io.casehub.fsitrading.model.OrderSide side()`

### `public java.lang.String strategyId()`

### `public final java.lang.String toString()`
