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
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;

/**
 * Resolves {@code apocrypha://} URLs to classpath resources.
 * <p>
 * The loader is immutable and therefore safe to use from the multiple IO threads which JCEF uses
 * for scheme handlers.
 */
final class ResourceLoader {

    private static final ClassLoader LOADER = Application.class.getClassLoader();

    private final ApplicationConfig config;

    private final Bridge bridge;

    private final String root;

    ResourceLoader(ApplicationConfig config, Bridge bridge) {
        this.config = config;
        this.bridge = bridge;
        this.root = config.resourceRoot();
    }

    /**
     * Whether this loader owns the URL.
     * 
     * @param url A URL.
     * @return True if the URL uses the configured scheme and host.
     */
    boolean handles(String url) {
        if (url == null) {
            return false;
        }
        try {
            URI uri = URI.create(url);
            return matches(uri);
        } catch (RuntimeException error) {
            return false;
        }
    }

    /**
     * Load the resource for an application URL.
     * 
     * @param url An application URL.
     * @return A resource. Missing resources are represented by a 404 response.
     */
    Resource load(String url) {
        try {
            URI uri = URI.create(url);
            if (!matches(uri)) {
                return notFound(url);
            }

            String decoded = uri.getPath();
            if (decoded == null || decoded.isEmpty()) {
                decoded = "/";
            }
            if (decoded.indexOf('\0') >= 0 || decoded.indexOf('\\') >= 0) {
                return notFound(url);
            }

            boolean directory = decoded.isEmpty() || decoded.endsWith("/");
            String path = normalize(decoded);
            if (path == null) {
                return notFound(url);
            }

            if (directory || path.isEmpty()) {
                path = path.isEmpty() ? "index.html" : path + "/index.html";
            }

            if (path.equals("__apocrypha/bridge.js")) {
                return new Resource(200, MimeTypes.of("bridge.js"), bridge.script().getBytes(StandardCharsets.UTF_8));
            }

            if (path.startsWith("__apocrypha/")) {
                return notFound(url);
            }

            byte[] data = read(path);
            boolean fallback = false;
            if (data == null && config.spaFallback() && !hasExtension(path)) {
                path = entryPath();
                data = read(path);
                fallback = true;
            }

            if (data == null) {
                return notFound(url);
            }

            String mime = MimeTypes.of(path);
            if (mime.startsWith("text/html")) {
                String html = new String(data, StandardCharsets.UTF_8);
                data = bridge.inject(html, fallback).getBytes(StandardCharsets.UTF_8);
            }
            return new Resource(200, mime, data);
        } catch (RuntimeException error) {
            Application.logger.log(System.Logger.Level.DEBUG, "Failed to resolve resource: " + url, error);
            return notFound(url);
        }
    }

    /**
     * Read a classpath resource.
     * 
     * @param path A normalized path relative to the resource root.
     * @return The bytes, or {@code null} if the resource does not exist.
     */
    private byte[] read(String path) {
        String name = root.isEmpty() ? path : root + "/" + path;
        try (InputStream input = LOADER.getResourceAsStream(name)) {
            return input == null ? null : input.readAllBytes();
        } catch (IOException error) {
            return null;
        }
    }

    private boolean matches(URI uri) {
        return config.scheme().equalsIgnoreCase(uri.getScheme())
                && config.host().equalsIgnoreCase(uri.getHost())
                && uri.getPort() == -1
                && uri.getUserInfo() == null;
    }

    private String entryPath() {
        String entry = config.entry();
        if (entry.contains("://")) {
            return "index.html";
        }
        String normalized = normalize(entry);
        return normalized == null || normalized.isEmpty() ? "index.html" : normalized;
    }

    private Resource notFound(String url) {
        String requested = url == null ? "" : escape(url);
        String html = """
                <!doctype html>
                <html lang="en">
                <head>
                <meta charset="utf-8">
                <meta name="viewport" content="width=device-width, initial-scale=1">
                <title>Resource not found</title>
                <style>
                :root { color-scheme: dark; }
                body { margin: 0; min-height: 100vh; display: grid; place-items: center; background: #111318; color: #e6e8ee; font: 15px/1.6 ui-sans-serif, system-ui, sans-serif; }
                main { max-width: 42rem; padding: 2rem; }
                h1 { margin: 0 0 .5rem; font-size: 1.4rem; }
                p { margin: .25rem 0; color: #9aa3b2; }
                code { color: #c7d2fe; overflow-wrap: anywhere; }
                </style>
                </head>
                <body>
                <main>
                <h1>Resource not found</h1>
                <p>The frontend resource is not available on the classpath.</p>
                <p><code>%s</code></p>
                </main>
                </body>
                </html>
                """.formatted(requested);
        return new Resource(404, "text/html; charset=utf-8", html.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Normalize a URL path and reject traversal attempts.
     * 
     * @param path A decoded URL path.
     * @return A normalized path, or {@code null} if the path is unsafe.
     */
    static String normalize(String path) {
        if (path == null) {
            return null;
        }

        StringBuilder normalized = new StringBuilder();
        for (String segment : path.split("/", -1)) {
            if (segment.isEmpty() || segment.equals(".")) {
                continue;
            }
            if (segment.equals("..")) {
                return null;
            }
            if (normalized.length() > 0) {
                normalized.append('/');
            }
            normalized.append(segment);
        }
        return normalized.toString();
    }

    private static boolean hasExtension(String path) {
        int slash = path.lastIndexOf('/');
        int dot = path.lastIndexOf('.');
        return dot > slash && dot < path.length() - 1;
    }

    private static String escape(String value) {
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }

    @Override
    public String toString() {
        return "ResourceLoader [root=" + root + ", url=" + config.baseUrl() + "]";
    }
}
