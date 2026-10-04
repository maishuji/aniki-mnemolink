# MnemoLink for Anki

An Android and Linux desktop companion that will generate AI-assisted mnemonics, let you review and edit them, and save approved memory aids to your Anki notes.

## Delivery approach

Implementation is desktop-first, starting with read-only AnkiConnect access in the Linux companion. Provider configuration, credentials, prompting, and draft editing stay in the companion. Android retains the offline demo; a later thin AnkiDroid integration will provide a native reviewer action and own the final save for that entry point.

Linux desktop has a read-only AnkiConnect backend (commit `e7e6e75`), with a desktop UI for explicit connection, search, note selection, and field mapping. Android note access will use stock AnkiDroid's public API but is not implemented yet. Future saves must require an existing dedicated destination field, preserve unrelated note content, and never save generated output without explicit approval.

## Current status

The first companion workflow is implemented: enter a concept/context, generate a deterministic mock mnemonic, edit it, approve locally, and discard it. Source edits invalidate old drafts/approval, and cancellation ignores stale responses.

**This is a development increment, not the complete MVP.** Desktop note access is read-only and local; Android remains offline with no AnkiDroid connection. Generation is deterministic mock generation only: no cloud/provider calls and no note writes on either platform. Approval is local-only. Drafts are in memory: Android retains them across activity recreation, but process death or closing the desktop window loses them. Durable recovery comes later.

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

Next increments: one real provider with explicit content-sharing consent, then separately guarded approved saves. Live Anki integration and UI-click behavior have not been validated.

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
