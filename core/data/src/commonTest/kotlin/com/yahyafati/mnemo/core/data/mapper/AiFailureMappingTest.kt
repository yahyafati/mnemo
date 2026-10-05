package com.yahyafati.mnemo.core.data.mapper

import com.yahyafati.mnemo.core.common.result.MnemoError
import com.yahyafati.mnemo.core.model.AiProblem
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import kotlin.test.Test
import kotlin.test.assertEquals

class AiFailureMappingTest {
    @Test
    fun aReadTimeoutIsATimeout() {
        val failure = MnemoError.Network(SocketTimeoutException("timeout")).toAiFailure()
        assertEquals(AiProblem.Timeout, failure.problem)
    }

    @Test
    fun aCallTimeoutIsATimeout() {
        val failure = MnemoError.Network(java.io.InterruptedIOException("timeout")).toAiFailure()
        assertEquals(AiProblem.Timeout, failure.problem)
    }

    @Test
    fun otherNetworkErrorsAreUnreachable() {
        assertEquals(AiProblem.Unreachable, MnemoError.Network(ConnectException("refused")).toAiFailure().problem)
        assertEquals(AiProblem.Unreachable, MnemoError.Network(IOException("reset")).toAiFailure().problem)
        assertEquals(AiProblem.Unreachable, MnemoError.Network().toAiFailure().problem)
    }
}
