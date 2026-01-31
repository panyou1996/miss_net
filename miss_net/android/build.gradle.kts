allprojects {
    repositories {
        google()
        mavenCentral()
    }
}

// ✅ 修复版：移除了 'project ->'，使用 'it' 来代表当前项目，避免类型推断错误
subprojects {
    afterEvaluate { 
        // 在这里，'it' 代表正在配置的子项目
        if (it.name.contains("ffmpeg_kit_flutter")) {
            val android = it.extensions.findByName("android")
            if (android != null) {
                // 使用反射动态设置 namespace，解决 AGP 8.0+ 的兼容性问题
                val setNamespace = android.javaClass.getMethod("setNamespace", String::class.java)
                setNamespace.invoke(android, "com.arthenica.ffmpegkit.flutter")
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

