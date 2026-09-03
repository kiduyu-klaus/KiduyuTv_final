package com.kiduyuk.klausk.kiduyutv.ui.player.directstream.playback

import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import android.widget.AdapterView
import android.widget.BaseAdapter
import android.widget.ListView
import android.widget.TextView
import androidx.annotation.RequiresApi
import androidx.media3.common.C
import androidx.media3.common.TrackGroup
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.TrackSelectionParameters
import androidx.media3.common.Tracks
import com.kiduyuk.klausk.kiduyutv.R

/**
 * Tabbed track-selection dialog.
 *
 * Built around a single [ListView] whose contents are swapped when the
 * user picks a tab. The first row of every list is an "Auto" / "Off"
 * entry; subsequent rows are the actual tracks in manifest order. The
 * currently-active track gets a checkmark.
 *
 * Selecting a row applies the change immediately (no "OK" step) so the
 * user can browse quality / language / subtitle options by feel.
 *
 * Media3 1.4.1 API notes:
 *  - `TrackGroup` is the correct class name (no `MediaTrackGroup`).
 *  - `TrackSelectionParameters` has no public `trackSelectionOverrides`
 *    accessor and no `clearOverrides(int)`.
 *  - `TrackSelectionOverride` in 1.4.1 doesn't expose a reliable public
 *    `trackIndex` / `trackIndexes` field for read-back (the class is
 *    `@Deprecated` and the field name has changed across versions). We
 *    track the active selection ourselves and rebuild the parameters
 *    from that state on every change.
 */
