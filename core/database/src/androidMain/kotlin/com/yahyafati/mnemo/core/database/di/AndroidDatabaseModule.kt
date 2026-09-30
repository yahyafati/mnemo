package com.yahyafati.mnemo.core.database.di

import android.content.Context
import com.yahyafati.mnemo.core.database.MnemoDatabase
import com.yahyafati.mnemo.core.database.android.build
import org.koin.core.scope.Scope

internal actual fun Scope.createDatabase(): MnemoDatabase = MnemoDatabase.build(get<Context>())
