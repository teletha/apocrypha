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

import java.awt.Dimension;
import java.awt.GraphicsDevice;
import java.awt.GraphicsEnvironment;
import java.awt.Rectangle;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import apocrypha.webview.Win32;

import io.avaje.webview.Webview;

import kiss.I;

/**
 * A native application window which hosts an avaje-webview browser.
 * <p>
 * avaje-webview creates and owns the top-level native window. The framework manages its bounds,
 * visibility, and state, and runs the webview event loop on a dedicated thread. Create windows from
 * {@link Application#onStart()} with {@link Application#createWindow()}. All methods may be called
 * from any thread.
 */
public final class Window {

    private static final boolean WINDOWS = System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");

    private static final int DEFAULT_WIDTH = 1100;

    private static final int DEFAULT_HEIGHT = 760;

    private static final int MIN_WIDTH = 320;

    private static final int MIN_HEIGHT = 240;

    private static final long STATE_POLL_MILLIS = 300;

    private static final long STATE_SAVE_DELAY_NANOS = 500_000_000L;

    private final Application application;

    private final Queue<Event> pending = new ConcurrentLinkedQueue<>();

    private final Queue<String> pendingScripts = new ConcurrentLinkedQueue<>();

    private final List<Runnable> closeListeners = new CopyOnWriteArrayList<>();

    private final Rectangle normalBounds = new Rectangle(0, 0, DEFAULT_WIDTH, DEFAULT_HEIGHT);

    private final CountDownLatch stopped = new CountDownLatch(1);

    private final AtomicBoolean cleaned = new AtomicBoolean();

    private volatile Webview webview;

    private volatile Thread thread;

    private volatile String currentUrl;

    private volatile String id;

    private volatile String title;

    private volatile boolean titleExplicit;

    private volatile boolean restoreStateEnabled;


    private volatile boolean maximized;

    private volatile boolean resizable = true;

    private volatile boolean alwaysOnTop;

    private volatile boolean loaded;

    private volatile boolean closed;

    private volatile boolean started;

    private volatile boolean devToolsHinted;

    private volatile boolean stateWatcherStarted;

    Window(Application application, String url, String id) {
        this.application = application;
        this.id = Objects.requireNonNull(id, "id");
        this.currentUrl = url;
        this.title = application.config().name();
        this.restoreStateEnabled = application.config().restoreWindowState();

        Rectangle screen = screenBounds();
        this.normalBounds.setBounds(screen.x + (screen.width - DEFAULT_WIDTH) / 2,
                screen.y + (screen.height - DEFAULT_HEIGHT) / 2, DEFAULT_WIDTH, DEFAULT_HEIGHT);
    }

    /**
     * Set an explicit window title. An explicit title is not overwritten by the document title.
     * 
     * @param title A window title.
     * @return This window.
     */
    public Window title(String title) {
        Objects.requireNonNull(title, "title");
        this.title = title;
        this.titleExplicit = true;
        Webview browser = webview;
        if (browser != null) {
            browser.setTitle(title);
        }
        return this;
    }

    /**
     * Apply a title reported by the renderer. This is ignored after {@link #title(String)} has set
     * an explicit title.
     * 
     * @param title A document title.
     */
    void documentTitle(String title) {
        if (!titleExplicit && title != null && !title.isBlank()) {
            this.title = title;
            Webview browser = webview;
            if (browser != null) {
                browser.setTitle(title);
            }
        }
    }

    /**
     * Set a stable identifier for this window. The identifier keys the persisted position, size, and
     * maximized state. Set it before {@link #show()}.
     * <p>
     * The default identifiers are {@code main}, {@code window-2}, {@code window-3}, and so on. Use
     * an explicit identifier when the window is not the first window or when windows are created in
     * a data-dependent order.
     * 
     * @param id A non-blank identifier.
     * @return This window.
     */
    public Window id(String id) {
        Objects.requireNonNull(id, "id");
        if (id.isBlank()) {
            throw new IllegalArgumentException("Window id must not be blank");
        }
        this.id = id;
        return this;
    }

    /**
     * The stable identifier used for persisted window state.
     * 
     * @return An identifier.
     */
    public String id() {
        return id;
    }

    /**
     * Configure whether this window restores its persisted position, size, and maximized state.
     * This can opt out one window when {@link ApplicationConfig#restoreWindowState(boolean)} is
     * enabled for the application. Call it before {@link #show()}.
     * 
     * @param enable Whether to restore state.
     * @return This window.
     */
    public Window restoreState(boolean enable) {
        this.restoreStateEnabled = enable;
        return this;
    }

