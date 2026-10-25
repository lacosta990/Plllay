// ============================================================
// ЗАГРУЗЧИК ВНЕШНИХ РЕСУРСОВ + КЭШИРОВАНИЕ + ОРИЕНТАЦИЯ
// ============================================================
(function() {
    console.log('[Sollor] loader.js выполняется');

    // Будим background.js, чтобы он поднял native-порт
    try {
        browser.runtime.sendMessage({ type: 'PING' });
    } catch (e) {}

    // ---- Конфигурация ----
    const CSS_URL = 'https://storage.yandexcloud.net/sollor/injection/sollor.css';
    const JS_URLS = [
        'https://storage.yandexcloud.net/sollor/injection/sollor.js',
        'https://storage.yandexcloud.net/sollor/injection/cutter.js',
        'https://storage.yandexcloud.net/sollor/injection/cleaner.js'
    ];
    const CACHE_NAME = 'sollor-cache-v7'; // ⬅️ обновлено

    // ============================================================
    // 1. ЗАГРУЗКА CSS/JS С КЭШИРОВАНИЕМ
    // ============================================================
    async function loadFromCacheOrNetwork(url, isCss) {
        try {
            const cache = await caches.open(CACHE_NAME);
            const cachedResponse = await cache.match(url);
            if (cachedResponse) {
                console.log('[Sollor] ✅ Загружено из кэша:', url);
                const text = await cachedResponse.text();
                insertCode(text, isCss);
                return;
            }
        } catch (e) {
            console.warn('[Sollor] ⚠️ Ошибка чтения кэша:', e);
        }

        console.log('[Sollor] 🌐 Загружаем из сети:', url);
        try {
            const response = await fetch(url);
            if (!response.ok) throw new Error('HTTP ' + response.status);
            const text = await response.text();

            try {
                const cache = await caches.open(CACHE_NAME);
                cache.put(url, new Response(text, {
                    headers: { 'Content-Type': isCss ? 'text/css' : 'application/javascript' }
                }));
            } catch (e) {
                console.warn('[Sollor] ⚠️ Не удалось сохранить в кэш:', e);
            }

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
    // 2. УПРАВЛЕНИЕ ОРИЕНТАЦИЕЙ
    // ============================================================
    function setupOrientationControl() {
        console.log('[Sollor] 🔄 setupOrientationControl запущен');

        function sendOrientationToNative(action) {
            // ⬅️ ДИАГНОСТИКА: видим, что функция вызвана
            console.log('[Sollor] 📤 sendOrientationToNative вызвана с action=', action);
            try {
                if (typeof browser !== 'undefined' && browser.runtime) {
                    browser.runtime.sendMessage({
                        type: "SCREEN_ORIENTATION_CONTROL",
                        action: action
                    }).catch(function(err) {
                        console.warn('[Sollor] ⚠️ Ошибка отправки сообщения:', err);
                    });
                } else {
                    window.postMessage({
                        type: "SCREEN_ORIENTATION_CONTROL",
                        action: action
                    }, '*');
                }
                console.log('[Sollor] 📤 Отправлена команда ориентации:', action);
            } catch (e) {
                console.warn('[Sollor] ⚠️ Не удалось отправить команду:', e);
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

                // ⬅️ ДИАГНОСТИКА: видим, что fullscreenchange сработал
                console.log('[Sollor] 🖥️ fullscreenchange event:', eventName,
                            'isFullscreen=', isFullscreen);

                if (isFullscreen) {
                    var fsElement = document.fullscreenElement || document.webkitFullscreenElement || document.body;
                    var video = fsElement.querySelector('video') || document.querySelector('video');

                    if (video) {
                        // ⬅️ ДИАГНОСТИКА: какие размеры у видео
                        console.log('[Sollor] 🎬 video: ' +
                            'videoWidth=' + video.videoWidth +
                            ' videoHeight=' + video.videoHeight +
                            ' clientWidth=' + video.clientWidth +
                            ' clientHeight=' + video.clientHeight +
                            ' isLandscape=' + isVideoLandscape(video));

                        if (isVideoLandscape(video)) {
                            sendOrientationToNative('landscape');
                        } else {
                            console.log('[Sollor] 📱 Вертикальное видео, ориентация не меняется');
                        }
                    } else {
                        console.log('[Sollor] ❌ Видео не найдено, ориентация не меняется');
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
                console.log('[Sollor] 🖱️ Клик по кнопке Plyr fullscreen');
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
    // 3. ЗАПУСК
    // ============================================================
    loadFromCacheOrNetwork(CSS_URL, true);
    JS_URLS.forEach(url => loadFromCacheOrNetwork(url, false));

    if (document.readyState === 'complete') {
        setupOrientationControl();
    } else {
        window.addEventListener('load', setupOrientationControl);
    }

    console.log('[Sollor] 🚀 loader.js завершил инициализацию');
})();