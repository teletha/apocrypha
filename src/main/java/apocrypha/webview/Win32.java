/*
 * Copyright (C) 2026 The APOCRYPHA Development Team
 *
 * Licensed under the MIT License (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *          https://opensource.org/licenses/MIT
 */
package apocrypha.webview;

import static java.lang.foreign.ValueLayout.ADDRESS;
import static java.lang.foreign.ValueLayout.JAVA_INT;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.invoke.MethodHandle;

/**
 * The Win32 calls needed to manage an avaje-webview window.
 * <p>
 * avaje-webview owns the native browser window and exposes its handle through
 * {@link io.avaje.webview.Webview#nativeWindowPointer()}. The framework uses these helpers to
 * provide the window operations which the webview API does not expose (position, bounds,
 * always-on-top, resizability, hide/show, and so on). They are only used on Windows.
 */
public final class Win32 {

    private static final Linker LINKER = Linker.nativeLinker();

    private static final SymbolLookup USER32 = SymbolLookup.libraryLookup("user32", Arena.global());

    private static final int GWL_STYLE = -16;

    private static final int WS_THICKFRAME = 0x00040000;

    private static final int WS_MAXIMIZEBOX = 0x00010000;

    private static final int SW_HIDE = 0;

    private static final int SW_SHOW = 5;

    private static final int SW_MINIMIZE = 6;

    private static final int SW_MAXIMIZE = 3;

    private static final int SW_RESTORE = 9;

    private static final int SWP_NOSIZE = 0x0001;

    private static final int SWP_NOMOVE = 0x0002;

    private static final int SWP_NOZORDER = 0x0004;

    private static final int SWP_NOACTIVATE = 0x0010;

    private static final int SWP_FRAMECHANGED = 0x0020;

    private static final int WM_KEYDOWN = 0x0100;

    private static final int WM_KEYUP = 0x0101;

    private static final long VK_F12 = 0x7B;

    private static final MemorySegment HWND_TOPMOST = MemorySegment.ofAddress(-1L);

    private static final MemorySegment HWND_NOTOPMOST = MemorySegment.ofAddress(-2L);

