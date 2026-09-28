package io.casehub.fsitrading.app.api;

import io.casehub.fsitrading.app.resource.LayoutResource;
import io.casehub.platform.api.mcp.McpDomain;
import io.casehub.platform.api.mcp.PathParam;
import io.casehub.platform.api.mcp.PlatformMutation;
import io.casehub.platform.api.mcp.PlatformQuery;
import io.casehub.platform.api.mcp.RestPath;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@McpDomain(value = "fsi/layout", basePath = "/api/fsi/layout")
@ApplicationScoped
public class FsiLayoutApi {

    @Inject LayoutResource resource;

    @PlatformQuery("Get layout by key")
    @RestPath("/{key}")
    public Object getLayout(@PathParam String key) {
        return resource.get(key).getEntity();
    }

    @PlatformMutation("Save layout by key")
    @RestPath("/{key}")
    public Object putLayout(@PathParam String key, String body) {
        return resource.put(key, body).getEntity();
    }
}
