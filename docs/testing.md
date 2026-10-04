# Build and test

## Linux desktop

Desktop checks do not require an Android SDK:

```sh
./gradlew -PdesktopOnly=true :core:ktlintCheck :core:test :desktop:build
./gradlew -PdesktopOnly=true :desktop:run --args=--smoke-test
```

The second command requires a display, exercises application composition and local mock workflow, then exits. It uses software rendering, makes no AnkiConnect request, and is not an interactive/rendered-screen assertion. See [desktop instructions](desktop-linux.md) for AnkiConnect setup and standalone image/package checks.

### Read-only AnkiConnect coverage and manual verification

Backend commit `e7e6e75` adds JVM adapter tests using MockWebServer on ephemeral loopback ports, plus controller/mapping tests using fake repositories and injected coroutine scopes. `:desktop:build` includes the desktop JVM tests. Coverage includes optional-key authentication/errors, malformed and bounded responses, cancellation/resource cleanup, the 20-note body limit, fresh note identity/schema checks, stale completions, field mapping, and raw HTML preservation/plaintext extraction. These fixtures are not a live Anki integration test.

The desktop UI is wired to this backend. Two additional JVM tests verify that loading identical input clears draft/approval and that loading cancels pending generation. **No live collection end-to-end validation or UI-click test result is claimed.** The existing smoke check does not validate connecting, searching, dropdowns, preview, or replacement confirmation. The initial backend version-only probe found no reachable AnkiConnect endpoint; it read or wrote no collection content.

For later manual verification, use Anki Desktop with add-on `2055492159` at `http://127.0.0.1:8765/` and a disposable collection containing synthetic `MnemoLinkDemo`-tagged notes. Check:

- No startup request; optional masked key is session-only; explicit Connect and Search handle missing/unauthorized AnkiConnect safely.
- Initial query `tag:MnemoLinkDemo`, full match count, at most the first 20 note bodies, and a fresh reread on selection (including changed/deleted notes).
- Dropdowns require existing, distinct concept/optional context/destination fields; missing destination blocks load without creating fields.
- Plaintext preview leaves raw Anki values unchanged; invalid/unsupported input blocks load. Selection and mapping alone leave workflow input untouched.
- Explicit load replaces concept/context; a draft or active generation requires confirmation. Cancel preserves state; confirmed replacement invalidates stale draft/approval/generation results.
- Generation stays mock-only, approval stays local, and no cloud requests, note writes, schema changes, or scheduling changes occur.

These are verification steps, not recorded passing results. Real-provider and guarded-save tests belong to future increments; read-only fixture coverage does not establish conflict-safe writes.

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
