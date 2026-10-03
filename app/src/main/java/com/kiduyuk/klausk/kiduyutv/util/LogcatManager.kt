package com.kiduyuk.klausk.kiduyutv.util

import android.content.Context
import android.os.Process
import kotlinx.coroutines.*
import java.io.BufferedWriter
import java.io.File
import java.io.FileWriter
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.*

/**
 * LogcatManager - Manages the capture of Android logcat output to a file.
 * 
 * This object provides functionality to:
 * - Start/stop logcat capture process
 * - Write this app process's logs to a timestamped file in the app's files directory
 * - Provide access to the current log file
 */
object LogcatManager {
    
    private var logcatProcess: Process? = null
    private var loggingJob: Job? = null
    private val coroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    private var currentLogFile: File? = null
    
    /**
     * Starts capturing logcat output to a file.
     * 
     * @param context Application context used for file operations
     * @param tagFilter Optional tag filter to capture only specific tags from this app process
     */
    fun start(context: Context, tagFilter: String? = null) {
        // Stop any existing capture process first
        stop()
        
        // Create log file with timestamp
        val timestamp = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.getDefault()).format(Date())
        val logFileName = "logcat_$timestamp.txt"
        currentLogFile = File(context.filesDir, logFileName)
        
        // Build the logcat command. Restrict the stream to this process so
        // system-wide Android logs do not flood the in-app viewer. This still
        // includes logs emitted by the app's own code and SDKs in this process.
        val processFilter = "--pid=${Process.myPid()}"
        val command = if (tagFilter != null) {
            arrayOf("logcat", "-v", "threadtime", processFilter, "*:V", "-s", tagFilter)
        } else {
            arrayOf("logcat", "-v", "threadtime", processFilter)
        }
        
        try {
            // Start the logcat process
            logcatProcess = Runtime.getRuntime().exec(command)
            
            // Start a coroutine to read and write logs
            loggingJob = coroutineScope.launch {
                try {
                    val inputStream: InputStream = logcatProcess!!.inputStream
                    val bufferedReader = inputStream.bufferedReader()
                    
                    // Ensure file is created
                    currentLogFile?.createNewFile()
                    // Open the file in append mode so existing log content is preserved
                    // across stop/start cycles and across multiple capture sessions.
                    val fileWriter: BufferedWriter? = currentLogFile?.let { file ->
                        BufferedWriter(FileWriter(file, /* append = */ true))
                    }
                    
                    fileWriter?.use { writer ->
                        bufferedReader.useLines { lines ->
                            lines.forEach { line ->
                                writer.write(line)
                                writer.newLine()
                                writer.flush()
                            }
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
            
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    /**
     * Stops the logcat capture process.
     */
    fun stop() {
        loggingJob?.cancel()
        loggingJob = null
        
        logcatProcess?.destroy()
        logcatProcess = null
    }
    
    /**
     * Gets the current log file if one exists.
     * 
     * @return The current log file, or null if no capture is in progress
     */
    fun getCurrentLogFile(): File? = currentLogFile
    
    /**
     * Gets all log files in the app's files directory.
     * 
     * @param context Application context
     * @return List of log files sorted by modification time (newest first)
     */
    fun getAllLogFiles(context: Context): List<File> {
        val filesDir = context.filesDir
        return filesDir.listFiles { file ->
            file.name.startsWith("logcat_") && file.name.endsWith(".txt")
        }?.sortedByDescending { it.lastModified() } ?: emptyList()
    }
    
    /**
     * Clears all log files from the app's files directory.
     * 
     * @param context Application context
     * @return Number of files deleted
     */
    fun clearAllLogs(context: Context): Int {
        val logFiles = getAllLogFiles(context)
        logFiles.forEach { it.delete() }
        return logFiles.size
    }

    /**
     * Deletes completed logcat files whose last modification time is older
     * than [maxAgeMillis]. Files outside the `logcat_*.txt` naming contract
     * and the active capture file are never touched.
     *
     * @return Number of files successfully deleted.
     */
    fun deleteLogsOlderThan(context: Context, maxAgeMillis: Long): Int {
        require(maxAgeMillis >= 0L) { "maxAgeMillis must not be negative" }
        val cutoffMillis = System.currentTimeMillis() - maxAgeMillis
        val activeLogPath = currentLogFile?.absolutePath

        return getAllLogFiles(context).count { file ->
            file.absolutePath != activeLogPath &&
                file.lastModified() < cutoffMillis &&
                runCatching { file.delete() }.getOrDefault(false)
        }
    }
    
    /**
     * Reads the content of the current or most recent log file.
     * 
     * @param context Application context
     * @return The log content as a String, or empty string if no logs exist
     */
    fun readLogContent(context: Context): String {
        val logFile = currentLogFile ?: getAllLogFiles(context).firstOrNull()
        return logFile?.readText() ?: ""
    }
    
    /**
     * Checks if logcat capture is currently running.
     * 
     * @return true if capturing, false otherwise
     */
    fun isCapturing(): Boolean = loggingJob?.isActive == true && logcatProcess?.isAlive == true
}
