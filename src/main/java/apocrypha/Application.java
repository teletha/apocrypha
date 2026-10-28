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

import java.awt.GraphicsEnvironment;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;

import javax.swing.SwingUtilities;
import javax.swing.Timer;

import org.cef.CefApp;
import org.cef.CefApp.CefAppState;
import org.cef.callback.CefSchemeRegistrar;

import me.friwi.jcefmaven.CefAppBuilder;
import me.friwi.jcefmaven.CefInitializationException;
import me.friwi.jcefmaven.MavenCefAppHandlerAdapter;
import me.friwi.jcefmaven.UnsupportedPlatformException;

/**
 * The Java side of an Apocrypha application.
 * <p>
 * An application owns the Chromium runtime, all windows, and the IPC bridge. Subclasses configure
 * the runtime in {@link #configure(ApplicationConfig)}, create windows in {@link #onStart()}, and
 * release application resources in {@link #onExit()}.
 *
 * <pre>{@code
 * public class Notes extends Application {
 *     public static void main(String[] args) {
 *         Application.launch(Notes.class, args);
 *     }
 *
 *     @Override
 *     protected void onStart() {
 *         ipc().handle("notes:save", request -> {
 *             Files.writeString(Path.of("note.txt"), request.string(0));
 *             return true;
 *         });
 *         createWindow().title("Notes").show();
 *     }
 * }
 * }</pre>
 */
public abstract class Application {

    /** The framework version exposed to the renderer. */
    public static final String FRAMEWORK_VERSION = "0.1";

    /** The framework logger. */
    static final System.Logger logger = System.getLogger("apocrypha");

    private static final String RESTART_PARENT = "--apocrypha-restart-parent=";

    private static final int QUIT_GRACE_MILLIS = 300;

    private static final int RESTART_WAIT_SECONDS = 30;

    private static volatile Application current;

    private final List<Window> windows = new CopyOnWriteArrayList<>();

    private final List<String> arguments = new ArrayList<>();

    private String mainClass;

    private String mainJar;

    private volatile boolean restarting;

    private ApplicationConfig config;

    private Ipc ipc;

    private Bridge bridge;

    private ResourceLoader loader;

    private WindowStateStore windowStates;

    private Timer windowStateTimer;

    private int windowSequence;

    private CefApp cefApp;

    private volatile boolean running;

    private volatile boolean quitting;

    protected Application() {
    }

    /**
     * Launch an application.
     * <p>
     * This method initializes Chromium on the calling thread and then schedules the application
     * lifecycle on the AWT event thread. It returns after the lifecycle has been scheduled.
     * 
     * @param type An application class with an accessible no-argument constructor.
     * @param args Command line arguments. Switches beginning with {@code --} are forwarded to
     *        Chromium.
     * @return The launched application.
     */
    public static Application launch(Class<? extends Application> type, String... args) {
        Objects.requireNonNull(type, "type");
        try {
            Application application = type.getDeclaredConstructor().newInstance();
            application.determineRestartTarget(type);
            application.boot(args == null ? new String[0] : args);
            return application;
        } catch (ReflectiveOperationException error) {
            throw new IllegalStateException("Failed to create the application: " + type.getName(), error);
        }
    }

    /**
     * The currently running application.
     * 
     * @return An application or {@code null}.
     */
    public static Application current() {
        return current;
    }

    /**
     * Configure the application before Chromium starts.
     * 
     * @param config The mutable configuration.
     */
    protected void configure(ApplicationConfig config) {
    }

    /**
     * Start the application. This method runs on the AWT event thread. Create windows here.
     */
    protected void onStart() {
    }

    /**
     * Release application resources. This method runs on the AWT event thread before Chromium is
     * disposed.
     */
    protected void onExit() {
    }

    /**
     * The application configuration.
     * 
     * @return A configuration.
     */
    public final ApplicationConfig config() {
        return config;
    }

    /**
     * The IPC bridge.
     * 
     * @return An IPC bridge.
     */
    public final Ipc ipc() {
        return ipc;
    }

    /**
     * The Chromium user data directory.
     * 
     * @return A directory.
     */
    public final Path dataDirectory() {
        return config.dataDirectory();
    }

