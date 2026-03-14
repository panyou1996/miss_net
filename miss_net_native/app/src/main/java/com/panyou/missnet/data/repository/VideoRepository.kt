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
    private val homeCacheTtlMs = 10 * 60 * 1000L
    private var homeCache: TimedCache<HomePayload>? = null
    private var actorCache: TimedCache<List<ActorInfo>>? = null
    private var tagCache: TimedCache<List<String>>? = null
    private val videoCache = LinkedHashMap<String, Video>()

    suspend fun getRecentVideos(limit: Int = 20, category: String = "new", offset: Int = 0): List<Video> {
        return getRecentVideosDirect(limit, category, offset)
    }

    suspend fun getVideosByCategory(category: String, limit: Int = 20, offset: Int = 0): List<Video> {
        return try {
            rememberVideos(
                supabase.postgrest
                .rpc("get_videos_by_category", buildJsonObject {
                    put("category_text", category)
                    put("limit_count", limit)
                    put("offset_count", offset)
                })
                .decodeList<Video>()
            )
        } catch (_: Exception) {
            getRecentVideosDirect(limit, category, offset)
        }
    }

    suspend fun getVideosByActor(actor: String, limit: Int = 20, offset: Int = 0): List<Video> {
        return try {
            rememberVideos(
                supabase.postgrest
                .rpc("get_videos_by_actor", buildJsonObject {
                    put("actor_name", actor)
                    put("limit_count", limit)
                    put("offset_count", offset)
                })
                .decodeList<Video>()
            )
        } catch (_: Exception) {
            try {
                rememberVideos(supabase.postgrest["videos"].select {
                    filter {
                        eq("is_active", true)
                        contains("actors", listOf(actor))
                    }
                    order("source_release_date", Order.DESCENDING)
                    order("created_at", Order.DESCENDING)
                    range(offset.toLong(), (offset + limit - 1).toLong())
                }.decodeList<Video>())
            } catch (_: Exception) {
                emptyList()
            }
        }
    }

    suspend fun getLikedVideos(): List<Video> = getRecentVideos(limit = 10, category = "new")

    suspend fun getWatchHistory(): List<Video> = getRecentVideos(limit = 15, category = "monthly_hot")

    suspend fun getHomePayload(sectionLimit: Int = 10, weeklyLimit: Int = 15, forceRefresh: Boolean = false): HomePayload {
        if (!forceRefresh) {
            homeCache?.takeIf { it.isFresh(homeCacheTtlMs) }?.value?.let { return it }
        }
        return try {
            val rows = supabase.postgrest.rpc(
                "get_home_payload",
                buildJsonObject {
                    put("section_limit", sectionLimit)
                    put("weekly_limit", weeklyLimit)
                }
            ).decodeList<HomePayloadRow>()
            rows.toHomePayload().also {
                cacheHomePayload(it)
                homeCache = TimedCache(it)
            }
        } catch (_: Exception) {
            HomePayload(
                newVideos = getRecentVideosDirect(sectionLimit, "new"),
                monthlyVideos = getRecentVideosDirect(sectionLimit, "monthly_hot"),
                weeklyVideos = getRecentVideosDirect(weeklyLimit, "weekly_hot"),
                uncensoredVideos = getRecentVideosDirect(sectionLimit, "uncensored"),
                subtitleVideos = getRecentVideosDirect(sectionLimit, "subtitled"),
                vrVideos = getRecentVideosDirect(sectionLimit, "vr"),
                chiguaVideos = getRecentVideosDirect(sectionLimit, "51cg"),
            ).also {
                cacheHomePayload(it)
                homeCache = TimedCache(it)
            }
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

    suspend fun getActorsWithCovers(limit: Int = 20, forceRefresh: Boolean = false): List<ActorInfo> {
        if (!forceRefresh) {
            actorCache?.takeIf { it.isFresh(homeCacheTtlMs) }?.value?.takeIf { it.isNotEmpty() }?.let { return it.take(limit) }
        }
        return try {
            val primary = supabase.postgrest
                .rpc("get_actor_aggregates", buildJsonObject { put("limit_count", limit) })
                .decodeList<ActorAggregateRow>()
                .filter { isUsableCoverUrl(it.coverUrl) }
                .distinctBy { it.actor }
                .map {
                    ActorInfo(
                        name = it.actor,
                        coverUrl = it.coverUrl,
                        videoCount = it.videoCount,
                        latestReleaseDate = it.latestReleaseDate
                    )
                }
            val merged = if (primary.size >= limit) {
                primary.take(limit)
            } else {
                val fallback = getActorCoverFallback(limit * 8)
                (primary + fallback.filter { candidate -> primary.none { it.name == candidate.name } })
                    .take(limit)
            }
            actorCache = TimedCache(merged)
            merged
        } catch (_: Exception) {
            getActorCoverFallback(limit).also { actorCache = TimedCache(it) }
        }
    }

    suspend fun getPopularTags(limit: Int = 30, forceRefresh: Boolean = false): List<String> {
        if (!forceRefresh) {
            tagCache?.takeIf { it.isFresh(homeCacheTtlMs) }?.value?.takeIf { it.isNotEmpty() }?.let { return it.take(limit) }
        }
        return try {
            val primary = supabase.postgrest
                .rpc("get_tag_aggregates", buildJsonObject { put("limit_count", limit) })
                .decodeList<TagAggregateRow>()
                .mapNotNull { normalizeBrowseTag(it.tag) }
                .distinct()
            (primary + getPopularTagsFallback(limit * 10) + defaultBrowseTags())
                .distinct()
                .take(limit)
                .also { tagCache = TimedCache(it) }
        } catch (_: Exception) {
            try {
                val legacy = supabase.postgrest
                    .rpc("get_popular_tags", buildJsonObject { put("limit_count", limit) })
                    .decodeList<TagRpcResult>()
                    .mapNotNull { normalizeBrowseTag(it.tag) }
                (legacy + getPopularTagsFallback(limit * 10) + defaultBrowseTags())
                    .distinct()
                    .take(limit)
                    .also { tagCache = TimedCache(it) }
            } catch (_: Exception) {
                (getPopularTagsFallback(limit * 10) + defaultBrowseTags()).distinct().take(limit)
                    .also { tagCache = TimedCache(it) }
            }
        }
    }

    suspend fun getVideoById(id: String): Video? {
        videoCache[id]?.let { return it }
        return try {
            supabase.postgrest["videos"].select { filter { eq("id", id) } }.decodeSingleOrNull<Video>()?.also { rememberVideo(it) }
        } catch (_: Exception) {
            null
        }
    }

    suspend fun searchVideos(query: String, limit: Int = 20, offset: Int = 0): List<Video> {
        return try {
            rememberVideos(
                supabase.postgrest
                .rpc("search_videos_multi", buildJsonObject {
                    put("query_text", query)
                    put("limit_count", limit)
                    put("offset_count", offset)
                })
                .decodeList<Video>()
            )
        } catch (_: Exception) {
            searchVideosFallback(query, limit, offset)
        }
    }

    private suspend fun getRecentVideosDirect(limit: Int = 20, category: String = "new", offset: Int = 0): List<Video> {
        return try {
            rememberVideos(supabase.postgrest["videos"].select {
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
            }.decodeList<Video>())
        } catch (_: Exception) {
            emptyList()
        }
    }

    private suspend fun getActorCoverFallback(limit: Int): List<ActorInfo> {
        val recentVideos = getRecentVideosDirect(limit = 800)
        return recentVideos
            .filter { isUsableCoverUrl(it.coverUrl) && it.actors.isNotEmpty() }
            .flatMap { video ->
                video.actors
                    .map(String::trim)
                    .filter(String::isNotBlank)
                    .map { actor ->
                        actor to ActorInfo(
                            name = actor,
                            coverUrl = video.coverUrl,
                            videoCount = 1,
                            latestReleaseDate = video.sourceReleaseDate
                        )
                    }
            }
            .groupBy({ it.first }, { it.second })
            .values
            .map { items ->
                val first = items.first()
                first.copy(
                    videoCount = items.size,
                    latestReleaseDate = items.mapNotNull { it.latestReleaseDate }.maxOrNull()
                )
            }
            .sortedWith(
                compareByDescending<ActorInfo> { it.videoCount }
                    .thenByDescending { it.latestReleaseDate ?: "" }
                    .thenBy { it.name }
            )
            .take(limit)
    }

    private suspend fun getPopularTagsFallback(limit: Int): List<String> {
        return getRecentVideosDirect(limit = 800)
            .flatMap { video -> video.tags + video.categoriesForBrowseFallback() }
            .mapNotNull(::normalizeBrowseTag)
            .groupingBy { it }
            .eachCount()
            .entries
            .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
            .map { it.key }
            .take(limit)
    }

    private suspend fun searchVideosFallback(query: String, limit: Int, offset: Int): List<Video> {
        val normalized = query.trim().lowercase()
        if (normalized.isBlank()) return emptyList()
        return getRecentVideosDirect(limit = 600)
            .filter { video ->
                video.title.contains(query, ignoreCase = true) ||
                    video.actors.any { it.contains(query, ignoreCase = true) } ||
                    video.tags.any { it.contains(query, ignoreCase = true) }
            }
            .distinctBy { it.id }
            .drop(offset)
            .take(limit)
    }

    private fun cacheHomePayload(payload: HomePayload) {
        rememberVideos(
            payload.newVideos +
                payload.monthlyVideos +
                payload.weeklyVideos +
                payload.uncensoredVideos +
                payload.subtitleVideos +
                payload.vrVideos +
                payload.chiguaVideos
        )
    }

    private fun rememberVideos(videos: List<Video>): List<Video> {
        videos.forEach(::rememberVideo)
        return videos
    }

    private fun rememberVideo(video: Video) {
        if (video.id.isBlank()) return
        videoCache[video.id] = video
        if (videoCache.size > 1500) {
            val firstKey = videoCache.entries.firstOrNull()?.key ?: return
            videoCache.remove(firstKey)
        }
    }
}

private data class TimedCache<T>(
    val value: T,
    val timestampMs: Long = System.currentTimeMillis()
) {
    fun isFresh(ttlMs: Long): Boolean = System.currentTimeMillis() - timestampMs <= ttlMs
}

private fun isUsableCoverUrl(url: String?): Boolean {
    val value = url?.trim().orEmpty()
    if (value.isBlank()) return false
    val lower = value.lowercase()
    return !lower.startsWith("data:image") &&
        !lower.startsWith("blob:") &&
        !lower.startsWith("about:blank")
}

private fun normalizeBrowseTag(raw: String?): String? {
    val trimmed = raw?.trim().orEmpty()
    if (trimmed.isBlank()) return null
    val normalized = when (trimmed.lowercase()) {
        "chinese_subtitle", "subtitle", "subtitles" -> "subtitled"
        else -> trimmed
    }
    return normalized.takeIf {
        it.lowercase() !in setOf("new", "monthly_hot", "weekly_hot", "51cg", "51mrds", "uncensored", "vr")
    }
}

private fun Video.categoriesForBrowseFallback(): List<String> =
    when {
        sourceUrl.contains("uncensored", ignoreCase = true) -> listOf("uncensored")
        else -> emptyList()
    }

private fun defaultBrowseTags(): List<String> = listOf(
    "single",
    "exclusive",
    "creampie",
    "big_tits",
    "mature",
    "subtitled",
    "巨乳",
    "中出",
    "voyeur",
    "school",
)

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
    @SerialName("inventory_status") val inventoryStatus: String? = null,
    @SerialName("detail_status") val detailStatus: String? = null,
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
        tags = tags,
        inventoryStatus = inventoryStatus,
        detailStatus = detailStatus
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
