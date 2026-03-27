package com.panyou.missnet.data.local

import androidx.test.core.app.ApplicationProvider
import com.panyou.missnet.data.model.Video
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class LocalVideoStateStoreIncognitoTest {

    private lateinit var store: LocalVideoStateStore

    private val video = Video(
        id = "video-1",
        title = "Test Video",
        sourceUrl = "https://example.com/video.m3u8"
    )

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
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
}
