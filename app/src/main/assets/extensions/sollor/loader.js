// ============================================================
// ЗАГРУЗЧИК ВНЕШНИХ РЕСУРСОВ + ХРАНЕНИЕ В STORAGE + ОРИЕНТАЦИЯ
// ============================================================
(function() {
    console.log('[Sollor] loader.js выполняется');

    // Будим background, чтобы поднять native-порт
    try {
        browser.runtime.sendMessage({ type: 'PING' });
    } catch (e) {}

    // ---- Конфигурация ----
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

    // ---- Чтение из storage (без сети) ----
    async function readFromStorage(url) {
        try {
            const key = storageKey(url);
            const result = await browser.storage.local.get(key);
            return result[key] || null;
        } catch (e) {
            console.warn('[Sollor] storage.local.get ошибка:', e);
            return null;
        }
    }

    // ---- Запись в storage ----
    async function writeToStorage(url, text) {
        try {
            const key = storageKey(url);
            const obj = {};
            obj[key] = text;
            await browser.storage.local.set(obj);
        } catch (e) {
            console.warn('[Sollor] storage.local.set ошибка:', e);
        }
    }

    // ---- Загрузка с облака ----
    async function fetchFromCloud(url) {
        const response = await fetch(url);
        if (!response.ok) throw new Error('HTTP ' + response.status);
        return await response.text();
    }

    // ---- Основная функция ----
    async function loadAndInject(url, isCss) {
        // 1. Пытаемся из storage — БЕЗ СЕТИ
        const cached = await readFromStorage(url);
        if (cached) {
            console.log('[Sollor] ✅ Из storage:', url);
            insertCode(cached, isCss);
            return;
        }

        // 2. В storage нет — качаем ОДИН РАЗ и сохраняем
        console.log('[Sollor] 🌐 Первая загрузка с облака:', url);
        try {
            const text = await fetchFromCloud(url);
            await writeToStorage(url, text);
            insertCode(text, isCss);
        } catch (e) {
            console.error('[Sollor] ❌ Ошибка загрузки:', url, e);
        }
    }

    function insertCode(text, isCss) {
        if (isCss) {
            const style = document.createElement('style');
            style.textContent = text;
            document.head.appendChild(style);
            console.log('[Sollor] 🎨 CSS вставлен');
        } else {
            try {
                const script = document.createElement('script');
                script.textContent = text;
                document.head.appendChild(script);
                console.log('[Sollor] 📜 JS выполнен');
            } catch (e) {
                console.error('[Sollor] ❌ Ошибка выполнения JS:', e);
            }
        }
    }

    // ============================================================
    // УПРАВЛЕНИЕ ОРИЕНТАЦИЕЙ
    // ============================================================
    function setupOrientationControl() {
        console.log('[Sollor] 🔄 setupOrientationControl запущен');

        function sendOrientationToNative(action) {
            console.log('[Sollor] 📤 sendOrientationToNative action=', action);
            try {
                if (typeof browser !== 'undefined' && browser.runtime) {
                    browser.runtime.sendMessage({
                        type: "SCREEN_ORIENTATION_CONTROL",
                        action: action
                    }).catch(function(err) {
                        console.warn('[Sollor] ⚠️ Ошибка отправки:', err);
                    });
                } else {
                    window.postMessage({
                        type: "SCREEN_ORIENTATION_CONTROL",
                        action: action
                    }, '*');
                }
            } catch (e) {
                console.warn('[Sollor] ⚠️ Не удалось отправить:', e);
            }
        }

        function isVideoLandscape(video) {
            if (!video) return false;
            if (video.videoWidth && video.videoHeight) {
                return video.videoWidth > video.videoHeight;
            }
            return video.clientWidth > video.clientHeight;
        }

        var fullscreenEvents = ['fullscreenchange', 'webkitfullscreenchange', 'mozfullscreenchange'];
        fullscreenEvents.forEach(function(eventName) {
            document.addEventListener(eventName, function() {
                var isFullscreen = !!(document.fullscreenElement ||
                                       document.webkitFullscreenElement ||
                                       document.mozFullScreenElement);
                console.log('[Sollor] 🖥️ fullscreenchange:', eventName, 'isFullscreen=', isFullscreen);

                if (isFullscreen) {
                    var fsElement = document.fullscreenElement || document.webkitFullscreenElement || document.body;
                    var video = fsElement.querySelector('video') || document.querySelector('video');
                    if (video) {
                        console.log('[Sollor] 🎬 video: ' +
                            video.videoWidth + 'x' + video.videoHeight +
                            ' client=' + video.clientWidth + 'x' + video.clientHeight +
                            ' isLandscape=' + isVideoLandscape(video));
                        if (isVideoLandscape(video)) {
                            sendOrientationToNative('landscape');
                        } else {
                            console.log('[Sollor] 📱 Вертикальное видео, ориентация не меняется');
                        }
                    } else {
                        console.log('[Sollor] ❌ Видео не найдено');
                    }
                } else {
                    sendOrientationToNative('unspecified');
                }
            });
        });

        if (document.fullscreenElement || document.webkitFullscreenElement || document.mozFullScreenElement) {
            var fsElement = document.fullscreenElement || document.webkitFullscreenElement || document.body;
            var video = fsElement.querySelector('video') || document.querySelector('video');
            if (video && isVideoLandscape(video)) {
                sendOrientationToNative('landscape');
            }
        }

        document.addEventListener('click', function(e) {
            var target = e.target.closest('[data-plyr="fullscreen"], .plyr__control[data-plyr="fullscreen"]');
            if (target) {
                setTimeout(function() {
                    if (document.fullscreenElement || document.webkitFullscreenElement || document.mozFullScreenElement) {
                        var fsElement = document.fullscreenElement || document.webkitFullscreenElement || document.body;
                        var video = fsElement.querySelector('video') || document.querySelector('video');
                        if (video && isVideoLandscape(video)) {
                            sendOrientationToNative('landscape');
                        }
                    }
                }, 300);
            }
        });
    }

    // ============================================================
    // ЗАПУСК
    // ============================================================
    loadAndInject(CSS_URL, true);
    JS_URLS.forEach(url => loadAndInject(url, false));

    if (document.readyState === 'complete') {
        setupOrientationControl();
    } else {
        window.addEventListener('load', setupOrientationControl);
    }

    console.log('[Sollor] 🚀 loader.js завершил инициализацию');
})();