package com.yahyafati.mnemo.core.database.di

import com.yahyafati.mnemo.core.common.platform.AppDirectories
import com.yahyafati.mnemo.core.database.MnemoDatabase
import com.yahyafati.mnemo.core.database.desktop.build
import org.koin.core.scope.Scope

internal actual fun Scope.createDatabase(): MnemoDatabase =
    MnemoDatabase.build(get<AppDirectories>().databaseFile(MnemoDatabase.NAME))
