import com.android.build.api.dsl.ApplicationExtension
import com.yahyafati.mnemo.buildlogic.TARGET_SDK
import com.yahyafati.mnemo.buildlogic.configureKotlinAndroid
import com.yahyafati.mnemo.buildlogic.configureReleaseSigning
import com.yahyafati.mnemo.buildlogic.configureVersioning
import com.yahyafati.mnemo.buildlogic.libs
import com.yahyafati.mnemo.buildlogic.pluginId
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure

/** `mnemo.android.application`: the `:app` module. */
class AndroidApplicationConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply(libs.pluginId("android-application"))

            extensions.configure<ApplicationExtension> {
                configureKotlinAndroid(this)
                defaultConfig.targetSdk = TARGET_SDK
                configureVersioning(this)
                configureReleaseSigning(this)
            }
        }
    }
}
