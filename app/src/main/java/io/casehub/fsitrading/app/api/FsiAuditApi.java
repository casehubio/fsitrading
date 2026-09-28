package io.casehub.fsitrading.app.api;

import io.casehub.fsitrading.app.resource.AuditResource;
import io.casehub.platform.api.mcp.McpDomain;
import io.casehub.platform.api.mcp.PathParam;
import io.casehub.platform.api.mcp.PlatformQuery;
import io.casehub.platform.api.mcp.RestPath;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.UUID;

@McpDomain(value = "fsi/audit", basePath = "/api/fsi/audit")
@ApplicationScoped
public class FsiAuditApi {

    @Inject AuditResource resource;

    @PlatformQuery("Get audit trail for an order")
    @RestPath("/orders/{orderId}")
    public Object getOrderAuditTrail(@PathParam UUID orderId) {
        return resource.getOrderAuditTrail(orderId);
    }
}
