# Desktop AnkiConnect integration

Desktop integration is prioritized before additional AnkiDroid work. This increment introduces a **read-only** AnkiConnect adapter and note-browser state: connection, explicit search, fresh note selection, and field mapping. It contains no note-update, schema-change, scheduling, or collection-write operation.

## Setup

1. Install Anki Desktop and load a profile. Use a disposable test profile/collection initially.
2. Install the [AnkiConnect add-on](https://ankiweb.net/shared/info/2055492159), code `2055492159`, through Anki's **Tools → Add-ons → Get Add-ons**.
3. Restart Anki after installation. Keep Anki running while using the companion.
4. Leave AnkiConnect bound to loopback at its default `http://127.0.0.1:8765/`. Do not expose it to your LAN or set permissive browser-origin rules just for this native client.
5. If you configured AnkiConnect's `apiKey`, enter it in the companion's optional masked **AnkiConnect API key** field. This is not an LLM key. It remains in process memory for the current session, is not persisted, and is never printed by the companion.

Native requests do not use a web browser Origin or automatically invoke permission dialogs. Server configuration/authentication must permit the request. A connection check verifies API version 6 or newer, not that a collection is ready for every operation.

## Protocol and safety boundary

Only these actions are implemented:

| Action | Purpose |
| --- | --- |
| `version` | Check the local server/API version. |
| `findNotes` | Explicit Anki browser-syntax search. |
| `notesInfo` | Read selected/matching note identities, field names/order, and original raw values. |

Requests use API v6 envelopes with `action`, `version`, `params`, and an optional `key`. The client rejects invalid envelopes, mismatched/duplicate note IDs, invalid field order, and malformed result types. Missing/deleted notes are handled without inventing replacements.

- No network request at controller construction, application launch, or smoke-check startup.
- No automatic search after connection. The initial query `tag:MnemoLinkDemo` targets synthetic notes only when the user clicks Search.
- Empty queries are not submitted; queries are limited to 512 UTF-16 code units.
- `findNotes` has no backend pagination. The client fetches bodies for at most the first 20 matches and reports the full returned match count. A broad ID result can exceed the response limit; narrow the query rather than assuming the server search is paginated.
- Each response is capped at 1 MiB, with a bounded call timeout, cancellation, and response-body cleanup.
- Loopback HTTP only. `localhost` is normalized to a literal loopback address; redirects, proxy routing, and automatic connection retries are disabled.
- Remote error strings, keys, request bodies, and note content are not emitted in errors/logs. Errors use fixed safe messages. AnkiConnect itself can log requests if its own logging is enabled; review that server setting separately.
- Connection/query/selection changes invalidate pending results. Closing/disconnecting cancels owned work and closes the client, without canceling its caller's UI scope.

The local service is not cryptographically authenticated by the loopback address. Treat local processes and the AnkiConnect installation as trusted; an optional API key does not turn arbitrary loopback HTTP into TLS or protect a compromised machine.

## Field mapping and generation input

Mappings use field **names**, not cached positions. A concept, optional context, and existing destination must be distinct. The default destination is an existing field named `Mnemonic` (case-insensitive); the app does not silently choose the answer field or create a missing field.

HTML conversion is for the generation preview only. Original field strings remain unchanged. Conversion removes scripts/styles/media payloads, decodes entities, and preserves useful line/paragraph breaks without loading external resources. Image-only/empty concepts, unsupported cloze/template syntax, and overlong input block loading; there is no silent truncation of generation input.

Mappings and selection are in memory for this increment, not durable per-profile configuration. AnkiConnect's `notesInfo` response does not provide a robust collection-generation token or note GUID in the inspected protocol. This read path must not be advertised as a safe-write authorization mechanism.

## Loading into local generation

The desktop read-only UI provides plaintext preview and explicit loading into the shared workflow. Loading replaces concept/context; if a draft exists or generation is active, replacement requires confirmation and cancels/invalidates old draft/approval/work. Selection/mapping/preview alone do not change generation input.

The local Ollama adapter is committed in `9935282`; UI integration through `DesktopGenerationController` is implemented, defaulting to `qwen3:14b` with `qwen2.5:14b` and offline Demo alternatives. Loading a note is **not consent to send it to Ollama**. Each Ollama Generate/Regenerate action shows confirmation of current concept/context and model; only those source values are sent, never note identity/schema, raw HTML, destination content, or the AnkiConnect key. Backend switching asks for confirmation only if a draft, approval, or in-flight generation exists; confirming clears draft/approval and cancels the previous request. With empty idle state, switching is immediate and sends no request. Approval is still local-only; no Anki write action has been added.

Ollama uses the fixed `http://127.0.0.1:11434/api/chat` endpoint and requires trusted locally administered inference, not cloud-backed aliases. Loopback is not proof of local inference. There are no remote/custom endpoint controls, cloud credentials, automatic downloads, or retries. Smoke always uses offline Demo and contacts neither service. See [Ollama setup and limits](ollama.md).

## Testing and verification status

Adapter tests use MockWebServer on ephemeral loopback ports and synthetic fixtures. Controller/mapping tests inject fake repositories and coroutine scopes. They cover authentication/errors, malformed/bounded responses, cancellation/resource cleanup, result limits, fresh identity/schema checks, stale completions, and raw HTML preservation/plaintext extraction.

No AnkiConnect endpoint was reachable on the development machine during the initial version-only probe. No live collection content was read or written, and no UI-click validation is claimed. The separate opt-in Ollama adapter test passed with installed `qwen3:14b` and synthetic `ubiquitous` input (60-second bounded request); it reads/writes no Anki notes and is skipped by default. See [test command](testing.md#local-ollama-coverage-and-manual-verification). No final packaging result is claimed. Passing fixture tests is not a claim that the current installed Anki/add-on has been exercised end-to-end.

The protocol was checked against the [archived official FooSoft source](https://github.com/FooSoft/anki-connect/blob/master/plugin/__init__.py), including its web/config implementation. The repository points to [the current canonical SourceHut repository](https://git.sr.ht/~foosoft/anki-connect); access there was bot-blocked during this work. Verify installed-version behavior with a disposable collection before treating live compatibility as validated.

## Next slices

1. Validate the existing read-only desktop UI and explicit plaintext loading against a disposable collection; no live collection or UI-click result is claimed.
2. Validate the implemented desktop Ollama selector/per-request input/model confirmation, conditional backend-switch confirmation/cancellation, and service cleanup through actual UI clicks; the passing live adapter test does not validate this UI.
3. Add a separate, guarded approved-write path: fresh identity/schema/input/destination checks, current overwrite confirmation, only the named destination field, read-back verification, and uncertain-outcome recovery. Investigate actual AnkiConnect concurrency/undo behavior rather than promising atomic conflict safety.

Do not implement these writes by opening Anki's SQLite files or automatically creating note types/templates.
