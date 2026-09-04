package io.casehub.fsitrading.app.compliance;

import io.casehub.api.spi.routing.RequirementStatus;
import io.casehub.api.spi.routing.TrustRoutingRequirement;

public record ComplianceStatusRecord(
        String regulation,
        String requirement,
        String mechanism,
        String status,
        String evidenceUrl) {

    public static ComplianceStatusRecord from(TrustRoutingRequirement req) {
        String citation = req.citation();
        String regulation;
        String requirement;
        int dashIndex = citation.indexOf(" — ");
        if (dashIndex >= 0) {
            regulation = citation.substring(0, dashIndex);
            requirement = citation.substring(dashIndex + 3);
        } else {
            regulation = citation;
            requirement = req.requirementId();
        }
        return new ComplianceStatusRecord(
                regulation,
                requirement,
                req.mechanism(),
                mapStatus(req.status()),
                null);
    }

    private static String mapStatus(RequirementStatus status) {
        return switch (status) {
            case CLOSED -> "MET";
            case PARTIAL -> "PARTIAL";
            case GAP -> "GAP";
            case BREACHED -> "BREACHED";
        };
    }
}
