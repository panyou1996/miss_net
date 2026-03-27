package com.panyou.missnet.data.result

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AppResultTest {

    @Test
    fun `list helper returns empty for empty list`() {
        val result = appResultOfList(emptyList<String>())

        assertTrue(result is AppResult.Empty)
    }

    @Test
    fun `list helper returns success for non-empty list`() {
        val result = appResultOfList(listOf("a", "b"))

        assertTrue(result is AppResult.Success)
        assertEquals(listOf("a", "b"), (result as AppResult.Success).data)
    }

    @Test
    fun `map keeps failure unchanged`() {
        val failure: AppResult<List<Int>> = AppResult.Failure(message = "boom")
        val result = failure.map { values -> values.sum() }

        assertTrue(result is AppResult.Failure)
        assertEquals("boom", (result as AppResult.Failure).message)
    }

    @Test
    fun `map transforms success payload`() {
        val result = AppResult.Success(listOf(1, 2, 3)).map { values -> values.sum() }

        assertTrue(result is AppResult.Success)
        assertEquals(6, (result as AppResult.Success).data)
    }
}
