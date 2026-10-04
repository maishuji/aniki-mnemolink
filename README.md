# MnemoLink for Anki

An Android and Linux desktop companion that will generate AI-assisted mnemonics, let you review and edit them, and save approved memory aids to your Anki notes.

## Delivery approach

Implementation starts with the companion application. LLM configuration, credentials, prompting, and draft editing stay outside AnkiDroid. A later thin AnkiDroid integration will provide a native reviewer action and own the final save for that entry point.

Android note access will use stock AnkiDroid's public API. Linux desktop note access needs a separate adapter, likely AnkiConnect with Anki Desktop running. Neither integration is implemented yet. Both must require an existing dedicated destination field, preserve unrelated note content, and never save generated output without explicit approval.

## Current status

The first companion workflow is implemented: enter a concept/context, generate a deterministic mock mnemonic, edit it, approve locally, and discard it. Source edits invalidate old drafts/approval, and cancellation ignores stale responses.

**This is an offline development demo on Android and Linux, not the complete MVP.** It makes no LLM calls, does not connect to Anki/AnkiDroid, and does not save notes. Approval is clearly labeled as local-only. Drafts are in memory: Android retains them across activity recreation, but process death or closing the desktop window loses them. Durable recovery comes later.

The scaffold includes a pinned JDK 17 / Gradle 8.13 / Kotlin 2.2.21 toolchain, Compose UI, formatting, Android lint, shared JVM workflow tests, Android adapter tests, five device tests, dependency checksums, and CI checks. Device tests must be run on an emulator/phone; see the testing guide.

### Try the offline workflow

1. Install the debug APK on Android 8 or newer.
2. Review the synthetic example or enter plain-text concept/context.
3. Tap **Generate demo mnemonic** and edit the draft.
4. Tap **Approve demo draft**; the app confirms it was **not saved to AnkiDroid**.
5. Change the input to invalidate the old draft, or tap **Discard draft**.

Next integration increments: real provider configuration and platform-specific note access/search/field mapping.

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
