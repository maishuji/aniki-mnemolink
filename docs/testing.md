# Build and test

## Requirements

- JDK 17.
- Android SDK platform 36 and build-tools 36.1.0; platform-tools for device use.
- Android 8/API 26 or newer for running the app.
- Set `ANDROID_HOME` to your SDK, or create an untracked `local.properties` with `sdk.dir=/absolute/path/to/sdk`.

The Gradle wrapper pins Gradle 8.13 and verifies the distribution SHA-256. Dependency versions are pinned in `gradle/libs.versions.toml`, and Gradle verifies resolved artifacts against `gradle/verification-metadata.xml`. New artifacts require deliberate checksum review; do not routinely regenerate verification metadata just to silence a failure.

Dependency/toolchain update notices are informational in lint so a newly published version does not break a pinned build. Other lint warnings fail the checks. Updates and security review remain deliberate maintenance work.

## Local checks

```sh
python3 tools/verify_wrapper.py
./gradlew :app:ktlintCheck :app:testDebugUnitTest :app:lintDebug :app:assembleDebug :app:assembleDebugAndroidTest
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

CI runs formatting, JVM tests, Android lint, debug assembly, and compilation of the instrumentation-test APK. It does not yet run a device/emulator or live-provider suite.

## Workflow coverage

JVM tests cover validation, local approval, source-edit invalidation, cancellation, duplicate generation taps, noncooperative late responses, and failure recovery. Five Compose device tests cover generation/edit/approval, source invalidation, discard, unsafe-output rejection, and activity recreation. Compiling these tests is not evidence they passed on a device.

Current draft state is ViewModel-only: activity recreation retains it, process death does not. Encrypted durable recovery and the two-app contract are later increments.
