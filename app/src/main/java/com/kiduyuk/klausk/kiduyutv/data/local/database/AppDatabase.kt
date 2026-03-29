package com.kiduyuk.klausk.kiduyutv.data.local.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.kiduyuk.klausk.kiduyutv.data.local.dao.CachedMovieDao
import com.kiduyuk.klausk.kiduyutv.data.local.dao.CachedMovieDetailDao
import com.kiduyuk.klausk.kiduyutv.data.local.dao.CachedTvShowDao
import com.kiduyuk.klausk.kiduyutv.data.local.dao.CachedTvShowDetailDao
import com.kiduyuk.klausk.kiduyutv.data.local.dao.GenreDao
import com.kiduyuk.klausk.kiduyutv.data.local.dao.SavedMediaDao
import com.kiduyuk.klausk.kiduyutv.data.local.dao.WatchHistoryDao
import com.kiduyuk.klausk.kiduyutv.data.local.entity.CachedMovieDetailEntity
import com.kiduyuk.klausk.kiduyutv.data.local.entity.CachedMovieEntity
import com.kiduyuk.klausk.kiduyutv.data.local.entity.CachedTvShowDetailEntity
import com.kiduyuk.klausk.kiduyutv.data.local.entity.CachedTvShowEntity
import com.kiduyuk.klausk.kiduyutv.data.local.entity.GenreEntity
import com.kiduyuk.klausk.kiduyutv.data.local.entity.SavedMediaEntity
import com.kiduyuk.klausk.kiduyutv.data.local.entity.WatchHistoryEntity

/**
 * Room Database for KiduyuTv app.
 * This is the main database class that contains all entities and DAOs
 * for managing local data storage including:
 * - User's saved media (My List)
 * - Watch history and playback positions
 * - Cached movie and TV show data for offline support
 * - Genre information caching
 *
 * The database uses a singleton pattern and should be accessed through
 * DatabaseManager to ensure proper initialization.
 */
@Database(
    entities = [
        SavedMediaEntity::class,
        WatchHistoryEntity::class,
        CachedMovieEntity::class,
        CachedTvShowEntity::class,
        CachedMovieDetailEntity::class,
        CachedTvShowDetailEntity::class,
        GenreEntity::class
    ],
    version = 2,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    /**
     * DAO for saved media items (My List feature).
     */
    abstract fun savedMediaDao(): SavedMediaDao

    /**
     * DAO for watch history tracking.
     */
    abstract fun watchHistoryDao(): WatchHistoryDao

    /**
     * DAO for caching movies.
     */
    abstract fun cachedMovieDao(): CachedMovieDao

    /**
     * DAO for caching TV shows.
     */
    abstract fun cachedTvShowDao(): CachedTvShowDao

    /**
     * DAO for caching movie details.
     */
    abstract fun cachedMovieDetailDao(): CachedMovieDetailDao

    /**
     * DAO for caching TV show details.
     */
    abstract fun cachedTvShowDetailDao(): CachedTvShowDetailDao

    /**
     * DAO for caching genres.
     */
    abstract fun genreDao(): GenreDao

    companion object {
        private const val DATABASE_NAME = "kiduyu_tv_database"

        @Volatile
        private var INSTANCE: AppDatabase? = null

        /**
         * Get the singleton database instance.
         * Creates the database if it doesn't exist.
         *
         * @param context Application context
         * @return The database instance
         */
        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: buildDatabase(context).also { INSTANCE = it }
            }
        }

        private fun buildDatabase(context: Context): AppDatabase {
            return Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                DATABASE_NAME
            )
                // Allow main thread queries for backwards compatibility
                // In production, prefer off-main-thread operations
                .allowMainThreadQueries()
                .fallbackToDestructiveMigration()
                // Callback for database creation
                .addCallback(object : Callback() {
                    override fun onCreate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                        super.onCreate(db)
                        // Database created for the first time
                        // Can perform initial setup here if needed
                    }

                    override fun onOpen(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                        super.onOpen(db)
                        // Database opened
                    }
                })
                .build()
        }

        /**
         * Close the database instance.
         * Should be called when the application is being destroyed.
         */
        fun closeDatabase() {
            INSTANCE?.close()
            INSTANCE = null
        }
    }
}
