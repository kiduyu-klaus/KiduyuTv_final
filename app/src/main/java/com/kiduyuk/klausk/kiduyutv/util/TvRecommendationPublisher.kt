package com.kiduyuk.klausk.kiduyutv.util

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.BaseColumns
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.tvprovider.media.tv.Channel
import androidx.tvprovider.media.tv.ChannelLogoUtils
import androidx.tvprovider.media.tv.PreviewProgram
import androidx.tvprovider.media.tv.TvContractCompat
import com.kiduyuk.klausk.kiduyutv.R
import com.kiduyuk.klausk.kiduyutv.data.api.TmdbApiService
import com.kiduyuk.klausk.kiduyutv.data.model.Movie
import com.kiduyuk.klausk.kiduyutv.data.model.TvShow
import com.kiduyuk.klausk.kiduyutv.data.repository.TmdbRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

private data class RecommendationItem(
    val key: String,
    val id: Int,
    val type: String,
    val title: String,
    val overview: String,
    val posterPath: String?,
    val backdropPath: String?
) {
    val posterUrl: String?
        get() = posterPath?.takeIf { it.isNotBlank() }?.let {
            "${TmdbApiService.IMAGE_BASE_URL}${TmdbApiService.POSTER_SIZE}$it"
        }
    val backdropUrl: String?
        get() = backdropPath?.takeIf { it.isNotBlank() }?.let {
            "${TmdbApiService.IMAGE_BASE_URL}${TmdbApiService.BACKDROP_SIZE}$it"
        }
}

/** Publishes a bounded, idempotent set of TMDB trending catalogue cards. */
object TvRecommendationPublisher {
    private const val TAG = "TvRecommendationPublisher"
    private const val PREFS = "tv_recommendations"
    private const val KEY_DEFAULT_CHANNEL_ID = "default_channel_id"
    private const val KEY_DISMISSED_KEYS = "dismissed_keys"
    private const val KEY_PUBLISHED_NOTIFICATION_KEYS = "published_notification_keys"
    private const val DEFAULT_CHANNEL_NAME = "Trending on KiduyuTV"
    private const val APP_SCHEME = "kiduyutv"
    private const val FIRE_CHANNEL_ID = "kiduyutv_recommendations"
    private const val FIRE_CHANNEL_NAME = "KiduyuTV recommendations"
    private const val FIRE_CHANNEL_DESCRIPTION = "Movie and TV-show picks from KiduyuTV"
    private const val NOTIFICATION_ID_BASE = 24000
    private const val MAX_ITEMS = 12

