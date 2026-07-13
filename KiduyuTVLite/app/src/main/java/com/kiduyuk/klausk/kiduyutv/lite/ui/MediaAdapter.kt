package com.kiduyuk.klausk.kiduyutv.lite.ui

import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.kiduyuk.klausk.kiduyutv.lite.R
import com.kiduyuk.klausk.kiduyutv.lite.model.MediaItem

class MediaAdapter(
    private val items: List<MediaItem>,
    private val onSelect: (MediaItem) -> Unit
) : RecyclerView.Adapter<MediaAdapter.CardViewHolder>() {

    init {
        setHasStableIds(true)
    }

    inner class CardViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val cardRoot: View = view.findViewById(R.id.cardRoot)
        val poster: ImageView = view.findViewById(R.id.cardPoster)
        val title: TextView = view.findViewById(R.id.cardTitle)
        val badge: TextView = view.findViewById(R.id.cardBadge)
        val focusRing: View = view.findViewById(R.id.cardFocusRing)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CardViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_media_card, parent, false)
        view.isFocusable = true
        view.isFocusableInTouchMode = true
        return CardViewHolder(view)
    }

    override fun getItemCount(): Int = items.size

    override fun getItemId(position: Int): Long {
        val item = items[position]
        val typeBit = if (item.isMovie) 0L else 1L
        return (item.id.toLong() shl 1) or typeBit
    }

    override fun onBindViewHolder(holder: CardViewHolder, position: Int) {
        val item = items[position]
        val context = holder.itemView.context

        holder.title.text = item.title
        holder.badge.text = context.getString(
            if (item.isMovie) R.string.movie_badge else R.string.tv_badge
        )
        holder.badge.setBackgroundColor(
            context.getColor(if (item.isMovie) R.color.kiduyu_red else R.color.kiduyu_red_dark)
        )

        holder.poster.load(item.posterUrl.takeIf { it.isNotBlank() }) {
            placeholder(R.drawable.placeholder)
            error(R.drawable.placeholder)
            crossfade(true)
        }

        holder.itemView.setOnClickListener { onSelect(item) }
        holder.itemView.setOnKeyListener { _, keyCode, event ->
            if (
                event.action == KeyEvent.ACTION_DOWN &&
                (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER)
            ) {
                onSelect(item)
                true
            } else {
                false
            }
        }

        holder.itemView.setOnFocusChangeListener { _, hasFocus ->
            holder.focusRing.visibility = if (hasFocus) View.VISIBLE else View.INVISIBLE
            holder.cardRoot.animate()
                .scaleX(if (hasFocus) 1.10f else 1f)
                .scaleY(if (hasFocus) 1.10f else 1f)
                .translationZ(if (hasFocus) 16f else 0f)
                .setDuration(160)
                .start()
        }
    }
}
