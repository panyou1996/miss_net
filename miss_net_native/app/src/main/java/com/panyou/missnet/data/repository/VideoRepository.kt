package com.panyou.missnet.data.repository

import com.panyou.missnet.data.model.ActorInfo
import com.panyou.missnet.data.model.Video
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Order
import io.github.jan.supabase.postgrest.rpc
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import javax.inject.Inject

class VideoRepository @Inject constructor(
    private val supabase: SupabaseClient
) {
    suspend fun getRecentVideos(limit: Int = 20, category: String = "new", offset: Int = 0): List<Video> {
        return getRecentVideosDirect(limit, category, offset)
    }

    suspend fun getVideosByCategory(category: String, limit: Int = 20, offset: Int = 0): List<Video> {
        return try {
            supabase.postgrest
                .rpc("get_videos_by_category", buildJsonObject {
                    put("category_text", category)
                    put("limit_count", limit)
                    put("offset_count", offset)
                })
                .decodeList<Video>()
        } catch (_: Exception) {
            getRecentVideosDirect(limit, category, offset)
        }
    }

    suspend fun getVideosByActor(actor: String, limit: Int = 20, offset: Int = 0): List<Video> {
        return try {
            supabase.postgrest
                .rpc("get_videos_by_actor", buildJsonObject {
                    put("actor_name", actor)
                    put("limit_count", limit)
                    put("offset_count", offset)
                })
                .decodeList<Video>()
        } catch (_: Exception) {
            try {
                supabase.postgrest["videos"].select {
                    filter {
                        eq("is_active", true)
                        contains("actors", listOf(actor))
                    }
                    order("source_release_date", Order.DESCENDING)
                    order("created_at", Order.DESCENDING)
                    range(offset.toLong(), (offset + limit - 1).toLong())
                }.decodeList<Video>()
            } catch (_: Exception) {
                emptyList()
            }
        }
    }

    suspend fun getLikedVideos(): List<Video> = getRecentVideos(limit = 10, category = "new")

    suspend fun getWatchHistory(): List<Video> = getRecentVideos(limit = 15, category = "monthly_hot")

    suspend fun getHomePayload(sectionLimit: Int = 10, weeklyLimit: Int = 15): HomePayload {
        return try {
            val rows = supabase.postgrest.rpc(
                "get_home_payload",
                buildJsonObject {
                    put("section_limit", sectionLimit)
                    put("weekly_limit", weeklyLimit)
                }
            ).decodeList<HomePayloadRow>()
            rows.toHomePayload()
        } catch (_: Exception) {
            HomePayload(
                newVideos = getRecentVideosDirect(sectionLimit, "new"),
                monthlyVideos = getRecentVideosDirect(sectionLimit, "monthly_hot"),
                weeklyVideos = getRecentVideosDirect(weeklyLimit, "weekly_hot"),
                uncensoredVideos = getRecentVideosDirect(sectionLimit, "uncensored"),
                subtitleVideos = getRecentVideosDirect(sectionLimit, "subtitled"),
                vrVideos = getRecentVideosDirect(sectionLimit, "vr"),
                chiguaVideos = getRecentVideosDirect(sectionLimit, "51cg"),
            )
        }
    }

    suspend fun getPopularActors(limit: Int = 20): List<String> {
        return try {
            supabase.postgrest
                .rpc("get_popular_actors", buildJsonObject { put("limit_count", limit) })
                .decodeList<ActorRpcResult>()
                .map { it.actor }
        } catch (_: Exception) {
            emptyList()
        }
    }

    suspend fun getActorsWithCovers(limit: Int = 20): List<ActorInfo> {
        return try {
            supabase.postgrest
                .rpc("get_actor_aggregates", buildJsonObject { put("limit_count", limit) })
                .decodeList<ActorAggregateRow>()
                .map {
                    ActorInfo(
                        name = it.actor,
                        coverUrl = it.coverUrl,
                        videoCount = it.videoCount,
                        latestReleaseDate = it.latestReleaseDate
                    )
                }
        } catch (_: Exception) {
            val names = getPopularActors(limit)
            val recentVideos = getRecentVideosDirect(limit = 100)
            names.map { name ->
                ActorInfo(
                    name = name,
                    coverUrl = recentVideos.firstOrNull { it.actors.contains(name) }?.coverUrl,
                )
            }
        }
    }

    suspend fun getPopularTags(limit: Int = 30): List<String> {
        return try {
            supabase.postgrest
                .rpc("get_tag_aggregates", buildJsonObject { put("limit_count", limit) })
                .decodeList<TagAggregateRow>()
                .map { it.tag }
        } catch (_: Exception) {
            try {
                supabase.postgrest
                    .rpc("get_popular_tags", buildJsonObject { put("limit_count", limit) })
                    .decodeList<TagRpcResult>()
                    .map { it.tag }
            } catch (_: Exception) {
                emptyList()
            }
        }
    }

    suspend fun getVideoById(id: String): Video? {
        return try {
            supabase.postgrest["videos"].select { filter { eq("id", id) } }.decodeSingleOrNull<Video>()
        } catch (_: Exception) {
            null
        }
    }

    suspend fun searchVideos(query: String, limit: Int = 20): List<Video> {
        return try {
            supabase.postgrest
                .rpc("search_videos_title", buildJsonObject {
                    put("query_text", query)
                    put("limit_count", limit)
                })
                .decodeList<Video>()
        } catch (_: Exception) {
            try {
                supabase.postgrest["videos"].select {
                    filter {
                        eq("is_active", true)
                        ilike("title", "%$query%")
                    }
                    order("source_release_date", Order.DESCENDING)
                    order("created_at", Order.DESCENDING)
                    limit(limit.toLong())
                }.decodeList<Video>()
            } catch (_: Exception) {
                emptyList()
            }
        }
    }

    private suspend fun getRecentVideosDirect(limit: Int = 20, category: String = "new", offset: Int = 0): List<Video> {
        return try {
            supabase.postgrest["videos"].select {
                filter {
                    eq("is_active", true)
                    if (category != "new" && category.isNotEmpty()) {
                        or {
                            contains("tags", listOf(category))
                            contains("categories", listOf(category))
                        }
                    }
                }
                order("source_release_date", Order.DESCENDING)
                order("created_at", Order.DESCENDING)
                range(offset.toLong(), (offset + limit - 1).toLong())
            }.decodeList<Video>()
        } catch (_: Exception) {
            emptyList()
        }
    }
}

