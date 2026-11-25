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

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class RouteTest {

    private AppServer server;

    private HttpClient client;

    @BeforeEach
    void setup() throws Exception {
        ApplicationConfig config = new ApplicationConfig().name("Fixture").version("2.0").resourceRoot("fixture");
        Bridge bridge = new Bridge(config, "0.12.0");
        server = new AppServer(new ResourceLoader(config, bridge));
        server.start();
        client = HttpClient.newHttpClient();
    }

    @AfterEach
    void cleanup() {
        if (server != null) {
            server.close();
        }
    }

    @Test
    void servesDynamicContent() throws Exception {
        server.route("/data/", (path, query) -> Response.ok("image/webp", "pixels".getBytes(StandardCharsets.UTF_8)).cache("public, max-age=60"));
        HttpResponse<byte[]> response = get("data/photo/1/thumb");
        assertEquals(200, response.statusCode());
        assertEquals("image/webp", response.headers().firstValue("Content-Type").orElse(""));
        assertEquals("public, max-age=60", response.headers().firstValue("Cache-Control").orElse(""));
        assertEquals("pixels", new String(response.body(), StandardCharsets.UTF_8));
    }

    @Test
    void passesThePathBelowThePrefixAndTheQuery() throws Exception {
        server.route("/blob/", (path, query) -> Response.ok(path + "?" + query));
        HttpResponse<byte[]> response = get("blob/42/thumb?v=7");
        assertEquals("/42/thumb?v=7", new String(response.body(), StandardCharsets.UTF_8));
    }

    @Test
    void fallsBackToStaticResourcesWhenTheRouteReturnsNull() throws Exception {
        server.route("/", (path, query) -> null);
        HttpResponse<byte[]> response = get("app.js");
        assertEquals(200, response.statusCode());
        assertTrue(new String(response.body(), StandardCharsets.UTF_8).contains("console.log"));
    }

    @Test
    void reportsRouteFailures() throws Exception {
        server.route("/broken/", (path, query) -> {
            throw new IllegalStateException("no data");
        });
        assertEquals(500, get("broken/1").statusCode());
    }

    @Test
    void answersHeadRequests() throws Exception {
        server.route("/head/", (path, query) -> Response.ok("text/plain; charset=utf-8", "1234567890".getBytes(StandardCharsets.UTF_8)));
        HttpRequest request = HttpRequest.newBuilder(URI.create(server.baseUrl() + "head/x")).method("HEAD", HttpRequest.BodyPublishers.noBody()).build();
        HttpResponse<byte[]> response = client.send(request, HttpResponse.BodyHandlers.ofByteArray());
        assertEquals(200, response.statusCode());
        assertEquals(0, response.body().length);
        assertEquals("text/plain; charset=utf-8", response.headers().firstValue("Content-Type").orElse(""));
    }

    @Test
    void rejectsReservedAndMalformedPrefixes() {
        assertThrows(IllegalArgumentException.class, () -> server.route("/__apocrypha/", (path, query) -> null));
        assertThrows(IllegalArgumentException.class, () -> server.route("photos/", (path, query) -> null));
        assertThrows(IllegalArgumentException.class, () -> server.route("/../etc/", (path, query) -> null));
    }

    private HttpResponse<byte[]> get(String path) throws Exception {
        return client.send(HttpRequest.newBuilder(URI.create(server.baseUrl() + path)).GET().build(), HttpResponse.BodyHandlers.ofByteArray());
    }
}
