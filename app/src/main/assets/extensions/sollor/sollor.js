'use strict';

const END_GAP = 0.15;
const PX_PER_SECOND = 12;
const CONTROLS_HIDE_DELAY = 2000;

const controlsHtml = ({ id, title }) => `
  <div class="plyr__controls">

    <!-- Заставка (overlaid) -->
    <button
      type="button"
      class="plyr__control plyr__control--overlaid"
      data-plyr="play"
      aria-label="Play, ${title}"
    >
      <svg class="icon--pressed" role="presentation"><use xlink:href="#plyr-pause"></use></svg>
      <svg class="icon--not-pressed" role="presentation"><use xlink:href="#plyr-play"></use></svg>
      <span class="label--pressed plyr__tooltip" role="tooltip">Pause</span>
      <span class="label--not-pressed plyr__tooltip" role="tooltip">Play</span>
    </button>

    <!-- Центральная кнопка Play для мобилок -->
    <button
      type="button"
      class="plyr__center-play"
      data-plyr="play"
      aria-label="Play, ${title}"
    >
      <svg class="icon--pressed" role="presentation"><use xlink:href="#plyr-pause"></use></svg>
      <svg class="icon--not-pressed" role="presentation"><use xlink:href="#plyr-play"></use></svg>
      <span class="label--pressed plyr__tooltip" role="tooltip">Pause</span>
      <span class="label--not-pressed plyr__tooltip" role="tooltip">Play</span>
    </button>

    <!-- Прогресс-бар -->
    <div class="plyr__progress">
      <label for="seek${id}" class="plyr__sr-only">Seek</label>
      <input data-plyr="seek" id="seek${id}" type="range" min="0" max="100" step="0.01" value="0" aria-label="Seek" />
      <progress class="plyr__progress__buffer" min="0" max="100" value="0">% buffered</progress>
      <span role="tooltip" class="plyr__tooltip">00:00</span>
    </div>

    <!-- Строка кнопок -->
    <div class="plyr__controls-row">
      <div class="plyr__controls-left">
        <button type="button" class="plyr__control" data-plyr="play" aria-label="Play, ${title}">
          <svg class="icon--pressed" role="presentation"><use xlink:href="#plyr-pause"></use></svg>
          <svg class="icon--not-pressed" role="presentation"><use xlink:href="#plyr-play"></use></svg>
          <span class="label--pressed plyr__tooltip" role="tooltip">Pause</span>
          <span class="label--not-pressed plyr__tooltip" role="tooltip">Play</span>
        </button>
        <button type="button" class="plyr__control" data-plyr="mute" aria-label="Mute">
          <svg class="icon--pressed" role="presentation"><use xlink:href="#plyr-muted"></use></svg>
          <svg class="icon--not-pressed" role="presentation"><use xlink:href="#plyr-volume"></use></svg>
          <span class="label--pressed plyr__tooltip" role="tooltip">Unmute</span>
          <span class="label--not-pressed plyr__tooltip" role="tooltip">Mute</span>
        </button>
        <div class="plyr__time plyr__time--current" aria-label="Current time">00:00</div>
        <div class="plyr__time plyr__time--duration" aria-label="Duration">00:00</div>
      </div>
      <div class="plyr__controls-right">
        <button type="button" class="plyr__control" data-plyr="captions" aria-label="Captions">
          <svg class="icon--pressed" role="presentation"><use xlink:href="#plyr-captions-on"></use></svg>
          <svg class="icon--not-pressed" role="presentation"><use xlink:href="#plyr-captions-off"></use></svg>
          <span class="label--pressed plyr__tooltip" role="tooltip">Disable captions</span>
          <span class="label--not-pressed plyr__tooltip" role="tooltip">Enable captions</span>
        </button>
        <button type="button" class="plyr__control" data-plyr="settings" aria-label="Settings">
          <svg role="presentation"><use xlink:href="#plyr-settings"></use></svg>
          <span class="plyr__tooltip" role="tooltip">Quality</span>
        </button>
        <button type="button" class="plyr__control" data-plyr="fullscreen" aria-label="Fullscreen">
          <svg class="icon--pressed" role="presentation"><use xlink:href="#plyr-exit-fullscreen"></use></svg>
          <svg class="icon--not-pressed" role="presentation"><use xlink:href="#plyr-enter-fullscreen"></use></svg>
          <span class="label--pressed plyr__tooltip" role="tooltip">Exit fullscreen</span>
          <span class="label--not-pressed plyr__tooltip" role="tooltip">Enter fullscreen</span>
        </button>
      </div>
    </div>

    <!-- Вертикальный скраббер -->
    <div class="plyr__vertical-scrub" id="vs_wrap" aria-label="Vertical seek">
      <div class="plyr__vertical-scrub__center" id="vs_center">
        <div class="plyr__vertical-scrub__tape" id="vs_tape"></div>
        <div class="plyr__vertical-scrub__marker"></div>
      </div>
      <div class="plyr__vertical-scrub__tooltip" id="vs_tip">0:00</div>
    </div>

    <!-- Регулятор громкости (скрыт) -->
    <div class="plyr__volume">
      <input data-plyr="volume" type="range" min="0" max="1" step="0.05" value="1" autocomplete="off" aria-label="Volume" />
    </div>

  </div>
`;

