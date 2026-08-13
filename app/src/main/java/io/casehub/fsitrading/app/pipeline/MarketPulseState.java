package io.casehub.fsitrading.app.pipeline;

import java.time.Instant;

public class MarketPulseState {

    private boolean marketClosed;
    private boolean shutdownRequested;
    private Instant lastNarrativeTimestamp;
    private Instant sessionStart;
    private Instant sessionEnd;

    public boolean isMarketClosed() { return marketClosed; }
    public void setMarketClosed(boolean marketClosed) { this.marketClosed = marketClosed; }

    public boolean isShutdownRequested() { return shutdownRequested; }
    public void setShutdownRequested(boolean shutdownRequested) { this.shutdownRequested = shutdownRequested; }

    public Instant getLastNarrativeTimestamp() { return lastNarrativeTimestamp; }
    public void setLastNarrativeTimestamp(Instant lastNarrativeTimestamp) {
        this.lastNarrativeTimestamp = lastNarrativeTimestamp;
    }

    public Instant getSessionStart() { return sessionStart; }
    public void setSessionStart(Instant sessionStart) { this.sessionStart = sessionStart; }

    public Instant getSessionEnd() { return sessionEnd; }
    public void setSessionEnd(Instant sessionEnd) { this.sessionEnd = sessionEnd; }

    public boolean shouldExit() {
        return marketClosed || shutdownRequested;
    }
}
