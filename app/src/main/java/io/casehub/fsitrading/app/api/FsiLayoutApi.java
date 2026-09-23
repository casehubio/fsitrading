package io.casehub.fsitrading.app.api;

import io.casehub.pages.layout.LayoutPersistenceStore;
import io.casehub.platform.api.mcp.McpDomain;
import io.casehub.platform.api.mcp.PathParam;
import io.casehub.platform.api.mcp.PlatformMutation;
import io.casehub.platform.api.mcp.PlatformQuery;
import io.casehub.platform.api.mcp.RestPath;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.NotFoundException;

@McpDomain(value = "fsi/layout", basePath = "/api/fsi/layout")
@ApplicationScoped
public class FsiLayoutApi {

    @Inject LayoutPersistenceStore layoutStore;

    @PlatformQuery("Get layout by key")
    @RestPath("/{key}")
    public String getLayout(@PathParam String key) {
        return layoutStore.load(key, "default", "default")
                .orElseThrow(() -> new NotFoundException("Layout not found: " + key));
    }

    @PlatformMutation("Save layout by key")
    @RestPath("/{key}")
    public void putLayout(@PathParam String key, String body) {
        layoutStore.save(key, "default", "default", body);
    }
}
