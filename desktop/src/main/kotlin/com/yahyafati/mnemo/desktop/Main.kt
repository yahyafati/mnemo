package com.yahyafati.mnemo.desktop

import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.yahyafati.mnemo.core.common.platform.AppDirectories
import java.awt.GraphicsEnvironment
import javax.swing.JOptionPane
import kotlin.system.exitProcess

fun main() {
    val session = openCollection()
    if (session == null) {
        // A second Mnemo must not open the same database: say so and leave.
        val message = "Mnemo is already open. Switch to its window instead of starting it again."
        if (GraphicsEnvironment.isHeadless()) System.err.println(message) else JOptionPane.showMessageDialog(null, message, "Mnemo", JOptionPane.INFORMATION_MESSAGE)
        exitProcess(1)
    }
    application {
        Window(
            onCloseRequest = ::exitApplication,
            title = "Mnemo",
            state = rememberWindowState(size = DpSize(1100.dp, 800.dp)),
        ) {
            DesktopApp(mediaDirectory = session.koin.get<AppDirectories>().media)
        }
    }
    session.close()
}
