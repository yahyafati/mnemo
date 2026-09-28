import com.android.build.api.dsl.LibraryExtension
import com.yahyafati.mnemo.buildlogic.configureKotlinAndroid
import com.yahyafati.mnemo.buildlogic.libs
import com.yahyafati.mnemo.buildlogic.library
import com.yahyafati.mnemo.buildlogic.mnemoNamespace
import com.yahyafati.mnemo.buildlogic.pluginId
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies

/** `mnemo.android.library`: every Android library module. The namespace follows the module path. */
class AndroidLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply(libs.pluginId("android-library"))

            extensions.configure<LibraryExtension> {
                configureKotlinAndroid(this)
                namespace = mnemoNamespace
                defaultConfig.testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
            }

            dependencies {
                add("testImplementation", libs.library("junit"))
                add("testImplementation", libs.library("kotlin-test"))
                add("androidTestImplementation", libs.library("androidx-junit"))
                add("androidTestImplementation", libs.library("androidx-test-runner"))
            }
        }
    }
}
