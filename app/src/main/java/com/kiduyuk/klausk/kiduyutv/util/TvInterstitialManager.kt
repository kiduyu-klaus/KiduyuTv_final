package com.kiduyuk.klausk.kiduyutv.util

import android.app.Activity
import android.app.Dialog
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.KeyEvent
import android.view.ViewGroup
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusable
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.compose.ui.platform.ViewCompositionStrategy
import com.kiduyuk.klausk.kiduyutv.ui.components.NativeAdFallbackDispatcher
import java.util.concurrent.atomic.AtomicBoolean

/**
 * TV-only ad presentation. A custom dialog is used instead of SDK-managed
 * fullscreen interstitial windows so dismissing with Back cannot resize or
 * leave an overlay attached to the underlying Compose screen.
 */
object TvInterstitialManager {

    private const val TAG = "TvInterstitialManager"

    /** Shows a focus-safe native-ad transition before launching the player. */
    fun showAndThenLaunch(activity: Activity, onDismissed: () -> Unit) {
        if (activity.isFinishing || activity.isDestroyed) {
            onDismissed()
            return
        }

        val completed = AtomicBoolean(false)
        fun complete(dialog: Dialog?) {
            if (completed.compareAndSet(false, true)) {
                dialog?.dismiss()
                onDismissed()
            }
        }

        val dialog = Dialog(activity)
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        dialog.setCancelable(true)
        dialog.setCanceledOnTouchOutside(false)

        val composeView = ComposeView(activity).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
            isFocusable = true
            isFocusableInTouchMode = true
            requestFocus()
            setContent {
                TvInterstitialContent(onContinue = { complete(dialog) })
            }
        }
        dialog.setContentView(composeView)
        dialog.setOnKeyListener { _, keyCode, event ->
            if (event.action == KeyEvent.ACTION_UP &&
                (keyCode == KeyEvent.KEYCODE_BACK ||
                    keyCode == KeyEvent.KEYCODE_ESCAPE)
            ) {
                complete(dialog)
                true
            } else {
                false
            }
        }
        dialog.setOnDismissListener {
            if (completed.compareAndSet(false, true)) onDismissed()
        }
        dialog.setOnCancelListener { complete(dialog) }
        dialog.setOnShowListener {
            dialog.window?.let { window ->
                window.setLayout(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
                window.setDimAmount(0.72f)
            }
            composeView.requestFocus()
        }
        dialog.show()
        dialog.window?.let { window ->
            window.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            window.setLayout(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            window.setDimAmount(0.72f)
        }
    }

    /** Back navigation is never monetized; return immediately. */
    fun showAndThen(activity: Activity, onDismissed: () -> Unit) {
        onDismissed()
    }

    val isReady: Boolean
        get() = AdManager.isInterstitialReady
}

@androidx.compose.runtime.Composable
private fun TvInterstitialContent(onContinue: () -> Unit) {
    val continueFocusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        continueFocusRequester.requestFocus()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(ComposeColor(0xFF080A10)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.72f)
                .background(ComposeColor(0xFF151923))
                .padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            Text(
                text = "Advertisement",
                color = ComposeColor.White,
                style = MaterialTheme.typography.headlineSmall
            )
            NativeAdFallbackDispatcher.Render(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(250.dp)
            )
            Button(
                onClick = onContinue,
                modifier = Modifier
                    .zIndex(2f)
                    .fillMaxWidth()
                    .focusRequester(continueFocusRequester)
                    .focusable()
            ) {
                Text("Continue")
            }
        }
    }
}
