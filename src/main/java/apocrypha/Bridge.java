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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import kiss.I;

/**
 * Builds and injects the JavaScript preload which exposes {@code window.apocrypha}.
 * <p>
 * The bridge is served as an external script instead of an inline script so that a strict
 * Content-Security-Policy can still allow it through {@code 'self'}.
 */
final class Bridge {

    /** The URL path which serves the bridge script. */
    static final String PATH = "/__apocrypha/bridge.js";

    private static final String MARKER = "__APOCRYPHA_META__";

    private static final String ATTRIBUTE = "data-apocrypha-bridge";

    private static final Pattern HEAD = Pattern.compile("<head\\b[^>]*>", Pattern.CASE_INSENSITIVE);

    private static final Pattern HTML = Pattern.compile("<html\\b[^>]*>", Pattern.CASE_INSENSITIVE);

    private static final Pattern BASE = Pattern.compile("<base\\b", Pattern.CASE_INSENSITIVE);

    private final String script;

    Bridge(ApplicationConfig config, String webviewVersion) {
        Meta meta = new Meta(Application.FRAMEWORK_VERSION, config.name(), config.version(),
                System.getProperty("os.name", "unknown"), System.getProperty("os.arch", "unknown"),
                System.getProperty("java.version", "unknown"), webviewVersion == null ? "unknown" : webviewVersion);

        String source = read();
        if (!source.contains(MARKER)) {
            throw new IllegalStateException("The bridge script does not contain the metadata marker");
        }
        this.script = source.replace(MARKER, I.write(meta));
    }

    /**
     * The bridge script.
     * 
     * @return JavaScript source.
     */
    String script() {
        return script;
    }

    /**
     * Inject the bridge into an HTML document.
     * 
     * @param html An HTML document.
     * @return The document with the bridge.
     */
    String inject(String html) {
        return inject(html, false);
    }

    /**
     * Inject the bridge and optionally a root base URL into an HTML document.
     * 
     * @param html An HTML document.
     * @param rootBase Whether to force a root base URL.
     * @return The document with the bridge.
     */
    String inject(String html, boolean rootBase) {
        if (html == null || html.isEmpty() || html.contains(ATTRIBUTE)) {
            return html;
        }

        StringBuilder tag = new StringBuilder();
        if (rootBase && !BASE.matcher(html).find()) {
            tag.append("<base href=\"/\">");
        }
        tag.append("<script src=\"").append(PATH).append("\" ").append(ATTRIBUTE).append("></script>");

        Matcher head = HEAD.matcher(html);
        if (head.find()) {
            return html.substring(0, head.end()) + tag + html.substring(head.end());
        }

        Matcher root = HTML.matcher(html);
        if (root.find()) {
            return html.substring(0, root.end()) + tag + html.substring(root.end());
        }

        return tag + html;
    }

    private static String read() {
        try (InputStream input = Bridge.class.getResourceAsStream("/apocrypha/bridge.js")) {
            if (input == null) {
                throw new IllegalStateException("The bridge script is missing from the classpath");
            }
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException error) {
            throw new IllegalStateException("Failed to read the bridge script", error);
        }
    }

    private record Meta(String frameworkVersion, String name, String version, String platform, String arch, String java,
            String webview) {
    }
}
