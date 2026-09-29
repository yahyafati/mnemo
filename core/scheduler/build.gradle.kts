plugins {
    alias(libs.plugins.mnemo.jvm.library)
}

dependencies {
    // Reads the optimizer's reference data (src/test/resources/optimizer/reference.json).
    testImplementation(libs.kotlinx.serialization.json)
}
