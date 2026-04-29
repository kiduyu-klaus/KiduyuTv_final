package com.kiduyuk.klausk.kiduyutv.util

import android.content.Context
import android.util.Log
import kotlinx.coroutines.*
import java.io.*
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/**
 * Manages downloading and updating of ad-blocking filter lists
 */
object FilterListUpdater {
    
    private const val TAG = "FilterListUpdater"
    private const val UPDATE_INTERVAL_MS = 24 * 60 * 60 * 1000L // 24 hours
    private const val TIMEOUT_MS = 30_000 // 30 seconds timeout
    
    // Filter list URLs
    private val FILTER_SOURCES = mapOf(
        "easylist.txt" to "https://easylist.to/easylist/easylist.txt",
        "easyprivacy.txt" to "https://easylist.to/easylist/easyprivacy.txt",
        "custom_filters.txt" to "https://raw.githubusercontent.com/hagezi/dns-blocklists/main/adblock/native.oppo-realme.txt"
    )
    
    // Fallback/backup URLs in case primary fails
    private val FALLBACK_SOURCES = mapOf(
        "easylist.txt" to "https://easylist-downloads.adblockplus.org/easylist.txt",
        "easyprivacy.txt" to "https://easylist-downloads.adblockplus.org/easyprivacy.txt",
        "custom_filters.txt" to "https://raw.githubusercontent.com/hagezi/dns-blocklists/main/adblock/native.oppo-realme.txt"
    )
    
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    /**
     * Represents the update status for each filter
     */
    data class UpdateStatus(
        val filename: String,
        val success: Boolean,
        val errorMessage: String? = null,
        val fileSize: Long = 0,
        val ruleCount: Int = 0
    )
    
    /**
     * Callback interface for update progress
     */
    interface UpdateCallback {
        fun onUpdateStarted(totalFiles: Int)
        fun onProgress(filename: String, progress: Int)
        fun onFileCompleted(status: UpdateStatus)
        fun onAllCompleted(results: List<UpdateStatus>)
        fun onError(filename: String, error: String)
    }
    
    /**
     * Check if any filter files need updating
     */
    fun needsUpdate(context: Context): Boolean {
        val prefs = context.getSharedPreferences("filter_updates", Context.MODE_PRIVATE)
        val lastUpdate = prefs.getLong("last_update_timestamp", 0)
        val now = System.currentTimeMillis()
        
        if (now - lastUpdate > UPDATE_INTERVAL_MS) {
            return true
        }
        
        // Check if any filter file is missing
        return FILTER_SOURCES.keys.any { filename ->
            !File(context.filesDir, filename).exists()
        }
    }
    
    /**
     * Check if all filter files exist in assets
     */
    fun areAssetsPresent(context: Context): Boolean {
        return FILTER_SOURCES.keys.all { filename ->
            try {
                context.assets.open(filename).close()
                true
            } catch (e: IOException) {
                false
            }
        }
    }
    
    /**
     * Copy missing files from assets to internal storage if they don't exist
     */
    fun copyMissingFromAssets(context: Context): List<String> {
        val copied = mutableListOf<String>()
        
        FILTER_SOURCES.keys.forEach { filename ->
            val targetFile = File(context.filesDir, filename)
            if (!targetFile.exists()) {
                try {
                    context.assets.open(filename).use { input ->
                        FileOutputStream(targetFile).use { output ->
                            input.copyTo(output)
                        }
                    }
                    copied.add(filename)
                    Log.i(TAG, "Copied $filename from assets to internal storage")
                } catch (e: IOException) {
                    Log.w(TAG, "Could not copy $filename from assets: ${e.message}")
                }
            }
        }
        
        return copied
    }
    
    /**
     * Update all filter lists synchronously
     */
    fun updateAllFiltersSync(context: Context): List<UpdateStatus> {
        val results = mutableListOf<UpdateStatus>()
        
        FILTER_SOURCES.forEach { (filename, url) ->
            val result = downloadFilter(context, filename, url)
            results.add(result)
            
            if (!result.success) {
                // Try fallback URL
                FALLBACK_SOURCES[filename]?.let { fallbackUrl ->
                    Log.w(TAG, "Trying fallback for $filename: $fallbackUrl")
                    results.add(downloadFilter(context, filename, fallbackUrl))
                }
            }
        }
        
        // Update timestamp
        if (results.any { it.success }) {
            context.getSharedPreferences("filter_updates", Context.MODE_PRIVATE)
                .edit()
                .putLong("last_update_timestamp", System.currentTimeMillis())
                .apply()
        }
        
        return results
    }
    
    /**
     * Update all filter lists asynchronously with progress callbacks
     */
    fun updateAllFiltersAsync(
        context: Context,
        callback: UpdateCallback? = null
    ) {
        val totalFiles = FILTER_SOURCES.size
        callback?.onUpdateStarted(totalFiles)
        
        scope.launch {
            val results = mutableListOf<UpdateStatus>()
            var completed = 0
            
            FILTER_SOURCES.forEach { (filename, url) ->
                val result = downloadFilter(context, filename, url)
                results.add(result)
                completed++
                
                withContext(Dispatchers.Main) {
                    callback?.onProgress(filename, (completed * 100) / totalFiles)
                    callback?.onFileCompleted(result)
                }
                
                // Try fallback if primary failed
                if (!result.success) {
                    FALLBACK_SOURCES[filename]?.let { fallbackUrl ->
                        delay(1000) // Brief delay before fallback
                        val fallbackResult = downloadFilter(context, filename, fallbackUrl)
                        results.add(fallbackResult)
                        withContext(Dispatchers.Main) {
                            callback?.onFileCompleted(fallbackResult)
                        }
                    }
                }
            }
            
            // Update timestamp if any succeeded
            if (results.any { it.success }) {
                context.getSharedPreferences("filter_updates", Context.MODE_PRIVATE)
                    .edit()
                    .putLong("last_update_timestamp", System.currentTimeMillis())
                    .apply()
            }
            
            withContext(Dispatchers.Main) {
                callback?.onAllCompleted(results)
            }
        }
    }
    
