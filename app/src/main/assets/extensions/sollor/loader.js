// loader.js — загрузка внешних скриптов + ориентация в fullscreen
(function () {
    'use strict';

    const SOLLOR_VER = '9.20';
    const BASE = 'https://storage.yandexcloud.net/sollor/injection';
    const CSS_URL = BASE + '/sollor.css?v=' + SOLLOR_VER;
    const JS_URLS = [
        BASE + '/sollor.js?v=' + SOLLOR_VER,
        BASE + '/cutter.js?v=' + SOLLOR_VER,
        BASE + '/plus.js?v=' + SOLLOR_VER,
        BASE + '/cleaner.js?v=' + SOLLOR_VER
    ];
    const STORAGE_PREFIX = 'sollor_';
    const REFRESH = new URLSearchParams(location.search).get('sollor') === 'refresh';

    try { browser.runtime.sendMessage({ type: 'PING' }); } catch (e) {}

    function key(url) { return STORAGE_PREFIX + url; }

    async function read(url) {
        try { const r = await browser.storage.local.get(key(url)); return r[key(url)] || null; }
        catch (e) { return null; }
    }
    async function write(url, text) {
        try { const o = {}; o[key(url)] = text; await browser.storage.local.set(o); } catch (e) {}
    }
    async function fromCloud(url) {
        const res = await fetch(url);
        if (!res.ok) throw new Error('HTTP ' + res.status);
        return await res.text();
    }

    function insert(text, isCss) {
        try {
            if (isCss) {
                const s = document.createElement('style');
                s.textContent = text;
                (document.head || document.documentElement).appendChild(s);
            } else {
                const s = document.createElement('script');
                s.textContent = text;
                (document.head || document.documentElement).appendChild(s);
            }
        } catch (e) { console.warn('[Sollor] insert:', e); }
    }

    async function loadAndInject(url, isCss) {
        if (!REFRESH) {
            const cached = await read(url);
            if (cached) { insert(cached, isCss); return; }
        }
        try {
            const text = await fromCloud(url);
            await write(url, text);
            insert(text, isCss);
        } catch (e) {
            // Фолбэк: если версионированный URL ещё не залит — берём без ?v=
            if (url.indexOf('?v=') > 0) {
                try {
                    const plain = url.split('?')[0];
                    const text = await fromCloud(plain);
                    await write(plain, text);
                    insert(text, isCss);
                    return;
                } catch (e2) {}
            }
            const cached = await read(url);
            if (cached) insert(cached, isCss);
            else console.warn('[Sollor] не загрузилось:', url, e);
        }
    }

    // ================= FULLSCREEN + ОРИЕНТАЦИЯ =================
    let exitTimer = null;
    let lastOrientation = null;
    let lastFullscreen = null;
    let sizeWaitHooked = false;

    function post(msg) {
        try {
            if (typeof browser !== 'undefined' && browser.runtime) {
                browser.runtime.sendMessage(msg).catch(function () {});
            } else {
                window.postMessage(msg, '*');
            }
        } catch (e) {}
    }

    function uiLog(layer, msg) {
        try {
            const pw = window.wrappedJSObject || window;
            if (pw.Sollor && typeof pw.Sollor.log === 'function') pw.Sollor.log(layer, msg);
        } catch (e) {}
    }

    function sendOrientation(action) {
        if (action === lastOrientation) return;
        lastOrientation = action;
        uiLog('fs', 'orientation → ' + action);
        post({ type: 'SCREEN_ORIENTATION_CONTROL', action: action });
    }

    function sendFullscreen(fs) {
        if (fs === lastFullscreen) return;
        lastFullscreen = fs;
        uiLog('fs', 'fullscreen=' + fs);
        post({ type: 'FULLSCREEN_STATE', fullscreen: !!fs });
    }

    // Рекурсивный поиск <video>, включая shadow DOM
    function deepVideos(root, out) {
        out = out || [];
        if (!root || !root.querySelectorAll) return out;
        try {
            root.querySelectorAll('video').forEach(function (v) { out.push(v); });
        } catch (e) {}
        try {
            root.querySelectorAll('*').forEach(function (n) {
                if (n.shadowRoot) deepVideos(n.shadowRoot, out);
            });
        } catch (e) {}
        return out;
    }

    function area(v) {
        try { const r = v.getBoundingClientRect(); return (r.width || 0) * (r.height || 0); }
        catch (e) { return 0; }
    }

    function pickVideo() {
        const all = deepVideos(document);
        if (!all.length) return null;
        // Скрытая реклама имеет нулевую площадь — её отбрасываем
        const visible = all.filter(function (v) { return area(v) > 0; });
        const pool = visible.length ? visible : all;
        for (let i = 0; i < pool.length; i++) {
            if (!pool[i].paused && pool[i].readyState >= 2) return pool[i];
        }
        for (let i = 0; i < pool.length; i++) {
            if (isFinite(pool[i].duration) && pool[i].duration > 0) return pool[i];
        }
        return pool.sort(function (a, b) { return area(b) - area(a); })[0];
    }

    function sizeKnown(v) { return !!(v && v.videoWidth > 0 && v.videoHeight > 0); }

    function inFullscreen() {
        if (document.fullscreenElement || document.webkitFullscreenElement ||
            document.mozFullScreenElement) return true;
        // GeckoView не всегда выставляет fullscreenElement — смотрим классы плееров
        try {
            if (document.querySelector('.jwplayer.jw-flag-fullscreen')) return true;
            if (document.querySelector('.video-js.vjs-fullscreen')) return true;
            if (document.querySelector('.plyr--fullscreen')) return true;
        } catch (e) {}
        return false;
    }

    function waitForSize() {
        if (sizeWaitHooked) return;
        sizeWaitHooked = true;
        const video = pickVideo();
        if (!video) { sizeWaitHooked = false; return; }
        const events = ['loadedmetadata', 'resize', 'playing', 'canplay'];
        let done = false;
        const finish = function () {
            if (done) return;
            done = true;
            events.forEach(function (ev) { video.removeEventListener(ev, finish); });
            sizeWaitHooked = false;
            decide();
        };
        events.forEach(function (ev) { video.addEventListener(ev, finish); });
        setTimeout(finish, 2000); // страховка
    }

    function decide() {
        const fs = inFullscreen();
        sendFullscreen(fs);

        if (!fs) {
            clearTimeout(exitTimer);
            exitTimer = setTimeout(function () {
                if (!inFullscreen()) sendOrientation('unspecified');
            }, 300);
            return;
        }

        const video = pickVideo();
        if (!video) { uiLog('fs', 'видео не найдено'); return; }
        if (!sizeKnown(video)) { uiLog('fs', 'ждём размеры видео'); waitForSize(); return; }

        const wide = video.videoWidth > video.videoHeight;
        uiLog('fs', 'видео ' + video.videoWidth + 'x' + video.videoHeight + ' → ' + (wide ? 'landscape' : 'portrait'));
        sendOrientation(wide ? 'landscape' : 'portrait');
    }

    function onFullscreenEvent() {
        decide();
        // Gecko может выставить состояние чуть позже — перепроверяем
        setTimeout(decide, 200);
    }

    ['fullscreenchange', 'webkitfullscreenchange', 'mozfullscreenchange'].forEach(function (ev) {
        document.addEventListener(ev, onFullscreenEvent);
    });

    // Клик по кнопке fullscreen у плеера — тоже сигнал
    document.addEventListener('click', function (e) {
        const t = e.target;
        if (!t || !t.closest) return;
        if (t.closest('[data-plyr="fullscreen"], .jw-icon-fullscreen, .vjs-fullscreen-control, .ytp-fullscreen-button')) {
            setTimeout(onFullscreenEvent, 300);
        }
    }, true);

    // ================= ЗАПУСК =================
    loadAndInject(CSS_URL, true);
    JS_URLS.forEach(function (u) { loadAndInject(u, false); });

    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', function () { if (inFullscreen()) decide(); });
    } else if (inFullscreen()) {
        decide();
    }
})();
