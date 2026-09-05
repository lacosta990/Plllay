// ============================================================
// ЗАГРУЗЧИК ВНЕШНИХ РЕСУРСОВ + КЭШИРОВАНИЕ + ОРИЕНТАЦИЯ
// ============================================================
(function() {
    console.log('[Sollor] loader.js выполняется');

    // ---- Конфигурация ----
    const CSS_URL = 'https://storage.yandexcloud.net/sollor/injection/sollor.css';
    const JS_URL = 'https://storage.yandexcloud.net/sollor/injection/sollor.js';
    const CACHE_NAME = 'sollor-cache-v1'; // ⬅️ Меняйте при обновлении файлов

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

        // Нет в кэше – загружаем из сети
        console.log('[Sollor] 🌐 Загружаем из сети:', url);
        try {
            const response = await fetch(url);
            if (!response.ok) throw new Error('HTTP ' + response.status);
            const text = await response.text();

            // Сохраняем в кэш
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
                // Используем eval для выполнения в глобальном контексте (чтобы код мог объявлять функции и переменные)
                // Но безопаснее использовать Function constructor или script.textContent
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
    // 2. УПРАВЛЕНИЕ ОРИЕНТАЦИЕЙ (ОТПРАВКА СООБЩЕНИЙ В NATIVE)
    // ============================================================
    function setupOrientationControl() {
        console.log('[Sollor] 🔄 setupOrientationControl запущен');

        function sendOrientationToNative(action) {
            try {
                if (typeof browser !== 'undefined' && browser.runtime) {
                    browser.runtime.sendMessage({
                        type: "SCREEN_ORIENTATION_CONTROL",
                        action: action
                    }).catch(function(err) {
                        console.warn('[Sollor] ⚠️ Ошибка отправки сообщения:', err);
                    });
                } else {
                    // fallback
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
            // Приоритет – метаданные видео
            if (video.videoWidth && video.videoHeight) {
                return video.videoWidth > video.videoHeight;
            }
            // CSS-размеры (если метаданные ещё не загружены)
            return video.clientWidth > video.clientHeight;
        }

        // Отслеживаем Fullscreen
        var fullscreenEvents = ['fullscreenchange', 'webkitfullscreenchange', 'mozfullscreenchange'];
        fullscreenEvents.forEach(function(eventName) {
            document.addEventListener(eventName, function() {
                var isFullscreen = !!(document.fullscreenElement ||
                                       document.webkitFullscreenElement ||
                                       document.mozFullScreenElement);
                console.log('[Sollor] 🖥️ Fullscreen:', isFullscreen);

                if (isFullscreen) {
                    var fsElement = document.fullscreenElement || document.webkitFullscreenElement || document.body;
                    var video = fsElement.querySelector('video') || document.querySelector('video');
                    if (video) {
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

        // Если страница уже в Fullscreen при загрузке
        if (document.fullscreenElement || document.webkitFullscreenElement || document.mozFullScreenElement) {
            var fsElement = document.fullscreenElement || document.webkitFullscreenElement || document.body;
            var video = fsElement.querySelector('video') || document.querySelector('video');
            if (video && isVideoLandscape(video)) {
                sendOrientationToNative('landscape');
            }
        }

        // Для плееров с кастомными кнопками (Plyr)
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
    // 3. ЗАПУСК
    // ============================================================
    // Сначала загружаем ресурсы
    loadFromCacheOrNetwork(CSS_URL, true);
    loadFromCacheOrNetwork(JS_URL, false);

    // Затем настраиваем ориентацию после того, как DOM готов
    if (document.readyState === 'complete') {
        setupOrientationControl();
    } else {
        window.addEventListener('load', setupOrientationControl);
    }

    console.log('[Sollor] 🚀 loader.js завершил инициализацию');
})();