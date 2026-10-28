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

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.regex.Pattern;

import me.friwi.jcefmaven.CefAppBuilder;

/**
 * Configures the application before the Chromium runtime is initialized.
 * <p>
 * The configuration is intentionally mutable only while {@link Application#configure(ApplicationConfig)}
 * is running. After the application has started, all accessors return immutable values.
 */
public final class ApplicationConfig {

    /** The name used when the application does not provide one. */
    static final String DEFAULT_NAME = "Apocrypha";

    private static final Pattern SCHEME = Pattern.compile("[a-z][a-z0-9+.-]*");

    private static final Pattern ABSOLUTE_URL = Pattern.compile("^[a-zA-Z][a-zA-Z0-9+.-]*://.*");

    private String name;

    private String version;

    private String scheme = "apocrypha";

    private String host = "app";

    private String resourceRoot = "web";

    private String entry = "index.html";

    private Path dataDirectory;

    private Path installDirectory = Path.of("jcef-bundle");

    private boolean devTools;

    private boolean quitWhenLastWindowClosed = true;

    private boolean spaFallback = true;

    private boolean restoreWindowState = true;

    private final List<String> cefArguments = new ArrayList<>();

    private Consumer<CefAppBuilder> cefCustomizer;

    ApplicationConfig() {
    }

    /**
     * Set the application name. It is used for the title bar, the user data directory, and the
     * metadata exposed to the renderer.
     * 
     * @param name A human readable application name.
     * @return This configuration.
     */
    public ApplicationConfig name(String name) {
        this.name = requireText(name, "name");
        return this;
    }

    /**
     * Set the application version exposed to the renderer.
     * 
     * @param version An application version.
     * @return This configuration.
     */
    public ApplicationConfig version(String version) {
        this.version = Objects.requireNonNull(version, "version");
        return this;
    }

    /**
     * Set the custom URL scheme used to serve the bundled frontend. The default is
     * {@code apocrypha}.
     * 
     * @param scheme A standard URL scheme.
     * @return This configuration.
     */
    public ApplicationConfig scheme(String scheme) {
        String value = requireText(scheme, "scheme").toLowerCase(Locale.ROOT);
        if (!SCHEME.matcher(value).matches()) {
            throw new IllegalArgumentException("Invalid URL scheme: " + scheme);
        }
        this.scheme = value;
        return this;
    }

    /**
     * Set the host component of the application URL. The default is {@code app}.
     * 
     * @param host A host name.
     * @return This configuration.
     */
    public ApplicationConfig host(String host) {
        String value = requireText(host, "host");
        if (value.contains("/") || value.contains(":")) {
            throw new IllegalArgumentException("Invalid URL host: " + host);
        }
        this.host = value;
        return this;
    }

    /**
     * Set the classpath directory which contains the frontend build. The default is {@code web}.
     * <p>
     * For example, if the frontend build is placed in {@code src/main/resources/web}, the resource
     * root is {@code web}.
     * 
     * @param root A classpath directory.
     * @return This configuration.
     */
    public ApplicationConfig resourceRoot(String root) {
        String value = root == null ? "" : root.replace('\\', '/');
        while (value.startsWith("/")) {
            value = value.substring(1);
        }
        while (value.endsWith("/")) {
            value = value.substring(0, value.length() - 1);
        }
        if (value.contains("..")) {
            throw new IllegalArgumentException("Invalid resource root: " + root);
        }
        this.resourceRoot = value;
        return this;
    }

    /**
     * Set the entry point of the frontend. A relative path is resolved against the application
     * URL. An absolute URL (such as {@code http://localhost:5173}) is used as is.
     * 
     * @param entry A relative path or an absolute URL.
     * @return This configuration.
     */
    public ApplicationConfig entry(String entry) {
        this.entry = requireText(entry, "entry");
        return this;
    }

    /**
     * Set the directory where Chromium stores its user data. The default is
     * {@code ~/.apocrypha/<application-name>}.
     * 
     * @param directory A writable directory.
     * @return This configuration.
     */
    public ApplicationConfig dataDirectory(Path directory) {
        this.dataDirectory = Objects.requireNonNull(directory, "directory").toAbsolutePath().normalize();
        return this;
    }

    /**
     * Set the directory where the JCEF native bundle is installed. The default is
     * {@code jcef-bundle} in the working directory.
     * 
     * @param directory A directory for the native JCEF bundle.
     * @return This configuration.
     */
    public ApplicationConfig installDirectory(Path directory) {
        this.installDirectory = Objects.requireNonNull(directory, "directory");
        return this;
    }

    /**
     * Open the Chromium developer tools automatically after the first page has loaded.
     * 
     * @param enable Whether to open the developer tools.
     * @return This configuration.
     */
    public ApplicationConfig devTools(boolean enable) {
        this.devTools = enable;
        return this;
    }

    /**
     * Configure whether closing the last window terminates the application. The default is
     * {@code true}.
     * 
     * @param enable Whether to quit when the last window is closed.
     * @return This configuration.
     */
    public ApplicationConfig quitWhenLastWindowClosed(boolean enable) {
        this.quitWhenLastWindowClosed = enable;
        return this;
    }