    private static final MethodHandle GetWindowRect = down("GetWindowRect",
            FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS));

    private static final MethodHandle SetWindowPos = down("SetWindowPos", FunctionDescriptor.of(JAVA_INT,
            ADDRESS, ADDRESS, JAVA_INT, JAVA_INT, JAVA_INT, JAVA_INT, JAVA_INT));

    private static final MethodHandle ShowWindow = down("ShowWindow",
            FunctionDescriptor.of(JAVA_INT, ADDRESS, JAVA_INT));

    private static final MethodHandle IsZoomed = down("IsZoomed", FunctionDescriptor.of(JAVA_INT, ADDRESS));

    private static final MethodHandle IsIconic = down("IsIconic", FunctionDescriptor.of(JAVA_INT, ADDRESS));

    private static final MethodHandle IsWindowVisible = down("IsWindowVisible",
            FunctionDescriptor.of(JAVA_INT, ADDRESS));

    private static final MethodHandle GetWindowLongW = down("GetWindowLongW",
            FunctionDescriptor.of(JAVA_INT, ADDRESS, JAVA_INT));

    private static final MethodHandle SetWindowLongW = down("SetWindowLongW",
            FunctionDescriptor.of(JAVA_INT, ADDRESS, JAVA_INT, JAVA_INT));

    private static final MethodHandle SetForegroundWindow = down("SetForegroundWindow",
            FunctionDescriptor.of(JAVA_INT, ADDRESS));

    private static final MethodHandle SetFocus = down("SetFocus", FunctionDescriptor.of(ADDRESS, ADDRESS));

    private static final MethodHandle FindWindowExA = down("FindWindowExA",
            FunctionDescriptor.of(ADDRESS, ADDRESS, ADDRESS, ADDRESS, ADDRESS));

    private static final MethodHandle PostMessageW = down("PostMessageW",
            FunctionDescriptor.of(JAVA_INT, ADDRESS, JAVA_INT, ADDRESS, ADDRESS));

    private Win32() {
    }

    /**
     * Read a window's outer bounds.
     * 
     * @param window A native window handle.
     * @return {@code x, y, width, height}, or {@code {0, 0, 0, 0}} when unavailable.
     */
    public static int[] windowRect(long window) {
        if (window == 0) {
            return new int[4];
        }
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment rect = arena.allocate(16, 4);
            int ok = (int) GetWindowRect.invokeExact(MemorySegment.ofAddress(window), rect);
            if (ok == 0) {
                return new int[4];
            }
            int left = rect.get(JAVA_INT, 0);
            int top = rect.get(JAVA_INT, 4);
            return new int[] { left, top, rect.get(JAVA_INT, 8) - left, rect.get(JAVA_INT, 12) - top };
        } catch (Throwable error) {
            throw new IllegalStateException("Failed to read the window bounds", error);
        }
    }

    /**
     * Set a window's outer bounds without activating it.
     * 
     * @param window A native window handle.
     * @param x The horizontal position.
     * @param y The vertical position.
     * @param width The width.
     * @param height The height.
     */
    public static void setBounds(long window, int x, int y, int width, int height) {
        if (window == 0 || width <= 0 || height <= 0) {
            return;
        }
        try {
            int ignored = (int) SetWindowPos.invokeExact(MemorySegment.ofAddress(window), MemorySegment.NULL, x, y,
                    width, height, SWP_NOZORDER | SWP_NOACTIVATE);
        } catch (Throwable error) {
            throw new IllegalStateException("Failed to set the window bounds", error);
        }
    }

    /**
     * Show and focus a window.
     * 
     * @param window A native window handle.
     */
    public static void show(long window) {
        if (window == 0) {
            return;
        }
        try {
            MemorySegment handle = MemorySegment.ofAddress(window);
            int ignored = (int) ShowWindow.invokeExact(handle, SW_SHOW);
            ignored = (int) SetForegroundWindow.invokeExact(handle);
            MemorySegment previous = (MemorySegment) SetFocus.invokeExact(handle);
        } catch (Throwable error) {
            throw new IllegalStateException("Failed to show the window", error);
        }
    }

    /**
     * Hide a window.
     * 
     * @param window A native window handle.
     */
    public static void hide(long window) {
        if (window == 0) {
            return;
        }
        try {
            int ignored = (int) ShowWindow.invokeExact(MemorySegment.ofAddress(window), SW_HIDE);
        } catch (Throwable error) {
            throw new IllegalStateException("Failed to hide the window", error);
        }
    }

    /**
     * Minimize a window.
     * 
     * @param window A native window handle.
     */
    public static void minimize(long window) {
        if (window == 0) {
            return;
        }
        try {
            int ignored = (int) ShowWindow.invokeExact(MemorySegment.ofAddress(window), SW_MINIMIZE);
        } catch (Throwable error) {
            throw new IllegalStateException("Failed to minimize the window", error);
        }
    }

    /**
     * Maximize a window.
     * 
     * @param window A native window handle.
     */
    public static void maximize(long window) {
        if (window == 0) {
            return;
        }
        try {
            int ignored = (int) ShowWindow.invokeExact(MemorySegment.ofAddress(window), SW_MAXIMIZE);
        } catch (Throwable error) {
            throw new IllegalStateException("Failed to maximize the window", error);
        }
    }

    /**
     * Restore a maximized or minimized window.
     * 
     * @param window A native window handle.
     */
    public static void restore(long window) {
        if (window == 0) {
            return;
        }
        try {
            int ignored = (int) ShowWindow.invokeExact(MemorySegment.ofAddress(window), SW_RESTORE);
        } catch (Throwable error) {
            throw new IllegalStateException("Failed to restore the window", error);
        }
    }

    /**
     * Whether a window is maximized.
     * 
     * @param window A native window handle.
     * @return True if maximized.
     */
    public static boolean isZoomed(long window) {
        if (window == 0) {
            return false;
        }
        try {
            return (int) IsZoomed.invokeExact(MemorySegment.ofAddress(window)) != 0;
        } catch (Throwable error) {
            throw new IllegalStateException("Failed to read the maximize state", error);
        }
    }

    /**
     * Whether a window is minimized.
     * 
     * @param window A native window handle.
     * @return True if minimized.
     */
    public static boolean isIconic(long window) {
        if (window == 0) {
            return false;
        }
        try {
            return (int) IsIconic.invokeExact(MemorySegment.ofAddress(window)) != 0;
        } catch (Throwable error) {
            throw new IllegalStateException("Failed to read the minimize state", error);
        }
    }

    /**
     * Whether a window is visible.
     * 
     * @param window A native window handle.
     * @return True if visible.
     */
    public static boolean isVisible(long window) {
        if (window == 0) {
            return false;
        }
        try {
            return (int) IsWindowVisible.invokeExact(MemorySegment.ofAddress(window)) != 0;
        } catch (Throwable error) {
            throw new IllegalStateException("Failed to read the visibility", error);
        }
    }

    /**
     * Toggle always-on-top.
     * 
     * @param window A native window handle.
     * @param enable Whether the window stays above other windows.
     */
    public static void setAlwaysOnTop(long window, boolean enable) {
        if (window == 0) {
            return;
        }
        try {
            int ignored = (int) SetWindowPos.invokeExact(MemorySegment.ofAddress(window),
                    enable ? HWND_TOPMOST : HWND_NOTOPMOST, 0, 0, 0, 0,
                    SWP_NOMOVE | SWP_NOSIZE | SWP_NOACTIVATE);
        } catch (Throwable error) {
            throw new IllegalStateException("Failed to change always-on-top", error);
        }
    }

    /**
     * Toggle whether the user can resize a window.
     * 
     * @param window A native window handle.
     * @param resizable Whether the window is resizable.
     */
    public static void setResizable(long window, boolean resizable) {
        if (window == 0) {
            return;
        }
        try {
            MemorySegment handle = MemorySegment.ofAddress(window);
            int style = (int) GetWindowLongW.invokeExact(handle, GWL_STYLE);
            int next = resizable ? style | WS_THICKFRAME | WS_MAXIMIZEBOX
                    : style & ~(WS_THICKFRAME | WS_MAXIMIZEBOX);
            int ignored = (int) SetWindowLongW.invokeExact(handle, GWL_STYLE, next);
            ignored = (int) SetWindowPos.invokeExact(handle, MemorySegment.NULL, 0, 0, 0, 0,
                    SWP_NOMOVE | SWP_NOSIZE | SWP_NOZORDER | SWP_NOACTIVATE | SWP_FRAMECHANGED);
        } catch (Throwable error) {
            throw new IllegalStateException("Failed to change resizability", error);
        }
    }

    /**
     * Move keyboard focus to a window.
     * 
     * @param window A native window handle.
     */
    public static void focus(long window) {
        if (window == 0) {
            return;
        }
        try {
            MemorySegment handle = MemorySegment.ofAddress(window);
            int ignored = (int) SetForegroundWindow.invokeExact(handle);
            MemorySegment previous = (MemorySegment) SetFocus.invokeExact(handle);
        } catch (Throwable error) {
            throw new IllegalStateException("Failed to focus the window", error);
        }
    }

    /**
     * Ask the WebView2 child window to open its developer tools.
     * <p>
     * avaje-webview does not expose {@code ICoreWebView2::OpenDevToolsWindow}, so this posts the
     * standard F12 accelerator to the embedded WebView2 window. Developer tools must have been
     * enabled when the webview was created.
     *
     * @param window The top-level webview window handle.
     * @return True if the F12 accelerator was posted.
     */
    public static boolean openDevTools(long window) {
        if (window == 0) {
            return false;
        }

        long target = findWindow(window, "Chrome_WidgetWin_1");
        if (target == 0) {
            target = findWindow(window, "Chrome_RenderWidgetHostHWND");
        }
        if (target == 0) {
            target = window;
        }

        try {
            MemorySegment handle = MemorySegment.ofAddress(target);
            MemorySegment key = MemorySegment.ofAddress(VK_F12);
            int down = (int) PostMessageW.invokeExact(handle, WM_KEYDOWN, key, MemorySegment.NULL);
            int up = (int) PostMessageW.invokeExact(handle, WM_KEYUP, key, MemorySegment.NULL);
            return down != 0 && up != 0;
        } catch (Throwable error) {
            throw new IllegalStateException("Failed to open the webview developer tools", error);
        }
    }

    private static long findWindow(long parent, String className) {
        if (parent == 0) {
            return 0;
        }

        try (Arena arena = Arena.ofConfined()) {
            MemorySegment name = arena.allocateFrom(className);
            MemorySegment direct = (MemorySegment) FindWindowExA.invokeExact(MemorySegment.ofAddress(parent),
                    MemorySegment.NULL, name, MemorySegment.NULL);
            if (direct != null && direct.address() != 0) {
                return direct.address();
            }

            MemorySegment after = MemorySegment.NULL;
            while (true) {
                MemorySegment child = (MemorySegment) FindWindowExA.invokeExact(MemorySegment.ofAddress(parent), after,
                        MemorySegment.NULL, MemorySegment.NULL);
                if (child == null || child.address() == 0) {
                    return 0;
                }
                long found = findWindow(child.address(), className);
                if (found != 0) {
                    return found;
                }
                after = child;
            }
        } catch (Throwable error) {
            throw new IllegalStateException("Failed to find the webview child window", error);
        }
    }

    private static MethodHandle down(String name, FunctionDescriptor descriptor) {
        return LINKER.downcallHandle(USER32.find(name).orElseThrow(() -> new UnsatisfiedLinkError(name)), descriptor);
    }
}
