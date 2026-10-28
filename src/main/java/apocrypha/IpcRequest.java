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

import java.util.ArrayList;
import java.util.List;

import kiss.I;
import kiss.JSON;

/**
 * A request which was sent from the renderer through {@code window.apocrypha.invoke(...)}.
 * <p>
 * Positional arguments are stored as their JSON text. Use {@link #as(int, Class)} to decode a model
 * class with Sinobu ({@code I.json}), or {@link #json(int)} to work with the raw {@link JSON}.
 */
public final class IpcRequest {

    private final Window window;

    private final String channel;

    private final List<String> arguments;

    IpcRequest(Window window, String channel, List<String> arguments) {
        this.window = window;
        this.channel = channel;

        if (arguments == null) {
            this.arguments = List.of();
        } else {
            List<String> copy = new ArrayList<>(arguments.size());
            for (String argument : arguments) {
                copy.add(argument == null ? "null" : argument);
            }
            this.arguments = List.copyOf(copy);
        }
    }

    /**
     * The window which sent the request.
     * 
     * @return A window.
     */
    public Window window() {
        return window;
    }

    /**
     * The IPC channel.
     * 
     * @return A channel name.
     */
    public String channel() {
        return channel;
    }

    /**
     * The number of positional arguments.
     * 
     * @return An argument count.
     */
    public int size() {
        return arguments.size();
    }

    /**
     * Whether the request has no positional arguments.
     * 
     * @return True if there are no arguments.
     */
    public boolean isEmpty() {
        return arguments.isEmpty();
    }

    /**
     * Get the raw JSON text for an argument.
     * 
     * @param index A zero based index.
     * @return JSON text.
     */
    public String raw(int index) {
        if (index < 0 || index >= arguments.size()) {
            throw new IpcException("EINVAL", "Missing IPC argument at index " + index + " for channel " + channel);
        }
        return arguments.get(index);
    }

    /**
     * Get an argument as a parsed {@link JSON} value.
     * 
     * @param index A zero based index.
     * @return A JSON value.
     */
    public JSON json(int index) {
        return I.json(raw(index));
    }

    /**
     * Convert an argument into the requested model type.
     * <p>
     * This uses {@link I#json(String, Class)}. Records, beans with getter/setter pairs, and classes
     * with public fields are supported.
     * 
     * @param <T> The value type.
     * @param index A zero based index.
     * @param type A target type.
     * @return A converted value.
     */
    public <T> T as(int index, Class<T> type) {
        return IpcCodec.decode(raw(index), type);
    }

    /**
     * Get a string argument.
     * 
     * @param index A zero based index.
     * @return A string or {@code null}.
     */
    public String string(int index) {
        return IpcCodec.decode(raw(index), String.class);
    }

    /**
     * Get an integer argument.
     * 
     * @param index A zero based index.
     * @return An integer.
     */
    public int integer(int index) {
        return IpcCodec.decode(raw(index), int.class);
    }

    /**
     * Get a long argument.
     * 
     * @param index A zero based index.
     * @return A long.
     */
    public long longValue(int index) {
        return IpcCodec.decode(raw(index), long.class);
    }

    /**
     * Get a floating point argument.
     * 
     * @param index A zero based index.
     * @return A double.
     */
    public double decimal(int index) {
        return IpcCodec.decode(raw(index), double.class);
    }

    /**
     * Get a boolean argument.
     * 
     * @param index A zero based index.
     * @return A boolean.
     */
    public boolean bool(int index) {
        return IpcCodec.decode(raw(index), boolean.class);
    }

    /**
     * Create a request for a built-in channel. The window may be {@code null} in unit tests.
     * 
     * @param window A window.
     * @param channel A channel.
     * @param arguments JSON text for each positional argument.
     * @return A request.
     */
    static IpcRequest create(Window window, String channel, List<String> arguments) {
        return new IpcRequest(window, channel, arguments);
    }

    @Override
    public String toString() {
        return "IpcRequest [channel=" + channel + ", arguments=" + arguments + "]";
    }
}
