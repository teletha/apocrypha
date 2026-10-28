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

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AppServerTest {

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
    void servesTheEntryDocument() throws Exception {
        HttpResponse<String> response = get("");
        assertEquals(200, response.statusCode());
        assertTrue(response.body().contains("<h1>Fixture</h1>"));
        assertTrue(response.body().contains("/__apocrypha/bridge.js"));
        assertEquals("text/html; charset=utf-8", response.headers().firstValue("Content-Type").orElse(""));
    }

    @Test
    void servesAssets() throws Exception {
        HttpResponse<String> response = get("app.js");
        assertEquals(200, response.statusCode());
        assertEquals("text/javascript; charset=utf-8", response.headers().firstValue("Content-Type").orElse(""));
        assertTrue(response.body().contains("console.log"));
    }

    @Test
    void servesTheBridge() throws Exception {
        HttpResponse<String> response = get("__apocrypha/bridge.js");
        assertEquals(200, response.statusCode());
        assertTrue(response.body().contains("window.apocrypha"));
    }

    @Test
    void reportsMissingResources() throws Exception {
        assertEquals(404, get("missing.js").statusCode());
    }

    @Test
    void rejectsForeignHosts() throws Exception {
        int port = URI.create(server.baseUrl()).getPort();
        try (java.net.Socket socket = new java.net.Socket("127.0.0.1", port)) {
            socket.getOutputStream().write(("GET / HTTP/1.1\r\nHost: example.com\r\nConnection: close\r\n\r\n")
                    .getBytes(java.nio.charset.StandardCharsets.US_ASCII));
            socket.getOutputStream().flush();
            String response = new String(socket.getInputStream().readAllBytes(),
                    java.nio.charset.StandardCharsets.US_ASCII);
            assertTrue(response.startsWith("HTTP/1.1 403"), response);
        }
    }

    @Test
    void rejectsUnsupportedMethods() throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create(server.baseUrl())).POST(HttpRequest.BodyPublishers.noBody())
                .build();
        assertEquals(405, client.send(request, HttpResponse.BodyHandlers.ofString()).statusCode());
    }

    private HttpResponse<String> get(String path) throws Exception {
        return client.send(HttpRequest.newBuilder(URI.create(server.baseUrl() + path)).GET().build(),
                HttpResponse.BodyHandlers.ofString());
    }
}
