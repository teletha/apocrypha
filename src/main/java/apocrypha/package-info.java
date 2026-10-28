/*
 * Copyright (C) 2026 The APOCRYPHA Development Team
 *
 * Licensed under the MIT License (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *          https://opensource.org/licenses/MIT
 */

/**
 * Apocrypha is an Electron-style desktop application framework for Java.
 * <p>
 * The Java side owns the application lifecycle, native windows, and the IPC handlers. The user
 * interface is a standard web application rendered by the platform webview through avaje-webview. A
 * small preload bridge exposes {@code window.apocrypha} to the renderer:
 *
 * <pre>{@code
 * const message = await apocrypha.invoke('notes:save', 'Hello');
 * apocrypha.on('notes:loaded', payload => console.log(payload.text));
 * }</pre>
 *
 * The entry point is {@link apocrypha.Application}. Frontend files are served from the classpath
 * through a loopback HTTP server, so the same static build works in development and in a packaged
 * jar. IPC payloads may be Java model classes; they are mapped to JSON by Sinobu and can be
 * registered with {@link apocrypha.IpcChannel} annotations.
 */
package apocrypha;
