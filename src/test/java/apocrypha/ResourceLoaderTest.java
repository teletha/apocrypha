/*
 * Copyright (C) 2026 The APOCRYPHA Development Team
 *
 * Licensed under the MIT License (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *          https://opensource.org/licenses/MIT
 */
package apocrypha;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ResourceLoaderTest {

    private ApplicationConfig config;

    private ResourceLoader loader;

    @BeforeEach
    void setup() {
        config = new ApplicationConfig().name("Fixture").version("2.0").resourceRoot("fixture");
        Bridge bridge = new Bridge(config, "0.12.0");
        loader = new ResourceLoader(config, bridge);
    }

    @Test
    void index() {
        Resource resource = loader.load("/");

        assertEquals(200, resource.status());
        assertEquals("text/html; charset=utf-8", resource.mimeType());
        String html = text(resource);
        assertFalse(html.contains("<base href=\"/\">"));
        assertTrue(html.contains("/__apocrypha/bridge.js"));
        assertTrue(html.contains("<h1>Fixture</h1>"));
    }

    @Test
    void directory() {
        Resource resource = loader.load("/nested/");
        assertEquals(200, resource.status());
        assertTrue(text(resource).contains("Nested index"));
    }

    @Test
    void asset() {
        Resource resource = loader.load("/app.js");
        assertEquals(200, resource.status());
        assertEquals("text/javascript; charset=utf-8", resource.mimeType());
        assertTrue(text(resource).contains("console.log"));
    }

    @Test
    void bridge() {
        Resource resource = loader.load("/__apocrypha/bridge.js");
        assertEquals(200, resource.status());
        assertEquals("text/javascript; charset=utf-8", resource.mimeType());
        String script = text(resource);
        assertTrue(script.contains("window.apocrypha"));
        assertTrue(script.contains("\"name\": \"Fixture\""));
        assertTrue(script.contains("\"webview\": \"0.12.0\""));
        assertFalse(script.contains("__APOCRYPHA_META__"));
    }

    @Test
    void spaFallback() {
        Resource resource = loader.load("/notes/2026");
        assertEquals(200, resource.status());
        String html = text(resource);
        assertTrue(html.contains("<base href=\"/\">"));
        assertTrue(html.contains("<h1>Fixture</h1>"));
    }

    @Test
    void missingAssetDoesNotFallBack() {
        Resource resource = loader.load("/missing.js");
        assertEquals(404, resource.status());
        assertTrue(text(resource).contains("Resource not found"));
    }

    @Test
    void traversal() {
        assertEquals(404, loader.load("/../secret.txt").status());
        assertEquals(404, loader.load("/..\\secret.txt").status());
        assertEquals(404, loader.load("/nested/../../secret.txt").status());
    }

    @Test
    void reservedNamespace() {
        assertEquals(404, loader.load("/__apocrypha/missing.js").status());
    }

    @Test
    void fallbackDisabled() {
        config.spaFallback(false);
        assertEquals(404, loader.load("/notes/2026").status());
    }

    @Test
    void mimeTypes() {
        assertEquals("text/css; charset=utf-8", MimeTypes.of("a/b.css"));
        assertEquals("image/png", MimeTypes.of("icon.png"));
        assertEquals("application/wasm", MimeTypes.of("app.wasm"));
        assertEquals("application/octet-stream", MimeTypes.of("README"));
    }

    private static String text(Resource resource) {
        return new String(resource.data(), StandardCharsets.UTF_8);
    }
}
