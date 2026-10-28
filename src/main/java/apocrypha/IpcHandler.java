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
 * Handles one IPC channel.
 * <p>
 * The handler is invoked on a virtual thread, so it may block or perform IO without freezing the
 * browser UI. A returned {@link java.util.concurrent.CompletionStage} or
 * {@link java.util.concurrent.Future} is awaited before the response is sent.
 */
@FunctionalInterface
public interface IpcHandler {

    /**
     * Handle an IPC request.
     * 
     * @param request The request sent by the renderer.
     * @return A value serialized to JSON, or a future of it.
     * @throws Exception If the request cannot be handled.
     */
    Object handle(IpcRequest request) throws Exception;
}
