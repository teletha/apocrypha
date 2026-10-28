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

import java.awt.Desktop;
import java.awt.Dimension;
import java.awt.GraphicsDevice;
import java.awt.GraphicsEnvironment;
import java.awt.Rectangle;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.net.URI;
import java.util.List;
import java.util.Objects;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import javax.swing.JFrame;
import javax.swing.SwingUtilities;
import javax.swing.WindowConstants;

import org.cef.CefClient;
import org.cef.CefSettings;
import org.cef.browser.CefBrowser;
import org.cef.browser.CefFrame;
import org.cef.browser.CefMessageRouter;
import org.cef.callback.CefQueryCallback;
import org.cef.handler.CefDisplayHandlerAdapter;
import org.cef.handler.CefLifeSpanHandlerAdapter;
import org.cef.handler.CefLoadHandler;
import org.cef.handler.CefLoadHandlerAdapter;
import org.cef.handler.CefMessageRouterHandlerAdapter;
import org.cef.handler.CefRequestHandlerAdapter;
import org.cef.handler.CefResourceHandler;
import org.cef.handler.CefResourceRequestHandler;
import org.cef.handler.CefResourceRequestHandlerAdapter;
import org.cef.misc.BoolRef;
import org.cef.network.CefRequest;

import kiss.I;

/**
 * A native window which hosts a Chromium browser.
 * <p>
 * Create windows from {@link Application#onStart()} with {@link Application#createWindow()}. All
 * methods may be called from any thread.
 */
public final class Window {

    private final Application application;

    private final JFrame frame;

    private final CefClient client;

    private final CefMessageRouter router;

    private final CefBrowser browser;

    private final Queue<Event> pending = new ConcurrentLinkedQueue<>();

    private final List<Runnable> closeListeners = new CopyOnWriteArrayList<>();

    private volatile boolean loaded;

    private volatile boolean closed;

    private volatile boolean titleExplicit;

    private volatile boolean devToolsOpened;

    private volatile String id;

    private volatile boolean restoreStateEnabled;

    private final Rectangle normalBounds = new Rectangle();

    private volatile boolean maximized;

    private boolean restored;

    private boolean restoring;

