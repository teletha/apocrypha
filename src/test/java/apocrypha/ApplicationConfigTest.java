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

import java.nio.file.Path;

import com.formdev.flatlaf.FlatDarkLaf;
import com.formdev.flatlaf.FlatLightLaf;

import org.junit.jupiter.api.Test;

class ApplicationConfigTest {

    @Test
    void defaults() {
        ApplicationConfig config = new ApplicationConfig();

        assertEquals("Apocrypha", config.name());
        assertEquals("dev", config.version());
        assertEquals("apocrypha", config.scheme());
        assertEquals("app", config.host());
        assertEquals("web", config.resourceRoot());
        assertEquals("index.html", config.entry());
        assertTrue(config.restoreWindowState());
        assertEquals("apocrypha://app/", config.baseUrl());
        assertEquals("apocrypha://app/index.html", config.entryUrl());
        assertEquals("apocrypha://app/static/app.js", config.url("static/app.js"));
        assertEquals("apocrypha://app/static/app.js", config.url("/static/app.js"));
        assertEquals("https://example.com/", config.url("https://example.com/"));
        assertEquals(Path.of(System.getProperty("user.home"), ".apocrypha", "apocrypha").toAbsolutePath(),
                config.dataDirectory());
    }

    @Test
    void customize() {
        ApplicationConfig config = new ApplicationConfig().name("My Notes").version("1.2.3").scheme("desk")
                .host("local").resourceRoot("/assets/frontend/").entry("main.html");

        assertEquals("My Notes", config.name());
        assertEquals("desk://local/main.html", config.entryUrl());
        assertEquals("assets/frontend", config.resourceRoot());
        assertEquals(Path.of(System.getProperty("user.home"), ".apocrypha", "my-notes").toAbsolutePath(),
                config.dataDirectory());
    }

    @Test
    void windowState() {
        ApplicationConfig config = new ApplicationConfig();
        assertTrue(config.restoreWindowState());
        assertSame(config, config.restoreWindowState(false));
        assertFalse(config.restoreWindowState());
    }

    @Test
    void theme() {
        ApplicationConfig config = new ApplicationConfig();
        assertInstanceOf(FlatDarkLaf.class, config.theme());

        FlatLightLaf light = new FlatLightLaf();
        assertSame(config, config.theme(light));
        assertSame(light, config.theme());
        assertThrows(NullPointerException.class, () -> config.theme(null));
    }

    @Test
    void absoluteEntry() {
        ApplicationConfig config = new ApplicationConfig().entry("http://localhost:5173/");
        assertEquals("http://localhost:5173/", config.entryUrl());
    }

    @Test
    void invalidValues() {
        ApplicationConfig config = new ApplicationConfig();
        assertThrows(IllegalArgumentException.class, () -> config.scheme("1invalid"));
        assertThrows(IllegalArgumentException.class, () -> config.scheme("with space"));
        assertThrows(IllegalArgumentException.class, () -> config.host("a/b"));
        assertThrows(IllegalArgumentException.class, () -> config.host("a:b"));
        assertThrows(IllegalArgumentException.class, () -> config.resourceRoot("../secret"));
        assertThrows(IllegalArgumentException.class, () -> config.name(" "));
        assertThrows(IllegalArgumentException.class, () -> config.entry(""));
    }

    @Test
    void slug() {
        assertEquals("my-notes", ApplicationConfig.slug("My Notes"));
        assertEquals("a-b", ApplicationConfig.slug("  A / B  "));
        assertEquals("application", ApplicationConfig.slug("!!!"));
    }

    @Test
    void cefArguments() {
        ApplicationConfig config = new ApplicationConfig().cefArguments("--disable-gpu", " ", null, "--in-process-gpu");
        assertEquals(2, config.cefArguments().size());
        assertThrows(UnsupportedOperationException.class, () -> config.cefArguments().add("--x"));
    }
}
