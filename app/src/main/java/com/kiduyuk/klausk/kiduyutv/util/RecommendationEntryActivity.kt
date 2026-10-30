package com.kiduyuk.klausk.kiduyutv.util

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import com.kiduyuk.klausk.kiduyutv.activity.mainactivity.MainActivity

/** External entry point for system recommendation cards; MainActivity remains private. */
class RecommendationEntryActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val uri = intent?.data
        if (uri == null || uri.scheme != SCHEME || uri.query != null || uri.fragment != null) {
            finish()
            return
        }
        when (uri.host) {
            "home" -> launchMainActivity()
            "movie", "tv" -> {
                val id = uri.pathSegments.singleOrNull()?.toIntOrNull()
                if (id != null && id > 0) launchMainActivity(uri.host, id) else finish()
            }
            else -> finish()
        }
    }

    private fun launchMainActivity(type: String? = null, id: Int? = null) {
        startActivity(Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            if (type != null && id != null) {
                putExtra("NOTIFICATION_MEDIA_ID", id)
                putExtra("NOTIFICATION_MEDIA_TYPE", type)
            }
        })
        finish()
    }

    companion object {
        private const val SCHEME = "kiduyutv"

        fun intent(context: Context, type: String, id: Int): Intent =
            Intent(context, RecommendationEntryActivity::class.java).apply {
                data = Uri.parse("$SCHEME://$type/$id")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
    }
}
