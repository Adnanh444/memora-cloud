plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
}

// Telegram API credentials come from GitHub Secrets at build time. They are NEVER stored in the repository.
val tgApiId = System.getenv("TELEGRAM_API_ID")?.trim()?.toIntOrNull() ?: 0
val tgApiHash = System.getenv("TELEGRAM_API_HASH")?.trim()?.filter { it.isLetterOrDigit() } ?: ""

android {
    namespace = "com.adnan.memora"
    compileSdk = 34
    defaultConfig {
        applicationId = "com.adnan.memora"
        minSdk = 26
        targetSdk = 34
        versionCode = 10
        versionName = "0.5.1"
        // Keep only the languages the app supports (strips unused library translations).
        resourceConfigurations += listOf("en", "bn")
        buildConfigField("int", "TELEGRAM_API_ID", tgApiId.toString())
        buildConfigField("String", "TELEGRAM_API_HASH", "\"$tgApiHash\"")
        // TDLib is large; ship only 64-bit ARM (all phones since ~2016) to keep the APK smaller.
        ndk { abiFilters += listOf("arm64-v8a") }
    }
    signingConfigs {
        create("release") {
            val path = System.getenv("KEYSTORE_FILE")
            if (!path.isNullOrBlank() && File(path).exists()) {
                storeFile = File(path)
                storePassword = System.getenv("KEYSTORE_PASSWORD")
                keyAlias = System.getenv("KEY_ALIAS")
                keyPassword = System.getenv("KEY_PASSWORD")
            }
        }
    }
    buildTypes {
        release {
            isMinifyEnabled = true      // R8: removes unused code (biggest size win)
            isShrinkResources = true    // removes unused resources
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // Signed with the permanent private key from GitHub Secrets when available.
            // (Falls back to the debug key only if the secrets are missing.)
            signingConfig = if (System.getenv("KEYSTORE_FILE").isNullOrBlank())
                signingConfigs.getByName("debug") else signingConfigs.getByName("release")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true; buildConfig = true }
    packaging {
        resources {
            excludes += setOf(
                "/META-INF/{AL2.0,LGPL2.1}", "META-INF/*.version",
                "DebugProbesKt.bin", "kotlin-tooling-metadata.json",
            )
        }
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.09.00")
    implementation(composeBom)
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.navigation:navigation-compose:2.8.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.6")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.6")
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("io.coil-kt:coil-compose:2.7.0")
    implementation("io.coil-kt:coil-video:2.7.0")
    implementation("androidx.exifinterface:exifinterface:1.3.7")
    implementation("androidx.work:work-runtime-ktx:2.9.1")
    // Community-maintained prebuilt TDLib (Telegram client library). Pinned to an exact version.
    implementation("io.github.tdlib-android:core:0.1.1")

    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")
    // WorkManager is added back in Phase 4 (backup engine); leaving it out keeps the APK smaller now.
}
