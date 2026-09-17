package com.kiduyuk.klausk.kiduyutv.util

import android.net.Uri
import java.net.URI

object UrlUtils {
    /**
     * Normalizes a URL by ensuring that special characters in the path (like spaces and brackets)
     * are properly percent-encoded. This prevents URISyntaxException in java.net.URI and
     * ensures compatibility with various network stacks.
     */
    fun normalize(url: String?): String {
        if (url.isNullOrBlank()) return ""
        val trimmed = url.trim()
        
        // If it's already a valid URI according to java.net.URI, we still check if it's "safe".
        // java.net.URI(String) is very strict about spaces.
        return try {
            val uri = URI(trimmed)
            // If it parsed, it might still have unencoded parts that URI(String) allows but 
            // causes issues elsewhere, or it might be perfectly fine.
            // For example, URI("http://host/a b") throws exception.
            // So if it DID NOT throw, it's likely already encoded or simple.
            trimmed
        } catch (e: Exception) {
            try {
                val parsed = Uri.parse(trimmed)
                // Use the multi-argument constructor which performs encoding
                URI(
                    parsed.scheme,
                    parsed.userInfo,
                    parsed.host,
                    parsed.port,
                    parsed.path,
                    parsed.query,
                    parsed.fragment
                ).toString()
            } catch (e2: Exception) {
                // Fallback to manual space replacement if all else fails
                trimmed.replace(" ", "%20")
            }
        }
    }
}
