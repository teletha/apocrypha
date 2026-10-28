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

import org.junit.jupiter.api.Test;

class BridgeTest {

    @Test
    void injectIntoHead() {
        Bridge bridge = bridge();
        String html = bridge.inject("<!doctype html><html><head><title>x</title></head><body></body></html>", true);

        assertTrue(html.contains("<base href=\"/\">"));
        assertTrue(html.contains("<script src=\"/__apocrypha/bridge.js\" data-apocrypha-bridge></script>"));
        assertTrue(html.indexOf("bridge.js") < html.indexOf("<title>"));
    }

    @Test
    void noBaseByDefault() {
        Bridge bridge = bridge();
        String html = bridge.inject("<!doctype html><html><head><title>x</title></head><body></body></html>");
        assertFalse(html.contains("<base"));
        assertTrue(html.contains("/__apocrypha/bridge.js"));
    }

    @Test
    void injectWithoutHead() {
        Bridge bridge = bridge();
        String html = bridge.inject("<!doctype html><html><body>x</body></html>", true);
        assertTrue(html.contains("<html>"));
        assertTrue(html.indexOf("bridge.js") < html.indexOf("<body>"));
    }

    @Test
    void injectWithoutHtml() {
        Bridge bridge = bridge();
        String html = bridge.inject("<p>x</p>", true);
        assertTrue(html.startsWith("<base href=\"/\">"));
        assertTrue(html.contains("<script src=\"/__apocrypha/bridge.js\""));
        assertTrue(html.indexOf("bridge.js") < html.indexOf("<p>"));
    }

    @Test
    void idempotent() {
        Bridge bridge = bridge();
        String html = bridge.inject("<html><head></head><body></body></html>");
        assertEquals(html, bridge.inject(html));
    }

    @Test
    void keepExistingBase() {
        Bridge bridge = bridge();
        String html = bridge.inject("<html><head><base href=\"/sub/\"></head><body></body></html>", true);
        assertFalse(html.contains("<base href=\"/\">"));
        assertTrue(html.contains("/__apocrypha/bridge.js"));
    }

    private static Bridge bridge() {
        ApplicationConfig config = new ApplicationConfig().name("Test").version("1.0");
        return new Bridge(config, "152.0.0.0");
    }
}
