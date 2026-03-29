package com.kiduyuk.klausk.kiduyutv.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kiduyuk.klausk.kiduyutv.data.local.database.DatabaseManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class SettingsViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState

    /**
     * Clear all application cache including database and internal files.
     */
    fun clearCache(context: Context) {
        if (_uiState.value.isClearingCache) return

        _uiState.value = _uiState.value.copy(isClearingCache = true, cacheClearSuccess = false)

        viewModelScope.launch {
            // 1. Clear Database Cache
            DatabaseManager.clearAllCache()

            // 2. Clear File Cache (Coil, OkHttp, etc.)
            withContext(Dispatchers.IO) {
                try {
                    deleteDir(context.cacheDir)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            // Artificial delay for better UX feedback
            delay(1000)

            _uiState.value = _uiState.value.copy(
                isClearingCache = false,
                cacheClearSuccess = true
            )

            // Reset success message after a few seconds
            delay(3000)
            _uiState.value = _uiState.value.copy(cacheClearSuccess = false)
        }
    }

    private fun deleteDir(dir: File?): Boolean {
        return if (dir != null && dir.isDirectory) {
            val children = dir.list()
            if (children != null) {
                for (i in children.indices) {
                    val success = deleteDir(File(dir, children[i]))
                    if (!success) {
                        return false
                    }
                }
            }
            dir.delete()
        } else if (dir != null && dir.isFile) {
            dir.delete()
        } else {
            false
        }
    }
}

data class SettingsUiState(
    val isClearingCache: Boolean = false,
    val cacheClearSuccess: Boolean = false
)
