allprojects {
    repositories {
        google()
        mavenCentral()
    }
}

val newBuildDir: Directory =
    rootProject.layout.buildDirectory
        .dir("../../build")
        .get()
rootProject.layout.buildDirectory.value(newBuildDir)

subprojects {
    val newSubprojectBuildDir: Directory = newBuildDir.dir(project.name)
    project.layout.buildDirectory.value(newSubprojectBuildDir)
}

subprojects {
    project.evaluationDependsOn(":app")
}

tasks.register<Delete>("clean") {
    delete(rootProject.layout.buildDirectory)
}

// ✅ 添加以下代码来修复 ffmpeg_kit 的 namespace 问题
subprojects {
    afterEvaluate {
        if (project.name.contains("ffmpeg_kit_flutter")) {
            // 在 Kotlin DSL 中访问动态属性比较麻烦，需要扩展函数或直接配置
            configure<com.android.build.gradle.LibraryExtension> {
                namespace = "com.arthenica.ffmpegkit.flutter"
            }
        }
    }
}

