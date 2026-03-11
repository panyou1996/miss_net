package com.panyou.missnet.data.repository

import android.util.Log
import com.panyou.missnet.data.model.Video
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Order
import io.github.jan.supabase.postgrest.rpc
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import javax.inject.Inject

class VideoRepository @Inject constructor(
    private val supabase: SupabaseClient
) {
    suspend fun getRecentVideos(limit: Int = 20, category: String = "new", offset: Int = 0): List<Video> {
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
                order("created_at", Order.DESCENDING)
                range(offset.toLong(), (offset + limit - 1).toLong())
            }.decodeList<Video>()
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun getVideosByCategory(category: String, limit: Int = 20, offset: Int = 0): List<Video> = 
        getRecentVideos(limit, category, offset)

    suspend fun getVideosByActor(actor: String, limit: Int = 20, offset: Int = 0): List<Video> {
        return try {
            supabase.postgrest["videos"].select {
                filter {
                    eq("is_active", true)
                    contains("actors", listOf(actor))
                }
                order("created_at", Order.DESCENDING)
                range(offset.toLong(), (offset + limit - 1).toLong())
            }.decodeList<Video>()
        } catch (e: Exception) {
            emptyList()
        }
    }

    // New: Mock Likes and History
    suspend fun getLikedVideos(): List<Video> = getRecentVideos(limit = 10, category = "new")
    suspend fun getWatchHistory(): List<Video> = getRecentVideos(limit = 15, category = "monthly_hot")

    suspend fun getPopularActors(limit: Int = 20): List<String> {
        return try {
            val response = supabase.postgrest.rpc("get_popular_actors", buildJsonObject { put("limit_count", limit) })
            response.decodeList<ActorRpcResult>().map { it.actor }
        } catch (e: Exception) { emptyList() }
    }

    suspend fun getActorsWithCovers(limit: Int = 20): List<com.panyou.missnet.data.model.ActorInfo> {
        return try {
            val names = getPopularActors(limit)
            // Fetch recent videos to extract covers
            val recentVideos = getRecentVideos(limit = 100)
            
            names.map { name ->
                val cover = recentVideos.firstOrNull { it.actors.contains(name) }?.coverUrl
                com.panyou.missnet.data.model.ActorInfo(name, cover)
            }
        } catch (e: Exception) { emptyList() }
    }

    suspend fun getPopularTags(): List<String> {
        return try {
            val response = supabase.postgrest.rpc("get_popular_tags", buildJsonObject { put("limit_count", 30) })
            response.decodeList<TagRpcResult>().map { it.tag }
        } catch (e: Exception) { emptyList() }
    }

    suspend fun getVideoById(id: String): Video? {
        return try {
            supabase.postgrest["videos"].select { filter { eq("id", id) } }.decodeSingleOrNull<Video>()
        } catch (e: Exception) { null }
    }

    suspend fun searchVideos(query: String, limit: Int = 20): List<Video> {
        return try {
            supabase.postgrest["videos"].select {
                filter {
                    eq("is_active", true)
                    ilike("title", "%$query%")
                }
                order("created_at", Order.DESCENDING)
                limit(limit.toLong())
            }.decodeList<Video>()
        } catch (e: Exception) { emptyList() }
    }
}

@kotlinx.serialization.Serializable
data class ActorRpcResult(val actor: String)

@kotlinx.serialization.Serializable
data class TagRpcResult(val tag: String)
