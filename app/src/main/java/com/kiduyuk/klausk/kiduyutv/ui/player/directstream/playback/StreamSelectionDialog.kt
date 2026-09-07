package com.kiduyuk.klausk.kiduyutv.ui.player.directstream.playback

import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.Window
import android.view.WindowManager
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kiduyuk.klausk.kiduyutv.R
import com.kiduyuk.klausk.kiduyutv.ui.player.directstream.model.StreamItem
import com.kiduyuk.klausk.kiduyutv.ui.theme.BackgroundDark
import com.kiduyuk.klausk.kiduyutv.ui.theme.CardDark
import com.kiduyuk.klausk.kiduyutv.ui.theme.DarkRed
import com.kiduyuk.klausk.kiduyutv.ui.theme.TextPrimary
import com.kiduyuk.klausk.kiduyutv.ui.theme.TextSecondary

class StreamSelectionDialog(
    context: Context,
    streams: List<StreamItem>,
    activeUrl: String?,
    private val onStreamSelected: (StreamItem) -> Unit
) : Dialog(context) {

    private val streamsState = mutableStateOf(streams)
    private val activeUrlState = mutableStateOf(activeUrl)

    init {
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        setContentView(
            ComposeView(context).apply {
                setContent {
                    StreamSelectionContent(
                        streams = streamsState.value,
                        activeUrl = activeUrlState.value,
                        onStreamSelected = ::selectStream,
                        onDismiss = ::dismiss
                    )
                }
            }
        )
        window?.apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            setDimAmount(0.76f)
            setGravity(Gravity.CENTER)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setCanceledOnTouchOutside(true)
    }

    override fun onStart() {
        super.onStart()
        val isCompactScreen = context.resources.configuration.smallestScreenWidthDp < 600
        val maxWidthFraction = if (isCompactScreen) 0.84f else 0.9f
        val preferredWidthDp = if (isCompactScreen) 560 else 720
        val metrics = context.resources.displayMetrics
        val maxWidth = (metrics.widthPixels * maxWidthFraction).toInt()
        val preferredWidth = (preferredWidthDp * metrics.density).toInt()
        window?.setLayout(
            preferredWidth.coerceAtMost(maxWidth),
            WindowManager.LayoutParams.WRAP_CONTENT
        )
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() = dismiss()

    fun updateStreams(updated: List<StreamItem>) {
        if (!isShowing) return
        streamsState.value = updated
    }

    private fun selectStream(stream: StreamItem) {
        activeUrlState.value = stream.url
        onStreamSelected(stream)
        dismiss()
    }

    @Composable
    private fun StreamSelectionContent(
        streams: List<StreamItem>,
        activeUrl: String?,
        onStreamSelected: (StreamItem) -> Unit,
        onDismiss: () -> Unit
    ) {
        var selectedLanguage by remember { mutableStateOf(LanguageFilter.ALL) }
        var isGridLayout by remember { mutableStateOf(false) }
        val filteredStreams = remember(streams, selectedLanguage) {
            streams.filter { stream ->
                when (selectedLanguage) {
                    LanguageFilter.ALL -> true
                    LanguageFilter.ENGLISH -> detectLanguages(stream).contains("English")
                    LanguageFilter.HINDI -> detectLanguages(stream).contains("Hindi")
                }
            }
        }
        val firstStreamFocusRequester = remember { FocusRequester() }
        val closeFocusRequester = remember { FocusRequester() }
        val filterFocusRequester = remember { FocusRequester() }

        LaunchedEffect(filteredStreams, activeUrl, isGridLayout) {
            if (filteredStreams.isNotEmpty()) {
                firstStreamFocusRequester.requestFocus()
            } else {
                closeFocusRequester.requestFocus()
            }
        }

        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = BackgroundDark,
            shape = RoundedCornerShape(18.dp),
            tonalElevation = 12.dp
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                Text(
                    text = context.getString(R.string.stream_choose),
                    color = TextPrimary,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = context.getString(R.string.stream_source_count, filteredStreams.size),
                    color = TextSecondary,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 4.dp)
                )
                Text(
                    text = context.getString(R.string.stream_choose_hint),
                    color = TextSecondary,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 14.dp)
                )

                Row(
                    modifier = Modifier.padding(top = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FilterButton(
                        text = context.getString(R.string.stream_filter_all),
                        selected = selectedLanguage == LanguageFilter.ALL,
                        modifier = Modifier.focusRequester(filterFocusRequester),
                        onClick = { selectedLanguage = LanguageFilter.ALL }
                    )
                    FilterButton(
                        text = context.getString(R.string.stream_filter_english),
                        selected = selectedLanguage == LanguageFilter.ENGLISH,
                        onClick = { selectedLanguage = LanguageFilter.ENGLISH }
                    )
                    FilterButton(
                        text = context.getString(R.string.stream_filter_hindi),
                        selected = selectedLanguage == LanguageFilter.HINDI,
                        onClick = { selectedLanguage = LanguageFilter.HINDI }
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    FilterButton(
                        text = if (isGridLayout) "List" else "Grid",
                        selected = false,
                        onClick = { isGridLayout = !isGridLayout }
                    )
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(360.dp)
                        .padding(top = 14.dp)
                        .focusProperties { down = closeFocusRequester }
                ) {
                    if (filteredStreams.isEmpty()) {
                        Text(
                            text = "No streams available",
                            color = TextSecondary,
                            modifier = Modifier.align(Alignment.Center)
                        )
                    } else if (isGridLayout) {
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(2),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            items(filteredStreams, key = { it.url }) { stream ->
                                StreamCard(
                                    stream = stream,
                                    active = stream.url == activeUrl,
                                    firstFocusRequester = if (stream == filteredStreams.first()) firstStreamFocusRequester else null,
                                    onClick = { onStreamSelected(stream) }
                                )
                            }
                        }
                    } else {
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            items(filteredStreams, key = { it.url }) { stream ->
                                StreamCard(
                                    stream = stream,
                                    active = stream.url == activeUrl,
                                    firstFocusRequester = if (stream == filteredStreams.first()) firstStreamFocusRequester else null,
                                    onClick = { onStreamSelected(stream) }
                                )
                            }
                        }
                    }
                }

                Button(
                    onClick = onDismiss,
                    modifier = Modifier
                        .align(Alignment.End)
                        .padding(top = 16.dp)
                        .focusRequester(closeFocusRequester)
                        .focusProperties { up = firstStreamFocusRequester },
                    colors = ButtonDefaults.buttonColors(containerColor = CardDark)
                ) {
                    Text(text = context.getString(R.string.track_close), color = TextPrimary)
                }
            }
        }
    }

    @Composable
    private fun FilterButton(
        text: String,
        selected: Boolean,
        modifier: Modifier = Modifier,
        onClick: () -> Unit
    ) {
        var focused by remember { mutableStateOf(false) }
        Text(
            text = text,
            color = TextPrimary,
            fontWeight = FontWeight.Bold,
            modifier = modifier
                .background(
                    if (selected || focused) DarkRed else CardDark,
                    RoundedCornerShape(8.dp)
                )
                .onFocusChanged { focused = it.isFocused }
                .focusable()
                .clickable(onClick = onClick)
                .padding(horizontal = 14.dp, vertical = 10.dp)
        )
    }

    @Composable
    private fun StreamCard(
        stream: StreamItem,
        active: Boolean,
        firstFocusRequester: FocusRequester?,
        onClick: () -> Unit
    ) {
        var focused by remember { mutableStateOf(false) }
        val host = runCatching { Uri.parse(stream.url).host }.getOrNull().orEmpty()
        val title = stream.name.ifBlank { stream.title.ifBlank { "Stream" } }
        val provider = stream.provider.ifBlank { title }
        val metadata = listOf(provider, stream.quality, stream.type.uppercase(), host)
            .filter { it.isNotBlank() }
            .distinctBy { it.lowercase() }
        val status = when {
            stream.isValid && !stream.isFailed -> context.getString(R.string.stream_ok)
            stream.isChecking -> context.getString(R.string.stream_checking)
            else -> context.getString(R.string.stream_failed)
        }
        val statusColor = when {
            stream.isValid && !stream.isFailed -> ComposeColor(0xFF2E7D32)
            stream.isChecking -> ComposeColor(0xFF8A6D1D)
            else -> ComposeColor(0xFF9B2C2C)
        }

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .then(if (firstFocusRequester != null) Modifier.focusRequester(firstFocusRequester) else Modifier)
                .onFocusChanged { focused = it.isFocused }
                .focusable()
                .clickable(onClick = onClick)
                .border(
                    width = if (focused || active) 2.dp else 1.dp,
                    color = if (focused) MaterialTheme.colorScheme.primary else if (active) DarkRed else ComposeColor.Transparent,
                    shape = RoundedCornerShape(12.dp)
                ),
            colors = CardDefaults.cardColors(containerColor = if (focused) CardDark.copy(alpha = 0.95f) else CardDark),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .background(DarkRed, RoundedCornerShape(10.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = provider.firstOrNull()?.uppercaseChar()?.toString() ?: "S",
                            color = TextPrimary,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Column(modifier = Modifier.padding(start = 10.dp).weight(1f)) {
                        Text(
                            text = title,
                            color = TextPrimary,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = metadata.joinToString("  •  "),
                            color = TextSecondary,
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Text(
                        text = status,
                        color = TextPrimary,
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier
                            .background(statusColor, RoundedCornerShape(6.dp))
                            .padding(horizontal = 7.dp, vertical = 4.dp)
                    )
                }
                Text(
                    text = context.getString(
                        R.string.stream_language_label,
                        detectLanguages(stream).joinToString(" • ")
                    ),
                    color = TextSecondary,
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        }
    }

    private enum class LanguageFilter { ALL, ENGLISH, HINDI }

    companion object {
        private val LANGUAGE_PATTERNS = listOf(
            "English" to Regex("\\benglish\\b", RegexOption.IGNORE_CASE),
            "Hindi" to Regex("\\bhindi\\b", RegexOption.IGNORE_CASE),
            "Telugu" to Regex("\\btelugu\\b", RegexOption.IGNORE_CASE),
            "Tamil" to Regex("\\btamil\\b", RegexOption.IGNORE_CASE)
        )

        private fun detectLanguages(stream: StreamItem): List<String> {
            val searchableText = "${stream.name} ${stream.title}"
            return LANGUAGE_PATTERNS.mapNotNull { (label, pattern) ->
                label.takeIf { pattern.containsMatchIn(searchableText) }
            }.ifEmpty { listOf("English") }
        }
    }
}
