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

import java.awt.Rectangle;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.Consumer;

import kiss.I;
import kiss.JSON;

/**
 * The asynchronous bridge between the Java backend and the JavaScript renderer.
 * <p>
 * A channel is registered with {@link #handle(String, IpcHandler)} or automatically from methods
 * annotated with {@link IpcChannel} through {@link #register(Object)} or
 * {@link #register(Class)}. A request from the renderer is handled on a virtual thread, so the
 * webview UI thread is never blocked. The renderer can also emit named events to the Java side with
 * {@code apocrypha.emit(...)}; register those with {@link #on(String, Consumer)}.
 * <p>
 * Values are encoded with Sinobu ({@code I.write} / {@code I.json}). Application channels must not
 * use the {@code apocrypha:} namespace.
 */
public final class Ipc {

    private static final String RESERVED = "apocrypha:";

    private final Application application;

    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

    private final Map<String, IpcHandler> handlers = new ConcurrentHashMap<>();

    private final Map<String, List<Consumer<JSON>>> listeners = new ConcurrentHashMap<>();

    Ipc(Application application) {
        this.application = application;

        builtin("apocrypha:app:info", request -> application.info());
        builtin("apocrypha:app:quit", request -> {
            application.quit();
            return null;
        });
        builtin("apocrypha:app:restart", request -> {
            application.restart();
            return null;
        });
        builtin("apocrypha:event:emit", this::emitToJava);
        builtin("apocrypha:window:setTitle", request -> {
            window(request).title(request.string(0));
            return null;
        });
        builtin("apocrypha:window:documentTitle", request -> {
            Window window = request.window();
            if (window != null) {
                window.documentTitle(request.string(0));
            }
            return null;
        });
        builtin("apocrypha:window:show", request -> {
            window(request).show();
            return null;
        });
        builtin("apocrypha:window:hide", request -> {
            window(request).hide();
            return null;
        });
        builtin("apocrypha:window:minimize", request -> {
            window(request).minimize();
            return null;
        });
        builtin("apocrypha:window:maximize", request -> {
            window(request).maximize();
            return null;
        });
        builtin("apocrypha:window:unmaximize", request -> {
            window(request).unmaximize();
            return null;
        });
        builtin("apocrypha:window:isMaximized", request -> window(request).isMaximized());
        builtin("apocrypha:window:setSize", request -> {
            window(request).size(request.integer(0), request.integer(1));
            return null;
        });
        builtin("apocrypha:window:getSize", request -> {
            var size = window(request).size();
            Map<String, Integer> result = new LinkedHashMap<>();
            result.put("width", size.width);
            result.put("height", size.height);
            return result;
        });
        builtin("apocrypha:window:getBounds", request -> {
            Window window = window(request);
            Rectangle bounds = window.bounds();
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("x", bounds.x);
            result.put("y", bounds.y);
            result.put("width", bounds.width);
            result.put("height", bounds.height);
            result.put("maximized", window.isMaximized());
            return result;
        });
        builtin("apocrypha:window:setBounds", request -> {
            window(request).bounds(request.integer(0), request.integer(1), request.integer(2), request.integer(3));
            return null;
        });
        builtin("apocrypha:window:setPosition", request -> {
            window(request).position(request.integer(0), request.integer(1));
            return null;
        });
        builtin("apocrypha:window:center", request -> {
            window(request).center();
            return null;
        });
        builtin("apocrypha:window:setAlwaysOnTop", request -> {
            window(request).setAlwaysOnTop(request.bool(0));
            return null;
        });
        builtin("apocrypha:window:setResizable", request -> {
            window(request).setResizable(request.bool(0));
            return null;
        });
        builtin("apocrypha:window:reload", request -> {
            window(request).reload();
            return null;
        });
        builtin("apocrypha:window:load", request -> {
            window(request).load(request.string(0));
            return null;
        });
        builtin("apocrypha:window:openDevTools", request -> {
            window(request).openDevTools();
            return null;
        });
        builtin("apocrypha:window:openExternal", request -> {
            application.openExternal(request.string(0));
            return null;
        });
        builtin("apocrypha:window:close", request -> {
            window(request).close();
            return null;
        });
    }

