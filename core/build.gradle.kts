plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.iosuite.core"
    compileSdk = 36

    defaultConfig {
        minSdk = 31
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true }
}

dependencies {
    api(platform("androidx.compose:compose-bom:2025.02.00"))
    api("androidx.compose.animation:animation-core")
    api("androidx.compose.ui:ui")
    api("androidx.compose.foundation:foundation")
    api("androidx.core:core-ktx:1.15.0")
    api("androidx.core:core-splashscreen:1.0.1")
    implementation("androidx.metrics:metrics-performance:1.0.0-beta01")
}
