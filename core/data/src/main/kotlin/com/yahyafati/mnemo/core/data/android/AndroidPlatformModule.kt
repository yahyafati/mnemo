package com.yahyafati.mnemo.core.data.android

import android.content.Context
import com.yahyafati.mnemo.core.common.platform.AppDirectories
import com.yahyafati.mnemo.core.common.platform.DocumentAccess
import org.koin.dsl.module

/**
 * What only the platform can provide: where files live and how the user's files are opened. This
 * is the Android side; the desktop app binds the same two interfaces to plain paths (desktop D4).
 * Needs an `androidContext`.
 */
val platformModule = module {
    single<AppDirectories> { AndroidAppDirectories(get<Context>()) }
    single<DocumentAccess> { AndroidDocumentAccess(get<Context>()) }
}