    /**
     * The raw command line arguments.
     * 
     * @return An immutable argument list.
     */
    public final List<String> arguments() {
        return List.copyOf(arguments);
    }

    /**
     * All open windows.
     * 
     * @return An immutable snapshot of the windows.
     */
    public final List<Window> windows() {
        return List.copyOf(windows);
    }

    /**
     * Create a window which loads the configured entry point.
     * 
     * @return A new window.
     */
    public final Window createWindow() {
        return createWindow(config.entryUrl());
    }

    /**
     * Create a window which loads the given path or URL.
     * 
     * @param url A relative path or an absolute URL.
     * @return A new window.
     */
    public final Window createWindow(String url) {
        if (quitting) {
            throw new IllegalStateException("The application is shutting down");
        }

        if (SwingUtilities.isEventDispatchThread()) {
            return createWindowOnEdt(url);
        }

        AtomicReference<Window> reference = new AtomicReference<>();
        try {
            SwingUtilities.invokeAndWait(() -> reference.set(createWindowOnEdt(url)));
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while creating a window", error);
        } catch (java.lang.reflect.InvocationTargetException error) {
            throw new IllegalStateException("Failed to create a window", error.getCause());
        }
        return reference.get();
    }

    /**
     * Quit the application. The last window may also trigger this automatically.
     */
    public final void quit() {
        if (SwingUtilities.isEventDispatchThread()) {
            doQuit();
        } else {
            SwingUtilities.invokeLater(this::doQuit);
        }
    }

    /**
     * Restart the application in a new JVM process.
     * <p>
     * The new process is launched with the same Java executable, JVM options (debug-agent switches
     * excepted), classpath or jar, working directory, and application arguments. It waits for this
     * process to exit before initializing Chromium, so the JCEF cache lock is released and the saved
     * window state can be restored.
     * <p>
     * This requires the application to be started through {@link #launch(Class, String...)}. The
     * call runs {@link #onExit()} before the old process exits; the new process runs
     * {@link #onStart()} normally.
     */
    public final void restart() {
        if (restarting || quitting) {
            return;
        }

        restarting = true;
        if (SwingUtilities.isEventDispatchThread()) {
            doRestart();
        } else {
            SwingUtilities.invokeLater(this::doRestart);
        }
    }

    /**
     * The information exposed to the renderer as {@code apocrypha.app}.
     * 
     * @return An immutable metadata map.
     */
    Map<String, String> info() {
        Map<String, String> info = new LinkedHashMap<>();
        info.put("name", config.name());
        info.put("version", config.version());
        info.put("frameworkVersion", FRAMEWORK_VERSION);
        info.put("platform", System.getProperty("os.name", "unknown"));
        info.put("arch", System.getProperty("os.arch", "unknown"));
        info.put("java", System.getProperty("java.version", "unknown"));
        info.put("cef", cefVersion());
        return info;
    }

    String bridgeScript() {
        return bridge.script();
    }

    CefApp cefApp() {
        return cefApp;
    }

    ResourceLoader resourceLoader() {
        return loader;
    }

    WindowStateStore.State windowState(String id) {
        if (windowStates == null || !config.restoreWindowState()) {
            return null;
        }
        return windowStates.get(id);
    }

    /**
     * Schedule a window state save. Move and resize events are coalesced so dragging a window does
     * not write the JSON file for every pixel.
     */
    void windowStateChanged() {
        if (quitting || windowStates == null || !config.restoreWindowState()) {
            return;
        }
        if (!SwingUtilities.isEventDispatchThread()) {
            SwingUtilities.invokeLater(this::windowStateChanged);
            return;
        }
        if (windowStateTimer == null) {
            windowStateTimer = new Timer(400, event -> saveWindowStates());
            windowStateTimer.setRepeats(false);
        }
        windowStateTimer.restart();
    }

    /**
     * Persist the state of every open window.
     */
    void saveWindowStates() {
        if (windowStates == null || !config.restoreWindowState()) {
            return;
        }
        if (!SwingUtilities.isEventDispatchThread()) {
            SwingUtilities.invokeLater(this::saveWindowStates);
            return;
        }

        Map<String, WindowStateStore.State> states = new LinkedHashMap<>();
        for (Window window : windows) {
            if (!window.isClosed()) {
                WindowStateStore.State state = window.windowState();
                if (state != null) {
                    states.put(window.id(), state);
                }
            }
        }
        windowStates.putAll(states);
    }

