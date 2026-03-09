package com.panyou.missnet.nativeapp.core.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import com.panyou.missnet.nativeapp.core.model.AppSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore by preferencesDataStore(name = "missnet_settings")

class SettingsRepository(private val context: Context) {
    private val dynamicColorKey = booleanPreferencesKey("dynamic_color")
    private val autoplayRelatedKey = booleanPreferencesKey("autoplay_related")
    private val incognitoModeKey = booleanPreferencesKey("incognito_mode")
    private val preferWifiDownloadsKey = booleanPreferencesKey("prefer_wifi_downloads")
    private val keepScreenAwakeKey = booleanPreferencesKey("keep_screen_awake")

    val settings: Flow<AppSettings> = context.settingsDataStore.data.map { prefs ->
        AppSettings(
            useDynamicColor = prefs[dynamicColorKey] ?: true,
            autoplayRelated = prefs[autoplayRelatedKey] ?: false,
            incognitoMode = prefs[incognitoModeKey] ?: false,
            preferWifiDownloads = prefs[preferWifiDownloadsKey] ?: true,
            keepScreenAwakeInPlayer = prefs[keepScreenAwakeKey] ?: true,
        )
    }

    suspend fun setUseDynamicColor(enabled: Boolean) {
        context.settingsDataStore.edit { it[dynamicColorKey] = enabled }
    }

    suspend fun setAutoplayRelated(enabled: Boolean) {
        context.settingsDataStore.edit { it[autoplayRelatedKey] = enabled }
    }

    suspend fun setIncognitoMode(enabled: Boolean) {
        context.settingsDataStore.edit { it[incognitoModeKey] = enabled }
    }

    suspend fun setPreferWifiDownloads(enabled: Boolean) {
        context.settingsDataStore.edit { it[preferWifiDownloadsKey] = enabled }
    }

    suspend fun setKeepScreenAwake(enabled: Boolean) {
        context.settingsDataStore.edit { it[keepScreenAwakeKey] = enabled }
    }
}