// Инициализация плеера
const player = new Plyr('#player', {
  controls: controlsHtml,
  hideControls: false,
  clickToPlay: false,
  tooltips: { controls: true, seek: true },
  captions: { active: true, update: false },
  keyboard: { focused: true, global: true },
  seekTime: 10
});

const mainVideo = document.getElementById('player');
const clamp = (value, min, max) => Math.max(min, Math.min(max, value));
const formatTime = (time) => {
  if (!time || Number.isNaN(time)) return '0:00';
  const minutes = Math.floor(time / 60);
  const seconds = Math.floor(time % 60);
  return `${minutes}:${String(seconds).padStart(2, '0')}`;
};

// Управление таймером скрытия контролов
const controlsState = { timer: null, locked: false };
const controlsElement = () => player.elements?.container?.querySelector('.plyr__controls');

const showControls = () => {
  const container = player.elements?.container;
  if (!container) return;
  container.classList.remove('plyr--hide-controls');
  const controls = controlsElement();
  if (controls) {
    controls.style.opacity = '1';
    controls.style.visibility = 'visible';
    controls.style.pointerEvents = 'auto';
  }
};

const hideControls = () => {
  const container = player.elements?.container;
  if (!container) return;
  if (controlsState.locked || player.paused) return;
  container.classList.add('plyr--hide-controls');
  const controls = controlsElement();
  if (controls) {
    controls.style.opacity = '';
    controls.style.visibility = '';
    controls.style.pointerEvents = '';
  }
};

const restartControlsTimer = () => {
  clearTimeout(controlsState.timer);
  showControls();
  if (player.paused || controlsState.locked) return;
  controlsState.timer = setTimeout(hideControls, CONTROLS_HIDE_DELAY);
};

const lockControls = () => {
  controlsState.locked = true;
  clearTimeout(controlsState.timer);
  showControls();
};

const unlockControls = () => {
  controlsState.locked = false;
  restartControlsTimer();
};

// Вертикальный скраббер
const initVerticalScrubber = () => {
  const center = document.getElementById('vs_center');
  const tape = document.getElementById('vs_tape');
  const tip = document.getElementById('vs_tip');
  if (!center || !tape || !tip) return;

  let dragging = false;
  let activePointerId = null;
  let startY = 0;
  let startOffset = 0;
  let offset = 0;
  let wasPlaying = false;

  const getMaxTime = () => Math.max(0, (player.duration || 0) - END_GAP);

  const renderTape = () => {
    tape.innerHTML = '';
    const duration = player.duration || 0;
    if (!duration) {
      tape.style.height = '0px';
      return;
    }
    tape.style.height = `${Math.ceil(duration * PX_PER_SECOND)}px`;
    for (let sec = 0; sec <= Math.ceil(duration); sec++) {
      const tick = document.createElement('span');
      tick.className = 'plyr__vertical-scrub__tick';
      if (sec % 5 !== 0) tick.classList.add('plyr__vertical-scrub__tick--dim');
      tick.style.top = `${sec * PX_PER_SECOND}px`;
      tape.appendChild(tick);
    }
  };

  const setTapeOffset = (value) => {
    tape.style.transform = `translateX(-50%) translateY(-${value}px)`;
  };

  const timeFromOffset = (pixels) => {
    const duration = player.duration || 0;
    if (!duration) return 0;
    return clamp(pixels / PX_PER_SECOND, 0, getMaxTime());
  };

  const syncFromTime = () => {
    if (dragging) return;
    const current = clamp(player.currentTime || 0, 0, getMaxTime());
    offset = current * PX_PER_SECOND;
    setTapeOffset(offset);
    tip.style.display = 'none';
  };

  const seekTo = (time) => {
    const target = clamp(time, 0, getMaxTime());
    mainVideo.currentTime = target;
  };

  const moveByPointer = (event) => {
    if (!dragging) return;
    const deltaY = startY - event.clientY;
    const totalPixels = (player.duration || 0) * PX_PER_SECOND;
    offset = clamp(startOffset + deltaY, 0, totalPixels);
    const current = timeFromOffset(offset);
    setTapeOffset(offset);
    tip.style.display = 'block';
    tip.textContent = formatTime(current);
    seekTo(current);
  };

  const startDrag = (event) => {
    if (!player.duration || event.button !== 0) return;
    dragging = true;
    activePointerId = event.pointerId;
    wasPlaying = !player.paused;
    lockControls();
    player.pause();
    startY = event.clientY;
    startOffset = offset;
    try { center.setPointerCapture(event.pointerId); } catch {}
    moveByPointer(event);
    event.preventDefault();
  };

  const finishDrag = (event) => {
    if (!dragging || (activePointerId !== null && event.pointerId !== activePointerId)) return;
    dragging = false;
    tip.style.display = 'none';
    try { if (center.hasPointerCapture?.(event.pointerId)) center.releasePointerCapture(event.pointerId); } catch {}
    activePointerId = null;
    if (wasPlaying && player.currentTime < getMaxTime()) player.play().catch(() => {});
    unlockControls();
  };

  center.addEventListener('pointerdown', startDrag, { passive: false });
  center.addEventListener('pointermove', (event) => {
    if (!dragging || (activePointerId !== null && event.pointerId !== activePointerId)) return;
    moveByPointer(event);
    event.preventDefault();
  }, { passive: false });
  center.addEventListener('pointerup', finishDrag);
  center.addEventListener('pointercancel', finishDrag);
  center.addEventListener('lostpointercapture', (event) => { if (dragging) finishDrag(event); });

  player.on('loadedmetadata', () => { renderTape(); syncFromTime(); });
  player.on('ready', () => { renderTape(); syncFromTime(); });
  player.on('timeupdate', syncFromTime);
  player.on('seeked', syncFromTime);
  player.on('durationchange', () => { renderTape(); syncFromTime(); });
  window.addEventListener('resize', syncFromTime);
  renderTape();
  syncFromTime();
};

