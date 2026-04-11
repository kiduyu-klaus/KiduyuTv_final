package com.kiduyuk.klausk.kiduyutv.ui.player.webview

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import androidx.core.graphics.toColorInt

class MouseCursorView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val cursorSize = 120f // px — tweak to taste

    private val cursorPath = Path().apply {
        // Tip at top-left
        moveTo(0f, 0f)
        // Right point (the sharp tail-right)
        lineTo(cursorSize * 0.95f, cursorSize * 0.42f)
        // Bottom-right inner notch (creates the concave tail)
        lineTo(cursorSize * 0.62f, cursorSize * 0.62f)
        // Bottom tip of tail
        lineTo(cursorSize * 0.42f, cursorSize * 0.95f)
        close()
    }

    // Shadow paint for soft drop shadow
    private val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.argb(60, 80, 0, 180)
        maskFilter = BlurMaskFilter(14f, BlurMaskFilter.Blur.NORMAL)
    }

    // Gradient fill — cyan top-left → purple bottom-right
    private val gradientPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        shader = LinearGradient(
            0f, 0f,
            cursorSize * 0.95f, cursorSize * 0.95f,
            intArrayOf(
                "#00CFFF".toColorInt(), // cyan
                "#7B4FFF".toColorInt(), // violet
                "#E040FB".toColorInt()  // magenta
            ),
            floatArrayOf(0f, 0.55f, 1f),
            Shader.TileMode.CLAMP
        )
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        // Draw shadow slightly offset
        canvas.save()
        canvas.translate(6f, 8f)
        canvas.drawPath(cursorPath, shadowPaint)
        canvas.restore()
        // Draw gradient cursor
        canvas.drawPath(cursorPath, gradientPaint)
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val size = (cursorSize * 1.1f).toInt()
        setMeasuredDimension(size, size)
    }
}