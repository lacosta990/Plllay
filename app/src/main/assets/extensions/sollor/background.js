// background.js — диспетчер между Android и content script
'use strict';

console.log('[sollor-bg] background.js стартовал');

let nativePort = null;
let activeTabId = null;

function closeNativePort() {
    if (!nativePort) return;
    try { nativePort.disconnect(); } catch (e) {}
    nativePort = null;
}

function sendToggleToTab() {
    if (activeTabId == null) {
        throw new Error('Нет зарегистрированной вкладки');
    }
    return browser.tabs.sendMessage(activeTabId, { type: 'TOGGLE_SIDE' });
}

function createNativePort() {
    closeNativePort();
    try {
        nativePort = browser.runtime.connectNative('browser');
        console.log('[sollor-bg] connectNative OK');

        nativePort.onMessage.addListener((message) => {
            console.log('[sollor-bg] Сообщение от Android:', message);

            if (!message || message.type !== 'TOGGLE_SCRUBBER_SIDE') return;

            sendToggleToTab()
                .then(response => {
                    console.log('[sollor-bg] Ответ от content script:', response);
                    try { nativePort.postMessage(response || { ok: true }); } catch (e) {}
                })
                .catch(error => {
                    console.error('[sollor-bg] Ошибка:', error);
                    try { nativePort.postMessage({ error: String(error) }); } catch (e) {}
                });
        });

        nativePort.onDisconnect.addListener(() => {
            console.log('[sollor-bg] Порт отключён');
            nativePort = null;
        });

        console.log('[sollor-bg] Порт создан, ждём ответа от натива');
    } catch (e) {
        console.error('[sollor-bg] connectNative упал:', e);
        nativePort = null;
    }
    return nativePort;
}

// Единый обработчик runtime-сообщений:
//   PING         — от loader.js, пересоздаёт порт
//   REGISTER_TAB — от content.js, запоминает id вкладки
browser.runtime.onMessage.addListener((message, sender, sendResponse) => {
    if (!message) return false;

    if (message.type === 'PING') {
        console.log('[sollor-bg] Получен PING, пересоздаю порт');
        createNativePort();
        sendResponse({ ok: true });
        return true;
    }

    if (message.type === 'REGISTER_TAB') {
        if (sender && sender.tab && sender.tab.id != null) {
            activeTabId = sender.tab.id;
            console.log('[sollor-bg] Зарегистрирована вкладка id=' + activeTabId);
        } else {
            console.warn('[sollor-bg] REGISTER_TAB без sender.tab');
        }
        sendResponse({ ok: true });
        return true;
    }

    return false;
});

// Первичный запуск
createNativePort();

// Пересоздание порта при установке/старте
browser.runtime.onInstalled.addListener(() => {
    console.log('[sollor-bg] onInstalled');
    createNativePort();
});

if (browser.runtime.onStartup) {
    browser.runtime.onStartup.addListener(() => {
        console.log('[sollor-bg] onStartup');
        createNativePort();
    });
}