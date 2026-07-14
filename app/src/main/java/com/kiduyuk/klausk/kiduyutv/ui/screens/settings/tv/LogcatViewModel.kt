package com.kiduyuk.klausk.kiduyutv.ui.screens.settings.tv

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.kiduyuk.klausk.kiduyutv.util.LogcatManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * ViewModel for managing logcat viewer functionality.
 * 
 * This ViewModel provides:
 * - Log content display
 * - Log file management (view, export, clear)
 * - Capture state management
 */
class LogcatViewModel(application: Application) : AndroidViewModel(application) {
    
    private val _logContent = MutableStateFlow("")
    val logContent: StateFlow<String> = _logContent.asStateFlow()
    
    private val _logFiles = MutableStateFlow<List<File>>(emptyList())
    val logFiles: StateFlow<List<File>> = _logFiles.asStateFlow()
    
    private val _isCapturing = MutableStateFlow(false)
    val isCapturing: StateFlow<Boolean> = _isCapturing.asStateFlow()
    
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()
    
    private val _selectedFile = MutableStateFlow<File?>(null)
    val selectedFile: StateFlow<File?> = _selectedFile.asStateFlow()
    
    private val _exportIntent = MutableStateFlow<Intent?>(null)
    val exportIntent: StateFlow<Intent?> = _exportIntent.asStateFlow()
    
    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()
    
    init {
        refreshLogFiles()
        updateCapturingState()
    }
    
    /**
     * Loads the content of the current or most recent log file.
     */
    fun loadLogContent() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val content = withContext(Dispatchers.IO) {
                    LogcatManager.readLogContent(getApplication())
                }
                _logContent.value = content
            } catch (e: Exception) {
                _message.value = "Failed to load log content: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }
    
    /**
     * Loads the content of a specific log file.
     */
    fun loadLogFile(file: File) {
        viewModelScope.launch {
            _isLoading.value = true
            _selectedFile.value = file
            try {
                val content = withContext(Dispatchers.IO) {
                    file.readText()
                }
                _logContent.value = content
            } catch (e: Exception) {
                _message.value = "Failed to load log file: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }
    
    /**
     * Refreshes the list of available log files.
     */
    fun refreshLogFiles() {
        viewModelScope.launch {
            try {
                val files = withContext(Dispatchers.IO) {
                    LogcatManager.getAllLogFiles(getApplication())
                }
                _logFiles.value = files
            } catch (e: Exception) {
                _message.value = "Failed to refresh log files: ${e.message}"
            }
        }
    }
    
    /**
     * Clears all log files.
     */
    fun clearAllLogs() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val count = withContext(Dispatchers.IO) {
                    LogcatManager.clearAllLogs(getApplication())
                }
                _message.value = "Cleared $count log file(s)"
                _logContent.value = ""
                _selectedFile.value = null
                refreshLogFiles()
            } catch (e: Exception) {
                _message.value = "Failed to clear logs: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }
    
    /**
     * Exports the currently selected log file or the most recent one.
     */
    fun exportLogFile() {
        viewModelScope.launch {
            try {
                val fileToExport = _selectedFile.value ?: withContext(Dispatchers.IO) {
                    LogcatManager.getAllLogFiles(getApplication()).firstOrNull()
                }
                
                if (fileToExport == null) {
                    _message.value = "No log file to export"
                    return@launch
                }
                
                val context = getApplication<Application>()
                val uri: Uri = withContext(Dispatchers.IO) {
                    FileProvider.getUriForFile(
                        context,
                        "${context.packageName}.provider",
                        fileToExport
                    )
                }
                
                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    putExtra(Intent.EXTRA_SUBJECT, "KiduyuTv Log File")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }

                _exportIntent.value = Intent.createChooser(shareIntent, "Share Log File")
            } catch (e: Exception) {
                _message.value = "Failed to export log: ${e.message}"
            }
        }
    }

    /**
     * Exports a specific log file.
     */
    fun exportLogFile(file: File) {
        viewModelScope.launch {
            try {
                val context = getApplication<Application>()
                val uri: Uri = withContext(Dispatchers.IO) {
                    FileProvider.getUriForFile(
                        context,
                        "${context.packageName}.provider",
                        file
                    )
                }

                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    putExtra(Intent.EXTRA_SUBJECT, "KiduyuTv Log File: ${file.name}")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                
                _exportIntent.value = Intent.createChooser(shareIntent, "Share Log File")
            } catch (e: Exception) {
                _message.value = "Failed to export log: ${e.message}"
            }
        }
    }
    
    /**
     * Starts logcat capture.
     */
    fun startCapture(tagFilter: String? = null) {
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    LogcatManager.start(getApplication(), tagFilter)
                }
                updateCapturingState()
                _message.value = "Logcat capture started"
            } catch (e: Exception) {
                _message.value = "Failed to start capture: ${e.message}"
            }
        }
    }
    
    /**
     * Stops logcat capture.
     */
    fun stopCapture() {
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    LogcatManager.stop()
                }
                updateCapturingState()
                refreshLogFiles()
                loadLogContent()
                _message.value = "Logcat capture stopped"
            } catch (e: Exception) {
                _message.value = "Failed to stop capture: ${e.message}"
            }
        }
    }
    
    /**
     * Updates the capturing state from LogcatManager.
     */
    private fun updateCapturingState() {
        _isCapturing.value = LogcatManager.isCapturing()
    }
    
    /**
     * Clears the message after it has been shown.
     */
    fun clearMessage() {
        _message.value = null
    }
    
    /**
     * Clears the export intent after it has been handled.
     */
    fun clearExportIntent() {
        _exportIntent.value = null
    }
    
    override fun onCleared() {
        super.onCleared()
        // Don't stop LogcatManager here as it should run throughout app lifecycle
    }
}
