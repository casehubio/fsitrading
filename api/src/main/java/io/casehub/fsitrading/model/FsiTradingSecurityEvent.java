package io.casehub.fsitrading.model;

import java.time.Instant;

public record FsiTradingSecurityEvent(
        Severity severity,
        String source,
        String detail,
        Instant timestamp) {

    public enum Severity { INFORMATIONAL, WARNING, CRITICAL }
}
