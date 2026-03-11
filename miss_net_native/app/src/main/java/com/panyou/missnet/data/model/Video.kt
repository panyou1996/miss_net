package com.panyou.missnet.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Video(
    val id: String = "", 
    @SerialName("external_id") val externalId: String? = null,
    val title: String = "Unknown Title",
    @SerialName("cover_url") val coverUrl: String? = null,
    @SerialName("source_url") val sourceUrl: String = "",
    val duration: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
    val actors: List<String> = emptyList(), 
    val tags: List<String> = emptyList()
)

@Serializable
data class ActorInfo(
    val name: String,
    val coverUrl: String? = null
)