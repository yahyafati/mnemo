package com.yahyafati.mnemo.core.common.dispatchers

/** Which [kotlinx.coroutines.CoroutineDispatcher] to inject: `dispatcher(MnemoDispatchers.IO)` in a Koin definition. */
enum class MnemoDispatchers {
    Default,
    IO,
}
