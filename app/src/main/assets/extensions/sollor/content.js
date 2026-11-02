// content.js — «руки» расширения на странице
'use strict';

console.log('[sollor-content] content.js загружен');

// Регистрируемся в background, чтобы он знал id этой вкладки
try {
    browser.runtime.sendMessage({ type: 'REGISTER_TAB' });
} catch (e) {}

function getPageWindow() {
    try {
        return window.wrappedJSObject || window;
    } catch (e) {
        return window;
    }
}

// ---- Конфигурация (должна совпадать с loader.js) ----
const CSS_URL = 'https://storage.yandexcloud.net/sollor/injection/sollor.css';
const JS_URLS = [
    'https://storage.yandexcloud.net/sollor/injection/sollor.js',
    'https://storage.yandexcloud.net/sollor/injection/cutter.js',
    'https://storage.yandexcloud.net/sollor/injection/plus.js',
    'https://storage.yandexcloud.net/sollor/injection/cleaner.js'
];
const STORAGE_PREFIX = 'sollor_';

function storageKey(url) {
    return STORAGE_PREFIX + url;
}

async function fetchAndSave(url) {
    console.log('[sollor-content] 🌐 Скачиваем:', url);
    const response = await fetch(url);
    if (!response.ok) throw new Error('HTTP ' + response.status);
    const text = await response.text();

    const key = storageKey(url);
    const obj = {};
    obj[key] = text;
    await browser.storage.local.set(obj);

    console.log('[sollor-content] 💾 Сохранено в storage:', url, '(' + text.length + ' байт)');
}

browser.runtime.onMessage.addListener((message, sender, sendResponse) => {
    if (!message) return false;

    // --- Переключение стороны скраббера ---
    if (message.type === 'TOGGLE_SIDE') {
        try {
            const pw = getPageWindow();
            let side = null;

            if (pw && typeof pw.__sollorToggleScrubberSide === 'function') {
                side = pw.__sollorToggleScrubberSide();
                console.log('[sollor-content] Переключено через API, сторона:', side);
            } else {
                const scrubber = document.querySelector('.jw-vertical-scrub');
                if (!scrubber) {
                    sendResponse({ error: 'scrubber not found' });
                    return true;
                }
                scrubber.classList.toggle('jw-vertical-scrub--left');
                side = scrubber.classList.contains('jw-vertical-scrub--left') ? 'left' : 'right';
                try { localStorage.setItem('sollor-scrubber-side', side); } catch (e) {}
                console.log('[sollor-content] Переключено через DOM, сторона:', side);
            }

            sendResponse({ side: String(side) });
        } catch (e) {
            console.error('[sollor-content] Ошибка переключения:', e);
            sendResponse({ error: String(e) });
        }
        return true;
    }

    // --- Скачать свежие скрипты с облака + перезагрузить страницу ---
    if (message.type === 'RELOAD_SCRIPTS_IN_PAGE') {
        (async () => {
            try {
                const urls = [CSS_URL, ...JS_URLS];
                console.log('[sollor-content] 🔄 Обновление ' + urls.length + ' файлов');

                for (const url of urls) {
                    await fetchAndSave(url);
                }

                console.log('[sollor-content] ✅ Все скрипты скачаны и сохранены. Перезагружаю страницу.');

                // Отвечаем нативу ДО перезагрузки
                sendResponse({ ok: true, count: urls.length });

                // Даём ответу уйти, потом перезагружаем
                setTimeout(() => {
                    location.reload();
                }, 200);
            } catch (e) {
                console.error('[sollor-content] ❌ Ошибка обновления:', e);
                sendResponse({ error: String(e) });
            }
        })();
        return true;
    }

    return false;
});