    void windowClosed(Window window) {
        windows.remove(window);
        if (!quitting && windows.isEmpty() && config.quitWhenLastWindowClosed()) {
            SwingUtilities.invokeLater(this::doQuit);
        }
    }

    /**
     * Whether disposing this window will terminate the application. In that case the window leaves
     * the CEF client to {@link CefApp#dispose()} to avoid racing the client close callbacks.
     * 
     * @param window A window.
     * @return True if the application will quit.
     */
    boolean willQuitOnClose(Window window) {
        return quitting || (config.quitWhenLastWindowClosed() && windows.size() == 1);
    }

    private Window createWindowOnEdt(String url) {
        if (cefApp == null) {
            throw new IllegalStateException("Windows cannot be created before Chromium is initialized; use onStart()");
        }
        String target = url == null || url.isBlank() ? config.entryUrl() : (url
                .contains("://") ? url : config.url(url));
        Window window = new Window(this, target, nextWindowId());
        windows.add(window);
        return window;
    }

    private String nextWindowId() {
        int index = ++windowSequence;
        return index == 1 ? "main" : "window-" + index;
    }

    private void boot(String[] args) {
        awaitPreviousInstance(args);

        if (GraphicsEnvironment.isHeadless()) {
            throw new IllegalStateException("Apocrypha requires a graphical environment");
        }

        current = this;
        for (String argument : args) {
            if (argument != null && !argument.startsWith(RESTART_PARENT)) {
                arguments.add(argument);
            }
        }

        this.config = new ApplicationConfig();
        configure(config);
        for (String argument : args) {
            if ("--devtools".equals(argument) || "--apocrypha-devtools".equals(argument)) {
                config.devTools(true);
            }
        }

        Path data = config.dataDirectory();
        try {
            Files.createDirectories(data);
            Files.createDirectories(data.resolve("cache"));
        } catch (IOException error) {
            throw new IllegalStateException("Failed to create the Chromium user data directory: " + data, error);
        }

        this.windowStates = new WindowStateStore(data.resolve("window-state.json"));
        this.ipc = new Ipc(this);
        startCef(args);
        SwingUtilities.invokeLater(this::startUi);
    }

