package com.yahyafati.mnemo.core.common.dispatchers

import javax.inject.Qualifier
import kotlin.annotation.AnnotationRetention.RUNTIME

/** Injects a [kotlinx.coroutines.CoroutineDispatcher]: `@Dispatcher(IO) ioDispatcher`. */
@Qualifier
@Retention(RUNTIME)
annotation class Dispatcher(val dispatcher: MnemoDispatchers)

enum class MnemoDispatchers {
    Default,
    IO,
}
