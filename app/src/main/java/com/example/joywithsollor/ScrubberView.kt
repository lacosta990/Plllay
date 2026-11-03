package com.example.joywithsollor

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.os.SystemClock
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Нативный вертикальный скраббер.
 *
 * Плавность: собственный кадровый цикл (postOnAnimation) интерполирует позицию
 * между редкими синхронизациями с плеером. Перемотка отправляется не на каждое
 * движение пальца, а с дебаунсом 100 мс и финально — на отпускании.
 */
class ScrubberView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    private val density = resources.displayMetrics.density
    private fun dp(v: Float) = v * density

    // Геометрия
    private val visualWidthDp = 24f
    private val visualInsetDp = 8f
    private val tickWidthDp = 24f
    private val tickHeightDp = 2f
    private val markerWidthDp = 24f
    private val markerHeightDp = 3f
    private val padTopDp = 8f
    private val padBottomDp = 8f

    // Время
    private val syncIntervalMs = 1000L
    private val syncStaleMs = 1500L
    private val seekDebounceMs = 100L
    private val expectedToleranceSec = 0.5
    private val expectedWindowMs = 1500L

    private val tickPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        alpha = 191 // ~75%
    }
    private val markerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF00E5FF.toInt()
        textSize = dp(12f)
    }
    private val pillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xD9000000.toInt() }
    private val pillRect = RectF()
    private val markerRect = RectF()

    var duration: Double = 0.0
        set(value) { field = value.coerceAtLeast(0.0); invalidate() }

    /** Позиция, которую показывает скраббер (может быть интерполированной) */
    var position: Double = 0.0
        private set

    var rate: Float = 1f
    var playing: Boolean = false
    var buffering: Boolean = false
    var seeking: Boolean = false
    var sideLeft: Boolean = false
        set(value) { field = value; invalidate() }

    var pxPerSecond: Float = dp(12f)

    var onSeek: ((Double) -> Unit)? = null
    var onSeekStart: (() -> Unit)? = null
    var onSeekEnd: (() -> Unit)? = null

    private var dragging = false
    private var dragStartY = 0f
    private var dragStartPos = 0.0
    private var lastFrameAt = 0L
    private var lastSyncAt = 0L
    private var expectedSeek: Double? = null
    private var expectedUntil = 0L
    private var seekRunnable: Runnable? = null

    // Инерция после свайпа
    private var velocityTracker: android.view.VelocityTracker? = null
    private var inertiaRunning = false
    private var inertiaVelocity = 0f
    private val inertiaFriction = 0.92f        // затухание за 16 мс
    private val inertiaStopPxPerSec = 10f
    private val inertiaMinPxPerSec = 50f
    private val inertiaMaxPxPerSec = 5000f
    private var lastInertiaSeekAt = 0L
    private val inertiaSeekIntervalMs = 120L

    /** Вызывается из состояния плеера: позиция, время */
    fun onPlayerPosition(sec: Double) {
        if (dragging || inertiaRunning) return
        val now = SystemClock.uptimeMillis()
        val exp = expectedSeek
        if (exp != null) {
            if (abs(sec - exp) <= expectedToleranceSec) {
                expectedSeek = null            // плеер догнал — доверяем ему
            } else if (buffering || seeking) {
                // перемотка на непробуференный участок: ждём, сколько нужно
                expectedUntil = now + 5000L
                return
            } else if (now < expectedUntil) {
                return
            } else {
                expectedSeek = null            // перемотка не удалась — берём как есть
            }
        }
        position = sec.coerceIn(0.0, if (duration > 0) duration else sec)
        lastSyncAt = now
        invalidate()
        scheduleFrame()   // цикл не должен умирать, если он остановился
    }

    fun onPlayerFlags(isPlaying: Boolean, isBuffering: Boolean, playbackRate: Float, isSeeking: Boolean = false) {
        playing = isPlaying
        buffering = isBuffering
        seeking = isSeeking
        rate = if (playbackRate > 0f) playbackRate else 1f
        lastSyncAt = SystemClock.uptimeMillis()
        scheduleFrame()
    }

    private val frameCallback = object : Runnable {
        override fun run() {
            val now = SystemClock.uptimeMillis()
            val dt = if (lastFrameAt == 0L) 0L else now - lastFrameAt
            lastFrameAt = now

            if (inertiaRunning && !dragging && duration > 0.0) {
                if (dt in 1..200) {
                    val dtSec = (dt.coerceAtMost(50L)) / 1000.0
                    position = (position + inertiaVelocity * dtSec / pxPerSecond)
                        .coerceIn(0.0, duration)
                    inertiaVelocity *= Math.pow(inertiaFriction.toDouble(), dtSec * 60.0).toFloat()
                    invalidate()
                    // Как в JS: во время инерции плеер не трогаем —
                    // перемотка уходит один раз, когда движение затухло.
                }
                if (abs(inertiaVelocity) < inertiaStopPxPerSec) {
                    inertiaRunning = false
                    expectedSeek = position
                    expectedUntil = SystemClock.uptimeMillis() + expectedWindowMs
                    onSeek?.invoke(position)
                    onSeekEnd?.invoke()
                }
            }

            if (!dragging && duration > 0.0) {
                if (!inertiaRunning && playing && !buffering && dt in 1..200) {
                    position = (position + dt / 1000.0 * rate).coerceAtMost(duration)
                }
                // принудительная синхронизация, если данных давно нет
                if (now - lastSyncAt > syncStaleMs) expectedSeek = null
                invalidate()
                scheduleFrame()
            } else {
                lastFrameAt = 0L
            }
        }
    }

    /** Разбудить кадровый цикл (после показа вью, смены вкладки и т.п.). */
    fun resume() {
        lastFrameAt = 0L
        scheduleFrame()
    }

    private fun scheduleFrame() {
        if (dragging || duration <= 0.0) return
        removeCallbacks(frameCallback)
        postOnAnimation(frameCallback)
    }

    override fun onAttachedToWindow() { super.onAttachedToWindow(); scheduleFrame() }
    override fun onDetachedFromWindow() { removeCallbacks(frameCallback); super.onDetachedFromWindow() }

    override fun onVisibilityChanged(changedView: View, visibility: Int) {
        super.onVisibilityChanged(changedView, visibility)
        if (visibility == VISIBLE) resume()
    }

    private fun stripLeft(): Float = if (sideLeft) 0f else width - dp(56f)

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(
            MeasureSpec.makeMeasureSpec(dp(160f).toInt(), MeasureSpec.EXACTLY),
            heightMeasureSpec
        )
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (duration <= 0.0) return

        val topPad = dp(padTopDp)
        val bottomPad = dp(padBottomDp)
        val usableH = height - topPad - bottomPad
        if (usableH <= 0f) return

        val cy = topPad + usableH / 2f
        val visual = dp(visualWidthDp)
        val inset = dp(visualInsetDp)
        val left = if (sideLeft) inset else width - inset - visual
        val right = left + visual

        val offsetPx = (position * pxPerSecond).toFloat()
        val half = usableH / 2f
        val firstSec = ((offsetPx - half) / pxPerSecond).toInt() - 1
        val lastSec = ((offsetPx + half) / pxPerSecond).toInt() + 1
        val tickW = dp(tickWidthDp)
        val tickH = dp(tickHeightDp)
        val fadeBand = usableH * 0.12f

        for (sec in firstSec..lastSec) {
            if (sec < 0 || sec > duration) continue
            val y = cy + (sec * pxPerSecond - offsetPx)
            if (y < topPad - tickH || y > height - bottomPad + tickH) continue
            val local = y - topPad
            val fade = when {
                local < fadeBand -> (local / fadeBand).coerceIn(0f, 1f)
                local > usableH - fadeBand -> ((usableH - local) / fadeBand).coerceIn(0f, 1f)
                else -> 1f
            }
            tickPaint.alpha = (191 * fade).toInt().coerceIn(0, 255)
            val tickLeft = if (sideLeft) left else right - tickW
            canvas.drawRect(tickLeft, y - tickH / 2f, tickLeft + tickW, y + tickH / 2f, tickPaint)
        }

        val mw = dp(markerWidthDp)
        val mh = dp(markerHeightDp)
        val mx = if (sideLeft) left else right - mw
        markerRect.set(mx, cy - mh / 2f, mx + mw, cy + mh / 2f)
        markerPaint.alpha = 255
        canvas.drawRoundRect(markerRect, mh / 2f, mh / 2f, markerPaint)

        // Таймкод показываем, только когда пользователь взаимодействует
        if (dragging || inertiaRunning) {
            val total = position.toInt().coerceAtLeast(0)
            val label = String.format("%d:%02d", total / 60, total % 60)
            val tw = textPaint.measureText(label)
            val padH = dp(8f)
            val padV = dp(6f)
            val gap = dp(12f)
            val pillW = tw + padH * 2
            val pillH = textPaint.textSize + padV * 2
            val pillX = if (sideLeft) right + gap else left - gap - pillW
            val pillY = cy - pillH / 2f
            pillRect.set(pillX, pillY, pillX + pillW, pillY + pillH)
            canvas.drawRoundRect(pillRect, dp(6f), dp(6f), pillPaint)
            canvas.drawText(label, pillX + padH, cy + textPaint.textSize / 3f, textPaint)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (duration <= 0.0) return false

        // Полосу проверяем ТОЛЬКО при касании: дальше жест наш,
        // даже если палец уйдёт в сторону.
        if (event.actionMasked == MotionEvent.ACTION_DOWN) {
            val stripStart = stripLeft()
            if (event.x < stripStart || event.x > stripStart + dp(56f)) return false
        }

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                dragging = true
                inertiaRunning = false
                parent?.requestDisallowInterceptTouchEvent(true)
                velocityTracker?.recycle()
                velocityTracker = android.view.VelocityTracker.obtain()
                velocityTracker?.addMovement(event)
                dragStartY = event.y
                dragStartPos = position
                onSeekStart?.invoke()
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (!dragging) return false
                velocityTracker?.addMovement(event)
                val deltaSec = ((dragStartY - event.y) / pxPerSecond).toDouble()
                position = (dragStartPos + deltaSec).coerceIn(0.0, duration)
                invalidate()
                scheduleSeek()
                return true
            }
            MotionEvent.ACTION_UP -> {
                if (!dragging) return false
                dragging = false
                parent?.requestDisallowInterceptTouchEvent(false)
                cancelSeek()
                velocityTracker?.computeCurrentVelocity(1000)
                val v = velocityTracker?.yVelocity ?: 0f
                velocityTracker?.recycle()
                velocityTracker = null
                val clamped = v.coerceIn(-inertiaMaxPxPerSec, inertiaMaxPxPerSec)
                if (abs(clamped) > inertiaMinPxPerSec) {
                    inertiaRunning = true
                    lastInertiaSeekAt = SystemClock.uptimeMillis()
                    inertiaVelocity = -clamped   // свайп вверх (v<0) двигает вперёд
                    lastFrameAt = 0L
                    postOnAnimation(frameCallback)
                    return true
                }
                expectedSeek = position
                expectedUntil = SystemClock.uptimeMillis() + expectedWindowMs
                onSeek?.invoke(position)
                onSeekEnd?.invoke()
                lastFrameAt = 0L
                scheduleFrame()
                return true
            }
            MotionEvent.ACTION_CANCEL -> {
                if (!dragging) return false
                dragging = false
                inertiaRunning = false
                parent?.requestDisallowInterceptTouchEvent(false)
                cancelSeek()
                velocityTracker?.recycle()
                velocityTracker = null
                // инерцию не запускаем: жест забрала система,
                // но финальную перемотку применяем
                expectedSeek = position
                expectedUntil = SystemClock.uptimeMillis() + expectedWindowMs
                onSeek?.invoke(position)
                onSeekEnd?.invoke()
                lastFrameAt = 0L
                scheduleFrame()
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    private fun scheduleSeek() {
        cancelSeek()
        val r = Runnable {
            expectedSeek = position
            expectedUntil = SystemClock.uptimeMillis() + expectedWindowMs
            onSeek?.invoke(position)
        }
        seekRunnable = r
        postDelayed(r, seekDebounceMs)
    }

    private fun cancelSeek() {
        seekRunnable?.let { removeCallbacks(it) }
        seekRunnable = null
    }
}
