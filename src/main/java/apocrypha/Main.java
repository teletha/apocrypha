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

import apocrypha.demo.DemoHandlers;

import kiss.I;

/**
 * A small demonstration application. It also serves as the executable entry point for the project.
 */
public class Main extends Application {

    /**
     * Launch the demonstration application.
     * 
     * @param args Command line arguments.
     */
    public static void main(String[] args) {
        Application.launch(Main.class, args);
    }

    @Override
    protected void configure(ApplicationConfig config) {
        config.name("Apocrypha").version(FRAMEWORK_VERSION).resourceRoot("demo").entry("index.html").spaFallback(true)
                .devTools(true);
    }

    @Override
    protected void onStart() {
        ipc().register(new DemoHandlers());

        ipc().on("demo:log", payload -> {
            I.info("apocrypha", "[renderer] " + payload);
            ipc().emit("demo:logged", java.util.Map.of("text", payload.text("text")));
        });

        Window window = createWindow();
        window.id("main").title("Apocrypha").size(1180, 780).show();
        window.emit("demo:server", java.util.Map
                .of("message", "The Java event channel is live.", "timestamp", java.time.Instant.now().toString()));
    }

    @Override
    protected void onExit() {
        I.info("apocrypha", "The demo application is exiting.");
    }
}
