allprojects {
    repositories {
        google()
        mavenCentral()
    }
}

// ✅ 修复版：使用 'val p = this' 显式捕获项目对象
// 这样可以避开 'it' 或 'project ->' 导致的语法错误
subprojects {
    val p = this
    p.afterEvaluate {
        if (p.name.contains("ffmpeg_kit_flutter")) {
            val android = p.extensions.findByName("android")
            if (android != null) {
                try {
                    val setNamespace = android.javaClass.getMethod("setNamespace", String::class.java)
                    // Use the package name found in the error message
                    val targetNamespace = if (p.name.contains("new")) "com.antonkarpenko.ffmpegkit" else "com.arthenica.ffmpegkit.flutter"
                    setNamespace.invoke(android, targetNamespace)
                } catch (e: Exception) {
                    println("Failed to set namespace for ${p.name}: ${e.message}")
                }
            }
        }
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

