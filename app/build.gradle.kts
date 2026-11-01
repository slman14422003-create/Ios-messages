plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("kotlin-parcelize")
}

android {
    namespace = "app.ios.messages"
    compileSdk = 36

    defaultConfig {
        applicationId = "app.ios.messages"
        minSdk = 31
        targetSdk = 36
        versionCode = 22
        versionName = "3.2"
        // SHA-256 of the release signing certificate. At start-up the app compares it with the
        // certificate it is actually signed with; a re-signed / re-packaged copy locks itself.
        // If you ever change release.jks, update this value (keytool -list -v -keystore release.jks).
        buildConfigField("String", "CERT_SHA256", "\"C16DDD8ADAFDEC27F547A8126F22183F2E1D1E3E76439DCABCB2A71B96EF938A\"")
    }

    signingConfigs {
        create("release") {
            storeFile = file("release.jks")
            // Prefer environment variables so the passwords need not live in a public repository.
            storePassword = System.getenv("IMSG_STORE_PASSWORD") ?: "iosmessages"
            keyAlias = "ios"
            keyPassword = System.getenv("IMSG_KEY_PASSWORD") ?: "iosmessages"
        }
    }
    buildTypes {
        release {
            isDebuggable = false
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("release")
        }
        debug {
            signingConfig = signingConfigs.getByName("release")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    lint {
        checkReleaseBuilds = false
        abortOnError = false
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    composeOptions { kotlinCompilerExtensionVersion = "1.5.14" }
    packaging { resources.excludes += "/META-INF/{AL2.0,LGPL2.1}" }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.06.00"))
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.7.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.7.0")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.profileinstaller:profileinstaller:1.3.1")
}
