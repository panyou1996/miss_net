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
        // 直接使用 p，不再依赖 lambda 参数推断
        if (p.name.contains("ffmpeg_kit_flutter")) {
            val android = p.extensions.findByName("android")
            if (android != null) {
                // 使用反射设置 namespace，解决 AGP 8.0 报错
                try {
                    val setNamespace = android.javaClass.getMethod("setNamespace", String::class.java)
                    setNamespace.invoke(android, "com.arthenica.ffmpegkit.flutter")
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

