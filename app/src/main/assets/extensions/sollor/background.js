// background.js — маршрутизатор между Android и content script
'use strict';

let nativePort = null;
let activeTabId = null;
let pendingOrientation = null;
let pendingFullscreen = null;

function closeNativePort() {
    if (!nativePort) return;
    try { nativePort.disconnect(); } catch (e) {}
    nativePort = null;
}

function createNativePort() {
    if (nativePort) return nativePort;   // не плодим порты на каждый PING
    closeNativePort();
    try {
        nativePort = browser.runtime.connectNative('browser');
        console.log('[sollor-bg] connectNative OK');

        // Отложенные сообщения, пришедшие до готовности порта
        if (pendingFullscreen !== null) {
            const fs = pendingFullscreen;
            pendingFullscreen = null;
            try { nativePort.postMessage({ type: 'FULLSCREEN_STATE', fullscreen: fs }); } catch (e) {}
        }
        if (pendingOrientation !== null) {
            const action = pendingOrientation;
            pendingOrientation = null;
            try { nativePort.postMessage({ type: 'SCREEN_ORIENTATION_CONTROL', action: action }); } catch (e) {}
        }

        nativePort.onMessage.addListener((message) => {
            if (!message) return;

            if (message.type === 'TOGGLE_SCRUBBER_SIDE') {
                browser.tabs.sendMessage(activeTabId, { type: 'TOGGLE_SIDE' })
                    .then(r => { try { nativePort.postMessage(r || { ok: true }); } catch (e) {} })
                    .catch(e => { try { nativePort.postMessage({ error: String(e) }); } catch (e2) {} });
                return;
            }

            if (message.type === 'RELOAD_SCRIPTS') {
                browser.tabs.sendMessage(activeTabId, { type: 'RELOAD_SCRIPTS_IN_PAGE' })
                    .then(r => { try { nativePort.postMessage(r || { ok: true }); } catch (e) {} })
                    .catch(e => { try { nativePort.postMessage({ error: String(e) }); } catch (e2) {} });
                return;
            }

            if (message.type === 'TOGGLE_VIDEO') {
                const targetTab = (message.tabId != null && message.tabId >= 0) ? message.tabId : activeTabId;
                if (targetTab == null) {
                    try { nativePort.postMessage({ type: 'VIDEO_STATE', error: 'no active tab' }); } catch (e) {}
                    return;
                }
                browser.tabs.sendMessage(targetTab, {
                    type: 'SOLLOR_VIDEO',
                    action: message.action,
                    value: message.value
                }, { frameId: (message.frameId != null && message.frameId >= 0) ? message.frameId : 0 })
                    .then(r => {
                        try {
                            nativePort.postMessage(r && r.ok
                                ? { type: 'VIDEO_STATE', state: r.state || null }
                                : { type: 'VIDEO_STATE', error: (r && r.error) || 'fail' });
                        } catch (e) {}
                    })
                    .catch(e => {
                        try { nativePort.postMessage({ type: 'VIDEO_STATE', error: String(e) }); } catch (e2) {}
                    });
                return;
            }
        });

        nativePort.onDisconnect.addListener(() => { nativePort = null; });
    } catch (e) {
        nativePort = null;
    }
    return nativePort;
}

browser.runtime.onMessage.addListener((message, sender, sendResponse) => {
    if (!message) return false;

    if (message.type === 'PING') {
        createNativePort();
        sendResponse({ ok: true });
        return true;
    }

    if (message.type === 'REGISTER_TAB') {
        if (sender && sender.tab && sender.tab.id != null) {
            activeTabId = sender.tab.id;
            console.log('[sollor-bg] REGISTER_TAB tab=' + sender.tab.id +
                        ' frame=' + (sender.frameId != null ? sender.frameId : '?'));
        }
        sendResponse({ ok: true });
        return true;
    }

    if (message.type === 'VIDEO_SCROLL') {
        try {
            if (nativePort) nativePort.postMessage({ type: 'VIDEO_SCROLL', scrolling: !!message.scrolling });
        } catch (e) {}
        sendResponse({ ok: true });
        return true;
    }

    if (message.type === 'VIDEO_INFO') {
        if (!sender || !sender.tab || sender.tab.id == null) return false;
        try {
            if (nativePort) nativePort.postMessage({
                type: 'VIDEO_INFO',
                tabId: sender.tab.id,
                frameId: (sender.frameId != null ? sender.frameId : 0),
                data: message.data || null
            });
        } catch (e) {}
        sendResponse({ ok: true });
        return true;
    }

    if (message.type === 'FULLSCREEN_STATE') {
        if (nativePort) {
            try { nativePort.postMessage({ type: 'FULLSCREEN_STATE', fullscreen: !!message.fullscreen }); } catch (e) {}
        } else {
            pendingFullscreen = !!message.fullscreen;
            createNativePort();
        }
        sendResponse({ ok: true });
        return true;
    }

    if (message.type === 'SCREEN_ORIENTATION_CONTROL') {
        if (nativePort) {
            try { nativePort.postMessage({ type: 'SCREEN_ORIENTATION_CONTROL', action: message.action }); } catch (e) {}
        } else {
            pendingOrientation = message.action;
            createNativePort();
        }
        sendResponse({ ok: true });
        return true;
    }

    return false;
});

createNativePort();
browser.runtime.onInstalled.addListener(() => createNativePort());
if (browser.runtime.onStartup) browser.runtime.onStartup.addListener(() => createNativePort());
