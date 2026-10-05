# Build and test

## Linux desktop

Desktop checks do not require an Android SDK. From the repository root, `make test` runs core/desktop JVM tests, `make check` runs their formatting/build/test checks, and `make smoke` builds the standalone image then runs its offline Demo smoke mode with a 30-second launcher timeout. Smoke requires a graphical display; the image build happens before the launcher timeout starts. Normal Makefile test/build/check targets set `MNEMOLINK_OLLAMA_TEST=false`; only `make test-ollama` opts into live synthetic Qwen3 generation. Use `make help` for all targets, including `make android-check` for SDK-dependent Android validation.

Equivalent direct desktop commands:

```sh
./gradlew -PdesktopOnly=true :core:ktlintCheck :core:test :desktop:build
./gradlew -PdesktopOnly=true :desktop:run --args=--smoke-test
```

The second command requires a display, exercises application composition and the offline Demo workflow, then exits. Smoke always uses Demo regardless of the normal controller Qwen3 default. It uses software rendering, makes no Ollama or AnkiConnect requests, and is not an interactive/rendered-screen assertion. See [desktop instructions](desktop-linux.md) for AnkiConnect setup and standalone image/package checks.

### Read-only AnkiConnect coverage and manual verification

Backend commit `e7e6e75` adds JVM adapter tests using MockWebServer on ephemeral loopback ports, plus controller/mapping tests using fake repositories and injected coroutine scopes. `:desktop:build` includes the desktop JVM tests. Coverage includes optional-key authentication/errors, malformed and bounded responses, cancellation/resource cleanup, the 20-note body limit, fresh note identity/schema checks, stale completions, field mapping, and raw HTML preservation/plaintext extraction. These fixtures are not a live Anki integration test.

The desktop UI is wired to this backend. Two additional JVM tests verify that loading identical input clears draft/approval and that loading cancels pending generation. **No live collection end-to-end validation or UI-click test result is claimed.** The existing smoke check does not validate connecting, searching, dropdowns, preview, or replacement confirmation. The initial backend version-only probe found no reachable AnkiConnect endpoint; it read or wrote no collection content.

For later manual verification, use Anki Desktop with add-on `2055492159` at `http://127.0.0.1:8765/` and a disposable collection containing synthetic `MnemoLinkDemo`-tagged notes. Check:

- No startup request; optional masked key is session-only; explicit Connect and Search handle missing/unauthorized AnkiConnect safely.
- Initial query `tag:MnemoLinkDemo`, full match count, at most the first 20 note bodies, and a fresh reread on selection (including changed/deleted notes).
- Dropdowns require existing, distinct concept/optional context/destination fields; missing destination blocks load without creating fields.
- Plaintext preview leaves raw Anki values unchanged; invalid/unsupported input blocks load. Selection and mapping alone leave workflow input untouched.
- Explicit load replaces concept/context; a draft or active generation requires confirmation. Cancel preserves state; confirmed replacement invalidates stale draft/approval/generation results.
- Loading/selection does not generate or authorize an Ollama request. Offline Demo makes no server requests; Ollama sends only separately confirmed concept/context, not note metadata or destination content. Approval stays local; no note writes, schema changes, or scheduling changes occur.

These are verification steps, not recorded passing results. The passing opt-in live Ollama adapter test is separate from pending UI verification and guarded-save tests; read-only fixture coverage does not establish conflict-safe writes.

### Local Ollama coverage and manual verification

Adapter commit `9935282` adds `OllamaMnemonicServiceTest` using MockWebServer on ephemeral loopback ports and shared generation-failure tests. Fixture coverage comprises 14 adapter tests, 17 desktop controller tests, and 2 core safe-error tests. Fixtures cover minimal concept/context-only source data, French/untrusted-JSON prompt policy, no tools/credentials, request options, endpoint restrictions, redirects/proxy/retry behavior, bounded bodies/timeouts, malformed/model-mismatched/incomplete/truncated/invalid output, safe error categories, cancellation, and close cleanup. These are synthetic HTTP fixtures, not live model inference.

