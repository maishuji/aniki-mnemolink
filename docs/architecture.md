# Architecture

MnemoLink is an independent companion for Anki. The shared `core` Kotlin/JVM module contains domain models, validation, deterministic generation, and the generation workflow; it has no Android dependencies. The Android `app` module owns its platform UI and lifecycle adapter. The Linux `desktop` module uses Compose Desktop and the same core without duplicating approval/cancellation rules. There is no Kotlin Multiplatform/native conversion: both targets consume a plain JVM library.

## Boundaries

- Compose screens render state and emit events.
- ViewModels coordinate generation and approval; they do not modify Anki data directly.
- Domain models and validation remain independent of Android UI/networking.
- `MnemonicService` only returns a suggestion. It has no collection-writing capability.
- Desktop read-only Anki access uses `AnkiRepository`/`AnkiConnectRepository`; a real provider and any write adapter remain future, separate capabilities.
- `AppContainer` supplies the deterministic service; `MainActivity` provides a ViewModel factory and collects state with lifecycle awareness.
- `GenerationWorkflow` in `core` invalidates a request before canceling its job, preventing a late noncooperative response from replacing newer state. Source edits revoke the draft/approval; draft edits revoke approval.
- Android `GenerationViewModel` delegates to the workflow using `viewModelScope`. Each workflow owns a child job; closing it freezes its state and cancels only that job, not the caller's scope. Calls and the supplied dispatcher must stay on one UI thread.
- Desktop creates its workflow with a Swing-dispatched composition scope and closes it on window disposal. Desktop widgets emit callbacks to the same workflow; its entry point has no Android/lifecycle dependency.
- Platform UIs stay separate: Android uses XML/localized strings and lifecycle collection; desktop owns windowing, scrolling, theme selection, and packaging. AnkiDroid API access and the desktop AnkiConnect adapter cannot be treated as the same implementation.
- Input/output lengths are bounded. Initial plain-text validation rejects field separators, Anki media/template markup, and HTML-like angle brackets. This is a restricted demo, not general HTML/cloze/formula support.

There will be two explicit entry modes: standalone note selection/API save and reviewer-launched result-only generation. One request has one save owner. The return-only companion must never fall back to an API write.

## Desktop read-only note boundary

Desktop-first backend commit `e7e6e75` adds `AnkiConnectRepository`, `AnkiBrowserController`, and `NoteFieldMapping`. The desktop UI exposes this read path; live integration and UI-click behavior are not validated.

- Only `version`, `findNotes`, and `notesInfo` are exposed, using API v6 and loopback HTTP (default `http://127.0.0.1:8765/`). Redirects, proxies, and automatic retries are disabled; calls and response sizes are bounded. No note, schema, or scheduling writes exist.
- The optional masked API key stays in session memory, not persistent settings or companion logs. Explicit Connect checks version; explicit Search submits the initial `tag:MnemoLinkDemo` query or a user-edited query. Construction/startup/smoke checks make no Anki request.
- Search fetches at most the first 20 matching note bodies and retains the full match count; `findNotes` itself is not paginated. Selection rereads the note rather than trusting the search snapshot. Connection/query/selection changes invalidate stale results; disposal cancels owned work and closes the client without canceling the caller scope.
- Field dropdowns map by existing field names: concept, optional context, and required destination must be distinct. An existing `Mnemonic` field is the default destination when available; no field is created. Mapping and selection are in memory.
- HTML-to-plaintext conversion is only for preview/generation input; original values remain unchanged and no external media is loaded. Unsupported or invalid input blocks loading. Preview can be shown before a valid destination is selected, but loading requires a valid mapping including destination.
- Only explicit load replaces workflow concept/context. The UI must confirm replacement if a draft exists or generation is running; selection/mapping/preview alone do not mutate the workflow. Loading explicitly cancels the old workflow before copying input, clearing draft/approval even for identical plaintext, and does not trigger generation.

This read snapshot is not authorization for a later write: the inspected protocol lacks a robust collection-generation token/note GUID. Future saves need fresh identity/schema/source/destination checks, current approval/overwrite confirmation, a destination-only update, read-back verification, and uncertain-outcome recovery. Do not promise atomic conflict safety or undo semantics without validating the installed adapter. See [AnkiConnect details](ankiconnect.md).

## Current limits

Both application targets use deterministic, clearly labeled demo suggestions. Android requests no network or database permission and does not connect to AnkiDroid. Desktop contacts only local AnkiConnect on explicit actions; neither application contacts a cloud provider or writes notes. Approval is local workflow state, not a claim that a note was saved. The UI explicitly says so, offers editable input/drafts and discard, and supports system dark mode and scrollable large-font layouts.

Drafts remain in each platform's in-memory workflow and are lost after process death or desktop window closure. Rotation retention has a device test, but requires execution on an emulator/phone before claiming device validation. The `EntryMode` domain type anticipates standalone and return-only modes; no host result endpoint is exposed yet.

The working application ID is `dev.mnemolink.app`; confirm ownership and final distribution naming before publishing. No production license or provider choice is assumed.
