# Android TV Recommendation Channels Implementation Guide

## For KiduyuTV App (Phone & TV Flavors)

This document provides a comprehensive, step-by-step guide for implementing Android TV Recommendation Channels in KiduyuTV. The app uses product flavors (`phone` and `tv`), so this guide covers implementation specific to each flavor while maximizing code sharing.

---

## Table of Contents

1. [Understanding the App Structure](#1-understanding-the-app-structure)
2. [Prerequisites and Dependencies](#2-prerequisites-and-dependencies)
3. [Step 1: Add TV Provider Dependencies](#3-step-1-add-tv-provider-dependencies)
4. [Step 2: Create Package Structure](#4-step-2-create-package-structure)
5. [Step 3: Implement Channel Manager](#5-step-3-implement-channel-manager)
6. [Step 4: Implement Program Manager](#6-step-4-implement-program-manager)
7. [Step 5: Create BroadcastReceivers](#7-step-5-create-broadcastreceivers)
8. [Step 6: Implement Deep Linking](#8-step-6-implement-deep-linking)
9. [Step 7: Configure AndroidManifest](#9-step-7-configure-androidmanifest)
10. [Step 8: Add Flavor-Specific Resources](#10-step-8-add-flavor-specific-resources)
11. [Step 9: Integrate with Existing Data Layer](#11-step-9-integrate-with-existing-data-layer)
12. [Step 10: Testing and Verification](#12-step-10-testing-and-verification)

---

## 1. Understanding the App Structure

### Current Build Configuration

The app uses two product flavors defined in `app/build.gradle`:

```groovy
flavorDimensions += "formfactor"

productFlavors {
    phone {
        dimension "formfactor"
        applicationIdSuffix ".phone"
        versionNameSuffix "-phone"
        resValue "string", "app_name", "KiduyuTV"
    }
    tv {
        dimension "formfactor"
        applicationIdSuffix ".tv"
        versionNameSuffix "-tv"
        resValue "string", "app_name", "KiduyuTV"
    }
}
```

### Target SDK Configuration

- **compileSdk**: 35
- **minSdk**: 24
- **targetSdk**: 35

### Channel Availability Note

Android TV channels (Preview Programs and Watch Next) are only available on **Android 8.0 (API level 26)** and higher. The implementation must check the SDK version before attempting channel operations.

---

## 2. Prerequisites and Dependencies

### Required Library

The `androidx.tvprovider:tvprovider` library is required for TV channels. This library should only be added to the **tv flavor** since phone apps do not support TV channels.

### Library Documentation

- [TV Provider Library](https://developer.android.com/reference/androidx/tvprovider/package-summary)
- [Channel Builder API](https://developer.android.com/reference/androidx/tvprovider/tvinput/Channel.Builder)
- [Preview Program Builder API](https://developer.android.com/reference/androidx/tvprovider/tvinput/PreviewProgram.Builder)

---

## 3. Step 1: Add TV Provider Dependencies

### 3.1 Update app/build.gradle

Add the tvprovider dependency only for the TV flavor. Phone apps do not support TV channels.

```groovy
dependencies {
    // ... existing dependencies ...

    // ─────────────────────────────────────────────
    // TV Channels (TV flavor only)
    // ─────────────────────────────────────────────
    tvImplementation 'androidx.tvprovider:tvprovider:1.0.0'
}
```

### 3.2 Add Permissions

Add these permissions to the TV flavor's `AndroidManifest.xml`. Since we're using flavor-specific source sets, create a separate manifest for the TV flavor.

Create `app/src/tv/AndroidManifest.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:amazon="http://schemas.amazon.com/apk/res/android">

    <!-- Existing permissions from main manifest -->
    
    <!-- TV Channel Permissions -->
    <uses-permission android:name="com.android.providers.tv.permission.WRITE_EPG_DATA" />
    <uses-permission android:name="com.android.providers.tv.permission.READ_EPG_DATA" />
    <uses-permission android:name="com.android.providers.tv.permission.READ_WATCH_NEXT" />

    <!-- Leanback support for TV -->
    <uses-feature
        android:name="android.software.leanback"
        android:required="true" />

    <application
        android:name=".KiduyuTVApplication"
        android:allowBackup="true"
        android:icon="@mipmap/ic_launcher"
        android:banner="@drawable/banner"
        android:label="@string/app_name"
        android:largeHeap="true"
        android:supportsRtl="true"
        android:theme="@style/Theme.KiduyuTV">

        <!-- TV-specific activities and receivers -->
        
    </application>
</manifest>
```

---

## 4. Step 2: Create Package Structure

Create the following package structure for TV channel implementation:

```
app/src/main/java/com/kiduyuk/klausk/kiduyutv/
├── data/
│   └── repository/
│       └── TmdbRepository.kt          # Existing - used for fetching content
├── tv/                                # TV channel implementation
│   ├── channel/
│   │   ├── TvChannelManager.kt        # Creates/manages channels
│   │   └── TvProgramManager.kt        # Creates/manages programs
│   ├── receiver/
│   │   ├── InitializeProgramsReceiver.kt
│   │   └── WatchNextRemovedReceiver.kt
│   └── sync/
│       └── ChannelSyncManager.kt      # Syncs watch history to channels
├── util/
│   └── TvChannelHelper.kt             # Utility functions
└── KiduyuTVApplication.kt             # Add initialization code
```

### Create Source Directory

Create the directory structure for TV-specific code:

```bash
mkdir -p app/src/main/java/com/kiduyuk/klausk/kiduyutv/tv/{channel,receiver,sync}
mkdir -p app/src/main/java/com/kiduyuk/klausk/kiduyutv/util
```

---

## 5. Step 3: Implement Channel Manager

Create `TvChannelManager.kt` to handle channel creation and management.

```kotlin
package com.kiduyuk.klausk.kiduyutv.tv.channel

import android.content.ContentValues
import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import android.os.Build
import android.support.tv.tvinput.TvContractCompat
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Manages TV channels for KiduyuTV.
 * 
 * Channels appear as rows on the Android TV home screen.
 * Each channel can contain multiple programs (movie/TV show cards).
 * 
 * Channel Implementation Guide:
 * 1. Use Channel.Builder to create channel metadata
 * 2. Insert into TV provider using ContentResolver
 * 3. Store the returned channel ID for program management
 * 4. Call requestChannelBrowsable() to make channel visible
 * 
 * @see <a href="https://developer.android.com/training/tv/discovery/recommendations-channel">
 *      Creating a Recommendations Channel</a>
 */
class TvChannelManager(private val context: Context) {

    companion object {
        private const val TAG = "TvChannelManager"
        
        // Channel identifiers (used internally and stored in SharedPreferences)
        const val CHANNEL_MOVIES = "kiduyutv_movies"
        const val CHANNEL_TV_SHOWS = "kiduyutv_tvshows"
        const val CHANNEL_CONTINUE = "kiduyutv_continue"
        const val CHANNEL_NEW = "kiduyutv_new_releases"
        
        // SharedPreferences file name and key
        private const val PREFS_NAME = "tv_channel_prefs"
        private const val KEY_CHANNEL_ID = "provider_channel_id"
    }
    
    private val prefs: SharedPreferences = 
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /**
     * Check if device supports TV channels (API 26+).
     * TV channels are only available on Android 8.0 (Oreo) and higher.
     */
    fun isChannelSupported(): Boolean {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
    }

    /**
     * Create a new TV channel.
     * 
     * Implementation steps:
     * 1. Build channel metadata using Channel.Builder
     * 2. Insert into TV provider
     * 3. Extract and store channel ID
     * 4. Request browsable status
     * 
     * @param channelId Internal identifier for this channel
     * @param displayName Name shown on TV home screen
     * @param description Brief description shown when channel is focused
     * @param logoUri Optional URI to channel logo (80dp x 80dp, will be circular)
     * @param appLinkUri Intent URI when user clicks channel logo
     * @return Provider channel ID, or -1 if creation failed
     */
    suspend fun createChannel(
        channelId: String,
        displayName: String,
        description: String,
        logoUri: Uri? = null,
        appLinkUri: Uri? = null
    ): Long = withContext(Dispatchers.IO) {
        if (!isChannelSupported()) {
            Log.e(TAG, "TV channels not supported on this device")
            return@withContext -1L
        }

        try {
            // Step 1: Build channel metadata
            val builder = android.support.tv.tvinput.Channel.Builder()
            
            // Set channel type - TYPE_PREVIEW for recommendation channels
            builder.setType(TvContractCompat.Channels.TYPE_PREVIEW)
            
            // Set display information
            builder.setDisplayName(displayName)
            builder.setDescription(description)
            
            // Set logo if provided (recommended: 80dp x 80dp, will be circular)
            logoUri?.let { builder.setLogo(it) }
            
            // Set app link intent - what opens when user clicks channel logo
            appLinkUri?.let { builder.setAppLinkIntentUri(it) }
                ?: builder.setAppLinkIntentUri(Uri.parse("kiduyutv://channel/$channelId"))
            
            // Set internal provider ID for tracking
            builder.setInternalProviderId(channelId)
            
            // Step 2: Insert into TV provider
            val channelUri = context.contentResolver.insert(
                TvContractCompat.Channels.CONTENT_URI,
                builder.build().toContentValues()
            )
            
            if (channelUri != null) {
                // Step 3: Extract channel ID from URI
                val providerChannelId = android.content.ContentUris.parseId(channelUri)
                
                // Store mapping for later retrieval
                storeChannelMapping(channelId, providerChannelId)
                
                Log.d(TAG, "Created channel '$displayName' with ID: $providerChannelId")
                
                // Step 4: Make channel browsable (visible on home screen)
                requestChannelBrowsable(providerChannelId)
                
                providerChannelId
            } else {
                Log.e(TAG, "Failed to insert channel")
                -1L
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "Security exception - check TV provider permissions", e)
            -1L
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create channel", e)
            -1L
        }
    }

    /**
     * Request the system to make a channel browsable.
     * This shows the channel on the TV home screen.
     * 
     * @param channelId Provider channel ID
     */
    private fun requestChannelBrowsable(channelId: Long) {
        try {
            TvContractCompat.requestChannelBrowsable(context, channelId)
            Log.d(TAG, "Requested channel browsable for ID: $channelId")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to request channel browsable", e)
        }
    }

    /**
     * Store channel mapping between internal ID and provider ID.
     */
    private fun storeChannelMapping(channelId: String, providerId: Long) {
        prefs.edit().putLong(channelId, providerId).apply()
    }

    /**
     * Get provider channel ID from internal channel ID.
     * 
     * @param channelId Internal channel identifier
     * @return Provider channel ID, or -1 if not found
     */
    fun getProviderChannelId(channelId: String): Long {
        return prefs.getLong(channelId, -1L)
    }

    /**
     * Check if a channel exists.
     */
    fun channelExists(channelId: String): Boolean {
        return getProviderChannelId(channelId) != -1L
    }

    /**
     * Update channel metadata.
     * 
     * @param channelId Internal channel identifier
     * @param displayName New display name (null to keep existing)
     * @param description New description (null to keep existing)
     * @param logoUri New logo URI (null to keep existing)
     */
    suspend fun updateChannel(
        channelId: String,
        displayName: String? = null,
        description: String? = null,
        logoUri: Uri? = null
    ): Boolean = withContext(Dispatchers.IO) {
        val providerChannelId = getProviderChannelId(channelId)
        if (providerChannelId == -1L) {
            Log.e(TAG, "Channel not found: $channelId")
            return@withContext false
        }

        try {
            val builder = android.support.tv.tvinput.Channel.Builder()
            
            displayName?.let { builder.setDisplayName(it) }
            description?.let { builder.setDescription(it) }
            logoUri?.let { builder.setLogo(it) }
            
            val rows = context.contentResolver.update(
                TvContractCompat.buildChannelUri(providerChannelId),
                builder.build().toContentValues(),
                null, null
            )
            
            rows > 0
        } catch (e: Exception) {
            Log.e(TAG, "Failed to update channel", e)
            false
        }
    }

    /**
     * Delete a channel and all its programs.
     * 
     * @param channelId Internal channel identifier
     */
    suspend fun deleteChannel(channelId: String): Boolean = withContext(Dispatchers.IO) {
        val providerChannelId = getProviderChannelId(channelId)
        if (providerChannelId == -1L) {
            return@withContext false
        }

        try {
            // First delete all programs in the channel
            deleteAllPrograms(providerChannelId)
            
            // Then delete the channel
            val rows = context.contentResolver.delete(
                TvContractCompat.buildChannelUri(providerChannelId),
                null, null
            )
            
            // Remove from preferences
            prefs.edit().remove(channelId).apply()
            
            rows > 0
        } catch (e: Exception) {
            Log.e(TAG, "Failed to delete channel", e)
            false
        }
    }

    /**
     * Delete all programs in a channel.
     * Note: WHERE clauses are not supported, so we delete by channel ID manually.
     */
    private suspend fun deleteAllPrograms(channelId: Long) = withContext(Dispatchers.IO) {
        try {
            // Query programs in this channel
            val cursor = context.contentResolver.query(
                TvContractCompat.PreviewPrograms.CONTENT_URI,
                arrayOf(TvContractCompat.PreviewPrograms._ID),
                null, null, null
            )
            
            cursor?.use {
                while (it.moveToNext()) {
                    val programId = it.getLong(0)
                    try {
                        context.contentResolver.delete(
                            TvContractCompat.buildPreviewProgramUri(programId),
                            null, null
                        )
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to delete program $programId", e)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to delete channel programs", e)
        }
    }

    /**
     * Get all channels for this app.
     */
    suspend fun getAllChannels(): List<ChannelInfo> = withContext(Dispatchers.IO) {
        val channels = mutableListOf<ChannelInfo>()
        
        // Get all known channel IDs from prefs
        val channelIds = listOf(CHANNEL_MOVIES, CHANNEL_TV_SHOWS, CHANNEL_CONTINUE, CHANNEL_NEW)
        
        channelIds.forEach { channelId ->
            val providerId = getProviderChannelId(channelId)
            if (providerId != -1L) {
                channels.add(ChannelInfo(channelId, providerId))
            }
        }
        
        channels
    }

    /**
     * Remove all channels for this app.
     */
    suspend fun removeAllChannels(): Boolean = withContext(Dispatchers.IO) {
        val channelIds = listOf(CHANNEL_MOVIES, CHANNEL_TV_SHOWS, CHANNEL_CONTINUE, CHANNEL_NEW)
        var allDeleted = true
        
        channelIds.forEach { channelId ->
            if (!deleteChannel(channelId)) {
                allDeleted = false
            }
        }
        
        allDeleted
    }

    data class ChannelInfo(
        val internalId: String,
        val providerId: Long
    )
}
```

---

## 6. Step 4: Implement Program Manager

Create `TvProgramManager.kt` to handle program creation within channels.

```kotlin
package com.kiduyuk.klausk.kiduyutv.tv.channel

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.support.tv.tvinput.TvContractCompat
import android.util.Log
import com.kiduyuk.klausk.kiduyutv.data.model.Movie
import com.kiduyuk.klausk.kiduyutv.data.model.TvShow
import com.kiduyuk.klausk.kiduyutv.data.repository.TmdbRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Manages preview programs within TV channels.
 * 
 * Programs are the individual cards that appear in a channel row.
 * Each program represents a movie, TV show, or clip.
 * 
 * Program Types:
 * - TYPE_MOVIE: Full-length movies
 * - TYPE_TV_SHOW: TV series
 * - TYPE_CLIP: Short video clips
 * - TYPE_CHANNEL: Live channel preview
 * 
 * @see <a href="https://developer.android.com/training/tv/discovery/video-programs">
 *      Video Program Attributes</a>
 */
class TvProgramManager(private val context: Context) {

    companion object {
        private const val TAG = "TvProgramManager"
        
        // Program type constants
        const val TYPE_MOVIE = TvContractCompat.PreviewPrograms.TYPE_MOVIE
        const val TYPE_TV_SHOW = TvContractCompat.PreviewPrograms.TYPE_TV_SHOW
        const val TYPE_CLIP = TvContractCompat.PreviewPrograms.TYPE_CLIP
        
        // Maximum programs per channel
        const val MAX_PROGRAMS_PER_CHANNEL = 50
    }

    private val repository = TmdbRepository(context)

    /**
     * Add a movie as a program in a channel.
     * 
     * Implementation steps:
     * 1. Build program metadata with PreviewProgram.Builder
     * 2. Set channel ID, type, title, description
     * 3. Set poster art URI (will be fetched from TMDB)
     * 4. Set intent URI for deep linking when clicked
     * 5. Insert into TV provider
     * 
     * @param channelProviderId Provider channel ID
     * @param movie Movie to add
     * @return Program ID if successful, null otherwise
     */
    suspend fun addMovieProgram(
        channelProviderId: Long,
        movie: Movie
    ): Long? = withContext(Dispatchers.IO) {
        if (!isChannelSupported()) return@withContext null

        try {
            // Build program metadata
            val builder = android.support.tv.tvinput.PreviewProgram.Builder()
            
            // Set channel this program belongs to
            builder.setChannelId(channelProviderId)
            
            // Set program type
            builder.setType(TYPE_MOVIE)
            
            // Set basic metadata
            builder.setTitle(movie.title)
            builder.setDescription(movie.overview.take(500)) // Max 500 chars
            
            // Set poster art - use TMDB image URL
            val posterUri = buildPosterUri(movie.posterPath)
            builder.setPosterArtUri(posterUri)
            
            // Set internal ID for tracking (used for updates/deletes)
            builder.setInternalProviderId("movie_${movie.id}")
            
            // Set genres if available
            if (movie.genres.isNotEmpty()) {
                builder.setGenre(movie.genres.first().name)
            }
            
            // Set duration (convert minutes to milliseconds)
            movie.runtime?.let { duration ->
                builder.setDuration(duration * 60 * 1000L)
            }
            
            // Set intent URI for deep linking
            // When user clicks this program, app opens with this URI
            val intentUri = Uri.parse("kiduyutv://movie/${movie.id}")
            builder.setIntentUri(intentUri)
            builder.setAppLinkIntentUri(intentUri)
            
            // Set poster aspect ratio (use POSTER_RATIO_3_1 for movie posters)
            builder.setPosterAspectRatio(
                TvContractCompat.PreviewPrograms.ASPECT_RATIO_POSTER_3_1
            )
            
            // Set ranking (higher numbers appear first)
            builder.setRank(System.currentTimeMillis() % 1000)
            
            // Insert into TV provider
            val programUri = context.contentResolver.insert(
                TvContractCompat.PreviewPrograms.CONTENT_URI,
                builder.build().toContentValues()
            )
            
            if (programUri != null) {
                val programId = android.content.ContentUris.parseId(programUri)
                Log.d(TAG, "Added movie program: ${movie.title} (ID: $programId)")
                programId
            } else {
                Log.e(TAG, "Failed to insert movie program")
                null
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "Security exception adding movie program", e)
            null
        } catch (e: Exception) {
            Log.e(TAG, "Failed to add movie program", e)
            null
        }
    }

    /**
     * Add a TV show as a program in a channel.
     */
    suspend fun addTvShowProgram(
        channelProviderId: Long,
        tvShow: TvShow,
        seasonNumber: Int = 1,
        episodeNumber: Int = 1
    ): Long? = withContext(Dispatchers.IO) {
        if (!isChannelSupported()) return@withContext null

        try {
            val builder = android.support.tv.tvinput.PreviewProgram.Builder()
            
            builder.setChannelId(channelProviderId)
            builder.setType(TYPE_TV_SHOW)
            
            builder.setTitle(tvShow.name)
            builder.setDescription(tvShow.overview.take(500))
            
            val posterUri = buildPosterUri(tvShow.posterPath)
            builder.setPosterArtUri(posterUri)
            
            // Set episode info
            builder.setSeasonDisplayNumber(seasonNumber)
            builder.setEpisodeDisplayNumber(episodeNumber)
            
            // Set internal ID including episode info
            builder.setInternalProviderId("tvshow_${tvShow.id}_s${seasonNumber}e${episodeNumber}")
            
            // Set genres
            if (tvShow.genres.isNotEmpty()) {
                builder.setGenre(tvShow.genres.first().name)
            }
            
            // Deep link with episode info
            val intentUri = Uri.parse(
                "kiduyutv://tvshow/${tvShow.id}?season=$seasonNumber&episode=$episodeNumber"
            )
            builder.setIntentUri(intentUri)
            builder.setAppLinkIntentUri(intentUri)
            
            // Set aspect ratio for TV show posters
            builder.setPosterAspectRatio(
                TvContractCompat.PreviewPrograms.ASPECT_RATIO_POSTER_3_1
            )
            
            builder.setRank(System.currentTimeMillis() % 1000)
            
            val programUri = context.contentResolver.insert(
                TvContractCompat.PreviewPrograms.CONTENT_URI,
                builder.build().toContentValues()
            )
            
            if (programUri != null) {
                android.content.ContentUris.parseId(programUri)
            } else {
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to add TV show program", e)
            null
        }
    }

    /**
     * Add a "Continue Watching" program with playback position.
     * 
     * This creates a program that shows the movie/TV show with resume position.
     * The Watch Next type will be set to CONTINUE.
     */
    suspend fun addContinueWatchingProgram(
        channelProviderId: Long,
        title: String,
        description: String,
        posterUrl: String,
        contentId: Long,
        contentType: String, // "movie" or "tvshow"
        currentPositionMs: Long,
        totalDurationMs: Long
    ): Long? = withContext(Dispatchers.IO) {
        if (!isChannelSupported()) return@withContext null

        try {
            val builder = android.support.tv.tvinput.PreviewProgram.Builder()
            
            // Determine program type
            val type = if (contentType == "movie") TYPE_MOVIE else TYPE_TV_SHOW
            
            builder.setChannelId(channelProviderId)
            builder.setType(type)
            
            builder.setTitle(title)
            builder.setDescription(description)
            builder.setPosterArtUri(Uri.parse(posterUrl))
            
            // Set internal ID for tracking
            builder.setInternalProviderId("${contentType}_${contentId}_continue")
            
            // Set watch progress
            builder.setLastPlaybackPositionMillis(currentPositionMs)
            builder.setDuration(totalDurationMs)
            
            // Set Watch Next type to CONTINUE
            builder.setWatchNextMode(TvContractCompat.PreviewPrograms.WATCH_NEXT_TYPE_CONTINUE)
            
            // Deep link with position for resume
            val intentUri = Uri.parse(
                "kiduyutv://$contentType/$contentId?position=$currentPositionMs"
            )
            builder.setIntentUri(intentUri)
            builder.setAppLinkIntentUri(intentUri)
            
            builder.setPosterAspectRatio(
                TvContractCompat.PreviewPrograms.ASPECT_RATIO_POSTER_3_1
            )
            
            builder.setRank(System.currentTimeMillis() % 1000)
            
            val programUri = context.contentResolver.insert(
                TvContractCompat.PreviewPrograms.CONTENT_URI,
                builder.build().toContentValues()
            )
            
            if (programUri != null) {
                android.content.ContentUris.parseId(programUri)
            } else {
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to add continue watching program", e)
            null
        }
    }

    /**
     * Update a program's watch progress.
     * 
     * @param internalProviderId Internal program ID (e.g., "movie_123")
     * @param positionMs New playback position in milliseconds
     */
    suspend fun updateWatchProgress(
        internalProviderId: String,
        positionMs: Long
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            // Note: WHERE clauses are not supported by TV provider
            // Query first to get the program ID, then update
            
            val values = ContentValues().apply {
                put(TvContractCompat.PreviewPrograms.COLUMN_LAST_PLAYBACK_POSITION_MILLIS, positionMs)
                put(TvContractCompat.PreviewPrograms.COLUMN_WATCH_NEXT_MODE,
                    TvContractCompat.PreviewPrograms.WATCH_NEXT_TYPE_CONTINUE)
            }
            
            // Get program ID
            val programId = getProgramIdByInternalId(internalProviderId)
            if (programId != null) {
                val rows = context.contentResolver.update(
                    TvContractCompat.buildPreviewProgramUri(programId),
                    values, null, null
                )
                rows > 0
            } else {
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to update watch progress", e)
            false
        }
    }

    /**
     * Delete a program by its internal provider ID.
     */
    suspend fun deleteProgram(internalProviderId: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val programId = getProgramIdByInternalId(internalProviderId)
            if (programId != null) {
                val rows = context.contentResolver.delete(
                    TvContractCompat.buildPreviewProgramUri(programId),
                    null, null
                )
                rows > 0
            } else {
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to delete program", e)
            false
        }
    }

    /**
     * Get program ID by internal provider ID.
     */
    private suspend fun getProgramIdByInternalId(internalProviderId: String): Long? = 
        withContext(Dispatchers.IO) {
            try {
                val cursor = context.contentResolver.query(
                    TvContractCompat.PreviewPrograms.CONTENT_URI,
                    arrayOf(TvContractCompat.PreviewPrograms._ID),
                    "${TvContractCompat.PreviewPrograms.COLUMN_INTERNAL_PROVIDER_ID} = ?",
                    arrayOf(internalProviderId),
                    null
                )
                
                cursor?.use {
                    if (it.moveToFirst()) {
                        return@withContext it.getLong(0)
                    }
                }
                null
            } catch (e: Exception) {
                Log.e(TAG, "Failed to get program ID", e)
                null
            }
        }

    /**
     * Check if device supports TV channels.
     */
    private fun isChannelSupported(): Boolean {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
    }

    /**
     * Build poster URI from TMDB path.
     */
    private fun buildPosterUri(posterPath: String?): Uri {
        return if (posterPath != null && posterPath.isNotEmpty()) {
            Uri.parse("https://image.tmdb.org/t/p/w500$posterPath")
        } else {
            Uri.EMPTY
        }
    }

    /**
     * Populate Movies channel with trending movies from TMDB.
     */
    suspend fun populateMoviesChannel(channelProviderId: Long): Int = withContext(Dispatchers.IO) {
        try {
            val response = repository.getTrendingMovies(1)
            var count = 0
            
            response.results.take(MAX_PROGRAMS_PER_CHANNEL).forEach { movie ->
                if (addMovieProgram(channelProviderId, movie) != null) {
                    count++
                }
            }
            
            Log.d(TAG, "Populated Movies channel with $count programs")
            count
        } catch (e: Exception) {
            Log.e(TAG, "Failed to populate Movies channel", e)
            0
        }
    }

    /**
     * Populate TV Shows channel with trending shows from TMDB.
     */
    suspend fun populateTvShowsChannel(channelProviderId: Long): Int = withContext(Dispatchers.IO) {
        try {
            val response = repository.getTrendingTv(1)
            var count = 0
            
            response.results.take(MAX_PROGRAMS_PER_CHANNEL).forEach { tvShow ->
                if (addTvShowProgram(channelProviderId, tvShow) != null) {
                    count++
                }
            }
            
            Log.d(TAG, "Populated TV Shows channel with $count programs")
            count
        } catch (e: Exception) {
            Log.e(TAG, "Failed to populate TV Shows channel", e)
            0
        }
    }

    /**
     * Clear all programs from a channel and repopulate.
     */
    suspend fun refreshChannel(
        channelProviderId: Long,
        channelType: String
    ): Int = withContext(Dispatchers.IO) {
        // Delete all existing programs
        deleteAllProgramsInChannel(channelProviderId)
        
        // Repopulate based on channel type
        when (channelType) {
            TvChannelManager.CHANNEL_MOVIES -> populateMoviesChannel(channelProviderId)
            TvChannelManager.CHANNEL_TV_SHOWS -> populateTvShowsChannel(channelProviderId)
            else -> 0
        }
    }

    /**
     * Delete all programs in a channel.
     * Note: This queries and deletes each program individually since WHERE clauses
     * are not supported by the TV provider.
     */
    private suspend fun deleteAllProgramsInChannel(channelId: Long) = withContext(Dispatchers.IO) {
        try {
            val cursor = context.contentResolver.query(
                TvContractCompat.PreviewPrograms.CONTENT_URI,
                arrayOf(TvContractCompat.PreviewPrograms._ID),
                null, null, null
            )
            
            cursor?.use {
                while (it.moveToNext()) {
                    try {
                        val programId = it.getLong(0)
                        context.contentResolver.delete(
                            TvContractCompat.buildPreviewProgramUri(programId),
                            null, null
                        )
                    } catch (e: Exception) {
                        // Continue with next program
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to delete programs", e)
        }
    }
}
```

---

## 7. Step 5: Create BroadcastReceivers

### 7.1 InitializeProgramsReceiver

This receiver is triggered by the system when the user wants to initialize your channels.

Create `InitializeProgramsReceiver.kt`:

```kotlin
package com.kiduyuk.klausk.kiduyutv.tv.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.kiduyuk.klausk.kiduyutv.tv.channel.TvChannelManager
import com.kiduyuk.klausk.kiduyutv.tv.channel.TvProgramManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * BroadcastReceiver that handles INITIALIZE_PROGRAMS intent.
 * 
 * This receiver is triggered by the system in these scenarios:
 * 1. When your app is first installed
 * 2. When the user resets TV data
 * 3. When the user manually requests channel initialization
 * 
 * Implementation note:
 * - Show popular/trending content for unauthenticated users
 * - Show personalized content for authenticated users
 * 
 * @see <a href="https://developer.android.com/training/tv/discovery/recommendations-channel#initialize-programs">
 *      Initialize Programs</a>
 */
class InitializeProgramsReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "InitializePrograms"
        const val ACTION = "android.media.tv.action.INITIALIZE_PROGRAMS"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION) {
            return
        }

        Log.d(TAG, "Received INITIALIZE_PROGRAMS intent")

        // Run initialization in background to avoid ANR
        CoroutineScope(Dispatchers.IO).launch {
            initializeAllChannels(context)
        }
    }

    /**
     * Initialize all TV channels with programs.
     * 
     * This creates the following channels:
     * 1. Movies - Trending movies from TMDB
     * 2. TV Shows - Trending TV shows from TMDB
     * 3. Continue Watching - Content from user's watch history
     */
    private suspend fun initializeAllChannels(context: Context) {
        val channelManager = TvChannelManager(context)
        val programManager = TvProgramManager(context)

        try {
            Log.d(TAG, "Starting channel initialization")

            // Create and populate Movies channel
            if (!channelManager.channelExists(TvChannelManager.CHANNEL_MOVIES)) {
                val moviesChannelId = channelManager.createChannel(
                    channelId = TvChannelManager.CHANNEL_MOVIES,
                    displayName = "KiduyuTV Movies",
                    description = "Trending and popular movies",
                    appLinkUri = null // Will use default
                )

                if (moviesChannelId != -1L) {
                    programManager.populateMoviesChannel(moviesChannelId)
                }
            }

            // Create and populate TV Shows channel
            if (!channelManager.channelExists(TvChannelManager.CHANNEL_TV_SHOWS)) {
                val tvShowsChannelId = channelManager.createChannel(
                    channelId = TvChannelManager.CHANNEL_TV_SHOWS,
                    displayName = "KiduyuTV TV Shows",
                    description = "Popular TV series and shows",
                    appLinkUri = null
                )

                if (tvShowsChannelId != -1L) {
                    programManager.populateTvShowsChannel(tvShowsChannelId)
                }
            }

            // Create Continue Watching channel
            if (!channelManager.channelExists(TvChannelManager.CHANNEL_CONTINUE)) {
                channelManager.createChannel(
                    channelId = TvChannelManager.CHANNEL_CONTINUE,
                    displayName = "Continue Watching",
                    description = "Resume your movies and shows",
                    appLinkUri = null
                )
            }

            Log.d(TAG, "Channel initialization completed successfully")

        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize channels", e)
        }
    }
}
```

### 7.2 WatchNextRemovedReceiver

This receiver handles when users remove Watch Next programs.

Create `WatchNextRemovedReceiver.kt`:

```kotlin
package com.kiduyuk.klausk.kiduyutv.tv.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * BroadcastReceiver that handles Watch Next program removal.
 * 
 * IMPORTANT: When a user removes a program from Watch Next,
 * do NOT reinsert it. The user has explicitly chosen to remove it,
 * and respecting this choice is critical for user trust.
 */
class WatchNextRemovedReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "WatchNextRemoved"
        const val ACTION = "android.media.tv.action.WATCH_NEXT_PROGRAM_BROWSABLE_DISABLED"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION) {
            return
        }

        Log.d(TAG, "Watch Next program was removed by user")

        // Log the removal for analytics (but don't act on it)
        val extras = intent.extras
        if (extras != null) {
            for (key in extras.keySet()) {
                Log.d(TAG, "Extra: $key = ${extras.get(key)}")
            }
        }

        // Important: Do NOT reinsert the program
        // The user explicitly removed it - respect their choice
    }
}
```

### 7.3 Register Receivers in TV Manifest

Add to `app/src/tv/AndroidManifest.xml`:

```xml
<!-- Initialize Programs Receiver -->
<receiver
    android:name=".tv.receiver.InitializeProgramsReceiver"
    android:exported="true">
    <intent-filter>
        <action android:name="android.media.tv.action.INITIALIZE_PROGRAMS" />
        <category android:name="android.intent.category.DEFAULT" />
    </intent-filter>
</receiver>

<!-- Watch Next Removal Receiver -->
<receiver
    android:name=".tv.receiver.WatchNextRemovedReceiver"
    android:enabled="true"
    android:exported="true">
    <intent-filter>
        <action android:name="android.media.tv.action.WATCH_NEXT_PROGRAM_BROWSABLE_DISABLED" />
    </intent-filter>
</receiver>
```

---

## 8. Step 6: Implement Deep Linking

### 8.1 Deep Link URI Scheme

Define the deep link URI scheme for TV channel navigation:

```kotlin
// In a constants file or TvChannelManager companion object
object TvChannelUris {
    const val SCHEME = "kiduyutv"
    
    // Movie deep link: kiduyutv://movie/12345?position=60000
    fun movie(movieId: Long, position: Long = 0): String {
        return "$SCHEME://movie/$movieId${if (position > 0) "?position=$position" else ""}"
    }
    
    // TV Show deep link: kiduyutv://tvshow/67890?season=2&episode=5
    fun tvShow(tvId: Long, season: Int = 1, episode: Int = 1): String {
        return "$SCHEME://tvshow/$tvId?season=$season&episode=$episode"
    }
    
    // Channel deep link: kiduyutv://channel/movies
    fun channel(channelId: String): String {
        return "$SCHEME://channel/$channelId"
    }
}
```

### 8.2 Handle Deep Links in Main Activity

Update your main activity to handle deep links from TV channels:

```kotlin
package com.kiduyuk.klausk.kiduyutv.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.kiduyuk.klausk.kiduyutv.ui.theme.KiduyTVTheme
import com.kiduyuk.klausk.kiduyutv.util.TvChannelUris

/**
 * Main Activity for KiduyuTV.
 * 
 * Handles deep links from:
 * - TV launcher (LEANBACK_LAUNCHER)
 * - TV channels (kiduyutv:// URI scheme)
 * - Watch Next programs
 */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Handle incoming intents (deep links from TV channels)
        handleIntent(intent)
        
        setContent {
            KiduyTVTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    // Your existing composable content
                    KiduyuTVApp(
                        onMovieClick = { movieId ->
                            navigateToMovie(movieId)
                        },
                        onTvShowClick = { tvId ->
                            navigateToTvShow(tvId)
                        }
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        intent?.let { handleIntent(it) }
    }

    /**
     * Handle incoming intents from TV channels and deep links.
     * 
     * Expected URI formats:
     * - kiduyutv://movie/12345
     * - kiduyutv://movie/12345?position=60000
     * - kiduyutv://tvshow/67890
     * - kiduyutv://tvshow/67890?season=2&episode=5
     * - kiduyutv://channel/movies
     */
    private fun handleIntent(intent: Intent) {
        val data = intent.data ?: return
        
        when (data.scheme) {
            "kiduyutv" -> handleKiduyTvUri(data)
            "https", "http" -> handleHttpUri(data)
        }
        
        // Handle extras if URI is not provided
        handleIntentExtras(intent.extras)
    }

    /**
     * Handle kiduyutv:// scheme URIs.
     */
    private fun handleKiduyTvUri(uri: Uri) {
        when (uri.host) {
            "movie" -> {
                val movieId = uri.pathSegments.firstOrNull()?.toLongOrNull()
                val position = uri.getQueryParameter("position")?.toLongOrNull() ?: 0L
                
                movieId?.let { 
                    navigateToMovieWithPosition(it, position)
                }
            }
            "tvshow" -> {
                val tvId = uri.pathSegments.firstOrNull()?.toLongOrNull()
                val season = uri.getQueryParameter("season")?.toIntOrNull() ?: 1
                val episode = uri.getQueryParameter("episode")?.toIntOrNull() ?: 1
                
                tvId?.let {
                    navigateToTvShowWithEpisode(it, season, episode)
                }
            }
            "channel" -> {
                val channelId = uri.pathSegments.firstOrNull()
                channelId?.let {
                    navigateToChannel(it)
                }
            }
        }
    }

    /**
     * Handle HTTPS/HTTP deep links.
     * Example: https://kiduyutv.app/movie/12345
     */
    private fun handleHttpUri(uri: Uri) {
        if (uri.host?.contains("kiduyutv") == true) {
            when {
                uri.pathSegments.getOrNull(0) == "movie" -> {
                    val movieId = uri.lastPathSegment?.toLongOrNull()
                    movieId?.let { navigateToMovie(it) }
                }
                uri.pathSegments.getOrNull(0) == "tvshow" -> {
                    val tvId = uri.lastPathSegment?.toLongOrNull()
                    tvId?.let { navigateToTvShow(it) }
                }
            }
        }
    }

    /**
     * Handle intent extras (fallback for non-URI intents).
     */
    private fun handleIntentExtras(extras: Bundle?) {
        extras ?: return
        
        // Handle movie extras
        val movieId = extras.getLong("movie_id", -1)
        if (movieId != -1L) {
            val position = extras.getLong("playback_position", 0L)
            navigateToMovieWithPosition(movieId, position)
            return
        }
        
        // Handle TV show extras
        val tvId = extras.getLong("tv_id", -1)
        if (tvId != -1L) {
            val season = extras.getInt("season", 1)
            val episode = extras.getInt("episode", 1)
            navigateToTvShowWithEpisode(tvId, season, episode)
        }
    }

    /**
     * Navigate to movie detail screen.
     */
    private fun navigateToMovie(movieId: Long) {
        // Navigate to movie detail composable/screen
        // This depends on your navigation implementation
        navigateToMovieWithPosition(movieId, 0L)
    }

    /**
     * Navigate to movie detail with playback position.
     */
    private fun navigateToMovieWithPosition(movieId: Long, position: Long) {
        // Store position in ViewModel or pass through navigation
        // Navigate to movie detail screen
        android.util.Log.d("DeepLink", "Navigate to movie $movieId at position $position")
        
        // Example: Set the playback position and navigate
        // viewModel.setPlaybackPosition(position)
        // navController.navigate("movie/$movieId")
    }

    /**
     * Navigate to TV show detail screen.
     */
    private fun navigateToTvShow(tvId: Long) {
        navigateToTvShowWithEpisode(tvId, 1, 1)
    }

    /**
     * Navigate to TV show detail with specific episode.
     */
    private fun navigateToTvShowWithEpisode(tvId: Long, season: Int, episode: Int) {
        android.util.Log.d("DeepLink", "Navigate to TV show $tvId S${season}E${episode}")
        
        // Navigate to TV show detail with episode selected
    }

    /**
     * Navigate to channel content.
     */
    private fun navigateToChannel(channelId: String) {
        android.util.Log.d("DeepLink", "Navigate to channel $channelId")
        
        // Navigate to channel's content screen
        // For example, if channel is "movies", show movies list
    }
}
```

### 8.3 Register Intent Filters

Add to `app/src/tv/AndroidManifest.xml`:

```xml
<activity
    android:name=".ui.MainActivity"
    android:exported="true"
    android:launchMode="singleTop"
    android:screenOrientation="landscape">

    <!-- TV Launcher intent -->
    <intent-filter>
        <action android:name="android.intent.action.MAIN" />
        <category android:name="android.intent.category.LEANBACK_LAUNCHER" />
    </intent-filter>

    <!-- Deep link intent for kiduyutv:// scheme -->
    <intent-filter>
        <action android:name="android.intent.action.VIEW" />
        <category android:name="android.intent.category.DEFAULT" />
        <category android:name="android.intent.category.BROWSABLE" />
        <data
            android:scheme="kiduyutv"
            android:host="*" />
    </intent-filter>

    <!-- HTTPS deep links (optional) -->
    <intent-filter android:autoVerify="true">
        <action android:name="android.intent.action.VIEW" />
        <category android:name="android.intent.category.DEFAULT" />
        <category android:name="android.intent.category.BROWSABLE" />
        <data
            android:scheme="https"
            android:host="kiduyutv.app" />
    </intent-filter>
</activity>
```

---

## 9. Step 7: Configure AndroidManifest

Complete TV manifest configuration:

```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:amazon="http://schemas.amazon.com/apk/res/android">

    <!-- TV-specific permissions for channels -->
    <uses-permission android:name="com.android.providers.tv.permission.WRITE_EPG_DATA" />
    <uses-permission android:name="com.android.providers.tv.permission.READ_EPG_DATA" />
    <uses-permission android:name="com.android.providers.tv.permission.READ_WATCH_NEXT" />

    <!-- Leanback feature requirement -->
    <uses-feature
        android:name="android.software.leanback"
        android:required="true" />

    <application
        android:name=".KiduyuTVApplication"
        android:allowBackup="true"
        android:icon="@mipmap/ic_launcher"
        android:banner="@drawable/banner"
        android:label="@string/app_name"
        android:largeHeap="true"
        android:supportsRtl="true"
        android:theme="@style/Theme.KiduyuTV">

        <!-- Main Activity with TV launcher and deep link support -->
        <activity
            android:name=".ui.MainActivity"
            android:exported="true"
            android:launchMode="singleTop"
            android:screenOrientation="landscape">

            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LEANBACK_LAUNCHER" />
            </intent-filter>

            <intent-filter>
                <action android:name="android.intent.action.VIEW" />
                <category android:name="android.intent.category.DEFAULT" />
                <category android:name="android.intent.category.BROWSABLE" />
                <data
                    android:scheme="kiduyutv"
                    android:host="*" />
            </intent-filter>

            <intent-filter android:autoVerify="true">
                <action android:name="android.intent.action.VIEW" />
                <category android:name="android.intent.category.DEFAULT" />
                <category android:name="android.intent.category.BROWSABLE" />
                <data
                    android:scheme="https"
                    android:host="kiduyutv.app" />
            </intent-filter>
        </activity>

        <!-- Detail Activity for deep link handling -->
        <activity
            android:name=".ui.DetailActivity"
            android:exported="true"
            android:launchMode="singleTop"
            android:parentActivityName=".ui.MainActivity">
            
            <intent-filter>
                <action android:name="android.intent.action.VIEW" />
                <category android:name="android.intent.category.DEFAULT" />
                <category android:name="android.intent.category.BROWSABLE" />
                <data
                    android:scheme="kiduyutv" />
            </intent-filter>

            <meta-data
                android:name="android.support.PARENT_ACTIVITY"
                android:value=".ui.MainActivity" />
        </activity>

        <!-- Initialize Programs Receiver -->
        <receiver
            android:name=".tv.receiver.InitializeProgramsReceiver"
            android:exported="true">
            <intent-filter>
                <action android:name="android.media.tv.action.INITIALIZE_PROGRAMS" />
                <category android:name="android.intent.category.DEFAULT" />
            </intent-filter>
        </receiver>

        <!-- Watch Next Removal Receiver -->
        <receiver
            android:name=".tv.receiver.WatchNextRemovedReceiver"
            android:enabled="true"
            android:exported="true">
            <intent-filter>
                <action android:name="android.media.tv.action.WATCH_NEXT_PROGRAM_BROWSABLE_DISABLED" />
            </intent-filter>
        </receiver>

    </application>
</manifest>
```

---

## 10. Step 8: Add Flavor-Specific Resources

### 10.1 TV-Specific Strings

Create `app/src/tv/res/values/strings.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <string name="app_name">KiduyuTV</string>
    
    <!-- Channel Names -->
    <string name="channel_movies">KiduyuTV Movies</string>
    <string name="channel_tv_shows">KiduyuTV TV Shows</string>
    <string name="channel_continue">Continue Watching</string>
    <string name="channel_new">New Releases</string>
    
    <!-- Channel Descriptions -->
    <string name="channel_movies_desc">Trending and popular movies</string>
    <string name="channel_tv_shows_desc">Popular TV series and shows</string>
    <string name="channel_continue_desc">Resume your movies and shows</string>
    <string name="channel_new_desc">Latest movie and TV releases</string>
</resources>
```

### 10.2 TV Banner Image

Create a banner image at `app/src/tv/res/drawable/banner.png` (recommended size: 320x180 dp).

### 10.3 TV Theme

Create `app/src/tv/res/values/themes.xml` if needed for TV-specific styling.

---

## 11. Step 9: Integrate with Existing Data Layer

### 11.1 Channel Sync Manager

Create a sync manager to synchronize watch history with TV channels:

```kotlin
package com.kiduyuk.klausk.kiduyutv.tv.sync

import android.content.Context
import android.util.Log
import com.kiduyuk.klausk.kiduyutv.tv.channel.TvChannelManager
import com.kiduyuk.klausk.kiduyutv.tv.channel.TvProgramManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Syncs watch history and user data with TV channels.
 * 
 * This manager ensures that:
 * 1. Continue Watching channel has the latest progress
 * 2. Personalized channels reflect user preferences
 * 3. Channels are updated when watch history changes
 */
class ChannelSyncManager(private val context: Context) {

    companion object {
        private const val TAG = "ChannelSyncManager"
    }

    private val channelManager = TvChannelManager(context)
    private val programManager = TvProgramManager(context)

    /**
     * Sync watch history to Continue Watching channel.
     * Call this when:
     * - User starts watching a movie/TV show
     * - User completes or abandons content
     * - App launches
     */
    fun syncWatchHistory() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val channelId = channelManager.getProviderChannelId(
                    TvChannelManager.CHANNEL_CONTINUE
                )
                
                if (channelId == -1L) {
                    Log.w(TAG, "Continue Watching channel not found")
                    return@launch
                }

                // Clear existing Continue Watching programs
                // (This would query and delete each program)
                
                // Get watch history from database
                val watchHistoryItems = getWatchHistoryItems()
                
                // Add each item to the channel
                watchHistoryItems.forEach { item ->
                    programManager.addContinueWatchingProgram(
                        channelProviderId = channelId,
                        title = item.title,
                        description = item.description,
                        posterUrl = item.posterUrl,
                        contentId = item.contentId,
                        contentType = item.contentType,
                        currentPositionMs = item.position,
                        totalDurationMs = item.duration
                    )
                }
                
                Log.d(TAG, "Synced ${watchHistoryItems.size} items to Continue Watching")
                
            } catch (e: Exception) {
                Log.e(TAG, "Failed to sync watch history", e)
            }
        }
    }

    /**
     * Update a single program's watch progress.
     * Call this during playback when position changes.
     */
    fun updateProgress(contentId: Long, contentType: String, positionMs: Long) {
        CoroutineScope(Dispatchers.IO).launch {
            val internalId = "${contentType}_${contentId}_continue"
            programManager.updateWatchProgress(internalId, positionMs)
        }
    }

    /**
     * Remove an item from Continue Watching when completed.
     */
    fun markAsCompleted(contentId: Long, contentType: String) {
        CoroutineScope(Dispatchers.IO).launch {
            val internalId = "${contentType}_${contentId}_continue"
            programManager.deleteProgram(internalId)
        }
    }

    /**
     * Get watch history items from Room database.
     * Replace with actual implementation based on your database structure.
     */
    private suspend fun getWatchHistoryItems(): List<WatchHistorySyncItem> {
        // This is a placeholder - implement based on your WatchHistoryDatabase
        return try {
            // Example implementation:
            // val dao = WatchHistoryDatabase.getInstance(context).watchHistoryDao()
            // dao.getInProgressItems().map { item ->
            //     WatchHistorySyncItem(
            //         contentId = item.contentId.toLong(),
            //         contentType = if (item.isTvShow) "tvshow" else "movie",
            //         title = item.title,
            //         description = item.description,
            //         posterUrl = item.posterUrl,
            //         position = item.position,
            //         duration = item.duration
            //     )
            // }
            emptyList()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get watch history", e)
            emptyList()
        }
    }

    data class WatchHistorySyncItem(
        val contentId: Long,
        val contentType: String, // "movie" or "tvshow"
        val title: String,
        val description: String,
        val posterUrl: String,
        val position: Long,
        val duration: Long
    )
}
```

### 11.2 Initialize in Application Class

Update `KiduyuTVApplication.kt` to initialize channels on first launch:

```kotlin
package com.kiduyuk.klausk.kiduyutv

import android.app.Application
import android.os.Build
import android.util.Log
import com.kiduyuk.klausk.kiduyutv.tv.channel.TvChannelManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class KiduyuTVApplication : Application() {

    companion object {
        private const val TAG = "KiduyuTVApp"
    }

    override fun onCreate() {
        super.onCreate()

        // Initialize TV channels on API 26+ devices
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            initializeTvChannels()
        }
    }

    private fun initializeTvChannels() {
        val channelManager = TvChannelManager(this)

        // Check if channels are set up, if not, trigger initialization
        if (!channelManager.channelExists(TvChannelManager.CHANNEL_MOVIES)) {
            Log.d(TAG, "TV channels not initialized, requesting setup")

            CoroutineScope(Dispatchers.IO).launch {
                // Create channels synchronously
                createDefaultChannels()
            }
        }
    }

    private suspend fun createDefaultChannels() {
        val channelManager = TvChannelManager(this)

        try {
            // Create Movies channel
            if (!channelManager.channelExists(TvChannelManager.CHANNEL_MOVIES)) {
                channelManager.createChannel(
                    channelId = TvChannelManager.CHANNEL_MOVIES,
                    displayName = "KiduyuTV Movies",
                    description = "Trending and popular movies"
                )
            }

            // Create TV Shows channel
            if (!channelManager.channelExists(TvChannelManager.CHANNEL_TV_SHOWS)) {
                channelManager.createChannel(
                    channelId = TvChannelManager.CHANNEL_TV_SHOWS,
                    displayName = "KiduyuTV TV Shows",
                    description = "Popular TV series and shows"
                )
            }

            // Create Continue Watching channel
            if (!channelManager.channelExists(TvChannelManager.CHANNEL_CONTINUE)) {
                channelManager.createChannel(
                    channelId = TvChannelManager.CHANNEL_CONTINUE,
                    displayName = "Continue Watching",
                    description = "Resume your movies and shows"
                )
            }

            Log.d(TAG, "Default channels created successfully")

        } catch (e: Exception) {
            Log.e(TAG, "Failed to create default channels", e)
        }
    }
}
```

---

## 12. Step 10: Testing and Verification

### 12.1 Build the TV Flavor

```bash
# Build TV debug APK
./gradlew assembleTvDebug

# Build TV release APK
./gradlew assembleTvRelease
```

### 12.2 Install on Android TV Device

```bash
# Install via ADB
adb install -r app/build/outputs/apk/tv/debug/app-tv-debug.apk

# Or install specific APK
adb install app/build/outputs/apk/tv/debug/kiduyutv_tv-debug.apk
```

### 12.3 Trigger Channel Initialization

```bash
# Trigger INITIALIZE_PROGRAMS broadcast
adb shell am broadcast -a android.media.tv.action.INITIALIZE_PROGRAMS \
    -n com.kiduyuk.klausk.kiduyutv/.tv.receiver.InitializeProgramsReceiver
```

### 12.4 Verify Channel Creation

```bash
# List all TV channels in the system
adb shell content query --projection _id,display_name,browsable \
    --uri content://android.media.tv/channels

# List preview programs in a specific channel
adb shell content query --projection _id,title,internal_provider_id \
    --uri content://android.media.tv/previewprograms

# List Watch Next programs
adb shell content query --projection _id,title,last_position \
    --uri content://android.media.tv/watchnextprograms
```

### 12.5 Test Deep Links

```bash
# Test movie deep link
adb shell am start -d "kiduyutv://movie/12345?position=60000"

# Test TV show deep link
adb shell am start -d "kiduyutv://tvshow/67890?season=2&episode=5"

# Test channel deep link
adb shell am start -d "kiduyutv://channel/movies"
```

### 12.6 Debug Channel Issues

```bash
# Check TV input manager status
adb shell dumpsys tv_input_manager

# Check app's channels
adb shell content query --projection _id,display_name \
    --uri content://android.media.tv/channels \
    --where "input_id LIKE '%kiduyutv%'"

# Query all programs for your app
adb shell content query --projection _id,title,channel_id \
    --uri content://android.media.tv/previewprograms \
    --where "internal_provider_id LIKE '%kiduyutv%'"

# Clear all channels and programs for the app
adb shell content delete --uri \
    "content://android.media.tv/channels?input_id LIKE '%kiduyutv%'"
```

---

## Implementation Checklist

Use this checklist to ensure complete implementation:

### Dependencies
- [ ] Added `androidx.tvprovider:tvprovider:1.0.0` to TV flavor dependencies
- [ ] Added TV permissions to TV manifest
- [ ] Leanback feature declared in manifest

### Channel Management
- [ ] Created `TvChannelManager.kt` with channel creation, update, delete
- [ ] Implemented channel mapping storage (SharedPreferences)
- [ ] Created `InitializeProgramsReceiver.kt`
- [ ] Created `WatchNextRemovedReceiver.kt`

### Program Management
- [ ] Created `TvProgramManager.kt` with program creation
- [ ] Implemented movie programs with TMDB poster URLs
- [ ] Implemented TV show programs with episode info
- [ ] Implemented continue watching programs with progress

### Deep Linking
- [ ] Defined URI scheme (`kiduyutv://`)
- [ ] Updated MainActivity to handle deep links
- [ ] Added intent filters for kiduyutv:// scheme
- [ ] Implemented navigation based on URI

### Integration
- [ ] Created `ChannelSyncManager.kt` for watch history sync
- [ ] Updated Application class for channel initialization
- [ ] Implemented refresh on watch history changes

### Testing
- [ ] Built TV debug APK successfully
- [ ] Installed on Android TV device
- [ ] Triggered INITIALIZE_PROGRAMS
- [ ] Verified channels appear on TV home screen
- [ ] Tested deep links from channel programs
- [ ] Verified Watch Next integration

---

## Summary

This implementation guide covered:

1. **Dependencies** - Adding tvprovider library only to TV flavor
2. **Channel Manager** - Creating and managing channels with proper API usage
3. **Program Manager** - Adding movies, TV shows, and continue watching programs
4. **BroadcastReceivers** - System callbacks for initialization and removal
5. **Deep Linking** - URI scheme and intent handling for navigation
6. **Manifest Configuration** - TV-specific permissions and intent filters
7. **Data Integration** - Syncing watch history with channels
8. **Testing** - Verification commands and troubleshooting

The implementation works with the existing data layer (TmdbRepository, Room database) while respecting the app's two-flavor structure (phone and TV).