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
     * Invoke a Java IPC channel. The returned promise resolves with the JSON result.
     */
    function invoke(channel) {
        var args = Array.prototype.slice.call(arguments, 1);
        return new Promise(function (resolve, reject) {
            if (typeof window.cefQuery !== 'function') {
                reject(new Error('The Apocrypha bridge is not connected to the Java backend.'));
                return;
            }

            var id = ++sequence;
            pending.set(id, { resolve: resolve, reject: reject });

            try {
                window.cefQuery({
                    request: JSON.stringify({
                        id: id,
                        channel: channel,
                        args: args.map(function (value) {
                            var encoded = JSON.stringify(value === undefined ? null : value);
                            return encoded === undefined ? 'null' : encoded;
                        })
                    }),
                    persistent: false,
                    onSuccess: function (response) {
                        pending.delete(id);
                        var message;
                        try {
                            message = JSON.parse(response);
                        } catch (error) {
                            reject(new Error('The Java backend returned an invalid IPC response.'));
                            return;
                        }
                        if (message && message.ok) {
                            resolve(message.result);
                        } else {
                            reject(errorFrom(message && message.error));
                        }
                    },
                    onFailure: function (code, message) {
                        pending.delete(id);
                        reject(errorFrom({
                            code: code === undefined ? 'EIPC' : 'EIPC:' + code,
                            message: message || 'The IPC transport failed.'
                        }));
                    }
                });
            } catch (error) {
                pending.delete(id);
                reject(error);
            }
        });
    }

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

    var app = Object.freeze({
        name: META.name,
        version: META.version,
        frameworkVersion: META.frameworkVersion,
        platform: META.platform,
        arch: META.arch,
        java: META.java,
        cef: META.cef,
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
