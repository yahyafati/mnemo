package com.yahyafati.mnemo.core.ui.keyboard

import androidx.compose.runtime.Stable
import androidx.compose.runtime.staticCompositionLocalOf
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow

/**
 * Asks the card browser to put the cursor in its search box (Find, desktop ROADMAP D7). The shell owns
 * one and provides it with [LocalFindRequests]; the browser collects [requests]. A feature can't call
 * another, and a shortcut works from any screen, so the ask goes through here.
 */
@Stable
class FindRequests {
    private val channel = Channel<Unit>(Channel.CONFLATED)

    val requests: Flow<Unit> = channel.receiveAsFlow()

    fun request() {
        channel.trySend(Unit)
    }
}

/** The shell's [FindRequests]; null where there is none (previews, tests). */
val LocalFindRequests = staticCompositionLocalOf<FindRequests?> { null }
