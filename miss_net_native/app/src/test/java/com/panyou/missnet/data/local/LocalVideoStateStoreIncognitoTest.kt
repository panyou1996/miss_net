package com.panyou.missnet.data.local

import androidx.test.core.app.ApplicationProvider
import com.panyou.missnet.data.model.Video
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class LocalVideoStateStoreIncognitoTest {

    private lateinit var store: LocalVideoStateStore
    private lateinit var context: android.content.Context
    private val json = Json { encodeDefaults = true }

    private val video = Video(
        id = "video-1",
        title = "Test Video",
        sourceUrl = "https://example.com/video.m3u8"
    )

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.deleteDatabase("missnet-local.db")
        context.getDatabasePath("missnet-local.db-wal").delete()
        context.getDatabasePath("missnet-local.db-shm").delete()
        context.getSharedPreferences("missnet_local_state", android.content.Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
        store = LocalVideoStateStore(context)
    }

    @Test
    fun incognitoModeHidesExistingHistoryAndSearchState() {
        store.addSearchHistory("abc")
        store.upsertWatchProgress(video, positionMs = 15_000L, durationMs = 60_000L)

        store.setIncognitoMode(enabled = true)

        assertEquals(emptyList<String>(), store.getSearchHistory())
        assertEquals(emptyList<WatchProgressEntry>(), store.getHistoryEntries())
        assertNull(store.getProgress(video.id))
    }

    @Test
    fun incognitoModePreventsNewHistoryWrites() {
        store.setIncognitoMode(enabled = true)

        store.addSearchHistory("abc")
        store.upsertWatchProgress(video, positionMs = 15_000L, durationMs = 60_000L)

        assertEquals(emptyList<String>(), store.getSearchHistory())
        assertEquals(emptyList<WatchProgressEntry>(), store.getHistoryEntries())
        assertNull(store.getProgress(video.id))
    }

    @Test
    fun migratesLegacySharedPreferencesIntoRoomBackedState() {
        val legacyPrefs = context.getSharedPreferences("missnet_local_state", android.content.Context.MODE_PRIVATE)
        context.deleteDatabase("missnet-local.db")
        context.getDatabasePath("missnet-local.db-wal").delete()
        context.getDatabasePath("missnet-local.db-shm").delete()
        legacyPrefs.edit()
            .putString("favorites", json.encodeToString(listOf(video)))
            .putString(
                "history",
                json.encodeToString(
                    listOf(
                        WatchProgressEntry(
                            video = video,
                            positionMs = 15_000L,
                            durationMs = 60_000L,
                            progress = 0.25f,
                            updatedAt = 1234L
                        )
                    )
                )
            )
            .putString("search_history", json.encodeToString(listOf("abc")))
            .putBoolean("room_migrated_v1", false)
            .commit()

        store = LocalVideoStateStore(context)

        assertEquals(listOf(video), store.getFavorites())
        assertEquals(listOf("abc"), store.getSearchHistory())
        assertEquals(1, store.getHistoryEntries().size)
        assertNull(legacyPrefs.getString("favorites", null))
        assertNull(legacyPrefs.getString("history", null))
        assertNull(legacyPrefs.getString("search_history", null))
    }
}
