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

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Parameter;
import java.lang.reflect.Type;

/**
 * Adapts an annotated Java method to an {@link IpcHandler}.
 */
final class IpcMethodHandler implements IpcHandler {

    private final Object target;

    private final Method method;

    private final Parameter[] parameters;

    IpcMethodHandler(Object target, Method method) {
        this.target = target;
        this.method = method;
        this.parameters = method.getParameters();
        method.setAccessible(true);
    }

    @Override
    public Object handle(IpcRequest request) throws Exception {
        Object[] arguments = new Object[parameters.length];
        int index = 0;

        for (int i = 0; i < parameters.length; i++) {
            Class<?> type = parameters[i].getType();
            if (IpcRequest.class.isAssignableFrom(type)) {
                arguments[i] = request;
            } else if (Window.class.isAssignableFrom(type)) {
                arguments[i] = request.window();
            } else {
                if (index >= request.size()) {
                    throw new IpcException("EINVAL",
                            "Missing argument " + index + " for channel " + request.channel() + " ("
                                    + method.getName() + ")");
                }
                Type parameterType = parameters[i].getParameterizedType();
                if (!(parameterType instanceof Class<?> typeClass)) {
                    throw new IpcException("EUNSUPPORTED", "Generic parameter " + parameterType
                            + " is not supported; use a model class which contains the generic field");
                }
                arguments[i] = request.as(index++, typeClass);
            }
        }

        try {
            Object receiver = Modifier.isStatic(method.getModifiers()) ? null : target;
            if (receiver == null && !Modifier.isStatic(method.getModifiers())) {
                throw new IpcException("EINTERNAL", "No instance is registered for " + method);
            }
            return method.invoke(receiver, arguments);
        } catch (InvocationTargetException error) {
            Throwable cause = error.getCause();
            if (cause instanceof Exception exception) {
                throw exception;
            }
            if (cause instanceof Error fatal) {
                throw fatal;
            }
            throw new IpcException("EINTERNAL", cause == null ? error.getMessage() : cause.getMessage(), cause);
        } catch (IllegalArgumentException error) {
            throw new IpcException("EINVAL", "Invalid argument for channel " + request.channel() + " ("
                    + method.getName() + ")", error);
        } catch (IllegalAccessException error) {
            throw new IpcException("EINTERNAL", "Cannot access IPC method " + method, error);
        }
    }

    @Override
    public String toString() {
        return "IpcMethodHandler [method=" + method + "]";
    }
}
