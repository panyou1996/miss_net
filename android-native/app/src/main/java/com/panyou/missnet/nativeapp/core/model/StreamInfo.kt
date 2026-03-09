package com.panyou.missnet.nativeapp.core.model

data class StreamInfo(
    val streamUrl: String,
    val headers: Map<String, String>,
    val mimeType: String?,
)
