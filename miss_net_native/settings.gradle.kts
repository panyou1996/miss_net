pluginManagement {
    repositories {
        // Google Maven first — required for KSP, Guava, AndroidX artifacts
        google()
        mavenCentral()
        gradlePluginPortal()
        // Aliyun mirrors as fallback only (some artifacts may be missing/incomplete)
        maven { url = uri("https://maven.aliyun.com/repository/google") }
        maven { url = uri("https://maven.aliyun.com/repository/public") }
        maven { url = uri("https://maven.aliyun.com/repository/gradle-plugin") }
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        // Google Maven first — Aliyun mirrors have incomplete KSP sync
        google()
        mavenCentral()
        maven { url = uri("https://maven.aliyun.com/repository/google") }
        maven { url = uri("https://maven.aliyun.com/repository/public") }
    }
}

rootProject.name = "MissNetNative"
include(":app")