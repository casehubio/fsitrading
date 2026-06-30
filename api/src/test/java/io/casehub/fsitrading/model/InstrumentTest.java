package io.casehub.fsitrading.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class InstrumentTest {

    @Test
    void validInstrument() {
        var inst = new Instrument("AAPL", AssetClass.EQUITY, "NASDAQ");
        assertEquals("AAPL", inst.symbol());
        assertEquals(AssetClass.EQUITY, inst.assetClass());
        assertEquals("NASDAQ", inst.exchange());
    }

    @Test
    void nullSymbolThrows() {
        assertThrows(NullPointerException.class,
                () -> new Instrument(null, AssetClass.EQUITY, "NASDAQ"));
    }

    @Test
    void blankSymbolThrows() {
        assertThrows(IllegalArgumentException.class,
                () -> new Instrument("  ", AssetClass.EQUITY, "NASDAQ"));
    }

    @Test
    void nullAssetClassThrows() {
        assertThrows(NullPointerException.class,
                () -> new Instrument("AAPL", null, "NASDAQ"));
    }

    @Test
    void nullExchangeThrows() {
        assertThrows(NullPointerException.class,
                () -> new Instrument("AAPL", AssetClass.EQUITY, null));
    }

    @Test
    void equalityByValue() {
        var a = new Instrument("AAPL", AssetClass.EQUITY, "NASDAQ");
        var b = new Instrument("AAPL", AssetClass.EQUITY, "NASDAQ");
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
    }

    @Test
    void differentSymbolNotEqual() {
        var aapl = new Instrument("AAPL", AssetClass.EQUITY, "NASDAQ");
        var msft = new Instrument("MSFT", AssetClass.EQUITY, "NASDAQ");
        assertNotEquals(aapl, msft);
    }
}
