# Build and test

## Linux desktop

Desktop checks do not require an Android SDK:

```sh
./gradlew -PdesktopOnly=true :core:ktlintCheck :core:test :desktop:build
./gradlew -PdesktopOnly=true :desktop:run --args=--smoke-test
```

The second command requires a display, exercises application composition and local workflow, then exits. It uses software rendering and is not an interactive/rendered-screen assertion. See [desktop instructions](desktop-linux.md) for standalone image/package checks and remaining integration work.

## Android requirements

- JDK 17.
- Android SDK platform 36 and build-tools 36.1.0; platform-tools for device use.
- Android 8/API 26 or newer for running the app.
- Set `ANDROID_HOME` to your SDK, or create an untracked `local.properties` with `sdk.dir=/absolute/path/to/sdk`.

The Gradle wrapper pins Gradle 8.13 and verifies the distribution SHA-256. Dependency versions are pinned in `gradle/libs.versions.toml`, and Gradle verifies resolved artifacts against `gradle/verification-metadata.xml`. New artifacts require deliberate checksum review; do not routinely regenerate verification metadata just to silence a failure.

Dependency/toolchain update notices are informational in lint so a newly published version does not break a pinned build. Other lint warnings fail the checks. Updates and security review remain deliberate maintenance work.

## Local checks

```sh
python3 tools/verify_wrapper.py
./gradlew :core:ktlintCheck :core:test :app:ktlintCheck :app:testDebugUnitTest :app:lintDebug :app:assembleDebug :app:assembleDebugAndroidTest
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

The Android CI job runs formatting, shared/adapter JVM tests, Android lint, debug assembly, and compilation of the instrumentation-test APK. A separate desktop-only job runs headless core/launch-option tests, formatting/build, native image creation, and the bounded bundled-app smoke check under Xvfb. Neither job uses a live LLM, real Anki collection, or Android device/emulator.

## Workflow coverage

The platform-independent `core` JVM tests cover validation, local approval, source-edit invalidation, cancellation, duplicate generation taps, noncooperative late responses, failure recovery, entry-mode preservation, and workflow disposal without canceling its caller scope. Android adapter tests verify the same behavior through `GenerationViewModel`. Five Compose device tests cover generation/edit/approval, source invalidation, discard, unsafe-output rejection, and activity recreation. Compiling these tests is not evidence they passed on a device.

Current draft state is in-memory workflow state: Android activity recreation retains it; process death or desktop window closure does not. Encrypted durable recovery and the two-app contract are later increments.