`DesktopGenerationControllerTest` uses fake services and injected coroutine scopes for the Qwen3 default without requests, exact input dispatch to each backend, draft/approval invalidation on switching, same-backend preservation, cancellation/stale completions, old-service closure, controller disposal, and input/model-bound generation confirmation that rejects stale or duplicate requests. The UI uses this controller; the selector and confirmation dialogs are implemented, but controller tests do not prove rendered selector/dialog behavior. `:desktop:build` includes desktop JVM tests. Targeted headless commands are:

```sh
./gradlew -PdesktopOnly=true :desktop:test --tests 'dev.mnemolink.desktop.data.ollama.OllamaMnemonicServiceTest'
./gradlew -PdesktopOnly=true :desktop:test --tests 'dev.mnemolink.desktop.DesktopGenerationControllerTest'
```

The separate opt-in `OllamaLocalIntegrationTest` **passed** using installed `qwen3:14b` with synthetic concept `ubiquitous` and context `Mot anglais : présent partout.` via the adapter's 60-second bounded request. It asserts shared draft validity, not factual quality or UI behavior. By default, a JUnit assumption skips this test unless `MNEMOLINK_OLLAMA_TEST=true`; no live Ollama request is made when skipped.

To opt in deliberately against a trusted local Ollama service and already installed model, run `make test-ollama` or the equivalent direct command:

```sh
MNEMOLINK_OLLAMA_TEST=true ./gradlew -PdesktopOnly=true :desktop:test --tests '*OllamaLocalIntegrationTest' --rerun-tasks
```

This command sends synthetic concept/context to the fixed local endpoint and uses local compute; it never downloads a model or starts a server. The full local regression run passed 162 JVM tests with zero failures; the opt-in live test was skipped in that default run and passed separately. Desktop/core build, Android formatting/lint, debug/test APK compilation, Linux image/`.deb` builds, and the bundled Demo smoke check passed. The package was not installed, and Android device tests were not run. No live Anki note read/write or rendered UI-click validation is claimed.

Both `qwen3:14b` and `qwen2.5:14b` are already installed on the development machine; previous 100% GPU placement on an RTX 3080 Ti Laptop with 16 GB VRAM is a hardware observation, not an adapter test result. Check `ollama list` and `ollama ps` manually. If a model is absent, a user may explicitly run `ollama pull` as described in [Ollama setup](ollama.md), accepting network use and roughly 9 GB storage per model. Neither fixture tests, the opt-in live test, nor smoke pull models or start an unbounded `ollama serve` process.

For the implemented UI, verify manually with synthetic input and a trusted, locally administered Ollama installation using local weights/inference, never cloud-backed aliases (loopback alone is not proof):

- Qwen3 is the normal default; Qwen2.5 and offline Demo are selectable. Startup, selection, loading, approval, and smoke send no generation requests.
- Each Ollama Generate/Regenerate opens a dialog showing the current concept/context and exact model; cancel/dismiss sends nothing. Confirm sends exactly those source values, with no stale input/model and no reuse of previous consent.
- Switching backends requires confirmation only when a draft, approval, or in-flight generation exists. Cancel preserves backend/draft/approval; confirm clears draft/approval, cancels the prior request, closes the old client, preserves input, and rejects late results. With empty idle state, switching is immediate and sends no request. Selecting the same backend does not reset state.
- Both supported local models can return a reviewable French mnemonic. Invented associations are clearly mnemonic fiction, not factual etymology; source instructions do not authorize tools. Human review is still required.
- Missing/stopped server, missing model, 60-second timeout, malformed/truncated/oversized/invalid output, and cancellation produce safe errors or canceled state, not partial drafts or automatic retries/downloads.
- Closing the window during generation cancels owned work and closes client resources; it does not stop the independently managed Ollama server. Requests use the fixed endpoint, 256 KiB cap, 2048-token context, 384-token output budget, `think: false`, and `keep_alive: 2m`.
- Approval remains local, drafts remain in memory, and no note/schema/scheduling writes occur. Offline Demo and smoke work without either server.

This checklist is pending manual verification, not recorded passing results.

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