data class HomePayload(
    val newVideos: List<Video> = emptyList(),
    val monthlyVideos: List<Video> = emptyList(),
    val weeklyVideos: List<Video> = emptyList(),
    val uncensoredVideos: List<Video> = emptyList(),
    val subtitleVideos: List<Video> = emptyList(),
    val vrVideos: List<Video> = emptyList(),
    val chiguaVideos: List<Video> = emptyList(),
)

@Serializable
private data class HomePayloadRow(
    val section: String,
    val id: String = "",
    @SerialName("external_id") val externalId: String? = null,
    val title: String = "未知标题",
    @SerialName("cover_url") val coverUrl: String? = null,
    @SerialName("source_url") val sourceUrl: String = "",
    val duration: String? = null,
    @SerialName("source_release_date") val sourceReleaseDate: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
    val actors: List<String> = emptyList(),
    val tags: List<String> = emptyList(),
) {
    fun toVideo(): Video = Video(
        id = id,
        externalId = externalId,
        title = title,
        coverUrl = coverUrl,
        sourceUrl = sourceUrl,
        duration = duration,
        sourceReleaseDate = sourceReleaseDate,
        createdAt = createdAt,
        actors = actors,
        tags = tags
    )
}

private fun List<HomePayloadRow>.toHomePayload(): HomePayload {
    val grouped = groupBy { it.section }.mapValues { entry -> entry.value.map { it.toVideo() } }
    return HomePayload(
        newVideos = grouped["new"].orEmpty(),
        monthlyVideos = grouped["monthly_hot"].orEmpty(),
        weeklyVideos = grouped["weekly_hot"].orEmpty(),
        uncensoredVideos = grouped["uncensored"].orEmpty(),
        subtitleVideos = grouped["subtitled"].orEmpty(),
        vrVideos = grouped["vr"].orEmpty(),
        chiguaVideos = grouped["51cg"].orEmpty(),
    )
}

@Serializable
data class ActorRpcResult(val actor: String)

@Serializable
data class TagRpcResult(val tag: String)

@Serializable
data class ActorAggregateRow(
    val actor: String,
    @SerialName("cover_url") val coverUrl: String? = null,
    @SerialName("video_count") val videoCount: Int = 0,
    @SerialName("latest_release_date") val latestReleaseDate: String? = null,
)

@Serializable
data class TagAggregateRow(
    val tag: String,
    @SerialName("video_count") val videoCount: Int = 0,
    @SerialName("latest_release_date") val latestReleaseDate: String? = null,
)