    /**
     * Register a handler for an application channel.
     * 
     * @param channel A channel name. The {@code apocrypha:} prefix is reserved.
     * @param handler A handler.
     * @return This IPC bridge.
     */
    public Ipc handle(String channel, IpcHandler handler) {
        String name = requireChannel(channel);
        Objects.requireNonNull(handler, "handler");
        handlers.put(name, handler);
        return this;
    }

    /**
     * Remove an application channel.
     * 
     * @param channel A channel name.
     * @return This IPC bridge.
     */
    public Ipc remove(String channel) {
        String name = requireChannel(channel);
        handlers.remove(name);
        return this;
    }

    /**
     * Register every method of an object which is annotated with {@link IpcChannel}.
     * 
     * @param handler A handler object.
     * @return This IPC bridge.
     */
    public Ipc register(Object handler) {
        Objects.requireNonNull(handler, "handler");
        return register(handler, annotatedMethods(handler.getClass()));
    }

    /**
     * Instantiate a class and register every method which is annotated with {@link IpcChannel}.
     * <p>
     * A class containing instance methods must provide a no-argument constructor. Classes with only
     * static annotated methods are not instantiated.
     * 
     * @param type A handler class.
     * @return This IPC bridge.
     */
    public Ipc register(Class<?> type) {
        Objects.requireNonNull(type, "type");

        List<Method> methods = annotatedMethods(type);
        boolean needsInstance = methods.stream().anyMatch(method -> !Modifier.isStatic(method.getModifiers()));

        Object instance = null;
        if (needsInstance) {
            try {
                Constructor<?> constructor = type.getDeclaredConstructor();
                constructor.setAccessible(true);
                instance = constructor.newInstance();
            } catch (ReflectiveOperationException | RuntimeException error) {
                throw new IllegalArgumentException("Cannot instantiate IPC handler " + type.getName()
                        + ". Register an instance instead of the class.", error);
            }
        }

        return register(instance, methods);
    }

    private Ipc register(Object instance, List<Method> methods) {
        for (Method method : methods) {
            IpcChannel annotation = method.getAnnotation(IpcChannel.class);
            String channel = annotation.value().isBlank() ? method.getName() : annotation.value();
            if (!Modifier.isStatic(method.getModifiers()) && instance == null) {
                throw new IllegalArgumentException("No instance is registered for IPC method " + method);
            }
            handle(channel, new IpcMethodHandler(instance, method));
        }
        return this;
    }

    private static List<Method> annotatedMethods(Class<?> type) {
        List<Method> methods = new ArrayList<>();
        collectMethods(type, methods, new HashSet<>());
        return methods;
    }

    private static void collectMethods(Class<?> type, List<Method> methods, Set<String> signatures) {
        if (type == null || type == Object.class) {
            return;
        }

        for (Method method : type.getDeclaredMethods()) {
            if (method.isAnnotationPresent(IpcChannel.class)) {
                String signature = method.getName() + Arrays.toString(method.getParameterTypes());
                if (signatures.add(signature)) {
                    methods.add(method);
                }
            }
        }

        for (Class<?> typeInterface : type.getInterfaces()) {
            collectMethods(typeInterface, methods, signatures);
        }
        collectMethods(type.getSuperclass(), methods, signatures);
    }

    /**
     * Register a listener for events emitted from the renderer.
     * 
     * @param event An event name.
     * @param listener A listener.
     * @return This IPC bridge.
     */
    public Ipc on(String event, Consumer<JSON> listener) {
        Objects.requireNonNull(event, "event");
        Objects.requireNonNull(listener, "listener");
        listeners.computeIfAbsent(event, key -> new CopyOnWriteArrayList<>()).add(listener);
        return this;
    }

    /**
     * Remove an event listener.
     * 
     * @param event An event name.
     * @param listener A listener.
     * @return This IPC bridge.
     */
    public Ipc off(String event, Consumer<JSON> listener) {
        Objects.requireNonNull(event, "event");
        Objects.requireNonNull(listener, "listener");
        List<Consumer<JSON>> list = listeners.get(event);
        if (list != null) {
            list.remove(listener);
            if (list.isEmpty()) {
                listeners.remove(event);
            }
        }
        return this;
    }

