# MissNet

This repository is now split into two app clients:

- `android-native/`: the active Android-native rewrite built with Kotlin, Jetpack Compose, Material 3, Room, and Media3.
- `miss_net/`: the legacy Flutter client kept intact as reference only.

The backend/data shape is still serverless:

- video metadata comes from Supabase
- the scraper lives under `scraper/`
- runtime playback resolves the real stream URL from each `source_url`

## Android Native App

Location: `android-native/`

### Stack

- Kotlin
- Jetpack Compose
- Material 3
- Navigation Compose
- ViewModel + StateFlow
- Room
- Media3 ExoPlayer + Media3 download service
- DataStore
- OkHttp + Kotlin serialization
- Android WebView stream resolver fallback for Cloudflare-protected pages

### Implemented flows

- home feed sections from Supabase
- explore with popular actors, tags, and category shortcuts
- search with local history + remote suggestions/results
- favorites persisted locally in Room
- player with network playback, offline playback, related videos, favorites, progress save, and PiP entry
- downloads page backed by Media3 download state
- settings with dynamic color, autoplay, incognito, Wi-Fi-only downloads, keep-screen-awake, and storage cleanup actions

## Build Requirements

- JDK 17
- Android SDK installed locally
- Android platform 36 available

If `android-native/local.properties` does not exist, create it with:

```properties
sdk.dir=/path/to/Android/Sdk
```

## Build And Run

```bash
cd android-native
./gradlew :app:assembleDebug
```

Debug APK output:

```text
android-native/app/build/outputs/apk/debug/app-debug.apk
```

To install on a connected device:

```bash
cd android-native
./gradlew :app:installDebug
```

## Notes

- The Android-native app currently embeds the public Supabase project URL and anon key from the legacy client so it can run without extra setup.
- The Flutter app was not removed or refactored further.
- The scraper and Supabase function folders are unchanged and remain shared infrastructure.
