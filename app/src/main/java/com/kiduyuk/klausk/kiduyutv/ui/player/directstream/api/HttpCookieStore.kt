package com.kiduyuk.klausk.kiduyutv.ui.player.directstream.api

import android.net.Uri
import java.net.CookieHandler
import java.net.CookieManager
import java.net.CookiePolicy
import java.net.HttpURLConnection
import java.net.URI

/**
 * Process-wide, domain-scoped cookie store shared by API discovery and
 * Media3's HttpURLConnection-backed data source.
 */
object HttpCookieStore {
    private val manager = CookieManager(null, CookiePolicy.ACCEPT_ALL)

    init {
        CookieHandler.setDefault(manager)
    }

    private fun toSafeUri(url: String): URI {
        return try {
            URI(url)
        } catch (_: Exception) {
            try {
                val parsed = Uri.parse(url)
                URI(
                    parsed.scheme,
                    parsed.userInfo,
                    parsed.host,
                    parsed.port,
                    parsed.path,
                    parsed.query,
                    parsed.fragment
                )
            } catch (_: Exception) {
                // Fallback to a basic URI if all else fails to avoid crashing
                URI("http://invalid.url")
            }
        }
    }

    fun applyTo(connection: HttpURLConnection, url: String) {
        val uri = toSafeUri(url)
        manager.get(uri, emptyMap()).forEach { (name, values) ->
            if (name.isNotBlank() && values.isNotEmpty()) {
                connection.setRequestProperty(name, values.joinToString("; "))
            }
        }
    }

    fun captureFrom(connection: HttpURLConnection, url: String) {
        val uri = toSafeUri(url)
        manager.put(uri, connection.headerFields)
    }

    fun cookieHeader(url: String): String? {
        val uri = toSafeUri(url)
        return manager.get(uri, emptyMap())
            .entries
            .firstOrNull { it.key.equals("Cookie", ignoreCase = true) }
            ?.value
            ?.joinToString("; ")
            ?.takeIf { it.isNotBlank() }
    }
}