    private void startCef(String[] args) {
        CefAppBuilder builder = new CefAppBuilder();
        builder.setInstallDir(config.installDirectory().toFile());
        builder.getCefSettings().root_cache_path = config.dataDirectory().toString();
        builder.getCefSettings().cache_path = config.dataDirectory().resolve("cache").toString();
        builder.getCefSettings().persist_session_cookies = true;

        builder.addJcefArgs(config.cefArguments().toArray(String[]::new));
        for (String argument : args) {
            if (argument != null && argument.startsWith("--") && !argument.startsWith("--apocrypha-")
                    && !argument.equals("--devtools")) {
                builder.addJcefArgs(argument);
            }
        }
        if (config.cefCustomizer() != null) {
            config.cefCustomizer().accept(builder);
        }
        builder.getCefSettings().windowless_rendering_enabled = false;

        builder.setAppHandler(new MavenCefAppHandlerAdapter() {
            @Override
            public void onRegisterCustomSchemes(CefSchemeRegistrar registrar) {
                registrar.addCustomScheme(config.scheme(), true, false, false, true, true, false, true);
            }

            @Override
            public void stateHasChanged(CefAppState state) {
                if (state == CefAppState.TERMINATED && running) {
                    SwingUtilities.invokeLater(Application.this::quit);
                }
            }
        });

        try {
            this.cefApp = builder.build();
        } catch (IOException | UnsupportedPlatformException | InterruptedException | CefInitializationException error) {
            if (error instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            throw new IllegalStateException("Failed to initialize Chromium", error);
        }

        this.bridge = new Bridge(config, cefVersion());
        this.loader = new ResourceLoader(config, bridge);
    }

    private void startUi() {
        if (quitting) {
            return;
        }

        running = true;
        logger.log(System.Logger.Level.INFO, "Starting " + config.name() + " " + config.version() + " (" + config
                .baseUrl() + ")");
        try {
            onStart();
        } catch (Throwable error) {
            logger.log(System.Logger.Level.ERROR, "The application failed to start", error);
            quit();
        }
    }

    private void doRestart() {
        if (quitting) {
            restarting = false;
            return;
        }

        List<String> command;
        try {
            command = restartCommand();
        } catch (Throwable error) {
            restarting = false;
            logger.log(System.Logger.Level.ERROR, "Failed to prepare a restart", error);
            return;
        }

        try {
            ProcessBuilder builder = new ProcessBuilder(command);
            String directory = System.getProperty("user.dir");
            if (directory != null && !directory.isBlank()) {
                builder.directory(Path.of(directory).toFile());
            }
            builder.inheritIO();
            Process process = builder.start();
            logger.log(System.Logger.Level.INFO, "Restarting " + config.name() + " in process " + process.pid());
        } catch (Throwable error) {
            restarting = false;
            logger.log(System.Logger.Level.ERROR, "Failed to restart " + config.name(), error);
            return;
        }

        try {
            doQuit();
        } finally {
            restarting = false;
        }
    }

    private List<String> restartCommand() {
        if (mainJar == null && (mainClass == null || mainClass.isBlank())) {
            throw new IllegalStateException("Restart requires an application started with Application.launch(...)");
        }

        List<String> command = new ArrayList<>();
        command.add(javaExecutable().toString());

        List<String> jvmArguments = ManagementFactory.getRuntimeMXBean().getInputArguments();
        for (int i = 0; i < jvmArguments.size(); i++) {
            String argument = jvmArguments.get(i);
            if (argument == null) {
                continue;
            }
            if (isClassPathOption(argument)) {
                if (!argument.contains("=")) {
                    i++;
                }
                continue;
            }
            if (argument.startsWith("-Djava.class.path=")) {
                continue;
            }
            if (argument.equals("-jar") || argument.equals("--jar")) {
                i++;
                continue;
            }
            if (argument.startsWith(RESTART_PARENT) || argument.startsWith("-agentlib:jdwp")
                    || argument.startsWith("-Xrunjdwp")) {
                continue;
            }
            command.add(argument);
        }

        if (mainJar != null) {
            command.add("-jar");
            command.add(mainJar);
        } else {
            String classPath = System.getProperty("java.class.path");
            if (classPath == null || classPath.isBlank()) {
                throw new IllegalStateException("The java.class.path system property is empty");
            }
            command.add("-cp");
            command.add(classPath);
            command.add(mainClass);
        }

        command.addAll(arguments);
        command.add(RESTART_PARENT + ProcessHandle.current().pid());
        return command;
    }

    /**
     * Find the class or jar which the JVM was started with. This makes restart work when the
     * application class does not contain the {@code main} method.
     * 
     * @param type The application class.
     */
    private void determineRestartTarget(Class<?> type) {
        String launched = System.getProperty("sun.java.command");
        if (launched != null && !launched.isBlank()) {
            String candidate = launched.trim().split("\\s+", 2)[0];
            if (candidate.endsWith(".jar")) {
                try {
                    Path jar = Path.of(candidate);
                    if (Files.isRegularFile(jar)) {
                        this.mainJar = jar.toAbsolutePath().normalize().toString();
                        this.mainClass = null;
                        return;
                    }
                } catch (RuntimeException error) {
                    // Fall through to the application class.
                }
            } else if (!candidate.isBlank() && !candidate.startsWith("-") && candidate.indexOf('/') < 0
                    && candidate.indexOf('\\') < 0) {
                this.mainClass = candidate;
                return;
            }
        }

        try {
            type.getMethod("main", String[].class);
            this.mainClass = type.getName();
        } catch (ReflectiveOperationException | SecurityException error) {
            this.mainClass = null;
        }
    }

    private static void awaitPreviousInstance(String[] args) {
        for (String argument : args) {
            if (argument == null || !argument.startsWith(RESTART_PARENT)) {
                continue;
            }

            long pid;
            try {
                pid = Long.parseLong(argument.substring(RESTART_PARENT.length()));
            } catch (NumberFormatException error) {
                logger.log(System.Logger.Level.WARNING, "Invalid restart parent pid: " + argument);
                continue;
            }
            if (pid <= 0 || pid == ProcessHandle.current().pid()) {
                continue;
            }

            try {
                ProcessHandle.of(pid).ifPresent(previous -> {
                    logger.log(System.Logger.Level.INFO, "Waiting for previous process " + pid + " to exit");
                    try {
                        previous.onExit().get(RESTART_WAIT_SECONDS, TimeUnit.SECONDS);
                    } catch (TimeoutException error) {
                        logger.log(System.Logger.Level.WARNING,
                                "Timed out waiting for previous process " + pid + "; continuing");
                    } catch (InterruptedException error) {
                        Thread.currentThread().interrupt();
                    } catch (ExecutionException error) {
                        logger.log(System.Logger.Level.WARNING, "Failed to wait for previous process " + pid,
                                error.getCause());
                    }
                });
            } catch (RuntimeException error) {
                logger.log(System.Logger.Level.WARNING, "Failed to inspect previous process " + pid, error);
            }
        }
    }

    private static Path javaExecutable() {
        String home = System.getProperty("java.home");
        if (home == null || home.isBlank()) {
            throw new IllegalStateException("The java.home system property is empty");
        }

        Path bin = Path.of(home, "bin");
        Path executable = bin.resolve(isWindows() ? "java.exe" : "java");
        if (!Files.isExecutable(executable)) {
            executable = bin.resolve("java");
        }
        if (!Files.isExecutable(executable)) {
            throw new IllegalStateException("Cannot find the java executable in " + bin);
        }
        return executable;
    }

    private static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
    }

