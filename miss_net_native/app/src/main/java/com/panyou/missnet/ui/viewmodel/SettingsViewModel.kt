package com.panyou.missnet.ui.viewmodel

import android.content.Context
import android.content.SharedPreferences
import android.os.Environment
import android.os.StatFs
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.gotrue.auth
import io.github.jan.supabase.gotrue.user.UserInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

data class SettingsUiState(
    val user: UserInfo? = null,
    val isLoggedIn: Boolean = false, // Mock state
    val isDarkMode: Boolean = true,
    val isIncognito: Boolean = false,
    val isAppLockEnabled: Boolean = false,
    val themeColorIndex: Int = 0, // 0:Red, 1:Blue, 2:Green, 3:Purple
    val usedStorageStr: String = "0.0 GB",
    val storageProgress: Float = 0f,
    val version: String = "1.0.0"
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val supabase: SupabaseClient,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState
    
    private val prefs: SharedPreferences = context.getSharedPreferences("missnet_prefs", Context.MODE_PRIVATE)

    init {
        refreshState()
    }

    fun refreshState() {
        viewModelScope.launch {
            val user = supabase.auth.currentUserOrNull()
            val storageInfo = calculateStorage()
            
            // Load prefs
            val isDark = prefs.getBoolean("is_dark_mode", true)
            val isIncog = prefs.getBoolean("is_incognito", false)
            val isLock = prefs.getBoolean("is_app_lock", false)
            val colorIndex = prefs.getInt("theme_color_index", 0)
            val loggedIn = prefs.getBoolean("is_logged_in", false)

            _uiState.value = _uiState.value.copy(
                user = user,
                isLoggedIn = loggedIn || (user != null),
                isDarkMode = isDark,
                isIncognito = isIncog,
                isAppLockEnabled = isLock,
                themeColorIndex = colorIndex,
                usedStorageStr = storageInfo.first,
                storageProgress = storageInfo.second
            )
        }
    }

    fun toggleDarkMode() {
        val newState = !_uiState.value.isDarkMode
        prefs.edit().putBoolean("is_dark_mode", newState).apply()
        _uiState.value = _uiState.value.copy(isDarkMode = newState)
    }

    fun toggleIncognito() {
        val newState = !_uiState.value.isIncognito
        prefs.edit().putBoolean("is_incognito", newState).apply()
        _uiState.value = _uiState.value.copy(isIncognito = newState)
    }

    fun toggleAppLock() {
        val newState = !_uiState.value.isAppLockEnabled
        prefs.edit().putBoolean("is_app_lock", newState).apply()
        _uiState.value = _uiState.value.copy(isAppLockEnabled = newState)
    }

    fun setThemeColor(index: Int) {
        prefs.edit().putInt("theme_color_index", index).apply()
        _uiState.value = _uiState.value.copy(themeColorIndex = index)
    }

    fun signIn() {
        // Mock Login
        viewModelScope.launch {
            prefs.edit().putBoolean("is_logged_in", true).apply()
            refreshState()
        }
    }

    fun logout() {
        viewModelScope.launch {
            supabase.auth.signOut()
            prefs.edit().putBoolean("is_logged_in", false).apply()
            refreshState()
        }
    }

    fun clearCache() {
        viewModelScope.launch {
            try {
                context.cacheDir.deleteRecursively()
                context.codeCacheDir.deleteRecursively()
                refreshState()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun calculateStorage(): Pair<String, Float> {
        return try {
            val path = Environment.getDataDirectory()
            val stat = StatFs(path.path)
            val blockSize = stat.blockSizeLong
            val totalBlocks = stat.blockCountLong
            val availableBlocks = stat.availableBlocksLong

            val totalBytes = totalBlocks * blockSize
            val availableBytes = availableBlocks * blockSize
            val usedBytes = totalBytes - availableBytes
            
            val usedGB = usedBytes / (1024.0 * 1024 * 1024)
            val totalGB = totalBytes / (1024.0 * 1024 * 1024)
            
            "%.1f GB / %.0f GB".format(usedGB, totalGB) to (usedGB / totalGB).toFloat()
        } catch (e: Exception) {
            "Unknown" to 0f
        }
    }
}
