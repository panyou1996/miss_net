package com.panyou.missnet.nativeapp.core.util

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

val appJson = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
    encodeDefaults = true
}

fun Json.encodeStringMap(value: Map<String, String>): String = encodeToString(value)
