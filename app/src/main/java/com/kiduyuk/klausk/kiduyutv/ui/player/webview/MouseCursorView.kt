package com.kiduyuk.klausk.kiduyutv.ui.player.webview

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.View

/**
 * A custom view that renders a mouse cursor on screen.
 */
class MouseCursorView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val cursorPath = Path()
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.FILL
    }
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLACK
        style = Paint.Style.STROKE
        strokeWidth = 2f
    }

    init {
        // Define a classic cursor shape
        cursorPath.moveTo(0f, 0f)
        cursorPath.lineTo(0f, 40f)
        cursorPath.lineTo(10f, 30f)
        cursorPath.lineTo(18f, 45f)
        cursorPath.lineTo(22f, 43f)
        cursorPath.lineTo(14f, 28f)
        cursorPath.lineTo(25f, 28f)
        cursorPath.close()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawPath(cursorPath, fillPaint)
        canvas.drawPath(cursorPath, strokePaint)
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        // Fixed size for the cursor
        setMeasuredDimension(30, 50)
    }
}