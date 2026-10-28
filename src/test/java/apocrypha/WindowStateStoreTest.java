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

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class WindowStateStoreTest {

    @TempDir
    Path temp;

    @Test
    void roundTrip() {
        Path file = temp.resolve("window-state.json");
        WindowStateStore store = new WindowStateStore(file);
        assertNull(store.get("main"));

        store.putAll(Map.of("main", new WindowStateStore.State(10, 20, 800, 600, true)));

        WindowStateStore loaded = new WindowStateStore(file);
        WindowStateStore.State state = loaded.get("main");
        assertNotNull(state);
        assertEquals(10, state.x);
        assertEquals(20, state.y);
        assertEquals(800, state.width);
        assertEquals(600, state.height);
        assertTrue(state.maximized);
        assertTrue(Files.exists(file));
        assertFalse(Files.exists(file.resolveSibling("window-state.json.tmp")));
    }

    @Test
    void mergeKeepsOtherWindows() {
        Path file = temp.resolve("window-state.json");
        WindowStateStore store = new WindowStateStore(file);

        store.putAll(Map.of("main", new WindowStateStore.State(1, 2, 300, 200, false)));
        store.putAll(Map.of("window-2", new WindowStateStore.State(3, 4, 500, 400, true)));

        WindowStateStore loaded = new WindowStateStore(file);
        assertNotNull(loaded.get("main"));
        assertNotNull(loaded.get("window-2"));
        assertTrue(loaded.get("window-2").maximized);
    }

    @Test
    void invalidStateIsIgnored() {
        Path file = temp.resolve("window-state.json");
        WindowStateStore store = new WindowStateStore(file);

        store.putAll(Map.of("broken", new WindowStateStore.State(0, 0, 0, -1, false)));

        assertNull(new WindowStateStore(file).get("broken"));
    }

    @Test
    void corruptFileStartsEmpty() throws IOException {
        Path file = temp.resolve("window-state.json");
        Files.createDirectories(temp);
        Files.writeString(file, "{not valid json", StandardCharsets.UTF_8);

        WindowStateStore store = new WindowStateStore(file);
        assertNull(store.get("main"));

        // A subsequent save must repair the file.
        store.putAll(Map.of("main", new WindowStateStore.State(1, 2, 300, 200, false)));
        assertNotNull(new WindowStateStore(file).get("main"));
    }

    @Test
    void stateCopiesAreDefensive() {
        WindowStateStore.State original = new WindowStateStore.State(1, 2, 300, 200, false);
        WindowStateStore.State copy = original.copy();
        original.width = 999;
        assertEquals(300, copy.width);
    }
}
