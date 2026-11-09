package com.example.joywithsollor

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View

/**
 * Вертикальный индикатор прогресса: только отображение, без касаний.
 * Ширина 4dp, заполнение сверху вниз цветом #E2104C.
 */
class VideoProgressView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    private val density = resources.displayMetrics.density
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFE2104C.toInt() }

    /** 0..1 */
    var progress: Double = 0.0
        set(value) {
            field = value.coerceIn(0.0, 1.0)
            invalidate()
        }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(
            MeasureSpec.makeMeasureSpec((4 * density).toInt(), MeasureSpec.EXACTLY),
            heightMeasureSpec
        )
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (progress <= 0.0) return
        val h = (height * progress).toFloat()
        canvas.drawRect(0f, 0f, width.toFloat(), h, paint)
    }

    override fun onTouchEvent(event: android.view.MotionEvent): Boolean = false
}
