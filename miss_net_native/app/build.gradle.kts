plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("com.google.devtools.ksp")
    id("com.google.dagger.hilt.android")
}

android {
    namespace = "com.panyou.missnet"
    compileSdk = 35

    // Read signing config from CI-generated signing.properties (only present on CI)
    val ciSigningConfig = file("signing.properties").let { propsFile ->
        if (!propsFile.exists()) return@let null
        val props = java.util.Properties()
        propsFile.inputStream().use { props.load(it) }
        signingConfigs.create("ciSigning") {
            storeFile = file(props["keystore"] as String)
            storePassword = props["keystore_password"] as String
            keyAlias = props["key_alias"] as String
            keyPassword = props["key_password"] as String
        }
    }

    defaultConfig {
        applicationId = "com.panyou.missnet"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        vectorDrawables {
            useSupportLibrary = true
        }

        ndk {
            // Keep real-device APKs small enough to ship/test.
            // Drop emulator ABIs from packaged artifacts.
            abiFilters += listOf("arm64-v8a")
        }
    }

    buildTypes {
        debug {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // Use CI-generated signing key when signing.properties is present
            signingConfig = ciSigningConfig
        }

        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
    }
    composeOptions {
        // kotlinCompilerExtensionVersion is now managed by org.jetbrains.kotlin.plugin.compose plugin
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    // Core Android
    implementation("androidx.core:core-ktx:1.13.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.7.0")
    implementation("androidx.activity:activity-compose:1.9.3")

    // Jetpack Compose & Material 3
    implementation(platform("androidx.compose:compose-bom:2025.02.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")

    // Navigation
    implementation("androidx.navigation:navigation-compose:2.8.5")
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")

    // Image Loading (Coil)
    implementation("io.coil-kt:coil-compose:2.5.0")

    // Biometric
    implementation("androidx.biometric:biometric-ktx:1.2.0-alpha05")

    // Video Player (Jetpack Media3 - 1.6.0+ with Compose support)
    val media3Version = "1.6.0"
    implementation("androidx.media3:media3-exoplayer:$media3Version")
    implementation("androidx.media3:media3-exoplayer-hls:$media3Version")
    implementation("androidx.media3:media3-ui:$media3Version")
    implementation("androidx.media3:media3-ui-compose:$media3Version")
    implementation("androidx.media3:media3-session:$media3Version")
    implementation("androidx.media3:media3-datasource-okhttp:$media3Version")
    implementation("androidx.media3:media3-exoplayer-dash:$media3Version")


    // Dependency Injection (Hilt) - KSP MIGRATION
    implementation("com.google.dagger:hilt-android:2.51.1")
    ksp("com.google.dagger:hilt-android-compiler:2.51.1")
    implementation("androidx.hilt:hilt-navigation-compose:1.1.0")

    // Guava for Media3 Futures
    implementation("com.google.guava:guava:33.0.0-android")

    // Networking (Supabase & Ktor)
    implementation("io.github.jan-tennert.supabase:postgrest-kt:2.1.0")
    implementation("io.github.jan-tennert.supabase:gotrue-kt:2.1.0")
    implementation("io.ktor:ktor-client-android:2.3.7")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.0")
    implementation("com.arthenica:ffmpeg-kit-full:6.0-2")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.13")
    testImplementation("androidx.test:core-ktx:1.6.1")
    testImplementation("androidx.room:room-testing:2.6.1")
}