    /**
     * Set the window size.
     * 
     * @param width A positive width.
     * @param height A positive height.
     * @return This window.
     */
    public Window size(int width, int height) {
        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException("Window size must be positive");
        }
        synchronized (this) {
            normalBounds.width = Math.max(width, MIN_WIDTH);
            normalBounds.height = Math.max(height, MIN_HEIGHT);
            long window = handle();
            if (WINDOWS && window != 0) {
                Win32.setBounds(window, normalBounds.x, normalBounds.y, normalBounds.width, normalBounds.height);
            } else if (webview != null) {
                webview.setSize(normalBounds.width, normalBounds.height);
            }
        }
        return this;
    }

    /**
     * Get the current window size.
     * 
     * @return A size.
     */
    public Dimension size() {
        Rectangle bounds = bounds();
        return new Dimension(bounds.width, bounds.height);
    }

    /**
     * Set the window bounds. The position and size are persisted for this window id.
     * 
     * @param x The horizontal position.
     * @param y The vertical position.
     * @param width A positive width.
     * @param height A positive height.
     * @return This window.
     */
    public Window bounds(int x, int y, int width, int height) {
        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException("Window size must be positive");
        }
        synchronized (this) {
            normalBounds.setBounds(x, y, Math.max(width, MIN_WIDTH), Math.max(height, MIN_HEIGHT));
            long window = handle();
            if (WINDOWS && window != 0) {
                Win32.setBounds(window, normalBounds.x, normalBounds.y, normalBounds.width, normalBounds.height);
            }
        }
        return this;
    }

    /**
     * Get the current window bounds.
     * 
     * @return A rectangle.
     */
    public Rectangle bounds() {
        long window = handle();
        if (WINDOWS && window != 0) {
            int[] rect = Win32.windowRect(window);
            if (rect[2] > 0 && rect[3] > 0) {
                return new Rectangle(rect[0], rect[1], rect[2], rect[3]);
            }
        }
        synchronized (this) {
            return new Rectangle(normalBounds);
        }
    }

    /**
     * Move the window without changing its size.
     * 
     * @param x The horizontal position.
     * @param y The vertical position.
     * @return This window.
     */
    public Window position(int x, int y) {
        synchronized (this) {
            normalBounds.x = x;
            normalBounds.y = y;
            long window = handle();
            if (WINDOWS && window != 0) {
                Win32.setBounds(window, x, y, normalBounds.width, normalBounds.height);
            }
        }
        return this;
    }

    /**
     * Center the window on the primary screen.
     * 
     * @return This window.
     */
    public Window center() {
        Rectangle screen = screenBounds();
        synchronized (this) {
            int width = Math.max(normalBounds.width, MIN_WIDTH);
            int height = Math.max(normalBounds.height, MIN_HEIGHT);
            normalBounds.setBounds(screen.x + (screen.width - width) / 2,
                    screen.y + (screen.height - height) / 2, width, height);
            long window = handle();
            if (WINDOWS && window != 0) {
                Win32.setBounds(window, normalBounds.x, normalBounds.y, normalBounds.width, normalBounds.height);
            }
        }
        return this;
    }

    /**
     * Show the window.
     * 
     * @return This window.
     */
    public Window show() {
        if (closed) {
            return this;
        }

        synchronized (this) {
            if (started) {
                if (WINDOWS) {
                    long window = handle();
                    if (window != 0) {
                        Win32.show(window);
                    }
                }
                return this;
            }
            started = true;
            thread = new Thread(this::run, "apocrypha-window-" + id);
            thread.setDaemon(true);
        }

        thread.start();
        return this;
    }

    /**
     * Hide the window.
     * 
     * @return This window.
     */
    public Window hide() {
        if (WINDOWS) {
            long window = handle();
            if (window != 0) {
                Win32.hide(window);
            }
        }
        return this;
    }

    /**
     * Minimize the window.
     * 
     * @return This window.
     */
    public Window minimize() {
        if (WINDOWS) {
            long window = handle();
            if (window != 0) {
                Win32.minimize(window);
            }
        }
        return this;
    }

    /**
     * Maximize the window.
     * 
     * @return This window.
     */
    public Window maximize() {
        synchronized (this) {
            if (!maximized) {
                captureBounds();
            }
            maximized = true;
        }
        long window = handle();
        if (WINDOWS && window != 0) {
            Win32.maximize(window);
        } else {
            Webview browser = webview;
            if (browser != null) {
                browser.maximizeWindow();
            }
        }
        return this;
    }

    /**
     * Restore the window from a maximized state.
     * 
     * @return This window.
     */
    public Window unmaximize() {
        maximized = false;
        if (WINDOWS) {
            long window = handle();
            if (window != 0) {
                Win32.restore(window);
            }
        }
        return this;
    }

    /**
     * Whether the window is maximized.
     * 
     * @return True if maximized.
     */
    public boolean isMaximized() {
        if (WINDOWS) {
            long window = handle();
            if (window != 0) {
                return Win32.isZoomed(window);
            }
        }
        return maximized;
    }

    /**
     * Configure whether the user can resize the window.
     * 
     * @param resizable Whether the window is resizable.
     * @return This window.
     */
    public Window setResizable(boolean resizable) {
        this.resizable = resizable;
        if (WINDOWS) {
            long window = handle();
            if (window != 0) {
                Win32.setResizable(window, resizable);
            }
        }
        return this;
    }

    /**
     * Configure whether the window is always on top.
     * 
     * @param alwaysOnTop Whether the window is always on top.
     * @return This window.
     */
    public Window setAlwaysOnTop(boolean alwaysOnTop) {
        this.alwaysOnTop = alwaysOnTop;
        if (WINDOWS) {
            long window = handle();
            if (window != 0) {
                Win32.setAlwaysOnTop(window, alwaysOnTop);
            }
        }
        return this;
    }

    /**
     * Navigate to another URL. A relative path is resolved against the application URL.
     * 
     * @param url A relative path or an absolute URL.
     * @return This window.
     */
    public Window load(String url) {
        String target = resolve(url);
        currentUrl = target;
        loaded = false;
        navigate(target);
        return this;
    }

    /**
     * Reload the current document.
     * 
     * @return This window.
     */
    public Window reload() {
        loaded = false;
        navigate(currentUrl);
        return this;
    }

    /**
     * Open the webview developer tools.
     * <p>
     * The platform webview does not expose a programmatic developer-tools API. Enable them with
     * {@link ApplicationConfig#devTools(boolean)} and use {@code F12} or the context menu.
     * 
     * @return This window.
     */
    public Window openDevTools() {
        if (!devToolsHinted) {
            devToolsHinted = true;
            if (application.config().devTools()) {
                I.info("apocrypha",
                        "Developer tools are enabled; press F12 or use the webview context menu.");
            } else {
                I.warn("apocrypha",
                        "Developer tools are disabled; enable them with ApplicationConfig.devTools(true).");
            }
        }
        return this;
    }

    /**
     * Execute JavaScript in the current document. Scripts which are executed before the document has
     * loaded are queued and run afterwards.
     * 
     * @param script A JavaScript expression.
     * @return This window.
     */
    public Window execute(String script) {
        Objects.requireNonNull(script, "script");
        if (closed) {
            return this;
        }

        Webview browser = webview;
        if (browser == null || !loaded) {
            pendingScripts.add(script);
            return this;
        }

        browser.eval(script);
        return this;
    }

    /**
     * Send an event to the renderer. Events which are emitted before the page has loaded are
     * buffered and delivered when loading completes.
     * 
     * @param event An event name.
     * @param payload A JSON serializable payload.
     * @return This window.
     */
    public Window emit(String event, Object payload) {
        Objects.requireNonNull(event, "event");
        if (closed) {
            return this;
        }

        String json = IpcCodec.write(payload);
        Event message = new Event(event, json);
        if (loaded) {
            dispatch(message);
        } else {
            pending.add(message);
            if (loaded) {
                drainPending();
            }
        }
        return this;
    }

    /**
     * Register a callback which runs when the window is closed.
     * 
     * @param listener A close listener.
     * @return This window.
     */
    public Window onClose(Runnable listener) {
        Objects.requireNonNull(listener, "listener");
        closeListeners.add(listener);
        return this;
    }

    /**
     * Close the window.
     */
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        captureBounds();

        Webview browser = webview;
        if (browser != null) {
            try {
                browser.close();
            } catch (Throwable error) {
                I.debug("apocrypha", new RuntimeException("Failed to close the webview", error));
            }
            if (Thread.currentThread() != thread) {
                try {
                    stopped.await(2, TimeUnit.SECONDS);
                } catch (InterruptedException error) {
                    Thread.currentThread().interrupt();
                }
            }
        }
        cleanup();
    }

    /**
     * Whether the window has been closed.
     * 
     * @return True if closed.
     */
    public boolean isClosed() {
        return closed;
    }

    /**
     * The native webview. It is available once the window has been shown.
     * 
     * @return A webview or {@code null}.
     */
    public Webview browser() {
        return webview;
    }

    /**
     * The application which owns this window.
     * 
     * @return An application.
     */
    public Application application() {
        return application;
    }

    private void run() {
        try {
            if (closed) {
                return;
            }
            restoreWindowState();

            Webview browser = Webview.builder()
                    .title(title)
                    .enableDeveloperTools(application.config().devTools())
                    .width(Math.max(normalBounds.width, MIN_WIDTH))
                    .height(Math.max(normalBounds.height, MIN_HEIGHT))
                    .build();
            webview = browser;
            if (closed) {
                browser.close();
                return;
            }

            initialize(browser);

            if (WINDOWS) {
                long window = browser.nativeWindowPointer().address();
                if (window != 0) {
                    Win32.setBounds(window, normalBounds.x, normalBounds.y, normalBounds.width, normalBounds.height);
                    if (alwaysOnTop) {
                        Win32.setAlwaysOnTop(window, true);
                    }
                    if (!resizable) {
                        Win32.setResizable(window, false);
                    }
                }
            } else {
                browser.setSize(normalBounds.width, normalBounds.height);
            }

            browser.run();
        } catch (Throwable error) {
            failed(error);
        } finally {
            stopped.countDown();
            onWebviewStopped();
        }
    }

    private void initialize(Webview browser) {
        browser.bind("__apocryphaIpc", this::receive);
        browser.bind("__apocryphaPageReady", arguments -> {
            onPageReady();
            return "null";
        });
        browser.setInitScript(application.bridgeScript());
        browser.navigate(currentUrl);
    }

    private void failed(Throwable error) {
        I.error("apocrypha", new RuntimeException("Failed to initialize the webview for window " + id, error));
        application.quit();
    }

    private void onWebviewStopped() {
        if (closed) {
            return;
        }
        closed = true;
        cleanup();
    }

    private void cleanup() {
        if (!cleaned.compareAndSet(false, true)) {
            return;
        }

        try {
            application.saveWindowStates();
        } catch (Throwable error) {
            I.warn("apocrypha", new RuntimeException("Failed to save window state", error));
        }

        loaded = false;
        pending.clear();
        pendingScripts.clear();
        webview = null;

        for (Runnable listener : closeListeners) {
            try {
                listener.run();
            } catch (Throwable error) {
                I.warn("apocrypha", new RuntimeException("Window close listener failed", error));
            }
        }
        closeListeners.clear();

        application.windowClosed(this);
    }

    @SuppressWarnings("unchecked")
    private String receive(String json) {
        if (closed) {
            return null;
        }
        try {
            List<?> values = I.json(json, List.class);
            if (values == null || values.isEmpty()) {
                return null;
            }

            Request message = I.json(String.valueOf(values.get(0)), Request.class);
            if (message.channel() == null || message.channel().isBlank()) {
                I.warn("apocrypha", "Malformed IPC request: missing channel");
                return null;
            }

            List<String> arguments = message.args() == null ? List.of() : message.args();
            long id = message.id();
            application.ipc().dispatch(this, message.channel(), arguments, id, response -> respond(id, response));
        } catch (Throwable error) {
            I.warn("apocrypha", new RuntimeException("Malformed IPC request", error));
        }
        return null;
    }

    private void respond(long id, String response) {
        execute("window.__apocryphaResolve(" + id + "," + IpcCodec.write(response) + ");");
    }

    private void onPageReady() {
        if (closed) {
            return;
        }
        loaded = true;
        if (maximized) {
            long window = handle();
            if (WINDOWS && window != 0) {
                Win32.maximize(window);
            } else {
                Webview browser = webview;
                if (browser != null) {
                    browser.maximizeWindow();
                }
            }
        }
        startStateWatcher();

        String script;
        while ((script = pendingScripts.poll()) != null) {
            execute(script);
        }
        drainPending();

        if (WINDOWS) {
            Win32.focus(handle());
        }
    }

    private void navigate(String target) {
        Webview browser = webview;
        if (browser != null && target != null && !closed) {
            browser.navigate(target);
        }
    }

    private void dispatch(Event event) {
        String script = "window.__apocryphaDispatch(" + I.write(event.name()) + "," + event.payload() + ");";
        execute(script);
    }

    /**
     * Apply the persisted bounds and maximized state. This is called once, on the webview thread,
     * before the native window is created.
     */
    private void restoreWindowState() {
        if (!restoreStateEnabled || !application.config().restoreWindowState()) {
            return;
        }

        WindowStateStore.State state = application.windowState(id);
        if (state == null || !state.valid()) {
            return;
        }

        Rectangle saved = state.bounds();
        saved.width = Math.max(saved.width, MIN_WIDTH);
        saved.height = Math.max(saved.height, MIN_HEIGHT);

        if (isVisibleOnScreen(saved)) {
            normalBounds.setBounds(saved);
        }
        maximized = state.maximized;
    }

    /**
     * Read the native window bounds into the cached state. The native X button destroys the window
     * before {@link Webview#run()} returns, so a small watcher keeps the state current while the
     * window is open.
     */
    private boolean captureBounds() {
        if (!WINDOWS) {
            return false;
        }

        long window = handle();
        if (window == 0) {
            return false;
        }
        if (!Win32.isVisible(window)) {
            return false;
        }

        int[] rect = Win32.windowRect(window);
        if (rect[2] <= 0 || rect[3] <= 0) {
            return false;
        }

        boolean zoomed = Win32.isZoomed(window);
        boolean iconic = Win32.isIconic(window);
        boolean changed = false;
        synchronized (this) {
            if (!iconic) {
                if (!zoomed && (normalBounds.x != rect[0] || normalBounds.y != rect[1]
                        || normalBounds.width != rect[2] || normalBounds.height != rect[3])) {
                    normalBounds.setBounds(rect[0], rect[1], rect[2], rect[3]);
                    changed = true;
                }
                if (maximized != zoomed) {
                    maximized = zoomed;
                    changed = true;
                }
            }
        }
        return changed;
    }

    private synchronized void startStateWatcher() {
        if (!WINDOWS || stateWatcherStarted) {
            return;
        }
        stateWatcherStarted = true;

        Thread watcher = new Thread(() -> {
            long changedAt = 0;
            while (!closed) {
                try {
                    if (captureBounds()) {
                        changedAt = System.nanoTime();
                    }
                    if (changedAt != 0 && System.nanoTime() - changedAt >= STATE_SAVE_DELAY_NANOS) {
                        changedAt = 0;
                        application.saveWindowStates();
                    }
                } catch (Throwable error) {
                    I.debug("apocrypha", new RuntimeException("Failed to capture the window state", error));
                }
                try {
                    Thread.sleep(STATE_POLL_MILLIS);
                } catch (InterruptedException error) {
                    return;
                }
            }
        }, "apocrypha-window-state-" + id);
        watcher.setDaemon(true);
        watcher.start();
    }

    private long handle() {
        Webview browser = webview;
        return browser == null ? 0 : browser.nativeWindowPointer().address();
    }

    /**
     * The current persisted state. The cached normal bounds are used while maximized or minimized so
     * that restoring the window returns it to its previous size.
     * 
     * @return A state or {@code null}.
     */
    WindowStateStore.State windowState() {
        captureBounds();
        synchronized (this) {
            Rectangle bounds = new Rectangle(normalBounds);
            if (bounds.width <= 0 || bounds.height <= 0) {
                return null;
            }
            return new WindowStateStore.State(bounds.x, bounds.y, bounds.width, bounds.height, maximized);
        }
    }

    private static Rectangle screenBounds() {
        try {
            GraphicsDevice device = GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice();
            return device.getDefaultConfiguration().getBounds();
        } catch (Throwable error) {
            return new Rectangle(0, 0, 1920, 1080);
        }
    }

    private static boolean isVisibleOnScreen(Rectangle bounds) {
        try {
            for (GraphicsDevice device : GraphicsEnvironment.getLocalGraphicsEnvironment().getScreenDevices()) {
                Rectangle screen = device.getDefaultConfiguration().getBounds();
                Rectangle intersection = screen.intersection(bounds);
                if (intersection.width >= 80 && intersection.height >= 40) {
                    return true;
                }
            }
        } catch (Throwable error) {
            // A headless or unusual graphics environment should not prevent window creation.
            return true;
        }
        return false;
    }

    private void drainPending() {
        Event event;
        while ((event = pending.poll()) != null) {
            dispatch(event);
        }
    }

    private String resolve(String url) {
        Objects.requireNonNull(url, "url");
        return url.contains("://") ? url : application.config().url(url);
    }

    private record Event(String name, String payload) {
    }

    private record Request(long id, String channel, List<String> args) {
    }

    @Override
    public String toString() {
        return "Window [id=" + id + ", url=" + currentUrl + ", closed=" + closed + "]";
    }
}
