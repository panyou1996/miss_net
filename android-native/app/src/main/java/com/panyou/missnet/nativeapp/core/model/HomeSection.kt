package com.panyou.missnet.nativeapp.core.model

@JvmInline
value class FeedCategory(val value: String)

data class HomeSection(
    val title: String,
    val category: FeedCategory,
    val videos: List<Video>,
)
