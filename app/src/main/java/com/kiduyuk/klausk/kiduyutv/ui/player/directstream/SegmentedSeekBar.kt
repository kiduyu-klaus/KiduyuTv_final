package com.kiduyuk.klausk.kiduyutv.ui.player.directstream

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.drawable.ColorDrawable
import android.util.AttributeSet
import androidx.appcompat.widget.AppCompatSeekBar
import com.kiduyuk.klausk.kiduyutv.data.model.SkipSegment
import com.kiduyuk.klausk.kiduyutv.data.model.SkipSegmentQuality
import com.kiduyuk.klausk.kiduyutv.data.model.SkipSegments
import kotlin.math.roundToInt
import kotlin.math.roundToLong

/**
 * SeekBar that keeps the normal SeekBar thumb/touch behavior while drawing
 * intro, recap, outro, preview, or credits intervals on the track.
 *
 * The native SeekBar progress remains normalized to 0..1000, matching the
 * existing DirectStreamActivity seek logic. setSegments
 */
class SegmentedSeekBar @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = android.R.attr.seekBarStyle
) : AppCompatSeekBar(context, attrs, defStyleAttr) {

    data class Highlight(
        val label: String,
        val startMs: Long,
        val endMs: Long,
        val color: Int
    )

    companion object {
        private const val NORMALIZED_MAX = 1_000
    }

    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val bufferPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val highlightPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val progressPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val trackRect = RectF()

    private var durationMs = 0L
    private var positionMs = 0L
    private var highlights: List<Highlight> = emptyList()

    private val density: Float
        get() = resources.displayMetrics.density

    var trackHeightDp: Float = 4f
        set(value) {
            field = value.coerceAtLeast(1f)
            invalidate()
        }

    var trackColor: Int = Color.parseColor("#59616D")
        set(value) {
            field = value
            invalidate()
        }

    /** Buffered portion of the media track. */
    var bufferColor: Int = Color.parseColor("#B7BEC8")
        set(value) {
            field = value
            invalidate()
        }

    /** Played portion is deliberately translucent so segment colors remain visible. */
    var progressColor: Int = Color.parseColor("#E31B23")
        set(value) {
            field = value
            invalidate()
        }

    var progressAlpha: Int = 120
        set(value) {
            field = value.coerceIn(0, 255)
            invalidate()
        }

    var introColor: Int = Color.parseColor("#42A5F5")
        set(value) {
            field = value
            invalidate()
        }

    var recapColor: Int = Color.parseColor("#FFC107")
        set(value) {
            field = value
            invalidate()
        }

    var outroColor: Int = Color.parseColor("#EF5350")
        set(value) {
            field = value
            invalidate()
        }

    var previewColor: Int = Color.parseColor("#AB47BC")
        set(value) {
            field = value
            invalidate()
        }

    init {
        max = NORMALIZED_MAX
        splitTrack = false

        // The track and progress are drawn below. Keeping the native thumb
        // allows the existing XML thumbTint and normal SeekBar interaction.
        progressDrawable = ColorDrawable(Color.TRANSPARENT)
    }

    fun setDurationMs(value: Long) {
        durationMs = value.takeIf { it > 0L } ?: 0L
        positionMs = positionMs.coerceIn(0L, durationMs)
        // Do not write progress here. DirectStreamActivity refreshes duration
        // every second, and writing progress during a drag would fight the user.
        invalidate()
    }

    fun setPositionMs(value: Long) {
        positionMs = value.coerceAtLeast(0L).let { position ->
            if (durationMs > 0L) position.coerceAtMost(durationMs) else position
        }
        syncNativeProgress()
        invalidate()
    }

    fun getPositionMsFromProgress(): Long {
        if (durationMs <= 0L || max <= 0) return 0L
        return ((progress.toDouble() / max.toDouble()) * durationMs)
            .roundToLong()
            .coerceIn(0L, durationMs)
    }

    /** Load all valid segments from the existing SkipDB response model. */
    fun setSegments(segments: SkipSegments?) {
        setHighlights(
            listOfNotNull(
                toHighlight("intro", segments?.intro, introColor),
                toHighlight("recap", segments?.recap, recapColor),
                toHighlight("outro", segments?.outro, outroColor),
                toHighlight("preview", segments?.preview, previewColor)
            )
        )
    }

    /** Generic method for a future separate movie credits segment. */
    fun setHighlights(items: List<Highlight>) {
        highlights = items
            .mapNotNull { item ->
                val start = item.startMs.coerceAtLeast(0L)
                val end = item.endMs.coerceAtLeast(0L)
                if (end <= start) null else item.copy(startMs = start, endMs = end)
            }
            .sortedBy { it.startMs }
        invalidate()
    }

    fun clearHighlights() {
        highlights = emptyList()
        invalidate()
    }

    private fun toHighlight(
        label: String,
        segment: SkipSegment?,
        color: Int
    ): Highlight? {
        if (!SkipSegmentQuality.isUsable(segment)) return null
        val safeSegment = segment ?: return null
        val endMs = safeSegment.endMs ?: return null
        if (endMs <= safeSegment.startMs) return null

        return Highlight(
            label = label,
            startMs = safeSegment.startMs,
            endMs = endMs,
            color = color
        )
    }

    private fun syncNativeProgress() {
        progress = if (durationMs > 0L) {
            ((positionMs.toDouble() / durationMs.toDouble()) * max)
                .roundToInt()
                .coerceIn(0, max)
        } else {
            0
        }
    }

    override fun onDraw(canvas: Canvas) {
        // Match Android SeekBar's real thumb travel range. This keeps the
        // colored intervals aligned with the thumb at 0% and 100%.
        val left = (paddingLeft + thumbOffset).toFloat()
        val right = (width - paddingRight - thumbOffset).toFloat()
        if (right <= left || durationMs <= 0L) {
            super.onDraw(canvas)
            return
        }

        val trackHeightPx = trackHeightDp * density
        val centerY = height / 2f
        val top = centerY - trackHeightPx / 2f
        val bottom = centerY + trackHeightPx / 2f
        val radius = trackHeightPx / 2f
        val trackWidth = right - left

        trackRect.set(left, top, right, bottom)
        trackPaint.color = trackColor
        trackPaint.alpha = 255
        canvas.drawRoundRect(trackRect, radius, radius, trackPaint)

        // Keep the existing Activity's secondaryProgress/buffer indicator.
        val bufferFraction = secondaryProgress.toFloat() / max.toFloat()
        val bufferRight = left + trackWidth * bufferFraction
        if (bufferRight > left) {
            bufferPaint.color = bufferColor
            bufferPaint.alpha = 180
            canvas.drawRoundRect(
                left,
                top,
                bufferRight.coerceAtMost(right),
                bottom,
                radius,
                radius,
                bufferPaint
            )
        }

        highlights.forEach { highlight ->
            val start = highlight.startMs.coerceIn(0L, durationMs)
            val end = highlight.endMs.coerceIn(0L, durationMs)
            if (end <= start) return@forEach

            val segmentLeft = left + trackWidth * (start.toDouble() / durationMs).toFloat()
            val segmentRight = left + trackWidth * (end.toDouble() / durationMs).toFloat()
            highlightPaint.color = highlight.color
            highlightPaint.alpha = 255
            canvas.drawRoundRect(
                segmentLeft,
                top,
                segmentRight,
                bottom,
                radius,
                radius,
                highlightPaint
            )
        }

        val progressFraction = progress.toFloat() / max.toFloat()
        val progressRight = left + trackWidth * progressFraction
        if (progressRight > left) {
            progressPaint.color = progressColor
            progressPaint.alpha = progressAlpha
            canvas.drawRoundRect(
                left,
                top,
                progressRight.coerceAtMost(right),
                bottom,
                radius,
                radius,
                progressPaint
            )
        }

        progressPaint.alpha = 255

        // Draw the native thumb after the custom track.
        super.onDraw(canvas)
    }
}
