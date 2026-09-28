package com.yahyafati.mnemo.core.common.result

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class MnemoResultTest {
    @Test
    fun `map transforms success`() {
        val result: MnemoResult<Int> = MnemoResult.Success(2)
        assertEquals(MnemoResult.Success(4), result.map { it * 2 })
    }

    @Test
    fun `map keeps failure`() {
        val failure = MnemoResult.Failure(MnemoError.NotFound("deck"))
        val result: MnemoResult<Int> = failure
        assertEquals(failure, result.map { it * 2 })
        assertNull(result.getOrNull())
    }

    @Test
    fun `callbacks run only for their branch`() {
        var seen = ""
        MnemoResult.Success("ok").onSuccess { seen += it }.onFailure { seen += "fail" }
        MnemoResult.Failure(MnemoError.Network()).onSuccess { seen += "no" }.onFailure { seen += "!" }
        assertEquals("ok!", seen)
    }
}
