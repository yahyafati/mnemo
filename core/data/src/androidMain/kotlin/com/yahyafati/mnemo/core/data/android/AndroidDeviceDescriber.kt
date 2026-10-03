package com.yahyafati.mnemo.core.data.android

import android.content.Context
import android.os.Build
import com.yahyafati.mnemo.core.data.sync.DeviceDescriber
import com.yahyafati.mnemo.core.data.sync.DeviceDescription

/** This phone as the other devices list it: its model and the app's version name. */
internal class AndroidDeviceDescriber(private val context: Context) : DeviceDescriber {
    override fun describe(): DeviceDescription {
        val version = try {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        } catch (_: Exception) {
            null
        }
        val model = listOf(Build.MANUFACTURER, Build.MODEL).filter { !it.isNullOrBlank() }.joinToString(" ").ifBlank { "Android" }
        return DeviceDescription(name = model, platform = "android", appVersion = version ?: "unknown")
    }
}
