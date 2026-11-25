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

/**
 * A handler for dynamically generated content.
 * <p>
 * Routes are registered with {@link Application#serve(String, Route)} and are matched before the
 * classpath resources. A handler receives the request path below the registered prefix and the raw
 * query string, and returns a {@link Response}. Returning {@code null} lets the request fall
 * through to the static frontend, which normally ends in a 404.
 *
 * <pre>{@code
 * app.serve("/photo/", (path, query) -> {
 *     long id = Long.parseLong(path.substring(1));
 *     byte[] jpeg = thumbnails.load(id);
 *     return jpeg == null ? null : Response.ok("image/jpeg", jpeg).cache("public, max-age=604800");
 * });
 * }</pre>
 *
 * Handlers run on a virtual thread per request, so blocking work such as database reads is
 * acceptable. Handlers should never trust the path; validate identifiers rather than resolving file
 * system paths supplied by the renderer.
 */
@FunctionalInterface
public interface Route {

    /**
     * Handle one request.
     * 
     * @param path The decoded request path below the registered prefix, starting with a slash.
     * @param query The raw query string, or {@code null} when the request has none.
     * @return A response, or {@code null} to fall through to the classpath resources.
     * @throws Exception When content generation fails; the server replies with a 500 response.
     */
    Response handle(String path, String query) throws Exception;
}
