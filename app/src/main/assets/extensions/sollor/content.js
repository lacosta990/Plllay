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

browser.runtime.onMessage.addListener((message, sender, sendResponse) => {
    if (!message || message.type !== 'TOGGLE_SIDE') {
        return false;
    }

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
        console.error('[sollor-content] Ошибка:', e);
        sendResponse({ error: String(e) });
    }

    return true;
});