    private val imageClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    suspend fun refresh(context: Context): Boolean = withContext(Dispatchers.IO) {
        if (!isTvLikeDevice(context)) return@withContext false
        val repository = TmdbRepository()
        val movies = repository.getTrendingMoviesToday().getOrNull().orEmpty()
        val shows = repository.getTrendingTvToday().getOrNull().orEmpty()
        val items = buildItems(movies, shows)
        if (items.isEmpty()) return@withContext false

        when {
            isAmazonDevice() -> {
                // Fire TV receives both surfaces: Preview Channel programs on
                // API 26+ and recommendation-style notifications enriched
                // with Amazon-specific metadata. Older Fire TV versions keep
                // the notification path because Preview Channels are not
                // available before Android O.
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    publishPreviewChannel(context, items)
                }
                publishFireRecommendations(context, items)
            }
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.O -> publishPreviewChannel(context, items)
            else -> publishLegacyRecommendations(context, items)
        }
        true
    }

    fun handleProgramDisabled(context: Context, programId: Long) {
        if (programId <= 0L || Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        runCatching {
            val uri = ContentUris.withAppendedId(
                TvContractCompat.PreviewPrograms.CONTENT_URI,
                programId
            )
            context.contentResolver.query(
                uri,
                arrayOf("internal_provider_id"),
                null,
                null,
                null
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val key = cursor.getString(0)
                    if (!key.isNullOrBlank()) {
                        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                        val dismissed = prefs.getStringSet(KEY_DISMISSED_KEYS, emptySet()).orEmpty().toMutableSet()
                        dismissed += key
                        prefs.edit().putStringSet(KEY_DISMISSED_KEYS, dismissed).apply()
                    }
                }
            }
            context.contentResolver.delete(uri, null, null)
        }
    }

    private fun buildItems(movies: List<Movie>, shows: List<TvShow>): List<RecommendationItem> {
        val movieItems = movies.mapNotNull { movie ->
            val title = movie.title?.trim().orEmpty()
            if (movie.id <= 0 || title.isBlank()) null else RecommendationItem(
                key = "movie:${movie.id}",
                id = movie.id,
                type = "movie",
                title = title,
                overview = movie.overview?.trim().orEmpty(),
                posterPath = movie.posterPath,
                backdropPath = movie.backdropPath
            )
        }
        val tvItems = shows.mapNotNull { show ->
            val title = show.name?.trim().orEmpty()
            if (show.id <= 0 || title.isBlank()) null else RecommendationItem(
                key = "tv:${show.id}",
                id = show.id,
                type = "tv",
                title = title,
                overview = show.overview?.trim().orEmpty(),
                posterPath = show.posterPath,
                backdropPath = show.backdropPath
            )
        }
        return (movieItems.take(MAX_ITEMS / 2) + tvItems.take(MAX_ITEMS / 2))
            .distinctBy { it.key }
            .take(MAX_ITEMS)
    }

    private fun publishPreviewChannel(context: Context, items: List<RecommendationItem>) {
        runCatching {
            val channelId = ensureDefaultChannel(context)
            val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val dismissed = prefs.getStringSet(KEY_DISMISSED_KEYS, emptySet()).orEmpty()
            val activeItems = items.filterNot { it.key in dismissed }
            val existing = queryExistingPrograms(context, channelId)
            val activeKeys = activeItems.mapTo(mutableSetOf()) { it.key }

            activeItems.forEach { item ->
                val values = buildPreviewProgram(item, channelId).toContentValues()
                val existingId = existing[item.key]
                if (existingId == null) {
                    context.contentResolver.insert(TvContractCompat.PreviewPrograms.CONTENT_URI, values)
                } else {
                    val uri = ContentUris.withAppendedId(TvContractCompat.PreviewPrograms.CONTENT_URI, existingId)
                    context.contentResolver.update(uri, values, null, null)
                }
            }
            existing.filterKeys { it !in activeKeys }.values.forEach { id ->
                context.contentResolver.delete(
                    ContentUris.withAppendedId(TvContractCompat.PreviewPrograms.CONTENT_URI, id),
                    null,
                    null
                )
            }
        }
    }

    private fun ensureDefaultChannel(context: Context): Long {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val savedId = prefs.getLong(KEY_DEFAULT_CHANNEL_ID, -1L)
        if (savedId > 0L && channelExists(context, savedId)) return savedId

        val values = Channel.Builder()
            .setType(TvContractCompat.Channels.TYPE_PREVIEW)
            .setDisplayName(DEFAULT_CHANNEL_NAME)
            .setAppLinkIntentUri(Uri.parse("$APP_SCHEME://home"))
            .build()
            .toContentValues()
        val channelUri = requireNotNull(
            context.contentResolver.insert(TvContractCompat.Channels.CONTENT_URI, values)
        )
        val channelId = ContentUris.parseId(channelUri)
        storeChannelLogo(context, channelId)
        prefs.edit().putLong(KEY_DEFAULT_CHANNEL_ID, channelId).apply()
        return channelId
    }

    private fun channelExists(context: Context, channelId: Long): Boolean = runCatching {
        context.contentResolver.query(
            ContentUris.withAppendedId(TvContractCompat.Channels.CONTENT_URI, channelId),
            arrayOf(BaseColumns._ID),
            null,
            null,
            null
        )?.use { it.moveToFirst() } ?: false
    }.getOrDefault(false)

    private fun storeChannelLogo(context: Context, channelId: Long) {
        val source = BitmapFactory.decodeResource(context.resources, R.mipmap.ic_launcher11) ?: return
        val size = (80 * context.resources.displayMetrics.density).toInt().coerceAtLeast(80)
        val logo = Bitmap.createScaledBitmap(source, size, size, true)
        ChannelLogoUtils.storeChannelLogo(context, channelId, logo)
        if (logo !== source) logo.recycle()
    }

    private fun queryExistingPrograms(context: Context, channelId: Long): MutableMap<String, Long> {
        val result = mutableMapOf<String, Long>()
        context.contentResolver.query(
            TvContractCompat.PreviewPrograms.CONTENT_URI,
            arrayOf(BaseColumns._ID, "channel_id", "internal_provider_id"),
            null,
            null,
            null
        )?.use { cursor ->
            val idIndex = cursor.getColumnIndex(BaseColumns._ID)
            val channelIndex = cursor.getColumnIndex("channel_id")
            val keyIndex = cursor.getColumnIndex("internal_provider_id")
            while (cursor.moveToNext()) {
                if (idIndex >= 0 && channelIndex >= 0 && keyIndex >= 0 && cursor.getLong(channelIndex) == channelId) {
                    cursor.getString(keyIndex)?.takeIf { it.isNotBlank() }?.let {
                        result[it] = cursor.getLong(idIndex)
                    }
                }
            }
        }
        return result
    }

    private fun buildPreviewProgram(item: RecommendationItem, channelId: Long): PreviewProgram {
        val builder = PreviewProgram.Builder()
            .setChannelId(channelId)
            .setType(
                if (item.type == "movie") TvContractCompat.PreviewPrograms.TYPE_MOVIE
                else TvContractCompat.PreviewPrograms.TYPE_TV_SERIES
            )
            .setTitle(item.title)
            .setDescription(item.overview)
            .setPosterArtAspectRatio(TvContractCompat.PreviewPrograms.ASPECT_RATIO_MOVIE_POSTER)
            .setIntentUri(Uri.parse("$APP_SCHEME://${item.type}/${item.id}"))
            .setInternalProviderId(item.key)
        item.posterUrl?.let { builder.setPosterArtUri(Uri.parse(it)) }
        return builder.build()
    }

    private fun publishLegacyRecommendations(context: Context, items: List<RecommendationItem>) {
        publishNotificationRecommendations(context, items, fire = false)
    }

    private fun publishFireRecommendations(context: Context, items: List<RecommendationItem>) {
        publishNotificationRecommendations(context, items, fire = true)
    }

    private fun publishNotificationRecommendations(
        context: Context,
        items: List<RecommendationItem>,
        fire: Boolean
    ) {
        ensureNotificationChannel(context)
        val dismissed = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getStringSet(KEY_DISMISSED_KEYS, emptySet()).orEmpty()
        val activeItems = items.filterNot { it.key in dismissed }
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val previouslyPublished = prefs
            .getStringSet(KEY_PUBLISHED_NOTIFICATION_KEYS, emptySet())
            .orEmpty()
        val activeKeys = activeItems.mapTo(mutableSetOf()) { it.key }
        previouslyPublished.filterNot { it in activeKeys }.forEach { key ->
            runCatching {
                NotificationManagerCompat.from(context).cancel(notificationId(key))
            }
        }
        activeItems.forEach { postNotification(context, it, fire) }
        prefs.edit().putStringSet(KEY_PUBLISHED_NOTIFICATION_KEYS, activeKeys).apply()
    }

    private fun ensureNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(
                FIRE_CHANNEL_ID,
                FIRE_CHANNEL_NAME,
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply { description = FIRE_CHANNEL_DESCRIPTION }
        )
    }

    private fun postNotification(context: Context, item: RecommendationItem, fire: Boolean) {
        val pendingIntent = PendingIntent.getActivity(
            context,
            item.key.hashCode(),
            RecommendationEntryActivity.intent(context, item.type, item.id),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val poster = item.posterUrl?.let { downloadBitmap(it) }
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationCompat.Builder(context, FIRE_CHANNEL_ID)
        } else {
            NotificationCompat.Builder(context)
        }
            .setSmallIcon(R.mipmap.ic_launcher11)
            .setLargeIcon(poster)
            .setContentTitle(item.title)
            .setContentText(item.overview.ifBlank { "Discover this on KiduyuTV" })
            .setStyle(NotificationCompat.BigTextStyle().bigText(item.overview.ifBlank { item.title }))
            .setCategory(NotificationCompat.CATEGORY_RECOMMENDATION)
            .setLocalOnly(true)
            .setOngoing(true)
            .setAutoCancel(true)
            .setContentInfo(if (item.type == "movie") "Movie" else "TV Show")
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
        if (fire && Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            builder.setExtras(Bundle().apply {
                item.backdropUrl?.let { putString(Notification.EXTRA_BACKGROUND_IMAGE_URI, it) }
                putString("com.amazon.device.notification.extra.CONTENT_TYPE", item.type)
                putString("com.amazon.device.notification.extra.SOURCE_NAME", "KiduyuTV")
            })
        }
        runCatching {
            NotificationManagerCompat.from(context).notify(
                notificationId(item.key),
                builder.build()
            )
        }
    }

    private fun notificationId(key: String): Int =
        NOTIFICATION_ID_BASE + (key.hashCode() and 0x7fff)

    private fun downloadBitmap(url: String): Bitmap? = runCatching {
        val response = imageClient.newCall(Request.Builder().url(url).build()).execute()
        response.use { body ->
            if (!body.isSuccessful) return@runCatching null
            body.body?.bytes()?.let { BitmapFactory.decodeByteArray(it, 0, it.size) }
        }
    }.getOrNull()

    fun isTvLikeDevice(context: Context): Boolean =
        (context.resources.configuration.uiMode and Configuration.UI_MODE_TYPE_MASK) == Configuration.UI_MODE_TYPE_TELEVISION ||
            isAmazonDevice()

    private fun isAmazonDevice(): Boolean =
        Build.MANUFACTURER.equals("Amazon", ignoreCase = true) ||
            Build.BRAND.equals("Amazon", ignoreCase = true)
}
