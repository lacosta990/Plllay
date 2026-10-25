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
            console.error('[sollor-content] Ошибка переключения стороны:', e);
            sendResponse({ error: String(e) });
        }
        return true;
    }

    // --- Очистка кэша скриптов + перезагрузка страницы ---
    if (message.type === 'RELOAD_SCRIPTS_IN_PAGE') {
        (async () => {
            try {
                // Собираем все кэши, относящиеся к sollor
                const allKeys = await caches.keys();
                const sollorKeys = allKeys.filter(k => k.startsWith('sollor-cache-'));

                console.log('[sollor-content] Найдены кэши для удаления:', sollorKeys);

                // Удаляем их все
                await Promise.all(sollorKeys.map(k => caches.delete(k)));

                console.log('[sollor-content] Кэши удалены, перезагружаю страницу');

                // Отвечаем нативу до перезагрузки — иначе не успеет дойти
                sendResponse({ ok: true, deleted: sollorKeys });

                // Даём немного времени, чтобы ответ ушёл в порт,
                // затем перезагружаем — loader.js запустится заново
                // и скачает свежие скрипты с Яндекс.Облака.
                setTimeout(() => {
                    location.reload();
                }, 200);
            } catch (e) {
                console.error('[sollor-content] Ошибка очистки кэша:', e);
                sendResponse({ error: String(e) });
            }
        })();
        return true; // асинхронный ответ
    }

    return false;
});