@RequiresApi(Build.VERSION_CODES.ICE_CREAM_SANDWICH)
class TrackSelectionDialog(
    context: Context,
    private val tracks: Tracks,
    private val initialParameters: TrackSelectionParameters,
    private val onApply: (TrackSelectionParameters) -> Unit
) : Dialog(context) {

    private val tabVideo: TextView
    private val tabAudio: TextView
    private val tabSubtitle: TextView
    private val list: ListView
    private val emptyMessage: TextView
    private val btnClose: TextView

    /**
     * The active override for each track type, keyed by the type constant
     * from [C]. The `TrackGroup` is held alongside the indices because
     * [TrackSelectionOverride] needs the underlying [TrackGroup] when
     * the user re-applies or the dialog rebuilds the parameters.
     */
    private val activeOverrides: MutableMap<Int, ActiveOverride> = mutableMapOf()
    private val disabledTypes: MutableSet<Int> = initialParameters.disabledTrackTypes.toMutableSet()

    private val videoRows: MutableList<TrackRow> = buildRows(
        type = C.TRACK_TYPE_VIDEO,
        autoLabel = "Auto"
    )
    private val audioRows: MutableList<TrackRow> = buildRows(
        type = C.TRACK_TYPE_AUDIO,
        autoLabel = "Auto"
    )
    private val subtitleRows: MutableList<TrackRow> = buildRows(
        type = C.TRACK_TYPE_TEXT,
        autoLabel = "Off"
    )

    private var currentTab: Int = C.TRACK_TYPE_VIDEO
    private var currentParameters: TrackSelectionParameters = initialParameters
    private lateinit var listAdapter: TrackListAdapter

    init {
        // Seed the override map from the manifest: any audio/subtitle
        // group that has a currently-selected track contributes an
        // override. For HLS video groups, isTrackSelected() is unreliable
        // (the player picks a quality per-buffer), so we leave video in
        // the "Auto" state by default.
        tracks.groups.forEach { group ->
            when (group.type) {
                C.TRACK_TYPE_AUDIO, C.TRACK_TYPE_TEXT -> {
                    val selectedIndex = (0 until group.length).firstOrNull {
                        group.isTrackSelected(it)
                    }
                    if (selectedIndex != null) {
                        activeOverrides[group.type] = ActiveOverride(
                            type = group.type,
                            groupIndex = indexOfGroup(group),
                            trackIndex = selectedIndex,
                            trackGroup = group.mediaTrackGroup
                        )
                    }
                }
            }
        }
        refreshSelections()

        requestWindowFeature(Window.FEATURE_NO_TITLE)
        val view = LayoutInflater.from(context).inflate(R.layout.dialog_direct_stream_tracks, null)
        setContentView(view)

        window?.let {
            it.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            it.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            it.setDimAmount(0.6f)
            it.setGravity(Gravity.CENTER)
        }

        tabVideo = view.findViewById(R.id.tabVideo)
        tabAudio = view.findViewById(R.id.tabAudio)
        tabSubtitle = view.findViewById(R.id.tabSubtitle)
        list = view.findViewById(R.id.listTracks)
        emptyMessage = view.findViewById(R.id.emptyMessage)
        btnClose = view.findViewById(R.id.btnClose)

        wireTabs()
        wireList()
        wireClose()

        // Default to the first tab that has any rows, otherwise Video.
        currentTab = when {
            videoRows.isNotEmpty()    -> C.TRACK_TYPE_VIDEO
            audioRows.isNotEmpty()    -> C.TRACK_TYPE_AUDIO
            subtitleRows.isNotEmpty() -> C.TRACK_TYPE_TEXT
            else                      -> C.TRACK_TYPE_VIDEO
        }
        applyTab(currentTab)
    }

    private fun wireTabs() {
        tabVideo.setOnClickListener { applyTab(C.TRACK_TYPE_VIDEO) }
        tabAudio.setOnClickListener { applyTab(C.TRACK_TYPE_AUDIO) }
        tabSubtitle.setOnClickListener { applyTab(C.TRACK_TYPE_TEXT) }
    }

    private fun wireList() {
        listAdapter = TrackListAdapter(context)
        list.adapter = listAdapter
        list.onItemClickListener = AdapterView.OnItemClickListener { _, _, position, _ ->
            val rows = rowsFor(currentTab)
            if (position in rows.indices) {
                applyRow(rows[position])
            }
        }
    }

    private fun wireClose() {
        btnClose.setOnClickListener { dismiss() }
    }

    private fun applyTab(type: Int) {
        currentTab = type
        tabVideo.isSelected = type == C.TRACK_TYPE_VIDEO
        tabAudio.isSelected = type == C.TRACK_TYPE_AUDIO
        tabSubtitle.isSelected = type == C.TRACK_TYPE_TEXT

        val rows = rowsFor(type)
        listAdapter.submit(rows)
        list.nextFocusUpId = when (type) {
            C.TRACK_TYPE_AUDIO -> R.id.tabAudio
            C.TRACK_TYPE_TEXT -> R.id.tabSubtitle
            else -> R.id.tabVideo
        }
        if (rows.isEmpty()) {
            emptyMessage.visibility = View.VISIBLE
            emptyMessage.text = context.getString(R.string.track_none_available)
        } else {
            emptyMessage.visibility = View.GONE
            val selectedIndex = rows.indexOfFirst { it.isSelected }.coerceAtLeast(0)
            list.setSelection(selectedIndex)
        }
    }

    private fun rowsFor(type: Int): List<TrackRow> = when (type) {
        C.TRACK_TYPE_VIDEO -> videoRows
        C.TRACK_TYPE_AUDIO -> audioRows
        C.TRACK_TYPE_TEXT  -> subtitleRows
        else               -> emptyList()
    }

    /**
     * Builds the list of selectable rows for [type]. The first row is
     * always the "Auto"/"Off" entry; subsequent rows are the actual tracks
     * in manifest order.
     */
    private fun buildRows(type: Int, autoLabel: String): MutableList<TrackRow> {
        val rows = mutableListOf<TrackRow>()
        rows.add(
            TrackRow(
                type = type,
                groupIndex = -1,
                trackIndex = -1,
                title = autoLabel,
                subtitle = if (type == C.TRACK_TYPE_TEXT) "" else "Default",
                isAuto = true,
                isSelected = false
            )
        )
        tracks.groups.forEachIndexed { groupIndex, group ->
            if (group.type != type || group.length == 0) return@forEachIndexed
            for (trackIndex in 0 until group.length) {
                val format = group.getTrackFormat(trackIndex)
                val isDefaultSubtitle = type == C.TRACK_TYPE_TEXT &&
                    format.label.isNullOrBlank() &&
                    (format.language.isNullOrBlank() ||
                        format.language.equals("und", ignoreCase = true))
                rows.add(
                    TrackRow(
                        type = type,
                        groupIndex = groupIndex,
                        trackIndex = trackIndex,
                        title = when {
                            isDefaultSubtitle -> "Default"
                            type == C.TRACK_TYPE_AUDIO -> audioTitleOf(format)
                            else -> TrackFormatter.titleOf(format)
                        },
                        subtitle = if (isDefaultSubtitle) {
                            ""
                        } else {
                            TrackFormatter.describe(format)
                                .ifBlank { TrackFormatter.languageDisplay(format.language ?: "") }
                        },
                        isAuto = false,
                        isSelected = false
                    )
                )
            }
        }
        return rows
    }

    /**
     * Audio manifests often expose a generic label such as "Track" while
     * carrying the useful language in Format.language. Prefer the readable
     * language in the primary row title and keep codec/technical details in
     * the secondary line.
     */
    private fun audioTitleOf(format: androidx.media3.common.Format): String {
        val language = TrackFormatter.languageDisplay(format.language.orEmpty())
        val label = format.label?.trim().orEmpty()
        if (label.isBlank() || label.equals("track", ignoreCase = true)) return language
        if (label.equals(language, ignoreCase = true)) return language
        return if (language.equals("Default", ignoreCase = true)) label else "$language · $label"
    }

    /**
     * Applies the chosen row by mutating the per-type override map and
     * rebuilding [TrackSelectionParameters] from scratch. The "Auto" row
     * removes any override for the type; a specific track installs one.
     */
    private fun applyRow(row: TrackRow) {
        when {
            row.isAuto && row.type == C.TRACK_TYPE_TEXT -> {
                activeOverrides.remove(row.type)
                disabledTypes.add(row.type)
            }
            row.isAuto -> {
                activeOverrides.remove(row.type)
                disabledTypes.remove(row.type)
            }
            row.groupIndex < 0 -> return
            else -> {
                val group = tracks.groups[row.groupIndex]
                activeOverrides[row.type] = ActiveOverride(
                    type = row.type,
                    groupIndex = row.groupIndex,
                    trackIndex = row.trackIndex,
                    trackGroup = group.mediaTrackGroup
                )
                disabledTypes.remove(row.type)
            }
        }
        val next = buildParameters()
        currentParameters = next
        refreshSelections()
        renderCurrentSelection()
        onApply(next)

        Log.i(
            TAG,
            "Applied ${row.title} (type=${row.type} auto=${row.isAuto}) " +
                "active=${activeOverrides.keys} disabled=$disabledTypes"
        )
    }

    /**
     * Reconciles explicit dialog overrides with the track ExoPlayer reports
     * as selected after applying new parameters. Auto selections remain Auto;
     * only a type for which the user picked a concrete row is updated.
     */
    fun updateCurrentTracks(latestTracks: Tracks) {
        activeOverrides.toMap().forEach { (type, active) ->
            val confirmedGroupIndex = latestTracks.groups.indexOfFirst {
                it.type == type && it.mediaTrackGroup == active.trackGroup
            }
            if (confirmedGroupIndex < 0) return@forEach

            val confirmedGroup = latestTracks.groups[confirmedGroupIndex]
            val confirmedTrackIndex = (0 until confirmedGroup.length).firstOrNull {
                confirmedGroup.isTrackSelected(it)
            } ?: return@forEach

            val dialogGroupIndex = tracks.groups.indexOfFirst {
                it.type == type && it.mediaTrackGroup == confirmedGroup.mediaTrackGroup
            }
            if (dialogGroupIndex >= 0) {
                activeOverrides[type] = ActiveOverride(
                    type = type,
                    groupIndex = dialogGroupIndex,
                    trackIndex = confirmedTrackIndex,
                    trackGroup = confirmedGroup.mediaTrackGroup
                )
            }
        }
        refreshSelections()
        renderCurrentSelection()
    }

    private fun renderCurrentSelection() {
        if (!::listAdapter.isInitialized) return
        val rows = rowsFor(currentTab)
        listAdapter.submit(rows)
        val selectedIndex = rows.indexOfFirst { it.isSelected }
        if (selectedIndex >= 0) {
            list.setSelection(selectedIndex)
        }
        list.invalidateViews()
    }

    /**
     * Rebuild [TrackSelectionParameters] from the current per-type map.
     * Media3 1.4.1's [TrackSelectionParameters.Builder.clearOverrides] has
     * no per-type overload, so we clear everything and re-apply the
     * overrides we still want.
     */
    private fun buildParameters(): TrackSelectionParameters {
        val builder = currentParameters.buildUpon().clearOverrides()
        TRACK_TYPES.forEach { type ->
            builder.setTrackTypeDisabled(type, disabledTypes.contains(type))
        }
        activeOverrides.values.forEach { active ->
            val override = TrackSelectionOverride(active.trackGroup, active.trackIndex)
            builder.setOverrideForType(override)
        }
        return builder.build()
    }

    /**
     * Re-derives each row's `isSelected` flag from the active-override
     * map. Uses the dialog's own state (not the player) so the checkmark
     * placement is deterministic even before the next
     * `onTracksChanged` callback fires.
     */
    private fun refreshSelections() {
        forEachType { type, rows ->
            val isAutoSelected = if (type == C.TRACK_TYPE_TEXT) {
                disabledTypes.contains(type) && !activeOverrides.containsKey(type)
            } else {
                !disabledTypes.contains(type) && !activeOverrides.containsKey(type)
            }
            val active = activeOverrides[type]
            rows.forEach { row ->
                row.isSelected = when {
                    row.isAuto -> isAutoSelected
                    else -> active != null &&
                        row.groupIndex == active.groupIndex &&
                        row.trackIndex == active.trackIndex
                }
            }
        }
    }

    private inline fun forEachType(block: (Int, MutableList<TrackRow>) -> Unit) {
        block(C.TRACK_TYPE_VIDEO, videoRows)
        block(C.TRACK_TYPE_AUDIO, audioRows)
        block(C.TRACK_TYPE_TEXT, subtitleRows)
    }

    /** Returns the index of [group] in `tracks.groups`, or -1 if absent. */
    private fun indexOfGroup(group: Tracks.Group): Int =
        tracks.groups.indexOf(group).takeIf { it >= 0 } ?: -1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setCanceledOnTouchOutside(true)
    }

    override fun onStart() {
        super.onStart()
        val maxWidth = (context.resources.displayMetrics.widthPixels * 0.72f).toInt()
        val preferredWidth = (520 * context.resources.displayMetrics.density).toInt()
        window?.setLayout(
            preferredWidth.coerceAtMost(maxWidth),
            WindowManager.LayoutParams.WRAP_CONTENT
        )
        when (currentTab) {
            C.TRACK_TYPE_AUDIO -> tabAudio
            C.TRACK_TYPE_TEXT -> tabSubtitle
            else -> tabVideo
        }.requestFocus()
    }

    override fun onBackPressed() {
        dismiss()
    }

    /**
     * Per-type active override state. The dialog holds one of these per
     * track type that the user has picked something non-default on.
     */
    private data class ActiveOverride(
        val type: Int,
        val groupIndex: Int,
        val trackIndex: Int,
        val trackGroup: TrackGroup
    )

    private data class TrackRow(
        val type: Int,
        val groupIndex: Int,
        val trackIndex: Int,
        val title: String,
        val subtitle: String,
        val isAuto: Boolean,
        var isSelected: Boolean
    )

    /**
     * Tiny [BaseAdapter] that renders [TrackRow]s into the dialog's
     * [ListView]. We use a plain adapter (not RecyclerView) because the
     * list is short and lives only as long as the dialog.
     */
    private class TrackListAdapter(private val context: Context) : BaseAdapter() {
        private var rows: List<TrackRow> = emptyList()

        fun submit(newRows: List<TrackRow>) {
            rows = newRows
            notifyDataSetChanged()
        }

        override fun getCount(): Int = rows.size
        override fun getItem(position: Int): TrackRow = rows[position]
        override fun getItemId(position: Int): Long = position.toLong()

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val row = rows[position]
            val view = convertView ?: LayoutInflater.from(context)
                .inflate(R.layout.item_direct_stream_track, parent, false)
            val title = view.findViewById<TextView>(R.id.trackTitle)
            val subtitle = view.findViewById<TextView>(R.id.trackSubtitle)
            val check = view.findViewById<View>(R.id.trackCheck)
            title.text = row.title
            subtitle.text = row.subtitle
            subtitle.visibility = if (row.subtitle.isBlank()) View.GONE else View.VISIBLE
            check.visibility = if (row.isSelected) View.VISIBLE else View.INVISIBLE
            view.isActivated = row.isSelected
            return view
        }
    }

    private companion object {
        private const val TAG = "KiduyuLiteTrackDialog"
        private val TRACK_TYPES = intArrayOf(
            C.TRACK_TYPE_VIDEO,
            C.TRACK_TYPE_AUDIO,
            C.TRACK_TYPE_TEXT
        )
    }
}
