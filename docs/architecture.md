# Architecture

MnemoLink is an independent companion for Anki. The shared `core` Kotlin/JVM module contains domain models, validation, deterministic generation, and the generation workflow; it has no Android dependencies. The Android `app` module owns its platform UI and lifecycle adapter. The Linux `desktop` module uses Compose Desktop and the same core without duplicating approval/cancellation rules. There is no Kotlin Multiplatform/native conversion: both targets consume a plain JVM library.

## Boundaries

- Compose screens render state and emit events.
- ViewModels coordinate generation and approval; they do not modify Anki data directly.
- Domain models and validation remain independent of Android UI/networking.
- `MnemonicService` only returns a suggestion. It has no collection-writing capability.
- Desktop read-only Anki access uses `AnkiRepository`/`AnkiConnectRepository`. `OllamaMnemonicService` is a separate suggestion-only generation adapter (commit `9935282`); any write adapter remains a future, separate capability.
- `AppContainer` supplies the deterministic service; `MainActivity` provides a ViewModel factory and collects state with lifecycle awareness.
- `GenerationWorkflow` in `core` invalidates a request before canceling its job, preventing a late noncooperative response from replacing newer state. Source edits revoke the draft/approval; draft edits revoke approval.
- Android `GenerationViewModel` delegates to the workflow using `viewModelScope`. Each workflow owns a child job; closing it freezes its state and cancels only that job, not the caller's scope. Calls and the supplied dispatcher must stay on one UI thread.
- Desktop uses a Swing-dispatched composition scope; its entry point has no Android/lifecycle dependency. `DesktopGenerationController` owns backend selection, the shared workflow, and service lifetime. Its UI integration is implemented: it defaults to `qwen3:14b`, offers `qwen2.5:14b` and offline Demo, cancels/invalidates workflow state before replacing the service, and closes the old service. Disposal closes the workflow/client without canceling the caller's scope. The UI confirms a backend change only if a draft, approval, or in-flight generation exists; an empty idle workflow switches immediately without sending a request.
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

## Desktop local generation boundary

The application endpoint is fixed to `http://127.0.0.1:11434/api/chat`; no remote/custom endpoint UI, cloud credentials, model downloads, or automatic retries are provided. Fixture tests may inject validated loopback endpoints/timeouts; this is not an end-user configuration feature. A trusted, locally administered Ollama service with local weights/inference is required. Loopback alone does not prove inference stays local, and cloud-backed aliases are outside the supported trust boundary.

The UI confirms the current concept/context and selected model for each explicit Ollama Generate/Regenerate action. Confirmation is request-specific, not stored consent. Only concept/context are sent as source data, serialized as JSON; no note metadata, destination value, credentials, or prior draft is sent. Backend switching requires confirmation only when a draft, approval, or in-flight generation exists; confirmation invalidates draft/approval and cancels prior work, while canceled dialogs preserve state. Empty idle state switches immediately without a request. Loading, selection, startup, and local approval do not generate. Smoke always uses offline Demo without server requests.

The French system prompt asks for a brief plain-text mnemonic, treating invented associations as fiction/images rather than real etymology. Serialized source is untrusted data, not instructions; no tools are supplied and tool-call output is rejected. Prompting does not guarantee factual correctness or injection resistance.

The adapter disables redirects/proxies/retries, bounds calls to 60 seconds and bodies to 256 KiB, and requests `num_ctx: 2048`, `num_predict: 384`, `think: false`, `keep_alive: 2m`, and nonstreaming responses. It rejects truncation, malformed/incomplete output, unexpected models, tool calls, and invalid drafts. Cancellation cancels the HTTP call; response bodies are closed. Service close cancels calls, evicts connections, and shuts down its executor, not the independently managed Ollama server. See [Ollama details](ollama.md).

## Current limits

Android uses deterministic, clearly labeled demo suggestions, requests no network or database permission, and does not connect to AnkiDroid. Desktop has read-only local AnkiConnect access and the local Ollama adapter; the controller/consent UI is implemented. The opt-in live adapter test passed with installed `qwen3:14b` and synthetic `ubiquitous` input, using a 60-second bounded request; it is skipped by default. See [test instructions](testing.md#local-ollama-coverage-and-manual-verification). No live Anki note read/write, rendered UI-click validation, or final packaging result is claimed. There is no cloud-provider integration or note-writing capability. Approval is local workflow state, not a claim that a note was saved. The UI explicitly says so, offers editable input/drafts and discard, and supports system dark mode and scrollable large-font layouts.

Drafts remain in each platform's in-memory workflow and are lost after process death or desktop window closure. Rotation retention has a device test, but requires execution on an emulator/phone before claiming device validation. The `EntryMode` domain type anticipates standalone and return-only modes; no host result endpoint is exposed yet.

The working application ID is `dev.mnemolink.app`; confirm ownership and final distribution naming before publishing. No production license is assumed; the current desktop local-model default is Qwen3, not a commitment to future cloud-provider support.
