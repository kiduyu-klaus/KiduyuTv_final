package com.kiduyuk.klausk.kiduyutv.ui.player.webview

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat

/**
 * A custom view that renders a mouse cursor on screen.
 * Supports both custom drawable pointers and the default path-based cursor.
 */
class MouseCursorView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
    private var cursorDrawable: Drawable? = null
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

    /**
     * Sets the cursor drawable to display.
     * @param drawableResId The resource ID of the drawable to use as cursor
     */
    fun setCursorDrawable(drawableResId: Int) {
        cursorDrawable = ContextCompat.getDrawable(context, drawableResId)
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        
        // If a custom drawable is set, draw it instead of the path
        cursorDrawable?.let { drawable ->
            // Draw the custom cursor image
            drawable.setBounds(0, 0, drawable.intrinsicWidth, drawable.intrinsicHeight)
            drawable.draw(canvas)
        } ?: run {
            // Fall back to the default path-based cursor
            canvas.drawPath(cursorPath, fillPaint)
            canvas.drawPath(cursorPath, strokePaint)
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        // Use custom drawable dimensions if available, otherwise use default size
        val cursorDrawable = this.cursorDrawable
        if (cursorDrawable != null) {
            setMeasuredDimension(cursorDrawable.intrinsicWidth, cursorDrawable.intrinsicHeight)
        } else {
            setMeasuredDimension(30, 50)
        }
    }
}
