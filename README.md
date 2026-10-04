# MnemoLink for AnkiDroid

An Android companion app for AnkiDroid that will generate AI-assisted mnemonics, let you review and edit them, and save approved memory aids to your notes.

## Delivery approach

Implementation starts with the companion application. LLM configuration, credentials, prompting, and draft editing stay outside AnkiDroid. A later thin AnkiDroid integration will provide a native reviewer action and own the final save for that entry point.

The standalone companion will use stock AnkiDroid's public API to find and update existing notes. It will require an existing dedicated destination field, preserve unrelated note content, and never save generated output without explicit approval.

## Current status

The first companion workflow is implemented: enter a concept/context, generate a deterministic mock mnemonic, edit it, approve locally, and discard it. Source edits invalidate old drafts/approval, and cancellation ignores stale responses.

**This is an offline development demo, not the complete MVP.** It makes no LLM calls, does not connect to AnkiDroid, and does not save notes. Approval is clearly labeled as local-only. Drafts survive activity recreation but are lost after process death; durable recovery comes later.

The scaffold includes a pinned JDK 17 / Gradle 8.13 / Kotlin 2.2.21 toolchain, Compose UI, formatting, Android lint, shared JVM workflow tests, Android adapter tests, five device tests, dependency checksums, and CI checks. Device tests must be run on an emulator/phone; see the testing guide.

### Try the offline workflow

1. Install the debug APK on Android 8 or newer.
2. Review the synthetic example or enter plain-text concept/context.
3. Tap **Generate demo mnemonic** and edit the draft.
4. Tap **Approve demo draft**; the app confirms it was **not saved to AnkiDroid**.
5. Change the input to invalidate the old draft, or tap **Discard draft**.

Next increment: stock AnkiDroid availability/permission handling, real note search, and field mapping.

## Build

Configure your Android SDK using `ANDROID_HOME` or an untracked `local.properties`, then run:

```sh
python3 tools/verify_wrapper.py
./gradlew :core:ktlintCheck :core:test :app:ktlintCheck :app:testDebugUnitTest :app:lintDebug :app:assembleDebug :app:assembleDebugAndroidTest
```

See [build and test instructions](docs/testing.md) and [architecture](docs/architecture.md). The debug APK is `app/build/outputs/apk/debug/app-debug.apk`.

Private planning files, SDK paths, keys, build output, and personal Anki collections are excluded from Git. Do not add real note content or provider credentials to tests or documentation.

## Product boundaries

- Mnemonics belong to **notes**; card templates determine whether they are displayed.
- The native reviewer action is part of the integrated MVP, not something stock AnkiDroid can gain by installing the companion alone.
- A companion API save and a host-owned save are separate modes. They must never both execute for one request.
- Provider output is a suggestion, not a factual guarantee.

This is an independent project, not an official Anki or AnkiDroid application. Project licensing will be decided before distribution; absence of a license is not permission to redistribute.
