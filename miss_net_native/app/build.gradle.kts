import java.io.File
import java.util.*

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

    val KEYSTORE_FILE = file("debug.keystore")
    val KEYSTORE_PASSWORD = "MissNet2026"
    val KEY_ALIAS = "missnet-debug"
    val KEY_PASSWORD = "MissNet2026"

    // ── Auto-generate debug keystore at build time if missing ──
    // Each CI run produces a unique signature, so no conflict with any previously installed APK.
    // This sidesteps the need to commit or manage keystores in the repo.
    val generateKeystoreTask = tasks.register<Exec>("generateDebugKeystore") {
        group = "build"
        description = "Generates debug.keystore if not present"
        onlyIf { !KEYSTORE_FILE.exists() }
        commandLine(
            "keytool", "-genkeypair",
            "-keystore", KEYSTORE_FILE.name,
            "-alias", KEY_ALIAS,
            "-keyalg", "RSA",
            "-keysize", "2048",
            "-validity", "10000",
            "-storepass", KEYSTORE_PASSWORD,
            "-keypass", KEY_PASSWORD,
            "-dname", "CN=miss_net, OU=dev, O=miss_net, L=SH, ST=SH, C=CN",
            "-noprompt"
        )
    }

    // Load signing config — works whether keystore was pre-existing or just generated
    val signingPropsFile = file("signing.properties")
    val signingProps = signingPropsFile.takeIf { it.exists() }?.let { f ->
        java.util.Properties().apply { f.inputStream().use { load(it) } }
    }
    if (signingProps != null) {
        signingConfigs.create("ci") {
            storeFile = file(signingProps["keystore"] as String)
            storePassword = signingProps["keystore_password"] as String
            keyAlias = signingProps["key_alias"] as String
            keyPassword = signingProps["key_password"] as String
        }
    } else if (KEYSTORE_FILE.exists()) {
        // Use auto-generated keystore as fallback
        signingConfigs.create("autoGen") {
            storeFile = KEYSTORE_FILE
            storePassword = KEYSTORE_PASSWORD
            keyAlias = KEY_ALIAS
            keyPassword = KEY_PASSWORD
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
            abiFilters += listOf("arm64-v8a")
        }
    }

    buildTypes {
        debug {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // Ensure keystore exists before assemble
            tasks.getByName("assembleDebug").dependsOn(generateKeystoreTask)
            // Apply auto-generated keystore if no CI signing.properties
            if (signingProps == null && KEYSTORE_FILE.exists()) {
                signingConfig = signingConfigs.getByName("autoGen")
            } else if (signingProps != null) {
                signingConfig = signingConfigs.getByName("ci")
            }
        }

        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
}
