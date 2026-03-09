package com.panyou.missnet.nativeapp.core.media

import android.content.Context
import com.panyou.missnet.nativeapp.BuildConfig
import com.panyou.missnet.nativeapp.core.util.appJson
import kotlinx.serialization.encodeToString

class MediaHeaderStore(context: Context) {
    private val prefs = context.getSharedPreferences("media_headers", Context.MODE_PRIVATE)
    @Volatile
    private var currentHeaders: Map<String, String> = loadHeaders()

    fun update(headers: Map<String, String>) {
        val normalized = headers.filterValues { it.isNotBlank() }
            .toMutableMap()
            .apply {
                putIfAbsent("User-Agent", DEFAULT_USER_AGENT)
                putIfAbsent("Referer", BuildConfig.DEFAULT_SOURCE_REFERER)
            }
        currentHeaders = normalized
        prefs.edit().putString(KEY_HEADERS, appJson.encodeToString(normalized)).apply()
    }

    fun snapshot(): Map<String, String> = currentHeaders.ifEmpty {
        mapOf(
            "User-Agent" to DEFAULT_USER_AGENT,
            "Referer" to BuildConfig.DEFAULT_SOURCE_REFERER,
        )
    }

    private fun loadHeaders(): Map<String, String> {
        val raw = prefs.getString(KEY_HEADERS, null).orEmpty()
        if (raw.isBlank()) {
            return mapOf(
                "User-Agent" to DEFAULT_USER_AGENT,
                "Referer" to BuildConfig.DEFAULT_SOURCE_REFERER,
            )
        }
        return runCatching { appJson.decodeFromString<Map<String, String>>(raw) }
            .getOrDefault(emptyMap())
    }

    private companion object {
        const val KEY_HEADERS = "headers_json"
        const val DEFAULT_USER_AGENT =
            "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
    }
}
