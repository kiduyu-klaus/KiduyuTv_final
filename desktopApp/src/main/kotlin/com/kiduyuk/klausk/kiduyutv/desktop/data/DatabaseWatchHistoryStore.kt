package com.kiduyuk.klausk.kiduyutv.desktop.data

import com.kiduyuk.klausk.kiduyutv.desktop.model.MediaType
import com.kiduyuk.klausk.kiduyutv.desktop.model.PlayRequest
import com.kiduyuk.klausk.kiduyutv.desktop.model.WatchProgress
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.file.Files
import java.nio.file.Paths
import java.sql.Connection
import java.sql.DriverManager
import java.sql.SQLException

class DatabaseWatchHistoryStore(
    private val dbPath: String = Paths.get(
        System.getenv("LOCALAPPDATA") ?: System.getProperty("user.home"),
        "KiduyuTV",
        "history.db"
    ).toString()
) {
    private val connection by lazy { createConnection() }

    init {
        createSchema()
    }

    suspend fun find(request: PlayRequest): WatchProgress? =
        withContext(Dispatchers.IO) {
            try {
                val query = """
                    SELECT tmdb_id, media_type, title, poster_path, backdrop_path, season, episode, position_ms, duration_ms, updated_at
                    FROM watch_progress
                    WHERE tmdb_id = ? AND media_type = ?
                      AND (media_type = 'movie' OR (season = ? AND episode = ?))
                    ORDER BY updated_at DESC
                    LIMIT 1
                """.trimIndent()

                connection.prepareStatement(query).use { stmt ->
                    stmt.setInt(1, request.tmdbId)
                    stmt.setString(2, request.mediaType.apiValue)
                    stmt.setInt(3, request.season ?: -1)
                    stmt.setInt(4, request.episode ?: -1)

                    stmt.executeQuery().use { rs ->
                        if (rs.next()) {
                            WatchProgress(
                                tmdbId = rs.getInt(1),
                                mediaType = MediaType.entries.first { it.apiValue == rs.getString(2) },
                                title = rs.getString(3),
                                posterPath = rs.getString(4).takeIf { it.isNotEmpty() },
                                backdropPath = rs.getString(5).takeIf { it.isNotEmpty() },
                                season = rs.getInt(6).takeIf { it > 0 },
                                episode = rs.getInt(7).takeIf { it > 0 },
                                positionMs = rs.getLong(8),
                                durationMs = rs.getLong(9),
                                updatedAt = rs.getLong(10)
                            )
                        } else null
                    }
                }
            } catch (e: SQLException) {
                e.printStackTrace()
                null
            }
        }

    suspend fun save(progress: WatchProgress) =
        withContext(Dispatchers.IO) {
            try {
                val query = """
                    INSERT OR REPLACE INTO watch_progress
                    (tmdb_id, media_type, title, poster_path, backdrop_path, season, episode, position_ms, duration_ms, updated_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """.trimIndent()

                connection.prepareStatement(query).use { stmt ->
                    stmt.setInt(1, progress.tmdbId)
                    stmt.setString(2, progress.mediaType.apiValue)
                    stmt.setString(3, progress.title)
                    stmt.setString(4, progress.posterPath ?: "")
                    stmt.setString(5, progress.backdropPath ?: "")
                    stmt.setInt(6, progress.season ?: -1)
                    stmt.setInt(7, progress.episode ?: -1)
                    stmt.setLong(8, progress.positionMs)
                    stmt.setLong(9, progress.durationMs)
                    stmt.setLong(10, progress.updatedAt)
                    stmt.executeUpdate()
                }
            } catch (e: SQLException) {
                e.printStackTrace()
            }
        }

    suspend fun getAll(): List<WatchProgress> =
        withContext(Dispatchers.IO) {
            try {
                val query = """
                    SELECT tmdb_id, media_type, title, poster_path, backdrop_path, season, episode, position_ms, duration_ms, updated_at
                    FROM watch_progress
                    ORDER BY updated_at DESC
                    LIMIT 250
                """.trimIndent()

                connection.createStatement().use { stmt ->
                    stmt.executeQuery(query).use { rs ->
                        val results = mutableListOf<WatchProgress>()
                        while (rs.next()) {
                            results.add(
                                WatchProgress(
                                    tmdbId = rs.getInt(1),
                                    mediaType = MediaType.entries.first { it.apiValue == rs.getString(2) },
                                    title = rs.getString(3),
                                    posterPath = rs.getString(4).takeIf { it.isNotEmpty() },
                                    backdropPath = rs.getString(5).takeIf { it.isNotEmpty() },
                                    season = rs.getInt(6).takeIf { it > 0 },
                                    episode = rs.getInt(7).takeIf { it > 0 },
                                    positionMs = rs.getLong(8),
                                    durationMs = rs.getLong(9),
                                    updatedAt = rs.getLong(10)
                                )
                            )
                        }
                        results
                    }
                }
            } catch (e: SQLException) {
                e.printStackTrace()
                emptyList()
            }
        }

    suspend fun delete(request: PlayRequest) =
        withContext(Dispatchers.IO) {
            try {
                val query = """
                    DELETE FROM watch_progress
                    WHERE tmdb_id = ? AND media_type = ?
                      AND (media_type = 'movie' OR (season = ? AND episode = ?))
                """.trimIndent()

                connection.prepareStatement(query).use { stmt ->
                    stmt.setInt(1, request.tmdbId)
                    stmt.setString(2, request.mediaType.apiValue)
                    stmt.setInt(3, request.season ?: -1)
                    stmt.setInt(4, request.episode ?: -1)
                    stmt.executeUpdate()
                }
            } catch (e: SQLException) {
                e.printStackTrace()
            }
        }

    suspend fun clear() =
        withContext(Dispatchers.IO) {
            try {
                connection.createStatement().use { stmt ->
                    stmt.execute("DELETE FROM watch_progress")
                }
            } catch (e: SQLException) {
                e.printStackTrace()
            }
        }

    private fun createConnection(): Connection {
        val dbFile = File(dbPath)
        Files.createDirectories(dbFile.parentFile.toPath())
        
        // Load SQLite JDBC driver
        Class.forName("org.sqlite.JDBC")
        
        return DriverManager.getConnection("jdbc:sqlite:$dbPath")
    }

    private fun createSchema() {
        try {
            val schema = """
                CREATE TABLE IF NOT EXISTS watch_progress (
                    tmdb_id INTEGER NOT NULL,
                    media_type TEXT NOT NULL,
                    title TEXT NOT NULL,
                    poster_path TEXT,
                    backdrop_path TEXT,
                    season INTEGER DEFAULT -1,
                    episode INTEGER DEFAULT -1,
                    position_ms INTEGER NOT NULL DEFAULT 0,
                    duration_ms INTEGER NOT NULL DEFAULT 0,
                    updated_at INTEGER NOT NULL,
                    PRIMARY KEY (tmdb_id, media_type, season, episode)
                );
                CREATE INDEX IF NOT EXISTS idx_updated_at ON watch_progress(updated_at DESC);
            """.trimIndent()

            connection.createStatement().use { stmt ->
                val statements = schema.split(";").filter { it.isNotBlank() }
                statements.forEach { stmt.execute(it) }
            }
        } catch (e: SQLException) {
            e.printStackTrace()
        }
    }
}
