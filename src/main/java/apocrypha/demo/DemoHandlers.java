/*
 * Copyright (C) 2026 The APOCRYPHA Development Team
 *
 * Licensed under the MIT License (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *          https://opensource.org/licenses/MIT
 */
package apocrypha.demo;

import java.time.Instant;

import apocrypha.IpcChannel;
import apocrypha.IpcException;

/**
 * Annotated IPC handlers for the demo application.
 * <p>
 * {@code ipc().register(new DemoHandlers())} discovers these methods automatically.
 */
public class DemoHandlers {

    /**
     * Greet a renderer. The request and response are model records which Sinobu maps to JSON.
     * 
     * @param request A greeting request.
     * @return A greeting response.
     */
    @IpcChannel("demo:greet")
    public Greeting greet(GreetRequest request) {
        String name = request.name() == null || request.name().isBlank() ? "world" : request.name();
        return new Greeting("Hello, " + name + "!", Instant.now().toString(),
                Thread.currentThread().toString());
    }

    /**
     * Add two numbers. Each method parameter consumes one positional IPC argument.
     * 
     * @param a The first number.
     * @param b The second number.
     * @return The sum.
     */
    @IpcChannel("demo:sum")
    public long sum(long a, long b) {
        return a + b;
    }

    /**
     * Demonstrate a typed error.
     */
    @IpcChannel("demo:fail")
    public void fail() {
        throw new IpcException("EDEMO", "This error was created on the Java side.");
    }
}
