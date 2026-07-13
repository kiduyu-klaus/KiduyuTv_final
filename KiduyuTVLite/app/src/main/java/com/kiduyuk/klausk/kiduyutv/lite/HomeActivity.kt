package com.kiduyuk.klausk.kiduyutv.lite

import android.content.Intent
import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.kiduyuk.klausk.kiduyutv.lite.api.TmdbApi
import com.kiduyuk.klausk.kiduyutv.lite.model.MediaItem
import com.kiduyuk.klausk.kiduyutv.lite.ui.MediaAdapter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class HomeActivity : AppCompatActivity() {

    private data class HomeRow(
        val recyclerView: RecyclerView,
        val statusView: TextView,
        val fetch: () -> List<MediaItem>,
        var loadJob: Job? = null,
        var skipInVerticalNavigation: Boolean = false
    )

    private lateinit var rows: List<HomeRow>
    private lateinit var searchInput: EditText
    private lateinit var searchOverlay: View
    private lateinit var searchResults: RecyclerView
    private lateinit var searchStatus: TextView
    private lateinit var homeContent: ViewGroup

    private var searchJob: Job? = null
    private var searchOverlayVisible = false
    private var currentRow = 0
    private val rowPositions = intArrayOf(0, 0, 0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        makeFullscreen()
        setContentView(R.layout.activity_home)

        searchInput = findViewById(R.id.searchInput)
        searchOverlay = findViewById(R.id.searchOverlay)
        searchResults = findViewById(R.id.rvSearchOverlay)
        searchStatus = findViewById(R.id.searchStatus)
        homeContent = findViewById(R.id.homeContent)

        rows = listOf(
            HomeRow(
                findViewById(R.id.rvTrending),
                findViewById(R.id.statusTrending),
                TmdbApi::trendingAll
            ),
            HomeRow(
                findViewById(R.id.rvMovies),
                findViewById(R.id.statusMovies),
                TmdbApi::popularMovies
            ),
            HomeRow(
                findViewById(R.id.rvTv),
                findViewById(R.id.statusTv),
                TmdbApi::popularTv
            )
        )

        currentRow = savedInstanceState?.getInt(STATE_ROW, 0)
            ?.coerceIn(rows.indices) ?: 0
        savedInstanceState?.getIntArray(STATE_POSITIONS)?.forEachIndexed { index, value ->
            if (index in rowPositions.indices) rowPositions[index] = value.coerceAtLeast(0)
        }

        setupRows()
        setupSearch()
        loadAllRows()

        val restoredQuery = savedInstanceState?.getString(STATE_SEARCH_QUERY).orEmpty()
        if (savedInstanceState?.getBoolean(STATE_SEARCH_VISIBLE) == true && restoredQuery.isNotBlank()) {
            searchInput.setText(restoredQuery)
            showSearchOverlay(restoredQuery)
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt(STATE_ROW, currentRow)
        outState.putIntArray(STATE_POSITIONS, rowPositions)
        outState.putBoolean(STATE_SEARCH_VISIBLE, searchOverlayVisible)
        outState.putString(STATE_SEARCH_QUERY, searchInput.text.toString())
    }

    override fun onDestroy() {
        searchJob?.cancel()
        rows.forEach { it.loadJob?.cancel() }
        super.onDestroy()
    }

    private fun makeFullscreen() {
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_FULLSCREEN or
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
            )
    }

    private fun setupRows() {
        rows.forEachIndexed { index, row ->
            row.recyclerView.apply {
                layoutManager = LinearLayoutManager(
                    this@HomeActivity,
                    LinearLayoutManager.HORIZONTAL,
                    false
                )
                clipChildren = false
                clipToPadding = false
                isFocusable = false
                isFocusableInTouchMode = false
            }
            row.statusView.setOnClickListener { loadRow(index) }
        }
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action != KeyEvent.ACTION_DOWN) return super.dispatchKeyEvent(event)

        if (searchOverlayVisible) {
            if (event.keyCode == KeyEvent.KEYCODE_BACK || event.keyCode == KeyEvent.KEYCODE_ESCAPE) {
                hideSearchOverlay()
                return true
            }
            return super.dispatchKeyEvent(event)
        }

        if (currentFocus?.id == R.id.searchInput) {
            if (
                event.keyCode == KeyEvent.KEYCODE_BACK ||
                event.keyCode == KeyEvent.KEYCODE_ESCAPE ||
                event.keyCode == KeyEvent.KEYCODE_DPAD_DOWN
            ) {
                focusRow(currentRow)
                return true
            }
            return super.dispatchKeyEvent(event)
        }

        when (event.keyCode) {
            KeyEvent.KEYCODE_DPAD_RIGHT -> {
                val row = rows[currentRow]
                val lastIndex = (row.recyclerView.adapter?.itemCount ?: 0) - 1
                if (rowPositions[currentRow] < lastIndex) {
                    rowPositions[currentRow]++
                    focusCard(row.recyclerView, rowPositions[currentRow])
                }
                return true
            }

            KeyEvent.KEYCODE_DPAD_LEFT -> {
                if (rowPositions[currentRow] > 0) {
                    rowPositions[currentRow]--
                    focusCard(rows[currentRow].recyclerView, rowPositions[currentRow])
                }
                return true
            }

            KeyEvent.KEYCODE_DPAD_DOWN -> {
                moveVertically(1)
                return true
            }

            KeyEvent.KEYCODE_DPAD_UP -> {
                moveVertically(-1)
                return true
            }

            KeyEvent.KEYCODE_DPAD_CENTER,
            KeyEvent.KEYCODE_ENTER -> {
                val row = rows[currentRow]
                val holder = row.recyclerView
                    .findViewHolderForAdapterPosition(rowPositions[currentRow])
                if (holder != null) {
                    holder.itemView.performClick()
                } else if (row.statusView.isClickable) {
                    row.statusView.performClick()
                }
                return true
            }

            KeyEvent.KEYCODE_BACK,
            KeyEvent.KEYCODE_ESCAPE -> {
                finish()
                return true
            }
        }

        return super.dispatchKeyEvent(event)
    }

    private fun moveVertically(direction: Int) {
        var requestedRow = currentRow + direction
        while (
            requestedRow in rows.indices &&
            rows[requestedRow].skipInVerticalNavigation
        ) {
            requestedRow += direction
        }

        if (requestedRow !in rows.indices) {
            if (direction < 0) searchInput.requestFocus()
            return
        }

        currentRow = requestedRow
        focusRow(currentRow)
        scrollToRow(currentRow)
    }

    private fun focusRow(rowIndex: Int) {
        val row = rows[rowIndex]
        val count = row.recyclerView.adapter?.itemCount ?: 0
        if (count > 0) {
            rowPositions[rowIndex] = rowPositions[rowIndex].coerceIn(0, count - 1)
            focusCard(row.recyclerView, rowPositions[rowIndex])
        } else if (row.statusView.visibility == View.VISIBLE) {
            row.statusView.requestFocus()
        }
    }

    private fun focusCard(recyclerView: RecyclerView, requestedPosition: Int) {
        val count = recyclerView.adapter?.itemCount ?: 0
        if (count == 0) return
        val position = requestedPosition.coerceIn(0, count - 1)
        recyclerView.scrollToPosition(position)
        recyclerView.post {
            val holder = recyclerView.findViewHolderForAdapterPosition(position)
            if (holder != null) {
                holder.itemView.requestFocus()
            } else {
                recyclerView.postDelayed({
                    recyclerView.findViewHolderForAdapterPosition(position)
                        ?.itemView
                        ?.requestFocus()
                }, 50)
            }
        }
    }

    private fun scrollToRow(rowIndex: Int) {
        val scrollView = findViewById<ScrollView>(R.id.rowsScroll)
        val rowBlockPx = (280 * resources.displayMetrics.density).toInt()
        scrollView.smoothScrollTo(0, rowIndex * rowBlockPx)
    }

    private fun loadAllRows() {
        rows.indices.forEach(::loadRow)
    }

    private fun loadRow(rowIndex: Int) {
        val row = rows[rowIndex]
        row.loadJob?.cancel()
        showRowStatus(row, getString(R.string.loading_titles), retryEnabled = false)

        row.loadJob = lifecycleScope.launch {
            val result = runCatching {
                withContext(Dispatchers.IO) { row.fetch() }
            }

            result.onSuccess { items ->
                if (items.isEmpty()) {
                    showRowStatus(
                        row,
                        getString(R.string.empty_titles),
                        retryEnabled = true,
                        skipInVerticalNavigation = true
                    )
                } else {
                    bindRow(rowIndex, items)
                }
            }.onFailure {
                showRowStatus(row, getString(R.string.load_failed), retryEnabled = true)
            }
        }
    }

    private fun bindRow(rowIndex: Int, items: List<MediaItem>) {
        val row = rows[rowIndex]
        row.statusView.visibility = View.GONE
        row.statusView.isFocusable = false
        row.skipInVerticalNavigation = false
        row.recyclerView.adapter = MediaAdapter(items, ::openDetail)
        rowPositions[rowIndex] = rowPositions[rowIndex].coerceIn(items.indices)

        if (
            rowIndex == currentRow &&
            !searchOverlayVisible &&
            (currentFocus == null || currentFocus === row.statusView)
        ) {
            focusCard(row.recyclerView, rowPositions[rowIndex])
        }
    }

    private fun showRowStatus(
        row: HomeRow,
        message: String,
        retryEnabled: Boolean,
        skipInVerticalNavigation: Boolean = false
    ) {
        row.statusView.text = message
        row.statusView.visibility = View.VISIBLE
        row.statusView.isClickable = retryEnabled
        row.statusView.isFocusable = true
        row.statusView.alpha = if (retryEnabled) 1f else 0.75f
        row.skipInVerticalNavigation = skipInVerticalNavigation
    }

    private fun setupSearch() {
        searchResults.layoutManager = GridLayoutManager(this, 5)
        searchResults.clipChildren = false
        searchResults.clipToPadding = false
        findViewById<View>(R.id.btnSearchClose).setOnClickListener { hideSearchOverlay() }

        searchInput.setOnEditorActionListener { _, actionId, event ->
            val isSearch = actionId == EditorInfo.IME_ACTION_SEARCH
            val isEnter = event?.keyCode == KeyEvent.KEYCODE_ENTER &&
                event.action == KeyEvent.ACTION_DOWN
            if (isSearch || isEnter) {
                val query = searchInput.text.toString().trim()
                if (query.isNotEmpty()) {
                    (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager)
                        .hideSoftInputFromWindow(searchInput.windowToken, 0)
                    searchInput.clearFocus()
                    showSearchOverlay(query)
                }
                true
            } else {
                false
            }
        }
    }

    private fun showSearchOverlay(query: String) {
        searchJob?.cancel()
        findViewById<TextView>(R.id.labelSearchQuery).text =
            getString(R.string.search_results_for, query)
        searchOverlay.visibility = View.VISIBLE
        searchOverlayVisible = true
        homeContent.descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
        searchResults.adapter = null
        searchResults.visibility = View.GONE
        showSearchStatus(getString(R.string.search_loading), retryEnabled = false) {}
        findViewById<View>(R.id.btnSearchClose).requestFocus()

        searchJob = lifecycleScope.launch {
            val result = runCatching {
                withContext(Dispatchers.IO) { TmdbApi.search(query) }
            }

            result.onSuccess { items ->
                if (items.isEmpty()) {
                    showSearchStatus(getString(R.string.search_empty), retryEnabled = true) {
                        hideSearchOverlay()
                    }
                } else {
                    searchStatus.visibility = View.GONE
                    searchResults.visibility = View.VISIBLE
                    searchResults.adapter = MediaAdapter(items, ::openDetail)
                    searchResults.post {
                        searchResults.findViewHolderForAdapterPosition(0)
                            ?.itemView
                            ?.requestFocus()
                            ?: searchResults.requestFocus()
                    }
                }
            }.onFailure {
                showSearchStatus(getString(R.string.search_failed), retryEnabled = true) {
                    showSearchOverlay(query)
                }
            }
        }
    }

    private fun showSearchStatus(
        message: String,
        retryEnabled: Boolean,
        action: () -> Unit
    ) {
        searchStatus.text = message
        searchStatus.visibility = View.VISIBLE
        searchStatus.isFocusable = retryEnabled
        searchStatus.isClickable = retryEnabled
        searchStatus.setOnClickListener { if (retryEnabled) action() }
        if (retryEnabled) searchStatus.requestFocus()
    }

    private fun hideSearchOverlay() {
        searchJob?.cancel()
        searchOverlay.visibility = View.GONE
        searchResults.adapter = null
        searchResults.visibility = View.VISIBLE
        searchStatus.visibility = View.GONE
        searchOverlayVisible = false
        homeContent.descendantFocusability = ViewGroup.FOCUS_AFTER_DESCENDANTS
        searchInput.text.clear()
        focusRow(currentRow)
    }

    private fun openDetail(item: MediaItem) {
        startActivity(Intent(this, DetailActivity::class.java).apply {
            putExtra(EXTRA_ID, item.id)
            putExtra(EXTRA_TITLE, item.title)
            putExtra(EXTRA_OVERVIEW, item.overview)
            putExtra(EXTRA_POSTER_URL, item.posterUrl)
            putExtra(EXTRA_BACKDROP_URL, item.backdropUrl)
            putExtra(EXTRA_MEDIA_TYPE, item.mediaType)
            putExtra(EXTRA_RATING, item.voteAverage)
            putExtra(EXTRA_YEAR, item.releaseDate.take(4))
        })
    }

    companion object {
        const val EXTRA_ID = "id"
        const val EXTRA_TITLE = "title"
        const val EXTRA_OVERVIEW = "overview"
        const val EXTRA_POSTER_URL = "poster_url"
        const val EXTRA_BACKDROP_URL = "backdrop_url"
        const val EXTRA_MEDIA_TYPE = "media_type"
        const val EXTRA_RATING = "rating"
        const val EXTRA_YEAR = "year"

        private const val STATE_ROW = "current_row"
        private const val STATE_POSITIONS = "row_positions"
        private const val STATE_SEARCH_VISIBLE = "search_visible"
        private const val STATE_SEARCH_QUERY = "search_query"
    }
}
