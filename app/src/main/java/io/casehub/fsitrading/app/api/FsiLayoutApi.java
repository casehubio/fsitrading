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
    public String getLayout(@PathParam String key) {
        return (String) resource.get(key).getEntity();
    }

    @PlatformMutation("Save layout by key")
    @RestPath("/{key}")
    public String putLayout(@PathParam String key, String body) {
        resource.put(key, body);
        return key;
    }
}
