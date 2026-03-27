package com.panyou.missnet.ui.theme

import org.junit.Assert.assertEquals
import org.junit.Test

class MediaSurfaceTokensTest {

    @Test
    fun `video shared transition key is stable`() {
        assertEquals("image-abc123", videoSharedTransitionKey("abc123"))
    }
}