    /**
     * Configure whether a missing frontend path without a file extension falls back to the entry
     * document. This is useful for single page applications which use client-side routing.
     * 
     * @param enable Whether to enable the fallback.
     * @return This configuration.
     */
    public ApplicationConfig spaFallback(boolean enable) {
        this.spaFallback = enable;
        return this;
    }

    /**
     * Configure whether window position, size, and maximized state are persisted in the application
     * data directory and restored when a window with the same id is shown again. The default is
     * {@code true}.
     * <p>
     * The state is stored in {@code window-state.json}. Use {@link Window#restoreState(boolean)} to
     * opt out for an individual window.
     * 
     * @param enable Whether to restore window state.
     * @return This configuration.
     */
    public ApplicationConfig restoreWindowState(boolean enable) {
        this.restoreWindowState = enable;
        return this;
    }

    /**
     * Add command line switches for Chromium.
     * 
     * @param arguments Chromium switches, such as {@code --disable-gpu}.
     * @return This configuration.
     */
    public ApplicationConfig cefArguments(String... arguments) {
        Objects.requireNonNull(arguments, "arguments");
        for (String argument : arguments) {
            if (argument != null && !argument.isBlank()) {
                cefArguments.add(argument);
            }
        }
        return this;
    }

    /**
     * Customize the low-level JCEF builder. This is the escape hatch for advanced settings which
     * are not exposed by this configuration.
     * 
     * @param customizer A builder customizer.
     * @return This configuration.
     */
    public ApplicationConfig configureCef(Consumer<CefAppBuilder> customizer) {
        this.cefCustomizer = Objects.requireNonNull(customizer, "customizer");
        return this;
    }

    /**
     * The human readable application name.
     * 
     * @return The application name.
     */
    public String name() {
        return name == null ? DEFAULT_NAME : name;
    }

    /**
     * The application version exposed to the renderer.
     * 
     * @return The application version.
     */
    public String version() {
        return version == null ? "dev" : version;
    }

    /**
     * The custom URL scheme.
     * 
     * @return The scheme.
     */
    public String scheme() {
        return scheme;
    }

    /**
     * The host component of the application URL.
     * 
     * @return The host.
     */
    public String host() {
        return host;
    }

    /**
     * The classpath directory containing the frontend build.
     * 
     * @return The resource root.
     */
    public String resourceRoot() {
        return resourceRoot;
    }

    /**
     * The configured frontend entry point.
     * 
     * @return The entry point.
     */
    public String entry() {
        return entry;
    }

    /**
     * The Chromium user data directory.
     * 
     * @return The user data directory.
     */
    public Path dataDirectory() {
        if (dataDirectory != null) {
            return dataDirectory;
        }
        return Path.of(System.getProperty("user.home"), ".apocrypha", slug(name())).toAbsolutePath();
    }

    /**
     * The JCEF native bundle installation directory.
     * 
     * @return The installation directory.
     */
    public Path installDirectory() {
        return installDirectory;
    }

    /**
     * Whether the developer tools should open automatically.
     * 
     * @return True to open developer tools.
     */
    public boolean devTools() {
        return devTools;
    }

    /**
     * Whether the application quits when the last window is closed.
     * 
     * @return True to quit.
     */
    public boolean quitWhenLastWindowClosed() {
        return quitWhenLastWindowClosed;
    }

    /**
     * Whether missing extension-less paths fall back to the entry document.
     * 
     * @return True to enable the fallback.
     */
    public boolean spaFallback() {
        return spaFallback;
    }

    /**
     * Whether window position, size, and maximized state are restored.
     * 
     * @return True to restore window state.
     */
    public boolean restoreWindowState() {
        return restoreWindowState;
    }

    /**
     * Additional Chromium switches.
     * 
     * @return An immutable list of switches.
     */
    public List<String> cefArguments() {
        return List.copyOf(cefArguments);
    }

    /**
     * The low-level JCEF builder customizer, if any.
     * 
     * @return A customizer or {@code null}.
     */
    public Consumer<CefAppBuilder> cefCustomizer() {
        return cefCustomizer;
    }

    /**
     * The application base URL.
     * 
     * @return The base URL.
     */
    public String baseUrl() {
        return scheme + "://" + host + "/";
    }

    /**
     * The resolved frontend entry URL.
     * 
     * @return The entry URL.
     */
    public String entryUrl() {
        return url(entry);
    }

    /**
     * Resolve a path against the application URL.
     * 
     * @param path A relative path or an absolute URL.
     * @return The resolved URL.
     */
    public String url(String path) {
        String value = requireText(path, "path");
        if (ABSOLUTE_URL.matcher(value).matches()) {
            return value;
        }
        return baseUrl() + (value.startsWith("/") ? value.substring(1) : value);
    }

    /**
     * Normalize the name into a portable directory name.
     * 
     * @param name A name.
     * @return A slug.
     */
    static String slug(String name) {
        String slug = name.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("^[-]+|[-]+$", "");
        return slug.isEmpty() ? "application" : slug;
    }

    private static String requireText(String value, String name) {
        Objects.requireNonNull(value, name);
        if (value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }

    @Override
    public String toString() {
        return "ApplicationConfig [name=" + name() + ", version=" + version() + ", url=" + baseUrl() + "]";
    }
}
