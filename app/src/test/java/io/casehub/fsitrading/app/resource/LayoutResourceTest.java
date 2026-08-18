package io.casehub.fsitrading.app.resource;

import io.casehub.pages.layout.LayoutPersistenceStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class LayoutResourceTest {

    private LayoutResource resource;
    private final Map<String, String> store = new HashMap<>();

    @BeforeEach
    void setUp() {
        store.clear();
        resource = new LayoutResource();
        resource.layoutStore = new LayoutPersistenceStore() {
            @Override
            public Optional<String> load(String key, String tenantId, String userId) {
                return Optional.ofNullable(store.get(key));
            }

            @Override
            public void save(String key, String tenantId, String userId, String payload) {
                store.put(key, payload);
            }

            @Override
            public void delete(String key, String tenantId, String userId) {
                store.remove(key);
            }
        };
    }

    @Test
    void get_missingKey_returns404() {
        var response = resource.get("nonexistent");
        assertEquals(404, response.getStatus());
    }

    @Test
    void putThenGet_returnsStoredValue() {
        resource.put("desk", "{\"layout\":\"saved\"}");
        var response = resource.get("desk");
        assertEquals(200, response.getStatus());
        assertEquals("{\"layout\":\"saved\"}", response.getEntity());
    }

    @Test
    void put_returnsNoContent() {
        var response = resource.put("desk", "{\"layout\":\"new\"}");
        assertEquals(204, response.getStatus());
    }

    @Test
    void put_overwritesPreviousValue() {
        resource.put("desk", "{\"v\":1}");
        resource.put("desk", "{\"v\":2}");
        var response = resource.get("desk");
        assertEquals("{\"v\":2}", response.getEntity());
    }
}
