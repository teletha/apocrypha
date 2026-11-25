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

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import kiss.I;

/**
 * Serves the classpath frontend to the platform webview over a loopback HTTP connection.
 * <p>
 * A platform webview cannot intercept a custom URL scheme, so the frontend is exposed on an
 * ephemeral port on {@code 127.0.0.1} instead. The server is bound to the loopback interface and
 * requests carrying a foreign {@code Host} header are rejected to prevent DNS rebinding.
 */
final class AppServer implements AutoCloseable {

    /** A prefix registered with Application.serve and its handler. */
    private record Registration(String prefix, Route route) {
    }

    private final ResourceLoader loader;

    private final List<Registration> routes = new CopyOnWriteArrayList<>();

    private final HttpServer server;

    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

    private final String baseUrl;

    private final String host;

    AppServer(ResourceLoader loader) throws IOException {
        this.loader = loader;
        this.server = HttpServer.create(new InetSocketAddress(InetAddress.getByName("127.0.0.1"), 0), 0);
        this.server.createContext("/", this::handle);
        this.server.setExecutor(executor);
        this.host = "127.0.0.1:" + server.getAddress().getPort();
        this.baseUrl = "http://" + host + "/";
    }

    /**
     * The URL which the webview uses to load the application.
     * 
     * @return A loopback URL ending with a slash.
     */
    String baseUrl() {
        return baseUrl;
    }

    /**
     * Start serving requests.
     */
    void start() {
        server.start();
        I.debug("apocrypha", "Serving the frontend at " + baseUrl);
    }

    /**
     * Register a dynamic content route. Registered prefixes are matched against the request path
     * before the classpath resources, in registration order.
     * 
     * @param prefix A request path prefix starting with a slash.
     * @param route A handler.
     */
    void route(String prefix, Route route) {
        java.util.Objects.requireNonNull(route, "route");
        String value = prefix == null ? "" : prefix.trim();
        if (!value.startsWith("/") || value.indexOf("..") >= 0 || value.indexOf('\\') >= 0) {
            throw new IllegalArgumentException("Invalid route prefix: " + prefix);
        }
        if (value.startsWith("/" + "__apocrypha") || value.equals("/" + "__apocrypha")) {
            throw new IllegalArgumentException("The /__apocrypha namespace is reserved by the framework");
        }
        routes.add(new Registration(value.endsWith("/") ? value : value + "/", route));
    }

    /**
     * Find the registered prefix matching a request path.
     * 
     * @param path A decoded request path.
     * @return A registration, or {@code null}.
     */
    private Registration find(String path) {
        for (Registration registration : routes) {
            if (path.startsWith(registration.prefix())) {
                return registration;
            }
        }
        return null;
    }

    private void handle(HttpExchange exchange) throws IOException {
        try {
            String method = exchange.getRequestMethod();
            if (!"GET".equals(method) && !"HEAD".equals(method)) {
                exchange.getResponseHeaders().set("Allow", "GET, HEAD");
                send(exchange, 405, "text/plain; charset=utf-8", "Method not allowed".getBytes(StandardCharsets.UTF_8), Map.of());
                return;
            }

            String requestedHost = exchange.getRequestHeaders().getFirst("Host");
            if (requestedHost != null && !requestedHost.equalsIgnoreCase(host)) {
                send(exchange, 403, "text/plain; charset=utf-8", "Forbidden".getBytes(StandardCharsets.UTF_8), Map.of());
                return;
            }

            String path = exchange.getRequestURI().getPath();
            Registration registration = find(path);
            if (registration != null) {
                Response response = registration.route().handle(path.substring(registration.prefix().length() - 1), exchange.getRequestURI().getQuery());
                if (response != null) {
                    if ("HEAD".equals(method)) {
                        Headers headers = exchange.getResponseHeaders();
                        headers.set("Content-Type", response.mimeType());
                        for (Map.Entry<String, String> header : response.headers().entrySet()) {
                            headers.set(header.getKey(), header.getValue());
                        }
                        if (!response.headers().containsKey("Cache-Control")) {
                            headers.set("Cache-Control", "no-cache");
                        }
                        exchange.sendResponseHeaders(response.status(), -1);
                    } else {
                        send(exchange, response.status(), response.mimeType(), response.data(), response.headers());
                    }
                    return;
                }
            }

            Resource resource = loader.load(path);
            if ("HEAD".equals(method)) {
                Headers headers = exchange.getResponseHeaders();
                headers.set("Content-Type", resource.mimeType());
                headers.set("Cache-Control", "no-cache");
                headers.set("X-Content-Type-Options", "nosniff");
                exchange.sendResponseHeaders(resource.status(), -1);
            } else {
                send(exchange, resource.status(), resource.mimeType(), resource.data(), Map.of());
            }
        } catch (Throwable error) {
            I.warn("apocrypha", new RuntimeException("Failed to serve " + exchange.getRequestURI(), error));
            try {
                send(exchange, 500, "text/plain; charset=utf-8", "Internal server error".getBytes(StandardCharsets.UTF_8), Map.of());
            } catch (Throwable ignored) {
                // The connection may already be closed.
            }
        } finally {
            exchange.close();
        }
    }

    private static void send(HttpExchange exchange, int status, String mimeType, byte[] data, Map<String, String> extra) throws IOException {
        Headers headers = exchange.getResponseHeaders();
        headers.set("Content-Type", mimeType);
        headers.set("Cache-Control", "no-cache");
        headers.set("X-Content-Type-Options", "nosniff");
        for (Map.Entry<String, String> header : extra.entrySet()) {
            headers.set(header.getKey(), header.getValue());
        }
        exchange.sendResponseHeaders(status, data.length == 0 ? -1 : data.length);
        if (data.length > 0) {
            try (OutputStream output = exchange.getResponseBody()) {
                output.write(data);
            }
        }
    }

    @Override
    public void close() {
        server.stop(0);
        executor.shutdownNow();
    }

    @Override
    public String toString() {
        return "AppServer [url=" + baseUrl + "]";
    }
}