// Определение сенсорного устройства
const isTouchDevice = () => {
  return ('ontouchstart' in window) || (navigator.maxTouchPoints > 0) || (navigator.msMaxTouchPoints > 0);
};

const container = player.elements.container;
if (isTouchDevice()) container.classList.add('is-touch');

// Универсальная функция для показа контролов
const showControlsAndResetTimer = () => {
  if (controlsState.locked) return;
  showControls();
  clearTimeout(controlsState.timer);
  if (!player.paused && !controlsState.locked) {
    controlsState.timer = setTimeout(hideControls, CONTROLS_HIDE_DELAY);
  }
};

// Обработчик клика по контейнеру
container.addEventListener('click', (event) => {
  if (event.target.closest('[data-plyr="play"]')) {
    if (container.classList.contains('plyr--hide-controls')) {
      event.preventDefault();
      showControlsAndResetTimer();
      return;
    }
    return;
  }
  showControlsAndResetTimer();
  if (!isTouchDevice()) {
    const isControl = event.target.closest(
      '.plyr__control, .plyr__progress, .plyr__time, .plyr__menu, ' +
      '.plyr__tooltip, .plyr__volume, .plyr__vertical-scrub'
    );
    if (!isControl) player.togglePlay();
  }
});

container.addEventListener('pointermove', () => {
  if (!controlsState.locked) showControlsAndResetTimer();
}, { passive: true });
container.addEventListener('focusin', () => {
  if (!controlsState.locked) showControlsAndResetTimer();
});

player.on('play', () => {
  if (!controlsState.locked) showControlsAndResetTimer();
});
player.on('pause', () => {
  clearTimeout(controlsState.timer);
  showControls();
});
player.on('ended', () => {
  clearTimeout(controlsState.timer);
  showControls();
});

// Первое воспроизведение – удаляем overlaid и переключаем состояния
let isFirstPlay = true;
container.classList.add('plyr--initial');

player.on('play', () => {
  if (isFirstPlay) {
    isFirstPlay = false;
    container.classList.remove('plyr--initial');
    container.classList.add('plyr--started');
    const overlaid = container.querySelector('.plyr__control--overlaid');
    if (overlaid) overlaid.remove();
    showControlsAndResetTimer();
  }
});

// Управление полноэкранным режимом и ориентацией
document.addEventListener('fullscreenchange', () => {
  const isFullscreen = !!document.fullscreenElement;
  container.classList.toggle('is-fullscreen', isFullscreen);
  if (isFullscreen && isTouchDevice() && screen.orientation) {
    screen.orientation.lock('landscape').catch(() => {});
  } else if (!isFullscreen && screen.orientation) {
    screen.orientation.unlock().catch(() => {});
  }
});
window.addEventListener('resize', () => {
  if (document.fullscreenElement && isTouchDevice() && screen.orientation) {
    screen.orientation.lock('landscape').catch(() => {});
  }
});

// Инициализация скраббера и показ контролов
player.on('ready', () => {
  initVerticalScrubber();
  showControls();
});