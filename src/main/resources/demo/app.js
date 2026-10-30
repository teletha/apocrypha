'use strict';

(function () {
    const $ = (selector) => document.querySelector(selector);
    const bridge = window.apocrypha;

    const setOutput = (selector, value, error) => {
        const element = $(selector);
        element.classList.toggle('error', !!error);
        element.textContent = typeof value === 'string' ? value : JSON.stringify(value, null, 2);
    };

    if (!bridge) {
        setOutput('#greet-result', 'The Apocrypha bridge was not injected into this page.', true);
        setOutput('#sum-result', 'Open this page through the Apocrypha application server.', true);
        return;
    }

    const describeError = (error) => {
        const code = error && error.code ? error.code : 'EUNKNOWN';
        const message = error && error.message ? error.message : String(error);
        return code + ': ' + message;
    };

    const greet = async (name) => {
        setOutput('#greet-result', 'Calling the Java backend...');
        try {
            const result = await bridge.invoke('demo:greet', { name: name });
            setOutput('#greet-result', result.message + '\n' + result.timestamp + '\n' + result.thread);
        } catch (error) {
            setOutput('#greet-result', describeError(error), true);
        }
    };

    const sum = async () => {
        const a = Number($('#sum-a').value || 0);
        const b = Number($('#sum-b').value || 0);
        try {
            const result = await bridge.invoke('demo:sum', a, b);
            setOutput('#sum-result', a + ' + ' + b + ' = ' + result);
        } catch (error) {
            setOutput('#sum-result', describeError(error), true);
        }
    };

    const emit = async () => {
        const text = $('#event-text').value;
        try {
            await bridge.emit('demo:log', { text: text });
            setOutput('#event-result', 'Sent to Java. Waiting for the response event...');
        } catch (error) {
            setOutput('#event-result', describeError(error), true);
        }
    };

    const demonstrateError = async () => {
        try {
            await bridge.invoke('demo:fail');
            setOutput('#fail-result', 'The backend unexpectedly succeeded.', true);
        } catch (error) {
            setOutput('#fail-result', describeError(error), true);
        }
    };

    const getSize = async () => {
        try {
            const size = await bridge.window.getSize();
            setOutput('#window-result', 'Window size: ' + size.width + ' x ' + size.height);
        } catch (error) {
            setOutput('#window-result', describeError(error), true);
        }
    };

    const initializeRuntime = async () => {
        const app = bridge.app;
        $('#metric-java').textContent = 'Java ' + app.java;
        $('#metric-webview').textContent = 'WebView ' + app.webview;
        $('#metric-platform').textContent = app.arch;
        $('#framework-version').textContent = 'v' + app.frameworkVersion;
        $('#fact-name').textContent = app.name;
        $('#fact-version').textContent = app.version;
        $('#fact-framework').textContent = app.frameworkVersion;
        $('#fact-arch').textContent = app.platform + ' / ' + app.arch;
    };

    $('#greet-form').addEventListener('submit', (event) => {
        event.preventDefault();
        greet($('#greet-name').value || 'world');
    });

    $('#sum-form').addEventListener('submit', (event) => {
        event.preventDefault();
        sum();
    });

    $('#event-form').addEventListener('submit', (event) => {
        event.preventDefault();
        emit();
    });

    $('#fail').addEventListener('click', demonstrateError);

    $('#center').addEventListener('click', async () => {
        try {
            await bridge.window.center();
            await getSize();
        } catch (error) {
            setOutput('#window-result', describeError(error), true);
        }
    });

    $('#toggle-maximize').addEventListener('click', async () => {
        try {
            if (await bridge.window.isMaximized()) {
                await bridge.window.unmaximize();
            } else {
                await bridge.window.maximize();
            }
            await getSize();
        } catch (error) {
            setOutput('#window-result', describeError(error), true);
        }
    });

    $('#devtools').addEventListener('click', async () => {
        try {
            await bridge.window.openDevTools();
            setOutput('#window-result', 'Developer tools opened. You can also press F12 or use the context menu.');
        } catch (error) {
            setOutput('#window-result', describeError(error), true);
        }
    });

    $('#restart').addEventListener('click', async () => {
        setOutput('#window-result', 'Restarting the application...');
        try {
            await bridge.app.restart();
        } catch (error) {
            setOutput('#window-result', describeError(error), true);
        }
    });

    $('#minimize').addEventListener('click', () => bridge.window.minimize());
    $('#maximize').addEventListener('click', () => bridge.window.maximize());
    $('#close').addEventListener('click', () => bridge.window.close());

    bridge.on('demo:logged', (payload) => {
        setOutput('#event-result', 'Java received and echoed:\n' + JSON.stringify(payload, null, 2));
    });

    bridge.on('demo:server', (payload) => {
        setOutput('#event-result', 'Java event received:\n' + JSON.stringify(payload, null, 2));
    });

    bridge.on('*', (event) => {
        if (event.event !== 'demo:logged') {
            console.debug('[apocrypha] event', event.event, event.payload);
        }
    });

    initializeRuntime();
    getSize();
    greet('WebView');
    sum();
    window.scrollTo(0, 0);
})();

