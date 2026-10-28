/*
 * Copyright (C) 2024 The APOCRYPHA Development Team
 *
 * Licensed under the MIT License (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *          https://opensource.org/licenses/MIT
 */
package apocrypha;

import static bee.api.License.*;

import javax.lang.model.SourceVersion;

public class Project extends bee.api.Project {

    {
        product("io.github.teletha", "apocrypha", ref("version.txt"));
        license(MIT);
        require(SourceVersion.latest(), SourceVersion.RELEASE_25);

        describe("""
                Apocrypha is an Electron-style desktop application framework for Java. The backend runs on the JVM and the user interface is built with standard web technologies (HTML, CSS, and JavaScript) rendered by the platform webview through [avaje-webview](https://github.com/avaje/avaje-webview).

                * Java application lifecycle, native webview windows, and configuration
                * Native webview window with OS decorations through avaje-webview
                * A loopback HTTP server which serves a frontend build directly from the classpath
                * A preload bridge which exposes `window.apocrypha` without requiring a bundler plugin
                * Asynchronous request/response IPC and bidirectional events
                * Typed errors which arrive in JavaScript as rejected promises
                * Java model classes (records, beans with getter/setter pairs, and public fields) over IPC
                * Annotation-based channel registration with `@IpcChannel`
                * Persistent window position, size, and maximized state with per-window identifiers
                * Application restart from Java or JavaScript, preserving window state
                * Works with any browser toolchain that can emit static files (Vite, React, Vue, Svelte, and so on)

                JSON is handled by [Sinobu](https://github.com/teletha/sinobu) (`I.write`, `I.json`, and `kiss.JSON`); Gson is not used by the framework.

                ## Architecture

                ```
                +--------------------- Java backend ---------------------+
                |  Application -> Window -> Webview (avaje-webview)       |
                |       |              |                                 |
                |       +---- Ipc <----+---- bound JS functions          |
                +----------------------|---------------------------------+
                                       |  JSON request / response
                                       |  events
                +----------------------|---------------------------------+
                |  window.apocrypha <--+  native webview                 |
                |  HTML / CSS / JavaScript                               |
                +--------------------------------------------------------+
                ```

                The frontend is served from an ephemeral HTTP server bound to the loopback interface. Requests are resolved against a classpath directory. HTML responses receive an external bridge script, and single-page fallback responses also receive a root base URL, so a strict CSP can allow the bridge with `'self'`.

                ## Quick Start

                Create an application class:

                ```java
                package example;

                import apocrypha.Application;
                import apocrypha.ApplicationConfig;
                import apocrypha.Window;

                public class Notes extends Application {

                    public static void main(String[] args) {
                        Application.launch(Notes.class, args);
                    }

                    @Override
                    protected void configure(ApplicationConfig config) {
                        config.name("Notes")
                              .version("1.0.0")
                              .resourceRoot("web")
                              .entry("index.html");
                    }

                    @Override
                    protected void onStart() {
                        ipc().handle("notes:save", request -> {
                            java.nio.file.Files.writeString(
                                dataDirectory().resolve("note.txt"), request.string(0));
                            return true;
                        });

                        Window window = createWindow();
                        window.title("Notes").size(1100, 760).show();
                    }
                }
                ```

                Place the frontend build in `src/main/resources/web`:

                ```html
                <!doctype html>
                <html>
                <head>
                    <meta charset="utf-8">
                    <title>Notes</title>
                    <link rel="stylesheet" href="style.css">
                </head>
                <body>
                    <textarea id="note"></textarea>
                    <button id="save">Save</button>
                    <script src="app.js"></script>
                </body>
                </html>
                ```

                Call the backend from JavaScript:

                ```javascript
                const note = document.querySelector('#note');

                document.querySelector('#save').addEventListener('click', async () => {
                    try {
                        await apocrypha.invoke('notes:save', note.value);
                        console.log('saved');
                    } catch (error) {
                        console.error(error.code, error.message);
                    }
                });

                apocrypha.on('notes:loaded', (payload) => {
                    note.value = payload.text;
                });
                ```

                Send an event from Java to the renderer:

                ```java
                createWindow().emit("notes:loaded", java.util.Map.of("text", "Hello"));
                ```

                ## Java Model IPC

                IPC values are encoded with Sinobu. A record, a bean with getter/setter pairs, or a class with public fields can be used directly as an argument or return value. The renderer sends ordinary JSON; `I.json` restores the model and `I.write` serializes it.

                ```java
                public record Note(String title, String body) {
                }

                public class NoteHandlers {

                    @IpcChannel("notes:save")
                    public Note save(Note note) {
                        // save the model with your own storage layer
                        return note;
                    }
                }

                // onStart
                ipc().register(new NoteHandlers());
                ```

                ```javascript
                const saved = await apocrypha.invoke('notes:save', {
                    title: 'Shopping',
                    body: 'Milk'
                });
                ```

                Model parameters are decoded with `I.json`, and model results are encoded with `I.write`. Primitives, strings, maps, lists, and model classes are supported as well. Use a model class for generic structures such as `List<Note>`; declare a `JSON` parameter when raw JSON is required.

                ## Annotated Channels

                `@IpcChannel` registers a method automatically. The annotation value is the channel name; when it is empty, the method name is used.

                ```java
                public class NotesHandlers {

                    @IpcChannel
                    public long count() {
                        return 42;
                    }

                    @IpcChannel("notes:save")
                    public Note save(Note note, IpcRequest request) {
                        // request.window() is the source window
                        return note;
                    }

                    @IpcChannel("notes:reset")
                    public static void reset() {
                        // static methods are supported
                    }
                }

                ipc().register(new NotesHandlers()); // instance registration
                ipc().register(NotesHandlers.class); // class registration (requires a no-arg constructor)
                ```

                * A method parameter of type `IpcRequest` receives the request; a parameter of type `Window` receives the source window.
                * All other parameters consume positional arguments in order and are decoded with `I.json`.
                * Static methods are invoked without an instance. `register(Class)` instantiates the class only when it contains instance methods.
                * `IpcHandler` lambdas remain available through `ipc().handle(...)`.
                * The renderer event callback receives a `JSON` value; use `as(Model.class)` or `json.text(...)` to read it.

                ## JavaScript API

                The bridge is injected automatically into HTML served by the application server.

                | API | Description |
                | --- | --- |
                | `apocrypha.invoke(channel, ...args)` | Invoke a Java channel and await the JSON result. |
                | `apocrypha.on(event, listener)` | Listen for an event from Java. Returns an unsubscribe function. |
                | `apocrypha.once(event, listener)` | Listen for one event. |
                | `apocrypha.off(event, listener)` | Remove a listener. |
                | `apocrypha.emit(event, payload)` | Emit an event to Java. |
                | `apocrypha.ready` | A promise which resolves as soon as the bridge is available. |
                | `apocrypha.app` | Application name, version, framework version, platform, architecture, Java and webview versions. |
                | `apocrypha.app.quit()` | Quit the application. |
                | `apocrypha.app.restart()` | Restart the application in a new JVM process. |
                | `apocrypha.window.setTitle(title)` | Change the native window title. |
                | `apocrypha.window.show()` / `hide()` | Show or hide the native window. |
                | `apocrypha.window.setSize(width, height)` | Resize the native window. |
                | `apocrypha.window.getSize()` | Read the native window size. |
                | `apocrypha.window.getBounds()` | Read x, y, width, height, and the maximized flag. |
                | `apocrypha.window.setBounds(x, y, width, height)` | Set the position and size. |
                | `apocrypha.window.setPosition(x, y)` | Move the window without resizing it. |
                | `apocrypha.window.minimize()` | Minimize the window. |
                | `apocrypha.window.maximize()` | Maximize the window. |
                | `apocrypha.window.unmaximize()` | Restore the window. |
                | `apocrypha.window.isMaximized()` | Test whether the window is maximized. |
                | `apocrypha.window.center()` | Center the window. |
                | `apocrypha.window.setAlwaysOnTop(value)` | Toggle always-on-top. |
                | `apocrypha.window.setResizable(value)` | Toggle resizing. |
                | `apocrypha.window.reload()` | Reload the current document. |
                | `apocrypha.window.load(url)` | Navigate the window. |
                | `apocrypha.window.openDevTools()` | Log how to open the webview developer tools. |
                | `apocrypha.window.close()` | Close the window. |

                ## Frontend Tooling

                Apocrypha does not impose a frontend framework. The only requirement is that the build output is available in the configured classpath resource root.

                With Vite, for example, produce the production build under `src/main/resources/web`:

                ```javascript
                // vite.config.js
                export default {
                    build: {
                        outDir: 'src/main/resources/web',
                        emptyOutDir: true
                    }
                };
                ```

                During development, write the watch build directly to the compiled resource directory and reload the window:

                ```bash
                vite build --watch --outDir target/classes/web
                ```

                Alternatively, point `entry` at the Vite development server (`http://localhost:5173/`). The bridge is injected as a document-start user script, so IPC is available during initial page evaluation.

                Because the server reads resources from the classpath, the webview's regular developer tools, network panel, console, and source maps work as usual.

                ## Configuration

                `ApplicationConfig` provides:

                * `name`, `version` - application metadata
                * `resourceRoot` - classpath directory containing the frontend (default `web`)
                * `entry` - entry document or an absolute URL for a development server
                * `dataDirectory` - framework state directory (default `~/.apocrypha/<application-name>`)
                * `devTools` - enable the webview developer tools (default `false`)
                * `spaFallback` - serve the entry document for extension-less routes
                * `theme` - FlatLaf theme for Swing dialogs and components (default `FlatDarkLaf`)
                * `restoreWindowState` - persist and restore window bounds and maximized state (default `true`)

                For development, `entry("http://localhost:5173/")` loads an external page. The bridge is injected as a document-start user script, so it is available to the page immediately.

                ## Migrating from JCEF

                The browser backend is avaje-webview now, so a few JCEF-specific APIs are gone:

                * The `webview_java` backend and its JNA dependencies were replaced by avaje-webview, which uses Java 25 FFM and the system WebView2 runtime.
                * The `apocrypha://` scheme and the `scheme` / `host` configuration were removed. The frontend is served from an ephemeral `http://127.0.0.1:<port>/` origin.
                * `installDirectory`, `cefArguments`, and `configureCef` were removed. The platform webview uses the system runtime (WebView2 on Windows).
                * `Window.browser()` returns a `io.avaje.webview.Webview` and is `null` until the window is shown.
                * `Window.frame()` was removed because the main window is no longer a `JFrame`. Use `Window.browser()` for the native webview.
                * `Window.openDevTools()` no longer opens the tools programmatically. Enable them with `devTools(true)` (or `--devtools`) and use `F12` or the context menu.

                ## Window State

                Give each window a stable id before showing it:

                ```java
                Window main = createWindow();
                main.id("main")
                    .title("Notes")
                    .size(1100, 760)
                    .show();
                ```

                The position, size, and maximized state are written to `window-state.json` in the application data directory. The native window bounds are sampled every 300 ms and written after 500 ms without changes, and again when the window closes or the application quits.

                * The default ids are `main`, `window-2`, `window-3`, and so on. Set an explicit id when windows are created in a data-dependent order.
                * The values passed to `size()` / `bounds()` are used on the first run. On later runs the persisted state takes precedence.
                * Use `window.restoreState(false)` to keep programmatic bounds for one window, or `config.restoreWindowState(false)` for the whole application.
                * Saved bounds are validated against the current monitors. A window which would be completely off-screen falls back to the default centered position.
                * When a maximized window is closed, its previous normal bounds are saved and restored by `unmaximize()`.

                ## Restarting

                Restart from Java:

                ```java
                restart();
                ```

                Or from the renderer:

                ```javascript
                await apocrypha.app.restart();
                ```

                The framework starts a new JVM with the same Java executable, JVM options, classpath or jar, working directory, and application arguments. The new process waits for the old one to exit before creating the webview, so the previous native window is released. `onExit()` runs in the old process, `onStart()` runs in the new process, and the saved window state is restored.

                Restart requires the application to be started through `Application.launch(...)`. The launcher class is discovered from `sun.java.command`; if that property is unavailable, the application class must declare a `main` method. Debug-agent options (`-agentlib:jdwp` / `-Xrunjdwp`) are omitted from the new process so a debug port does not conflict.

                ## Security Model

                * The renderer has no direct access to Java objects. It can only call registered IPC channels with JSON values.
                * IPC queries are accepted from the main frame only.
                * The frontend is served from an ephemeral HTTP server bound to `127.0.0.1`; requests with a foreign `Host` header are rejected.
                * Classpath resolution rejects absolute paths, traversal segments, NUL bytes, and backslashes.
                * External links opened with `target="_blank"` are handed to the operating system browser instead of creating an unrestricted popup.

                ## Notes

                * Java 25 or later is required.
                * Add `--enable-native-access=ALL-UNNAMED` to the JVM options to avoid restricted native access warnings from the native webview (FFM).
                * avaje-webview bundles the native webview libraries for the supported platforms.
                * The build currently includes only the avaje-webview Windows x86_64 native artifact.
                * Windows requires the Microsoft Edge WebView2 runtime (preinstalled on Windows 10/11). Linux requires WebKitGTK.
                * The main window is a native avaje-webview window. On macOS, launch with `-XstartOnFirstThread`.
                * The Windows-specific window helpers provide position, bounds, always-on-top, resizability, and hide/show. Other platforms use avaje-webview's window API and may not support every operation.
                * Each window owns its own native webview instance.
                * The `apocrypha:` IPC namespace is reserved by the framework.
                * External `target=_blank` links are opened in the system browser.
                * Window state is stored under `<dataDirectory>/window-state.json`.
                * The repository contains a demo application at `apocrypha.Main`. Pass `--devtools` to enable the webview developer tools on startup.
                """);

        require("io.github.teletha", "sinobu");
        require("io.github.teletha", "stylist");
        require("io.github.teletha", "psychopath");
        require("io.github.teletha", "conjure");
        // require("io.github.teletha", "reincarnation");
        require("io.github.teletha", "antibug").atTest();

        require("io.avaje.webview", "avaje-webview", "0.19");
        require("io.avaje.webview", "avaje-webview-windows-x86_64", "0.19");
        require("com.formdev", "flatlaf");

        versionControlSystem("https://github.com/teletha/apocrypha");
    }
}
