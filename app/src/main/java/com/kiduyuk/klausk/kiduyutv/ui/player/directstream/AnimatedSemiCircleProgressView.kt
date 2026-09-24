package com.kiduyuk.klausk.kiduyutv.ui.player.directstream

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import android.view.animation.DecelerateInterpolator
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * A compact, label-free semi-circular progress indicator for provider fetches.
 * The numeric value is intentionally the only text rendered by this view.
 */
class AnimatedSemiCircleProgressView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private val density = resources.displayMetrics.density
    private val strokeWidth = 14f * density
    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(88, 255, 255, 255)
        style = Paint.Style.STROKE
        strokeWidth = this@AnimatedSemiCircleProgressView.strokeWidth
        strokeCap = Paint.Cap.ROUND
    }
    private val progressPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(88, 105, 245)
        style = Paint.Style.STROKE
        strokeWidth = this@AnimatedSemiCircleProgressView.strokeWidth
        strokeCap = Paint.Cap.ROUND
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textAlign = Paint.Align.CENTER
        typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
    }

    private var displayedProgress = 0f
    private var animator: ValueAnimator? = null

    fun setProviderProgress(completed: Int, total: Int) {
        val target = if (total > 0) {
            (completed.toFloat() / total.toFloat()).coerceIn(0f, 1f)
        } else {
            0f
        }
        contentDescription = if (total > 0) {
            "${(target * 100).roundToInt()} percent of providers fetched"
        } else {
            "Fetching providers"
        }
        animator?.cancel()
        animator = ValueAnimator.ofFloat(displayedProgress, target).apply {
            duration = 340L
            interpolator = DecelerateInterpolator()
            addUpdateListener {
                displayedProgress = it.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    fun reset() {
        animator?.cancel()
        animator = null
        displayedProgress = 0f
        invalidate()
    }

    override fun onDetachedFromWindow() {
        animator?.cancel()
        super.onDetachedFromWindow()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val availableWidth = width - paddingLeft - paddingRight
        val availableHeight = height - paddingTop - paddingBottom
        if (availableWidth <= 0 || availableHeight <= 0) return

        val radius = min(
            availableWidth / 2f - strokeWidth / 2f,
            availableHeight - strokeWidth / 2f
        ).coerceAtLeast(0f)
        val centerX = paddingLeft + availableWidth / 2f
        val centerY = paddingTop + availableHeight - strokeWidth / 2f
        val oval = RectF(
            centerX - radius,
            centerY - radius,
            centerX + radius,
            centerY + radius
        )
        val startAngle = 130f
        val sweepAngle = 280f
        canvas.drawArc(oval, startAngle, sweepAngle, false, trackPaint)
        canvas.drawArc(oval, startAngle, sweepAngle * displayedProgress, false, progressPaint)

        textPaint.textSize = min(42f * density, radius * 0.44f)
        val baseline = centerY - radius * 0.14f - (textPaint.descent() + textPaint.ascent()) / 2f
        canvas.drawText("${(displayedProgress * 100).roundToInt()}%", centerX, baseline, textPaint)
    }
}
