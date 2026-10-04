# Build and test

## Requirements

- JDK 17.
- Android SDK platform 36 and build-tools 36.1.0; platform-tools for device use.
- Android 8/API 26 or newer for running the app.
- Set `ANDROID_HOME` to your SDK, or create an untracked `local.properties` with `sdk.dir=/absolute/path/to/sdk`.

The Gradle wrapper pins Gradle 8.13 and verifies the distribution SHA-256. Dependency versions are pinned in `gradle/libs.versions.toml`.

## Local checks

```sh
python3 tools/verify_wrapper.py
./gradlew :app:ktlintCheck :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
```

To apply formatting deliberately, use `./gradlew :app:ktlintFormat` and review the diff.

Debug APK: `app/build/outputs/apk/debug/app-debug.apk`.

## Device tests

With an emulator or disposable test device connected:

```sh
./gradlew :app:connectedDebugAndroidTest
./gradlew :app:installDebug
```

No paid LLM request or personal Anki collection belongs in automated tests. Future API integration tests must use synthetic disposable notes; real cross-app permission tests cannot be replaced by repository fakes.

CI runs formatting, JVM tests, Android lint, and debug assembly. It does not yet run a device/emulator or live-provider suite.