    private static boolean isClassPathOption(String option) {
        return option.equals("-cp") || option.equals("-classpath") || option.equals("--class-path")
                || option.startsWith("-cp=") || option.startsWith("-classpath=") || option.startsWith("--class-path=");
    }

    private void doQuit() {
        if (quitting) {
            return;
        }

        quitting = true;
        running = false;
        if (windowStateTimer != null) {
            windowStateTimer.stop();
            windowStateTimer = null;
        }
        try {
            onExit();
        } catch (Throwable error) {
            logger.log(System.Logger.Level.ERROR, "The application failed to stop cleanly", error);
        }
        saveWindowStates();

        for (Window window : List.copyOf(windows)) {
            try {
                window.dispose();
            } catch (Throwable error) {
                logger.log(System.Logger.Level.WARNING, "Failed to dispose a window", error);
            }
        }
        windows.clear();

        if (ipc != null) {
            ipc.close();
        }
        disposeCefSoon();
    }

    private void disposeCefSoon() {
        if (cefApp == null) {
            completeQuit();
            return;
        }

        // Let browser close callbacks from windows disposed immediately before quit finish before
        // CefApp.dispose() acquires its monitors.
        Timer timer = new Timer(QUIT_GRACE_MILLIS, event -> completeQuit());
        timer.setRepeats(false);
        timer.start();
    }

    private void completeQuit() {
        if (cefApp != null) {
            try {
                cefApp.dispose();
            } catch (Throwable error) {
                logger.log(System.Logger.Level.WARNING, "Failed to dispose Chromium", error);
            }
            cefApp = null;
        }
        if (current == this) {
            current = null;
        }
        logger.log(System.Logger.Level.INFO, "Stopped " + config.name());
        System.exit(0);
    }

    private String cefVersion() {
        if (cefApp == null) {
            return "unknown";
        }
        try {
            CefApp.CefVersion version = cefApp.getVersion();
            if (version == null) {
                return "unknown";
            }
            return version.CHROME_VERSION_MAJOR + "." + version.CHROME_VERSION_MINOR + "." + version.CHROME_VERSION_BUILD
                    + "." + version.CHROME_VERSION_PATCH;
        } catch (Throwable error) {
            return "unknown";
        }
    }

    @Override
    public String toString() {
        return "Application [name=" + config.name() + ", version=" + config.version() + ", windows=" + windows.size()
                + "]";
    }
}
