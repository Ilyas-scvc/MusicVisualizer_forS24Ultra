plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.musicedge.visualizer"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.musicedge.visualizer"
        // Android 14+ only: the target device is a Galaxy S24 Ultra, and a higher
        // minSdk removes every legacy compatibility branch from the code.
        minSdk = 34
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0-mvp"

        // The app ships no native code; the filter keeps any transitive .so out of
        // the APK for non-ARM64 ABIs the target device cannot use anyway.
        ndk { abiFilters += "arm64-v8a" }

        resourceConfigurations += listOf("en")
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
        debug {
            applicationIdSuffix = ".debug"
            isMinifyEnabled = false
        }
    }

    buildFeatures {
        compose = true
        buildConfig = false
        resValues = false
        shaders = false
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    packaging {
        resources {
            excludes += setOf(
                "/META-INF/{AL2.0,LGPL2.1}",
                "/META-INF/*.version",
                "DebugProbesKt.bin",
                "kotlin-tooling-metadata.json",
            )
        }
    }

    // No dependency metadata block in the APK: it is not needed outside Play
    // signing flows and only adds bytes.
    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }

    lint {
        warningsAsErrors = false
        abortOnError = true
    }
}

dependencies {
    // Deliberately minimal: no DI framework, no Room, no networking, no image
    // library. Everything else comes from the platform SDK.
    // Dispatchers.Main for the engine's main-thread coroutine scope.
    implementation(libs.kotlinx.coroutines.android)

    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)

    val composeBom = platform(libs.androidx.compose.bom)
    implementation(composeBom)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.material3)

    debugImplementation(composeBom)
    debugImplementation(libs.androidx.compose.ui.tooling)
    implementation(libs.androidx.compose.ui.tooling.preview)
}
