package com.yahyafati.mnemo.core.data.desktop

import com.yahyafati.mnemo.core.data.sync.DeviceDescriber
import com.yahyafati.mnemo.core.data.sync.DeviceDescription
import com.yahyafati.mnemo.core.data.sync.SyncBackgroundWork
import com.yahyafati.mnemo.core.data.sync.SyncRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.net.InetAddress
import java.time.Duration

/** The periodic sync of the desktop: a timer in the application scope, since there is no job system behind the app. */
internal class DesktopSyncBackgroundWork(
    private val scope: CoroutineScope,
    private val repository: Lazy<SyncRepository>,
    private val interval: Duration = Duration.ofHours(1),
) : SyncBackgroundWork {
    private var job: Job? = null

    @Synchronized
    override fun schedule() {
        if (job?.isActive == true) return
        job = scope.launch {
            while (true) {
                delay(interval.toMillis())
                try {
                    repository.value.syncNow()
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    // The status says what went wrong.
                }
            }
        }
    }

    @Synchronized
    override fun cancel() {
        job?.cancel()
        job = null
    }
}

/** This computer as the other devices list it: its host name and system, and the app's version if the launcher knows it. */
class DesktopDeviceDescriber(private val appVersion: String = "") : DeviceDescriber {
    override fun describe(): DeviceDescription {
        val host = try {
            InetAddress.getLocalHost().hostName
        } catch (_: Exception) {
            null
        }
        val system = System.getProperty("os.name").orEmpty()
        return DeviceDescription(
            name = host?.takeIf { it.isNotBlank() } ?: system.ifBlank { "Computer" },
            platform = system.lowercase().let {
                when {
                    "mac" in it -> "macos"
                    "win" in it -> "windows"
                    else -> "linux"
                }
            },
            appVersion = appVersion.ifBlank { "unknown" },
        )
    }
}