    /**
     * Emit an event to every open window.
     * 
     * @param event An event name.
     * @param payload A JSON serializable payload.
     */
    public void emit(String event, Object payload) {
        Objects.requireNonNull(event, "event");
        for (Window window : application.windows()) {
            window.emit(event, payload);
        }
    }

    /**
     * Dispatch a request from the renderer.
     * 
     * @param window The source window.
     * @param channel The channel.
     * @param arguments The arguments.
     * @param id The renderer query id.
     * @param callback The response callback.
     */
    void dispatch(Window window, String channel, List<String> arguments, long id, Consumer<String> callback) {
        IpcHandler handler = handlers.get(channel);
        if (handler == null) {
            respond(callback, response(id, "ENOENT", "No IPC handler is registered for channel: " + channel, null));
            return;
        }

        executor.execute(() -> {
            try {
                IpcRequest request = IpcRequest.create(window, channel, arguments);
                Object result = await(handler.handle(request));
                respond(callback, success(id, result));
            } catch (Throwable error) {
                respond(callback, response(id, error));
            }
        });
    }

    private void respond(Consumer<String> callback, String response) {
        try {
            callback.accept(response);
        } catch (Throwable error) {
            I.debug("apocrypha", new RuntimeException("Failed to deliver an IPC response", error));
        }
    }

    /**
     * Stop accepting work and release the worker threads.
     */
    void close() {
        executor.shutdownNow();
        handlers.clear();
        listeners.clear();
    }

    private void builtin(String channel, IpcHandler handler) {
        handlers.put(channel, handler);
    }

    private Object emitToJava(IpcRequest request) {
        String event = request.string(0);
        if (event == null || event.isBlank()) {
            throw new IpcException("EINVAL", "An event name is required");
        }

        JSON payload = request.size() > 1 ? request.json(1) : null;
        List<Consumer<JSON>> list = listeners.get(event);
        if (list != null) {
            for (Consumer<JSON> listener : list) {
                try {
                    listener.accept(payload);
                } catch (Throwable error) {
                    I.warn("apocrypha", new RuntimeException("IPC event listener failed: " + event, error));
                }
            }
        }
        return null;
    }

    private Window window(IpcRequest request) {
        Window window = request.window();
        if (window == null) {
            throw new IpcException("EINVAL", "This IPC channel requires a window");
        }
        return window;
    }

    private Object await(Object value) throws Exception {
        if (value instanceof CompletionStage<?> stage) {
            return stage.toCompletableFuture().join();
        }
        if (value instanceof Future<?> future) {
            return future.get();
        }
        return value;
    }

    private String success(long id, Object value) {
        return "{\"id\":" + id + ",\"ok\":true,\"result\":" + IpcCodec.write(value) + "}";
    }

    private String response(long id, Throwable error) {
        Throwable cause = unwrap(error);
        String code = "EINTERNAL";
        if (cause instanceof IpcException ipc) {
            code = ipc.code();
        } else if (cause instanceof IllegalArgumentException) {
            code = "EINVAL";
        }
        String message = cause.getMessage();
        if (message == null || message.isBlank()) {
            message = cause.getClass().getSimpleName();
        }
        return response(id, code, message, cause.getClass().getSimpleName());
    }

    private String response(long id, String code, String message, String type) {
        StringBuilder response = new StringBuilder();
        response.append("{\"id\":").append(id).append(",\"ok\":false,\"error\":{\"code\":").append(I.write(code))
                .append(",\"message\":").append(I.write(message));
        if (type != null) {
            response.append(",\"type\":").append(I.write(type));
        }
        response.append("}}");
        return response.toString();
    }

    private static Throwable unwrap(Throwable error) {
        Throwable current = error;
        while ((current instanceof CompletionException || current instanceof ExecutionException)
                && current.getCause() != null) {
            current = current.getCause();
        }
        return current;
    }

    private static String requireChannel(String channel) {
        Objects.requireNonNull(channel, "channel");
        if (channel.isBlank()) {
            throw new IllegalArgumentException("channel must not be blank");
        }
        if (channel.startsWith(RESERVED)) {
            throw new IllegalArgumentException("The " + RESERVED + " namespace is reserved by the framework");
        }
        return channel;
    }

    @Override
    public String toString() {
        return "Ipc [handlers=" + handlers.size() + "]";
    }
}
