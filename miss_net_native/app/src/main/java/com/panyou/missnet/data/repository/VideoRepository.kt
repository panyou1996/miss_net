package com.panyou.missnet.data.repository

import android.util.Log
import androidx.collection.LruCache
import com.panyou.missnet.data.model.ActorInfo
import com.panyou.missnet.data.model.Video
import com.panyou.missnet.data.result.AppResult
import com.panyou.missnet.data.result.appResultOf
import com.panyou.missnet.data.result.appResultOfList
import com.panyou.missnet.data.result.orEmptyList
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Order
import io.github.jan.supabase.postgrest.rpc
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import javax.inject.Inject

private const val TAG = "VideoRepository"
private const val VIDEO_CACHE_SIZE = 1500
private val SECTION_TAGS = setOf("monthly_hot", "weekly_hot", "uncensored", "subtitled", "vr", "51cg")

class VideoRepository @Inject constructor(
    private val supabase: SupabaseClient
) {
    private val homeCacheTtlMs = 10 * 60 * 1000L
    private var homeCache: TimedCache<HomePayload>? = null
    private var actorCache: TimedCache<List<ActorInfo>>? = null
    private var tagCache: TimedCache<List<String>>? = null
    // S-5: Replace LinkedHashMap with Android LruCache for true LRU semantics
    private val videoCache: LruCache<String, Video> = object : LruCache<String, Video>(VIDEO_CACHE_SIZE) {
        override fun sizeOf(key: String, video: Video): Int = 1  // 1 unit per entry
    }

    suspend fun getRecentVideos(limit: Int = 20, category: String = "new", offset: Int = 0): List<Video> {
        return getRecentVideosDirectResult(limit, category, offset).orEmptyList()
    }

    suspend fun getVideosByCategory(category: String, limit: Int = 20, offset: Int = 0): List<Video> {
        return getVideosByCategoryResult(category, limit, offset).orEmptyList()
    }

    suspend fun getVideosByActor(actor: String, limit: Int = 20, offset: Int = 0): List<Video> {
        return getVideosByActorResult(actor, limit, offset).orEmptyList()
    }

    suspend fun getLikedVideos(): List<Video> = getRecentVideos(limit = 10, category = "new")

    suspend fun getWatchHistory(): List<Video> = getRecentVideos(limit = 15, category = "monthly_hot")

    suspend fun getHomePayload(sectionLimit: Int = 10, weeklyLimit: Int = 15, forceRefresh: Boolean = false): HomePayload {
        return when (val result = getHomePayloadResult(sectionLimit, weeklyLimit, forceRefresh)) {
            AppResult.Empty -> HomePayload()
            is AppResult.Failure -> HomePayload()
            is AppResult.Success -> result.data
        }
    }

    suspend fun getPopularActors(limit: Int = 20): List<String> {
        return try {
            supabase.postgrest
                .rpc("get_popular_actors", buildJsonObject { put("limit_count", limit) })
                .decodeList<ActorRpcResult>()
                .map { it.actor }
        } catch (e: Exception) {
            Log.e(TAG, "getPopularActors failed", e)
            emptyList()
        }
    }

    suspend fun getActorsWithCovers(limit: Int = 20, forceRefresh: Boolean = false): List<ActorInfo> {
        return when (val result = getActorsWithCoversResult(limit, forceRefresh)) {
            AppResult.Empty -> emptyList()
            is AppResult.Failure -> emptyList()
            is AppResult.Success -> result.data
        }
    }

    suspend fun getActorsWithCoversResult(limit: Int = 20, forceRefresh: Boolean = false): AppResult<List<ActorInfo>> {
        if (!forceRefresh) {
            actorCache?.takeIf { it.isFresh(homeCacheTtlMs) }?.value?.takeIf { it.isNotEmpty() }?.let {
                return AppResult.Success(it.take(limit))
            }
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
            appResultOfList(merged)
        } catch (e: Exception) {
            Log.e(TAG, "getActorsWithCoversResult RPC failed, falling back", e)
            when (val fallback = getActorCoverFallbackResult(limit)) {
                AppResult.Empty -> AppResult.Empty
                is AppResult.Failure -> AppResult.Failure("演员入口加载失败，请稍后重试。", fallback.cause)
                is AppResult.Success -> fallback.data.also { actorCache = TimedCache(it) }.let { AppResult.Success(it) }
            }
        }
    }

    suspend fun getPopularTags(limit: Int = 30, forceRefresh: Boolean = false): List<String> {
        return when (val result = getPopularTagsResult(limit, forceRefresh)) {
            AppResult.Empty -> defaultBrowseTags().take(limit)
            is AppResult.Failure -> defaultBrowseTags().take(limit)
            is AppResult.Success -> if (result.data.isNotEmpty()) result.data else defaultBrowseTags().take(limit)
        }
    }

    suspend fun getPopularTagsResult(limit: Int = 30, forceRefresh: Boolean = false): AppResult<List<String>> {
        if (!forceRefresh) {
            tagCache?.takeIf { it.isFresh(homeCacheTtlMs) }?.value?.takeIf { it.isNotEmpty() }?.let {
                return AppResult.Success(it.take(limit))
            }
        }
        return try {
            val primary = supabase.postgrest
                .rpc("get_tag_aggregates", buildJsonObject { put("limit_count", limit) })
                .decodeList<TagAggregateRow>()
                .mapNotNull { normalizeBrowseTag(it.tag) }
                .distinct()
            appResultOfList(
                (primary + getPopularTagsFallback(limit * 10))
                    .distinct()
                    .take(limit)
                    .also { tagCache = TimedCache(it) }
            )
        } catch (e: Exception) {
            Log.e(TAG, "getPopularTagsResult primary RPC failed, trying legacy", e)
            try {
                val legacy = supabase.postgrest
                    .rpc("get_popular_tags", buildJsonObject { put("limit_count", limit) })
                    .decodeList<TagRpcResult>()
                    .mapNotNull { normalizeBrowseTag(it.tag) }
                appResultOfList(
                    (legacy + getPopularTagsFallback(limit * 10))
                        .distinct()
                        .take(limit)
                        .also { tagCache = TimedCache(it) }
                )
            } catch (e2: Exception) {
                Log.e(TAG, "getPopularTagsResult legacy RPC also failed, falling back to default tags", e2)
                when (val fallback = getPopularTagsFallbackResult(limit * 10)) {
                    AppResult.Empty -> AppResult.Empty
                    is AppResult.Failure -> AppResult.Failure("标签入口加载失败，请稍后重试。", fallback.cause)
                    is AppResult.Success -> {
                        val tags = fallback.data.distinct().take(limit)
                        tagCache = TimedCache(tags)
                        appResultOfList(tags)
                    }
                }
            }
        }
    }

    suspend fun getVideoById(id: String): Video? {
        return when (val result = getVideoByIdResult(id)) {
            AppResult.Empty -> null
            is AppResult.Failure -> null
            is AppResult.Success -> result.data
        }
    }

    suspend fun searchVideos(query: String, limit: Int = 20, offset: Int = 0): List<Video> {
        return searchVideosResult(query, limit, offset).orEmptyList()
    }

    suspend fun getVideosByCategoryResult(category: String, limit: Int = 20, offset: Int = 0): AppResult<List<Video>> {
        return try {
            appResultOfList(
                rememberVideos(
                    supabase.postgrest
                        .rpc("get_videos_by_category", buildJsonObject {
                            put("category_text", category)
                            put("limit_count", limit)
                            put("offset_count", offset)
                        })
                        .decodeList<Video>()
                )
            )
        } catch (e: Exception) {
            Log.e(TAG, "getVideosByCategoryResult RPC failed for category=$category", e)
            when (val fallback = getRecentVideosDirectResult(limit, category, offset)) {
                AppResult.Empty -> AppResult.Empty
                is AppResult.Failure -> AppResult.Failure("分类加载失败，请重试。", fallback.cause)
                is AppResult.Success -> fallback
            }
        }
    }

    suspend fun getVideosByActorResult(actor: String, limit: Int = 20, offset: Int = 0): AppResult<List<Video>> {
        return try {
            appResultOfList(
                rememberVideos(
                    supabase.postgrest
                        .rpc("get_videos_by_actor", buildJsonObject {
                            put("actor_name", actor)
                            put("limit_count", limit)
                            put("offset_count", offset)
                        })
                        .decodeList<Video>()
                )
            )
        } catch (e: Exception) {
            Log.e(TAG, "getVideosByActorResult RPC failed for actor=$actor, trying fallback", e)
            try {
                appResultOfList(
                    rememberVideos(
                        supabase.postgrest["videos"].select {
                            filter {
                                eq("is_active", true)
                                contains("actors", listOf(actor))
                            }
                            order("source_release_date", Order.DESCENDING)
                            order("created_at", Order.DESCENDING)
                            range(offset.toLong(), (offset + limit - 1).toLong())
                        }.decodeList<Video>()
                    )
                )
            } catch (fallbackError: Exception) {
                AppResult.Failure("演员内容加载失败，请重试。", fallbackError)
            }
        }
    }

    suspend fun getHomePayloadResult(
        sectionLimit: Int = 10,
        weeklyLimit: Int = 15,
        forceRefresh: Boolean = false
    ): AppResult<HomePayload> {
        if (!forceRefresh) {
            homeCache?.takeIf { it.isFresh(homeCacheTtlMs) }?.value?.let { cached ->
                return cached.toAppResult()
            }
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
            }.toAppResult()
        } catch (rpcError: Exception) {
            Log.e(TAG, "getHomePayloadResult RPC failed, falling back to single-query grouping", rpcError)
            // S-3 fix: single query, in-memory grouping — no more 7×800 redundant fetches
            val allVideos = when (val r = getRecentVideosDirectResult(limit = 800)) {
                is AppResult.Success -> r.data
                else -> return AppResult.Failure("首页加载失败，请稍后重试。", rpcError)
            }
            if (allVideos.isEmpty()) return AppResult.Empty

            val grouped = allVideos.groupBy { video ->
                video.tags.firstOrNull { it in SECTION_TAGS } ?: "new"
            }
            HomePayload(
                newVideos = grouped["new"].orEmpty().take(sectionLimit),
                monthlyVideos = grouped["monthly_hot"].orEmpty().take(sectionLimit),
                weeklyVideos = grouped["weekly_hot"].orEmpty().take(weeklyLimit),
                uncensoredVideos = grouped["uncensored"].orEmpty().take(sectionLimit),
                subtitleVideos = grouped["subtitled"].orEmpty().take(sectionLimit),
                vrVideos = grouped["vr"].orEmpty().take(sectionLimit),
                chiguaVideos = grouped["51cg"].orEmpty().take(sectionLimit),
            ).also {
                cacheHomePayload(it)
                homeCache = TimedCache(it)
            }.toAppResult()
        }
    }

    suspend fun getVideoByIdResult(id: String): AppResult<Video> {
        videoCache[id]?.let { return AppResult.Success(it) }
        return try {
            appResultOf(
                supabase.postgrest["videos"].select { filter { eq("id", id) } }.decodeSingleOrNull<Video>()?.also { rememberVideo(it) }
            ) { false }
        } catch (error: Exception) {
            AppResult.Failure("资源详情加载失败，请稍后重试。", error)
        }
    }

    suspend fun searchVideosResult(query: String, limit: Int = 20, offset: Int = 0): AppResult<List<Video>> {
        return try {
            appResultOfList(
                rememberVideos(
                    supabase.postgrest
                        .rpc("search_videos_multi", buildJsonObject {
                            put("query_text", query)
                            put("limit_count", limit)
                            put("offset_count", offset)
                        })
                        .decodeList<Video>()
                )
            )
        } catch (e: Exception) {
            Log.e(TAG, "searchVideosResult RPC failed for query=$query", e)
            searchVideosFallbackResult(query, limit, offset)
        }
    }

    private suspend fun getRecentVideosDirectResult(
        limit: Int = 20,
        category: String = "new",
        offset: Int = 0
    ): AppResult<List<Video>> {
        return try {
            appResultOfList(
                rememberVideos(
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
                )
            )
        } catch (error: Exception) {
            AppResult.Failure("内容加载失败，请稍后重试。", error)
        }
    }

    private suspend fun getActorCoverFallback(limit: Int): List<ActorInfo> = getActorCoverFallbackResult(limit).orEmptyList()

    private suspend fun getActorCoverFallbackResult(limit: Int): AppResult<List<ActorInfo>> {
        val recentVideos = when (val result = getRecentVideosDirectResult(limit = 800)) {
            AppResult.Empty -> return AppResult.Empty
            is AppResult.Failure -> return AppResult.Failure("演员入口加载失败，请稍后重试。", result.cause)
            is AppResult.Success -> result.data
        }
        return appResultOfList(
            recentVideos
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
        )
    }

    private suspend fun getPopularTagsFallback(limit: Int): List<String> {
        return getPopularTagsFallbackResult(limit).orEmptyList()
    }

    private suspend fun getPopularTagsFallbackResult(limit: Int): AppResult<List<String>> {
        val videos = when (val result = getRecentVideosDirectResult(limit = 800)) {
            AppResult.Empty -> return AppResult.Empty
            is AppResult.Failure -> return AppResult.Failure("标签入口加载失败，请稍后重试。", result.cause)
            is AppResult.Success -> result.data
        }
        return appResultOfList(
            videos
            .flatMap { video -> video.tags + video.categoriesForBrowseFallback() }
            .mapNotNull(::normalizeBrowseTag)
            .groupingBy { it }
            .eachCount()
            .entries
            .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
            .map { it.key }
            .take(limit)
        )
    }

    private suspend fun searchVideosFallbackResult(query: String, limit: Int, offset: Int): AppResult<List<Video>> {
        val normalized = query.trim().lowercase()
        if (normalized.isBlank()) return AppResult.Empty
        return when (val fallback = getRecentVideosDirectResult(limit = 600)) {
            AppResult.Empty -> AppResult.Empty
            is AppResult.Failure -> AppResult.Failure("搜索失败，请检查网络后重试。", fallback.cause)
            is AppResult.Success -> {
                appResultOfList(
                    fallback.data
                        .filter { video ->
                            video.title.contains(query, ignoreCase = true) ||
                                video.actors.any { it.contains(query, ignoreCase = true) } ||
                                video.tags.any { it.contains(query, ignoreCase = true) }
                        }
                        .distinctBy { it.id }
                        .drop(offset)
                        .take(limit)
                )
            }
        }
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
        videoCache.put(video.id, video)
    }
}

private fun HomePayload.toAppResult(): AppResult<HomePayload> = appResultOf(this) { payload -> payload.isEmpty() }

private fun HomePayload.isEmpty(): Boolean {
    return newVideos.isEmpty() &&
        monthlyVideos.isEmpty() &&
        weeklyVideos.isEmpty() &&
        uncensoredVideos.isEmpty() &&
        subtitleVideos.isEmpty() &&
        vrVideos.isEmpty() &&
        chiguaVideos.isEmpty()
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
