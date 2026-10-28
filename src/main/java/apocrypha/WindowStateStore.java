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

import java.awt.Rectangle;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashMap;
import java.util.Map;

import kiss.I;

/**
 * Reads and writes the persisted window state.
 * <p>
 * The file is small JSON. Writes go through a temporary file so a crash cannot leave a truncated
 * document behind.
 */
final class WindowStateStore {

    private static final int VERSION = 1;

    private final Path file;

    private final Map<String, State> states = new LinkedHashMap<>();

    WindowStateStore(Path file) {
        this.file = file.toAbsolutePath().normalize();
        read();
    }

    /**
     * The persisted state for a window id.
     * 
     * @param id A window id.
     * @return A copy of the state, or {@code null}.
     */
    synchronized State get(String id) {
        State state = id == null ? null : states.get(id);
        return state == null ? null : state.copy();
    }

    /**
     * Merge window states into the store and persist them.
     * 
     * @param values Window states keyed by window id.
     */
    synchronized void putAll(Map<String, State> values) {
        for (Map.Entry<String, State> entry : values.entrySet()) {
            String id = entry.getKey();
            State state = entry.getValue();
            if (id != null && !id.isBlank() && state != null && state.valid()) {
                states.put(id, state.copy());
            }
        }
        write();
    }

    /**
     * The backing file.
     * 
     * @return A path.
     */
    Path file() {
        return file;
    }

    private void read() {
        if (!Files.isRegularFile(file)) {
            return;
        }

        try {
            Snapshot snapshot = I.json(Files.readString(file, StandardCharsets.UTF_8), Snapshot.class);
            if (snapshot == null || snapshot.windows == null) {
                return;
            }
            for (Map.Entry<String, State> entry : snapshot.windows.entrySet()) {
                String id = entry.getKey();
                State state = entry.getValue();
                if (id != null && !id.isBlank() && state != null && state.valid()) {
                    states.put(id, state.copy());
                }
            }
        } catch (Exception error) {
            I.warn("apocrypha",
                    new RuntimeException("Failed to read window state: " + file + " (starting with defaults)", error));
            states.clear();
        }
    }

    private void write() {
        Snapshot snapshot = new Snapshot();
        snapshot.version = VERSION;
        snapshot.windows = new LinkedHashMap<>();
        for (Map.Entry<String, State> entry : states.entrySet()) {
            snapshot.windows.put(entry.getKey(), entry.getValue().copy());
        }

        try {
            Path parent = file.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }

            Path temporary = file.resolveSibling(file.getFileName().toString() + ".tmp");
            Files.writeString(temporary, I.write(snapshot), StandardCharsets.UTF_8);
            try {
                Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException error) {
                Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException error) {
            I.warn("apocrypha", new RuntimeException("Failed to save window state: " + file, error));
        }
    }

    /**
     * The JSON document.
     */
    public static final class Snapshot {

        public int version = VERSION;

        public Map<String, State> windows = new LinkedHashMap<>();

        public Snapshot() {
        }
    }

    /**
     * The state of one window.
     */
    public static final class State {

        public int x;

        public int y;

        public int width;

        public int height;

        public boolean maximized;

        public State() {
        }

        public State(int x, int y, int width, int height, boolean maximized) {
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
            this.maximized = maximized;
        }

        public Rectangle bounds() {
            return new Rectangle(x, y, width, height);
        }

        public boolean valid() {
            return width > 0 && height > 0;
        }

        public State copy() {
            return new State(x, y, width, height, maximized);
        }
    }
}
