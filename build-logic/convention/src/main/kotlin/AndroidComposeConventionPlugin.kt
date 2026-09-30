import com.android.build.api.dsl.ApplicationExtension
import com.android.build.api.dsl.LibraryExtension
import com.yahyafati.mnemo.buildlogic.configureAndroidCompose
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.getByType

/** `mnemo.android.compose`: apply after `mnemo.android.application`. */
class AndroidComposeConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            val extension = when {
                pluginManager.hasPlugin("com.android.application") ->
                    extensions.getByType<ApplicationExtension>()

                pluginManager.hasPlugin("com.android.library") ->
                    extensions.getByType<LibraryExtension>()

                else -> error("mnemo.android.compose needs an Android application or library plugin first")
            }
            configureAndroidCompose(extension)
        }
    }
}
