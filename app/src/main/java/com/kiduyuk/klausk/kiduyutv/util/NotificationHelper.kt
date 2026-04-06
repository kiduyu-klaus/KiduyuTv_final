package com.kiduyuk.klausk.kiduyutv.util

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.kiduyuk.klausk.kiduyutv.R
import com.kiduyuk.klausk.kiduyutv.activity.mainactivity.MainActivity

/**
 * Helper class to manage notification channels and post notifications.
 */
object NotificationHelper {

    private const val CHANNEL_ID = "media_recommendations"
    private const val CHANNEL_NAME = "Recommendations"
    private const val CHANNEL_DESCRIPTION = "Daily movie and TV show recommendations"
    private const val NOTIFICATION_ID = 1001

    /**
     * Creates the notification channel for media recommendations.
     * Should be called during app initialization (e.g., in SplashActivity or Application class).
     */
    fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val importance = NotificationManager.IMPORTANCE_DEFAULT
            val channel = NotificationChannel(CHANNEL_ID, CHANNEL_NAME, importance).apply {
                description = CHANNEL_DESCRIPTION
            }
            val notificationManager: NotificationManager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    /**
     * Posts a notification for a movie or TV show.
     * 
     * @param context The application context.
     * @param id The TMDB ID of the media.
     * @param title The title of the movie or TV show.
     * @param type The type of media ("movie" or "tv").
     * @param overview A brief overview of the media.
     */
    fun postMediaNotification(
        context: Context,
        id: Int,
        title: String,
        type: String,
        overview: String
    ) {
        // Create an intent that will be handled by MainActivity for deep-linking
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra("NOTIFICATION_MEDIA_ID", id)
            putExtra("NOTIFICATION_MEDIA_TYPE", type)
        }

        val pendingIntent: PendingIntent = PendingIntent.getActivity(
            context,
            id, // Use ID as request code to allow multiple distinct intents if needed
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)
        )

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher11) // Using existing launcher icon
            .setContentTitle("Recommended for you")
            .setContentText(title)
            .setStyle(NotificationCompat.BigTextStyle().bigText("$title\n\n$overview"))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)

        with(NotificationManagerCompat.from(context)) {
            // Check for permission is handled by the system on API 33+
            try {
                notify(NOTIFICATION_ID, builder.build())
            } catch (e: SecurityException) {
                // Handle case where permission might have been revoked
            }
        }
    }
}
