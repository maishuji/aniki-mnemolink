# Linux desktop companion

MnemoLink has a native JVM/Compose desktop target alongside Android. Both platforms use the same validation, deterministic generator, and generation/approval workflow in `core`.

## Run from source

Requirements: JDK 17, a Linux graphical session, and internet access for the first Gradle/dependency download. **No Android SDK or emulator is needed.** Manual-input mock generation does not require Anki or an API key; read-only note access requires Anki Desktop with AnkiConnect running locally. On Wayland, the JVM/AWT window may require XWayland; the desktop must provide a usable `DISPLAY`.

From the repository root:

```sh
./gradlew -PdesktopOnly=true :desktop:run
```

`desktopOnly` excludes the Android `app` project. Gradle still resolves the pinned build plugins, but it does not configure an Android module or access its SDK.

The window supports editable concept/context, mock generation, draft editing, local approval, discard, scrolling, and a dark-theme toggle. **Approval does not save an Anki note.** Drafts are in memory and disappear when the window closes.

## Read-only AnkiConnect setup and flow

The desktop-first read-only backend is committed in `e7e6e75`; the desktop UI is wired to it. This is not a live-integration or UI-click validation claim.

1. Open Anki Desktop with a disposable test profile/collection.
2. In **Tools → Add-ons → Get Add-ons**, install [AnkiConnect](https://ankiweb.net/shared/info/2055492159) using code `2055492159`, then restart Anki.
3. Keep Anki running with AnkiConnect bound to its default local endpoint, `http://127.0.0.1:8765/`. Do not expose it to the LAN or loosen browser-origin rules for this native client.
4. If AnkiConnect has an `apiKey` configured, enter it in the optional masked key field. This is not a provider key: it is session-only, not persisted or printed by the companion. Review AnkiConnect's own logging separately.
5. Explicitly **Connect**, then **Search**. Connection checks API version 6 or newer; it does not automatically search. The initial query is `tag:MnemoLinkDemo`; prepare synthetic notes with that tag in Anki. Startup and smoke mode do not contact Anki.
6. Search uses Anki browser syntax, reports the total match count, and fetches bodies for at most the first 20 results. This is a client limit, not server pagination; narrow broad queries.
7. Select a note to reread it, then use dropdowns for concept, optional context, and destination. All chosen fields must already exist and be distinct. Destination is required even in this read-only increment; an existing `Mnemonic` field is the default when available. No missing field is created.
8. Review the plaintext concept/context preview, then explicitly load it into the workflow. Loading replaces the current inputs and requires confirmation when a draft exists or generation is running; canceling leaves the workflow untouched. Selection, mapping, and preview alone do not load or generate.
9. Generate a mock mnemonic, edit it, and approve locally if desired. Nothing is sent to a cloud provider or written back to Anki.

HTML conversion affects only the preview/input copy; raw Anki field values remain unchanged. Empty/image-only concepts, unsupported cloze/template syntax, or excessive input length block loading rather than silently truncating input. Key, selection, and mappings are in memory, not durable profile settings. See [protocol and safety details](ankiconnect.md).

## Build a directly runnable application

```sh
./gradlew -PdesktopOnly=true :desktop:createDistributable
```

Launch the generated application:

```sh
./desktop/build/compose/binaries/main/app/mnemolink/bin/mnemolink
```

Keep the whole `mnemolink` directory together: its launcher relies on sibling runtime/library files. The image includes a Java runtime, so running it does not require Gradle, an Android SDK, or a separately installed JDK. It still needs the Linux system libraries used by Java/AWT and Skiko. Native packaging targets the build machine's architecture; cross-distribution compatibility is not established by a local smoke check.

## Debian package

On a Debian-compatible build machine with JDK 17, `dpkg-deb`, and `fakeroot` available:

```sh
./gradlew -PdesktopOnly=true :desktop:packageDeb
```

Packages are written under `desktop/build/compose/binaries/main/deb/`. On the current Linux x86-64 machine the output is `mnemolink_0.1.0-1_amd64.deb`.

The package is a development artifact, not a production release. Project licensing, maintainer/release metadata, signing, installation/upgrade behavior, and target-distribution support need review before distribution. No package is installed automatically.

## Checks

```sh
./gradlew -PdesktopOnly=true :core:ktlintCheck :core:test :desktop:build
```

A bounded smoke mode opens/composes the application, exercises generation/edit/approval/discard through the shared workflow, and exits:

```sh
./gradlew -PdesktopOnly=true :desktop:run --args=--smoke-test
./desktop/build/compose/binaries/main/app/mnemolink/bin/mnemolink --smoke-test
```

Smoke mode selects software rendering for machines without a GPU. It is **not** a control-click/rendered-screen test and does not validate the normal hardware renderer. It requires a graphical display; CI uses Xvfb and an outer process timeout. Core unit tests are headless and use injected coroutine scopes.

## Toward the desktop MVP

The current increment adds read-only local note access to the maintained mock workflow, not the complete mnemonic-generating/saving MVP. The remaining product slices are:

1. Configure one real LLM provider and explicit content-sharing/credential UX. Desktop secrets require a Linux-appropriate strategy; Android Keystore code must not be reused as if it were portable.
2. Validate the read-only UI against a disposable Anki collection, including unavailable/unauthorized servers and replacement confirmation.
3. Add a separate guarded save path: reread identity/schema/source/destination, require current approval and overwrite confirmation, write only the mapped destination, verify by read-back, and handle uncertain outcomes. Do not write directly to Anki's SQLite database or use an undocumented automation shortcut.
4. Test the actual Anki integration with synthetic disposable collections; preserve unrelated fields and scheduling, and clearly document concurrency/undo limitations of the chosen adapter.

AnkiConnect is required only for explicit local note reads, not manual-input mock generation or smoke checks. No live Anki integration or rendered UI-click tests have been validated for this increment. A native button in Anki Desktop would be a separate Anki add-on/integration, not the AnkiDroid fork.
