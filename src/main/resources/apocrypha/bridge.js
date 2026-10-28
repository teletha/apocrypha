/*
 * Copyright (C) 2026 The APOCRYPHA Development Team
 *
 * Licensed under the MIT License (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *          https://opensource.org/licenses/MIT
 */
(function () {
    'use strict';

    var META = __APOCRYPHA_META__;

    if (window.apocrypha && window.apocrypha.__apocrypha) {
        return;
    }

    var sequence = 0;
    var pending = new Map();
    var listeners = new Map();

    function errorFrom(info) {
        var detail = info || {};
        var error = new Error(detail.message || 'The IPC request failed.');
        error.code = detail.code || 'EIPC';
        if (detail.type) {
            error.name = detail.type;
        }
        return error;
    }

    /**
     * The function which the Java side binds to the webview.
     */
    function transport() {
        return typeof window.__apocryphaIpc === 'function' ? window.__apocryphaIpc : null;
    }

    /**
     * Invoke a Java IPC channel. The returned promise resolves with the JSON result.
     */
    function invoke(channel) {
        var args = Array.prototype.slice.call(arguments, 1);
        return new Promise(function (resolve, reject) {
            if (typeof channel !== 'string' || channel.length === 0) {
                reject(new TypeError('The IPC channel name must be a non-empty string.'));
                return;
            }

            var ipc = transport();
            if (!ipc) {
                reject(new Error('The Apocrypha bridge is not connected to the Java backend.'));
                return;
            }

            var id = ++sequence;
            pending.set(id, { resolve: resolve, reject: reject });

            try {
                ipc(JSON.stringify({
                    id: id,
                    channel: channel,
                    args: args.map(function (value) {
                        var encoded = JSON.stringify(value === undefined ? null : value);
                        return encoded === undefined ? 'null' : encoded;
                    })
                }));
            } catch (error) {
                pending.delete(id);
                reject(error);
            }
        });
    }

    /**
     * Deliver a response from the Java backend. Called by {@code Window} with the rendered JSON
     * response as a string.
     */
    function resolveResponse(id, response) {
        var entry = pending.get(id);
        if (!entry) {
            return;
        }
        pending.delete(id);

        var message;
        try {
            message = JSON.parse(response);
        } catch (error) {
            entry.reject(new Error('The Java backend returned an invalid IPC response.'));
            return;
        }

        if (message && message.ok) {
            entry.resolve(message.result);
        } else {
            entry.reject(errorFrom(message && message.error));
        }
    }

    window.__apocryphaResolve = resolveResponse;

    /**
     * Subscribe to an event sent by the Java backend. Returns an unsubscribe function.
     */
    function on(name, listener) {
        if (typeof listener !== 'function') {
            throw new TypeError('The event listener must be a function.');
        }
        var list = listeners.get(name);
        if (!list) {
            list = [];
            listeners.set(name, list);
        }
        list.push(listener);
        return function () {
            off(name, listener);
        };
    }

    /**
     * Subscribe to the next event with the given name.
     */
    function once(name, listener) {
        var unsubscribe = on(name, function (payload) {
            unsubscribe();
            listener(payload);
        });
        return unsubscribe;
    }

    /**
     * Remove an event listener. Omit the listener to remove every listener for the event.
     */
    function off(name, listener) {
        var list = listeners.get(name);
        if (!list) {
            return;
        }
        if (!listener) {
            listeners.delete(name);
            return;
        }
        var index = list.indexOf(listener);
        if (index >= 0) {
            list.splice(index, 1);
        }
        if (list.length === 0) {
            listeners.delete(name);
        }
    }

    /**
     * Emit an event to the Java backend.
     */
    function emit(name, payload) {
        return invoke('apocrypha:event:emit', name, payload === undefined ? null : payload);
    }

    /**
     * Deliver an event from the Java backend.
     */
    function dispatch(name, payload) {
        var list = listeners.get(name);
        if (list) {
            list.slice().forEach(function (listener) {
                try {
                    listener(payload);
                } catch (error) {
                    console.error('[apocrypha] event listener failed:', error);
                }
            });
        }

        var wildcard = listeners.get('*');
        if (wildcard) {
            wildcard.slice().forEach(function (listener) {
                try {
                    listener({ event: name, payload: payload });
                } catch (error) {
                    console.error('[apocrypha] event listener failed:', error);
                }
            });
        }
    }

    /**
     * Tell the Java side that the document has loaded, so buffered events can be delivered.
     */
    function pageReady() {
        reportTitle();
        if (typeof window.__apocryphaPageReady === 'function') {
            try {
                window.__apocryphaPageReady();
            } catch (error) {
                console.error('[apocrypha] page ready callback failed:', error);
            }
        }
    }

    var reportedTitle;

    /**
     * Mirror the document title to the native window title.
     */
    function reportTitle() {
        var title = document.title;
        if (title && title !== reportedTitle) {
            reportedTitle = title;
            invoke('apocrypha:window:documentTitle', title);
        }
    }

    function watchTitle() {
        reportTitle();
        if (document.head && typeof MutationObserver === 'function') {
            new MutationObserver(reportTitle).observe(document.head, {
                childList: true,
                subtree: true,
                characterData: true
            });
        }
    }

    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', watchTitle, { once: true });
    } else {
        watchTitle();
    }

    if (document.readyState === 'complete') {
        setTimeout(pageReady, 0);
    } else {
        window.addEventListener('load', function () {
            setTimeout(pageReady, 0);
        }, { once: true });
    }

    /**
     * Open an application URL in place and hand external links to the operating system browser.
     * Returns true when the URL was handled.
     */
    function openLink(url) {
        var target = url;
        try {
            target = new URL(url, window.location.href).href;
        } catch (error) {
            return false;
        }

        if (target === window.location.origin || target.indexOf(window.location.origin + '/') === 0) {
            window.location.href = target;
            return true;
        }
        if (/^(https?|mailto):/i.test(target)) {
            invoke('apocrypha:window:openExternal', target);
            return true;
        }
        return false;
    }

    /**
     * Hand external popups to the operating system browser and keep same-origin navigation in the
     * current window.
     */
    var open = window.open;
    window.open = function (url, name, features) {
        if (typeof url === 'string' && url.length > 0 && openLink(url)) {
            return null;
        }
        return open.apply(window, arguments);
    };

    /**
     * The platform webview cannot intercept a popup at the browser level, so route
     * {@code target="_blank"} links through the same policy as {@code window.open}.
     */
    document.addEventListener('click', function (event) {
        var element = event.target;
        if (!element || typeof element.closest !== 'function') {
            return;
        }
        var anchor = element.closest('a[target]');
        if (!anchor || !anchor.target || anchor.target.toLowerCase() !== '_blank' || !anchor.href) {
            return;
        }
        if (openLink(anchor.href)) {
            event.preventDefault();
        }
    }, true);

    var app = Object.freeze({
        name: META.name,
        version: META.version,
        frameworkVersion: META.frameworkVersion,
        platform: META.platform,
        arch: META.arch,
        java: META.java,
        webview: META.webview,
        quit: function () {
            return invoke('apocrypha:app:quit');
        },
        restart: function () {
            return invoke('apocrypha:app:restart');
        },
        info: function () {
            return invoke('apocrypha:app:info');
        }
    });

    var windowApi = Object.freeze({
        setTitle: function (title) {
            return invoke('apocrypha:window:setTitle', title);
        },
        show: function () {
            return invoke('apocrypha:window:show');
        },
        hide: function () {
            return invoke('apocrypha:window:hide');
        },
        minimize: function () {
            return invoke('apocrypha:window:minimize');
        },
        maximize: function () {
            return invoke('apocrypha:window:maximize');
        },
        unmaximize: function () {
            return invoke('apocrypha:window:unmaximize');
        },
        isMaximized: function () {
            return invoke('apocrypha:window:isMaximized');
        },
        setSize: function (width, height) {
            return invoke('apocrypha:window:setSize', width, height);
        },
        getSize: function () {
            return invoke('apocrypha:window:getSize');
        },
        getBounds: function () {
            return invoke('apocrypha:window:getBounds');
        },
        setBounds: function (x, y, width, height) {
            return invoke('apocrypha:window:setBounds', x, y, width, height);
        },
        setPosition: function (x, y) {
            return invoke('apocrypha:window:setPosition', x, y);
        },
        center: function () {
            return invoke('apocrypha:window:center');
        },
        setAlwaysOnTop: function (value) {
            return invoke('apocrypha:window:setAlwaysOnTop', !!value);
        },
        setResizable: function (value) {
            return invoke('apocrypha:window:setResizable', !!value);
        },
        reload: function () {
            return invoke('apocrypha:window:reload');
        },
        load: function (url) {
            return invoke('apocrypha:window:load', url);
        },
        openDevTools: function () {
            return invoke('apocrypha:window:openDevTools');
        },
        close: function () {
            return invoke('apocrypha:window:close');
        }
    });

    var api = Object.freeze({
        __apocrypha: true,
        version: META.frameworkVersion,
        app: app,
        window: windowApi,
        invoke: invoke,
        on: on,
        once: once,
        off: off,
        emit: emit,
        ready: Promise.resolve()
    });

    window.apocrypha = api;
    window.__apocryphaDispatch = dispatch;
})();
