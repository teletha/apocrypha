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

import java.io.File;
import java.lang.reflect.InvocationTargetException;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicReference;

import javax.swing.SwingUtilities;

import com.formdev.flatlaf.util.SystemFileChooser;

import apocrypha.webview.Win32;

/**
 * Native system dialogs which are shown in front of an application window.
 */
public final class Dialogs {

    private static final boolean WINDOWS = System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");

    private Dialogs() {
    }

    /**
     * Opens the operating system folder chooser using FlatLaf's {@link SystemFileChooser}.
     * <p>
     * The native dialogs of Windows 10+, macOS and GTK3 Linux are used automatically; other
     * platforms fall back to a FlatLaf-styled {@code JFileChooser}. Because system file choosers must
     * run on the AWT event dispatch thread, the calling IPC thread waits until the user decides.
     * </p>
     *
     * @param window The window which requests the dialog, or null.
     * @param title The dialog title, or null for the platform default.
     * @return The selected directory, or null when the user cancels.
     */
    public static String chooseFolder(Window window, String title) {
        if (WINDOWS && window != null) {
            // The webview is not an AWT window, so the chooser cannot use it as an owner. Bringing it
            // to the foreground keeps the native dialog above the application instead of behind it.
            Win32.focus(window.handle());
        }

        AtomicReference<String> selected = new AtomicReference<>();
        try {
            SwingUtilities.invokeAndWait(() -> {
                SystemFileChooser chooser = new SystemFileChooser();
                chooser.setFileSelectionMode(SystemFileChooser.DIRECTORIES_ONLY);
                chooser.setDialogTitle(title == null ? "Select a folder" : title);
                if (chooser.showOpenDialog(null) == SystemFileChooser.APPROVE_OPTION) {
                    File file = chooser.getSelectedFile();
                    selected.set(file == null ? null : file.getPath());
                }
            });
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
        } catch (InvocationTargetException error) {
            Throwable cause = error.getCause();
            if (cause instanceof RuntimeException runtime) {
                throw runtime;
            }
            throw new IllegalStateException("Failed to open the folder chooser", cause);
        }
        return selected.get();
    }
}
