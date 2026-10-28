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

import java.util.Map;

import kiss.I;
import kiss.JSON;

/**
 * Serializes and deserializes the values exchanged by IPC.
 * <p>
 * Java model classes are handled by Sinobu ({@link I#write(Object)} and
 * {@link I#json(String, Class)}). Maps, iterables, and arrays are emitted as JSON containers
 * recursively, so a handler may return a model, a collection of models, or a plain value.
 */
final class IpcCodec {

    private IpcCodec() {
    }

    /**
     * Write a Java value as JSON text.
     * 
     * @param value A value.
     * @return JSON text.
     */
    static String write(Object value) {
        StringBuilder out = new StringBuilder();
        write(value, out);
        return out.toString();
    }

    /**
     * Read JSON text as the requested type.
     * 
     * @param <T> The target type.
     * @param json JSON text.
     * @param type A target class.
     * @return A decoded value.
     */
    static <T> T decode(String json, Class<T> type) {
        if (type == JSON.class) {
            return type.cast(I.json(json));
        }
        if (type == Object.class) {
            // Object is the raw JSON escape hatch. Declare JSON instead when the shape matters.
            return type.cast(I.json(json));
        }
        if (type.isArray()) {
            throw new IpcException("EUNSUPPORTED",
                    "Array parameters are not supported; use a model class which contains a List field");
        }
        if (type == String.class) {
            return type.cast(I.json(json, String.class));
        }
        if (type.isPrimitive() || type.isEnum() || Number.class.isAssignableFrom(type) || type == Boolean.class
                || type == Character.class) {
            @SuppressWarnings("unchecked")
            T value = (T) I.transform(I.json(json, String.class), type);
            return value;
        }
        return I.json(json, type);
    }

    private static void write(Object value, StringBuilder out) {
        if (value == null) {
            out.append("null");
        } else if (value instanceof JSON json) {
            out.append(json.toString());
        } else if (value instanceof Map<?, ?> map) {
            out.append('{');
            boolean first = true;
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (!first) {
                    out.append(',');
                }
                first = false;
                out.append(I.write(String.valueOf(entry.getKey()))).append(':');
                write(entry.getValue(), out);
            }
            out.append('}');
        } else if (value instanceof Iterable<?> iterable) {
            out.append('[');
            boolean first = true;
            for (Object item : iterable) {
                if (!first) {
                    out.append(',');
                }
                first = false;
                write(item, out);
            }
            out.append(']');
        } else if (value.getClass().isArray()) {
            out.append('[');
            int length = java.lang.reflect.Array.getLength(value);
            for (int i = 0; i < length; i++) {
                if (i != 0) {
                    out.append(',');
                }
                write(java.lang.reflect.Array.get(value, i), out);
            }
            out.append(']');
        } else {
            out.append(I.write(value));
        }
    }
}
