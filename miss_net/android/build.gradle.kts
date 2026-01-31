allprojects {
    repositories {
        google()
        mavenCentral()
    }
}

// ✅ 关键修复：这段代码必须放在文件靠前的位置！
// 在项目被 evaluate 之前注册监听器，动态注入 namespace
subprojects {
    afterEvaluate { project ->
        if (project.name.contains("ffmpeg_kit_flutter")) {
            val android = project.extensions.findByName("android")
            if (android != null) {
                // 使用反射设置 namespace，避免 AGP 8.0 报错
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
    // ❌ 之前的报错就是因为修复代码放在了这句话之后
    project.evaluationDependsOn(":app")
}

tasks.register<Delete>("clean") {
    delete(rootProject.layout.buildDirectory)
}

