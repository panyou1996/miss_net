package com.panyou.missnet.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Video(
    val id: String = "", 
    @SerialName("external_id") val externalId: String? = null,
    val title: String = "未知标题",
    @SerialName("cover_url") val coverUrl: String? = null,
    @SerialName("source_url") val sourceUrl: String = "",
    val duration: String? = null,
    @SerialName("source_release_date") val sourceReleaseDate: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
    val actors: List<String> = emptyList(), 
    val tags: List<String> = emptyList()
,
    @SerialName("inventory_status") val inventoryStatus: String? = null,
    @SerialName("detail_status") val detailStatus: String? = null
) {
    val displayDate: String?
        get() = sourceReleaseDate?.takeIf { it.isNotBlank() }
            ?: createdAt?.take(10)?.takeIf { it.isNotBlank() }

    val primaryActorOrNull: String?
        get() = actors.firstOrNull()?.takeIf { it.isNotBlank() }

    val displayDurationOrNull: String?
        get() = duration?.takeIf { it.isNotBlank() && it != "0" && it != "00:00" && it != "00:00:00" && it != "Unknown" }

    val metadataStatusLabel: String
        get() = when (inventoryStatus) {
            "detail_ready" -> "信息较完整"
            "cover_ready" -> "已收录封面"
            "indexed" -> "已索引"
            else -> when (detailStatus) {
                "success" -> "信息较完整"
                "partial" -> "信息待补全"
                else -> "待补全"
            }
        }
}

@Serializable
data class ActorInfo(
    val name: String,
    val coverUrl: String? = null,
    val videoCount: Int = 0,
    val latestReleaseDate: String? = null
)
