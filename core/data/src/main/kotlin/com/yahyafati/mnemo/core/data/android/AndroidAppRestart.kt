package com.yahyafati.mnemo.core.data.android

import android.content.Context
import android.content.Intent

/** Restarts the app in a fresh process, so a staged restore is applied before anything opens the collection. */
fun restartAndroidApp(context: Context) {
    val launch = checkNotNull(context.packageManager.getLaunchIntentForPackage(context.packageName))
    context.startActivity(Intent.makeRestartActivityTask(launch.component))
    Runtime.getRuntime().exit(0)
}
