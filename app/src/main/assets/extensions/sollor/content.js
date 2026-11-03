// content.js — «руки» расширения на странице
'use strict';

console.log('[sollor-content] загружен');
try { browser.runtime.sendMessage({ type: 'REGISTER_TAB' }); } catch (e) {}

const VIDEO_ACTIONS = {
    'play-pause': 'toggle_play',
    'rewind': 'seek_back',
    'forward': 'seek_fwd',
    'mute': 'mute',
    'fullscreen': 'fullscreen',
    'cover': 'cover',
    'cover_on': 'cover_on',
    'cover_off': 'cover_off',
    'pause': 'pause',
    'seek_to': 'seek_frac',
    'seek_frac': 'seek_frac'
};

const SOLLOR_VER = '9.21';
const BASE = 'https://storage.yandexcloud.net/sollor/injection';
const CSS_URL = BASE + '/sollor.css?v=' + SOLLOR_VER;
const JS_URLS = [
    BASE + '/sollor.js?v=' + SOLLOR_VER,
    BASE + '/cutter.js?v=' + SOLLOR_VER,
    BASE + '/plus.js?v=' + SOLLOR_VER,
    BASE + '/cleaner.js?v=' + SOLLOR_VER
];
const PREFIX = 'sollor_';

function pageWindow() {
    try { return window.wrappedJSObject || window; } catch (e) { return window; }
}

function log(msg) {
    try {
        const pw = pageWindow();
        if (pw.Sollor && typeof pw.Sollor.log === 'function') pw.Sollor.log('ext', msg);
        console.log('[sollor-content]', msg);
    } catch (e) {}
}

function executeVideoValue(action, value) {
    const pw = pageWindow();
    if (!pw || typeof pw.__sollorExecuteVideoCommand !== 'function') {
        return { ok: false, code: 'ENGINE_MISSING', error: 'нет движка' };
    }
    try {
        const res = pw.__sollorExecuteVideoCommand(action, value);
        return { ok: !!(res && res.ok), error: res && res.reason };
    } catch (e) {
        return { ok: false, code: 'CALL_THROWN', error: String(e) };
    }
}

function executeVideo(action) {
    const pw = pageWindow();
    if (!pw.__sollorLoaded) return { ok: false, code: 'SCRIPT_MISSING', error: 'sollor.js не загружен' };
    if (typeof pw.__sollorExecuteVideoCommand !== 'function') {
        return { ok: false, code: 'ENGINE_MISSING', error: 'нет движка (версия ' + pw.__sollorLoaded + ')' };
    }
    try {
        const res = pw.__sollorExecuteVideoCommand(action);
        // Объекты из мира страницы нельзя клонировать через границу расширения —
        // прогоняем через JSON (иначе DataCloneError).
        let state = null;
        try {
            const raw = pw.Sollor && pw.Sollor.getState ? pw.Sollor.getState() : null;
            state = raw ? JSON.parse(JSON.stringify(raw)) : null;
        } catch (e) { state = null; }
        return { ok: !!(res && res.ok), state: state,
                 error: (res && res.reason) ? String(res.reason) : null };
    } catch (e) {
        return { ok: false, code: 'CALL_THROWN', error: String(e) };
    }
}

async function store(url) {
    const res = await fetch(url);
    if (!res.ok) throw new Error('HTTP ' + res.status);
    const text = await res.text();
    const o = {}; o[PREFIX + url] = text;
    await browser.storage.local.set(o);
}

// Страница шлёт состояние — пробрасываем в натив
let stateSeq = 0;
document.addEventListener('sollor-state', (e) => {
    try {
        const data = e && e.detail ? JSON.parse(JSON.stringify(e.detail)) : null;
        if (!data) return;
        const pw0 = pageWindow();
        if (!pw0.__sollorAttachedOnce) return;   // плеер не найден — не шлём мусор

        // Площадь видео относительно вьюпорта: по ней натив выберет владельца
        const r = data.rect || {};
        const vpArea = Math.max(1, (data.vpWidth || 1) * (data.vpHeight || 1));
        data.areaRatio = ((r.w || 0) * (r.h || 0)) / vpArea;
        data.topFrame = (window === window.top);
        data.seq = ++stateSeq;
        // Нулевая длительность от невидимых превью — не шлём вообще
        if (!(data.duration > 1) && !data.topFrame) return;
        browser.runtime.sendMessage({ type: 'VIDEO_INFO', data: data }).catch(() => {});
    } catch (err) {}
});

// Состояние скролла — отдельным типом сообщения, чтобы не смешивать с данными видео
let scrollTimer = null;
let scrollSent = false;
window.addEventListener('scroll', () => {
    if (!scrollSent) {
        scrollSent = true;
        browser.runtime.sendMessage({ type: 'VIDEO_SCROLL', scrolling: true }).catch(() => {});
    }
    clearTimeout(scrollTimer);
    scrollTimer = setTimeout(() => {
        scrollSent = false;
        browser.runtime.sendMessage({ type: 'VIDEO_SCROLL', scrolling: false }).catch(() => {});
    }, 400);
}, { passive: true });

browser.runtime.onMessage.addListener((message, sender, sendResponse) => {
    if (!message) return false;

    if (message.type === 'TOGGLE_SIDE') {
        const pw = pageWindow();
        try {
            if (pw.Sollor && typeof pw.Sollor.toggleSide === 'function') {
                sendResponse({ side: String(pw.Sollor.toggleSide()) });
            } else {
                sendResponse({ error: 'нет Sollor.toggleSide' });
            }
        } catch (e) {
            sendResponse({ error: String(e) });
        }
        return true;
    }

    if (message.type === 'SOLLOR_VIDEO') {
        const action = VIDEO_ACTIONS[message.action];
        if (!action) {
            log('НЕИЗВЕСТНОЕ действие: ' + message.action);
            sendResponse({ ok: false, error: 'unknown action ' + message.action });
            return true;
        }
        log('нажато: ' + message.action + (message.value != null ? ' ' + message.value : ''));
        const res = (message.value != null)
            ? executeVideoValue(action, message.value)
            : executeVideo(action);
        log((res.ok ? 'OK' : 'FAIL') + ' | ' + message.action + (res.error ? ' | ' + res.error : ''));
        sendResponse(res);
        return true;
    }

    if (message.type === 'RELOAD_SCRIPTS_IN_PAGE') {
        (async () => {
            try {
                const urls = [CSS_URL].concat(JS_URLS);
                for (const u of urls) await store(u);
                sendResponse({ ok: true, count: urls.length });
                setTimeout(() => location.reload(), 200);
            } catch (e) {
                sendResponse({ error: String(e) });
            }
        })();
        return true;
    }

    return false;
});
