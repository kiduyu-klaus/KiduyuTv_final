package com.kiduyuk.klausk.kiduyutv.ui.player.directstream

import android.app.Activity
import android.content.Intent
import android.content.DialogInterface
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.util.TypedValue
import android.widget.Button
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.kiduyuk.klausk.kiduyutv.activity.mainactivity.MainActivity
import com.kiduyuk.klausk.kiduyutv.R
import com.kiduyuk.klausk.kiduyutv.util.SettingsManager

/**
 * Warns before a direct-stream request fans out to every provider.
 *
 * This check belongs immediately before provider resolution, rather than only
 * at the launch boundary, because activities can also be opened directly and
 * can reload streams without going through [DirectStreamLauncher].
 */
object DirectStreamProviderWarning {
    private const val ALL_PROVIDERS_LABEL = "All Providers"

    /**
     * Shows the warning when the saved default direct-stream provider is AUTO.
     * Returns true when a dialog was shown; the caller must then wait for the
     * user's choice before starting the fetch.
     */
    fun showIfNeeded(
        activity: Activity,
        intent: Intent,
        onContinue: () -> Unit
    ): Boolean {
        val requestedProvider = intent.getStringExtra(DirectStreamActivity.EXTRA_PROVIDER)
        if (activity.isFinishing || activity.isDestroyed || !isAllProviders(
                SettingsManager(activity).getDefaultDirectStreamProvider()
            ) || !isAllProviders(requestedProvider)
        ) {
            return false
        }

        val dialog = MaterialAlertDialogBuilder(activity)
            .setTitle("Set a default provider")
            .setMessage(
                "Fetching from All Providers can take longer and this option will be " +
                    "removed in a future update. Choose a default direct stream provider " +
                    "in Playback settings."
            )
            .setNegativeButton("Open settings") { _, _ ->
                activity.startActivity(MainActivity.createSettingsIntent(activity))
            }
            .setPositiveButton("Continue with all providers") { _, _ ->
                onContinue()
            }
            .create()

        dialog.setOnShowListener {
            listOf(
                dialog.getButton(DialogInterface.BUTTON_NEGATIVE),
                dialog.getButton(DialogInterface.BUTTON_POSITIVE)
            ).forEach { button ->
                button?.let(::styleActionButton)
            }
            dialog.getButton(DialogInterface.BUTTON_POSITIVE)?.requestFocus()
        }
        dialog.show()
        return true
    }

    private fun styleActionButton(button: Button) {
        val context = button.context
        val red = context.getColor(R.color.kiduyu_red)
        val surface = context.getColor(R.color.direct_stream_surface_high)
        val pressedRed = Color.rgb(178, 7, 16)
        val radius = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            24f,
            context.resources.displayMetrics
        )
        val strokeWidth = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            1f,
            context.resources.displayMetrics
        ).toInt()

        fun background(fill: Int, stroke: Int) = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = radius
            setColor(fill)
            setStroke(strokeWidth, stroke)
        }

        button.backgroundTintList = null
        button.background = StateListDrawable().apply {
            addState(
                intArrayOf(android.R.attr.state_focused),
                background(red, red)
            )
            addState(
                intArrayOf(android.R.attr.state_pressed),
                background(pressedRed, pressedRed)
            )
            addState(
                intArrayOf(),
                background(Color.TRANSPARENT, surface)
            )
        }
        button.setTextColor(
            ColorStateList(
                arrayOf(
                    intArrayOf(android.R.attr.state_focused),
                    intArrayOf(android.R.attr.state_pressed),
                    intArrayOf()
                ),
                intArrayOf(Color.WHITE, Color.WHITE, red)
            )
        )
        button.minHeight = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            48f,
            context.resources.displayMetrics
        ).toInt()
        button.isFocusable = true
        button.isFocusableInTouchMode = true
        button.stateListAnimator = null
    }

    fun isAllProviders(provider: String?): Boolean {
        val value = provider?.trim().orEmpty()
        return value.isBlank() ||
            value.equals(SettingsManager.AUTO, ignoreCase = true) ||
            value.equals(ALL_PROVIDERS_LABEL, ignoreCase = true)
    }
}
