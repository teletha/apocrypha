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

import java.nio.charset.StandardCharsets;

import org.cef.callback.CefCallback;
import org.cef.callback.CefResourceReadCallback;
import org.cef.callback.CefResourceSkipCallback;
import org.cef.handler.CefResourceHandlerAdapter;
import org.cef.misc.BoolRef;
import org.cef.misc.IntRef;
import org.cef.misc.LongRef;
import org.cef.misc.StringRef;
import org.cef.network.CefRequest;
import org.cef.network.CefResponse;

/**
 * Serves a classpath resource for one application scheme request.
 */
final class AppResourceHandler extends CefResourceHandlerAdapter {

    private final ResourceLoader loader;

    private volatile Resource resource;

    private volatile int position;

    AppResourceHandler(ResourceLoader loader) {
        this.loader = loader;
    }

    @Override
    public boolean processRequest(CefRequest request, CefCallback callback) {
        prepare(request);
        callback.Continue();
        return true;
    }

    @Override
    public boolean open(CefRequest request, BoolRef handleRequest, CefCallback callback) {
        prepare(request);
        handleRequest.set(true);
        return true;
    }

    private void prepare(CefRequest request) {
        this.resource = loader.load(request.getURL());
        this.position = 0;
    }

    @Override
    public void getResponseHeaders(CefResponse response, IntRef responseLength, StringRef redirectUrl) {
        Resource current = resource;
        if (current == null) {
            byte[] body = "The application resource is not available.".getBytes(StandardCharsets.UTF_8);
            response.setStatus(500);
            response.setStatusText("Internal Server Error");
            response.setMimeType("text/plain");
            response.setHeaderByName("Content-Type", "text/plain; charset=utf-8", true);
            responseLength.set(body.length);
            resource = new Resource(500, "text/plain; charset=utf-8", body);
            return;
        }

        String mime = current.mimeType();
        int charset = mime.indexOf(';');
        response.setStatus(current.status());
        response.setStatusText(current.status() == 200 ? "OK" : current.status() == 404 ? "Not Found" : "Error");
        response.setMimeType(charset < 0 ? mime : mime.substring(0, charset).trim());
        response.setHeaderByName("Content-Type", mime, true);
        response.setHeaderByName("Cache-Control", "no-cache", true);
        response.setHeaderByName("X-Content-Type-Options", "nosniff", true);
        responseLength.set(current.data().length);
    }

    @Override
    public boolean readResponse(byte[] dataOut, int bytesToRead, IntRef bytesRead, CefCallback callback) {
        return readData(dataOut, bytesToRead, bytesRead);
    }

    @Override
    public boolean read(byte[] dataOut, int bytesToRead, IntRef bytesRead, CefResourceReadCallback callback) {
        return readData(dataOut, bytesToRead, bytesRead);
    }

    private boolean readData(byte[] dataOut, int bytesToRead, IntRef bytesRead) {
        Resource current = resource;
        if (current == null) {
            bytesRead.set(0);
            return false;
        }

        byte[] data = current.data();
        int remaining = data.length - position;
        if (remaining <= 0 || bytesToRead <= 0) {
            bytesRead.set(0);
            return false;
        }

        int length = Math.min(remaining, bytesToRead);
        System.arraycopy(data, position, dataOut, 0, length);
        position += length;
        bytesRead.set(length);
        return true;
    }

    @Override
    public boolean skip(long bytesToSkip, LongRef bytesSkipped, CefResourceSkipCallback callback) {
        Resource current = resource;
        if (current == null || bytesToSkip <= 0) {
            bytesSkipped.set(0);
            return false;
        }

        long remaining = current.data().length - position;
        long skipped = Math.min(remaining, bytesToSkip);
        position += (int) skipped;
        bytesSkipped.set(skipped);
        return skipped > 0;
    }

    @Override
    public void cancel() {
        resource = null;
    }
}
