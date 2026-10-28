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
import java.nio.charset.StandardCharsets;

import kiss.I;

/**
 * Resolves request paths to classpath resources.
 * <p>
 * The loader is immutable and therefore safe to use from the multiple server threads which handle
 * requests.
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
     * Load the resource for a request path.
     * 
     * @param rawPath A decoded URL path, such as {@code /index.html}.
     * @return A resource. Missing resources are represented by a 404 response.
     */
    Resource load(String rawPath) {
        try {
            String path = rawPath == null || rawPath.isEmpty() ? "/" : rawPath;
            if (path.indexOf('\0') >= 0 || path.indexOf('\\') >= 0) {
                return notFound(path);
            }

            boolean directory = path.endsWith("/");
            String normalized = normalize(path);
            if (normalized == null) {
                return notFound(path);
            }

            if (directory || normalized.isEmpty()) {
                normalized = normalized.isEmpty() ? "index.html" : normalized + "/index.html";
            }

            if (normalized.equals("__apocrypha/bridge.js")) {
                return new Resource(200, MimeTypes.of("bridge.js"), bridge.script().getBytes(StandardCharsets.UTF_8));
            }

            if (normalized.startsWith("__apocrypha/")) {
                return notFound(path);
            }

            byte[] data = read(normalized);
            boolean fallback = false;
            if (data == null && config.spaFallback() && !hasExtension(normalized)) {
                normalized = entryPath();
                data = read(normalized);
                fallback = true;
            }

            if (data == null) {
                return notFound(path);
            }

            String mime = MimeTypes.of(normalized);
            if (mime.startsWith("text/html")) {
                String html = new String(data, StandardCharsets.UTF_8);
                data = bridge.inject(html, fallback).getBytes(StandardCharsets.UTF_8);
            }
            return new Resource(200, mime, data);
        } catch (RuntimeException error) {
            I.debug("apocrypha", new RuntimeException("Failed to resolve resource: " + rawPath, error));
            return notFound(rawPath);
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

    private String entryPath() {
        String entry = config.entry();
        if (entry.contains("://")) {
            return "index.html";
        }
        String normalized = normalize(entry);
        return normalized == null || normalized.isEmpty() ? "index.html" : normalized;
    }

    private Resource notFound(String path) {
        String requested = path == null ? "" : escape(path);
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
        return "ResourceLoader [root=" + root + "]";
    }
}
