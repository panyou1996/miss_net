package com.panyou.missnet.nativeapp.core.data.remote

import android.net.Uri
import com.panyou.missnet.nativeapp.BuildConfig
import com.panyou.missnet.nativeapp.core.model.Video
import com.panyou.missnet.nativeapp.core.util.appJson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

class SupabaseVideoRemoteSource(
    private val client: OkHttpClient,
) {
    private val restBaseUrl = "${BuildConfig.SUPABASE_URL}/rest/v1/".toHttpUrl()
    private val jsonMediaType = "application/json".toMediaType()

    suspend fun getRecentVideos(
        limit: Int = 20,
        offset: Int = 0,
        category: String? = null,
        actor: String? = null,
    ): List<Video> {
        val urlBuilder = restBaseUrl.newBuilder()
            .addPathSegment("videos")
            .addQueryParameter("select", VIDEO_SELECT)
            .addQueryParameter("is_active", "eq.true")
            .addQueryParameter("order", "created_at.desc")
            .addQueryParameter("limit", limit.toString())
            .addQueryParameter("offset", offset.toString())

        if (!category.isNullOrBlank() && category != "new") {
            urlBuilder.addQueryParameter(
                "or",
                "(tags.cs.{${encodeArrayToken(category)}},categories.cs.{${encodeArrayToken(category)}})",
            )
        }

        if (!actor.isNullOrBlank()) {
            urlBuilder.addQueryParameter("actors", "cs.{${encodeArrayToken(actor)}}")
        }

        return executeList<VideoDto>(urlBuilder.build().toString()).map { it.toModel() }
    }

    suspend fun searchVideos(query: String): List<Video> {
        if (query.isBlank()) return emptyList()
        val token = encodeArrayToken(query)
        val likeToken = sanitizeLikeToken(query)
        val url = restBaseUrl.newBuilder()
            .addPathSegment("videos")
            .addQueryParameter("select", VIDEO_SELECT)
            .addQueryParameter("is_active", "eq.true")
            .addQueryParameter(
                "or",
                "(title.ilike.*$likeToken*,actors.cs.{$token},categories.cs.{$token})",
            )
            .addQueryParameter("order", "created_at.desc")
            .addQueryParameter("limit", "50")
            .build()
            .toString()
        return executeList<VideoDto>(url).map { it.toModel() }
    }

    suspend fun getSearchSuggestions(query: String): List<String> {
        if (query.isBlank()) return emptyList()
        val token = encodeArrayToken(query)
        val likeToken = sanitizeLikeToken(query)
        val url = restBaseUrl.newBuilder()
            .addPathSegment("videos")
            .addQueryParameter("select", "title,actors,categories")
            .addQueryParameter(
                "or",
                "(title.ilike.*$likeToken*,actors.cs.{$token},categories.cs.{$token})",
            )
            .addQueryParameter("limit", "15")
            .build()
            .toString()
        val rows = executeList<SuggestionDto>(url)
        val lowerQuery = query.lowercase()
        return buildSet {
            rows.forEach { row ->
                row.actors.orEmpty()
                    .filter { it.lowercase().contains(lowerQuery) }
                    .forEach(::add)
                row.categories.orEmpty()
                    .filter { it.lowercase().contains(lowerQuery) }
                    .forEach(::add)
                row.title?.takeIf { it.lowercase().contains(lowerQuery) }?.let(::add)
            }
        }.take(10)
    }

    suspend fun getPopularActors(limit: Int = 20): List<String> {
        return executePostList<ActorCountDto>("rpc/get_popular_actors", """{"limit_count":$limit}""")
            .map { it.actor }
    }

    suspend fun getPopularTags(limit: Int = 30): List<String> {
        return executePostList<TagCountDto>("rpc/get_popular_tags", """{"limit_count":$limit}""")
            .map { it.tag }
    }

    suspend fun getRelatedVideos(video: Video): List<Video> {
        val byActors = if (video.actors.isNotEmpty()) {
            val actorClause = video.actors.joinToString(",") { encodeArrayToken(it) }
            val url = restBaseUrl.newBuilder()
                .addPathSegment("videos")
                .addQueryParameter("select", VIDEO_SELECT)
                .addQueryParameter("id", "neq.${video.id}")
                .addQueryParameter("actors", "ov.{$actorClause}")
                .addQueryParameter("limit", "12")
                .build()
                .toString()
            executeList<VideoDto>(url).map { it.toModel() }
        } else {
            emptyList()
        }

        val existingIds = byActors.mapTo(linkedSetOf()) { it.id }
        val byCategories = if (existingIds.size < 6 && video.categories.isNotEmpty()) {
            val categoryClause = video.categories.joinToString(",") { encodeArrayToken(it) }
            val url = restBaseUrl.newBuilder()
                .addPathSegment("videos")
                .addQueryParameter("select", VIDEO_SELECT)
                .addQueryParameter("id", "neq.${video.id}")
                .addQueryParameter("categories", "ov.{$categoryClause}")
                .addQueryParameter("limit", "12")
                .build()
                .toString()
            executeList<VideoDto>(url).map { it.toModel() }
        } else {
            emptyList()
        }

        return (byActors + byCategories).distinctBy { it.id }.take(12)
    }

    private suspend inline fun <reified T> executeList(url: String): List<T> = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(url)
            .header("apikey", BuildConfig.SUPABASE_ANON_KEY)
            .header("Authorization", "Bearer ${BuildConfig.SUPABASE_ANON_KEY}")
            .header("Accept", "application/json")
            .get()
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error("Supabase request failed: ${response.code}")
            val body = response.body?.string().orEmpty()
            appJson.decodeFromString(body)
        }
    }

    private suspend inline fun <reified T> executePostList(path: String, body: String): List<T> = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(restBaseUrl.newBuilder().addEncodedPathSegments(path).build())
            .header("apikey", BuildConfig.SUPABASE_ANON_KEY)
            .header("Authorization", "Bearer ${BuildConfig.SUPABASE_ANON_KEY}")
            .header("Accept", "application/json")
            .post(body.toRequestBody(jsonMediaType))
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error("Supabase RPC failed: ${response.code}")
            val responseBody = response.body?.string().orEmpty()
            appJson.decodeFromString(responseBody)
        }
    }

    private fun encodeArrayToken(value: String): String = value.replace(",", " ")

    private fun sanitizeLikeToken(value: String): String = Uri.encode(value).replace("%2A", "")

    @Serializable
    private data class SuggestionDto(
        val title: String? = null,
        val actors: List<String>? = null,
        val categories: List<String>? = null,
    )

    @Serializable
    private data class TagCountDto(
        val tag: String,
    )

    @Serializable
    private data class ActorCountDto(
        val actor: String,
    )

    @Serializable
    private data class VideoDto(
        val id: String,
        @SerialName("external_id") val externalId: String,
        val title: String,
        @SerialName("cover_url") val coverUrl: String? = null,
        @SerialName("source_url") val sourceUrl: String,
        @SerialName("created_at") val createdAt: String,
        val duration: String? = null,
        @SerialName("release_date") val releaseDate: String? = null,
        val actors: List<String>? = null,
        val categories: List<String>? = null,
    ) {
        fun toModel(): Video = Video(
            id = id,
            externalId = externalId,
            title = title,
            coverUrl = coverUrl,
            sourceUrl = sourceUrl,
            createdAtEpochMs = runCatching { java.time.OffsetDateTime.parse(createdAt).toInstant().toEpochMilli() }.getOrDefault(0L),
            duration = duration,
            releaseDate = releaseDate,
            actors = actors.orEmpty(),
            categories = categories.orEmpty(),
        )
    }

    private companion object {
        const val VIDEO_SELECT = "id,external_id,title,cover_url,source_url,created_at,duration,release_date,actors,categories"
    }
}
