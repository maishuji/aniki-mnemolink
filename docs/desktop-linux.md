# Linux desktop companion

MnemoLink has a native JVM/Compose desktop target alongside Android. Both platforms use the same validation, deterministic generator, and generation/approval workflow in `core`.

## Run from source

Requirements: JDK 17, a Linux graphical session, and internet access for the first Gradle/dependency download. **No Android SDK, emulator, Anki installation, or API key is required for this offline increment.** On Wayland, the JVM/AWT window may require XWayland; the desktop must provide a usable `DISPLAY`.

From the repository root:

```sh
./gradlew -PdesktopOnly=true :desktop:run
```

`desktopOnly` excludes the Android `app` project. Gradle still resolves the pinned build plugins, but it does not configure an Android module or access its SDK.

The window supports editable concept/context, mock generation, draft editing, local approval, discard, scrolling, and a dark-theme toggle. **Approval does not save an Anki note.** Drafts are in memory and disappear when the window closes.

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

The current target runs the maintained offline workflow, not the complete mnemonic-generating/saving MVP. The remaining product slices are:

1. Configure one real LLM provider and explicit content-sharing/credential UX. Desktop secrets require a Linux-appropriate strategy; Android Keystore code must not be reused as if it were portable.
2. Add a desktop Anki adapter, likely using the separately installed **AnkiConnect** add-on while Anki is running. Verify its documented API before implementation, bind to a local endpoint, and handle unavailable/unauthorized servers explicitly.
3. Select actual notes, map an existing destination field, detect changes, and save only after current approval/overwrite checks. Do not write directly to Anki's SQLite database or use an undocumented automation shortcut.
4. Test the actual Anki integration with synthetic disposable collections; preserve unrelated fields and scheduling, and clearly document concurrency/undo limitations of the chosen adapter.

AnkiConnect is not installed, contacted, or required by this increment. A native button in Anki Desktop would be a separate Anki add-on/integration, not the AnkiDroid fork.
