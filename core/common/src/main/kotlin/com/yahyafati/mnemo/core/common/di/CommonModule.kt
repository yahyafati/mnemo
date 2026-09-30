package com.yahyafati.mnemo.core.common.di

import com.yahyafati.mnemo.core.common.dispatchers.MnemoDispatchers
import com.yahyafati.mnemo.core.common.time.Clock
import com.yahyafati.mnemo.core.common.time.SystemClock
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import org.koin.core.qualifier.named
import org.koin.core.scope.Scope
import org.koin.dsl.module

/** Dispatchers and the clock. Tests replace [Clock] with a fixed one. */
val commonModule = module {
    single<CoroutineDispatcher>(named(MnemoDispatchers.IO)) { Dispatchers.IO }
    single<CoroutineDispatcher>(named(MnemoDispatchers.Default)) { Dispatchers.Default }
    single<Clock> { SystemClock }
}

/** The dispatcher for [kind], for definitions that take one as a constructor argument. */
fun Scope.dispatcher(kind: MnemoDispatchers): CoroutineDispatcher = get(named(kind))
