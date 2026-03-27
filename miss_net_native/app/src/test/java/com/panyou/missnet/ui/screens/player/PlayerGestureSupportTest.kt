package com.panyou.missnet.ui.screens.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PlayerGestureSupportTest {

    @Test
    fun `double tap on left third seeks backward`() {
        assertEquals(DoubleTapSeekAction.Backward, resolveDoubleTapSeekAction(tapX = 40f, width = 300f))
    }

    @Test
    fun `double tap on right third seeks forward`() {
        assertEquals(DoubleTapSeekAction.Forward, resolveDoubleTapSeekAction(tapX = 260f, width = 300f))
    }

    @Test
    fun `double tap near center does not trigger seek`() {
        assertNull(resolveDoubleTapSeekAction(tapX = 150f, width = 300f))
    }
}
