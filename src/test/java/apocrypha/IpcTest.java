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

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.cef.callback.CefQueryCallback;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import kiss.I;
import kiss.JSON;

class IpcTest {

    private Ipc ipc;

    @BeforeEach
    void setup() {
        ipc = new Ipc(new TestApplication());
    }

    @AfterEach
    void cleanup() {
        ipc.close();
    }

    @Test
    void success() throws Exception {
        ipc.handle("greet", request -> "Hello " + request.string(0));

        Callback callback = dispatch("greet", "world");
        JSON response = callback.response();

        assertTrue(response.get(boolean.class, "ok"));
        assertEquals("Hello world", response.text("result"));
    }

    @Test
    void jsonArguments() throws Exception {
        ipc.handle("sum", request -> request.integer(0) + request.integer(1));

        Callback callback = dispatch("sum", 20, 22);
        assertEquals(42, callback.response().get(int.class, "result"));
    }

    @Test
    void typedError() throws Exception {
        ipc.handle("fail", request -> {
            throw new IpcException("E42", "The answer is missing");
        });

        Callback callback = dispatch("fail");
        JSON error = callback.response().get("error");

        assertEquals("E42", error.text("code"));
        assertEquals("The answer is missing", error.text("message"));
    }

    @Test
    void unknownChannel() throws Exception {
        Callback callback = dispatch("missing");
        JSON error = callback.response().get("error");

        assertEquals("ENOENT", error.text("code"));
        assertTrue(error.text("message").contains("missing"));
    }

    @Test
    void reservedChannel() {
        assertThrows(IllegalArgumentException.class, () -> ipc.handle("apocrypha:window:close", request -> null));
    }

    @Test
    void rendererEvent() throws Exception {
        AtomicReference<JSON> received = new AtomicReference<>();
        ipc.on("ping", received::set);

        Callback callback = dispatch("apocrypha:event:emit", "ping", "payload");

        assertTrue(callback.response().get(boolean.class, "ok"));
        assertEquals("payload", received.get().as(String.class));
    }

    @Test
    void asyncResult() throws Exception {
        ipc.handle("later", request -> java.util.concurrent.CompletableFuture.supplyAsync(() -> "done"));

        Callback callback = dispatch("later");
        assertEquals("done", callback.response().text("result"));
    }

    private Callback dispatch(String channel, Object... arguments) throws InterruptedException {
        List<String> encoded = new ArrayList<>();
        for (Object argument : arguments) {
            encoded.add(IpcCodec.write(argument));
        }

        Callback callback = new Callback();
        ipc.dispatch(null, channel, encoded, 1, callback);
        assertTrue(callback.latch.await(5, TimeUnit.SECONDS), "IPC did not complete");
        return callback;
    }

    private static class TestApplication extends Application {
    }

    private static class Callback implements CefQueryCallback {

        private final CountDownLatch latch = new CountDownLatch(1);

        private volatile String response;

        private volatile int code;

        private volatile String message;

        @Override
        public void success(String response) {
            this.response = response;
            latch.countDown();
        }

        @Override
        public void failure(int code, String message) {
            this.code = code;
            this.message = message;
            latch.countDown();
        }

        private JSON response() {
            assertNull(message, "Transport failure: " + message);
            assertEquals(0, code);
            return I.json(response);
        }
    }
}
