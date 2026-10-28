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
        Bridge bridge = new Bridge(config, "152.0.0.0");
        loader = new ResourceLoader(config, bridge);
    }

    @Test
    void handlesApplicationUrl() {
        assertTrue(loader.handles("apocrypha://app/index.html"));
        assertTrue(loader.handles("apocrypha://app/notes/2026"));
        assertFalse(loader.handles("https://example.com/"));
        assertFalse(loader.handles("apocrypha://other/index.html"));
        assertFalse(loader.handles(null));
        assertFalse(loader.handles("not a url"));
    }

    @Test
    void index() {
        Resource resource = loader.load("apocrypha://app/");

        assertEquals(200, resource.status());
        assertEquals("text/html; charset=utf-8", resource.mimeType());
        String html = text(resource);
        assertFalse(html.contains("<base href=\"/\">"));
        assertTrue(html.contains("/__apocrypha/bridge.js"));
        assertTrue(html.contains("<h1>Fixture</h1>"));
    }

    @Test
    void directory() {
        Resource resource = loader.load("apocrypha://app/nested/");
        assertEquals(200, resource.status());
        assertTrue(text(resource).contains("Nested index"));
    }

    @Test
    void asset() {
        Resource resource = loader.load("apocrypha://app/app.js");
        assertEquals(200, resource.status());
        assertEquals("text/javascript; charset=utf-8", resource.mimeType());
        assertTrue(text(resource).contains("console.log"));
    }

    @Test
    void bridge() {
        Resource resource = loader.load("apocrypha://app/__apocrypha/bridge.js");
        assertEquals(200, resource.status());
        assertEquals("text/javascript; charset=utf-8", resource.mimeType());
        String script = text(resource);
        assertTrue(script.contains("window.apocrypha"));
        assertTrue(script.contains("\"name\": \"Fixture\""));
        assertFalse(script.contains("__APOCRYPHA_META__"));
    }

    @Test
    void spaFallback() {
        Resource resource = loader.load("apocrypha://app/notes/2026");
        assertEquals(200, resource.status());
        String html = text(resource);
        assertTrue(html.contains("<base href=\"/\">"));
        assertTrue(html.contains("<h1>Fixture</h1>"));
    }

    @Test
    void missingAssetDoesNotFallBack() {
        Resource resource = loader.load("apocrypha://app/missing.js");
        assertEquals(404, resource.status());
        assertTrue(text(resource).contains("Resource not found"));
    }

    @Test
    void traversal() {
        assertEquals(404, loader.load("apocrypha://app/%2e%2e/secret.txt").status());
        assertEquals(404, loader.load("apocrypha://app/%2e%2e%2fsecret.txt").status());
        assertEquals(404, loader.load("apocrypha://app/..%5csecret.txt").status());
    }

    @Test
    void wrongOrigin() {
        assertEquals(404, loader.load("https://app/index.html").status());
        assertEquals(404, loader.load("apocrypha://other/index.html").status());
    }

    @Test
    void fallbackDisabled() {
        config.spaFallback(false);
        assertEquals(404, loader.load("apocrypha://app/notes/2026").status());
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
