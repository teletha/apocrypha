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
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import kiss.I;
import kiss.JSON;

class IpcAnnotationTest {

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
    void registerInstanceAndModels() throws Exception {
        ipc.register(new Handler());

        JSON response = call("test:person", new Person("Alice", 20));
        JSON result = response.get("result");

        assertEquals("Hello Alice", result.text("message"));
        assertEquals(21, result.get(int.class, "age"));
    }

    @Test
    void defaultChannelName() throws Exception {
        ipc.register(new Handler());

        assertEquals(42L, call("add", 20, 22).get(long.class, "result"));
    }

    @Test
    void injectRequestAndWindow() throws Exception {
        ipc.register(new Handler());

        assertEquals("test:request", call("test:request").text("result"));
        assertTrue(call("test:window").get(boolean.class, "result"));
    }

    @Test
    void rawJsonParameter() throws Exception {
        ipc.register(new Handler());

        assertEquals("Alice", call("test:json", Map.of("name", "Alice")).text("result"));
    }

    @Test
    void asyncModel() throws Exception {
        ipc.register(new Handler());

        assertEquals("ALICE", call("test:async", new Person("Alice", 20)).get("result").text("name"));
    }

    @Test
    void annotatedError() throws Exception {
        ipc.register(new Handler());

        JSON error = call("test:error").get("error");
        assertEquals("E42", error.text("code"));
        assertEquals("boom", error.text("message"));
    }

    @Test
    void registerClassWithConstructor() throws Exception {
        ipc.register(InstanceHandler.class);

        assertEquals("instance", call("test:instance").text("result"));
    }

    @Test
    void registerStaticClass() throws Exception {
        ipc.register(StaticHandler.class);

        assertEquals(7, call("test:static").get(int.class, "result"));
    }

    @Test
    void registerPrivateMembers() throws Exception {
        ipc.register(PrivateHandler.class);

        assertEquals("private", call("test:private").text("result"));
    }

    @Test
    void genericParameterIsRejectedClearly() throws Exception {
        ipc.register(new GenericHandler());

        JSON error = call("test:generic", List.of(new Person("Alice", 20))).get("error");
        assertEquals("EUNSUPPORTED", error.text("code"));
    }

    @Test
    void reservedAnnotatedChannelIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> ipc.register(new ReservedHandler()));
    }

    private JSON call(String channel, Object... arguments) throws Exception {
        List<String> encoded = new ArrayList<>();
        for (Object argument : arguments) {
            encoded.add(IpcCodec.write(argument));
        }

        Callback callback = new Callback();
        ipc.dispatch(null, channel, encoded, 1, callback);
        assertTrue(callback.latch.await(5, TimeUnit.SECONDS), "IPC did not complete");
        return callback.response();
    }

    public record Person(String name, int age) {
    }

    public record Greeting(String message, int age) {
    }

    public static class Handler {

        @IpcChannel("test:person")
        public Greeting person(Person person) {
            return new Greeting("Hello " + person.name(), person.age() + 1);
        }

        @IpcChannel
        public long add(long a, long b) {
            return a + b;
        }

        @IpcChannel("test:request")
        public String request(IpcRequest request) {
            return request.channel();
        }

        @IpcChannel("test:window")
        public boolean window(Window window) {
            return window == null;
        }

        @IpcChannel("test:json")
        public String json(JSON json) {
            return json.text("name");
        }

        @IpcChannel("test:async")
        public CompletableFuture<Person> async(Person person) {
            return CompletableFuture.completedFuture(new Person(person.name().toUpperCase(), person.age()));
        }

        @IpcChannel("test:error")
        public void error() {
            throw new IpcException("E42", "boom");
        }
    }

    public static class InstanceHandler {

        @IpcChannel("test:instance")
        public String instance() {
            return "instance";
        }
    }

    public static class StaticHandler {

        @IpcChannel("test:static")
        public static int value() {
            return 7;
        }
    }

    public static class PrivateHandler {

        private PrivateHandler() {
        }

        @IpcChannel("test:private")
        private String value() {
            return "private";
        }
    }

    public static class GenericHandler {

        @IpcChannel("test:generic")
        public int size(List<Person> people) {
            return people.size();
        }
    }

    public static class ReservedHandler {

        @IpcChannel("apocrypha:reserved")
        public void reserved() {
        }
    }

    private static class TestApplication extends Application {
    }

    private static class Callback implements java.util.function.Consumer<String> {

        private final CountDownLatch latch = new CountDownLatch(1);

        private volatile String response;

        @Override
        public void accept(String response) {
            this.response = response;
            latch.countDown();
        }

        private JSON response() {
            assertNotNull(response, "No IPC response was delivered");
            return I.json(response);
        }
    }
}
