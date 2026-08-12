package io.casehub.fsitrading.app.pipeline;

import io.casehub.blocks.summarisation.EventLevel;

public final class FsiEventLevels {

    private FsiEventLevels() {}

    public static final EventLevel TICK = new EventLevel("tick", 0);
    public static final EventLevel BAR_1M = new EventLevel("bar-1m", 1);
    public static final EventLevel TREND_5M = new EventLevel("trend-5m", 2);
    public static final EventLevel REGIME_1H = new EventLevel("regime-1h", 3);
    public static final EventLevel NARRATIVE = new EventLevel("narrative", 4);
}
