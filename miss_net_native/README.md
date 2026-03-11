# MissNet Native (Legacy Kotlin Mainline)

## Build prerequisite

This project is pinned to **JDK 17**.

Before building, verify:

```bash
java -version
./gradlew -version
```

Expected major version: `17`.

### Quick start

```bash
export JAVA_HOME=/path/to/jdk-17
./gradlew :app:assembleDebug
```

### Why this exists

Android Gradle Plugin / Kotlin / KSP in this legacy branch are currently validated on JDK 17. Using JDK 21+ may trigger hard-to-diagnose build/runtime issues in this project.
