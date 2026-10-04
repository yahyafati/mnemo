plugins {
    alias(libs.plugins.mnemo.kmp.library)
}

// Smart Extract sources (ARCHITECTURE §5.2, step 1): PDF, web page and speech → plain text, and
// chunking. Only `:core:data` uses it.
kotlin {
    sourceSets {
        commonMain.dependencies {
            api(projects.core.common)
            api(projects.core.model)
            api(libs.okhttp)
            implementation(libs.jsoup)
        }
        androidMain.dependencies {
            implementation(libs.kotlinx.coroutines.android)
        }
        commonTest.dependencies {
            implementation(libs.kotlinx.coroutines.test)
            implementation(libs.okhttp.mockwebserver)
        }
        getByName("androidHostTest").dependencies {
            implementation(libs.androidx.junit)
            implementation(libs.robolectric)
        }
    }
}

dependencies {
    // Only PDF encryption with a certificate (public key) uses BouncyCastle, and its bcpkix ships a
    // trust-all X509TrustManager that store scanners flag. Such PDFs fail as "encrypted" instead
    // (ADR 0006). Excluded on the dependency, so the exclusion reaches the apps.
    "androidMainImplementation"(libs.pdfbox.android) { exclude(group = "org.bouncycastle") }
    "desktopMainImplementation"(libs.apache.pdfbox) { exclude(group = "org.bouncycastle") }
    // Registers itself with ImageIO; without it a JBIG2 scan renders as a white page (ADR 0014, "As built (P0)").
    "desktopMainImplementation"(libs.apache.pdfbox.jbig2)
}
