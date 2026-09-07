package com.kiduyuk.klausk.kiduyutv.ui.player.directstream.playback

import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import android.widget.BaseAdapter
import android.widget.GridView
import android.widget.TextView
import com.kiduyuk.klausk.kiduyutv.R
import com.kiduyuk.klausk.kiduyutv.ui.player.directstream.model.StreamItem

class StreamSelectionDialog(
    context: Context,
    private var streams: List<StreamItem>,
    private var activeUrl: String?,
    private val onStreamSelected: (StreamItem) -> Unit
) : Dialog(context) {

    private val list: GridView
    private val close: TextView
    private val streamCount: TextView
    private val streamAdapter: StreamAdapter

    init {
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        val view = LayoutInflater.from(context).inflate(R.layout.dialog_direct_stream_sources, null)
        setContentView(view)

        window?.let {
            it.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            it.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            it.setDimAmount(0.76f)
            it.setGravity(Gravity.CENTER)
        }

        list = view.findViewById(R.id.listStreams)
        close = view.findViewById(R.id.btnCloseStreams)
        streamCount = view.findViewById(R.id.tvStreamCount)
        streamCount.text =
            context.getString(R.string.stream_source_count, streams.size)
        streamAdapter = StreamAdapter(context, streams, activeUrl)
        list.adapter = streamAdapter
        list.setOnItemClickListener { _, _, position, _ ->
            streams.getOrNull(position)?.let {
                onStreamSelected(it)
                dismiss()
            }
        }
        close.setOnClickListener { dismiss() }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setCanceledOnTouchOutside(true)
    }

    override fun onStart() {
        super.onStart()

        // Keep the TV sizing and navigation behavior unchanged. Phones report
        // a small `smallestScreenWidthDp` even when held in landscape, so the
        // compact width is applied only to mobile-sized displays.
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

        val activeIndex = streams.indexOfFirst { it.url == activeUrl }.coerceAtLeast(0)
        list.setSelection(activeIndex)
        list.requestFocus()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        dismiss()
    }

    /**
     * Refresh the dialog with an updated stream list. Called by
     * [com.kiduyuk.klausk.kiduyutv.ui.player.directstream.DirectStreamActivity]
     * once the [StreamValidator] finishes probing each entry, so the
     * "stream ok" badge appears without the user having to reopen the
     * dialog.
     */
    fun updateStreams(updated: List<StreamItem>) {
        if (!isShowing) return
        streams = updated
        streamCount.text = context.getString(R.string.stream_source_count, updated.size)
        streamAdapter.replace(updated, activeUrl)
    }

    private class StreamAdapter(
        private val context: Context,
        private var streams: List<StreamItem>,
        private var activeUrl: String?
    ) : BaseAdapter() {
        override fun getCount(): Int = streams.size
        override fun getItem(position: Int): StreamItem = streams[position]
        override fun getItemId(position: Int): Long = position.toLong()

        fun replace(newStreams: List<StreamItem>, newActiveUrl: String?) {
            streams = newStreams
            activeUrl = newActiveUrl
            notifyDataSetChanged()
        }

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val stream = getItem(position)
            val view = convertView ?: LayoutInflater.from(context)
                .inflate(R.layout.item_direct_stream_source, parent, false)
            val host = runCatching { Uri.parse(stream.url).host }.getOrNull().orEmpty()
            val title = stream.name.ifBlank { stream.title.ifBlank { "Stream" } }
            val provider = stream.provider.ifBlank { title }
            view.findViewById<TextView>(R.id.streamSourceIcon).text =
                provider.firstOrNull()?.uppercaseChar()?.toString() ?: (position + 1).toString()
            view.findViewById<TextView>(R.id.streamSourceTitle).text = title
            view.findViewById<TextView>(R.id.streamSourceMetadata).apply {
                val metadata = listOf(
                    provider,
                    stream.quality,
                    stream.type.uppercase(),
                    host
                ).filter { it.isNotBlank() }.distinctBy { it.lowercase() }
                text = metadata.joinToString("  •  ")
                visibility = if (metadata.isEmpty()) View.GONE else View.VISIBLE
            }
            view.findViewById<TextView>(R.id.streamSourceLanguage).text =
                context.getString(
                    R.string.stream_language_label,
                    detectLanguages(stream)
                )
            val active = stream.url == activeUrl
            view.findViewById<View>(R.id.streamSourceActive).visibility =
                if (active) View.VISIBLE else View.GONE
            view.isActivated = active

            // Render the validation status badge. We only show "stream ok"
            // when the upstream probe reported 2xx with valid video stream
            // headers; we show "stream failed" when the probe reached the
            // server (2xx) but the response did not carry video stream signals,
            // or when the stream status is unknown (not yet validated).
            val statusView = view.findViewById<TextView>(R.id.streamSourceStatus)
            when {
                stream.isValid && !stream.isFailed -> {
                    statusView.text = context.getString(R.string.stream_ok)
                    statusView.setBackgroundResource(R.drawable.bg_stream_status_ok)
                    statusView.visibility = View.VISIBLE
                }
                stream.isChecking -> {
                    statusView.text = context.getString(R.string.stream_checking)
                    statusView.setBackgroundResource(R.drawable.bg_stream_status_pending)
                    statusView.visibility = View.VISIBLE
                }
                stream.isFailed || stream.httpStatusCode == 0 || (stream.httpStatusCode ?: 0) >= 400 -> {
                    // Show failed badge for explicitly failed streams or streams
                    // with HTTP error codes (4xx/5xx) or unknown status (0)
                    statusView.text = context.getString(R.string.stream_failed)
                    statusView.setBackgroundResource(R.drawable.bg_stream_status_failed)
                    statusView.visibility = View.VISIBLE
                }
                else -> {
                    // For any other unknown state, show as failed so user knows to try another
                    statusView.text = context.getString(R.string.stream_failed)
                    statusView.setBackgroundResource(R.drawable.bg_stream_status_failed)
                    statusView.visibility = View.VISIBLE
                }
            }
            return view
        }

        private fun detectLanguages(stream: StreamItem): String {
            val searchableText = "${stream.name} ${stream.title}"
            val languages = LANGUAGE_PATTERNS.mapNotNull { (label, pattern) ->
                label.takeIf { pattern.containsMatchIn(searchableText) }
            }
            return languages.joinToString(" • ")
                .ifBlank { context.getString(R.string.stream_language_unknown) }
        }

        companion object {
            private val LANGUAGE_PATTERNS = listOf(
                "English" to Regex("\\benglish\\b", RegexOption.IGNORE_CASE),
                "Hindi" to Regex("\\bhindi\\b", RegexOption.IGNORE_CASE),
                "Telugu" to Regex("\\btelugu\\b", RegexOption.IGNORE_CASE),
                "Tamil" to Regex("\\btamil\\b", RegexOption.IGNORE_CASE)
            )
        }
    }
}
