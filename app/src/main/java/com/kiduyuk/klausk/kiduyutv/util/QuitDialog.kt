package com.kiduyuk.klausk.kiduyutv.util

import android.app.Dialog
import android.content.Context
import android.content.ContextWrapper
import android.graphics.Color
import android.os.Bundle
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.foundation.focusable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.airbnb.lottie.compose.LottieAnimation
import com.airbnb.lottie.compose.LottieCompositionSpec
import com.airbnb.lottie.compose.LottieConstants
import com.airbnb.lottie.compose.rememberLottieComposition
import com.kiduyuk.klausk.kiduyutv.R

// ─────────────────────────────────────────────────────────────────────────────
// Usage (from any Activity / Fragment):
//
//   QuitDialog(this,
//       onNo  = { /* stay */   },
//       onYes = { finish()     }
//   ).show()
//
// Requires:
//   - res/raw/exit.json         (Lottie animation file)
//   - Lottie dependency in build.gradle:
//       implementation "com.airbnb.android:lottie-compose:<version>"
// ─────────────────────────────────────────────────────────────────────────────

class QuitDialog(
    context: Context,
    private val title: String,
    private val message: String,
    private val positiveButtonText: String,
    private val negativeButtonText: String,
    private val lottieAnimRes: Int,
    private val annotatedMessage: AnnotatedString? = null,
    private val onNo: () -> Unit = {},
    private val onYes: () -> Unit = {}
) : Dialog(context, android.R.style.Theme_Translucent_NoTitleBar) {
    // Disable back press — user must explicitly tap NO or YES
    @Deprecated("Deprecated in Java")
    override fun onBackPressed() = Unit
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        //setCancelable(false) prevents tapping outside the dialog from dismissing it
        setCancelable(false)

        val root = FrameLayout(context).apply {
            setBackgroundColor(Color.TRANSPARENT)
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        // ── Fix: ComposeView requires a ViewTreeLifecycleOwner.
        // The host Activity implements all three owner interfaces; wire them
        // onto the root FrameLayout so Compose can resolve them up the tree.
        val activity = context.findComponentActivity()
            ?: error("QuitDialog must be created with a ComponentActivity context")

        root.setViewTreeLifecycleOwner(activity as LifecycleOwner)
        root.setViewTreeViewModelStoreOwner(activity as ViewModelStoreOwner)
        root.setViewTreeSavedStateRegistryOwner(activity as SavedStateRegistryOwner)

        setContentView(root)
        window?.setLayout(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )

        val composeView = ComposeView(context).apply {
            setContent {
                QuitDialogContent(
                    title = title,
                    message = message,
                    annotatedMessage = annotatedMessage,
                    positiveButtonText = positiveButtonText,
                    negativeButtonText = negativeButtonText,
                    lottieAnimRes = lottieAnimRes,
                    onNo = {
                        onNo()
                        dismiss()
                    },
                    onYes = {
                        onYes()
                        dismiss()
                    }
                )
            }
        }

        root.addView(composeView)
    }

    private fun Context.findComponentActivity(): ComponentActivity? {
        var currentContext = this
        while (currentContext is ContextWrapper) {
            if (currentContext is ComponentActivity) {
                return currentContext
            }
            currentContext = currentContext.baseContext
        }
        return null
    }
}

@Composable
fun QuitDialogContent(
    title: String,
    message: String,
    annotatedMessage: AnnotatedString? = null,
    positiveButtonText: String,
    negativeButtonText: String,
    lottieAnimRes: Int,
    onNo: () -> Unit,
    onYes: () -> Unit
) {
    val composition by rememberLottieComposition(LottieCompositionSpec.RawRes(lottieAnimRes))
    val focusRequester = remember { FocusRequester() }

    // Use a Box to center the dialog card on screen.
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(ComposeColor.Black.copy(alpha = 0.5f)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .width(500.dp)
                .background(ComposeColor(0xFF1A1A1A), RoundedCornerShape(16.dp))
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Lottie Animation
            LottieAnimation(
                composition = composition,
                iterations = LottieConstants.IterateForever,
                modifier = Modifier
                    .size(150.dp)
                    .padding(bottom = 16.dp)
            )

                Text(
                    text = title,
                color = ComposeColor.White,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = message,
                color = ComposeColor.Gray,
                fontSize = 14.sp,
                textAlign = TextAlign.Center
            )

            if (annotatedMessage != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = annotatedMessage,
                    color = ComposeColor.Gray.copy(alpha = 0.8f),
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center,
                    maxLines = 5,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                var isNoFocused by remember { mutableStateOf(false) }
                var isYesFocused by remember { mutableStateOf(false) }

                // NO Button
                Button(
                    onClick = onNo,
                    modifier = Modifier
                        .weight(1f)
                        .focusRequester(focusRequester)
                        .onFocusChanged { isNoFocused = it.isFocused },
                        //.focusable(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isNoFocused) ComposeColor.White else ComposeColor(0xFF333333),
                        contentColor = if (isNoFocused) ComposeColor.Black else ComposeColor.White
                    ),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(negativeButtonText, fontWeight = FontWeight.SemiBold)
                }

                // YES Button
                Button(
                    onClick = onYes,
                    modifier = Modifier
                        .weight(1f)
                        .onFocusChanged { isYesFocused = it.isFocused },
                        //.focusable(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isYesFocused) ComposeColor.Red else ComposeColor(0xFF333333),
                        contentColor = ComposeColor.White
                    ),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(positiveButtonText, fontWeight = FontWeight.SemiBold)
                }
            }

            // Auto-focus the "No" button when dialog opens.
            LaunchedEffect(Unit) {
                focusRequester.requestFocus()
            }
        }
    }
}
// ─────────────────────────────────────────────────────────────────────────────
// Previews
// ─────────────────────────────────────────────────────────────────────────────

@Preview(name = "QuitDialog – NO focused", showBackground = true, widthDp = 420, heightDp = 320)
@Composable
private fun QuitDialogPreviewNoFocused() {
    QuitDialogContent(
        title = "Quit Kiduyu TV?",
        message = "Are you sure you want to exit the app?",
        positiveButtonText = "Yes",
        negativeButtonText = "No",
        lottieAnimRes = R.raw.exit,
        onNo = {},
        onYes = {}
    )
}

@Preview(name = "QuitDialog – Dark background", showBackground = true, backgroundColor = 0xFF1A2035, widthDp = 420, heightDp = 320)
@Composable
private fun QuitDialogPreviewDarkBg() {
    QuitDialogContent(
        title = "Quit Kiduyu TV?",
        message = "Are you sure you want to exit the app?",
        positiveButtonText = "Yes",
        negativeButtonText = "No",
        lottieAnimRes = R.raw.exit,
        onNo = {},
        onYes = {}
    )
}
