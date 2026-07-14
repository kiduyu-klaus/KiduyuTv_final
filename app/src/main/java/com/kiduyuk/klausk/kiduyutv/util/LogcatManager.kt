package com.kiduyuk.klausk.kiduyutv.util

import android.content.Context
import kotlinx.coroutines.*
import java.io.File
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.*

/**
 * LogcatManager - Manages the capture of Android logcat output to a file.
 * 
 * This object provides functionality to:
 * - Start/stop logcat capture process
 * - Write logs to a timestamped file in the app's files directory
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
     * @param tagFilter Optional tag filter to capture only specific tags (null for all logs)
     */
    fun start(context: Context, tagFilter: String? = null) {
        // Stop any existing capture process first
        stop()
        
        // Create log file with timestamp
        val timestamp = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.getDefault()).format(Date())
        val logFileName = "logcat_$timestamp.txt"
        currentLogFile = File(context.filesDir, logFileName)
        
        // Build the logcat command
        val command = if (tagFilter != null) {
            arrayOf("logcat", "-v", "threadtime", "*:V", "-s", tagFilter)
        } else {
            arrayOf("logcat", "-v", "threadtime")
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
                    val fileWriter = currentLogFile?.bufferedWriter()
                    
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
