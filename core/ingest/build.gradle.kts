plugins {
    alias(libs.plugins.mnemo.android.library)
}

android {
    // PdfBox-Android reads its fonts and glyph lists from assets, which Robolectric only sees with this.
    testOptions.unitTests.isIncludeAndroidResources = true
}

// Smart Extract sources (ARCHITECTURE §5.2, step 1): PDF, web page and speech → plain text, and
// chunking. Only `:core:data` uses it.
dependencies {
    api(projects.core.common)
    api(projects.core.model)
    api(libs.okhttp)
    implementation(libs.jsoup)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.pdfbox.android) {
        // Only PdfBox's certificate (public-key) encryption uses BouncyCastle, and its bcpkix ships a
        // trust-all X509TrustManager that store scanners flag. Such PDFs fail as "encrypted" instead.
        exclude(group = "org.bouncycastle")
    }

    testImplementation(libs.androidx.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.okhttp.mockwebserver)
    testImplementation(libs.robolectric)
}