    /**
     * Download a single filter file
     */
    private fun downloadFilter(
        context: Context,
        filename: String,
        urlString: String
    ): UpdateStatus {
        var connection: HttpURLConnection? = null
        var inputStream: InputStream? = null
        var outputStream: FileOutputStream? = null
        var reader: BufferedReader? = null
        
        try {
            val url = URL(urlString)
            connection = url.openConnection() as HttpURLConnection
            connection.apply {
                connectTimeout = TIMEOUT_MS
                readTimeout = TIMEOUT_MS
                setRequestProperty(
                    "User-Agent",
                    "Mozilla/5.0 (compatible; AdBlocker/1.0)"
                )
                setRequestProperty("Accept", "text/plain")
                instanceFollowRedirects = true
            }
            
            val responseCode = connection.responseCode
            if (responseCode != HttpURLConnection.HTTP_OK) {
                return UpdateStatus(
                    filename = filename,
                    success = false,
                    errorMessage = "HTTP $responseCode"
                )
            }
            
            // Create temp file first
            val tempFile = File(context.filesDir, "$filename.tmp")
            val targetFile = File(context.filesDir, filename)
            
            inputStream = BufferedInputStream(connection.inputStream)
            outputStream = FileOutputStream(tempFile)
            
            // Copy with progress tracking
            val buffer = ByteArray(8192)
            var bytesRead: Int
            var totalBytes = 0L
            
            while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                outputStream.write(buffer, 0, bytesRead)
                totalBytes += bytesRead
            }
            outputStream.flush()
            
            // Validate the downloaded file
            if (totalBytes < 1000) { // Less than 1KB is suspicious
                tempFile.delete()
                return UpdateStatus(
                    filename = filename,
                    success = false,
                    errorMessage = "File too small ($totalBytes bytes)"
                )
            }
            
            // Count rules
            var ruleCount = 0
            try {
                reader = BufferedReader(FileReader(tempFile))
                ruleCount = reader.lineSequence()
                    .map { it.trim() }
                    .count { it.isNotEmpty() && !it.startsWith("!") && !it.startsWith("[") }
            } catch (e: Exception) {
                Log.w(TAG, "Could not count rules in $filename: ${e.message}")
            }
            
            // Replace old file with new one
            if (targetFile.exists()) {
                val backupFile = File(context.filesDir, "$filename.bak")
                targetFile.renameTo(backupFile)
            }
            
            tempFile.renameTo(targetFile)
            
            // Clean up backup
            File(context.filesDir, "$filename.bak").delete()
            
            Log.i(TAG, "Successfully downloaded $filename: $totalBytes bytes, $ruleCount rules")
            
            return UpdateStatus(
                filename = filename,
                success = true,
                fileSize = totalBytes,
                ruleCount = ruleCount
            )
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to download $filename: ${e.message}", e)
            return UpdateStatus(
                filename = filename,
                success = false,
                errorMessage = e.message
            )
        } finally {
            try {
                connection?.disconnect()
                inputStream?.close()
                outputStream?.close()
                reader?.close()
            } catch (e: Exception) {
                // Ignore cleanup errors
            }
        }
    }
    
    /**
     * Verify integrity of downloaded file
     */
    fun verifyFileIntegrity(context: Context, filename: String): Boolean {
        val file = File(context.filesDir, filename)
        if (!file.exists()) return false
        
        // Check if file is a valid filter list
        try {
            BufferedReader(FileReader(file)).use { reader ->
                val firstLine = reader.readLine()
                // Most filter lists start with a header comment
                return firstLine != null && 
                       (firstLine.startsWith("[") || firstLine.startsWith("!") || firstLine.startsWith("||"))
            }
        } catch (e: Exception) {
            return false
        }
    }
    
    /**
     * Get the local file path for a filter
     */
    fun getFilterFile(context: Context, filename: String): File {
        return File(context.filesDir, filename)
    }
    
    /**
     * Calculate file checksum for comparison
     */
    fun getFileChecksum(file: File): String? {
        try {
            val digest = MessageDigest.getInstance("SHA-256")
            FileInputStream(file).use { input ->
                val buffer = ByteArray(8192)
                var bytesRead: Int
                while (input.read(buffer).also { bytesRead = it } != -1) {
                    digest.update(buffer, 0, bytesRead)
                }
            }
            return digest.digest().joinToString("") { "%02x".format(it) }
        } catch (e: Exception) {
            return null
        }
    }
    
    /**
     * Clean up filter files
     */
    fun clearAllFilters(context: Context) {
        FILTER_SOURCES.keys.forEach { filename ->
            File(context.filesDir, filename).delete()
            File(context.filesDir, "$filename.tmp").delete()
            File(context.filesDir, "$filename.bak").delete()
        }
        context.getSharedPreferences("filter_updates", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .apply()
    }
}