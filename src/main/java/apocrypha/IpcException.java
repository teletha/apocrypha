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

/**
 * An exception which carries a stable error code to the JavaScript caller.
 * <p>
 * Any other exception is reported with the {@code EINTERNAL} code.
 */
public class IpcException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final String code;

    /**
     * Create an exception with the {@code EINTERNAL} code.
     * 
     * @param message An error message.
     */
    public IpcException(String message) {
        this("EINTERNAL", message, null);
    }

    /**
     * Create an exception with a stable code.
     * 
     * @param code A stable error code.
     * @param message An error message.
     */
    public IpcException(String code, String message) {
        this(code, message, null);
    }

    /**
     * Create an exception with a stable code and a cause.
     * 
     * @param code A stable error code.
     * @param message An error message.
     * @param cause The cause.
     */
    public IpcException(String code, String message, Throwable cause) {
        super(message, cause);
        this.code = code == null || code.isBlank() ? "EINTERNAL" : code;
    }

    /**
     * The stable error code delivered to the renderer.
     * 
     * @return An error code.
     */
    public String code() {
        return code;
    }
}
