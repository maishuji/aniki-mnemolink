# MnemoLink for Anki

An Android and Linux desktop companion that will generate AI-assisted mnemonics, let you review and edit them, and save approved memory aids to your Anki notes.

## Delivery approach

Implementation is desktop-first, starting with read-only AnkiConnect access in the Linux companion. Provider configuration, credentials, prompting, and draft editing stay in the companion. Android retains the offline demo; a later thin AnkiDroid integration will provide a native reviewer action and own the final save for that entry point.

Linux desktop has a read-only AnkiConnect backend (commit `e7e6e75`), with a desktop UI for explicit connection, search, note selection, and field mapping. Android note access will use stock AnkiDroid's public API but is not implemented yet. Future saves must require an existing dedicated destination field, preserve unrelated note content, and never save generated output without explicit approval.

## Current status

The companion workflow supports concept/context input, generation, draft editing, local approval, and discard. Source edits invalidate old drafts/approval, and cancellation ignores stale responses. The local Ollama adapter is committed in `9935282`; desktop UI integration through `DesktopGenerationController` is implemented, with Qwen3 (`qwen3:14b`) as the default, selectable Qwen2.5 (`qwen2.5:14b`), and offline Demo. Android remains deterministic mock-only.

**This is a development increment, not the complete MVP.** Desktop note access is read-only; Ollama generation uses the fixed `http://127.0.0.1:11434/api/chat` endpoint and requires a trusted, locally administered server with local inference, not cloud-backed aliases. Loopback alone is not proof of local inference. There are no remote/custom endpoint controls, cloud credentials, automatic downloads/retries, or note writes. Android remains offline with no AnkiDroid connection. Approval is local-only. Drafts are in memory: Android retains them across activity recreation, but process death or closing the desktop window loses them. Durable recovery comes later.

The scaffold includes a pinned JDK 17 / Gradle 8.13 / Kotlin 2.2.21 toolchain, Compose UI, formatting, Android lint, shared JVM workflow tests, Android adapter tests, five device tests, dependency checksums, and CI checks. Device tests must be run on an emulator/phone; see the testing guide.

### Try the offline workflow

1. Install the debug APK on Android 8 or newer.
2. Review the synthetic example or enter plain-text concept/context.
3. Tap **Generate demo mnemonic** and edit the draft.
4. Tap **Approve demo draft**; the app confirms it was **not saved to AnkiDroid**.
5. Change the input to invalidate the old draft, or tap **Discard draft**.

### Desktop read-only Anki flow

Install AnkiConnect (add-on code `2055492159`) in Anki Desktop and keep Anki running at the local endpoint `http://127.0.0.1:8765/`. See [desktop setup](docs/desktop-linux.md#read-only-ankiconnect-setup-and-flow).

The desktop UI uses an optional masked, session-only AnkiConnect key and explicit **Connect** and **Search** actions; it does not contact Anki at startup. Search initially uses `tag:MnemoLinkDemo`, fetches at most the first 20 matching note bodies, and shows the total match count. Selecting a note rereads it. Field dropdowns map an existing concept field, optional context field, and required destination field, all distinct. A plaintext preview precedes explicit loading into the workflow; loading replaces concept/context and requires confirmation if a draft exists or generation is running. Selection/preview alone does not replace input or generate anything.

### Desktop local generation

The UI shows explicit Generate/Regenerate confirmation for each Ollama request, showing the current concept/context and exact model. Only concept/context are sent as source data; note metadata and destination content are not sent. A backend change asks for confirmation only if a draft, approval, or in-flight generation exists; confirming clears draft/approval and cancels the old request. With empty idle state, switching is immediate and sends no request. Approval still saves nothing. The French prompt treats serialized JSON input as untrusted data, uses no tools, and labels invented associations as mnemonic fiction, not real etymology.

See [Ollama setup, limits, and privacy](docs/ollama.md), including manual `ollama list`/`ollama ps` checks and optional user-run model downloads (network and roughly 9 GB storage per model). Smoke mode always uses offline Demo and makes no Ollama or AnkiConnect requests. Existing models/hardware observations are not a live adapter test. The opt-in live adapter test passed with installed `qwen3:14b` and a synthetic `ubiquitous` example (60-second bounded request); see [the opt-in command](docs/testing.md#local-ollama-coverage-and-manual-verification). The test is skipped by default. No live Anki note read/write or UI-click validation is claimed, and final packaging validation is not yet claimed.

Next increments: validate the implemented desktop selector/confirmation UI through actual clicks, complete packaging checks, then add separately guarded approved saves.

## Run on Linux

With JDK 17 and a graphical Linux session:

```sh
./gradlew -PdesktopOnly=true :desktop:run
```

**No Android SDK is needed.** To build a standalone application with its Java runtime included:

```sh
./gradlew -PdesktopOnly=true :desktop:createDistributable
./desktop/build/compose/binaries/main/app/mnemolink/bin/mnemolink
```

See [Linux desktop instructions](docs/desktop-linux.md) for Debian packaging, smoke checks, requirements, and remaining MVP scope.

## Build Android

Configure your Android SDK using `ANDROID_HOME` or an untracked `local.properties`, then run:

```sh
python3 tools/verify_wrapper.py
./gradlew :core:ktlintCheck :core:test :app:ktlintCheck :app:testDebugUnitTest :app:lintDebug :app:assembleDebug :app:assembleDebugAndroidTest
```

See [build and test instructions](docs/testing.md) and [architecture](docs/architecture.md). The debug APK is `app/build/outputs/apk/debug/app-debug.apk`.

Private planning files, SDK paths, keys, build output, and personal Anki collections are excluded from Git. Do not add real note content or provider credentials to tests or documentation.

## Product boundaries

- Mnemonics belong to **notes**; card templates determine whether they are displayed.
- A native reviewer action requires a platform-specific host integration: an AnkiDroid fork/extension on Android or an Anki Desktop add-on. Installing this companion alone does not add that action.
- A companion API save and a host-owned save are separate modes. They must never both execute for one request.
- Provider output is a suggestion, not a factual guarantee.

This is an independent project, not an official Anki or AnkiDroid application. Project licensing will be decided before distribution; absence of a license is not permission to redistribute.