    Window(Application application, String url, String id) {
        this.application = application;
        this.id = Objects.requireNonNull(id, "id");
        this.restoreStateEnabled = application.config().restoreWindowState();
        this.frame = new JFrame(application.config().name());
        this.frame.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
        this.frame.setMinimumSize(new Dimension(320, 240));
        this.frame.setSize(new Dimension(1100, 760));
        this.frame.setLocationRelativeTo(null);
        this.normalBounds.setBounds(frame.getBounds());

        this.client = application.cefApp().createClient();
        this.router = CefMessageRouter.create();
        this.router.addHandler(new Router(), true);
        this.client.addMessageRouter(router);
        this.client.addRequestHandler(new RequestHandler(application.resourceLoader()));
        this.client.addDisplayHandler(new Display());
        this.client.addLoadHandler(new Loader());
        this.client.addLifeSpanHandler(new Lifespan());
        this.browser = client.createBrowser(url, false, false);
        this.frame.add(browser.getUIComponent());

        this.frame.addComponentListener(new ComponentAdapter() {
            @Override
            public void componentMoved(ComponentEvent event) {
                boundsChanged();
            }

            @Override
            public void componentResized(ComponentEvent event) {
                boundsChanged();
            }
        });
        this.frame.addWindowStateListener(event -> stateChanged());
        this.frame.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent event) {
                close();
            }
        });
    }

    /**
     * Set an explicit window title. An explicit title is not overwritten by the document title.
     * 
     * @param title A window title.
     * @return This window.
     */
    public Window title(String title) {
        Objects.requireNonNull(title, "title");
        titleExplicit = true;
        runOnEdt(() -> frame.setTitle(title));
        return this;
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
        runOnEdt(() -> {
            frame.setSize(width, height);
            updateNormalBounds();
            application.windowStateChanged();
        });
        return this;
    }

    /**
     * Get the current window size.
     * 
     * @return A size.
     */
    public Dimension size() {
        return readOnEdt(frame::getSize);
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
        runOnEdt(() -> {
            frame.setBounds(x, y, width, height);
            updateNormalBounds();
            application.windowStateChanged();
        });
        return this;
    }

    /**
     * Get the current window bounds.
     * 
     * @return A rectangle.
     */
    public Rectangle bounds() {
        return readOnEdt(frame::getBounds);
    }

    /**
     * Move the window without changing its size.
     * 
     * @param x The horizontal position.
     * @param y The vertical position.
     * @return This window.
     */
    public Window position(int x, int y) {
        runOnEdt(() -> {
            frame.setLocation(x, y);
            updateNormalBounds();
            application.windowStateChanged();
        });
        return this;
    }

    /**
     * Center the window on the screen.
     * 
     * @return This window.
     */
    public Window center() {
        runOnEdt(() -> {
            frame.setLocationRelativeTo(null);
            updateNormalBounds();
            application.windowStateChanged();
        });
        return this;
    }

    /**
     * Show the window.
     * 
     * @return This window.
     */
    public Window show() {
        runOnEdt(() -> {
            restoreWindowState();
            if (maximized) {
                frame.setExtendedState(frame.getExtendedState() | JFrame.MAXIMIZED_BOTH);
            }
            frame.setVisible(true);
            frame.toFront();
            browser.setFocus(true);
            if (maximized) {
                frame.setExtendedState(frame.getExtendedState() | JFrame.MAXIMIZED_BOTH);
            }
            application.windowStateChanged();
        });
        return this;
    }

    /**
     * Hide the window.
     * 
     * @return This window.
     */
    public Window hide() {
        runOnEdt(() -> frame.setVisible(false));
        return this;
    }

    /**
     * Minimize the window.
     * 
     * @return This window.
     */
    public Window minimize() {
        runOnEdt(() -> frame.setExtendedState(frame.getExtendedState() | JFrame.ICONIFIED));
        return this;
    }

    /**
     * Maximize the window.
     * 
     * @return This window.
     */
    public Window maximize() {
        runOnEdt(() -> {
            if ((frame.getExtendedState() & JFrame.MAXIMIZED_BOTH) != JFrame.MAXIMIZED_BOTH) {
                updateNormalBounds();
            }
            maximized = true;
            frame.setExtendedState(JFrame.MAXIMIZED_BOTH);
            application.windowStateChanged();
        });
        return this;
    }

    /**
     * Restore the window from a maximized state.
     * 
     * @return This window.
     */
    public Window unmaximize() {
        runOnEdt(() -> {
            maximized = false;
            frame.setExtendedState(JFrame.NORMAL);
            SwingUtilities.invokeLater(() -> {
                if (!closed) {
                    updateNormalBounds();
                    application.windowStateChanged();
                }
            });
        });
        return this;
    }

    /**
     * Whether the window is maximized.
     * 
     * @return True if maximized.
     */
    public boolean isMaximized() {
        return readOnEdt(() -> (frame.getExtendedState() & JFrame.MAXIMIZED_BOTH) == JFrame.MAXIMIZED_BOTH);
    }

    /**
     * Configure whether the user can resize the window.
     * 
     * @param resizable Whether the window is resizable.
     * @return This window.
     */
    public Window setResizable(boolean resizable) {
        runOnEdt(() -> frame.setResizable(resizable));
        return this;
    }

    /**
     * Configure whether the window is always on top.
     * 
     * @param alwaysOnTop Whether the window is always on top.
     * @return This window.
     */
    public Window setAlwaysOnTop(boolean alwaysOnTop) {
        runOnEdt(() -> frame.setAlwaysOnTop(alwaysOnTop));
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
        loaded = false;
        runOnEdt(() -> browser.loadURL(target));
        return this;
    }

    /**
     * Reload the current document.
     * 
     * @return This window.
     */
    public Window reload() {
        loaded = false;
        runOnEdt(browser::reload);
        return this;
    }

    /**
     * Open the Chromium developer tools.
     * 
     * @return This window.
     */
    public Window openDevTools() {
        runOnEdt(browser::openDevTools);
        return this;
    }

    /**
     * Execute JavaScript in the current document.
     * 
     * @param script A JavaScript expression.
     * @return This window.
     */
    public Window execute(String script) {
        Objects.requireNonNull(script, "script");
        if (!closed) {
            runOnEdt(() -> browser.executeJavaScript(script, "", 0));
        }
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
        runOnEdt(this::dispose);
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
     * The native frame.
     * 
     * @return A frame.
     */
    public JFrame frame() {
        return frame;
    }

    /**
     * The native browser.
     * 
     * @return A browser.
     */
    public CefBrowser browser() {
        return browser;
    }

    /**
     * The application which owns this window.
     * 
     * @return An application.
     */
    public Application application() {
        return application;
    }

    void dispose() {
        if (closed) {
            return;
        }

        boolean releaseCef = !application.willQuitOnClose(this);
        try {
            application.saveWindowStates();
        } catch (Throwable error) {
            Application.logger.log(System.Logger.Level.WARNING, "Failed to save window state", error);
        }

        closed = true;
        loaded = false;
        pending.clear();

        for (Runnable listener : closeListeners) {
            try {
                listener.run();
            } catch (Throwable error) {
                Application.logger.log(System.Logger.Level.WARNING, "Window close listener failed", error);
            }
        }
        closeListeners.clear();

        if (releaseCef) {
            try {
                router.dispose();
            } catch (Throwable error) {
                Application.logger.log(System.Logger.Level.DEBUG, "Failed to dispose the message router", error);
            }
            try {
                client.dispose();
            } catch (Throwable error) {
                Application.logger.log(System.Logger.Level.DEBUG, "Failed to dispose the CEF client", error);
            }
        }
        frame.dispose();
        application.windowClosed(this);
    }

    private void dispatch(Event event) {
        String script = "window.__apocryphaDispatch(" + I.write(event.name()) + "," + event.payload() + ");";
        execute(script);
    }

    /**
     * Apply the persisted bounds and maximized state. This is called once, immediately before the
     * window becomes visible.
     */
    private void restoreWindowState() {
        if (restored) {
            return;
        }
        restored = true;

        if (!restoreStateEnabled || !application.config().restoreWindowState()) {
            return;
        }

        WindowStateStore.State state = application.windowState(id);
        if (state == null || !state.valid()) {
            return;
        }

        Rectangle saved = state.bounds();
        saved.width = Math.max(saved.width, frame.getMinimumSize().width);
        saved.height = Math.max(saved.height, frame.getMinimumSize().height);

        if (isVisibleOnScreen(saved)) {
            restoring = true;
            try {
                frame.setBounds(saved);
                normalBounds.setBounds(saved);
                maximized = state.maximized;
            } finally {
                restoring = false;
            }
        } else if (state.maximized) {
            // The saved monitor is gone, but a maximized window is still useful on the primary
            // screen. Keep the default bounds and maximize them.
            maximized = true;
        }
    }

    private void updateNormalBounds() {
        if (restoring) {
            return;
        }
        int state = frame.getExtendedState();
        if ((state & (JFrame.MAXIMIZED_BOTH | JFrame.ICONIFIED)) == 0) {
            Rectangle bounds = frame.getBounds();
            if (bounds.width > 0 && bounds.height > 0) {
                normalBounds.setBounds(bounds);
            }
        }
    }

    private void boundsChanged() {
        if (closed || restoring) {
            return;
        }
        updateNormalBounds();
        application.windowStateChanged();
    }

    private void stateChanged() {
        if (closed || restoring) {
            return;
        }

        int state = frame.getExtendedState();
        if ((state & JFrame.ICONIFIED) == 0) {
            boolean nowMaximized = (state & JFrame.MAXIMIZED_BOTH) == JFrame.MAXIMIZED_BOTH;
            if (!nowMaximized && !maximized) {
                updateNormalBounds();
            }
            maximized = nowMaximized;
        }
        application.windowStateChanged();
    }

    /**
     * The current persisted state. The normal bounds are used while maximized or minimized so that
     * restoring the window returns it to its previous size.
     * 
     * @return A state or {@code null}.
     */
    WindowStateStore.State windowState() {
        return readOnEdt(() -> {
            int state = frame.getExtendedState();
            Rectangle bounds = maximized || (state & JFrame.ICONIFIED) != 0 ? new Rectangle(normalBounds)
                    : frame.getBounds();
            if (bounds.width <= 0 || bounds.height <= 0) {
                return null;
            }
            return new WindowStateStore.State(bounds.x, bounds.y, bounds.width, bounds.height, maximized);
        });
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

    private void openExternal(String url) {
        if (url == null || url.isBlank()) {
            return;
        }
        try {
            URI uri = URI.create(url);
            String scheme = uri.getScheme();
            if (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme) && !"mailto".equalsIgnoreCase(scheme)) {
                return;
            }
            Thread.startVirtualThread(() -> {
                try {
                    if (Desktop.isDesktopSupported()) {
                        Desktop.getDesktop().browse(uri);
                    }
                } catch (Throwable error) {
                    Application.logger.log(System.Logger.Level.WARNING, "Failed to open external URL: " + url, error);
                }
            });
        } catch (RuntimeException error) {
            Application.logger.log(System.Logger.Level.WARNING, "Invalid external URL: " + url, error);
        }
    }

    private void runOnEdt(Runnable action) {
        if (SwingUtilities.isEventDispatchThread()) {
            action.run();
        } else {
            SwingUtilities.invokeLater(action);
        }
    }

    private <T> T readOnEdt(Supplier<T> supplier) {
        if (SwingUtilities.isEventDispatchThread()) {
            return supplier.get();
        }

        AtomicReference<T> value = new AtomicReference<>();
        AtomicReference<RuntimeException> failure = new AtomicReference<>();
        try {
            SwingUtilities.invokeAndWait(() -> {
                try {
                    value.set(supplier.get());
                } catch (RuntimeException error) {
                    failure.set(error);
                }
            });
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting for the window", error);
        } catch (java.lang.reflect.InvocationTargetException error) {
            throw new IllegalStateException("Failed to read the window state", error.getCause());
        }

        if (failure.get() != null) {
            throw failure.get();
        }
        return value.get();
    }

    private record Event(String name, String payload) {
    }

    private record Request(long id, String channel, List<String> args) {
    }

    private final class Router extends CefMessageRouterHandlerAdapter {

        @Override
        public boolean onQuery(CefBrowser target, CefFrame frame, long queryId, String request, boolean persistent,
                CefQueryCallback callback) {
            if (frame != null && !frame.isMain()) {
                callback.failure(-1, "IPC is only available in the main frame");
                return true;
            }

            try {
                Request message = I.json(request, Request.class);
                if (message.channel() == null || message.channel().isBlank()) {
                    callback.failure(-1, "Malformed IPC request: missing channel");
                    return true;
                }
                List<String> arguments = message.args() == null ? List.of() : message.args();
                long id = message.id() == 0 ? queryId : message.id();
                application.ipc().dispatch(Window.this, message.channel(), arguments, id, callback);
            } catch (RuntimeException error) {
                callback.failure(-1, "Malformed IPC request");
            }
            return true;
        }
    }

    private static final class RequestHandler extends CefRequestHandlerAdapter {

        private final ResourceLoader loader;

        private final CefResourceRequestHandler resources = new CefResourceRequestHandlerAdapter() {
            @Override
            public CefResourceHandler getResourceHandler(CefBrowser browser, CefFrame frame, CefRequest request) {
                return loader.handles(request.getURL()) ? new AppResourceHandler(loader) : null;
            }
        };

        private RequestHandler(ResourceLoader loader) {
            this.loader = loader;
        }

        @Override
        public CefResourceRequestHandler getResourceRequestHandler(CefBrowser browser, CefFrame frame, CefRequest request,
                boolean isNavigation, boolean isDownload, String requestInitiator, BoolRef disableDefaultHandling) {
            if (loader.handles(request.getURL())) {
                disableDefaultHandling.set(true);
                return resources;
            }
            return null;
        }
    }

    private final class Display extends CefDisplayHandlerAdapter {

        @Override
        public void onTitleChange(CefBrowser target, String title) {
            if (!titleExplicit && title != null && !title.isBlank()) {
                runOnEdt(() -> frame.setTitle(title));
            }
        }

        @Override
        public boolean onConsoleMessage(CefBrowser target, CefSettings.LogSeverity level, String message, String source,
                int line) {
            System.Logger.Level severity = switch (level) {
            case LOGSEVERITY_ERROR, LOGSEVERITY_FATAL -> System.Logger.Level.ERROR;
            case LOGSEVERITY_WARNING -> System.Logger.Level.WARNING;
            default -> System.Logger.Level.DEBUG;
            };
            Application.logger.log(severity, "[renderer] " + message + " (" + source + ":" + line + ")");
            return false;
        }
    }

    private final class Loader extends CefLoadHandlerAdapter {

        @Override
        public void onLoadStart(CefBrowser target, CefFrame frame, CefRequest.TransitionType transitionType) {
            if (frame != null && frame.isMain()) {
                loaded = false;
            }
        }

        @Override
        public void onLoadEnd(CefBrowser target, CefFrame frame, int httpStatusCode) {
            if (frame == null || !frame.isMain()) {
                return;
            }

            String url = browser.getURL();
            if (url != null && !url.startsWith(application.config().baseUrl())) {
                browser.executeJavaScript(application.bridgeScript(), "", 0);
            }

            loaded = true;
            drainPending();

            if (application.config().devTools() && !devToolsOpened) {
                devToolsOpened = true;
                browser.openDevTools();
            }
        }

        @Override
        public void onLoadError(CefBrowser target, CefFrame frame, CefLoadHandler.ErrorCode errorCode, String errorText,
                String failedUrl) {
            if (frame != null && frame.isMain()) {
                Application.logger.log(System.Logger.Level.WARNING,
                        "Failed to load " + failedUrl + ": " + errorCode + " (" + errorText + ")");
            }
        }
    }

    private final class Lifespan extends CefLifeSpanHandlerAdapter {

        @Override
        public boolean onBeforePopup(CefBrowser target, CefFrame frame, String targetUrl, String targetFrameName) {
            if (targetUrl != null && targetUrl.startsWith(application.config().baseUrl())) {
                load(targetUrl);
            } else {
                openExternal(targetUrl);
            }
            return true;
        }
    }
}
